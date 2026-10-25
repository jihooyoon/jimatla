package ji.minitvlauncher.system

import android.app.Notification
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** One row of the launcher's own notification panel. */
data class NotificationItem(
    val key: String,
    val appLabel: String,
    val postTime: Long,
    val title: String,
    val text: String,
    val clearable: Boolean,
)

/**
 * Mirrors the system's notifications for the bell badge and panel. Android TV launchers can only
 * read them through a notification listener, which the user (or adb) must allow:
 * `cmd notification allow_listener ji.minitvlauncher/ji.minitvlauncher.system.NotificationReader`.
 */
class NotificationReader : NotificationListenerService() {

    override fun onListenerConnected() {
        instance = this
        connected.value = true
        publish()
    }

    override fun onListenerDisconnected() {
        instance = null
        connected.value = false
        items.value = emptyList()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) = publish()
    override fun onNotificationRemoved(sbn: StatusBarNotification) = publish()

    private fun publish() {
        val pm = packageManager
        items.value = runCatching { activeNotifications }.getOrNull().orEmpty()
            .filter { it.packageName != packageName && !it.isOngoing && it.notification.flags and Notification.FLAG_GROUP_SUMMARY == 0 }
            .sortedByDescending { it.postTime }
            .map { sbn ->
                val extras = sbn.notification.extras
                NotificationItem(
                    key = sbn.key,
                    appLabel = runCatching { pm.getApplicationLabel(pm.getApplicationInfo(sbn.packageName, 0)).toString() }
                        .getOrDefault(sbn.packageName),
                    postTime = sbn.postTime,
                    title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty(),
                    text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty(),
                    clearable = sbn.isClearable,
                )
            }
    }

    companion object {
        private var instance: NotificationReader? = null
        private val connected = MutableStateFlow(false)
        private val items = MutableStateFlow(emptyList<NotificationItem>())

        /** Whether the listener runs, i.e. the user allowed notification access. */
        val isConnected: StateFlow<Boolean> = connected
        val notifications: StateFlow<List<NotificationItem>> = items

        fun hasAccess(context: Context): Boolean {
            val me = ComponentName(context, NotificationReader::class.java).flattenToString()
            return Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
                ?.split(':')?.contains(me) == true
        }

        /** Opens what the notification points to, removing it if it asks to be. */
        fun open(key: String) {
            val service = instance ?: return
            val sbn = runCatching { service.getActiveNotifications(arrayOf(key)) }.getOrNull()?.firstOrNull() ?: return
            runCatching { sbn.notification.contentIntent?.send() }.onFailure { if (it !is PendingIntent.CanceledException) throw it }
            if (sbn.notification.flags and Notification.FLAG_AUTO_CANCEL != 0) service.cancelNotification(key)
        }

        fun dismiss(key: String) {
            instance?.cancelNotification(key)
        }

        fun dismissAll() {
            instance?.cancelAllNotifications()
        }
    }
}
