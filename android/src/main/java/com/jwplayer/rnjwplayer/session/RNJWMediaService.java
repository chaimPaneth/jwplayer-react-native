//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package com.jwplayer.rnjwplayer.session;

import android.app.ActivityManager;
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
    private static final long FOREGROUND_VERIFY_INTERVAL_MS = 15000L;
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
    private boolean destroyed;
    private int foregroundNotificationId = RNJWNotificationHelper.DEFAULT_NOTIFICATION_ID;
    private Notification foregroundNotification;

    private final Runnable foregroundVerifier = new Runnable() {
        @Override
        public void run() {
            verifyForegroundState("periodic");
        }
    };

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
            JWLog.d(TAG, "START_REQUEST reason=" + reason);
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
        destroyed = false;
        JWLog.d(TAG, "ON_CREATE importance=" + currentProcessImportance());
    }

    private void promoteToForeground(int notificationId, Notification notification, String reason) {
        if (notification == null || destroyed) {
            return;
        }
        int serviceType = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
                ? MEDIA_PLAYBACK_SERVICE_TYPE
                : 0;
        ServiceCompat.startForeground(this, notificationId, notification, serviceType);
        foregroundNotificationId = notificationId;
        foregroundNotification = notification;
        foreground = true;
        JWLog.d(TAG, "FOREGROUND_PROMOTED reason=" + reason
                + " notificationId=" + notificationId
                + " requestedType=0x" + Integer.toHexString(serviceType)
                + " importance=" + currentProcessImportance());
        scheduleForegroundVerification();
    }

    private void showBootstrapNotification(String reason) {
        promoteToForeground(
                RNJWNotificationHelper.DEFAULT_NOTIFICATION_ID,
                RNJWNotificationHelper.createBootstrapNotification(this),
                "bootstrap:" + reason);
    }

    /**
     * Atomically publishes a new playback owner. The foreground promotion happens before
     * allowBackgroundAudio() or the controller's foreground-ready callback can touch audio.
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
                JWLog.w(TAG, "OWNER_REJECTED_STALE token=" + token + " type=" + type
                        + " generation=" + generation + " currentGeneration=" + mediaGeneration);
                return false;
            }
            if (ownerToken != null
                    && generation == mediaGeneration
                    && "ui".equals(ownerType)
                    && "headless".equals(type)) {
                JWLog.w(TAG, "OWNER_REJECTED_HEADLESS_AFTER_UI generation=" + generation);
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
            promoteToForeground(newNotificationHelper.notificationId, notification, "owner-attach");
            JWLog.d(TAG, "OWNER_ATTACHED token=" + token + " type=" + ownerType
                    + " generation=" + generation + " epoch=" + ownerEpoch);
        }

        if (previousHelper != null) {
            previousHelper.detachForTransfer();
        }
        try {
            // JW's SDK service is enabled only after our typed FGS is active. This preserves the
            // SDK's background mode without allowing it to race the Android 17 eligibility check.
            newServiceMediaApi.getPlayer().allowBackgroundAudio(true);
            JWLog.d(TAG, "SDK_BACKGROUND_AUDIO_ENABLED type=" + ownerType
                    + " generation=" + generation);
        } catch (Throwable error) {
            JWLog.w(TAG, "attachOwner: allowBackgroundAudio failed: " + error.getMessage());
        }
        verifyForegroundState("owner-attached");
        return true;
    }

    /** Detaches only the matching owner and keeps the started FGS alive for its successor. */
    public boolean prepareOwnerTransfer(String token, String reason) {
        RNJWMediaSessionHelper previousHelper;
        final long transferEpoch;
        synchronized (ownerLock) {
            if (token == null || !token.equals(ownerToken)) {
                JWLog.w(TAG, "TRANSFER_IGNORED_STALE token=" + token
                        + " current=" + ownerToken + " reason=" + reason);
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
            JWLog.d(TAG, "OWNER_TRANSFER token=" + token + " reason=" + reason
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
            JWLog.w(TAG, "OWNER_TRANSFER_TIMEOUT epoch=" + expectedEpoch);
        }
        stopForegroundAndSelf("transfer-timeout");
    }

    /** Performs a terminal stop only when the caller still owns the service token. */
    public boolean stopOwner(String token, String reason) {
        RNJWMediaSessionHelper previousHelper;
        ServiceMediaApi previousMediaApi;
        synchronized (ownerLock) {
            if (token == null || !token.equals(ownerToken)) {
                JWLog.w(TAG, "STOP_IGNORED_STALE token=" + token + " current=" + ownerToken
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
                    && foreground
                    && isActuallyForeground();
        }
    }

    private void stopForegroundAndSelf(String reason) {
        JWLog.d(TAG, "FOREGROUND_STOP reason=" + reason + " importance="
                + currentProcessImportance());
        mainHandler.removeCallbacks(foregroundVerifier);
        foreground = false;
        foregroundNotification = null;
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE);
        stopSelf();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String reason = intent != null ? intent.getStringExtra(EXTRA_START_REASON) : null;
        JWLog.d(TAG, "ON_START_COMMAND startId=" + startId + " flags=" + flags
                + " reason=" + reason + " owner=" + ownerType
                + " actualForeground=" + isActuallyForeground());

        synchronized (ownerLock) {
            if (ownerToken != null && foregroundNotification != null) {
                promoteToForeground(foregroundNotificationId, foregroundNotification, "start-command-owner");
            } else {
                showBootstrapNotification(reason == null ? "service-restart" : reason);
                final long bootstrapEpoch = ownerEpoch;
                mainHandler.postDelayed(
                        () -> stopIfStillUnowned(bootstrapEpoch),
                        OWNER_TRANSFER_TIMEOUT_MS);
            }
        }
        if (this.mediaSessionHelper != null) {
            MediaButtonReceiver.handleIntent(
                    this.mediaSessionHelper.mediaSessionStateProvider.mediaSessionCompat,
                    intent);
        }

        return Service.START_NOT_STICKY;
    }

    @Override
    @Nullable
    public IBinder onBind(Intent intent) {
        JWLog.d(TAG, "ON_BIND owner=" + ownerType + " actualForeground="
                + isActuallyForeground());
        return this.binder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        JWLog.d(TAG, "ON_UNBIND owner=" + ownerType + " actualForeground="
                + isActuallyForeground());
        return false;
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        boolean hasOwner;
        synchronized (ownerLock) {
            hasOwner = ownerToken != null && foregroundNotification != null;
            if (hasOwner) {
                promoteToForeground(
                        foregroundNotificationId,
                        foregroundNotification,
                        "task-removed-active-owner");
            }
        }
        JWLog.d(TAG, "ON_TASK_REMOVED hasOwner=" + hasOwner
                + " actualForeground=" + isActuallyForeground());
        super.onTaskRemoved(rootIntent);
    }

    private void scheduleForegroundVerification() {
        mainHandler.removeCallbacks(foregroundVerifier);
        if (!destroyed) {
            mainHandler.postDelayed(foregroundVerifier, FOREGROUND_VERIFY_INTERVAL_MS);
        }
    }

    private void verifyForegroundState(String reason) {
        Notification notification;
        int notificationId;
        String type;
        long generation;
        synchronized (ownerLock) {
            if (destroyed || ownerToken == null || foregroundNotification == null) {
                return;
            }
            notification = foregroundNotification;
            notificationId = foregroundNotificationId;
            type = ownerType;
            generation = mediaGeneration;
        }

        boolean actualForeground = isActuallyForeground();
        JWLog.d(TAG, "FOREGROUND_CHECK reason=" + reason
                + " actual=" + actualForeground
                + " local=" + foreground
                + " owner=" + type
                + " generation=" + generation
                + " importance=" + currentProcessImportance());
        if (!actualForeground) {
            // A bound service can survive after ActivityManager clears its started/foreground
            // state. Re-promote while the live playback owner is still attached so Android 17
            // never reaches the later process-freeze stage observed in the failing log.
            try {
                promoteToForeground(notificationId, notification, "repair:" + reason);
                JWLog.w(TAG, "FOREGROUND_REPAIRED owner=" + type
                        + " generation=" + generation);
            } catch (RuntimeException error) {
                foreground = false;
                JWLog.e(TAG, "Unable to repair foreground state", error);
            }
        } else {
            foreground = true;
            scheduleForegroundVerification();
        }
    }

    @SuppressWarnings("deprecation")
    private boolean isActuallyForeground() {
        ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        if (manager == null) {
            return foreground;
        }
        try {
            for (ActivityManager.RunningServiceInfo info
                    : manager.getRunningServices(Integer.MAX_VALUE)) {
                if (info.service != null
                        && getPackageName().equals(info.service.getPackageName())
                        && getClass().getName().equals(info.service.getClassName())) {
                    return info.foreground;
                }
            }
        } catch (RuntimeException error) {
            JWLog.w(TAG, "Unable to inspect foreground service state: " + error.getMessage());
        }
        return false;
    }

    private int currentProcessImportance() {
        ActivityManager.RunningAppProcessInfo processInfo =
                new ActivityManager.RunningAppProcessInfo();
        ActivityManager.getMyMemoryState(processInfo);
        return processInfo.importance;
    }

    @Override
    public void onDestroy() {
        destroyed = true;
        mainHandler.removeCallbacksAndMessages(null);
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
        foregroundNotification = null;
        if (activeInstance == this) {
            activeInstance = null;
        }
        JWLog.d(TAG, "ON_DESTROY importance=" + currentProcessImportance());
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
