package com.mag.anyauto;

import android.service.notification.NotificationListenerService;

/**
 * Does nothing itself. Android only lets an app read the playing music (title, cover, buttons)
 * when it holds "Notification access", and that permission is a notification listener; the
 * dashboard's music card names this class to ask for the active media sessions.
 */
public final class MediaListener extends NotificationListenerService {
}
