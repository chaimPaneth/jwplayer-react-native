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
import android.support.v4.media.session.MediaSessionCompat;

import androidx.core.app.NotificationCompat;

import com.jwplayer.rnjwplayer.misc.MediaServiceFactory;
import com.jwplayer.pub.api.background.ServiceMediaApi;
import com.jwplayer.rnjwplayer.misc.MediaSessionStateProvider;
import com.longtailvideo.jwplayer.R.drawable;

public class RNJWNotificationHelper extends MediaSessionCompat.Callback {
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
        MediaDescriptionCompat description = stateProvider.mediaSessionCompat.getController().getMetadata().getDescription();
        String channelId = this.notificationChannelId;
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, channelId);
        serviceMediaApi.addNotificationActions(context, builder);
        builder.setContentTitle(description.getTitle()).setContentText(description.getSubtitle()).setSubText(description.getDescription()).setLargeIcon(description.getIconBitmap()).setOnlyAlertOnce(true).setStyle((new androidx.media.app.NotificationCompat.MediaStyle()).setMediaSession(stateProvider.mediaSessionCompat.getSessionToken()).setShowActionsInCompactView(serviceMediaApi.getCompactActions())).setVisibility(NotificationCompat.VISIBILITY_PUBLIC).setSmallIcon(this.smallIconResId).setDeleteIntent(serviceMediaApi.getActionIntent(context, 86));
        Intent activityIntent;
        (activityIntent = new Intent(context, context.getClass())).setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        builder.setContentIntent(PendingIntent.getActivity(context, 0, activityIntent, PendingIntent.FLAG_IMMUTABLE));
        Notification notification = builder.build();
        this.notificationManager.notify(this.notificationId, notification);
        return notification;
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

            this.notificationId = 2005;
            this.notificationChannelId = "NotificationBarController";
            this.channelName = "Player Notification";
            this.channelDescription = "Control playback of the media player";
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
