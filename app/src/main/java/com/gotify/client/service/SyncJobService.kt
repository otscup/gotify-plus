package com.gotify.client.service

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.util.Log
import com.gotify.client.BuildConfig
import com.gotify.client.data.api.NetworkClientFactory
import com.gotify.client.data.datastore.PreferencesRepository
import com.gotify.client.data.db.MessageDao
import com.gotify.client.data.db.ServerDao
import com.gotify.client.data.db.toDomain
import com.gotify.client.data.model.ApiResult
import com.gotify.client.data.repository.MessageRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val TAG = "SyncJob"

/**
 * Periodic catch-up when "Keep connection alive" is off, so messages still
 * notify without a persistent foreground service.
 */
@AndroidEntryPoint
class SyncJobService : JobService() {

    @Inject lateinit var prefs: PreferencesRepository
    @Inject lateinit var serverDao: ServerDao
    @Inject lateinit var messageDao: MessageDao
    @Inject lateinit var ingestor: MessageIngestor

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onStartJob(params: JobParameters): Boolean {
        scope.launch {
            try {
                sync()
            } catch (e: Exception) {
                Log.w(TAG, "Background sync failed", e)
            } finally {
                jobFinished(params, false)
            }
        }
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean {
        scope.cancel()
        return true
    }

    private suspend fun sync() {
        val preferences = prefs.userPreferences.first()
        if (preferences.keepAliveEnabled) return
        val server = serverDao.getServerById(preferences.activeServerId)?.toDomain() ?: return
        if (server.clientToken.isBlank()) return
        val api = runCatching { NetworkClientFactory.create(server, isDebug = BuildConfig.DEBUG) }.getOrNull() ?: return

        val lastKnownId = messageDao.maxMessageId(server.id) ?: 0L
        val result = MessageRepository(api).getMessages(limit = 50)
        if (result !is ApiResult.Success) return
        // An empty cache means first sync: store history without a notification burst.
        val notify = lastKnownId > 0
        result.data.messages
            .filter { it.id > lastKnownId }
            .sortedBy { it.id }
            .forEach { ingestor.ingest(server.id, it, notify) }
    }

    companion object {
        private const val JOB_ID = 4201
        private const val FCM_JOB_ID = 4202
        private const val INTERVAL_MS = 15 * 60 * 1000L

        /**
         * One-shot catch-up for an FCM signal. FirebaseMessagingService is
         * torn down as soon as onMessageReceived returns, so the pull must not
         * run in that service's coroutine scope; the system keeps the process
         * alive while this job is executing.
         */
        fun scheduleImmediate(context: Context) {
            context.getSystemService(JobScheduler::class.java).schedule(
                JobInfo.Builder(FCM_JOB_ID, ComponentName(context, SyncJobService::class.java))
                    .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                    .setOverrideDeadline(0)
                    .build()
            )
        }

        fun schedule(context: Context) {
            val scheduler = context.getSystemService(JobScheduler::class.java)
            if (scheduler.getPendingJob(JOB_ID) != null) return
            scheduler.schedule(
                JobInfo.Builder(JOB_ID, ComponentName(context, SyncJobService::class.java))
                    .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                    .setPeriodic(INTERVAL_MS)
                    .setPersisted(true)
                    .build()
            )
        }

        fun cancel(context: Context) {
            context.getSystemService(JobScheduler::class.java).cancel(JOB_ID)
        }
    }
}
