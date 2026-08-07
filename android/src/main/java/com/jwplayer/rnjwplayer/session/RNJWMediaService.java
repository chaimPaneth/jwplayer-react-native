//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package com.jwplayer.rnjwplayer.session;

import android.app.Notification;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import androidx.annotation.Nullable;
import androidx.core.app.ServiceCompat;
import androidx.media.session.MediaButtonReceiver;

import com.jwplayer.pub.api.background.ServiceMediaApi;
import com.jwplayer.rnjwplayer.utils.JWLog;

public class RNJWMediaService extends Service {
    private static final String TAG = "RNJWMediaService";
    private static final String EXTRA_START_REASON = "rnjw_start_reason";
    private static final long OWNER_TRANSFER_TIMEOUT_MS = 60000L;
    private static final int MEDIA_PLAYBACK_SERVICE_TYPE = 0x00000002;
    private static volatile RNJWMediaService activeInstance;

    protected final IBinder binder = new Binder();
    private final Object ownerLock = new Object();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    protected RNJWMediaSessionHelper mediaSessionHelper;
    private RNJWNotificationHelper notificationHelper;
    private ServiceMediaApi serviceMediaApi;
    private String ownerToken;
    private String ownerType;
    private long mediaGeneration;
    private long ownerEpoch;
    private boolean foreground;

    public RNJWMediaService() {
    }

    /** Starts the mediaPlayback service while the caller is still eligible to do so. */
    public static boolean ensureStarted(Context context, String reason) {
        if (context == null) {
            return false;
        }
        Context appContext = context.getApplicationContext();
        Intent intent = new Intent(appContext, RNJWMediaService.class);
        intent.putExtra(EXTRA_START_REASON, reason == null ? "unspecified" : reason);
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                appContext.startForegroundService(intent);
            } else {
                appContext.startService(intent);
            }
            return true;
        } catch (RuntimeException error) {
            JWLog.e(TAG, "Unable to start playback foreground service; reason=" + reason, error);
            return false;
        }
    }

    public static boolean isUiOwnerAttached(long expectedGeneration) {
        RNJWMediaService service = activeInstance;
        return service != null && service.hasOwner("ui", expectedGeneration);
    }

    public static boolean prepareActiveOwnerTransfer(String token, String reason) {
        RNJWMediaService service = activeInstance;
        return service != null && service.prepareOwnerTransfer(token, reason);
    }

    public static boolean stopActiveOwner(String token, String reason) {
        RNJWMediaService service = activeInstance;
        return service != null && service.stopOwner(token, reason);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        activeInstance = this;
        JWLog.d(TAG, "onCreate()");
    }

    /**
     * Promotes to a mediaPlayback foreground service using the id the notification was actually
     * posted under. The owner's RNJWNotificationHelper can be built with a custom notificationId,
     * in which case it posts under that id while this service previously always promoted with
     * DEFAULT_NOTIFICATION_ID. That mismatch leaves the foreground service bound to an id nothing
     * updates, so the platform can drop the foreground state and the visible notification is not
     * the one keeping the service alive. Behaviour is unchanged when the default id is used.
     */
    private void promoteToForeground(int notificationId, Notification notification) {
        int serviceType = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
                ? MEDIA_PLAYBACK_SERVICE_TYPE
                : 0;
        ServiceCompat.startForeground(
                this,
                notificationId,
                notification,
                serviceType);
        foreground = true;
        // Forced to ERROR level deliberately: this call site produced zero lines in the analysed
        // capture even at MODE=ALL, and it must stay visible at MODE=ERROR for the next capture.
        // The isMediaStyle expression is a plain int compare — no probe work inside the
        // concatenation, which Java would evaluate regardless of the log level.
        JWLog.e(TAG, "promoteToForeground(id=" + notificationId
                + ", isMediaStyle=" + (notificationId == RNJWNotificationHelper.DEFAULT_NOTIFICATION_ID)
                + ")");
        notifyMediaBrowserOfOwnNotification();
    }

    /**
     * Tells com.mediabrowser.MediaBrowserService (when present) that JWPlayer now owns the
     * user-visible media notification, so it can drop its plain cold-start placeholder. Reflection
     * keeps this module free of a compile-time dependency on the media-browser lib.
     */
    private void notifyMediaBrowserOfOwnNotification() {
        try {
            Class<?> serviceClass = Class.forName("com.mediabrowser.MediaBrowserService");
            serviceClass.getMethod("onJwPlayerNotificationPosted").invoke(null);
        } catch (ClassNotFoundException ignored) {
            // Media-browser lib not installed — nothing to notify.
        } catch (Throwable error) {
            JWLog.w(TAG, "notifyMediaBrowserOfOwnNotification failed: " + error.getMessage());
        }
    }

    private void showBootstrapNotification(String reason) {
        promoteToForeground(
                RNJWNotificationHelper.DEFAULT_NOTIFICATION_ID,
                RNJWNotificationHelper.createBootstrapNotification(this));
        JWLog.d(TAG, "foreground bootstrap active; reason=" + reason);
    }

    /**
     * Atomically publishes a new playback owner. Cleanup from the previous owner happens only
     * after the token swap, so a stale destroy callback can never stop the successor.
     */
    public boolean attachOwner(
            String token,
            String type,
            long generation,
            RNJWMediaSessionHelper newMediaSessionHelper,
            RNJWNotificationHelper newNotificationHelper,
            ServiceMediaApi newServiceMediaApi) {
        if (token == null || newMediaSessionHelper == null
                || newNotificationHelper == null || newServiceMediaApi == null) {
            return false;
        }

        RNJWMediaSessionHelper previousHelper;
        synchronized (ownerLock) {
            if (ownerToken != null && generation < mediaGeneration) {
                JWLog.w(TAG, "Rejecting stale owner token=" + token + " type=" + type
                        + " generation=" + generation + " currentGeneration=" + mediaGeneration);
                return false;
            }
            if (ownerToken != null
                    && generation == mediaGeneration
                    && "ui".equals(ownerType)
                    && "headless".equals(type)) {
                JWLog.w(TAG, "Rejecting same-generation headless owner after UI attachment"
                        + " generation=" + generation);
                return false;
            }
            previousHelper = mediaSessionHelper == newMediaSessionHelper
                    ? null
                    : mediaSessionHelper;
            ownerToken = token;
            ownerType = type == null ? "unknown" : type;
            mediaGeneration = generation;
            mediaSessionHelper = newMediaSessionHelper;
            notificationHelper = newNotificationHelper;
            serviceMediaApi = newServiceMediaApi;
            ownerEpoch += 1L;

            Notification notification = newNotificationHelper.showNotification(
                    this,
                    newMediaSessionHelper.mediaSessionStateProvider,
                    newServiceMediaApi);
            promoteToForeground(newNotificationHelper.notificationId, notification);
            JWLog.d(TAG, "attachOwner token=" + token + " type=" + ownerType
                    + " generation=" + generation + " epoch=" + ownerEpoch);
        }

        if (previousHelper != null) {
            previousHelper.detachForTransfer();
        }
        try {
            newServiceMediaApi.getPlayer().allowBackgroundAudio(true);
        } catch (Throwable error) {
            JWLog.w(TAG, "attachOwner: allowBackgroundAudio failed: " + error.getMessage());
        }
        return true;
    }

    /** Detaches only the matching owner and keeps the started FGS alive for its successor. */
    public boolean prepareOwnerTransfer(String token, String reason) {
        RNJWMediaSessionHelper previousHelper;
        final long transferEpoch;
        synchronized (ownerLock) {
            if (token == null || !token.equals(ownerToken)) {
                JWLog.w(TAG, "Ignoring stale transfer token=" + token + " current=" + ownerToken);
                return false;
            }
            showBootstrapNotification("transfer:" + reason);
            previousHelper = mediaSessionHelper;
            mediaSessionHelper = null;
            notificationHelper = null;
            serviceMediaApi = null;
            ownerToken = null;
            ownerType = null;
            mediaGeneration = 0L;
            ownerEpoch += 1L;
            transferEpoch = ownerEpoch;
            JWLog.d(TAG, "prepareOwnerTransfer token=" + token + " reason=" + reason
                    + " epoch=" + transferEpoch);
        }

        if (previousHelper != null) {
            previousHelper.detachForTransfer();
        }

        mainHandler.postDelayed(() -> stopIfStillUnowned(transferEpoch), OWNER_TRANSFER_TIMEOUT_MS);
        return true;
    }

    private void stopIfStillUnowned(long expectedEpoch) {
        synchronized (ownerLock) {
            if (ownerEpoch != expectedEpoch || ownerToken != null) {
                return;
            }
            JWLog.w(TAG, "No playback owner arrived before transfer timeout; epoch=" + expectedEpoch);
        }
        stopForegroundAndSelf("transfer-timeout");
    }

    /** Performs a terminal stop only when the caller still owns the service token. */
    public boolean stopOwner(String token, String reason) {
        RNJWMediaSessionHelper previousHelper;
        ServiceMediaApi previousMediaApi;
        synchronized (ownerLock) {
            if (token == null || !token.equals(ownerToken)) {
                JWLog.w(TAG, "Ignoring stale stop token=" + token + " current=" + ownerToken
                        + " reason=" + reason);
                return false;
            }
            previousHelper = mediaSessionHelper;
            previousMediaApi = serviceMediaApi;
            mediaSessionHelper = null;
            notificationHelper = null;
            serviceMediaApi = null;
            ownerToken = null;
            ownerType = null;
            mediaGeneration = 0L;
            ownerEpoch += 1L;
        }

        try {
            if (previousMediaApi != null) {
                previousMediaApi.getPlayer().allowBackgroundAudio(false);
            }
        } catch (Throwable error) {
            JWLog.w(TAG, "stopOwner: allowBackgroundAudio failed: " + error.getMessage());
        }
        if (previousHelper != null) {
            previousHelper.cleanup();
        }
        stopForegroundAndSelf(reason);
        return true;
    }

    private boolean hasOwner(String expectedType, long expectedGeneration) {
        synchronized (ownerLock) {
            return ownerToken != null
                    && expectedType.equals(ownerType)
                    && mediaGeneration == expectedGeneration
                    && foreground;
        }
    }

    private void stopForegroundAndSelf(String reason) {
        // Forced to ERROR level deliberately: stopForeground(true) below REMOVES notification 2005,
        // so at MODE=ERROR the MediaStyle notification could otherwise vanish with no log line at
        // all. Fires at most a few times per session (transfer-timeout, owner detach), so it does
        // not spam. Message unchanged from the previous JWLog.d form.
        JWLog.e(TAG, "stopForegroundAndSelf reason=" + reason);
        foreground = false;
        stopForeground(true);
        stopSelf();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String reason = intent != null ? intent.getStringExtra(EXTRA_START_REASON) : null;
        if (!foreground) {
            showBootstrapNotification(reason == null ? "service-restart" : reason);
        }
        synchronized (ownerLock) {
            if (ownerToken == null) {
                final long bootstrapEpoch = ownerEpoch;
                mainHandler.postDelayed(
                        () -> stopIfStillUnowned(bootstrapEpoch),
                        OWNER_TRANSFER_TIMEOUT_MS);
            }
        }
        if (this.mediaSessionHelper != null) {
            MediaButtonReceiver.handleIntent(this.mediaSessionHelper.mediaSessionStateProvider.mediaSessionCompat, intent);
        }

        return Service.START_NOT_STICKY;
    }

    @Override
    @Nullable
    public IBinder onBind(Intent intent) {
        return this.binder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        JWLog.d(TAG, "onUnbind(): binding removed; started playback lifetime retained");
        return false;
    }

    @Override
    public void onDestroy() {
        RNJWMediaSessionHelper helper;
        synchronized (ownerLock) {
            helper = mediaSessionHelper;
            mediaSessionHelper = null;
            notificationHelper = null;
            serviceMediaApi = null;
            ownerToken = null;
            ownerType = null;
            mediaGeneration = 0L;
            ownerEpoch += 1L;
        }
        if (helper != null) {
            helper.cleanup();
        }
        foreground = false;
        if (activeInstance == this) {
            activeInstance = null;
        }
        JWLog.d(TAG, "onDestroy()");
        super.onDestroy();
    }

    public class Binder extends android.os.Binder {
        public Binder() {
        }

        public RNJWMediaService getService() {
            return RNJWMediaService.this;
        }
    }
}
