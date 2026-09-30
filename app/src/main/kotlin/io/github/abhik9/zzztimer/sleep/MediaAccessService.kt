package io.github.abhik9.zzztimer.sleep

import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.os.Build.VERSION.SDK_INT
import android.os.Build.VERSION_CODES.O_MR1
import android.service.notification.NotificationListenerService
import androidx.core.app.NotificationManagerCompat

/**
 * Only there to be enabled by the user (Notification access): an enabled notification listener may control the media
 * sessions of other apps, see [SessionPlayingMedia]. Notifications are never read: it unbinds itself as soon as the
 * system binds it, so that it isn't woken up by every notification. Being enabled is enough.
 */
class MediaAccessService : NotificationListenerService() {

    companion object {
        fun component(context: Context) = ComponentName(context, MediaAccessService::class.java)

        /** Whether the user granted Notification access, which lets ZzzTimer rewind the playing media. */
        fun isGranted(context: Context): Boolean = if (SDK_INT >= O_MR1) {
            context.getSystemService(NotificationManager::class.java).isNotificationListenerAccessGranted(component(context))
        } else {
            context.packageName in NotificationManagerCompat.getEnabledListenerPackages(context)
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        requestUnbind()
    }
}
