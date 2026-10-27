package com.mustangos.launcher.media

import android.service.notification.NotificationListenerService
import kotlinx.coroutines.flow.MutableStateFlow

// Android requires this service for authorized MediaSession discovery. No notification contents are read.
class MediaAccessService : NotificationListenerService() {
    override fun onListenerConnected() { revision.value += 1 }
    override fun onListenerDisconnected() { revision.value += 1 }
    companion object { val revision = MutableStateFlow(0) }
}
