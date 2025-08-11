package com.jwplayer.rnjwplayer;

import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.WritableMap;

import javax.annotation.Nonnull;

/**
 * React Native module to handle headless JWPlayer functionality
 */
public class RNJWPlayerHeadlessModule extends ReactContextBaseJavaModule {
    
    private final JWPlayerNativePlaybackHandler nativePlaybackHandler;
    
    public RNJWPlayerHeadlessModule(ReactApplicationContext reactContext) {
        super(reactContext);
        this.nativePlaybackHandler = JWPlayerNativePlaybackHandler.getInstance(reactContext);
    }

    @Nonnull
    @Override
    public String getName() {
        return "RNJWPlayerHeadless";
    }

    /**
     * Get pending media info from headless mode for app restoration
     */
    @ReactMethod
    public void getPendingMediaInfo(Promise promise) {
        try {
            WritableMap pendingMedia = nativePlaybackHandler.getPendingMediaInfo();
            promise.resolve(pendingMedia);
        } catch (Exception e) {
            promise.reject("GET_PENDING_MEDIA_ERROR", "Failed to get pending media info", e);
        }
    }

    /**
     * Clear pending media info after handling
     */
    @ReactMethod
    public void clearPendingMedia(Promise promise) {
        try {
            nativePlaybackHandler.clearPendingMedia();
            promise.resolve(true);
        } catch (Exception e) {
            promise.reject("CLEAR_PENDING_MEDIA_ERROR", "Failed to clear pending media", e);
        }
    }

    /**
     * Check if there's pending media from headless mode
     */
    @ReactMethod
    public void hasPendingMedia(Promise promise) {
        try {
            boolean hasPending = GlobalPlayingInfoManager.getInstance(getReactApplicationContext()).hasPendingMedia();
            promise.resolve(hasPending);
        } catch (Exception e) {
            promise.reject("CHECK_PENDING_MEDIA_ERROR", "Failed to check pending media", e);
        }
    }
}
