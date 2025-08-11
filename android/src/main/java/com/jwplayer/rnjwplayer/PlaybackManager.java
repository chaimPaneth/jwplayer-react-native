package com.jwplayer.rnjwplayer;

import android.content.Context;
import android.util.Log;

import com.jwplayer.pub.api.JWPlayer;

import java.lang.reflect.Method;

public class PlaybackManager {
    private static PlaybackManager instance;
    private Object mActivePlayerHandler;
    private JWPlayer mActivePlayer;

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
        // Before setting a new player, ensure any existing player is stopped.
        stopAndCleanupCurrentPlayer();

        this.mActivePlayer = player;
        this.mActivePlayerHandler = handler;
    }

    public void clearPlayer(Object handler) {
        if (this.mActivePlayerHandler == handler) {
            Log.d(TAG, "Clearing active player for handler: " + handler.getClass().getSimpleName());
            this.mActivePlayer = null;
            this.mActivePlayerHandler = null;
        } else {
            Log.w(TAG, "A non-active handler tried to clear the player. Ignoring.");
        }
    }

    public void stopAndCleanupCurrentPlayer() {
        if (mActivePlayerHandler != null) {
            Log.d(TAG, "Stopping and cleaning up current active player from handler: " + mActivePlayerHandler.getClass().getSimpleName());
            try {
                // We still need a bit of reflection here, but it's centralized.
                // This assumes handlers (RNJWPlayerView, JWPlayerNativePlaybackHandler)
                // have a public `stopAndCleanup()` or `destroyPlayer()` method.
                Method cleanupMethod;
                if (mActivePlayerHandler instanceof JWPlayerNativePlaybackHandler) {
                    cleanupMethod = mActivePlayerHandler.getClass().getMethod("stopAndCleanup");
                } else if (mActivePlayerHandler instanceof RNJWPlayerView) {
                    cleanupMethod = mActivePlayerHandler.getClass().getMethod("destroyPlayer");
                } else {
                    Log.e(TAG, "Unknown handler type, cannot clean up: " + mActivePlayerHandler.getClass().getName());
                    return;
                }
                cleanupMethod.invoke(mActivePlayerHandler);
                Log.d(TAG, "Cleanup method invoked successfully.");
            } catch (Exception e) {
                Log.e(TAG, "Error trying to stop and cleanup the active player", e);
            } finally {
                // Ensure we clear the references even if cleanup fails.
                mActivePlayer = null;
                mActivePlayerHandler = null;
            }
        } else {
            Log.d(TAG, "No active player to clean up.");
        }
    }
}
