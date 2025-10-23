package com.jwplayer.rnjwplayer;

import android.os.Handler;
import android.os.Looper;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.ReadableArray;
import com.facebook.react.bridge.UIManager;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.WritableArray;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.uimanager.IllegalViewOperationException;
import com.facebook.react.uimanager.NativeViewHierarchyManager;
import com.facebook.react.uimanager.UIBlock;
import com.facebook.react.uimanager.UIManagerHelper;
import com.facebook.react.uimanager.UIManagerModule;
import com.facebook.react.uimanager.common.UIManagerType;
import com.jwplayer.pub.api.JWPlayer;
import com.jwplayer.pub.api.PlayerState;
import com.jwplayer.pub.api.configuration.PlayerConfig;
import com.jwplayer.pub.api.media.adaptive.QualityLevel;
import com.jwplayer.pub.api.media.audio.AudioTrack;
import com.jwplayer.rnjwplayer.utils.JWLog;
import java.util.List;
import java.util.Map;

import android.view.View;

public class RNJWPlayerModule extends ReactContextBaseJavaModule {

    private final ReactApplicationContext mReactContext;

    private static final String TAG = "RNJWPlayerModule";

    public RNJWPlayerModule(ReactApplicationContext reactContext) {
        super(reactContext);

        mReactContext = reactContext;
        JWLog.d(TAG, "RNJWPlayerModule() constructed");
    }

    @Override
    public String getName() {
        JWLog.d(TAG, "getName() -> " + TAG);
        return TAG;
    }

    private RNJWPlayerView getPlayerView(int reactTag) {
        int uiManagerType;
        if (BuildConfig.IS_NEW_ARCHITECTURE_ENABLED) {
            uiManagerType = UIManagerType.FABRIC;
        } else {
            uiManagerType = UIManagerType.DEFAULT;
        }
        try {
            UIManager uiManager = UIManagerHelper.getUIManager(mReactContext, uiManagerType);
            if (uiManager != null) {
                View view = uiManager.resolveView(reactTag);
                if (view instanceof RNJWPlayerView) {
                    return (RNJWPlayerView) view;
                }
            }
        } catch (IllegalViewOperationException e) {
            JWLog.e(TAG, "View with tag " + reactTag + " doesn't exist: " + e.getMessage(), e);
            JWLog.w(TAG, "‼️ Picture-in-Picture (PiP) may not be enabled in the host app. To use PiP, add these *activity attributes* (not permissions) to your **main <activity>** in AndroidManifest.xml:\n" +
                    "  android:supportsPictureInPicture=\"true\"\n" +
                    "  android:resizeableActivity=\"true\"\n" +
                    "  android:configChanges=\"orientation|screenSize|smallestScreenSize|screenLayout\"\n" +
                    "Example:\n" +
                    "  <activity\n" +
                    "      android:name=\".MainActivity\"\n" +
                    "      android:supportsPictureInPicture=\"true\"\n" +
                    "      android:resizeableActivity=\"true\"\n" +
                    "      android:configChanges=\"orientation|screenSize|smallestScreenSize|screenLayout\" />");
        } catch (Exception e) {
            JWLog.w(TAG, "Error getting player view: " + e.getMessage());
        }
        return null;
    }

    private JWPlayer getPlayerDirectly(int reactTag) {
        // First try normal view access
        RNJWPlayerView playerView = getPlayerView(reactTag);
        if (playerView != null && playerView.mPlayer != null) {
            return playerView.mPlayer;
        }
        
        // If view access fails, try to get the active UI player from PlaybackManager
        try {
            // Check if we have an active UI player (which works in PIP)
            Class<?> playbackManagerClass = Class.forName("com.jwplayer.rnjwplayer.PlaybackManager");
            java.lang.reflect.Method getInstanceMethod = playbackManagerClass.getMethod("getInstance");
            Object playbackManager = getInstanceMethod.invoke(null);
            
            if (playbackManager != null) {
                java.lang.reflect.Method isUIActiveMethod = playbackManagerClass.getMethod("isUIActive");
                Boolean isUIActive = (Boolean) isUIActiveMethod.invoke(playbackManager);
                
                if (isUIActive != null && isUIActive) {
                    java.lang.reflect.Method getActivePlayerMethod = playbackManagerClass.getMethod("getActivePlayerIfUI");
                    JWPlayer activePlayer = (JWPlayer) getActivePlayerMethod.invoke(playbackManager);
                    
                    if (activePlayer != null) {
                        // Log.d(TAG, "Got active UI player directly from PlaybackManager (likely PIP mode)");
                        return activePlayer;
                    }
                }
            }
        } catch (Exception e) {
            JWLog.w(TAG, "Could not access active UI player: " + e.getMessage());
        }
        
        return null;
    }

    @ReactMethod
    public void loadPlaylist(final int reactTag, final ReadableArray playlistItems) {
        JWLog.d(TAG, "loadPlaylist(reactTag=" + reactTag + ", itemsLength=" + (playlistItems != null ? playlistItems.size() : -1) + ")");
        new Handler(Looper.getMainLooper()).post(() -> {
            RNJWPlayerView playerView = getPlayerView(reactTag);
            if (playerView != null && playerView.mPlayerView != null) {
                JWPlayer player = playerView.mPlayerView.getPlayer();

                PlayerConfig oldConfig = player.getConfig();
                PlayerConfig config = new PlayerConfig.Builder()
                        .autostart(oldConfig.getAutostart())
                        .nextUpOffset(oldConfig.getNextUpOffset())
                        .repeat(oldConfig.getRepeat())
                        .relatedConfig(oldConfig.getRelatedConfig())
                        .displayDescription(oldConfig.getDisplayDescription())
                        .displayTitle(oldConfig.getDisplayTitle())
                        .advertisingConfig(oldConfig.getAdvertisingConfig())
                        .stretching(oldConfig.getStretching())
                        .uiConfig(oldConfig.getUiConfig())
                        .playlist(Util.createPlaylist(playlistItems))
                        .allowCrossProtocolRedirects(oldConfig.getAllowCrossProtocolRedirects())
                        .preload(oldConfig.getPreload())
                        .useTextureView(oldConfig.useTextureView())
                        .thumbnailPreview(oldConfig.getThumbnailPreview())
                        .mute(oldConfig.getMute())
                        .build();

                player.setup(config);
            }
        });
    }

    @ReactMethod
    public void loadPlaylistWithUrl(final int reactTag, final String playlistUrl) {
        JWLog.d(TAG, "loadPlaylistWithUrl(reactTag=" + reactTag + ", url=" + JWLog.safe(playlistUrl) + ")");
        new Handler(Looper.getMainLooper()).post(() -> {
            RNJWPlayerView playerView = getPlayerView(reactTag);
            if (playerView != null && playerView.mPlayerView != null) {
                JWPlayer player = playerView.mPlayerView.getPlayer();

                PlayerConfig oldConfig = player.getConfig();
                PlayerConfig config = new PlayerConfig.Builder()
                        .autostart(oldConfig.getAutostart())
                        .nextUpOffset(oldConfig.getNextUpOffset())
                        .repeat(oldConfig.getRepeat())
                        .relatedConfig(oldConfig.getRelatedConfig())
                        .displayDescription(oldConfig.getDisplayDescription())
                        .displayTitle(oldConfig.getDisplayTitle())
                        .advertisingConfig(oldConfig.getAdvertisingConfig())
                        .stretching(oldConfig.getStretching())
                        .uiConfig(oldConfig.getUiConfig())
                        .playlistUrl(playlistUrl)
                        .allowCrossProtocolRedirects(oldConfig.getAllowCrossProtocolRedirects())
                        .preload(oldConfig.getPreload())
                        .useTextureView(oldConfig.useTextureView())
                        .thumbnailPreview(oldConfig.getThumbnailPreview())
                        .mute(oldConfig.getMute())
                        .build();

                player.setup(config);
            }
        });
    }

    @ReactMethod
    public void play(final int reactTag) {
        JWLog.d(TAG, "play(reactTag=" + reactTag + ")");
        new Handler(Looper.getMainLooper()).post(() -> {
            JWPlayer player = getPlayerDirectly(reactTag);
            if (player != null) {
                try {
                    player.play();
                    return;
                } catch (Exception e) {
                    JWLog.w(TAG, "Error calling play on direct player: " + e.getMessage());
                }
            }
        });
    }

    @ReactMethod
    public void toggleSpeed(final int reactTag) {
        JWLog.d(TAG, "toggleSpeed(reactTag=" + reactTag + ")");
        new Handler(Looper.getMainLooper()).post(() -> {
            RNJWPlayerView playerView = getPlayerView(reactTag);
            if (playerView != null && playerView.mPlayerView != null) {
                double rate = playerView.mPlayerView.getPlayer().getPlaybackRate();
                if (rate < 2) {
                    playerView.mPlayerView.getPlayer().setPlaybackRate(rate += 0.5);
                } else {
                    playerView.mPlayerView.getPlayer().setPlaybackRate((float) 0.5);
                }
            }
        });
    }

    @ReactMethod
    public void togglePIP(final int reactTag) {
        JWLog.d(TAG, "togglePIP(reactTag=" + reactTag + ")");
        new Handler(Looper.getMainLooper()).post(() -> {
            RNJWPlayerView playerView = getPlayerView(reactTag);
            if (playerView != null && playerView.mPlayerView != null) {
                if (playerView.mPlayerView.getPlayer().isInPictureInPictureMode()) {
                    playerView.mPlayerView.getPlayer().exitPictureInPictureMode();
                } else {
                    playerView.mPlayerView.getPlayer().enterPictureInPictureMode();
                }
            }
        });
    }

    @ReactMethod
    public void setSpeed(final int reactTag, final float speed) {
        JWLog.d(TAG, "setSpeed(reactTag=" + reactTag + ", speed=" + speed + ")");
        new Handler(Looper.getMainLooper()).post(() -> {
            RNJWPlayerView playerView = getPlayerView(reactTag);
            if (playerView != null && playerView.mPlayerView != null) {
                playerView.mPlayerView.getPlayer().setPlaybackRate(speed);
            }
        });
    }

    @ReactMethod
    public void getCurrentQuality(final int reactTag, final Promise promise) {
        JWLog.d(TAG, "getCurrentQuality(reactTag=" + reactTag + ")");
        new Handler(Looper.getMainLooper()).post(() -> {
            RNJWPlayerView playerView = getPlayerView(reactTag);
            if (playerView != null && playerView.mPlayerView != null) {
                int quality = playerView.mPlayerView.getPlayer().getCurrentQuality();
                promise.resolve(quality);
            } else {
                promise.reject("RNJW Error", "getCurrentQuality() Player is null");
            }
        });
    }

    @ReactMethod
    public void setCurrentQuality(final int reactTag, final int index) {
        JWLog.d(TAG, "setCurrentQuality(reactTag=" + reactTag + ", index=" + index + ")");
        new Handler(Looper.getMainLooper()).post(() -> {
            RNJWPlayerView playerView = getPlayerView(reactTag);
            if (playerView != null && playerView.mPlayerView != null) {
                playerView.mPlayerView.getPlayer().setCurrentQuality(index);
            }
        });
    }

    @ReactMethod
    public void getQualityLevels(final int reactTag, final Promise promise) {
        JWLog.d(TAG, "getQualityLevels(reactTag=" + reactTag + ")");
        new Handler(Looper.getMainLooper()).post(() -> {
            RNJWPlayerView playerView = getPlayerView(reactTag);
            if (playerView != null && playerView.mPlayerView != null) {
                List<QualityLevel> qualityLevelsList = playerView.mPlayerView.getPlayer().getQualityLevels();
                if (qualityLevelsList == null) { //if qualitylevels are null than pass empty array.
                    promise.resolve(null);
                    return;
                }
                WritableArray qualityLevels = Arguments.createArray();
                for (int i = 0; i < qualityLevelsList.size(); i++) {
                    WritableMap qualityLevel = Arguments.createMap();
                    QualityLevel level = qualityLevelsList.get(i);
                    qualityLevel.putInt("playListPosition", level.getPlaylistPosition());
                    qualityLevel.putInt("bitRate", level.getBitrate());
                    qualityLevel.putString("label", level.getLabel());
                    qualityLevel.putInt("height", level.getHeight());
                    qualityLevel.putInt("width", level.getWidth());
                    qualityLevel.putInt("index", level.getTrackIndex());
                    qualityLevels.pushMap(qualityLevel);
                }
                promise.resolve(qualityLevels);
            } else {
                promise.reject("RNJW Error", "getQualityLevels Player is null");
            }
        });
    }

    @ReactMethod
    public void pause(final int reactTag) {
        JWLog.d(TAG, "pause(reactTag=" + reactTag + ")");
        new Handler(Looper.getMainLooper()).post(() -> {
            RNJWPlayerView playerView = getPlayerView(reactTag);
            if (playerView != null && playerView.mPlayerView != null) {
                if (!playerView.getIsCastActive()) {
                    playerView.mPlayerView.getPlayer().pause();
                    playerView.userPaused = true;
                }
            }
        });
    }

    @ReactMethod
    public void stop(final int reactTag) {
        JWLog.d(TAG, "stop(reactTag=" + reactTag + ")");
        new Handler(Looper.getMainLooper()).post(() -> {
            RNJWPlayerView playerView = getPlayerView(reactTag);
            if (playerView != null && playerView.mPlayerView != null) {
                if (!playerView.getIsCastActive()) {
                    playerView.mPlayerView.getPlayer().stop();
                    playerView.userPaused = true;
                }
            }
        });
    }

    @ReactMethod
    public void seekTo(final int reactTag, final double time) {
        JWLog.d(TAG, "seekTo(reactTag=" + reactTag + ", time=" + time + ")");
        new Handler(Looper.getMainLooper()).post(() -> {
            RNJWPlayerView playerView = getPlayerView(reactTag);
            if (playerView != null && playerView.mPlayerView != null) {
                playerView.mPlayerView.getPlayer().seek(time);
            }
        });
    }

    @ReactMethod
    public void setPlaylistIndex(final int reactTag, final int index) {
        JWLog.d(TAG, "setPlaylistIndex(reactTag=" + reactTag + ", index=" + index + ")");
        new Handler(Looper.getMainLooper()).post(() -> {
            RNJWPlayerView playerView = getPlayerView(reactTag);
            if (playerView != null && playerView.mPlayerView != null) {
                playerView.mPlayerView.getPlayer().playlistItem(index);
            }
        });
    }

    @ReactMethod
    public void setControls(final int reactTag, final boolean show) {
        JWLog.d(TAG, "setControls(reactTag=" + reactTag + ", show=" + show + ")");
        new Handler(Looper.getMainLooper()).post(() -> {
            RNJWPlayerView playerView = getPlayerView(reactTag);
            if (playerView != null && playerView.mPlayerView != null) {
                playerView.mPlayerView.getPlayer().setControls(show);
            }
        });
    }

    @ReactMethod
    public void position(final int reactTag, final Promise promise) {
        // JWLog.d(TAG, "position(reactTag=" + reactTag + ")");
        new Handler(Looper.getMainLooper()).post(() -> {
            JWPlayer player = getPlayerDirectly(reactTag);
            
            if (player != null) {
                try {
                    promise.resolve((Double.valueOf(player.getPosition()).intValue()));
                    return;
                } catch (Exception e) {
                    JWLog.w(TAG, "Error getting position from direct player: " + e.getMessage());
                }
            }
            
            // Fallback: return background (headless) player position if available
            try {
                JWPlayerNativePlaybackHandler handler = JWPlayerNativePlaybackHandler.getInstance(mReactContext);
                Map<String, Object> info = handler.getCurrentBackgroundPlayerInfo();
                if (info != null && info.get("position") instanceof Number) {
                    int seconds = ((Double) ((Number) info.get("position")).doubleValue()).intValue();
                    promise.resolve(seconds);
                    return;
                }
            } catch (Throwable ignore) {
                JWLog.w(TAG, "Error retrieving background player position: " + ignore.getMessage());
            }

            promise.reject("RNJW Error", "No accessible player found");
        });
    }

    @ReactMethod
    public void state(final int reactTag, final Promise promise) {
        JWLog.d(TAG, "state(reactTag=" + reactTag + ")");
        new Handler(Looper.getMainLooper()).post(() -> {
            JWPlayer player = getPlayerDirectly(reactTag);
            
            if (player != null) {
                try {
                    PlayerState playerState = player.getState();
                    promise.resolve(stateToInt(playerState));
                    return;
                } catch (Exception e) {
                    JWLog.w(TAG, "Error getting state from direct player: " + e.getMessage());
                }
            }
            
            // Fallback: return background (headless) player state if available
            try {
                JWPlayerNativePlaybackHandler handler = JWPlayerNativePlaybackHandler.getInstance(mReactContext);
                Map<String, Object> info = handler.getCurrentBackgroundPlayerInfo();
                JWLog.d(TAG, "Background player info: " + info + ", isPlaying: " + (info != null ? info.get("isPlaying") : "null"));
                if (info != null && info.get("isPlaying") instanceof Boolean) {
                    boolean isPlaying = (Boolean) info.get("isPlaying");
                    int state = isPlaying ? 2 : 3; // PLAYING=2, PAUSED=3
                    promise.resolve(state);
                    return;
                }
            } catch (Throwable ignore) {
                JWLog.w(TAG, "Error retrieving background player state: " + ignore.getMessage());
            }

            promise.reject("RNJW Error", "No accessible player found");
        });
    }

    @ReactMethod
    public void setFullscreen(final int reactTag, final boolean fullscreen) {
        JWLog.d(TAG, "setFullscreen(reactTag=" + reactTag + ", fullscreen=" + fullscreen + ")");
        new Handler(Looper.getMainLooper()).post(() -> {
            RNJWPlayerView playerView = getPlayerView(reactTag);
            if (playerView != null && playerView.mPlayerView != null) {
                playerView.mPlayerView.getPlayer().setFullscreen(fullscreen, fullscreen);
            }
        });
    }

    @ReactMethod
    public void setVolume(final int reactTag, final int volume) {
        JWLog.d(TAG, "setVolume(reactTag=" + reactTag + ", volume=" + volume + ")");
        new Handler(Looper.getMainLooper()).post(() -> {
            RNJWPlayerView playerView = getPlayerView(reactTag);
            if (playerView != null && playerView.mPlayerView != null) {
                playerView.mPlayerView.getPlayer().setVolume(volume);
            }
        });
    }

    @ReactMethod
    public void getAudioTracks(final int reactTag, final Promise promise) {
        JWLog.d(TAG, "getAudioTracks(reactTag=" + reactTag + ")");
        new Handler(Looper.getMainLooper()).post(() -> {
            RNJWPlayerView playerView = getPlayerView(reactTag);
            if (playerView != null && playerView.mPlayer != null) {
                List<AudioTrack> audioTrackList = playerView.mPlayer.getAudioTracks();
                WritableArray audioTracks = Arguments.createArray();
                if (audioTrackList != null) {
                    for (int i = 0; i < audioTrackList.size(); i++) {
                        WritableMap audioTrack = Arguments.createMap();
                        AudioTrack track = audioTrackList.get(i);
                        audioTrack.putString("name", track.getName());
                        audioTrack.putString("language", track.getLanguage());
                        audioTrack.putString("groupId", track.getGroupId());
                        audioTrack.putBoolean("defaultTrack", track.isDefaultTrack());
                        audioTrack.putBoolean("autoSelect", track.isAutoSelect());
                        audioTracks.pushMap(audioTrack);
                    }
                }
                promise.resolve(audioTracks);
            } else {
                promise.reject("RNJW Error", "getAudioTracks Player is null");
            }
        });
    }

    @ReactMethod
    public void getCurrentAudioTrack(final int reactTag, final Promise promise) {
        JWLog.d(TAG, "getCurrentAudioTrack(reactTag=" + reactTag + ")");
        new Handler(Looper.getMainLooper()).post(() -> {
            RNJWPlayerView playerView = getPlayerView(reactTag);
            if (playerView != null && playerView.mPlayer != null) {
                promise.resolve(playerView.mPlayer.getCurrentAudioTrack());
            } else {
                promise.reject("RNJW Error", "getCurrentAudioTrack Player is null");
            }
        });
    }

    @ReactMethod
    public void setCurrentAudioTrack(final int reactTag, final int index) {
        JWLog.d(TAG, "setCurrentAudioTrack(reactTag=" + reactTag + ", index=" + index + ")");
        new Handler(Looper.getMainLooper()).post(() -> {
            RNJWPlayerView playerView = getPlayerView(reactTag);
            if (playerView != null && playerView.mPlayer != null) {
                playerView.mPlayer.setCurrentAudioTrack(index);
            }
        });
    }

    @ReactMethod
    public void setCurrentCaptions(final int reactTag, final int index) {
        JWLog.d(TAG, "setCurrentCaptions(reactTag=" + reactTag + ", index=" + index + ")");
        new Handler(Looper.getMainLooper()).post(() -> {
            RNJWPlayerView playerView = getPlayerView(reactTag);
            if (playerView != null && playerView.mPlayer != null) {
                playerView.mPlayer.setCurrentCaptions(index);
            }
        });
    }

    @ReactMethod
    public void getCurrentCaptions(final int reactTag, final Promise promise) {
        JWLog.d(TAG, "getCurrentCaptions(reactTag=" + reactTag + ")");
        new Handler(Looper.getMainLooper()).post(() -> {
            RNJWPlayerView playerView = getPlayerView(reactTag);
            if (playerView != null && playerView.mPlayer != null) {
                promise.resolve(playerView.mPlayer.getCurrentCaptions());
            } else {
                promise.reject("RNJW Error", "getCurrentCaptions Player is null");
            }
        });
    }

    @ReactMethod
    public void resolveNextPlaylistItem(final int reactTag, final ReadableMap playlistItem) {
        JWLog.d(TAG, "resolveNextPlaylistItem(reactTag=" + reactTag + ", playlistItem=" + JWLog.safe(playlistItem) + ")");
        new Handler(Looper.getMainLooper()).post(() -> {
            RNJWPlayerView playerView = getPlayerView(reactTag);
            if (playerView != null && playerView.mPlayerView != null) {
                playerView.resolveNextPlaylistItem(playlistItem);
            }
        });
    }

    private int stateToInt(PlayerState playerState) {
        JWLog.d(TAG, "stateToInt(playerState=" + playerState + ")");
        switch (playerState) {
            case IDLE:
                return 0;
            case BUFFERING:
                return 1;
            case PLAYING:
                return 2;
            case PAUSED:
                return 3;
            case COMPLETE:
                return 4;
            case ERROR:
                return 5;
            default:
                return -1;
        }
    }

    /**
     * Check for active headless playback and get comprehensive state for app handoff
     * This method is designed for React Native app wake-up scenarios
     */
    @ReactMethod
    public void checkForActiveHeadlessPlayback(Promise promise) {
        JWLog.d(TAG, "checkForActiveHeadlessPlayback()");
        try {
            JWPlayerNativePlaybackHandler nativePlaybackHandler = JWPlayerNativePlaybackHandler.getInstance(mReactContext);
            WritableMap playbackState = nativePlaybackHandler.getComprehensivePlaybackState();
            if (playbackState != null) {
                JWLog.d(TAG, "📱 JAVA: Active headless playback detected for app handoff");
                promise.resolve(playbackState);
            } else {
                JWLog.d(TAG, "📱 JAVA: No active headless playback found");
                promise.resolve(null);
            }
        } catch (Exception e) {
            JWLog.e(TAG, "📱 JAVA: Error checking for headless playback", e);
            promise.reject("CHECK_HEADLESS_PLAYBACK_ERROR", "Failed to check headless playback state", e);
        }
    }

    /**
     * Get pending media info from headless mode for app restoration (legacy method)
     */
    @ReactMethod
    public void getPendingMediaInfo(Promise promise) {
        JWLog.d(TAG, "getPendingMediaInfo()");
        try {
            JWPlayerNativePlaybackHandler nativePlaybackHandler = JWPlayerNativePlaybackHandler.getInstance(mReactContext);
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
        JWLog.d(TAG, "clearPendingMedia()");
        try {
            JWPlayerNativePlaybackHandler nativePlaybackHandler = JWPlayerNativePlaybackHandler.getInstance(mReactContext);
            nativePlaybackHandler.clearPendingMedia();
            promise.resolve(true);
        } catch (Exception e) {
            promise.reject("CLEAR_PENDING_MEDIA_ERROR", "Failed to clear pending media", e);
        }
    }

}