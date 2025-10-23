package com.jwplayer.rnjwplayer;

import android.content.Context;

import com.jwplayer.pub.api.JWPlayer;
import com.jwplayer.rnjwplayer.utils.JWLog;

import java.lang.reflect.Method;

public class PlaybackManager {
    private static PlaybackManager instance;
    private Object mActivePlayerHandler;
    private JWPlayer mActivePlayer;
    private final Object mutex = new Object();
    private boolean isTransitioning = false;
    // Track whether the UI player (RNJWPlayerView) is in background
    private volatile boolean uiInBackground = false;

    private static final String TAG = "PlaybackManager";

    private PlaybackManager() {}

    public static synchronized PlaybackManager getInstance() {
        // JWLog.d(TAG, "getInstance()"); // IGNORE
        if (instance == null) {
            instance = new PlaybackManager();
        }
        return instance;
    }

    public void setActivePlayer(JWPlayer player, Object handler) {
        JWLog.d(TAG, "setActivePlayer(player=" + JWLog.id(player) + ", handler=" + (handler != null ? handler.getClass().getSimpleName() : "null") + ")");
        // Ensure any existing player is fully stopped and we are idle before setting a new one
        stopAndCleanupCurrentPlayer();
        synchronized (mutex) {
            this.mActivePlayer = player;
            this.mActivePlayerHandler = handler;
            // Reset UI background flag when a new handler is set; RNJWPlayerView will update it via lifecycle callbacks
            if (!(handler instanceof RNJWPlayerView)) {
                uiInBackground = false;
            }
        }
    }

    public void clearPlayer(Object handler) {
        JWLog.d(TAG, "clearPlayer(handler=" + (handler != null ? handler.getClass().getSimpleName() : "null") + ")");
        synchronized (mutex) {
            if (this.mActivePlayerHandler == handler) {
                JWLog.d(TAG, "Clearing active player for handler: " + handler.getClass().getSimpleName());
                this.mActivePlayer = null;
                this.mActivePlayerHandler = null;
            } else {
                JWLog.w(TAG, "A non-active handler tried to clear the player. Ignoring.");
            }
        }
    }

    public void stopAndCleanupCurrentPlayer() {
        JWLog.d(TAG, "stopAndCleanupCurrentPlayer()");
        Object handlerToCleanup = null;
        synchronized (mutex) {
            if (mActivePlayerHandler != null) {
                // Mark transitioning and detach current references before invoking external cleanup
                isTransitioning = true;
                handlerToCleanup = mActivePlayerHandler;
                mActivePlayer = null;
                mActivePlayerHandler = null;
                uiInBackground = false;
            } else {
                JWLog.d(TAG, "No active player to clean up.");
            }
        }

        if (handlerToCleanup != null) {
            try {
                // Centralized reflection call to cleanup on the handler instance
                Method cleanupMethod;
                if (handlerToCleanup instanceof JWPlayerNativePlaybackHandler) {
                    cleanupMethod = handlerToCleanup.getClass().getMethod("stopAndCleanup");
                } else if (handlerToCleanup instanceof RNJWPlayerView) {
                    cleanupMethod = handlerToCleanup.getClass().getMethod("destroyPlayer");
                } else {
                    JWLog.e(TAG, "Unknown handler type, cannot clean up: " + handlerToCleanup.getClass().getName());
                    return;
                }
                cleanupMethod.invoke(handlerToCleanup);
                JWLog.d(TAG, "Cleanup method invoked successfully.");
            } catch (Exception e) {
                JWLog.e(TAG, "Error trying to stop and cleanup the active player", e);
            } finally {
                synchronized (mutex) {
                    isTransitioning = false;
                    mutex.notifyAll();
                }
            }
        }
    }

    /**
     * Block the caller until the manager is idle (not in the middle of cleaning up)
     * or until the timeout elapses.
     */
    public void waitForIdle(long timeoutMs) {
        JWLog.d(TAG, "waitForIdle(timeoutMs=" + timeoutMs + ")");
        long deadline = System.currentTimeMillis() + Math.max(0, timeoutMs);
        synchronized (mutex) {
            while (isTransitioning) {
                long now = System.currentTimeMillis();
                long remaining = deadline - now;
                if (remaining <= 0) break;
                try {
                    mutex.wait(remaining);
                } catch (InterruptedException ignored) {}
            }
        }
    }

    /**
     * Returns true if the currently active handler is the UI player view.
     * Useful for deciding whether to create a headless/background player.
     */
    public boolean isUIActive() {
        synchronized (mutex) {
            boolean active = mActivePlayerHandler instanceof RNJWPlayerView && !uiInBackground;
            // JWLog.d(TAG, "isUIActive() -> " + active + " (uiInBackground=" + uiInBackground + ")");
            return active;
        }
    }

    /**
     * Returns true if any player is currently registered as active.
     */
    public boolean hasActivePlayer() {
        // JWLog.d(TAG, "hasActivePlayer() -> " + (mActivePlayer != null));
        synchronized (mutex) {
            return mActivePlayer != null;
        }
    }

    /**
     * For logging/debugging: name of the current active handler class, or "none".
     */
    public String getActiveHandlerName() {
        JWLog.d(TAG, "getActiveHandlerName()");
        synchronized (mutex) {
            return mActivePlayerHandler != null ? mActivePlayerHandler.getClass().getSimpleName() : "none";
        }
    }

    /**
     * Returns the active JWPlayer instance only if the active handler is the UI player.
     * Otherwise returns null.
     */
    public JWPlayer getActivePlayerIfUI() {
        JWLog.d(TAG, "getActivePlayerIfUI()");
        synchronized (mutex) {
            if (mActivePlayerHandler instanceof RNJWPlayerView && !uiInBackground) {
                return mActivePlayer;
            }
            return null;
        }
    }

    /**
     * Notify the manager that the UI player's host (activity) went background/foreground.
     * When in background, the UI player should not be considered active for routing.
     */
    public void setUiInBackground(boolean inBackground) {
        this.uiInBackground = inBackground;
        JWLog.d(TAG, "setUiInBackground(" + inBackground + ")");
    }
}
