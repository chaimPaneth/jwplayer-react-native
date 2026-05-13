//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package com.jwplayer.rnjwplayer.session;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import androidx.core.content.ContextCompat;
import android.graphics.Bitmap;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.AudioFocusRequest;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.net.NetworkRequest;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.support.v4.media.MediaBrowserCompat;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.MediaControllerCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.view.KeyEvent;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.Collections;

import com.jwplayer.rnjwplayer.JWPlayerNativePlaybackHandler;
import com.jwplayer.rnjwplayer.PlaybackManager;
import com.jwplayer.rnjwplayer.misc.MediaServiceFactory;
import com.jwplayer.rnjwplayer.misc.MediaSessionStateProvider;
import com.jwplayer.rnjwplayer.misc.PlaybackStateCompatWrapper;
import com.jwplayer.rnjwplayer.session.RNJWNotificationHelper;
import com.jwplayer.pub.api.JWPlayer;
import com.jwplayer.pub.api.PlayerState;
import com.jwplayer.pub.api.background.ServiceMediaApi;
import com.jwplayer.pub.api.configuration.PlayerConfig;
import com.jwplayer.pub.api.events.AdCompleteEvent;
import com.jwplayer.pub.api.events.AdErrorEvent;
import com.jwplayer.pub.api.events.AdPlayEvent;
import com.jwplayer.pub.api.events.AdSkippedEvent;
import com.jwplayer.pub.api.events.BufferEvent;
import com.jwplayer.pub.api.events.ErrorEvent;
import com.jwplayer.pub.api.events.EventType;
import com.jwplayer.pub.api.events.PauseEvent;
import com.jwplayer.pub.api.events.PlayEvent;
import com.jwplayer.pub.api.events.PlaylistCompleteEvent;
import com.jwplayer.pub.api.events.PlaylistItemEvent;
import com.jwplayer.pub.api.media.playlists.MediaSource;
import com.jwplayer.pub.api.events.SeekEvent;
import com.jwplayer.pub.api.events.SeekedEvent;
import com.jwplayer.pub.api.events.listeners.AdvertisingEvents;
import com.jwplayer.pub.api.events.listeners.VideoPlayerEvents;
import com.jwplayer.pub.api.media.playlists.PlaylistItem;
import com.mediabrowser.MediaSessionSingleton;
import com.jwplayer.rnjwplayer.utils.JWLog;

import org.json.JSONException;
import org.json.JSONObject;

public class RNJWMediaSessionHelper implements AdvertisingEvents.OnAdCompleteListener, AdvertisingEvents.OnAdErrorListener, AdvertisingEvents.OnAdPlayListener, AdvertisingEvents.OnAdSkippedListener, VideoPlayerEvents.OnBufferListener, VideoPlayerEvents.OnErrorListener, VideoPlayerEvents.OnPauseListener, VideoPlayerEvents.OnPlayListener, VideoPlayerEvents.OnPlaylistCompleteListener, VideoPlayerEvents.OnPlaylistItemListener, VideoPlayerEvents.OnSeekListener, VideoPlayerEvents.OnSeekedListener {
    private static final String TAG = "RNJWMediaSessionHelper";

    private static final java.util.regex.Pattern MANIFEST_PATTERN = java.util.regex.Pattern.compile(".*/manifests/([^/?]+)\\.m3u8(?:\\?.*)?$", java.util.regex.Pattern.CASE_INSENSITIVE);
    private static final java.util.regex.Pattern HLS_PATTERN = java.util.regex.Pattern.compile(".*/(\\d+)/hls/.*", java.util.regex.Pattern.CASE_INSENSITIVE);
    private static final java.util.regex.Pattern APP_POST_MEDIA_ID_PATTERN = java.util.regex.Pattern.compile("^(post-)?\\d+$", java.util.regex.Pattern.CASE_INSENSITIVE);

    private JWPlayer jwPlayer;
    MediaSessionStateProvider mediaSessionStateProvider;
    private ServiceMediaApi serviceMediaApi;
    private final RNJWNotificationHelper rnjwNotificationHelper;
    final Context context;
    private final MediaServiceFactory mediaServiceFactory;

    private BroadcastReceiver mediaButtonFallbackReceiver;
    private final ExecutorService artworkExecutor = Executors.newSingleThreadExecutor();

    // Audio focus management
    private AudioManager audioManager;
    private AudioFocusRequest audioFocusRequest;
    private AudioManager.OnAudioFocusChangeListener legacyFocusChangeListener;
    private boolean wasPlayingBeforeFocusLoss = false;
    private boolean currentlyHasFocus = false;
    private boolean isPlayingFromAndroidAuto = false;
    private long lastFocusRequestTime = 0;
    private long androidAutoHandoffStartTime = 0;
    private static final long FOCUS_LOSS_IGNORE_WINDOW_MS = 1000; // 1 second
    private static final long ANDROID_AUTO_HANDOFF_TIMEOUT_MS = 5000; // 5 seconds max
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    // Flight Mode / network recovery state. Local downloaded media must bypass this guard.
    private ConnectivityManager connectivityManager;
    private ConnectivityManager.NetworkCallback networkCallback;
    private Boolean networkAvailable = null;
    private boolean currentMediaRequiresNetwork = true;
    private boolean pausedByUser = false;
    private boolean pausedByNetwork = false;
    private boolean pendingResumeAfterNetworkRecovery = false;
    private boolean playBlockedByNetwork = false;
    private boolean systemPauseInProgress = false;
    private boolean recoveryNeedsPlayerReload = false;
    private boolean networkRecoveryInProgress = false;
    private boolean lastPlaybackWasActive = false;
    private int networkRecoveryReloadAttempts = 0;
    private long lastKnownRealPlaybackPositionMs = 0L;

    // Static reference to track the active instance for delegation from MediaBrowserService
    private static RNJWMediaSessionHelper activeInstance = null;

    // Playback speed set via Android Auto custom action (persists across instance recreation)
    public static float currentSpeed = 1.0f;

    // Pending seek info
    private static Long pendingSeekMs = null;
    private static boolean pendingSeekApplied = false;
    private static int autoHandoffSeekAttempts = 0; // Track number of onSeeked during handoff
    private static final long SEEK_END_GUARD_MS = 500L;
    private static final long USER_PLAYBACK_GESTURE_WINDOW_MS = 1500L;
    private static volatile long lastUserPlaybackGestureMs = 0L;

    private static String externalMediaId = null;
    private static String externalSubtitle = null;

    // Stores the postId-format mediaId originally selected from Android Auto
    // (e.g. "175936" or "post-175936"). Unlike `externalMediaId`, this is NOT
    // overwritten by JW playlist callbacks (onPlaylistItem / storeSeekPosition)
    // which legitimately track the JW Player media ID (e.g. "J56s1rL4").
    // Used when emitting Next/Previous events so the JS HeadlessTaskHandler can
    // resolve the correct postId via SeriesNavigationService.
    private static String androidAutoSelectedMediaId = null;

    public static void noteUserPlaybackGesture(String reason) {
        lastUserPlaybackGestureMs = SystemClock.elapsedRealtime();
        JWLog.d(TAG, "USER_INTENT: playback gesture reason=" + reason
                + " activeInstance=" + (activeInstance != null));
    }

    private static boolean hasRecentUserPlaybackGesture() {
        long lastGestureMs = lastUserPlaybackGestureMs;
        return lastGestureMs > 0L
                && SystemClock.elapsedRealtime() - lastGestureMs <= USER_PLAYBACK_GESTURE_WINDOW_MS;
    }

    private static boolean isAppPostMediaId(String mediaId) {
        return mediaId != null && APP_POST_MEDIA_ID_PATTERN.matcher(mediaId.trim()).matches();
    }

    private void refreshAndroidAutoSelectedMediaIdFromPlaylist(String reason) {
        String inferredMediaId = inferMediaIdFromPlaylistItem(jwPlayer != null ? jwPlayer.getPlaylistItem() : null);
        if (!isAppPostMediaId(inferredMediaId)) {
            return;
        }

        String normalizedMediaId = inferredMediaId.trim();
        androidAutoSelectedMediaId = normalizedMediaId;
        externalMediaId = normalizedMediaId;
    }

    private String resolveMediaIdForSkip(String direction) {
        refreshAndroidAutoSelectedMediaIdFromPlaylist("skip-" + direction);

        if (isAppPostMediaId(androidAutoSelectedMediaId)) {
            return androidAutoSelectedMediaId.trim();
        }

        if (isAppPostMediaId(externalMediaId)) {
            androidAutoSelectedMediaId = externalMediaId.trim();
            return androidAutoSelectedMediaId;
        }

        return androidAutoSelectedMediaId != null ? androidAutoSelectedMediaId : externalMediaId;
    }

    private String getCurrentPlaybackDebugInfo(String command) {
        String currentMediaId = null;
        String currentTitle = "";
        String currentState = "unknown";
        int playlistSize = -1;

        try {
            if (jwPlayer != null) {
                PlaylistItem currentItem = jwPlayer.getPlaylistItem();
                currentMediaId = inferMediaIdFromPlaylistItem(currentItem);
                currentTitle = currentItem != null && currentItem.getTitle() != null ? currentItem.getTitle() : "";
                currentState = String.valueOf(jwPlayer.getState());
                if (jwPlayer.getPlaylist() != null) {
                    playlistSize = jwPlayer.getPlaylist().size();
                }
            }
        } catch (Exception e) {
            return "command=" + command
                + ", debugError=" + e.getMessage()
                + ", androidAutoSelectedMediaId=" + androidAutoSelectedMediaId
                + ", externalMediaId=" + externalMediaId;
        }

        return "command=" + command
            + ", currentMediaId=" + currentMediaId
            + ", currentTitle=" + currentTitle
            + ", androidAutoSelectedMediaId=" + androidAutoSelectedMediaId
            + ", externalMediaId=" + externalMediaId
            + ", playlistSize=" + playlistSize
            + ", state=" + currentState;
    }

    // Static position cache: stores last known position per mediaId (survives instance recreation)
    // This is critical for handoff because MediaItemsStore only has the original extras bundle
    private static final java.util.Map<String, Long> lastKnownPositionCache = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * Static method to cache a position for handoff from background player to UI player.
     * Called by JWPlayerNativePlaybackHandler during cleanup to ensure the position
     * survives instance recreation and is available when the new UI player initializes.
     */
    public static void cachePositionForHandoff(String mediaId, long positionMs) {
        if (mediaId != null && !mediaId.isEmpty() && positionMs >= 0) {
            lastKnownPositionCache.put(mediaId, positionMs);
            JWLog.d(TAG, "cachePositionForHandoff: Cached " + positionMs + "ms for mediaId=" + mediaId);
        }
    }

    private JWPlayerNativePlaybackHandler jwPlayerNativePlaybackHandler = null;
    private boolean lastSeekRequestedWhilePaused = false;
    private long lastRequestedSeekPositionMs = -1L;
    private long lastKnownDurationMs = -1L;
    private boolean suppressNextSeekCallback = false;
    private boolean completionScheduledFromSeek = false;
    private boolean resetToStartAfterSeekCompletion = false;
    private boolean suppressNextOnPlayAfterSeekCompletion = false;
    private long suppressOnPlayExpiryMs = 0L;

    // Block only the immediate auto-play callback that fires right after we force completion
    private static final long AUTO_PLAY_SUPPRESS_WINDOW_MS = 350L;

    private void captureDurationSnapshot() {
        try {
            double durationSeconds = -1.0;
            if (jwPlayer != null) {
                durationSeconds = jwPlayer.getDuration();
            } else if (serviceMediaApi != null && serviceMediaApi.getPlayer() != null) {
                durationSeconds = serviceMediaApi.getPlayer().getDuration();
            }

            if (durationSeconds > 0) {
                long durationMs = (long) (durationSeconds * 1000L);
                if (durationMs > 0) {
                    lastKnownDurationMs = durationMs;
                }
            }
        } catch (Exception durationEx) {
            JWLog.w(TAG, "captureDurationSnapshot failed: " + durationEx.getMessage());
        }
    }

    // MediaSession callback to handle transport controls on Android 13/14+
    private final MediaSessionCompat.Callback mediaSessionCallback = new MediaSessionCompat.Callback() {
        @Override
        public void onPlay() {
            JWLog.d(TAG, "mediaSessionCallback.onPlay()");
            performPlay();
        }

        @Override
        public void onPause() {
            JWLog.d(TAG, "mediaSessionCallback.onPause()", true);
            performPause();
        }

        @Override
        public void onStop() {
            JWLog.d(TAG, "mediaSessionCallback.onStop()");
            performStop();
        }

        @Override
        public void onSeekTo(long position) {
            JWLog.d(TAG, "mediaSessionCallback.onSeekTo(positionMs=" + position + ")");            
            performSeekTo(position);            
        }

        @Override
        public void onSkipToNext() {
            JWLog.d(TAG, "mediaSessionCallback.onSkipToNext()");
            try {
                performSkipToNext();
            } catch (Exception e) {
                JWLog.e(TAG, "mediaSessionCallback.onSkipToNext() - ERROR: " + e.getMessage(), e);
            }
        }

        @Override
        public void onSkipToPrevious() {
            JWLog.d(TAG, "mediaSessionCallback.onSkipToPrevious()");
            try {
                performSkipToPrevious();
            } catch (Exception e) {
                JWLog.e(TAG, "mediaSessionCallback.onSkipToPrevious() - ERROR: " + e.getMessage(), e);
            }
        }

        @Override
        public void onPlayFromMediaId(String mediaId, Bundle extras) {
            JWLog.d(TAG, "mediaSessionCallback.onPlayFromMediaId(mediaId=" + mediaId + ", extras=" + JWLog.bundleInfo(extras) + ")");
            performMediaItemSelection(mediaId, extras);
        }

        @Override
        public boolean onMediaButtonEvent(Intent mediaButtonIntent) {
            JWLog.d(TAG, "mediaSessionCallback.onMediaButtonEvent(intent=" + JWLog.intentInfo(mediaButtonIntent) + ")");
            // Let existing fallback also run; just return super after we optionally process
            try {
                if (mediaButtonIntent != null && Intent.ACTION_MEDIA_BUTTON.equals(mediaButtonIntent.getAction())) {
                    KeyEvent keyEvent = mediaButtonIntent.getParcelableExtra(Intent.EXTRA_KEY_EVENT);
                    if (keyEvent != null && keyEvent.getAction() == KeyEvent.ACTION_DOWN) {
                        int keyCode = keyEvent.getKeyCode();
                        switch (keyCode) {
                            case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
                                boolean isPlaying = false;
                                try {
                                    if (mediaSessionStateProvider != null && mediaSessionStateProvider.mediaSessionCompat != null) {
                                        PlaybackStateCompat playbackState = mediaSessionStateProvider.mediaSessionCompat.getController().getPlaybackState();
                                        if (playbackState != null) {
                                            int state = playbackState.getState();
                                            isPlaying = (state == PlaybackStateCompat.STATE_PLAYING || 
                                                    state == PlaybackStateCompat.STATE_BUFFERING);
                                        }
                                    }
                                } catch (Exception ex) {
                                    // Fallback to JWPlayer state if MediaSession state unavailable
                                    if (jwPlayer != null) {
                                        isPlaying = (jwPlayer.getState() == PlayerState.PLAYING);
                                    }
                                }
                                
                                if (isPlaying) {
                                    onPause();
                                } else {
                                    onPlay();
                                }
                                return true;
                                
                            case KeyEvent.KEYCODE_MEDIA_PLAY:
                                onPlay();
                                return true;
                                
                            case KeyEvent.KEYCODE_MEDIA_PAUSE:
                                onPause();
                                return true;
                                
                            case KeyEvent.KEYCODE_MEDIA_STOP:
                                onStop();
                                return true;
                        }
                    }
                }
            } catch (Exception ex) {
                JWLog.w(TAG, "mediaSessionCallback onMediaButtonEvent error: " + ex.getMessage());
            }
            return super.onMediaButtonEvent(mediaButtonIntent);
        }

        @Override
        public void onCustomAction(String action, android.os.Bundle extras) {
            JWLog.d(TAG, "mediaSessionCallback.onCustomAction(action=" + action + ")");
            // Delegate speed control back to MediaBrowserService which owns the speed state
            if ("com.mediabrowser.ACTION_CHANGE_SPEED".equals(action)) {
                try {
                    Class<?> serviceClass = Class.forName("com.mediabrowser.MediaBrowserService");
                    Object instance = serviceClass.getMethod("getInstance").invoke(null);
                    if (instance != null) {
                        serviceClass.getMethod("handleSpeedAction").invoke(instance);
                    }
                } catch (Exception e) {
                    JWLog.w(TAG, "onCustomAction: delegation to MediaBrowserService failed: " + e.getMessage());
                }
            }
        }
    };

    /** 
     * Attempts to capture the current playback position (from JWPlayer or MediaSession) 
     * and forward it to MediaBrowserService.
     */
    private void captureAndStoreSeekPosition() {
        JWLog.d(TAG, "captureAndStoreSeekPosition()");
        long positionMs = 0L;
        try {
            if (jwPlayer != null) {
                positionMs = (long) (jwPlayer.getPosition() * 1000);
            } else if (mediaSessionStateProvider != null && mediaSessionStateProvider.mediaSessionCompat != null) {
                PlaybackStateCompat ps = mediaSessionStateProvider.mediaSessionCompat.getController().getPlaybackState();
                if (ps != null) {
                    positionMs = ps.getPosition();
                }
            }
        } catch (Exception ignore) {}

        // Skip capturing zero positions. JWPlayer briefly
        // reports getPosition()==0 during media switches (before the new playlist item starts);
        // writing that 0 would clobber the legitimate resume position cached by the periodic save.
        if (positionMs <= 0) {
            JWLog.d(TAG, "captureAndStoreSeekPosition: skipping zero position capture");
            return;
        }

        storeSeekPosition(positionMs);
    }

    public void storeSeekPosition(long position) {
        JWLog.d(TAG, "storeSeekPosition(externalMediaId=" + externalMediaId + ", positionMs=" + position + ")");
        String inferredCurrentMediaId = inferMediaIdFromPlaylistItem(jwPlayer != null ? jwPlayer.getPlaylistItem() : null);
        if (inferredCurrentMediaId != null && !inferredCurrentMediaId.isEmpty()) {
            if (isAppPostMediaId(inferredCurrentMediaId)) {
                androidAutoSelectedMediaId = inferredCurrentMediaId.trim();
            }
            if (externalMediaId == null || externalMediaId.isEmpty() || !inferredCurrentMediaId.equals(externalMediaId)) {
                JWLog.d(TAG, "storeSeekPosition: refreshing externalMediaId from current playlist item: " + externalMediaId + " -> " + inferredCurrentMediaId);
                externalMediaId = inferredCurrentMediaId;
            }
        }

        if (externalMediaId == null || externalMediaId.isEmpty()) {
            JWLog.d(TAG, "storeSeekPosition: externalMediaId is null/empty after refresh");
            return;
        }

        // CRITICAL: Never store 0 during Android Auto handoff - it's spurious noise from JWPlayer
        if (position == 0 && isPlayingFromAndroidAuto && pendingSeekMs != null && pendingSeekMs > 0) {
            JWLog.d(TAG, "storeSeekPosition: BLOCKED storing 0 during AA handoff (pendingSeekMs=" + pendingSeekMs + ")");
            return;
        }

        if (resetToStartAfterSeekCompletion && position > 0) {
            JWLog.d(TAG, "storeSeekPosition: override due to pending completion reset");
            position = 0L;
        }

        // Don't overwrite a non-zero cached position with 0.
        // During AA Skip Next/Prev, storeSeekPosition(0) can fire after externalMediaId got refreshed
        // back to the OLD mediaId via inferMediaIdFromPlaylistItem, which would wipe the legitimate
        // resume position saved seconds earlier by the periodic progress sync.
        if (position == 0) {
            Long existing = lastKnownPositionCache.get(externalMediaId);
            if (existing != null && existing > 0) {
                JWLog.d(TAG, "storeSeekPosition: BLOCKED zero overwrite for mediaId=" + externalMediaId + " (existing=" + existing + "ms)");
                return;
            }
        }

        // CRITICAL: Cache position in static map for handoff resume
        // This survives instance recreation and is checked before MediaItemsResumeProvider
        if (position >= 0) {
            lastKnownRealPlaybackPositionMs = position;
            lastKnownPositionCache.put(externalMediaId, position);
            JWLog.d(TAG, "storeSeekPosition: Cached position " + position + "ms for mediaId=" + externalMediaId + " in static cache");
        }
        
        try {
            Class<?> mediaBrowserServiceClass = Class.forName("com.mediabrowser.MediaBrowserService");
            java.lang.reflect.Method reportSeek =
                    mediaBrowserServiceClass.getMethod("updateSeekPosition", String.class, long.class);
            reportSeek.invoke(null, externalMediaId, position); // position in ms
            JWLog.d(TAG, "storeSeekPosition: Successfully stored position " + position + "ms for mediaId=" + externalMediaId + " via MediaBrowserService.updateSeekPosition()");
        } catch (Exception e) {
            // Safe to ignore; just don't break the seek
            JWLog.w(TAG, "Could not report seek to React Native: " + e.getMessage());
        }
    }

    public RNJWMediaSessionHelper(Context context, RNJWNotificationHelper notificationHelper, ServiceMediaApi serviceMediaApi) {
        this(context, notificationHelper, serviceMediaApi, new MediaServiceFactory());
    }

    private RNJWMediaSessionHelper(Context context, RNJWNotificationHelper notificationHelper, ServiceMediaApi serviceMediaApi, MediaServiceFactory bgaFactory) {
        JWLog.d(TAG, "<init>-internal(context=" + JWLog.id(context) + ", notificationHelper=" + JWLog.id(notificationHelper) + ", serviceMediaApi=" + JWLog.id(serviceMediaApi) + ", mediaServiceFactory=" + JWLog.id(bgaFactory) + ")");
        this.context = context;
        this.rnjwNotificationHelper = notificationHelper;
        this.mediaServiceFactory = bgaFactory;
        this.jwPlayerNativePlaybackHandler = JWPlayerNativePlaybackHandler.getInstance(context);
        
        // Set this as the active instance for delegation
        activeInstance = this;
        
        this.setupServiceMediaApi(serviceMediaApi);
    }

    /**
     * Called by MediaBrowserService (via reflection) when a new MediaSession singleton
     * was created after the previous one was released.  Re-points this helper at the
     * new session and re-pushes the current metadata & playback state so Android Auto
     * sees the currently-playing track immediately.
     */
    public static void refreshSessionReference() {
        JWLog.d(TAG, "refreshSessionReference() activeInstance=" + (activeInstance != null));
        if (activeInstance == null) {
            JWLog.d(TAG, "refreshSessionReference: no active instance — nothing to refresh");
            return;
        }

        RNJWMediaSessionHelper self = activeInstance;
        if (self.context == null) {
            JWLog.w(TAG, "refreshSessionReference: context is null");
            return;
        }

        // Get the (possibly new) singleton
        MediaSessionCompat newSession = MediaSessionSingleton.getInstance(self.context);

        // Check if the reference actually changed
        MediaSessionCompat currentSession = (self.mediaSessionStateProvider != null)
                ? self.mediaSessionStateProvider.mediaSessionCompat : null;
        if (currentSession == newSession) {
            JWLog.d(TAG, "refreshSessionReference: session reference unchanged — re-pushing state");
        } else {
            JWLog.d(TAG, "refreshSessionReference: session reference CHANGED — updating provider");
            self.mediaSessionStateProvider = new MediaSessionStateProvider(newSession);
            try {
                newSession.setCallback(self.mediaSessionCallback);
            } catch (Exception e) {
                JWLog.w(TAG, "refreshSessionReference: failed to set callback: " + e.getMessage());
            }
        }

        // Re-push current metadata from the player
        try {
            if (self.jwPlayer != null) {
                PlaylistItem item = self.jwPlayer.getPlaylistItem();
                if (item != null) {
                    self.updatePlaylistItem(item);
                    JWLog.d(TAG, "refreshSessionReference: metadata re-pushed (title=" + item.getTitle() + ")");
                }
            }
        } catch (Exception e) {
            JWLog.w(TAG, "refreshSessionReference: metadata push failed: " + e.getMessage());
        }

        // Re-push current playback state
        try {
            if (self.jwPlayer != null) {
                PlayerState ps = self.jwPlayer.getState();
                int pbState = PlaybackStateCompat.STATE_NONE;
                if (ps == PlayerState.PLAYING) pbState = PlaybackStateCompat.STATE_PLAYING;
                else if (ps == PlayerState.PAUSED) pbState = PlaybackStateCompat.STATE_PAUSED;
                else if (ps == PlayerState.BUFFERING) pbState = PlaybackStateCompat.STATE_BUFFERING;
                else if (ps == PlayerState.IDLE) pbState = PlaybackStateCompat.STATE_STOPPED;

                self.updatePlaybackState(self.jwPlayer, pbState);
                JWLog.d(TAG, "refreshSessionReference: playback state re-pushed (state=" + pbState + ")");
            }
        } catch (Exception e) {
            JWLog.w(TAG, "refreshSessionReference: state push failed: " + e.getMessage());
        }
    }

    final void setupServiceMediaApi(ServiceMediaApi serviceMediaApi) {
        JWLog.d(TAG, "setupServiceMediaApi(serviceMediaApi=" + JWLog.id(serviceMediaApi) + ")");
        this.cleanup();
        if (serviceMediaApi != null) {
            this.serviceMediaApi = serviceMediaApi;

            initServiceMediaApi();
        }
    }

    private void initServiceMediaApi() {
        JWLog.d(TAG, "initServiceMediaApi()");    
        
        if (activeInstance != this) {
            JWLog.w(TAG, "initServiceMediaApi: This instance is not active; skipping initialization");
            return;
        }

        if (this.mediaSessionStateProvider != null) {
            JWLog.w(TAG, "initServiceMediaApi: MediaSession already initialized");
            return;
        }

        this.jwPlayer = serviceMediaApi.getPlayer();
        Context currentContext = this.context;
        this.mediaSessionStateProvider =  new MediaSessionStateProvider(MediaSessionSingleton.getInstance(currentContext));
        setupNetworkCallback();

        // Attach callback (was previously intentionally omitted)
        try {
            if (this.mediaSessionStateProvider != null && this.mediaSessionStateProvider.mediaSessionCompat != null) {
                this.mediaSessionStateProvider.mediaSessionCompat.setCallback(mediaSessionCallback);
            }
        } catch (Exception cbEx) {
            JWLog.w(TAG, "Failed to set MediaSession callback: " + cbEx.getMessage());
        }

        setupMediaButtonFallback(currentContext);
        
        // Check if background player is active and coordinate
        Boolean isBackgroundActive = jwPlayerNativePlaybackHandler.isBackgroundPlayerActive();

        if (isBackgroundActive != null && isBackgroundActive) {
            try {
                // CRITICAL: Capture background player's CURRENT position BEFORE transfer/cleanup
                // Get the comprehensive playback state which includes current position
                java.lang.reflect.Method getStateMethod = jwPlayerNativePlaybackHandler.getClass()
                        .getDeclaredMethod("getComprehensivePlaybackState");
                getStateMethod.setAccessible(true);
                Object stateObj = getStateMethod.invoke(jwPlayerNativePlaybackHandler);
                
                if (stateObj instanceof com.facebook.react.bridge.WritableMap) {
                    com.facebook.react.bridge.WritableMap stateMap = (com.facebook.react.bridge.WritableMap) stateObj;
                    
                    // Extract current position and mediaId
                    if (stateMap.hasKey("currentPosition") && stateMap.hasKey("mediaId")) {
                        double positionSeconds = stateMap.getDouble("currentPosition");
                        String bgMediaId = stateMap.getString("mediaId");
                        long positionMs = (long) (positionSeconds * 1000);
                        
                        if (positionMs > 0 && bgMediaId != null && !bgMediaId.isEmpty()) {
                            // Store this position in MediaBrowserService immediately
                            // This ensures queryResumeViaReflection will return the correct value
                            externalMediaId = bgMediaId; // Set before calling storeSeekPosition
                            storeSeekPosition(positionMs);
                            JWLog.d(TAG, "coordinateWithBackground: captured and stored position " + positionMs + "ms from background player for mediaId=" + bgMediaId);
                        }
                    }
                }
                
                // Now proceed with transfer
                Object transferResult = jwPlayerNativePlaybackHandler.transferToUIPlayer();
                JWLog.d(TAG, "coordinateWithBackground: transferToUIPlayer invoked, result=" + transferResult);
            } catch (Exception t) {
                JWLog.w(TAG, "coordinateWithBackground: failed: " + t.getMessage());
            }
        } else {
            JWLog.d(TAG, "coordinateWithBackground: background player not active");
        }
        
        // DON'T set callback here - MediaBrowserService already has the callback set
        // and it will delegate to us when needed via static methods or fallback to direct handling
        
        this.jwPlayer.addListeners(this, new EventType[]{EventType.PLAY, EventType.PAUSE, EventType.BUFFER, EventType.ERROR, EventType.PLAYLIST_ITEM, EventType.PLAYLIST_COMPLETE, EventType.AD_PLAY, EventType.AD_SKIPPED, EventType.AD_COMPLETE, EventType.AD_ERROR, EventType.SEEK, EventType.SEEKED});
        JWPlayer currentJwPlayer = this.jwPlayer;
        // Only seed playlist when there is no active background/service session
        try {
            // Check if there's already an active session by checking if we have a player
            boolean isActivePlayerExist = serviceMediaApi != null && serviceMediaApi.getPlayer() != null;
            if (!isActivePlayerExist) {
                this.updatePlaylistItem(currentJwPlayer.getPlaylistItem());
            } else {
                JWLog.d(TAG, "initServiceMediaApi: skipping updatePlaylistItem because a session is already active");
            }
        } catch (Exception t) {
            // be safe and skip seeding on errors to avoid replay storms
            JWLog.w(TAG, "initServiceMediaApi: capability check failed, skipping updatePlaylistItem: " + t.getMessage());
        }
        this.updatePlayerState(currentJwPlayer.getState());
        // Try to apply any pending seek right away
        try { 
            applyPendingSeekWhenReady(currentJwPlayer.getPlaylistItem()); 
        } catch (Exception ignore) {}
    }

    private void updatePlaybackState(JWPlayer player, int state) {
        JWLog.d(TAG, "updatePlaybackState(player=" + JWLog.id(player) + ", state=" + state + ")");
        updatePlaybackState(player, state, null);
    }

    private void updatePlaybackState(JWPlayer player, int state, Long overridePositionMs) {
        JWLog.d(TAG, "updatePlaybackState(player=" + JWLog.id(player) + ", state=" + state + ", overridePositionMs=" + (overridePositionMs == null ? "null" : overridePositionMs) + ")");
        if (this.mediaSessionStateProvider == null || this.mediaSessionStateProvider.mediaSessionCompat == null || player == null) {
            return;
        }

        long positionMs;
        if (overridePositionMs != null) {
            positionMs = overridePositionMs;
        } else {
            try {
                positionMs = (long) (player.getPosition() * 1000);
            } catch (Exception e) {
                positionMs = 0;
            }
        }

        long actions =
                PlaybackStateCompat.ACTION_PLAY |
                PlaybackStateCompat.ACTION_PAUSE |
                PlaybackStateCompat.ACTION_PLAY_PAUSE |
                PlaybackStateCompat.ACTION_STOP |
                PlaybackStateCompat.ACTION_SEEK_TO |
                PlaybackStateCompat.ACTION_SKIP_TO_NEXT |
                PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS |
                PlaybackStateCompat.ACTION_PLAY_FROM_MEDIA_ID;

        if (serviceMediaApi != null) {
            try {
                long caps = this.serviceMediaApi.getNotificationCapabilities();
                actions |= caps;
            } catch (Exception ex) {
                JWLog.w(TAG, "updatePlaybackState: capabilities read failed " + ex.getMessage());
            }
        }

        // Detect speed drift: if the in-app UI changed JWPlayer's rate directly
        // (bypassing MediaBrowserService.setPlaybackSpeed), our stored currentSpeed
        // is stale.  Read the actual player rate and, when it differs, update both
        // the stored value AND the AA speed custom-action icon so that the Android
        // Auto UI stays in sync — entirely within the library, no app code needed.
        if (state == PlaybackStateCompat.STATE_PLAYING && player != null) {
            try {
                float actualRate = (float) player.getPlaybackRate();
                if (actualRate > 0 && Math.abs(actualRate - currentSpeed) > 0.01f) {
                    JWLog.d(TAG, "updatePlaybackState: speed drift detected — actual=" + actualRate + " stored=" + currentSpeed + "; syncing");
                    currentSpeed = actualRate;
                    // Tell MediaBrowserService to refresh the custom action icon/label
                    try {
                        Class<?> mbsClass = Class.forName("com.mediabrowser.MediaBrowserService");
                        java.lang.reflect.Method setSpeed = mbsClass.getMethod("setPlaybackSpeedFromSync", float.class);
                        setSpeed.invoke(null, actualRate);
                    } catch (Exception ignored) {}
                }
            } catch (Exception ignored) {}
        }

        float speed = (state == PlaybackStateCompat.STATE_PLAYING) ? currentSpeed : 0.0f;

        PlaybackStateCompat.Builder builder = new PlaybackStateCompat.Builder()
                .setState(state, positionMs, speed)
                .setActions(actions);

        // Preserve any custom actions already registered on the session (e.g. the Android Auto
        // speed control button set by MediaBrowserService). Without this, every JWPlayer state
        // update overwrites the PlaybackStateCompat from scratch, stripping the custom actions
        // and causing the speed button to disappear from the Android Auto UI.
        boolean hasCustomActions = false;
        try {
            PlaybackStateCompat existingState =
                    this.mediaSessionStateProvider.mediaSessionCompat.getController().getPlaybackState();
            if (existingState != null) {
                for (PlaybackStateCompat.CustomAction ca : existingState.getCustomActions()) {
                    builder.addCustomAction(ca);
                    hasCustomActions = true;
                }
            }
        } catch (Exception ignored) {}

        // Fallback: if no custom actions were found on the session (e.g. they were wiped
        // during a media-switch destroy/recreate cycle), ask MediaBrowserService directly
        // for the current speed custom action.
        if (!hasCustomActions) {
            try {
                Class<?> mbsClass = Class.forName("com.mediabrowser.MediaBrowserService");
                java.lang.reflect.Method getAction = mbsClass.getMethod("getSpeedCustomAction");
                Object actionObj = getAction.invoke(null);
                if (actionObj instanceof PlaybackStateCompat.CustomAction) {
                    builder.addCustomAction((PlaybackStateCompat.CustomAction) actionObj);
                }
            } catch (Exception ignored) {}
        }

        try {
            this.mediaSessionStateProvider.mediaSessionCompat.setPlaybackState(builder.build());
            this.mediaSessionStateProvider.mediaSessionCompat.setActive(true);
        } catch (Exception ex) {
            JWLog.w(TAG, "updatePlaybackState: set failed " + ex.getMessage());
        }
    }

    private void setupMediaButtonFallback(Context ctx) {
        JWLog.d(TAG, "setupMediaButtonFallback(ctx=" + JWLog.id(ctx) + ")");
        if (mediaButtonFallbackReceiver != null) return;

        mediaButtonFallbackReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                JWLog.d(TAG, "mediaButtonFallbackReceiver.onReceive(intent=" + JWLog.intentInfo(intent) + ")");
                if (!Intent.ACTION_MEDIA_BUTTON.equals(intent.getAction())) return;
                
                KeyEvent keyEvent = intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT);
                if (keyEvent == null || keyEvent.getAction() != KeyEvent.ACTION_DOWN) return;
                int keyCode = keyEvent.getKeyCode();

                // Fallback handling if session callback not invoked
                try {
                    MediaSessionCompat session = (mediaSessionStateProvider != null) ? mediaSessionStateProvider.mediaSessionCompat : null;
                    MediaControllerCompat.TransportControls transportControls = (session != null)
                            ? session.getController().getTransportControls() : null;

                    boolean handled = false;
                    if (keyCode == KeyEvent.KEYCODE_MEDIA_PLAY
                            || keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
                            || keyCode == KeyEvent.KEYCODE_MEDIA_PAUSE) {

                        PlayerState ps = (jwPlayer != null) ? jwPlayer.getState() : null;

                        if (keyCode == KeyEvent.KEYCODE_MEDIA_PLAY ||
                                (keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE && ps != PlayerState.PLAYING)) {
                            if (transportControls != null) transportControls.play(); else if (jwPlayer != null) jwPlayer.play();
                            handled = true;
                        } else if (keyCode == KeyEvent.KEYCODE_MEDIA_PAUSE ||
                                (keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE && ps == PlayerState.PLAYING)) {
                            if (transportControls != null) transportControls.pause(); else if (jwPlayer != null) jwPlayer.pause();
                            handled = true;
                        }
                    }
                    if (handled) {
                        int playerState = (jwPlayer != null && jwPlayer.getState() == PlayerState.PLAYING)
                                ? PlaybackStateCompat.STATE_PLAYING : PlaybackStateCompat.STATE_PAUSED;
                        updatePlaybackState(jwPlayer, playerState);
                    }
                } catch (Exception ex) {
                    JWLog.w(TAG, "Fallback media button handling error " + ex.getMessage());
                }
            }
        };
        try {
            IntentFilter intentFilter = new IntentFilter(Intent.ACTION_MEDIA_BUTTON);
            ContextCompat.registerReceiver(
                    ctx,
                    mediaButtonFallbackReceiver,
                    intentFilter,
                    ContextCompat.RECEIVER_NOT_EXPORTED);
        } catch (Exception ex) {
            JWLog.w(TAG, "setupMediaButtonFallback: register failed " + ex.getMessage());
        }
    }

    // Audio focus management
    private boolean requestAudioFocusForPlayback() {
        JWLog.d(TAG, "requestAudioFocusForPlayback(context=" + JWLog.id(context) + ", isPlayingFromAndroidAuto=" + isPlayingFromAndroidAuto + ", currentlyHasFocus=" + currentlyHasFocus + ")");
        // If this is from Android Auto, let Android Auto handle audio focus
        if (isPlayingFromAndroidAuto) {
            currentlyHasFocus = true; // Assume we have focus
            return true;
        }

        // Don't request if we already have focus
        if (currentlyHasFocus) {
            return true;
        }
        
        // Detect Android Auto handoff: if we're currently playing and requesting focus
        // it's likely a handoff from Android Auto to phone app
        boolean wasPlayingBeforeRequest = isCurrentlyPlaying();
        if (wasPlayingBeforeRequest && serviceMediaApi != null) {
            JWLog.d(TAG, "requestAudioFocusForPlayback: Detected potential Android Auto handoff (was playing)");
            isPlayingFromAndroidAuto = true;
            androidAutoHandoffStartTime = System.currentTimeMillis();
        }
        
        lastFocusRequestTime = System.currentTimeMillis();
        
        if (audioManager == null) {
            audioManager = (android.media.AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        }
        if (audioManager == null) {
            JWLog.w(TAG, "AudioManager unavailable - cannot request focus");
            return false;
        }

        int resultRequestAudioFocus = android.media.AudioManager.AUDIOFOCUS_REQUEST_FAILED;

        if (android.os.Build.VERSION.SDK_INT >= 26) {
            if (audioFocusRequest == null) {
                audioFocusRequest = new android.media.AudioFocusRequest.Builder(android.media.AudioManager.AUDIOFOCUS_GAIN)
                    .setOnAudioFocusChangeListener(fc -> {
                        JWLog.d(TAG, "AudioFocusRequest onAudioFocusChange(focusChange=" + fc + ")", true);
                        handleAudioFocusChange(fc);
                    })
                    .setAudioAttributes(new android.media.AudioAttributes.Builder()
                        .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build())
                    .build();
            }
            
            resultRequestAudioFocus = audioManager.requestAudioFocus(audioFocusRequest);
        } else {
            // Legacy focus request for pre-26
            if (legacyFocusChangeListener == null) {
                legacyFocusChangeListener = fc -> {
                    JWLog.d(TAG, "Legacy onAudioFocusChange(focusChange=" + fc + ")", true);
                    handleAudioFocusChange(fc);
                };
            }
            
            resultRequestAudioFocus = audioManager.requestAudioFocus(
                legacyFocusChangeListener,
                android.media.AudioManager.STREAM_MUSIC,
                android.media.AudioManager.AUDIOFOCUS_GAIN
            );
        }

        if (resultRequestAudioFocus == android.media.AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            currentlyHasFocus = true;
        }
        
        if (resultRequestAudioFocus != android.media.AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            JWLog.w(TAG, "Audio focus request denied: " + resultRequestAudioFocus);
        }
        return resultRequestAudioFocus == android.media.AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
    }

    private void handleAudioFocusChange(int focusChange) {
        JWLog.d(TAG, "handleAudioFocusChange(focusChange=" + focusChange + ")");
        long currentTime = System.currentTimeMillis();
        boolean currentlyPlaying = isCurrentlyPlaying();
        long timeSinceLastRequest = currentTime - lastFocusRequestTime;

        switch (focusChange) {
            case android.media.AudioManager.AUDIOFOCUS_GAIN:
                JWLog.d(TAG, "AUDIOFOCUS_GAIN received");
                currentlyHasFocus = true;
                
                // Clear Android Auto handoff flag after gaining focus
                if (isPlayingFromAndroidAuto) {
                    JWLog.d(TAG, "AUDIOFOCUS_GAIN: Clearing Android Auto handoff flag");
                    isPlayingFromAndroidAuto = false;
                }
                
                if (wasPlayingBeforeFocusLoss) {
                    wasPlayingBeforeFocusLoss = false;
                    try {
                        if (mediaSessionStateProvider != null && mediaSessionStateProvider.mediaSessionCompat != null) {
                            mediaSessionStateProvider.mediaSessionCompat
                                .getController()
                                .getTransportControls()
                                .play();
                        } else if (jwPlayer != null) {
                            jwPlayer.play();
                        }
                    } catch (Exception ignore) {
                        JWLog.w(TAG, "Error resuming after focus gain: " + ignore.getMessage());
                    }
                }
                break;
                
            case android.media.AudioManager.AUDIOFOCUS_LOSS:
                JWLog.d(TAG, "AUDIOFOCUS_LOSS received (timeSinceLastRequest=" + timeSinceLastRequest + "ms, isPlayingFromAndroidAuto=" + isPlayingFromAndroidAuto + ")");
                currentlyHasFocus = false;
                
                // If focus loss happens long after the last request, this is Android Auto disconnect, not handoff
                // Clear the flag so normal pause/audio focus behavior resumes
                if (isPlayingFromAndroidAuto && timeSinceLastRequest > 2000) {
                    JWLog.d(TAG, "AUDIOFOCUS_LOSS: Clearing Android Auto flag - this is a disconnect (time=" + timeSinceLastRequest + "ms)");
                    resetAndroidAutoFlag();
                }

                // Ignore focus loss only during a *short* Android Auto handoff window.
                // If we ignore all focus losses while AA is connected, the user may be unable to pause.
                if (isPlayingFromAndroidAuto) {
                    if (timeSinceLastRequest < FOCUS_LOSS_IGNORE_WINDOW_MS) {
                        JWLog.d(TAG, "AUDIOFOCUS_LOSS: Ignoring during Android Auto handoff window (" + timeSinceLastRequest + "ms), keeping playing state");
                        return;
                    }

                    // Not a handoff anymore — treat as real focus loss and clear the AA flag.
                    JWLog.d(TAG, "AUDIOFOCUS_LOSS: AA flag set but outside handoff window (" + timeSinceLastRequest + "ms) — clearing flag and handling focus loss");
                    resetAndroidAutoFlag();
                }
                
                // Ignore focus loss if it happens too soon after requesting focus
                if (timeSinceLastRequest < FOCUS_LOSS_IGNORE_WINDOW_MS) {
                    JWLog.d(TAG, "AUDIOFOCUS_LOSS: Ignoring due to timing window (< " + FOCUS_LOSS_IGNORE_WINDOW_MS + "ms)");
                    return;
                }
                
                if (currentlyPlaying) {
                    wasPlayingBeforeFocusLoss = true;
                    pausePlayback();
                }
                break;
                
            case android.media.AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
                JWLog.d(TAG, "AUDIOFOCUS_LOSS_TRANSIENT received (timeSinceLastRequest=" + timeSinceLastRequest + "ms, isPlayingFromAndroidAuto=" + isPlayingFromAndroidAuto + ")");
                currentlyHasFocus = false;
                
                // If transient loss happens long after request, clear AA flag
                if (isPlayingFromAndroidAuto && timeSinceLastRequest > 2000) {
                    JWLog.d(TAG, "AUDIOFOCUS_LOSS_TRANSIENT: Clearing Android Auto flag - this is a disconnect (time=" + timeSinceLastRequest + "ms)");
                    resetAndroidAutoFlag();
                }

                // Ignore transient focus loss only during a *short* Android Auto handoff window.
                if (isPlayingFromAndroidAuto) {
                    if (timeSinceLastRequest < FOCUS_LOSS_IGNORE_WINDOW_MS) {
                        JWLog.d(TAG, "AUDIOFOCUS_LOSS_TRANSIENT: Ignoring during Android Auto handoff window (" + timeSinceLastRequest + "ms)");
                        return;
                    }

                    // Not a handoff anymore — clear the flag so pause behavior works normally.
                    JWLog.d(TAG, "AUDIOFOCUS_LOSS_TRANSIENT: AA flag set but outside handoff window (" + timeSinceLastRequest + "ms) — clearing flag and handling focus loss");
                    resetAndroidAutoFlag();
                }
                
                // Also ignore transient loss if too soon
                if (timeSinceLastRequest < FOCUS_LOSS_IGNORE_WINDOW_MS) {
                    JWLog.d(TAG, "AUDIOFOCUS_LOSS_TRANSIENT: Ignoring due to timing window");
                    return;
                }
                
                if (currentlyPlaying) {
                    wasPlayingBeforeFocusLoss = true;
                    pausePlayback();
                }
                break;
                
            case android.media.AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
                JWLog.d(TAG, "AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK received");
                break;
                
            default:
                JWLog.d(TAG, "Unknown audio focus change: " + focusChange);
                break;
        }
    }

    private boolean isCurrentlyPlaying() {
        JWLog.v(TAG, "isCurrentlyPlaying() called");
        try {
            if (jwPlayer != null) {
                return jwPlayer.getState() == PlayerState.PLAYING;
            } else if (mediaSessionStateProvider != null && mediaSessionStateProvider.mediaSessionCompat != null) {
                PlaybackStateCompat playbackState = mediaSessionStateProvider.mediaSessionCompat.getController().getPlaybackState();
                if (playbackState != null) {
                    int state = playbackState.getState();
                    return state == PlaybackStateCompat.STATE_PLAYING || state == PlaybackStateCompat.STATE_BUFFERING;
                }
            }
        } catch (Exception ignore) {}
        return false;
    }

    private void pausePlayback() {
        JWLog.d(TAG, "pausePlayback() - Pausing playback due to audio focus loss", true);        

        // Don't pause during any seek operation
        if (isAnySeekInProgress()) {
            JWLog.d(TAG, "Seek in progress - skipping pause");
            return;
        }
        
        try {
            systemPauseInProgress = true;
            if (mediaSessionStateProvider != null && mediaSessionStateProvider.mediaSessionCompat != null) {
                mediaSessionStateProvider.mediaSessionCompat
                    .getController()
                    .getTransportControls()
                    .pause();
            } else if (jwPlayer != null) {
                jwPlayer.pause();
            }
        } catch (Exception ignore) {
            JWLog.w(TAG, "Error pausing after focus loss: " + ignore.getMessage());
        } finally {
            mainHandler.postDelayed(() -> systemPauseInProgress = false, 250);
        }
    }

    private void releaseAudioFocus() {
        JWLog.d(TAG, "releaseAudioFocus()");
        if (audioManager != null) {
            try {
                if (android.os.Build.VERSION.SDK_INT >= 26 && audioFocusRequest != null) {
                    int result = audioManager.abandonAudioFocusRequest(audioFocusRequest);
                    audioFocusRequest = null;
                } else if (legacyFocusChangeListener != null) {
                    int result = audioManager.abandonAudioFocus(legacyFocusChangeListener);
                    legacyFocusChangeListener = null;
                }
            } catch (Exception ex) {
                JWLog.w(TAG, "Error releasing audio focus: " + ex.getMessage());
            }
            audioManager = null;
        }
        
        currentlyHasFocus = false;
        wasPlayingBeforeFocusLoss = false;
    }

    // Reset the flag when playback ends or changes
    private void resetAndroidAutoFlag() {
        JWLog.d(TAG, "resetAndroidAutoFlag()");
        isPlayingFromAndroidAuto = false;
        androidAutoHandoffStartTime = 0;
    }

    private void setupNetworkCallback() {
        if (networkCallback != null) return;

        try {
            connectivityManager = (ConnectivityManager) context.getApplicationContext().getSystemService(Context.CONNECTIVITY_SERVICE);
            networkAvailable = isNetworkCurrentlyAvailable();

            if (connectivityManager == null) {
                JWLog.w(TAG, "NETWORK_GUARD: ConnectivityManager unavailable; allowing playback");
                networkAvailable = true;
                return;
            }

            networkCallback = new ConnectivityManager.NetworkCallback() {
                @Override
                public void onAvailable(Network network) {
                    mainHandler.post(() -> handleNetworkAvailabilityChanged(isNetworkCurrentlyAvailable()));
                }

                @Override
                public void onLost(Network network) {
                    mainHandler.post(() -> handleNetworkAvailabilityChanged(false));
                }

                @Override
                public void onCapabilitiesChanged(Network network, NetworkCapabilities networkCapabilities) {
                    boolean reachable = networkCapabilities != null
                            && networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                            && networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
                    mainHandler.post(() -> handleNetworkAvailabilityChanged(reachable));
                }
            };

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                connectivityManager.registerDefaultNetworkCallback(networkCallback);
            } else {
                NetworkRequest request = new NetworkRequest.Builder()
                        .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                        .build();
                connectivityManager.registerNetworkCallback(request, networkCallback);
            }

            JWLog.d(TAG, "NETWORK_GUARD: callback registered, available=" + networkAvailable);
        } catch (Exception e) {
            JWLog.w(TAG, "NETWORK_GUARD: failed to register callback: " + e.getMessage());
            networkAvailable = true;
            networkCallback = null;
        }
    }

    private void teardownNetworkCallback() {
        if (connectivityManager == null || networkCallback == null) return;

        try {
            connectivityManager.unregisterNetworkCallback(networkCallback);
        } catch (Exception ignored) {}

        networkCallback = null;
        connectivityManager = null;
    }

    private boolean isNetworkCurrentlyAvailable() {
        try {
            ConnectivityManager manager = connectivityManager != null
                    ? connectivityManager
                    : (ConnectivityManager) context.getApplicationContext().getSystemService(Context.CONNECTIVITY_SERVICE);
            if (manager == null) return true;

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Network activeNetwork = manager.getActiveNetwork();
                if (activeNetwork == null) return false;
                NetworkCapabilities capabilities = manager.getNetworkCapabilities(activeNetwork);
                return capabilities != null
                        && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                        && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
            }

            NetworkInfo info = manager.getActiveNetworkInfo();
            return info != null && info.isConnected();
        } catch (Exception e) {
            JWLog.w(TAG, "NETWORK_GUARD: availability check failed: " + e.getMessage());
            return true;
        }
    }

    private boolean isNetworkAvailableForPlayback() {
        return networkAvailable != null ? networkAvailable.booleanValue() : isNetworkCurrentlyAvailable();
    }

    private void handleNetworkAvailabilityChanged(boolean available) {
        Boolean previous = networkAvailable;
        networkAvailable = available;

        if (previous != null && previous.booleanValue() == available) {
            return;
        }

        JWLog.d(TAG, "NETWORK_GUARD: network " + (available ? "available" : "unavailable")
                + " mediaRequiresNetwork=" + currentMediaRequiresNetwork
                + " pausedByUser=" + pausedByUser
                + " pendingResume=" + pendingResumeAfterNetworkRecovery
                + " lastPositionMs=" + lastKnownRealPlaybackPositionMs, true);

        if (!currentMediaRequiresNetwork) {
            return;
        }

        if (!available) {
            long positionMs = rememberPlaybackPosition("network-lost");
            PlayerState state = safePlayerState();
            boolean shouldResume = shouldResumeAfterNetworkLoss(state, positionMs);
            if (shouldResume) {
                pendingResumeAfterNetworkRecovery = true;
                pausedByNetwork = true;
                playBlockedByNetwork = state != PlayerState.PLAYING && state != PlayerState.BUFFERING;
                recoveryNeedsPlayerReload = recoveryNeedsPlayerReload || playBlockedByNetwork || isPlayerStateNeedingReload(state);
                networkRecoveryReloadAttempts = 0;
            }

            JWLog.d(TAG, "NETWORK_GUARD: network-lost decision state=" + state
                    + " shouldResume=" + shouldResume
                    + " pausedByUser=" + pausedByUser
                    + " reloadNeeded=" + recoveryNeedsPlayerReload
                    + " lastPlaybackWasActive=" + lastPlaybackWasActive
                    + " positionMs=" + positionMs, true);
            return;
        }

        if (pausedByUser) {
            if (pendingResumeAfterNetworkRecovery || pausedByNetwork || playBlockedByNetwork || networkRecoveryInProgress) {
                JWLog.d(TAG, "NETWORK_GUARD: network restored but user pause wins; clearing recovery intent"
                        + " pendingResume=" + pendingResumeAfterNetworkRecovery
                        + " pausedByNetwork=" + pausedByNetwork
                        + " playBlocked=" + playBlockedByNetwork, true);
            }
            pendingResumeAfterNetworkRecovery = false;
            pausedByNetwork = false;
            playBlockedByNetwork = false;
            recoveryNeedsPlayerReload = false;
            networkRecoveryInProgress = false;
            lastPlaybackWasActive = false;
            return;
        }

        if (pendingResumeAfterNetworkRecovery && !pausedByUser) {
            long resumePositionMs = lastKnownRealPlaybackPositionMs;
            boolean shouldReload = recoveryNeedsPlayerReload || isPlayerStateNeedingReload(safePlayerState());
            pendingResumeAfterNetworkRecovery = false;
            pausedByNetwork = false;
            playBlockedByNetwork = false;

            JWLog.d(TAG, "NETWORK_GUARD: resuming after network recovery at " + resumePositionMs + "ms", true);
            resumeAfterNetworkRecovery(resumePositionMs, shouldReload);
        }
    }

    private boolean shouldResumeAfterNetworkLoss(PlayerState state, long positionMs) {
        if (pausedByUser) {
            return false;
        }

        if (state == PlayerState.PLAYING || state == PlayerState.BUFFERING) {
            return true;
        }

        if (pendingResumeAfterNetworkRecovery || pausedByNetwork || playBlockedByNetwork || isPlayingFromAndroidAuto) {
            return true;
        }

        return lastPlaybackWasActive && positionMs > 0;
    }

    private boolean isPlayerStateNeedingReload(PlayerState state) {
        return state == PlayerState.ERROR || state == PlayerState.IDLE;
    }

    private boolean tryRecreateHeadlessPlayerForRecovery(long resumePositionMs, String reason) {
        try {
            if (jwPlayerNativePlaybackHandler == null || !jwPlayerNativePlaybackHandler.isManagingPlayer(jwPlayer)) {
                return false;
            }

            boolean recreating = jwPlayerNativePlaybackHandler.recreateBackgroundPlayerForNetworkRecovery(resumePositionMs, reason);
            if (recreating) {
                JWLog.d(TAG, "NETWORK_GUARD: delegated headless recovery recreate reason=" + reason
                        + " resumePositionMs=" + resumePositionMs, true);
                if (jwPlayer != null) {
                    updatePlaybackState(jwPlayer, PlaybackStateCompat.STATE_BUFFERING, resumePositionMs);
                }
            }
            return recreating;
        } catch (Exception e) {
            JWLog.w(TAG, "NETWORK_GUARD: headless recreate delegation failed reason=" + reason
                    + " error=" + e.getMessage());
            return false;
        }
    }

    private void resumeAfterNetworkRecovery(long resumePositionMs, boolean preferReload) {
        long safeResumePositionMs = sanitizeSeekPosition(resumePositionMs);
        networkRecoveryInProgress = true;
        lastPlaybackWasActive = true;

        PlayerState state = safePlayerState();
        boolean shouldReload = preferReload || isPlayerStateNeedingReload(state);
        JWLog.d(TAG, "NETWORK_GUARD: recovery attempt state=" + state
                + " shouldReload=" + shouldReload
                + " resumePositionMs=" + safeResumePositionMs
                + " reloadAttempts=" + networkRecoveryReloadAttempts, true);

        if (shouldReload && reloadPlayerForNetworkRecovery(safeResumePositionMs, "network-recovery")) {
            return;
        }

        if (safeResumePositionMs > 0) {
            performSeekTo(safeResumePositionMs);
        }
        performPlay();
        scheduleNetworkRecoveryVerification(safeResumePositionMs, "network-recovery-play");
    }

    private boolean reloadPlayerForNetworkRecovery(long resumePositionMs, String reason) {
        if (jwPlayer == null) {
            JWLog.w(TAG, "NETWORK_GUARD: cannot reload for recovery; jwPlayer is null reason=" + reason);
            return false;
        }

        if (networkRecoveryReloadAttempts >= 2) {
            if (tryRecreateHeadlessPlayerForRecovery(resumePositionMs, reason + "-headless-recreate")) {
                return true;
            }
            JWLog.w(TAG, "NETWORK_GUARD: reload limit reached reason=" + reason
                    + " resumePositionMs=" + resumePositionMs);
            recoveryNeedsPlayerReload = true;
            pausedByNetwork = true;
            playBlockedByNetwork = true;
            return false;
        }

        try {
            PlayerConfig config = jwPlayer.getConfig();
            if (config == null) {
                if (tryRecreateHeadlessPlayerForRecovery(resumePositionMs, reason + "-null-config")) {
                    return true;
                }
                JWLog.w(TAG, "NETWORK_GUARD: cannot reload for recovery; config is null reason=" + reason);
                return false;
            }

            long safeResumePositionMs = sanitizeSeekPosition(resumePositionMs);
            networkRecoveryReloadAttempts++;
            networkRecoveryInProgress = true;
            recoveryNeedsPlayerReload = false;

            if (safeResumePositionMs > 0) {
                pendingSeekMs = safeResumePositionMs;
                pendingSeekApplied = false;
                autoHandoffSeekAttempts = 0;
                lastRequestedSeekPositionMs = safeResumePositionMs;
            }

            PlayerState stateBeforeReload = safePlayerState();
            JWLog.d(TAG, "NETWORK_GUARD: reloading player for recovery reason=" + reason
                    + " state=" + stateBeforeReload
                    + " resumePositionMs=" + safeResumePositionMs
                    + " attempt=" + networkRecoveryReloadAttempts, true);

            jwPlayer.setup(config);
            updatePlaybackState(jwPlayer, PlaybackStateCompat.STATE_BUFFERING, safeResumePositionMs);

            mainHandler.postDelayed(() -> {
                if (pausedByUser || !isNetworkAvailableForPlayback()) {
                    JWLog.d(TAG, "NETWORK_GUARD: delayed recovery play skipped pausedByUser=" + pausedByUser
                            + " networkAvailable=" + isNetworkAvailableForPlayback());
                    networkRecoveryInProgress = false;
                    return;
                }

                if (safeResumePositionMs > 0) {
                    performSeekTo(safeResumePositionMs);
                }
                performPlay();
                scheduleNetworkRecoveryVerification(safeResumePositionMs, reason + "-reload");
            }, 700);

            return true;
        } catch (Exception e) {
            JWLog.w(TAG, "NETWORK_GUARD: reload for recovery failed reason=" + reason + " error=" + e.getMessage());
            return false;
        }
    }

    private void scheduleNetworkRecoveryVerification(long resumePositionMs, String reason) {
        mainHandler.postDelayed(() -> {
            if (!networkRecoveryInProgress || pausedByUser || !isNetworkAvailableForPlayback()) {
                return;
            }

            PlayerState state = safePlayerState();
            boolean recovered = state == PlayerState.PLAYING || state == PlayerState.BUFFERING;
            JWLog.d(TAG, "NETWORK_GUARD: recovery verification reason=" + reason
                    + " state=" + state
                    + " recovered=" + recovered
                    + " resumePositionMs=" + resumePositionMs
                    + " reloadAttempts=" + networkRecoveryReloadAttempts, true);

            if (recovered) {
                networkRecoveryInProgress = false;
                return;
            }

            if (isPlayerStateNeedingReload(state) && reloadPlayerForNetworkRecovery(resumePositionMs, reason + "-verify")) {
                return;
            }

            if (networkRecoveryReloadAttempts >= 2) {
                if (tryRecreateHeadlessPlayerForRecovery(resumePositionMs, reason + "-headless-recreate")) {
                    return;
                }
                recoveryNeedsPlayerReload = true;
                pausedByNetwork = true;
                playBlockedByNetwork = true;
                networkRecoveryInProgress = false;
                return;
            }

            if (resumePositionMs > 0) {
                performSeekTo(resumePositionMs);
            }
            performPlay();
            networkRecoveryInProgress = false;
        }, 1600);
    }

    private boolean isLocalPlaybackSource(String source) {
        if (source == null) return false;
        String normalized = source.trim().toLowerCase(java.util.Locale.US);
        return normalized.startsWith("file://")
                || normalized.startsWith("content://")
                || normalized.startsWith("android.resource://")
                || normalized.startsWith("asset://")
                || normalized.startsWith("data:");
    }

    private void updateCurrentMediaAvailability(PlaylistItem item, String reason) {
        String source = extractPrimarySourceFile(item);
        boolean requiresNetwork = !isLocalPlaybackSource(source);
        currentMediaRequiresNetwork = requiresNetwork;
        if (!requiresNetwork) {
            pendingResumeAfterNetworkRecovery = false;
            pausedByNetwork = false;
            playBlockedByNetwork = false;
            recoveryNeedsPlayerReload = false;
            networkRecoveryInProgress = false;
            networkRecoveryReloadAttempts = 0;
        }
        JWLog.d(TAG, "NETWORK_GUARD: media availability reason=" + reason
                + " requiresNetwork=" + currentMediaRequiresNetwork
                + " source=" + JWLog.safe(source));
    }

    private PlayerState safePlayerState() {
        try {
            return jwPlayer != null ? jwPlayer.getState() : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private long getCurrentPositionMs() {
        try {
            if (jwPlayer != null) {
                return Math.max(0L, (long) (jwPlayer.getPosition() * 1000L));
            }
        } catch (Exception ignored) {}

        try {
            if (mediaSessionStateProvider != null && mediaSessionStateProvider.mediaSessionCompat != null) {
                PlaybackStateCompat playbackState = mediaSessionStateProvider.mediaSessionCompat.getController().getPlaybackState();
                if (playbackState != null) {
                    return Math.max(0L, playbackState.getPosition());
                }
            }
        } catch (Exception ignored) {}

        return Math.max(0L, lastKnownRealPlaybackPositionMs);
    }

    private long rememberPlaybackPosition(String reason) {
        long positionMs = getCurrentPositionMs();
        if (positionMs >= 0) {
            lastKnownRealPlaybackPositionMs = positionMs;
            storeSeekPosition(lastKnownRealPlaybackPositionMs);
        }
        JWLog.d(TAG, "NETWORK_GUARD: remember position reason=" + reason + " positionMs=" + lastKnownRealPlaybackPositionMs);
        return lastKnownRealPlaybackPositionMs;
    }

    private boolean shouldBlockPlayForNetwork(String reason) {
        if (!currentMediaRequiresNetwork || isNetworkAvailableForPlayback()) {
            return false;
        }

        if (pausedByUser) {
            JWLog.d(TAG, "NETWORK_GUARD: blocking offline play because user pause is active reason=" + reason
                    + " lastPositionMs=" + lastKnownRealPlaybackPositionMs, true);
            return true;
        }

        PlayerState state = safePlayerState();
        if (state == PlayerState.PLAYING && lastPlaybackWasActive && !playBlockedByNetwork) {
            JWLog.d(TAG, "NETWORK_GUARD: allowing already-playing buffered audio while offline reason=" + reason);
            return false;
        }

        JWLog.d(TAG, "NETWORK_GUARD: blocking play reason=" + reason
                + " state=" + state
                + " lastPositionMs=" + lastKnownRealPlaybackPositionMs
                + " pausedByUser=" + pausedByUser, true);
        return true;
    }

    private void publishNetworkUnavailableState(String reason) {
        long positionMs = rememberPlaybackPosition(reason);
        pausedByNetwork = !pausedByUser;
        playBlockedByNetwork = true;
        pendingResumeAfterNetworkRecovery = !pausedByUser;
        lastPlaybackWasActive = !pausedByUser;
        recoveryNeedsPlayerReload = recoveryNeedsPlayerReload || isPlayerStateNeedingReload(safePlayerState());

        if (jwPlayer != null) {
            updatePlaybackState(jwPlayer, PlaybackStateCompat.STATE_PAUSED, positionMs);
        }
    }
    
    // End Audio focus management

    private boolean hasAudioFocus() {
        JWLog.d(TAG, "hasAudioFocus() -> querying current focus references");
        // This is a simplified check - Android doesn't provide a direct way to query focus state
        // You could track this with a boolean field updated in focus change listener
        return audioManager != null && (audioFocusRequest != null || legacyFocusChangeListener != null);
    }

    private boolean isAnySeekInProgress() {
        JWLog.v(TAG, "isAnySeekInProgress(pendingSeekMs=" + pendingSeekMs + ", pendingSeekApplied=" + pendingSeekApplied + ")");
        // Check both pending seek (media selection) and recent manual seek
        boolean hasPendingSeek = pendingSeekMs != null && !pendingSeekApplied;
        // boolean hasRecentManualSeek = (System.currentTimeMillis() - lastManualSeekTime) < MANUAL_SEEK_PROTECTION_MS;
        
        return hasPendingSeek; // || hasRecentManualSeek;
    }

    public final void cleanup() {
        JWLog.d(TAG, "cleanup()");
        if (this.mediaSessionStateProvider != null) {
            // Clear active instance if this is the active one
            if (activeInstance == this) {
                activeInstance = null;
            }
        }

        softCleanup();
    }

    /**
     * Lightweight cleanup for media switching - only detaches the old player
     * without destroying MediaSession state. Keeps AA UI visible during transitions.
     */
    public final void detachPlayerOnly() {
        JWLog.d(TAG, "detachPlayerOnly() - lightweight cleanup for media switch");

        if (activeInstance == this) {
            activeInstance = null;
        }

        teardownNetworkCallback();

        if (mediaButtonFallbackReceiver != null) {
            try {
                context.unregisterReceiver(mediaButtonFallbackReceiver);
            } catch (Exception unregEx) {
                JWLog.w(TAG, "detachPlayerOnly() - failed to unregister fallback receiver: " + unregEx.getMessage());
            }
            mediaButtonFallbackReceiver = null;
        }
        
        // Only remove player listeners - don't touch MediaSession, notification, or audio focus
        if (this.jwPlayer != null) {
            try {
                this.jwPlayer.removeListeners(this,
                    new EventType[]{EventType.PLAY, EventType.PAUSE, EventType.BUFFER, EventType.ERROR, 
                                    EventType.PLAYLIST_ITEM, EventType.PLAYLIST_COMPLETE, EventType.AD_PLAY, 
                                    EventType.AD_SKIPPED, EventType.AD_COMPLETE, EventType.AD_ERROR, 
                                    EventType.SEEK, EventType.SEEKED});
                JWLog.d(TAG, "detachPlayerOnly() - removed player listeners");
            } catch (Exception e) {
                JWLog.w(TAG, "detachPlayerOnly() - error removing listeners: " + e.getMessage());
            }
            this.jwPlayer = null;
        }
        
        // Clear service media API reference (new player will create new one)
        if (this.serviceMediaApi != null) {
            this.serviceMediaApi = null;
        }
    }

    private final void softCleanup() {
        JWLog.d(TAG, "softCleanup()");
        // Reset AA flag and release audio focus first
        resetAndroidAutoFlag();
        releaseAudioFocus();
        teardownNetworkCallback();

        RNJWNotificationHelper notificationHelper;

        // --- MediaSession soft close (no release) ---
        if (this.mediaSessionStateProvider != null && this.mediaSessionStateProvider.mediaSessionCompat != null) {
            // Unregister fallback receiver if present
            if (mediaButtonFallbackReceiver != null) {
                try {
                    context.unregisterReceiver(mediaButtonFallbackReceiver);
                } catch (Exception unregEx) {
                    JWLog.w(TAG, "Failed to unregister media button fallback receiver: " + unregEx.getMessage());
                }
                mediaButtonFallbackReceiver = null;
            }

            try {
                // 1) Publish a no‑playback state so controllers/AA drop Now Playing
                PlaybackStateCompat.Builder stateBuilder = new PlaybackStateCompat.Builder()
                        .setState(PlaybackStateCompat.STATE_NONE, 0L, 0f)
                        .setActions(0L);
                // Preserve custom actions (like Android Auto speed button set by
                // MediaBrowserService) so they survive the helper destroy/recreate
                // cycle during media switches.  Without this, the speed custom action
                // is wiped and the next RNJWMediaSessionHelper instance finds nothing
                // to carry forward, causing the speed button to disappear from AA UI.
                try {
                    PlaybackStateCompat existingState =
                            this.mediaSessionStateProvider.mediaSessionCompat.getController().getPlaybackState();
                    if (existingState != null) {
                        for (PlaybackStateCompat.CustomAction ca : existingState.getCustomActions()) {
                            stateBuilder.addCustomAction(ca);
                        }
                    }
                } catch (Exception caEx) {
                    JWLog.w(TAG, "softCleanup: preserving custom actions failed " + caEx.getMessage());
                }
                this.mediaSessionStateProvider.mediaSessionCompat.setPlaybackState(stateBuilder.build());
            } catch (Exception ex) {
                JWLog.w(TAG, "softCleanup: setPlaybackState failed " + ex.getMessage());
            }

            try {
                // 2) Clear metadata/queue so no stale UI remains
                this.mediaSessionStateProvider.mediaSessionCompat.setMetadata(null);
                try {
                    this.mediaSessionStateProvider.mediaSessionCompat.setQueue(null);
                } catch (Exception ignore) { /* setQueue may be unsupported in some paths */ }
            } catch (Exception ex) {
                JWLog.w(TAG, "softCleanup: clearing metadata/queue failed " + ex.getMessage());
            }

            try {
                // 3) Deactivate the session (keep it alive for reuse)
                this.mediaSessionStateProvider.mediaSessionCompat.setActive(false);
            } catch (Exception ex) {
                JWLog.w(TAG, "softCleanup: setActive(false) failed " + ex.getMessage());
            }

            // IMPORTANT: Do NOT call release() here; keep the session object for future use
            // this.serviceMediaApi can be cleared to avoid stale references
            this.serviceMediaApi = null;
        }

        // --- Player/notification cleanup ---
        if (this.jwPlayer != null) {
            this.jwPlayer.removeListeners(this,
                new EventType[]{EventType.PLAY, EventType.PAUSE, EventType.BUFFER, EventType.ERROR, EventType.PLAYLIST_ITEM, EventType.PLAYLIST_COMPLETE, EventType.AD_PLAY, EventType.AD_SKIPPED, EventType.AD_COMPLETE, EventType.AD_ERROR, EventType.SEEK, EventType.SEEKED});
            (notificationHelper = this.rnjwNotificationHelper).notificationManager.cancel(notificationHelper.notificationId);
            this.jwPlayer = null;
        } else {
            // Even if jwPlayer is null, make sure the media notification is hidden
            try {
                (notificationHelper = this.rnjwNotificationHelper).notificationManager.cancel(notificationHelper.notificationId);
            } catch (Exception ignore) {}
        }
    }

    private static long extractResumePosition(Bundle extras) {
        JWLog.d(TAG, "extractResumePosition(extras=" + JWLog.bundleInfo(extras) + ")");
        if (extras == null) return -1;

        // Parse JSON payload from extras: "info"
        String infoJson = extras.getString("info", null);

        if (infoJson != null) {
            try {
                JSONObject obj = new JSONObject(infoJson);

                Double sec = readSeconds(obj, "timepoint");

                if (sec != null && sec >= 0) {
                    long ms = (long) (sec * 1000L);
                    return ms;
                }
            } catch (Exception e) {
                JWLog.w(TAG, "Failed parsing extras JSON for resume: " + e.getMessage());
            }
        }
        return -1L; // -1 = absent/no timepoint; 0 is reserved for "resume at start"
    }

    /** Helper: reads a seconds value from JSON by key, accepting numbers or numeric strings. */
    private static Double readSeconds(JSONObject obj, String key) {
        JWLog.v(TAG, "readSeconds(key=" + key + ")");
        if (!obj.has(key)) return null;
        try {
        // Try native number first
        double val = obj.optDouble(key, Double.NaN);
        if (!Double.isNaN(val)) return val;

        // If stored as string, parse
        String s = obj.optString(key, null);
        if (s != null) return Double.parseDouble(s);
        } catch (Exception ignored) {}
        return null;
    }

    /** Helper: normalize an image URL/string coming from JSON. Treats empty/"null"/"undefined" as absent (null). */
    private static String normalizeImage(String s) {
        if (s == null) return null;
        String trimmed = s.trim();
        if (trimmed.isEmpty()) return null;
        if ("null".equalsIgnoreCase(trimmed)) return null;
        if ("undefined".equalsIgnoreCase(trimmed)) return null;
        return trimmed;
    }

    private String getStringFromExtras(Bundle extras, String key) {
        JWLog.d(TAG, "getStringFromExtras(extras=" + JWLog.bundleInfo(extras) + ", key=" + key + ")");
        if (extras == null) return null;
        String value = extras.getString(key, null);
        if (value == null) {
            String infoJson = extras.getString("info", null);
            if (infoJson == null) return null;
        
            try {
                JSONObject obj = new JSONObject(infoJson);
                value = obj.optString(key, null);                
            } catch (Exception e) {
                JWLog.w(TAG, "getStringFromExtras: failed parsing info JSON: " + e.getMessage());
            }
        }
        return value;
    }

    private String getSubtitleFromExtras(Bundle extras) {
        JWLog.d(TAG, "getSubtitleFromExtras(extras=" + JWLog.bundleInfo(extras) + ")");
        if (extras == null) return null;

        String subtitle = null;
        String infoJson = extras.getString("info", null);
        if (infoJson == null) return null;

        try {
            JSONObject obj = new JSONObject(infoJson);
            if (obj.has("series") && obj.get("series") instanceof JSONObject) {
                try { 
                    JSONObject series = (JSONObject)obj.get("series");
                    subtitle = series.optString("name", null); 
                } catch (Exception ignore) {
                    JWLog.e(TAG, "getSubtitleFromExtras Throwable series" + ignore);
                }
            }
        } catch (Exception e) {
            JWLog.w(TAG, "getSubtitleFromExtras: failed parsing info JSON: " + e.getMessage());
        }
        return subtitle;
    }

    private String getImageFromExtras(Bundle extras) {
        JWLog.d(TAG, "getImageFromExtras(extras=" + JWLog.bundleInfo(extras) + ")");
        if (extras == null) return null;

        String image = null;
        String infoJson = extras.getString("info", null);
        if (infoJson == null) return null;

        try {
            JSONObject obj = new JSONObject(infoJson);
            image = normalizeImage(obj.optString("image", null));
            if (obj.has("series") && obj.get("series") instanceof JSONObject) {
                try { 
                    JSONObject series = (JSONObject)obj.get("series");
                    // Prefer extras image if valid; otherwise, try series image
                    String seriesImage = normalizeImage(series.optString("image", null));
                    if (image == null && seriesImage != null) {
                        image = "https://res.cloudinary.com/ouinternal/image/upload/c_scale,f_auto,q_auto,w_275/" + seriesImage + ".jpeg";
                        JWLog.d(TAG, "Image from series = " + image);
                    } else if (image != null) {
                        JWLog.d(TAG, "Image from extras = " + image);
                    } else {
                        JWLog.d(TAG, "No image provided in extras/series");
                    }
                } catch (Exception ignore) {
                    JWLog.e(TAG, "getImageFromExtras Throwable series" + ignore);
                }
            }
        } catch (Exception e) {
            JWLog.w(TAG, "getImageFromExtras: failed parsing info JSON: " + e.getMessage());
        }
        return image;
    }

    private void updatePlayerState(PlayerState playerState) {
        JWLog.d(TAG, "updatePlayerState(playerState=" + playerState + ")", true);
        PlaybackStateCompatWrapper currentPlaybackState = this.mediaSessionStateProvider.getPlaybackState();
        PlaybackStateCompatWrapper.Builder playbackStateBuilder = new PlaybackStateCompatWrapper.Builder(currentPlaybackState);
        long notificationCapabilities = this.serviceMediaApi.getNotificationCapabilities();
        playbackStateBuilder.builder.setActions(
            notificationCapabilities | 
            PlaybackStateCompat.ACTION_SEEK_TO | 
            PlaybackStateCompat.ACTION_SKIP_TO_NEXT | 
            PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS);
        byte playbackState = 0;
        switch (playerState) {
            case PLAYING:
                playbackState = PlaybackStateCompat.STATE_PLAYING;
                break;
            case PAUSED:
                playbackState = PlaybackStateCompat.STATE_PAUSED;
                break;
            case BUFFERING:
                playbackState = PlaybackStateCompat.STATE_BUFFERING;
                break;
            case ERROR:
                playbackState = PlaybackStateCompat.STATE_ERROR;
                break;
            case IDLE:
            default:
                playbackState = PlaybackStateCompat.STATE_STOPPED;
        }
        
        // Get player position
        // Use actual player position, not controller extras
        long positionMs = 0L;
        try {
            positionMs = (long) (this.jwPlayer != null ? this.jwPlayer.getPosition() * 1000 : 0);
        } catch (Exception ex) {
            positionMs = 0L;
        }

        float speed = (playbackState == PlaybackStateCompat.STATE_PLAYING) ? currentSpeed : 0.0F;
        playbackStateBuilder.builder
            .setState(playbackState, positionMs, speed);

        PlaybackStateCompatWrapper updatedPlaybackState =  new PlaybackStateCompatWrapper(playbackStateBuilder.builder.build());
        this.mediaSessionStateProvider.mediaSessionCompat.setPlaybackState(updatedPlaybackState.playbackStateCompat);
        boolean isActive = playerState != PlayerState.ERROR && playerState != PlayerState.IDLE;
        this.mediaSessionStateProvider.mediaSessionCompat.setActive(isActive);
        if (isActive) {
            this.rnjwNotificationHelper.showNotification(this.context, this.mediaSessionStateProvider, this.serviceMediaApi);
        } else {
            RNJWNotificationHelper currentNotificationHelper;
            (currentNotificationHelper = this.rnjwNotificationHelper).notificationManager.cancel(currentNotificationHelper.notificationId);
        }
    }

    private void updatePlaylistItem(PlaylistItem playlistItem) {
        JWLog.d(TAG, "updatePlaylistItem(item=" + JWLog.playlistItemInfo(playlistItem) + ")", true);
        if (playlistItem == null || this.jwPlayer == null) {
            return;
        }
        updateCurrentMediaAvailability(playlistItem, "updatePlaylistItem");

        PlaylistItem currentItem = this.jwPlayer.getPlaylistItem();

        // Keep last known or existing metadata
        MediaMetadataCompat existing = (this.mediaSessionStateProvider != null && this.mediaSessionStateProvider.mediaSessionCompat != null)
                ? this.mediaSessionStateProvider.mediaSessionCompat.getController().getMetadata()
                : null;
        MediaMetadataCompat.Builder builder = (existing == null)
                ? new MediaMetadataCompat.Builder()
                : new MediaMetadataCompat.Builder(existing);

        String previousTitle = existing != null ? existing.getString(MediaMetadataCompat.METADATA_KEY_DISPLAY_TITLE) : null;
        String previousMediaId = existing != null ? existing.getString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID) : null;
        if (BuildConfig.DEBUG) {
            JWLog.d(TAG, "metadata-update-before mediaId="
                + playlistItem.getMediaId()
                + ", title=" + playlistItem.getTitle()
                + ", previousMediaId=" + previousMediaId
                + ", previousTitle=" + previousTitle
                + ", " + getCurrentPlaybackDebugInfo("metadata-update"));
        }

        builder.putString("android.media.metadata.DISPLAY_TITLE", 
            playlistItem.getTitle() != null ? playlistItem.getTitle() : "");

        String subtitle = playlistItem.getDescription();
        if (subtitle == null || subtitle.isEmpty()) {
            subtitle = externalSubtitle != null ? externalSubtitle : "";
        }

        builder.putString("android.media.metadata.DISPLAY_SUBTITLE", 
            subtitle);

        builder.putString("android.media.metadata.MEDIA_ID", 
            playlistItem.getMediaId() != null ? playlistItem.getMediaId() : "");

        if (playlistItem.getDuration() != null) {
            long durationMs = (long)(playlistItem.getDuration() * 1000);
            builder.putLong("android.media.metadata.DURATION", durationMs);
            lastKnownDurationMs = durationMs;
        }

        if (currentItem != null) {
            builder.putString("android.media.metadata.ARTIST", 
                currentItem.getDescription() != null ? currentItem.getDescription() : "");

            builder.putString("android.media.metadata.TITLE", 
                currentItem.getTitle() != null ? currentItem.getTitle() : "");
        } else {
            builder.putString("android.media.metadata.ARTIST", "");
            builder.putString("android.media.metadata.TITLE", "");
        }

        if (this.mediaSessionStateProvider != null && this.mediaSessionStateProvider.mediaSessionCompat != null) {
            this.mediaSessionStateProvider.mediaSessionCompat.setMetadata(builder.build());
            if (this.serviceMediaApi != null && this.mediaSessionStateProvider.mediaSessionCompat.isActive()) {
                this.rnjwNotificationHelper.showNotification(this.context, this.mediaSessionStateProvider, this.serviceMediaApi);
            }
        }

        if (playlistItem.getImage() != null && !playlistItem.getImage().isEmpty()) {
            this.updateAlbumArt(playlistItem.getImage());
        }

        // Try to request audio focus when a new item is loaded
        requestAudioFocusForPlayback();
    }

    public void onPlaylistItem(PlaylistItemEvent playlistItemEvent) {
        JWLog.d(TAG, "onPlaylistItem(event.item=" + JWLog.playlistItemInfo(playlistItemEvent != null ? playlistItemEvent.getPlaylistItem() : null) + ")", true);
        this.updatePlaylistItem(playlistItemEvent.getPlaylistItem());
        completionScheduledFromSeek = false;
        PlaylistItem incomingItem = playlistItemEvent != null ? playlistItemEvent.getPlaylistItem() : null;
        
        // Get playlist start time from JS (if available)
        long playlistStartMs = 0L;
        boolean hasExplicitStart = false;
        try {
            if (playlistItemEvent != null && playlistItemEvent.getPlaylistItem() != null) {
                Double startSeconds = playlistItemEvent.getPlaylistItem().getStartTime(); // seconds
                if (startSeconds != null && startSeconds > 0) {
                    playlistStartMs = (long) (startSeconds * 1000L);
                    hasExplicitStart = playlistStartMs > 0;
                }
            }
        } catch (Exception t) {
            JWLog.w(TAG, "onPlaylistItem: failed reading playlist starttime: " + t.getMessage());
        }

        String inferredMediaId = inferMediaIdFromPlaylistItem(incomingItem);
        if (isAppPostMediaId(inferredMediaId)) {
            String normalizedMediaId = inferredMediaId.trim();
            androidAutoSelectedMediaId = normalizedMediaId;
        }

        boolean hasPendingAndroidAutoResume = externalMediaId != null
            && pendingSeekMs != null
            && pendingSeekMs >= 0;
        
        // Only treat the new playlist item as Android Auto handoff when a real
        // resume target is already armed for this selection. A stale externalMediaId
        // from a previous item must not override the current item's explicit start.
        if (hasPendingAndroidAutoResume) {
            isPlayingFromAndroidAuto = true;
            androidAutoHandoffStartTime = System.currentTimeMillis();
            lastFocusRequestTime = System.currentTimeMillis();
            JWLog.d(TAG, "onPlaylistItem: Marked as Android Auto handoff (will ignore AUDIOFOCUS_LOSS for " + FOCUS_LOSS_IGNORE_WINDOW_MS + "ms)");
        } else {
            resetAndroidAutoFlag();
            if (inferredMediaId != null && !inferredMediaId.isEmpty()) {
                if (externalMediaId == null || !inferredMediaId.equals(externalMediaId)) {
                    JWLog.d(TAG, "onPlaylistItem: non-handoff media identity refresh externalMediaId=" + externalMediaId + " -> " + inferredMediaId);
                }
                externalMediaId = inferredMediaId;
            }
            JWLog.d(TAG, "onPlaylistItem: skipping Android Auto handoff resume (externalMediaId=" + externalMediaId
                + ", pendingSeekMs=" + pendingSeekMs
                + ", hasExplicitStart=" + hasExplicitStart + ")");
        }
        
        // Priority for resume position:
        // 1. Static cache (most recent position from background player cleanup)
        // 2. MediaItemsResumeProvider (original extras bundle)
        // 3. Explicit playlist startTime from JS
        // 4. Default to 0
        // Try to apply pending seek when the item switches
        // Resolve the app-level mediaId to look up the MediaBrowser item
        if (hasPendingAndroidAutoResume || hasExplicitStart) {
            JWLog.d(TAG, "onPlaylistItem: Resolving resume position for mediaId=" + externalMediaId + " (hasExplicitStart=" + hasExplicitStart + ", playlistStartMs=" + playlistStartMs + ")");
            
            // CRITICAL: Check static cache FIRST - this has the most recent position from background player
            // The cache survives instance recreation and is updated by storeSeekPosition during cleanup
            Long cachedPositionMs = lastKnownPositionCache.get(externalMediaId);
            long savedPositionMs;
            String positionSource;
            
            if (hasPendingAndroidAutoResume) {
                if (cachedPositionMs != null && cachedPositionMs >= 0) {
                    savedPositionMs = cachedPositionMs;
                    positionSource = "static cache (most recent)";
                    JWLog.d(TAG, "onPlaylistItem: Found position " + savedPositionMs + "ms in static cache for mediaId=" + externalMediaId);
                } else {
                    // Fallback to MediaItemsResumeProvider (original extras bundle)
                    savedPositionMs = queryResumeViaReflection(externalMediaId); // contract: -1 = absent
                    positionSource = "MediaItemsResumeProvider";
                }
            } else {
                savedPositionMs = -1L;
                positionSource = "explicit start only (non-handoff)";
            }
            long resumeMs;
            
            JWLog.d(TAG, "onPlaylistItem: Position sources - savedPositionMs=" + savedPositionMs + "ms (from " + positionSource + "), playlistStartMs=" + playlistStartMs + "ms (from extras)");
            
            if (savedPositionMs >= 0) {
                // Use saved position (most recent from background player or previous session)
                resumeMs = savedPositionMs;
                JWLog.d(TAG, "onPlaylistItem: using saved position resumeMs=" + resumeMs + "ms (Priority 1: Most recent saved position, overriding playlist starttime=" + playlistStartMs + "ms)");
            } else if (hasExplicitStart) {
                // Fallback to playlist startTime if no saved position
                resumeMs = playlistStartMs;
                JWLog.d(TAG, "onPlaylistItem: no saved position, using playlist starttime from JS as resumeMs=" + resumeMs + "ms (Priority 2: Explicit startTime from JS, no saved position available)");
            } else {
                // No saved position and no explicit start time
                resumeMs = -1L;
                JWLog.d(TAG, "onPlaylistItem: no saved position or playlist starttime, resumeMs=" + resumeMs + " (will use default 0ms)");
            }
            
            
            if (resumeMs >= 0) {
                pendingSeekMs = resumeMs;
                pendingSeekApplied = false;
                autoHandoffSeekAttempts = 0; // Reset for new handoff
                JWLog.d(TAG, "onPlaylistItem: Set pendingSeekMs=" + pendingSeekMs + "ms for Android Auto handoff (autoHandoffSeekAttempts reset to 0)");
                
                // If this is Android Auto handoff, trigger play immediately
                // The onSeeked handler will protect against spurious seeks:
                // - Phase 1: Block spurious seek-to-0 entirely and re-seek to pendingSeekMs
                // - Phase 2: Counter-based fallback for other wrong positions
                if (isPlayingFromAndroidAuto && jwPlayer != null) {
                    PlayerState currentState = jwPlayer.getState();
                    JWLog.d(TAG, "onPlaylistItem: Android Auto handoff detected (state=" + currentState + ", resumeMs=" + resumeMs + ", will trigger play for counter-based correction)");
                    
                    if (currentState == PlayerState.IDLE || currentState == PlayerState.PAUSED) {
                        try {
                            if (shouldBlockPlayForNetwork("onPlaylistItem-handoff")) {
                                publishNetworkUnavailableState("onPlaylistItem-handoff");
                                return;
                            }

                            // Trigger play first - this will start buffering and trigger onSeeked events
                            // The two-phase defense in onSeeked will handle position correction:
                            // - Phase 1: Block spurious seek-to-0 and re-seek
                            // - Phase 2: Counter-based validation for other wrong positions
                            jwPlayer.play();
                            JWLog.d(TAG, "onPlaylistItem: Triggered play for Android Auto handoff (state was " + currentState + "), counter logic will correct position in onSeeked to " + pendingSeekMs + "ms");
                            // pendingSeekMs is still set, onSeeked will validate and apply it
                        } catch (Exception ex) {
                            JWLog.w(TAG, "onPlaylistItem: Failed to trigger play during handoff: " + ex.getMessage());
                            // Fallback: try applying seek directly
                            JWLog.d(TAG, "onPlaylistItem: Falling back to applyPendingSeekWhenReady");
                            applyPendingSeekWhenReady(playlistItemEvent.getPlaylistItem());
                        }
                    } else {
                        // Player already playing/buffering, just let seek apply
                        JWLog.d(TAG, "onPlaylistItem: Player state is " + currentState + ", letting applyPendingSeekWhenReady handle position");
                        applyPendingSeekWhenReady(playlistItemEvent.getPlaylistItem());
                    }
                } else {
                    JWLog.d(TAG, "onPlaylistItem: Not Android Auto handoff (isPlayingFromAndroidAuto=false), applying pending seek normally");
                    applyPendingSeekWhenReady(playlistItemEvent.getPlaylistItem());
                }
            } else {
                JWLog.d(TAG, "onPlaylistItem: No valid resume position (resumeMs<=0), clearing pendingSeekMs");
                pendingSeekMs = null;
                pendingSeekApplied = false;
            }
        } else {
            JWLog.d(TAG, "onPlaylistItem: no handoff resume and no explicit start; leaving playlist start handling to current item without stale resume override");
        }
    }

    public void onError(ErrorEvent errorEvent) {
        JWLog.e(TAG, "onError(event=" + (errorEvent != null ? errorEvent.getMessage() : "null") + ")");
        try {
            if (currentMediaRequiresNetwork && !isNetworkAvailableForPlayback()) {
                long positionMs = rememberPlaybackPosition("network-error");
                pausedByNetwork = true;
                playBlockedByNetwork = true;
                pendingResumeAfterNetworkRecovery = !pausedByUser;
                lastPlaybackWasActive = !pausedByUser;
                recoveryNeedsPlayerReload = true;
                networkRecoveryInProgress = false;
                networkRecoveryReloadAttempts = 0;
                updatePlaybackState(jwPlayer, PlaybackStateCompat.STATE_PAUSED, positionMs);
                if (this.mediaSessionStateProvider != null && this.mediaSessionStateProvider.mediaSessionCompat != null) {
                    this.mediaSessionStateProvider.mediaSessionCompat.setActive(true);
                }
                JWLog.d(TAG, "NETWORK_GUARD: network error kept session paused for recovery positionMs=" + positionMs
                        + " pendingResume=" + pendingResumeAfterNetworkRecovery, true);
                return;
            }

            this.updatePlayerState(PlayerState.ERROR);
            // Instead of releasing the session (which causes Android Auto to lose root and show only Exit),
            // keep it inactive but alive so we can recover or replay without user leaving AA.
            if (this.mediaSessionStateProvider != null && this.mediaSessionStateProvider.mediaSessionCompat != null) {
                try {
                    this.mediaSessionStateProvider.mediaSessionCompat.setActive(false); // mark inactive
                } catch (Exception ignore) {}
            }
        } catch (Exception ex) {
            JWLog.w(TAG, "onError handling failed: " + ex.getMessage());
        }
    }

    public void onAdComplete(AdCompleteEvent adCompleteEvent) {
        JWLog.d(TAG, "onAdComplete()" );
    }

    public void onAdSkipped(AdSkippedEvent adSkippedEvent) {
        JWLog.d(TAG, "onAdSkipped()" );
    }

    public void onAdPlay(AdPlayEvent adPlayEvent) {
        JWLog.d(TAG, "onAdPlay()" );
    }

    public void onAdError(AdErrorEvent adErrorEvent) {
        JWLog.d(TAG, "onAdError()" );
    }

    public void onBuffer(BufferEvent bufferEvent) {
        JWLog.d(TAG, "onBuffer()", true);
        captureDurationSnapshot();
        if (currentMediaRequiresNetwork && !isNetworkAvailableForPlayback()) {
            long positionMs = rememberPlaybackPosition("buffer-while-offline");
            pausedByNetwork = !pausedByUser;
            pendingResumeAfterNetworkRecovery = !pausedByUser;
            lastPlaybackWasActive = !pausedByUser;
            updatePlaybackState(jwPlayer, PlaybackStateCompat.STATE_BUFFERING, positionMs);
            return;
        }

        this.updatePlayerState(PlayerState.BUFFERING);
        updatePlaybackState(jwPlayer, PlaybackStateCompat.STATE_BUFFERING);
        // Try to apply pending seek while buffering
        applyPendingSeekWhenReady(jwPlayer != null ? jwPlayer.getPlaylistItem() : null);
    }

    @Override
    public void onSeek(SeekEvent seekEvent) {
        double position = seekEvent != null ? seekEvent.getPosition() : 0.0;
        double offset = seekEvent != null ? seekEvent.getOffset() : 0.0;
        int androidVersion = Build.VERSION.SDK_INT;
        JWLog.d(TAG, "onSeek(position=" + position + ", offset=" + offset + ", Android=" + androidVersion + ")");
        
        if (suppressNextSeekCallback) {
            JWLog.d(TAG, "onSeek: suppressing callback triggered by guarded reseek");
            suppressNextSeekCallback = false;
            return;
        }

        if (seekEvent != null) {
            long offsetMs = (long) (offset * 1000L);
            long safeMs = sanitizeSeekPosition(offsetMs);

            // During Android Auto handoff, if JWPlayer is seeking to autostart position (mStartTime),
            // DON'T interrupt it - let it complete, then we'll correct the position in onSeeked
            if (isPlayingFromAndroidAuto && pendingSeekMs != null && !pendingSeekApplied && safeMs < pendingSeekMs) {
                JWLog.d(TAG, "onSeek: Android Auto handoff - JWPlayer autostart detected at " + safeMs + "ms, will correct to " + pendingSeekMs + "ms in onSeeked (Android " + androidVersion + ")");
            }

            maybeClearResetFlagForSeek(safeMs);

            if (safeMs != offsetMs) {
                JWLog.d(TAG, "onSeek: reseeking to guarded position " + safeMs + " ms (requested=" + offsetMs + " ms)");
                suppressNextSeekCallback = true;
                performSeekTo(safeMs);
                return;
            }

            lastRequestedSeekPositionMs = offsetMs;
            lastSeekRequestedWhilePaused = !isCurrentlyPlaying();
        }
    }

    @Override
    public void onSeeked(SeekedEvent seekedEvent) {
        double positionSeconds = seekedEvent != null ? seekedEvent.getPosition() : 0.0;
        long eventPositionMs = (long) (positionSeconds * 1000L);
        int androidVersion = Build.VERSION.SDK_INT;

        // CRITICAL: During Android Auto handoff, completely ignore spurious 0 seeks
        // Android 12/14 emit a second seek-to-0 after the correct resume seek - this must be blocked
        // Guard: only suppress the 0 event when the RESUME TARGET itself is > 0.
        // If target IS 0 (fresh/never-played media), position=0 is correct — don't loop.
        if (isPlayingFromAndroidAuto && pendingSeekMs != null && pendingSeekMs > 0 && eventPositionMs == 0) {
            JWLog.d(TAG, "onSeeked: IGNORING spurious 0 seek during AA handoff (pendingSeekMs=" + pendingSeekMs + ", applied=" + pendingSeekApplied + ")");
            // If we haven't successfully applied the pending seek yet, re-seek to the correct target
            if (!pendingSeekApplied && jwPlayer != null) {
                JWLog.d(TAG, "onSeeked: Re-seeking to pendingSeekMs=" + pendingSeekMs + " after spurious 0");
                suppressNextSeekCallback = true;
                lastRequestedSeekPositionMs = pendingSeekMs;
                jwPlayer.seek(pendingSeekMs / 1000.0);
            }
            return; // Don't process the spurious 0 event at all
        }

        long effectivePositionMs = eventPositionMs > 0 ? eventPositionMs : lastRequestedSeekPositionMs;
        effectivePositionMs = sanitizeSeekPosition(effectivePositionMs);

        JWLog.d(TAG, "onSeeked(position=" + positionSeconds + ", effectiveMs=" + effectivePositionMs + ", Android=" + androidVersion + ")");

        // Android Auto handoff correction: Two-phase defense strategy
        // Phase 1 (above): Block spurious seek-to-0 entirely (Android 12/14 quirk)
        // Phase 2 (below): Counter-based correction for other wrong positions
        // - Track onSeeked attempts when position is wrong (but not 0)
        // - Allow first attempt, correct on second if still wrong
        // - Immediately accept correct positions
        if (isPlayingFromAndroidAuto && pendingSeekMs != null && !pendingSeekApplied) {
            autoHandoffSeekAttempts++;
            long deltaFromTarget = Math.abs(effectivePositionMs - pendingSeekMs);
            
            JWLog.d(TAG, "onSeeked: Android " + androidVersion + " handoff attempt #" + autoHandoffSeekAttempts 
                + " (effective=" + effectivePositionMs + "ms, expected=" + pendingSeekMs + "ms, delta=" + deltaFromTarget + "ms)");
            JWLog.d(TAG, "onSeeked: Counter-based correction active - tracking seeks to validate final position");
            
            if (deltaFromTarget > 2000) {
                if (autoHandoffSeekAttempts == 1) {
                    // First onSeeked with wrong position - allow it, wait for second
                    JWLog.d(TAG, "onSeeked: First attempt wrong, waiting for second onSeeked");
                    return; // Keep pendingSeekMs guard active
                } else {
                    // Second (or later) onSeeked still wrong - correct now
                    JWLog.d(TAG, "onSeeked: Second attempt still wrong (" + effectivePositionMs + "ms), correcting to " + pendingSeekMs + "ms");
                    try {
                        if (jwPlayer != null) {
                            double correctPositionSeconds = pendingSeekMs / 1000.0;
                            jwPlayer.seek(correctPositionSeconds);
                            pendingSeekApplied = true;
                            lastRequestedSeekPositionMs = pendingSeekMs;
                            JWLog.d(TAG, "onSeeked: Correction seek initiated to " + correctPositionSeconds + "s");
                        }
                    } catch (Exception seekEx) {
                        JWLog.w(TAG, "onSeeked: Failed to correct seek position: " + seekEx.getMessage());
                    }
                    return; // Don't store the wrong position
                }
            } else {
                // Position is correct - use validated position and clear immediately
                JWLog.d(TAG, "onSeeked: Position correct on attempt #" + autoHandoffSeekAttempts + ", using validated position and clearing pendingSeekMs");
                effectivePositionMs = pendingSeekMs; // Use the validated stored position
                pendingSeekMs = null;
                pendingSeekApplied = true;
                // Continue to normal playback state handling with corrected position
            }
        }

        // Non-Android-Auto explicit-start seek should complete once the target is reached.
        // Without this, pendingSeekApplied stays false and applyPendingSeekWhenReady can
        // repeatedly re-issue the same seek on play/buffer callbacks.
        if (!isPlayingFromAndroidAuto && pendingSeekMs != null && !pendingSeekApplied) {
            long deltaFromTarget = Math.abs(effectivePositionMs - pendingSeekMs);
            if (deltaFromTarget < 2000) {
                pendingSeekApplied = true;
                pendingSeekMs = null;
            }
        }

        if (jwPlayer != null) {
            PlayerState stateAfterSeek;
            try {
                stateAfterSeek = jwPlayer.getState();
            } catch (Exception ignore) {
                stateAfterSeek = null;
            }

            int playbackState;
            if (stateAfterSeek == PlayerState.PLAYING) {
                playbackState = PlaybackStateCompat.STATE_PLAYING;
            } else if (stateAfterSeek == PlayerState.BUFFERING) {
                playbackState = lastSeekRequestedWhilePaused
                        ? PlaybackStateCompat.STATE_PAUSED
                        : PlaybackStateCompat.STATE_BUFFERING;
            } else {
                playbackState = PlaybackStateCompat.STATE_PAUSED;
            }

            updatePlaybackState(jwPlayer, playbackState, effectivePositionMs);

            if (lastSeekRequestedWhilePaused) {
                updatePlayerState(PlayerState.PAUSED);
            }
        }

        maybeClearResetFlagForSeek(effectivePositionMs);

        // Clear pendingSeekMs if we've reached the target position (within 2 seconds tolerance)
        if (pendingSeekApplied && pendingSeekMs != null) {
            long delta = Math.abs(effectivePositionMs - pendingSeekMs);
            if (delta < 2000) {
                JWLog.d(TAG, "onSeeked: target position reached (" + effectivePositionMs + " ~= " + pendingSeekMs + "), clearing pendingSeekMs");
                pendingSeekMs = null;
            } else {
                JWLog.d(TAG, "onSeeked: position mismatch, keeping pendingSeekMs guard (effective=" + effectivePositionMs + ", expected=" + pendingSeekMs + ", delta=" + delta + "ms)");
            }
        }

        // Avoid accidentally wiping a valid resume position with 0 during
        // handoff/guarded seeks. Only persist 0 when we are truly at start
        // without any pending resume semantics.
        if (effectivePositionMs <= 0
            && (pendingSeekMs != null && pendingSeekMs > 0
                || resetToStartAfterSeekCompletion
                || completionScheduledFromSeek
                || isPlayingFromAndroidAuto)) {
            JWLog.d(TAG, "onSeeked: skipping storeSeekPosition(0) to preserve resume state " +
                "(pendingSeekMs=" + pendingSeekMs +
                ", resetToStartAfterSeekCompletion=" + resetToStartAfterSeekCompletion +
                ", completionScheduledFromSeek=" + completionScheduledFromSeek +
                ", isPlayingFromAndroidAuto=" + isPlayingFromAndroidAuto + ")");
        } else {
            storeSeekPosition(effectivePositionMs);
        }

        maybeCompleteFromSeek(effectivePositionMs);

        lastSeekRequestedWhilePaused = false;
        lastRequestedSeekPositionMs = -1L;
        suppressNextSeekCallback = false;
    }

    public void onPause(PauseEvent pauseEvent) {
        JWLog.d(TAG, "onPause(isPlayingFromAndroidAuto=" + isPlayingFromAndroidAuto + ")", true);
        rememberPlaybackPosition("pause-event");
        boolean recentUserGesture = hasRecentUserPlaybackGesture();
        
        // Check if handoff flag is stale (been set for too long)
        if (isPlayingFromAndroidAuto && androidAutoHandoffStartTime > 0) {
            long handoffDuration = System.currentTimeMillis() - androidAutoHandoffStartTime;
            if (handoffDuration > ANDROID_AUTO_HANDOFF_TIMEOUT_MS) {
                JWLog.d(TAG, "onPause: Handoff flag timeout (" + handoffDuration + "ms) - clearing and allowing pause");
                resetAndroidAutoFlag();
            }
        }
        
        // Skip pause during Android Auto handoff - keep playing
        if (isPlayingFromAndroidAuto) {
            JWLog.d(TAG, "onPause: Ignoring pause during Android Auto handoff, forcing play");
            try {
                if (jwPlayer != null) {
                    jwPlayer.play();
                }
            } catch (Exception ex) {
                JWLog.w(TAG, "onPause: Failed to resume play: " + ex.getMessage());
            }
            return;
        }
        
        if (!systemPauseInProgress && recentUserGesture) {
            pausedByUser = true;
            pausedByNetwork = false;
            playBlockedByNetwork = false;
            pendingResumeAfterNetworkRecovery = false;
            recoveryNeedsPlayerReload = false;
            networkRecoveryInProgress = false;
            lastPlaybackWasActive = false;
            JWLog.d(TAG, "USER_INTENT: pause event treated as user pause recentGesture=true networkAvailable="
                    + isNetworkAvailableForPlayback(), true);
        } else if (currentMediaRequiresNetwork && !isNetworkAvailableForPlayback() && !pausedByUser) {
            pausedByNetwork = true;
            pendingResumeAfterNetworkRecovery = true;
            lastPlaybackWasActive = true;
        } else if (pausedByUser) {
            pausedByNetwork = false;
            playBlockedByNetwork = false;
            pendingResumeAfterNetworkRecovery = false;
            recoveryNeedsPlayerReload = false;
            networkRecoveryInProgress = false;
            lastPlaybackWasActive = false;
        } else if (!systemPauseInProgress && !networkRecoveryInProgress && !pausedByNetwork && !playBlockedByNetwork) {
            pausedByUser = true;
            pendingResumeAfterNetworkRecovery = false;
            lastPlaybackWasActive = false;
        }

        this.updatePlayerState(PlayerState.PAUSED);
        updatePlaybackState(jwPlayer, PlaybackStateCompat.STATE_PAUSED);
    }

    public void onPlay(PlayEvent playEvent) {
        JWLog.d(TAG, "onPlay()", true);
        if (shouldBlockPlayForNetwork("onPlay-offline")) {
            publishNetworkUnavailableState("onPlay-offline");
            return;
        }

        long now = SystemClock.elapsedRealtime();
        if (suppressNextOnPlayAfterSeekCompletion) {
            if (now <= suppressOnPlayExpiryMs) {
                JWLog.d(TAG, "onPlay: suppressing auto-play after seek completion");
                suppressNextOnPlayAfterSeekCompletion = false;
                performPause();
                return;
            }

            suppressNextOnPlayAfterSeekCompletion = false;
        }

        resetPlaybackToStartIfNeeded("onPlay");
        captureDurationSnapshot();
        pausedByUser = false;
        pausedByNetwork = false;
        playBlockedByNetwork = false;
        pendingResumeAfterNetworkRecovery = false;
        recoveryNeedsPlayerReload = false;
        networkRecoveryInProgress = false;
        lastPlaybackWasActive = true;
        networkRecoveryReloadAttempts = 0;
        rememberPlaybackPosition("play-event");
        this.updatePlayerState(PlayerState.PLAYING);
        updatePlaybackState(jwPlayer, PlaybackStateCompat.STATE_PLAYING);
        
        // Clear Android Auto handoff flag once playback successfully starts after handoff
        // This ensures pause button works after handoff completes
        if (isPlayingFromAndroidAuto && pendingSeekApplied) {
            JWLog.d(TAG, "onPlay: Android Auto handoff completed successfully, clearing flag");
            resetAndroidAutoFlag();
        }
        
        // Try to apply pending seek as soon as playback starts
        applyPendingSeekWhenReady(jwPlayer != null ? jwPlayer.getPlaylistItem() : null);
    }

    public void onPlaylistComplete(PlaylistCompleteEvent playlistCompleteEvent) {
        JWLog.d(TAG, "onPlaylistComplete()", true);
        boolean triggeredBySeekCompletion = completionScheduledFromSeek;
        completionScheduledFromSeek = false;
        resetAndroidAutoFlag();

        // Notify React Native about playlist completion via MediaBrowserService
        // This allows RN to decide whether to auto-advance to next track
        try {
            JWLog.d(TAG, "onPlaylistComplete: notifying React Native via MediaBrowserService with mediaId=" + externalMediaId);
            Class<?> mediaBrowserServiceClass = Class.forName("com.mediabrowser.MediaBrowserService");
            java.lang.reflect.Method sendCompleteMethod = mediaBrowserServiceClass.getMethod("sendPlaylistCompleteToReactNative", String.class);
            sendCompleteMethod.invoke(null, externalMediaId);
            JWLog.d(TAG, "onPlaylistComplete: React Native notified successfully");
        } catch (Exception e) {
            JWLog.w(TAG, "onPlaylistComplete: Could not notify MediaBrowserService: " + e.getMessage());
        }

        if (this.mediaSessionStateProvider == null || this.mediaSessionStateProvider.mediaSessionCompat == null) return;

        try {
            // Build a PAUSED playback state
            // This keeps the progress bar interactive and shows correct button state
            PlaybackStateCompatWrapper capsWrapper = this.mediaSessionStateProvider.getPlaybackState();
            PlaybackStateCompatWrapper.Builder stateBuilder = new PlaybackStateCompatWrapper.Builder(capsWrapper);

            // Include seek actions so progress bar remains interactive
            long actions = PlaybackStateCompat.ACTION_PLAY |
                    PlaybackStateCompat.ACTION_PLAY_FROM_MEDIA_ID |
                    PlaybackStateCompat.ACTION_SEEK_TO |
                    PlaybackStateCompat.ACTION_SKIP_TO_NEXT |
                    PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS;

            if (this.serviceMediaApi != null) {
                try {
                    long caps = this.serviceMediaApi.getNotificationCapabilities();
                    actions |= caps;
                } catch (Exception ex) {
                    JWLog.w(TAG, "Could not get service capabilities: " + ex.getMessage());
                }
            }

            stateBuilder.builder.setActions(actions);

            try {
                // Get the total duration for positioning at the end
                long totalDurationMs = 0;
                long positionMs = 0;

                try {
                    if (this.jwPlayer != null && this.jwPlayer.getPlaylistItem() != null && this.jwPlayer.getPlaylistItem().getDuration() != null) {
                        totalDurationMs = (long)(this.jwPlayer.getPlaylistItem().getDuration() * 1000);
                        positionMs = totalDurationMs; // Position at the end
                    } else {
                        // Fallback: try to get current position
                        positionMs = this.jwPlayer != null ? (long)(this.jwPlayer.getPosition() * 1000) : 0L;
                    }
                } catch (Exception ex) {
                    JWLog.w(TAG, "Could not get duration/position for completion: " + ex.getMessage());
                    positionMs = 0;
                }

                long durationCandidate = totalDurationMs > 0 ? totalDurationMs : positionMs;
                long resumePositionMs = 0L;
                if (triggeredBySeekCompletion) {
                    resetToStartAfterSeekCompletion = true;
                    if (durationCandidate > 0) {
                        positionMs = durationCandidate;
                    }
                } else {
                    resetToStartAfterSeekCompletion = false;
                    if (durationCandidate > 0) {
                        positionMs = durationCandidate;
                    }
                }
                storeSeekPosition(resumePositionMs);

                // Set to PAUSED state with normal playback rate so progress bar stays interactive
                // Position at the end, but with rate 1.0f so seeking works
                stateBuilder.builder.setState(PlaybackStateCompat.STATE_PAUSED, positionMs, 1.0f);

                this.mediaSessionStateProvider.mediaSessionCompat.setPlaybackState(new PlaybackStateCompatWrapper(stateBuilder.builder.build()).playbackStateCompat);

                // Keep session active so UI remains available
                this.mediaSessionStateProvider.mediaSessionCompat.setActive(true);

                // Explicitly show notification (like the a(PlayerState) method does)
                this.rnjwNotificationHelper.showNotification(this.context, this.mediaSessionStateProvider, this.serviceMediaApi);

                try {
                    updatePlaybackState(jwPlayer, PlaybackStateCompat.STATE_PAUSED);
                } catch (Exception ignore) {}
            } catch (Exception ex) {
                JWLog.w(TAG, "Failed to set completion state: " + ex.getMessage());
            }
        } catch (Exception ex) {
            JWLog.w(TAG, "Failed in onPlaylistComplete: " + ex.getMessage());
        }
    }

    void updateAlbumArt(String bitmapPath) {
        JWLog.d(TAG, "updateAlbumArt(bitmapPath=" + bitmapPath + ")");
        artworkExecutor.submit(() -> {
            if (this.mediaSessionStateProvider != null) {
                MediaMetadataCompat mediaMetadataCompat;
                MediaMetadataCompat.Builder builder = (mediaMetadataCompat = this.mediaSessionStateProvider.mediaSessionCompat.getController().getMetadata()) == null ? new MediaMetadataCompat.Builder() : new MediaMetadataCompat.Builder(mediaMetadataCompat);

                builder.putString(MediaMetadataCompat.METADATA_KEY_ALBUM_ART_URI, bitmapPath);
                builder.putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON_URI, bitmapPath); 

                MediaMetadataCompat metadataCompat = builder.build();
                this.mediaSessionStateProvider.mediaSessionCompat.setMetadata(metadataCompat);
            }
        });
    }

    private long queryResumeViaReflection(String mediaId) {
        JWLog.d(TAG, "queryResumeViaReflection(mediaId=" + mediaId + ")");
        if (mediaId == null || mediaId.isEmpty()) {
            JWLog.d(TAG, "queryResumeViaReflection: mediaId is null or empty, returning -1");
            return -1L;
        }
        try {
            Class<?> c = Class.forName("com.mediabrowser.MediaItemsResumeProvider");
            java.lang.reflect.Method m = c.getMethod("getResumePositionMs", String.class);
            Object out = m.invoke(null, mediaId);
            long result = (out instanceof Number) ? ((Number) out).longValue() : -1L;
            JWLog.d(TAG, "queryResumeViaReflection: Retrieved position " + result + "ms for mediaId=" + mediaId + " via MediaItemsResumeProvider.getResumePositionMs()");
            return result;
        } catch (Exception t) {
            JWLog.w(TAG, "queryResumeViaReflection: Resume provider unavailable - " + t.getMessage());
            return -1L;
        }
    }

    private String inferMediaIdFromPlaylistItem(PlaylistItem item) {
        if (item == null) {
            return null;
        }

        try {
            String mediaId = item.getMediaId();
            if (mediaId != null && !mediaId.trim().isEmpty()) {
                return mediaId.trim();
            }
        } catch (Exception ignored) {}

        String file = extractPrimarySourceFile(item);
        if (file == null || file.trim().isEmpty()) {
            return null;
        }

        try {
            java.util.regex.Matcher manifestMatcher = MANIFEST_PATTERN.matcher(file);
            if (manifestMatcher.matches()) {
                return manifestMatcher.group(1);
            }
        } catch (Exception ignored) {}

        try {
            java.util.regex.Matcher hlsMatcher = HLS_PATTERN.matcher(file);
            if (hlsMatcher.matches()) {
                return hlsMatcher.group(1);
            }
        } catch (Exception ignored) {}

        return file;
    }

    private String extractPrimarySourceFile(PlaylistItem item) {
        if (item == null) {
            return null;
        }

        try {
            java.util.List<MediaSource> sources = item.getSources();
            if (sources != null && !sources.isEmpty()) {
                MediaSource source = sources.get(0);
                if (source != null && source.getFile() != null) {
                    return source.getFile().trim();
                }
            }
        } catch (Exception ignored) {}

        return null;
    }

    private void applyPendingSeekWhenReady(PlaylistItem item) {
        JWLog.d(TAG, "applyPendingSeekWhenReady(pendingSeekMs=" + pendingSeekMs + ", applied=" + pendingSeekApplied + ", item=" + JWLog.playlistItemInfo(item) + ")");
        if (pendingSeekMs == null || pendingSeekMs < 0 || jwPlayer == null || pendingSeekApplied) return;

        // Do not block on id mismatches: items can legitimately differ across domains
        double duration = 0;
        try { duration = jwPlayer.getDuration(); } catch (Exception ignored) {}
        PlayerState st = jwPlayer.getState();

        if (duration > 0 || st == PlayerState.BUFFERING || st == PlayerState.PLAYING || st == PlayerState.PAUSED) {
            // Seed lastRequestedSeekPositionMs BEFORE calling performSeekTo so onSeeked has a valid fallback
            lastRequestedSeekPositionMs = pendingSeekMs;
            suppressNextSeekCallback = true;
            performSeekTo(pendingSeekMs);
            // DON'T set pendingSeekApplied here - let onSeeked's two-phase defense confirm position first!
            // Phase 1 blocks spurious 0 seeks, Phase 2 counter logic validates other positions
            // DON'T clear pendingSeekMs yet - keep it until onSeeked completes to guard against spurious seek-to-0
            JWLog.d(TAG, "Pending seek initiated with lastRequestedSeekPositionMs=" + lastRequestedSeekPositionMs + ", counter logic will validate in onSeeked");
            
            // If this is Android Auto handoff, force play after seeking
            if (isPlayingFromAndroidAuto && jwPlayer != null) {
                JWLog.d(TAG, "Android Auto handoff detected - forcing play after pending seek");
                try {
                    if (shouldBlockPlayForNetwork("pending-seek-handoff")) {
                        publishNetworkUnavailableState("pending-seek-handoff");
                        return;
                    }

                    jwPlayer.play();
                } catch (Exception ex) {
                    JWLog.w(TAG, "Failed to force play after Android Auto handoff: " + ex.getMessage());
                }
            }
        } else {
            JWLog.d(TAG, "Pending seek not applied; player not ready yet. Duration: " + duration + ", State: " + st);
        }
    }

    private void performPlay() {
        JWLog.d(TAG, "performPlay()");
        suppressNextOnPlayAfterSeekCompletion = false;
        resetPlaybackToStartIfNeeded("performPlay");

        if (shouldBlockPlayForNetwork("performPlay")) {
            publishNetworkUnavailableState("performPlay");
            return;
        }

        pausedByUser = false;

        PlayerState stateBeforePlay = safePlayerState();
        boolean stuckNetworkPlayer = currentMediaRequiresNetwork
            && isNetworkAvailableForPlayback()
            && isPlayerStateNeedingReload(stateBeforePlay)
            && lastKnownRealPlaybackPositionMs > 0;

        if (stuckNetworkPlayer && tryRecreateHeadlessPlayerForRecovery(lastKnownRealPlaybackPositionMs, "performPlay")) {
            return;
        }

        if (currentMediaRequiresNetwork
            && isNetworkAvailableForPlayback()
            && (pausedByNetwork || playBlockedByNetwork || recoveryNeedsPlayerReload || stuckNetworkPlayer)
            && isPlayerStateNeedingReload(stateBeforePlay)) {
            long resumePositionMs = rememberPlaybackPosition("performPlay-reload");
            if (reloadPlayerForNetworkRecovery(resumePositionMs, "performPlay")) {
                return;
            }
        }

        lastPlaybackWasActive = true;

        boolean focusGranted = requestAudioFocusForPlayback();
        if (!focusGranted) {
            JWLog.w(TAG, "Audio focus not granted - proceeding anyway");
        }

        try {
            if (serviceMediaApi != null) {
                serviceMediaApi.onPlay();
            } else if (jwPlayer != null) {
                jwPlayer.play();
            }
            if (jwPlayer != null && safePlayerState() == PlayerState.PLAYING) {
                updatePlaybackState(jwPlayer, PlaybackStateCompat.STATE_PLAYING);
            }
        } catch (Exception e) {
            JWLog.w(TAG, "performPlay error: " + e.getMessage());
        }
    }

    private void performPause() {
        JWLog.d(TAG, "performPause(isPlayingFromAndroidAuto=" + isPlayingFromAndroidAuto + ")", true);
        noteUserPlaybackGesture("media-session-pause");
        rememberPlaybackPosition("performPause");
        if (!systemPauseInProgress) {
            pausedByUser = true;
            pendingResumeAfterNetworkRecovery = false;
            pausedByNetwork = false;
            playBlockedByNetwork = false;
            recoveryNeedsPlayerReload = false;
            networkRecoveryInProgress = false;
            lastPlaybackWasActive = false;
        }
        
        // Check if handoff flag is stale (been set for too long)
        if (isPlayingFromAndroidAuto && androidAutoHandoffStartTime > 0) {
            long handoffDuration = System.currentTimeMillis() - androidAutoHandoffStartTime;
            if (handoffDuration > ANDROID_AUTO_HANDOFF_TIMEOUT_MS) {
                JWLog.d(TAG, "performPause: Handoff flag timeout (" + handoffDuration + "ms) - clearing and allowing pause");
                resetAndroidAutoFlag();
            } else {
                // Skip pause during Android Auto handoff
                JWLog.d(TAG, "performPause: Ignoring during Android Auto handoff");
                return;
            }
        }
        
        try {
            if (serviceMediaApi != null) {
                serviceMediaApi.onPause();
            } else if (jwPlayer != null) {
                jwPlayer.pause();
            }

            if (jwPlayer != null) {
                updatePlaybackState(jwPlayer, PlaybackStateCompat.STATE_PAUSED);
            }
        } catch (Exception e) {
            JWLog.w(TAG, "performPause error: " + e.getMessage());
        }

        captureAndStoreSeekPosition();
    }

    private void performStop() {
        JWLog.d(TAG, "performStop()");
        try {
            if (serviceMediaApi != null) {
                serviceMediaApi.onStop();
            } else if (jwPlayer != null) {
                jwPlayer.stop();
            }

             if (jwPlayer != null) {
                updatePlaybackState(jwPlayer, PlaybackStateCompat.STATE_STOPPED);
            }
        } catch (Exception ex) {
            JWLog.w(TAG, "mediaSessionCallback onStop error: " + ex.getMessage());
        }       

        captureAndStoreSeekPosition();
    }

    /**
     * Schedule a native skip fallback to fire after SKIP_ACK_TIMEOUT_MS (300 ms).
     * If React Native has not called acknowledgeSkip() by then, we fire the JWPlayer
     * internal skip so the user's button press is never silently dropped.
     *
     * @param skipToken The token returned by MediaBrowserService when the RN event was dispatched.
     *                  If null, the fallback is skipped (RN event dispatch failed entirely).
     * @param direction "next" or "previous"
     */
    private void scheduleSkipFallback(final String skipToken, final String direction) {
        if (skipToken == null) return;
        mainHandler.postDelayed(() -> {
            try {
                Class<?> mediaBrowserServiceClass = Class.forName("com.mediabrowser.MediaBrowserService");
                java.lang.reflect.Method isSkipPendingMethod = mediaBrowserServiceClass.getMethod("isSkipPending", String.class);
                Boolean isPending = (Boolean) isSkipPendingMethod.invoke(null, skipToken);
                if (Boolean.TRUE.equals(isPending)) {
                    JWLog.w(TAG, "skip-fallback firing: RN did not ack skipToken=" + skipToken + " command=" + direction);
                    if (serviceMediaApi != null) {
                        if ("next".equals(direction)) {
                            serviceMediaApi.onSkipToNext();
                        } else {
                            serviceMediaApi.onSkipToPrevious();
                        }
                    }
                }
            } catch (Exception e) {
                JWLog.w(TAG, "scheduleSkipFallback error: " + e.getMessage());
            }
        }, 300);
    }

    private void performSkipToNext() {
        JWLog.d(TAG, "performSkipToNext()");
        if (BuildConfig.DEBUG) {
            JWLog.d(TAG, "external-skip-entry " + getCurrentPlaybackDebugInfo("next"));
        }

        // Prefer the current React playlist's app post ID; fall back to the last
        // Android Auto selection when JW only exposes its internal media ID.
        String mediaIdForSkip = resolveMediaIdForSkip("next");
        boolean notifiedReactNative = false;
        String skipToken = null;

        // Notify React Native about skip to next via MediaBrowserService (using reflection)
        // This allows RN to fetch the next post from series and load it
        try {
            Class<?> mediaBrowserServiceClass = Class.forName("com.mediabrowser.MediaBrowserService");
            java.lang.reflect.Method sendSkipNextMethod = mediaBrowserServiceClass.getMethod("sendSkipToNextEventToReactNative", String.class);
            Object result = sendSkipNextMethod.invoke(null, mediaIdForSkip);
            skipToken = (result instanceof String) ? (String) result : null;
            notifiedReactNative = (skipToken != null);
            if (BuildConfig.DEBUG) {
                JWLog.d(TAG, "external-skip-rn-dispatch command=next, mediaIdForSkip=" + mediaIdForSkip + ", skipToken=" + skipToken);
            }
        } catch (Exception e) {
            if (BuildConfig.DEBUG) {
                JWLog.w(TAG, "performSkipToNext: Could not notify MediaBrowserService: " + e.getMessage());
            }
        }

        if (notifiedReactNative && isAppPostMediaId(mediaIdForSkip)) {
            if (BuildConfig.DEBUG) {
                JWLog.d(TAG, "external-skip-native-fallback-skipped command=next, reason=rn-owned-app-post-queue, mediaIdForSkip=" + mediaIdForSkip);
            }
            // Schedule a 300 ms fallback: if RN has not acknowledged the skip, fire native skip
            scheduleSkipFallback(skipToken, "next");
            return;
        }

        // Also try JWPlayer's internal skip (for playlists within a single post)
        try {
            if (serviceMediaApi != null) {
                if (BuildConfig.DEBUG) {
                    JWLog.d(TAG, "external-skip-native-fallback command=next, mediaIdForSkip=" + mediaIdForSkip);
                }
                serviceMediaApi.onSkipToNext();
            }
        } catch (Exception ex) {
            JWLog.w(TAG, "mediaSessionCallback onSkipToNext error: " + ex.getMessage());
        }
    }

    private void performSkipToPrevious() {
        JWLog.d(TAG, "performSkipToPrevious()");
        if (BuildConfig.DEBUG) {
            JWLog.d(TAG, "external-skip-entry " + getCurrentPlaybackDebugInfo("previous"));
        }

        // Prefer the current React playlist's app post ID. See performSkipToNext().
        String mediaIdForSkip = resolveMediaIdForSkip("previous");
        boolean notifiedReactNative = false;
        String skipToken = null;

        // Notify React Native about skip to previous via MediaBrowserService (using reflection)
        // This allows RN to fetch the previous post from series and load it
        try {
            Class<?> mediaBrowserServiceClass = Class.forName("com.mediabrowser.MediaBrowserService");
            java.lang.reflect.Method sendSkipPrevMethod = mediaBrowserServiceClass.getMethod("sendSkipToPreviousEventToReactNative", String.class);
            Object result = sendSkipPrevMethod.invoke(null, mediaIdForSkip);
            skipToken = (result instanceof String) ? (String) result : null;
            notifiedReactNative = (skipToken != null);
            if (BuildConfig.DEBUG) {
                JWLog.d(TAG, "external-skip-rn-dispatch command=previous, mediaIdForSkip=" + mediaIdForSkip + ", skipToken=" + skipToken);
            }
        } catch (Exception e) {
            if (BuildConfig.DEBUG) {
                JWLog.w(TAG, "performSkipToPrevious: Could not notify MediaBrowserService: " + e.getMessage());
            }
        }

        if (notifiedReactNative && isAppPostMediaId(mediaIdForSkip)) {
            if (BuildConfig.DEBUG) {
                JWLog.d(TAG, "external-skip-native-fallback-skipped command=previous, reason=rn-owned-app-post-queue, mediaIdForSkip=" + mediaIdForSkip);
            }
            // Schedule a 300 ms fallback: if RN has not acknowledged the skip, fire native skip
            scheduleSkipFallback(skipToken, "previous");
            return;
        }

        // Also try JWPlayer's internal skip (for playlists within a single post)
        try {
            if (serviceMediaApi != null) {
                if (BuildConfig.DEBUG) {
                    JWLog.d(TAG, "external-skip-native-fallback command=previous, mediaIdForSkip=" + mediaIdForSkip);
                }
                serviceMediaApi.onSkipToPrevious();
            }
        } catch (Exception ex) {
            JWLog.w(TAG, "mediaSessionCallback onSkipToPrevious error: " + ex.getMessage());
        }
    }

    /**
     * Handle seek to position for both UI and background players
     */
    private void performSeekTo(long positionMs) {
        JWLog.d(TAG, "performSeekTo(positionMs=" + positionMs + ")");
        long safePositionMs = sanitizeSeekPosition(positionMs);
        if (safePositionMs != positionMs) {
            JWLog.d(TAG, "performSeekTo: clamped requested position to " + safePositionMs + " ms");
        }

        maybeClearResetFlagForSeek(safePositionMs);

        boolean shouldRequestFocus = false;
        PlayerState previousState = null;

        if (jwPlayer != null) {
            try {
                previousState = jwPlayer.getState();
            } catch (Exception ignore) {
                previousState = null;
            }
        }

        try {
            jwPlayerNativePlaybackHandler.seekToPosition(safePositionMs);
            JWLog.d(TAG, "Performed seek in background player to " + safePositionMs + " ms");
        } catch (Exception handlerSeekError) {
            JWLog.w(TAG, "Background player seek failed: " + handlerSeekError.getMessage());
        }

        double safePositionSeconds = safePositionMs / 1000.0;

        // Always seek UI player too (or as fallback) so its reported position updates promptly
        if (this.jwPlayer != null) {
            try {
                this.jwPlayer.seek(safePositionSeconds);
                JWLog.d(TAG, "Performed seek in UI player to " + safePositionSeconds + " s");
            } catch (Exception uiSeekError) {
                JWLog.e(TAG, "UI player seek failed: " + uiSeekError.getMessage());
            }
        }

        if (jwPlayer != null) {
            // Keep state (playing vs paused) consistent after seek using the prior state snapshot
            boolean wasPlaying = previousState == PlayerState.PLAYING || previousState == PlayerState.BUFFERING;
            int targetPlaybackState = wasPlaying ? PlaybackStateCompat.STATE_PLAYING : PlaybackStateCompat.STATE_PAUSED;

            shouldRequestFocus = wasPlaying;
            updatePlaybackState(jwPlayer, targetPlaybackState, safePositionMs);

            lastSeekRequestedWhilePaused = !wasPlaying;
        } else {
            lastSeekRequestedWhilePaused = false;
        }

        if (shouldRequestFocus) {
            boolean focusGranted = requestAudioFocusForPlayback();
            if (!focusGranted) {
                JWLog.w(TAG, "Audio focus not granted for seek during playback");
            }
        }

        lastRequestedSeekPositionMs = safePositionMs;
        storeSeekPosition(safePositionMs);
    }

    private void maybeClearResetFlagForSeek(long targetPositionMs) {
        if (!resetToStartAfterSeekCompletion || targetPositionMs <= 0) {
            return;
        }

        long durationMs = getCurrentDurationMs();
        long guardWindow = Math.max(SEEK_END_GUARD_MS, 750L);
        boolean awayFromCompletionZone = durationMs <= 0 || targetPositionMs + guardWindow < durationMs;

        if (awayFromCompletionZone) {
            JWLog.d(TAG, "maybeClearResetFlagForSeek: clearing completion reset flag due to seek to " + targetPositionMs + " ms");
            resetToStartAfterSeekCompletion = false;
        }
    }

    private void resetPlaybackToStartIfNeeded(String caller) {
        if (!resetToStartAfterSeekCompletion) {
            return;
        }

        JWLog.d(TAG, caller + ": rewinding after seek-triggered completion");
        try {
            performSeekTo(0L);
        } catch (Exception resetEx) {
            JWLog.w(TAG, caller + ": rewind failed " + resetEx.getMessage());
        } finally {
            resetToStartAfterSeekCompletion = false;
        }
    }

    private long sanitizeSeekPosition(long positionMs) {
        if (positionMs <= 0) return 0L;

        long durationMs = getCurrentDurationMs();
        if (durationMs > 0 && positionMs >= durationMs) {
            long guardWindow = Math.min(SEEK_END_GUARD_MS, Math.max(durationMs / 20L, 250L));
            long safeUpperBound = durationMs - guardWindow;
            if (safeUpperBound < 0) {
                safeUpperBound = Math.max(durationMs - 50L, 0L);
            }
            long adjusted = Math.min(positionMs, safeUpperBound);
            if (adjusted < 0) {
                adjusted = 0L;
            }
            JWLog.d(TAG, "sanitizeSeekPosition: clamping requestedMs=" + positionMs + " to " + adjusted + " (durationMs=" + durationMs + ")");
            return adjusted;
        }

        return Math.max(positionMs, 0L);
    }

    private void maybeCompleteFromSeek(long positionMs) {
        long durationMs = getCurrentDurationMs();
        if (durationMs <= 0) {
            return;
        }

        PlayerState currentState = null;
        try {
            if (jwPlayer != null) {
                currentState = jwPlayer.getState();
            } else if (serviceMediaApi != null && serviceMediaApi.getPlayer() != null) {
                currentState = serviceMediaApi.getPlayer().getState();
            }
        } catch (Exception stateEx) {
            JWLog.w(TAG, "maybeCompleteFromSeek: state lookup failed " + stateEx.getMessage());
        }

        if (currentState == PlayerState.COMPLETE) {
            JWLog.d(TAG, "maybeCompleteFromSeek: ignoring seek while already complete");
            return;
        }

        long delta = Math.max(durationMs - positionMs, 0L);
        long completionThreshold = Math.max(SEEK_END_GUARD_MS, 750L);

        if (delta <= completionThreshold) {
            if (completionScheduledFromSeek) {
                JWLog.d(TAG, "maybeCompleteFromSeek: completion already scheduled");
                return;
            }

            JWLog.d(TAG, "maybeCompleteFromSeek: treating seek to " + positionMs + " as completion (duration=" + durationMs + ")");
            completionScheduledFromSeek = true;
            try {
                onPlaylistComplete(null);
                JWLog.d(TAG, "maybeCompleteFromSeek: pausing player after forced completion");
                performPause();
                suppressNextOnPlayAfterSeekCompletion = true;
                suppressOnPlayExpiryMs = SystemClock.elapsedRealtime() + AUTO_PLAY_SUPPRESS_WINDOW_MS;
            } catch (Exception completeEx) {
                JWLog.w(TAG, "maybeCompleteFromSeek: completion handling failed " + completeEx.getMessage());
            }
        }
    }

    private long getCurrentDurationMs() {
        double durationSeconds = -1.0;
        try {
            if (jwPlayer != null) {
                durationSeconds = jwPlayer.getDuration();
            } else if (serviceMediaApi != null && serviceMediaApi.getPlayer() != null) {
                durationSeconds = serviceMediaApi.getPlayer().getDuration();
            }
        } catch (Exception durationEx) {
            JWLog.w(TAG, "getCurrentDurationMs: duration lookup failed " + durationEx.getMessage());
        }

        long candidate = -1L;
        if (durationSeconds > 0) {
            candidate = (long) (durationSeconds * 1000L);
        }

        if (candidate > 0) {
            lastKnownDurationMs = candidate;
            return candidate;
        }

        return lastKnownDurationMs;
    }

    /**
     * Handle media item selection from Android Auto
     * This handles both MediaBrowser logic (React Native notification) and JWPlayer logic (actual playback)
     */
    private void performMediaItemSelection(String mediaId, Bundle extras) {
        JWLog.d(TAG, "performMediaItemSelection(mediaId=" + mediaId + ", extras=" + JWLog.bundleInfo(extras) + ")");

        requestAudioFocusForPlayback();

        // Use postDelayed instead of Thread.sleep so the MediaSession callback thread is not blocked.
        // The continuation runs on the main thread after the same 500 ms deferral.
        mainHandler.postDelayed(() -> finishMediaItemSelection(mediaId, extras), 500);
    }

    private void finishMediaItemSelection(String mediaId, Bundle extras) {
        JWLog.d(TAG, "finishMediaItemSelection(mediaId=" + mediaId + ")");

        initServiceMediaApi();

        // Mark that this is from Android Auto and record the time
        isPlayingFromAndroidAuto = true;
        androidAutoHandoffStartTime = System.currentTimeMillis();
        JWLog.d(TAG, "performMediaItemSelection: Set Android Auto handoff flag at " + androidAutoHandoffStartTime);

        try {
            // Determine if UI is currently in Android PiP (Picture-in-Picture) mode
            boolean isPip = false;
            try {
                PlaybackManager pm = PlaybackManager.getInstance();
                JWPlayer uiPlayer = pm.getActivePlayerIfUI();
                if (uiPlayer != null) {
                    try { 
                        isPip = uiPlayer.isInPictureInPictureMode(); 
                    } catch (Exception ignore) { 
                        isPip = false; 
                    }
                }
            } catch (Exception t) {
                JWLog.w(TAG, "AA_SELECT CHECK_PIP failed: " + t.getMessage());
            }

            // Send selection to React Native ONLY when not in PiP
            if (!isPip) {
                try {
                    Class<?> mediaBrowserServiceClass = Class.forName("com.mediabrowser.MediaBrowserService");
                    java.lang.reflect.Method sendToReactMethod = mediaBrowserServiceClass.getMethod("sendMediaItemToReactNative", String.class);
                    sendToReactMethod.invoke(null, mediaId);
                } catch (Exception e) {
                    JWLog.w(TAG, "Could not call MediaBrowserService.sendMediaItemToReactNative: " + e.getMessage());
                }
            } else {
                JWLog.d(TAG, "AA_SELECT PIP_NATIVE_LOAD: skipping RN dispatch; UI reuse path will load item natively");
            }

            // Then, handle JWPlayer logic - start actual playback
            String titleFromExtras = getStringFromExtras(extras, "title");
            String title = titleFromExtras != null ? titleFromExtras : "Unknown Title";

            String subtitleFromExtras = getSubtitleFromExtras(extras);
            String subtitle = subtitleFromExtras != null ? subtitleFromExtras : ""; // TODO: default subtitle?

            String iconFromExtras = getImageFromExtras(extras);
            String icon = iconFromExtras != null ? iconFromExtras : "";

            pendingSeekMs = extractResumePosition(extras);
            pendingSeekApplied = false;

            externalMediaId = mediaId;
            externalSubtitle = subtitle;
            androidAutoSelectedMediaId = mediaId;

            // Create extras map
            java.util.Map<String, Object> extrasMap = new java.util.HashMap<>();
            if (extras != null) {
                for (String key : extras.keySet()) {
                    Object value = extras.get(key);
                    if (value != null) {
                        extrasMap.put(key, value);
                    }
                }
            }

            jwPlayerNativePlaybackHandler.handleHeadlessMediaSelection(mediaId, title, subtitle, icon, extrasMap);
        } catch (Exception e) {
            JWLog.e(TAG, "Error handling media item selection: " + e.getMessage());
        }
    }

    /**
     * Static methods for MediaBrowserService to delegate to active instance
     */
    public static boolean handlePlayFromMediaId(String mediaId, Bundle extras) {
        JWLog.d(TAG, "handlePlayFromMediaId(mediaId=" + mediaId + ", extras=" + JWLog.bundleInfo(extras) + ") activeInstance=" + (activeInstance != null));
        pendingSeekMs = extractResumePosition(extras);
        pendingSeekApplied = false;
        autoHandoffSeekAttempts = 0; // Reset counter for new handoff
        
        externalMediaId = mediaId;
        androidAutoSelectedMediaId = mediaId;

        JWLog.d(TAG, "handlePlayFromMediaId: Static state initialized - externalMediaId=" + externalMediaId + ", pendingSeekMs=" + pendingSeekMs + "ms (extracted from extras), autoHandoffSeekAttempts=0, pendingSeekApplied=false");

        if (activeInstance != null) {
            try {
                activeInstance.performMediaItemSelection(mediaId, extras);
                return true;
            } catch (Exception e) {
                JWLog.e(TAG, "Error in static handlePlayFromMediaId: " + e.getMessage());
                return false;
            }
        }
        return false;
    }
    
    public static boolean handlePlay() {
        JWLog.d(TAG, "handlePlay() activeInstance=" + (activeInstance != null));
        if (activeInstance != null) {
            try {
                activeInstance.performPlay();
                return true;
            } catch (Exception e) {
                JWLog.e(TAG, "Error in static handlePlay: " + e.getMessage());
                return false;
            }
        }
        return false;
    }
    
    public static boolean handlePause() {
        JWLog.d(TAG, "handlePause() activeInstance=" + (activeInstance != null));
        if (activeInstance != null) {
            try {
                activeInstance.performPause();
                return true;
            } catch (Exception e) {
                JWLog.e(TAG, "Error in static handlePause", e);
                return false;
            }
        }
        return false;
    }
    
    public static boolean handleStop() {
        JWLog.d(TAG, "handleStop() activeInstance=" + (activeInstance != null));
        if (activeInstance != null) {
            try {
                activeInstance.performStop();
                return true;
            } catch (Exception e) {
                JWLog.e(TAG, "Error in static handleStop", e);
                return false;
            }
        }
        return false;
    }

    public static boolean handleDestroy() {
        JWLog.d(TAG, "handleDestroy() activeInstance=" + (activeInstance != null));
        if (activeInstance != null) {
            try {
                activeInstance.softCleanup();
                return true;
            } catch (Exception e) {
                JWLog.e(TAG, "Error in static handleDestroy", e);
                return false;
            }
        }
        return false;
    }
    
    public static boolean handleSkipToNext() {
        JWLog.d(TAG, "handleSkipToNext() activeInstance=" + (activeInstance != null));
        if (activeInstance != null) {
            try {
                activeInstance.performSkipToNext();
                return true;
            } catch (Exception e) {
                JWLog.e(TAG, "Error in static handleSkipToNext", e);
                return false;
            }
        }
        return false;
    }
    
    public static boolean handleSkipToPrevious() {
        JWLog.d(TAG, "handleSkipToPrevious() activeInstance=" + (activeInstance != null));
        if (activeInstance != null) {
            try {
                activeInstance.performSkipToPrevious();
                return true;
            } catch (Exception e) {
                JWLog.e(TAG, "Error in static handleSkipToPrevious", e);
                return false;
            }
        }
        return false;
    }
    
    public static boolean handleSeekTo(long position) {
        JWLog.d(TAG, "handleSeekTo(positionMs=" + position + ") activeInstance=" + (activeInstance != null));
        if (activeInstance != null) {
            try {
                activeInstance.performSeekTo(position);
                return true;
            } catch (Exception e) {
                JWLog.e(TAG, "Error in static handleSeekTo", e);
                return false;
            }
        }
        return false;
    }

    public static boolean handleSetSpeed(float speed) {
        JWLog.d(TAG, "handleSetSpeed(speed=" + speed + ") activeInstance=" + (activeInstance != null));
        currentSpeed = speed;
        if (activeInstance != null) {
            try {
                JWPlayer player = activeInstance.jwPlayer;
                if (player == null && activeInstance.serviceMediaApi != null) {
                    player = activeInstance.serviceMediaApi.getPlayer();
                }
                if (player != null) {
                    player.setPlaybackRate(speed);
                    return true;
                }
            } catch (Exception e) {
                JWLog.e(TAG, "Error in static handleSetSpeed", e);
            }
        }
        return false;
    }

    /**
     * Returns the actual playback rate from the active JWPlayer instance.
     * Falls back to the stored {@link #currentSpeed} if no player is available.
     * Called by MediaBrowserService.getPlaybackSpeed() so that JS always receives
     * the real rate — even if it was changed from the in-app UI without going
     * through the vehicle-aware wrapper.
     */
    public static float getActualPlaybackRate() {
        if (activeInstance != null) {
            try {
                JWPlayer player = activeInstance.jwPlayer;
                if (player == null && activeInstance.serviceMediaApi != null) {
                    player = activeInstance.serviceMediaApi.getPlayer();
                }
                if (player != null) {
                    float rate = (float) player.getPlaybackRate();
                    if (rate > 0) {
                        currentSpeed = rate; // keep stored value in sync
                        return rate;
                    }
                }
            } catch (Exception e) {
                JWLog.w(TAG, "getActualPlaybackRate: failed " + e.getMessage());
            }
        }
        return currentSpeed;
    }
}
