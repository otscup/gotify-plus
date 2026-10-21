package com.gotify.client.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.gotify.client.R
import com.gotify.client.data.datastore.PreferencesRepository
import com.gotify.client.util.currentLocalized
import com.gotify.client.data.db.MessageDao
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class UnreadWidgetProvider : AppWidgetProvider() {

    @Inject lateinit var prefs: PreferencesRepository
    @Inject lateinit var messageDao: MessageDao

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val serverId = prefs.userPreferences.first().activeServerId
                val unread = if (serverId > 0) messageDao.getUnreadCount(serverId).first() else 0
                val latest = if (serverId > 0) messageDao.observeLatestMessage(serverId).first() else null
                push(context, unread, latest?.title?.ifBlank { null } ?: latest?.message)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        fun push(context: Context, unread: Int, latest: String?) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, UnreadWidgetProvider::class.java))
            if (ids.isEmpty()) return

            val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
                ?.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP) ?: return
            val tap = PendingIntent.getActivity(
                context, 0, launch,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val lctx = context.currentLocalized()
            val views = RemoteViews(context.packageName, R.layout.widget_unread).apply {
                setTextViewText(R.id.widget_count, unread.toString())
                setTextViewText(
                    R.id.widget_label,
                    lctx.resources.getQuantityString(R.plurals.widget_unread, unread)
                )
                setTextViewText(R.id.widget_latest, latest ?: lctx.getString(R.string.widget_no_messages))
                setOnClickPendingIntent(R.id.widget_root, tap)
            }
            manager.updateAppWidget(ids, views)
        }
    }
}
