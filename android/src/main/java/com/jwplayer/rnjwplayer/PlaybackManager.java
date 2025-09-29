package com.jwplayer.rnjwplayer;

import android.content.Context;
import android.util.Log;

import com.jwplayer.pub.api.JWPlayer;

import java.lang.reflect.Method;

public class PlaybackManager {
    private static PlaybackManager instance;
    private Object mActivePlayerHandler;
    private JWPlayer mActivePlayer;
    private final Object mutex = new Object();
    private boolean isTransitioning = false;

    private static final String TAG = "PlaybackManager";

    private PlaybackManager() {}

    public static synchronized PlaybackManager getInstance() {
        if (instance == null) {
            instance = new PlaybackManager();
        }
        return instance;
    }

    public void setActivePlayer(JWPlayer player, Object handler) {
        Log.d(TAG, "Setting active player. Handler: " + (handler != null ? handler.getClass().getSimpleName() : "null"));
        // Ensure any existing player is fully stopped and we are idle before setting a new one
        stopAndCleanupCurrentPlayer();
        synchronized (mutex) {
            this.mActivePlayer = player;
            this.mActivePlayerHandler = handler;
        }
    }

    public void clearPlayer(Object handler) {
        synchronized (mutex) {
            if (this.mActivePlayerHandler == handler) {
                Log.d(TAG, "Clearing active player for handler: " + handler.getClass().getSimpleName());
                this.mActivePlayer = null;
                this.mActivePlayerHandler = null;
            } else {
                Log.w(TAG, "A non-active handler tried to clear the player. Ignoring.");
            }
        }
    }

    public void stopAndCleanupCurrentPlayer() {
        Object handlerToCleanup = null;
        synchronized (mutex) {
            if (mActivePlayerHandler != null) {
                // Mark transitioning and detach current references before invoking external cleanup
                isTransitioning = true;
                handlerToCleanup = mActivePlayerHandler;
                mActivePlayer = null;
                mActivePlayerHandler = null;
            } else {
                Log.d(TAG, "No active player to clean up.");
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
                    Log.e(TAG, "Unknown handler type, cannot clean up: " + handlerToCleanup.getClass().getName());
                    return;
                }
                cleanupMethod.invoke(handlerToCleanup);
                Log.d(TAG, "Cleanup method invoked successfully.");
            } catch (Exception e) {
                Log.e(TAG, "Error trying to stop and cleanup the active player", e);
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
            return mActivePlayerHandler instanceof RNJWPlayerView;
        }
    }

    /**
     * Returns true if any player is currently registered as active.
     */
    public boolean hasActivePlayer() {
        synchronized (mutex) {
            return mActivePlayer != null;
        }
    }

    /**
     * For logging/debugging: name of the current active handler class, or "none".
     */
    public String getActiveHandlerName() {
        synchronized (mutex) {
            return mActivePlayerHandler != null ? mActivePlayerHandler.getClass().getSimpleName() : "none";
        }
    }

    /**
     * Returns the active JWPlayer instance only if the active handler is the UI player.
     * Otherwise returns null.
     */
    public JWPlayer getActivePlayerIfUI() {
        synchronized (mutex) {
            if (mActivePlayerHandler instanceof RNJWPlayerView) {
                return mActivePlayer;
            }
            return null;
        }
    }
}
