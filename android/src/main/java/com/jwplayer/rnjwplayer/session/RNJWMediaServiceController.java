//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package com.jwplayer.rnjwplayer.session;

import android.app.NotificationManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.jwplayer.rnjwplayer.misc.MediaServiceFactory;
import com.jwplayer.pub.api.JWPlayer;
import com.jwplayer.pub.api.background.ServiceMediaApi;

public class RNJWMediaServiceController implements ServiceConnection {
    protected RNJWMediaService rnjwMediaService;
    protected AppCompatActivity appCompatActivity;
    protected RNJWNotificationHelper rnjwNotificationHelper;
    protected RNJWMediaSessionHelper rnjwMediaSessionHelper;
    protected ServiceMediaApi serviceMediaApi;
    protected Class<? extends RNJWMediaService> mediaServiceClass;
    private MediaServiceFactory mediaServiceFactory;

    protected RNJWMediaServiceController(AppCompatActivity activity, RNJWNotificationHelper notificationHelper, RNJWMediaSessionHelper mediaSessionHelper, ServiceMediaApi serviceMediaApi, Class<? extends RNJWMediaService> mediaServiceClass, MediaServiceFactory mediaServiceFactory) {
        this.appCompatActivity = activity;
        this.rnjwNotificationHelper = notificationHelper;
        this.rnjwMediaSessionHelper = mediaSessionHelper;
        this.serviceMediaApi = serviceMediaApi;
        this.mediaServiceClass = mediaServiceClass;
        this.mediaServiceFactory = mediaServiceFactory;
    }

    public void updateServiceMediaApi(@NonNull ServiceMediaApi serviceMediaApi) {
        if (serviceMediaApi != null) {
            serviceMediaApi.getPlayer().allowBackgroundAudio(true);
            this.serviceMediaApi = serviceMediaApi;
            this.rnjwMediaSessionHelper.setupServiceMediaApi(serviceMediaApi);
        }

    }

    public void bindService() {
        if (this.rnjwMediaService == null) {
            Class<? extends RNJWMediaService> serviceClass = this.mediaServiceClass;
            AppCompatActivity activity = this.appCompatActivity;
            this.appCompatActivity.bindService(new Intent(activity, serviceClass), this, Context.BIND_AUTO_CREATE);
        }
    }

    public void unbindService() {
        if (this.rnjwMediaService != null) {
            this.serviceMediaApi.getPlayer().allowBackgroundAudio(false);
            this.appCompatActivity.unbindService(this);
            this.rnjwMediaService = null;
        }

        this.appCompatActivity = null;
    }

    public void onServiceConnected(ComponentName name, IBinder service) {
        this.rnjwMediaService = ((RNJWMediaService.Binder)service).getService();
        this.rnjwMediaService.doStartForeground(this.rnjwMediaSessionHelper, this.rnjwNotificationHelper, this.serviceMediaApi);
        this.serviceMediaApi.getPlayer().allowBackgroundAudio(true);
    }

    public void onServiceDisconnected(ComponentName name) {
        this.rnjwMediaService = null;
    }

    public static class Builder {
        protected AppCompatActivity compatActivity;
        protected RNJWNotificationHelper notificationHelper;
        protected RNJWMediaSessionHelper mediaSessionHelper;
        protected ServiceMediaApi mediaApi;
        protected Class<? extends RNJWMediaService> mediaServiceClass;
        protected MediaServiceFactory mediaServiceFactory;

        public Builder(AppCompatActivity activity, JWPlayer player) {
            this(activity, player, new MediaServiceFactory());
        }

        private Builder(AppCompatActivity activity, JWPlayer player, MediaServiceFactory factory) {
            this.compatActivity = activity;
            this.mediaServiceFactory = factory;
            this.notificationHelper = (new RNJWNotificationHelper.Builder(this.compatActivity, (NotificationManager)activity.getSystemService(Context.NOTIFICATION_SERVICE))).build();
            this.mediaApi = new ServiceMediaApi(player);
            this.mediaSessionHelper = new RNJWMediaSessionHelper(activity, this.notificationHelper, this.mediaApi);
            this.mediaServiceClass = RNJWMediaService.class;
        }

        public Builder notificationHelper(RNJWNotificationHelper notificationHelper) {
            this.notificationHelper = notificationHelper;
            return this;
        }

        public Builder serviceMediaApi(ServiceMediaApi serviceMediaApi) {
            this.mediaApi = serviceMediaApi;
            return this;
        }

        public Builder mediaSessionHelper(RNJWMediaSessionHelper mediaSessionHelper) {
            this.mediaSessionHelper = mediaSessionHelper;
            return this;
        }

        public RNJWMediaServiceController build() {
            return new RNJWMediaServiceController(this.compatActivity, this.notificationHelper, this.mediaSessionHelper, this.mediaApi, this.mediaServiceClass, this.mediaServiceFactory);
        }
    }
}
