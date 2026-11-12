package com.gotify.client.service.up

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import org.unifiedpush.android.distributor.Database
import org.unifiedpush.android.distributor.RegistrationCounter
import org.unifiedpush.android.distributor.WorkerCompanion

private const val TAG = "UpDatabase"

/**
 * UnifiedPush distributor storage, using the library's own SQLite schema
 * ("apps_db"): one row per registered app (connector token, package name,
 * channel id, endpoint). Separate from the app's Room database.
 */
class UpDatabase(context: Context) : Database(context) {
    override val counter = UpRegistrationCounter
}

object UpDatabaseFactory {
    private val db = java.util.concurrent.atomic.AtomicReference<Database?>(null)

    fun getDb(context: Context): Database =
        db.get() ?: UpDatabase(context.applicationContext).also { db.set(it) }
}

/**
 * Registration counter. Its only job here is to run [UpWorker] (pull queued
 * payloads) when the registration count changes. It deliberately does NOT
 * manage Gotify's own connection: keep-alive / FCM behaviour is untouched,
 * and there is no distributor foreground notification to update.
 */
object UpRegistrationCounter : RegistrationCounter() {
    override val workerCompanion = UpWorkerCompanion

    override fun onCountRefreshed(context: Context) {
        // No distributor notification to update.
    }

    override fun hasManyFails(): Boolean = false

    override fun getDb(context: Context): Database = UpDatabaseFactory.getDb(context)
}

object UpWorkerCompanion : WorkerCompanion(UpWorker::class.java) {
    // The worker only pulls the /up/pending queue; whether it may run does
    // not depend on any distributor service state.
    override fun canRun(context: Context): Boolean = true

    // The periodic worker is never started; these are only used by it.
    override fun isServiceStarted(context: Context): Boolean = false
    override fun enableComponents(context: Context) { /* receiver stays enabled */ }
    override fun disableComponents(context: Context) { /* receiver stays enabled */ }
}

/**
 * One-shot worker run after a registration-count change: pull anything the
 * server queued while this device was unreachable and forward it. Never
 * retries: a failed pull leaves the rows on the server, and the next
 * websocket connect / FCM wake-up pulls again.
 */
class UpWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        return try {
            val entryPoint = EntryPointAccessors.fromApplication(
                applicationContext, UnifiedPushEntryPoint::class.java
            )
            entryPoint.unifiedPushForwarder().pullPendingForActiveServer()
            Result.success()
        } catch (e: Exception) {
            Log.w(TAG, "UP pending pull failed", e)
            Result.success()
        }
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface UnifiedPushEntryPoint {
    fun unifiedPushForwarder(): UnifiedPushForwarder
}
