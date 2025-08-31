//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package com.jwplayer.rnjwplayer.session;

import android.app.Notification;
import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import androidx.annotation.Nullable;
import androidx.media.session.MediaButtonReceiver;

import com.jwplayer.pub.api.background.ServiceMediaApi;

public class RNJWMediaService extends Service {
    protected final IBinder binder = new Binder();
    protected RNJWMediaSessionHelper mediaSessionHelper;

    public RNJWMediaService() {
    }

    public void doStartForeground(RNJWMediaSessionHelper mediaSessionHelper, RNJWNotificationHelper notificationHelper, ServiceMediaApi serviceMediaApi) {
        if (this.mediaSessionHelper != null) {
            this.mediaSessionHelper.cleanup();
        }

        this.mediaSessionHelper = mediaSessionHelper;
        Notification mediaSessionHelper1 = notificationHelper.showNotification(this.mediaSessionHelper.context, this.mediaSessionHelper.mediaSessionStateProvider, serviceMediaApi);
        this.startForeground(notificationHelper.notificationId, mediaSessionHelper1);
    }

    public int onStartCommand(Intent intent, int flags, int startId) {
        if (this.mediaSessionHelper != null) {
            MediaButtonReceiver.handleIntent(this.mediaSessionHelper.mediaSessionStateProvider.mediaSessionCompat, intent);
        }

        return Service.START_STICKY;
    }

    @Nullable
    public IBinder onBind(Intent intent) {
        return this.binder;
    }

    public boolean onUnbind(Intent intent) {
        if (this.mediaSessionHelper != null) {
            this.mediaSessionHelper.cleanup();
        }

        this.stopForeground(true);
        this.stopSelf();
        return false;
    }

    public void onDestroy() {
        if (this.mediaSessionHelper != null) {
            this.mediaSessionHelper.cleanup();
        }

    }

    public class Binder extends android.os.Binder {
        public Binder() {
        }

        public RNJWMediaService getService() {
            return RNJWMediaService.this;
        }
    }
}
