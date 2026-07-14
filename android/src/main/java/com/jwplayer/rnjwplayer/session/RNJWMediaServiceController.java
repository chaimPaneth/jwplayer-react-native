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
import com.jwplayer.rnjwplayer.utils.JWLog;

import java.util.UUID;

public class RNJWMediaServiceController implements ServiceConnection {
    private static final String TAG = "RNJWMediaServiceController";

    protected RNJWMediaService rnjwMediaService;
    protected Context appContext;
    protected RNJWNotificationHelper rnjwNotificationHelper;
    protected RNJWMediaSessionHelper rnjwMediaSessionHelper;
    protected ServiceMediaApi serviceMediaApi;
    protected Class<? extends RNJWMediaService> mediaServiceClass;
    private MediaServiceFactory mediaServiceFactory;
    private final String ownerToken;
    private final String ownerType;
    private final long mediaGeneration;
    private boolean bound;
    private boolean released;

    protected RNJWMediaServiceController(Context context, RNJWNotificationHelper notificationHelper, RNJWMediaSessionHelper mediaSessionHelper, ServiceMediaApi serviceMediaApi, Class<? extends RNJWMediaService> mediaServiceClass, MediaServiceFactory mediaServiceFactory, String ownerType, long mediaGeneration) {
        this.appContext = context.getApplicationContext();
        this.rnjwNotificationHelper = notificationHelper;
        this.rnjwMediaSessionHelper = mediaSessionHelper;
        this.serviceMediaApi = serviceMediaApi;
        this.mediaServiceClass = mediaServiceClass;
        this.mediaServiceFactory = mediaServiceFactory;
        this.ownerToken = UUID.randomUUID().toString();
        this.ownerType = ownerType == null ? "ui" : ownerType;
        this.mediaGeneration = mediaGeneration;
    }

    public void updateServiceMediaApi(@NonNull ServiceMediaApi serviceMediaApi) {
        if (serviceMediaApi != null) {
            serviceMediaApi.getPlayer().allowBackgroundAudio(true);
            this.serviceMediaApi = serviceMediaApi;
            this.rnjwMediaSessionHelper.setupServiceMediaApi(serviceMediaApi);
        }

    }

    public void bindService() {
        if (!bound && !released) {
            Class<? extends RNJWMediaService> serviceClass = this.mediaServiceClass;
            if (!RNJWMediaService.ensureStarted(
                    appContext,
                    ownerType + "-owner-" + mediaGeneration)) {
                return;
            }
            try {
                bound = appContext.bindService(
                        new Intent(appContext, serviceClass),
                        this,
                        Context.BIND_AUTO_CREATE);
                JWLog.d(TAG, "bindService token=" + ownerToken + " type=" + ownerType
                        + " generation=" + mediaGeneration + " bound=" + bound);
            } catch (RuntimeException error) {
                bound = false;
                JWLog.e(TAG, "bindService failed for token=" + ownerToken, error);
            }
        }
    }

    public void unbindService() {
        if (bound) {
            try {
                appContext.unbindService(this);
            } catch (IllegalArgumentException error) {
                JWLog.w(TAG, "unbindService ignored: " + error.getMessage());
            }
            bound = false;
        }
        this.rnjwMediaService = null;
    }

    public void prepareForTransfer(String reason) {
        released = true;
        try {
            this.serviceMediaApi.getPlayer().allowBackgroundAudio(false);
        } catch (Throwable ignored) {}
        RNJWMediaService service = rnjwMediaService;
        boolean handled = service != null
                ? service.prepareOwnerTransfer(ownerToken, reason)
                : RNJWMediaService.prepareActiveOwnerTransfer(ownerToken, reason);
        JWLog.d(TAG, "prepareForTransfer token=" + ownerToken + " handled=" + handled
                + " reason=" + reason);
        unbindService();
    }

    public void stopAndUnbind(String reason) {
        released = true;
        try {
            this.serviceMediaApi.getPlayer().allowBackgroundAudio(false);
        } catch (Throwable ignored) {}
        RNJWMediaService service = rnjwMediaService;
        boolean handled = service != null
                ? service.stopOwner(ownerToken, reason)
                : RNJWMediaService.stopActiveOwner(ownerToken, reason);
        JWLog.d(TAG, "stopAndUnbind token=" + ownerToken + " handled=" + handled
                + " reason=" + reason);
        unbindService();
    }

    public void onServiceConnected(ComponentName name, IBinder service) {
        this.rnjwMediaService = ((RNJWMediaService.Binder)service).getService();
        if (released) {
            JWLog.w(TAG, "Ignoring delayed service connection for released token=" + ownerToken);
            unbindService();
            return;
        }
        boolean attached = this.rnjwMediaService.attachOwner(
                ownerToken,
                ownerType,
                mediaGeneration,
                this.rnjwMediaSessionHelper,
                this.rnjwNotificationHelper,
                this.serviceMediaApi);
        JWLog.d(TAG, "onServiceConnected token=" + ownerToken + " attached=" + attached);
    }

    public void onServiceDisconnected(ComponentName name) {
        this.rnjwMediaService = null;
        this.bound = false;
    }

    public RNJWMediaSessionHelper getMediaSessionHelper() {
        return rnjwMediaSessionHelper;
    }

    public RNJWNotificationHelper getNotificationHelper() {
        return rnjwNotificationHelper;
    }

    public static class Builder {
        protected Context context;
        protected RNJWNotificationHelper notificationHelper;
        protected RNJWMediaSessionHelper mediaSessionHelper;
        protected ServiceMediaApi mediaApi;
        protected Class<? extends RNJWMediaService> mediaServiceClass;
        protected MediaServiceFactory mediaServiceFactory;
        protected String ownerType = "ui";
        protected long mediaGeneration = 0L;

        public Builder(AppCompatActivity activity, JWPlayer player) {
            this((Context) activity, player, new MediaServiceFactory());
        }

        public Builder(Context context, JWPlayer player) {
            this(context, player, new MediaServiceFactory());
        }

        private Builder(Context context, JWPlayer player, MediaServiceFactory factory) {
            this.context = context;
            this.mediaServiceFactory = factory;
            this.notificationHelper = (new RNJWNotificationHelper.Builder(
                    context,
                    (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE))).build();
            this.mediaApi = new ServiceMediaApi(player);
            this.mediaServiceClass = RNJWMediaService.class;
        }

        public Builder owner(String ownerType, long mediaGeneration) {
            this.ownerType = ownerType;
            this.mediaGeneration = mediaGeneration;
            return this;
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
            if (this.mediaSessionHelper == null) {
                this.mediaSessionHelper = new RNJWMediaSessionHelper(
                        this.context,
                        this.notificationHelper,
                        this.mediaApi);
            }
            return new RNJWMediaServiceController(
                    this.context,
                    this.notificationHelper,
                    this.mediaSessionHelper,
                    this.mediaApi,
                    this.mediaServiceClass,
                    this.mediaServiceFactory,
                    this.ownerType,
                    this.mediaGeneration);
        }
    }
}
