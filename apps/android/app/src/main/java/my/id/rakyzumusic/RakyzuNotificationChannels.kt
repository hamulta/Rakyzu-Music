package my.id.rakyzumusic

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build

object RakyzuNotificationChannels {
    const val RELEASES = "rakyzu_new_releases"
    const val ACCOUNT = "rakyzu_account"

    fun create(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannels(
            listOf(
                NotificationChannel(
                    RELEASES,
                    "New music releases",
                    NotificationManager.IMPORTANCE_DEFAULT,
                ).apply {
                    description = "Releases from Artists you choose to follow on Rakyzu Music"
                    setShowBadge(true)
                },
                NotificationChannel(
                    ACCOUNT,
                    "Account and security",
                    NotificationManager.IMPORTANCE_HIGH,
                ).apply {
                    description = "Important account and security information"
                    setShowBadge(false)
                },
            ),
        )
    }
}
