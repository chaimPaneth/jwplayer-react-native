//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package com.jwplayer.rnjwplayer.session;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build.VERSION;
import android.support.v4.media.MediaDescriptionCompat;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaSessionCompat;

import androidx.core.app.NotificationCompat;

import com.jwplayer.rnjwplayer.misc.MediaServiceFactory;
import com.jwplayer.pub.api.background.ServiceMediaApi;
import com.jwplayer.rnjwplayer.misc.MediaSessionStateProvider;
import com.jwplayer.rnjwplayer.utils.JWLog;
import com.longtailvideo.jwplayer.R.drawable;

public class RNJWNotificationHelper extends MediaSessionCompat.Callback {
    public static final int DEFAULT_NOTIFICATION_ID = 2005;
    public static final String DEFAULT_CHANNEL_ID = "NotificationBarController";
    public static final String DEFAULT_CHANNEL_NAME = "Player Notification";
    public static final String DEFAULT_CHANNEL_DESCRIPTION = "Control playback of the media player";

    final NotificationManager notificationManager;
    private NotificationChannel notificationChannel;
    private final int smallIconResId;
    final int notificationId;
    private final String notificationChannelId;
    private final String channelName;
    private final String channelDescription;
    private final MediaServiceFactory mediaServiceFactory;

    protected RNJWNotificationHelper(NotificationManager notificationManager, int iconDrawableResource, int notificationId, String notificationChannelId, String channelNameDisplayedToUser, String channelDescription, MediaServiceFactory factory) {
        this.notificationManager = notificationManager;
        this.smallIconResId = iconDrawableResource;
        this.notificationId = notificationId;
        this.notificationChannelId = notificationChannelId;
        this.channelName = channelNameDisplayedToUser;
        this.channelDescription = channelDescription;
        this.mediaServiceFactory = factory;
        if (VERSION.SDK_INT >= 26) {
            String channelName = this.channelName;
            String channelId = this.notificationChannelId;
            this.notificationChannel = new NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_LOW);
            this.notificationChannel.setDescription(this.channelDescription);
            this.notificationChannel.setShowBadge(false);
            this.notificationChannel.setLockscreenVisibility(1);
            this.notificationManager.createNotificationChannel(this.notificationChannel);
        }

    }

    final Notification showNotification(Context context, MediaSessionStateProvider stateProvider, ServiceMediaApi serviceMediaApi) {
        // Safely extract description; metadata can be null during rapid transitions
        MediaDescriptionCompat description = null;
        try {
            if (stateProvider != null && stateProvider.mediaSessionCompat != null && stateProvider.mediaSessionCompat.getController() != null) {
                MediaMetadataCompat metadata = stateProvider.mediaSessionCompat.getController().getMetadata();
                if (metadata != null) {
                    description = metadata.getDescription();
                }
            }
        } catch (Throwable ignored) {}

        if (description == null) {
            // Build a placeholder to avoid crashes; values may be updated on next tick
            MediaDescriptionCompat.Builder builder = new MediaDescriptionCompat.Builder();
            builder.setTitle("Playing");
            builder.setSubtitle("");
            builder.setDescription("");
            description = builder.build();
        }
        String channelId = this.notificationChannelId;
        // The MediaStyle below dereferences the session for its token. A null session only happens
        // on a build whose media-browser integration is broken (see RNJWSharedMediaSession): the
        // library is present but its contract is unreachable. Skipping the notification is not an
        // option — the caller feeds this straight into startForeground(), and a media-playback FGS
        // without its notification loses foreground state and Android stops background playback.
        // Post the bootstrap notification instead: same id and channel, already what this service
        // posts before any owner attaches, so playback survives with placeholder controls.
        if (stateProvider == null || stateProvider.mediaSessionCompat == null) {
            JWLog.e("RNJWNotificationHelper", "showNotification: no media session available"
                    + " — posting bootstrap notification to keep the service foregrounded");
            return createBootstrapNotification(context);
        }
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, channelId);
        serviceMediaApi.addNotificationActions(context, builder);
        builder.setContentTitle(description.getTitle()).setContentText(description.getSubtitle()).setSubText(description.getDescription()).setLargeIcon(description.getIconBitmap()).setOnlyAlertOnce(true).setStyle((new androidx.media.app.NotificationCompat.MediaStyle()).setMediaSession(stateProvider.mediaSessionCompat.getSessionToken()).setShowActionsInCompactView(serviceMediaApi.getCompactActions())).setVisibility(NotificationCompat.VISIBILITY_PUBLIC).setSmallIcon(this.smallIconResId).setDeleteIntent(serviceMediaApi.getActionIntent(context, 86));
        Intent activityIntent = context.getPackageManager().getLaunchIntentForPackage(context.getPackageName());
        if (activityIntent != null) {
            activityIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            builder.setContentIntent(PendingIntent.getActivity(
                context,
                0,
                activityIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
        }
        Notification notification = builder.build();
        this.notificationManager.notify(this.notificationId, notification);
        // Forced to ERROR level deliberately: without it there is no way to tell from a capture
        // whether the MediaStyle notification was ever posted. The previous capture had zero
        // RNJWNotificationHelper lines. Only field reads inside the concatenation — no method
        // calls, which Java would evaluate regardless of the log level.
        JWLog.e("RNJWNotificationHelper", "notify(id=" + this.notificationId
                + ", channel=" + this.notificationChannelId + ", mediaStyle=true)");
        return notification;
    }

        /**
         * Creates the notification used while ownership is moving between UI and headless players.
         * It deliberately has no transport actions; the attached helper replaces it atomically once
         * the successor player is ready to own the shared MediaSession.
         */
        public static Notification createBootstrapNotification(Context context) {
        NotificationManager manager =
            (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (VERSION.SDK_INT >= 26 && manager != null) {
            NotificationChannel channel = new NotificationChannel(
                DEFAULT_CHANNEL_ID,
                DEFAULT_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW);
            channel.setDescription(DEFAULT_CHANNEL_DESCRIPTION);
            channel.setShowBadge(false);
            channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
            manager.createNotificationChannel(channel);
        }

        int appIcon = context.getResources().getIdentifier(
            "ic_app_icon", "drawable", context.getPackageName());
        int smallIcon = appIcon > 0 ? appIcon : drawable.ic_jw_play;
        NotificationCompat.Builder builder = new NotificationCompat.Builder(
            context, DEFAULT_CHANNEL_ID)
            .setContentTitle("Preparing playback")
            .setContentText("")
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setSmallIcon(smallIcon);

        Intent activityIntent = context.getPackageManager().getLaunchIntentForPackage(context.getPackageName());
        if (activityIntent != null) {
            activityIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            builder.setContentIntent(PendingIntent.getActivity(
                context,
                0,
                activityIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
        }
        // Forced to ERROR level deliberately. This method does not post; its only caller
        // (RNJWMediaService.showBootstrapNotification) immediately posts the result under
        // DEFAULT_NOTIFICATION_ID via startForeground. RNJWMediaService's promoteToForeground log
        // derives isMediaStyle from the id alone, so it reports isMediaStyle=true for this
        // deliberately actionless placeholder. This line is what lets the capture tell a real
        // MediaStyle notification apart from the placeholder on the same id. Field reads and
        // string literals only — no method calls inside the concatenation.
        JWLog.e("RNJWNotificationHelper", "createBootstrapNotification(id=" + DEFAULT_NOTIFICATION_ID
                + ", channel=" + DEFAULT_CHANNEL_ID + ", mediaStyle=false)");
        return builder.build();
        }

    public static class Builder {
        protected NotificationManager notificationManager;
        protected MediaServiceFactory mediaServiceFactory;
        protected int iconDrawableResource;
        protected int notificationId;
        protected String notificationChannelId;
        protected String channelName;
        protected String channelDescription;

        protected Context context;

        public Builder(Context context, NotificationManager notificationManager) {
            this(context, notificationManager, new MediaServiceFactory());
        }

        private Builder(Context context, NotificationManager manager, MediaServiceFactory factory) {
            this.context = context;
            int appIcon = this.context.getResources().getIdentifier("ic_app_icon", "drawable", this.context.getPackageName());

            this.iconDrawableResource = appIcon > 0 ? appIcon : drawable.ic_jw_play;

            this.notificationId = DEFAULT_NOTIFICATION_ID;
            this.notificationChannelId = DEFAULT_CHANNEL_ID;
            this.channelName = DEFAULT_CHANNEL_NAME;
            this.channelDescription = DEFAULT_CHANNEL_DESCRIPTION;
            this.notificationManager = manager;
            this.mediaServiceFactory = factory;
        }

        public Builder iconDrawableResource(int iconDrawableResource) {
            this.iconDrawableResource = iconDrawableResource;
            return this;
        }

        public Builder notificationId(int notificationId) {
            this.notificationId = notificationId;
            return this;
        }

        public Builder notificationChannelId(String notificationChannelId) {
            this.notificationChannelId = notificationChannelId;
            return this;
        }

        public Builder channelNameDisplayedToUser(String channelNameDisplayedToUser) {
            this.channelName = channelNameDisplayedToUser;
            return this;
        }

        public Builder channelDescription(String channelDescription) {
            this.channelDescription = channelDescription;
            return this;
        }

        public RNJWNotificationHelper build() {
            return new RNJWNotificationHelper(this.notificationManager, this.iconDrawableResource, this.notificationId, this.notificationChannelId, this.channelName, this.channelDescription, this.mediaServiceFactory);
        }
    }
}
