package com.jwplayer.rnjwplayer;

import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.WritableMap;
import com.jwplayer.rnjwplayer.utils.JWLog;

import javax.annotation.Nonnull;

/**
 * React Native module to handle headless JWPlayer functionality
 */
public class RNJWPlayerHeadlessModule extends ReactContextBaseJavaModule {
    private static final String TAG = "RNJWPlayerHeadlessModule";
    
    private final JWPlayerNativePlaybackHandler nativePlaybackHandler;
    
    public RNJWPlayerHeadlessModule(ReactApplicationContext reactContext) {
        super(reactContext);
        JWLog.d(TAG, "RNJWPlayerHeadlessModule::<init>(reactContext=" + reactContext + ")");
        this.nativePlaybackHandler = JWPlayerNativePlaybackHandler.getInstance(reactContext);
    }

    @Nonnull
    @Override
    public String getName() {
        JWLog.d(TAG, "getName()");
        return "RNJWPlayerHeadless";
    }

    /**
     * Get pending media info from headless mode for app restoration
     */
    @ReactMethod
    public void getPendingMediaInfo(Promise promise) {
        try {
            JWLog.d(TAG, "getPendingMediaInfo()");
            WritableMap pendingMedia = nativePlaybackHandler.getPendingMediaInfo();
            JWLog.d(TAG, "getPendingMediaInfo -> hasPending=" + (pendingMedia != null));
            promise.resolve(pendingMedia);
        } catch (Exception e) {
            JWLog.e(TAG, "getPendingMediaInfo error", e);
            promise.reject("GET_PENDING_MEDIA_ERROR", "Failed to get pending media info", e);
        }
    }

    /**
     * Clear pending media info after handling
     */
    @ReactMethod
    public void clearPendingMedia(Promise promise) {
        try {
            JWLog.d(TAG, "clearPendingMedia()");
            nativePlaybackHandler.clearPendingMedia();
            JWLog.d(TAG, "clearPendingMedia -> true");
            promise.resolve(true);
        } catch (Exception e) {
            JWLog.e(TAG, "clearPendingMedia error", e);
            promise.reject("CLEAR_PENDING_MEDIA_ERROR", "Failed to clear pending media", e);
        }
    }

    /**
     * Check if there's pending media from headless mode
     */
    @ReactMethod
    public void hasPendingMedia(Promise promise) {
        try {
            JWLog.d(TAG, "hasPendingMedia()");
            boolean hasPending = GlobalPlayingInfoManager.getInstance(getReactApplicationContext()).hasPendingMedia();
            JWLog.d(TAG, "hasPendingMedia -> " + hasPending);
            promise.resolve(hasPending);
        } catch (Exception e) {
            JWLog.e(TAG, "hasPendingMedia error", e);
            promise.reject("CHECK_PENDING_MEDIA_ERROR", "Failed to check pending media", e);
        }
    }
}
