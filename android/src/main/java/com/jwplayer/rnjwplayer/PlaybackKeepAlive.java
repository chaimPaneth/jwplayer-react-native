package com.jwplayer.rnjwplayer;

import android.content.Context;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;

import com.jwplayer.rnjwplayer.utils.JWLog;

/**
 * Process-wide keep-alive locks for background streaming.
 *
 * <p>These were previously owned by {@code RNJWMediaSessionHelper} and released by every
 * {@code cleanup()} / {@code detachForTransfer()} call. That coupling is wrong: the helper is
 * recreated whenever ownership moves between the UI player and the headless player, and it is
 * torn down shortly after the app is backgrounded even though the JWPlayer instance keeps
 * playing. Dropping the high-performance Wi-Fi lock at that moment lets the radio enter
 * screen-off power save, so HLS segment loads start failing about a minute later and playback
 * dies with {@code UnknownHostException} once the pre-buffer drains -- observed as "audio stops
 * after ~2 minutes in the background".
 *
 * <p>Ownership therefore lives here, keyed to whether audio is really playing, and is driven by
 * player events rather than by MediaSession or service lifetime. A watchdog re-validates the
 * state so a missed "stopped" event can never pin the locks for the life of the process.
 */
public final class PlaybackKeepAlive {
    private static final String TAG = "PlaybackKeepAlive";
    private static final String WIFI_LOCK_TAG = "RNJWPlayer:wifi";
    private static final String WAKE_LOCK_TAG = "RNJWPlayer:wake";
    /** Re-validates that playback is still active so the locks cannot leak. */
    private static final long WATCHDOG_INTERVAL_MS = 60000L;

    private static final Object lock = new Object();
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());

    private static WifiManager.WifiLock wifiLock;
    private static PowerManager.WakeLock wakeLock;
    private static Context appContext;
    private static boolean playing;
    private static String lastReason = "none";

    private static final Runnable watchdog = new Runnable() {
        @Override
        public void run() {
            boolean stillPlaying = false;
            try {
                stillPlaying = PlaybackManager.getInstance().isActivePlayerPlaying();
            } catch (Throwable error) {
                JWLog.w(TAG, "watchdog: unable to read playback state: " + error.getMessage());
                // Unknown state: keep the locks rather than cutting a live stream.
                stillPlaying = true;
            }

            if (stillPlaying) {
                JWLog.d(TAG, "WATCHDOG_HOLD reason=" + lastReason);
                scheduleWatchdog();
            } else {
                JWLog.d(TAG, "WATCHDOG_RELEASE reason=" + lastReason);
                setPlaying(appContext, false, "watchdog-idle");
            }
        }
    };

    private PlaybackKeepAlive() {
    }

    /**
     * Acquires or releases the keep-alive locks.
     *
     * @param context any context; the application context is retained
     * @param isPlaying true while the player is playing or buffering
     * @param reason short marker used in release-visible logs
     */
    public static void setPlaying(Context context, boolean isPlaying, String reason) {
        synchronized (lock) {
            if (context != null && appContext == null) {
                appContext = context.getApplicationContext();
            }
            if (appContext == null) {
                return;
            }
            lastReason = reason == null ? "unspecified" : reason;

            if (isPlaying) {
                acquire();
                if (!playing) {
                    playing = true;
                    JWLog.d(TAG, "LOCKS_ACQUIRED reason=" + lastReason
                            + " wifiHeld=" + isWifiHeld() + " wakeHeld=" + isWakeHeld());
                }
                scheduleWatchdog();
            } else {
                mainHandler.removeCallbacks(watchdog);
                release();
                if (playing) {
                    playing = false;
                    JWLog.d(TAG, "LOCKS_RELEASED reason=" + lastReason);
                }
            }
        }
    }

    /**
     * Releases the locks unconditionally. Only for terminal process-level teardown; ordinary
     * owner transfer must not call this, because playback continues across it.
     */
    public static void releaseForShutdown(String reason) {
        synchronized (lock) {
            mainHandler.removeCallbacks(watchdog);
            release();
            playing = false;
            JWLog.d(TAG, "LOCKS_SHUTDOWN reason=" + (reason == null ? "unspecified" : reason));
        }
    }

    public static boolean isHoldingLocks() {
        synchronized (lock) {
            return isWifiHeld() || isWakeHeld();
        }
    }

    private static void scheduleWatchdog() {
        mainHandler.removeCallbacks(watchdog);
        mainHandler.postDelayed(watchdog, WATCHDOG_INTERVAL_MS);
    }

    @android.annotation.SuppressLint("WakelockTimeout")
    private static void acquire() {
        // High-performance Wi-Fi lock: prevents the DTIM power-save throttling that screen-off
        // imposes. WIFI_MODE_FULL_LOW_LATENCY is deliberately not used because it only engages
        // while the app is foreground with the screen on, which is the opposite of this case.
        if (wifiLock == null) {
            WifiManager wifiManager =
                    (WifiManager) appContext.getSystemService(Context.WIFI_SERVICE);
            if (wifiManager != null) {
                wifiLock = wifiManager.createWifiLock(
                        WifiManager.WIFI_MODE_FULL_HIGH_PERF, WIFI_LOCK_TAG);
                if (wifiLock != null) {
                    wifiLock.setReferenceCounted(false);
                }
            }
        }
        if (wifiLock != null && !wifiLock.isHeld()) {
            try {
                wifiLock.acquire();
            } catch (Exception error) {
                JWLog.w(TAG, "WifiLock acquire failed: " + error.getMessage());
            }
        }

        // Partial CPU wake lock keeps buffering/decoding alive during Doze. Held without a
        // timeout because a shiur can exceed an hour; the watchdog above bounds the risk.
        if (wakeLock == null) {
            PowerManager powerManager =
                    (PowerManager) appContext.getSystemService(Context.POWER_SERVICE);
            if (powerManager != null) {
                wakeLock = powerManager.newWakeLock(
                        PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG);
                if (wakeLock != null) {
                    wakeLock.setReferenceCounted(false);
                }
            }
        }
        if (wakeLock != null && !wakeLock.isHeld()) {
            try {
                wakeLock.acquire();
            } catch (Exception error) {
                JWLog.w(TAG, "WakeLock acquire failed: " + error.getMessage());
            }
        }
    }

    private static void release() {
        if (wifiLock != null && wifiLock.isHeld()) {
            try {
                wifiLock.release();
            } catch (Exception error) {
                JWLog.w(TAG, "WifiLock release failed: " + error.getMessage());
            }
        }
        if (wakeLock != null && wakeLock.isHeld()) {
            try {
                wakeLock.release();
            } catch (Exception error) {
                JWLog.w(TAG, "WakeLock release failed: " + error.getMessage());
            }
        }
    }

    private static boolean isWifiHeld() {
        return wifiLock != null && wifiLock.isHeld();
    }

    private static boolean isWakeHeld() {
        return wakeLock != null && wakeLock.isHeld();
    }
}
