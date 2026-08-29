package com.jian.audioRouter

import android.service.notification.NotificationListenerService

/**
 * Enabled notification listener used only as the authorization bridge that lets
 * MediaSessionManager expose other apps' active media sessions to AudioRouter.
 */
class MediaNotificationListener : NotificationListenerService()
