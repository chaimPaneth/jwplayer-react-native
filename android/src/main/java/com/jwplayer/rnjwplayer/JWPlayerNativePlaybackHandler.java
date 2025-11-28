package com.jwplayer.rnjwplayer;

import android.content.Context;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.bridge.WritableArray;
import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.ReadableArray;
import com.facebook.react.modules.core.DeviceEventManagerModule;
import com.facebook.react.ReactApplication;
import com.facebook.react.ReactNativeHost;
import com.facebook.react.ReactInstanceManager;
import com.facebook.react.bridge.ReactContext;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;
import android.os.Handler;
import android.os.Looper;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import java.net.URL;
import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

// JWPlayer imports for headless playback
import com.jwplayer.pub.api.JWPlayer;
import com.jwplayer.pub.api.PlayerState;
import com.jwplayer.pub.api.configuration.PlayerConfig;
import com.jwplayer.pub.api.media.playlists.PlaylistItem;
import com.jwplayer.pub.api.events.ReadyEvent;
import com.jwplayer.pub.api.events.PlayEvent;
import com.jwplayer.pub.api.events.PauseEvent;
import com.jwplayer.pub.api.events.ErrorEvent;
import com.jwplayer.pub.api.events.CompleteEvent;
import com.jwplayer.pub.api.events.listeners.VideoPlayerEvents;
import com.jwplayer.pub.api.license.LicenseUtil;
import com.jwplayer.pub.api.background.ServiceMediaApi;
import com.jwplayer.rnjwplayer.session.RNJWMediaSessionHelper;
import com.jwplayer.rnjwplayer.session.RNJWNotificationHelper;
import com.jwplayer.rnjwplayer.utils.JWLog;

// For creating minimal player view
import android.view.ViewGroup;
import android.widget.FrameLayout;

// Audio and MediaSession imports
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.app.NotificationManager;
import android.os.Build;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.support.v4.media.MediaMetadataCompat;
import com.mediabrowser.MediaSessionSingleton;

// Lifecycle imports for JWPlayer
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.LifecycleRegistry;

/**
 * Native playback handler for JWPlayer to handle headless mode media playback
 * 
 * Note: Due to JWPlayer's architecture, we cannot directly create headless players.
 * Instead, this handler stores the media info for later app restoration and sends
 * events to React Native to handle the actual playback through existing UI components.
 */
public class JWPlayerNativePlaybackHandler implements VideoPlayerEvents.OnReadyListener, 
        VideoPlayerEvents.OnPlayListener, VideoPlayerEvents.OnPauseListener, 
        VideoPlayerEvents.OnErrorListener, VideoPlayerEvents.OnCompleteListener,
        AudioManager.OnAudioFocusChangeListener, LifecycleOwner {
    private static final String TAG = "JWPlayerNativePlaybackHandler";
    private static final String CLOUDINARY_BASE = "https://res.cloudinary.com/ouinternal/image/upload/c_scale,f_auto,q_auto,w_275/";
    private static final String JW_MANIFEST_BASE = "https://cdn.jwplayer.com/manifests/";
    private static JWPlayerNativePlaybackHandler instance;
    private final Context context;
    private final GlobalPlayingInfoManager playingInfoManager;
    private final Gson gson;
    
    // Background playback components
    private RNJWPlayer backgroundPlayerView;
    private JWPlayer backgroundPlayer;
    private ServiceMediaApi serviceMediaApi;
    private RNJWMediaSessionHelper mediaSessionHelper;
    private RNJWNotificationHelper notificationHelper;
    private MediaSessionCompat sharedMediaSession;
    
    // Audio focus management
    private AudioManager audioManager;
    private AudioFocusRequest focusRequest;
    private boolean hasAudioFocus = false;
    private boolean playbackNowAuthorized = false;
    private boolean playbackDelayed = false;
    private final Object focusLock = new Object();
    
    // Lifecycle management for JWPlayer
    private final LifecycleRegistry lifecycleRegistry;
    private final Handler mainHandler;
    
    // Position update timer
    private Timer positionUpdateTimer;
    private boolean isPlaying = false;
    
    // Autostart and resume flags
    private int autostartAttempts = 0;
    private boolean hasStartedPlayback = false; // first successful play marker
    private boolean wasPlayingBeforeSeek = false; // used to resume after seek
    private boolean autoStartEnabled = true; // can be toggled if needed
    private boolean isPlayerReady = false; // becomes true in onReady
    private final ExecutorService artworkExecutor = Executors.newSingleThreadExecutor();
    
    private JWPlayerNativePlaybackHandler(Context context) {
        JWLog.d(TAG, "JWPlayerNativePlaybackHandler::<init>(context=" + context + ")");
        this.context = context;
        this.playingInfoManager = GlobalPlayingInfoManager.getInstance(context);
        this.gson = new Gson();
        
        // Initialize main thread handler
        this.mainHandler = new Handler(Looper.getMainLooper());
        
        // Initialize lifecycle registry for JWPlayer
        this.lifecycleRegistry = new LifecycleRegistry(this);
        
        // Initialize lifecycle on main thread
        mainHandler.post(() -> {
            lifecycleRegistry.markState(Lifecycle.State.CREATED);
            lifecycleRegistry.markState(Lifecycle.State.STARTED);
        });
        
        // Initialize audio manager for background audio
        this.audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        
        // Get shared media session from MediaBrowser
        this.sharedMediaSession = MediaSessionSingleton.getInstance(context);
    }
    
    private String stringOrNull(Object value) {
        if (value instanceof String) {
            String result = ((String) value).trim();
            return result.isEmpty() || "null".equalsIgnoreCase(result) ? null : result;
        }
        return null;
    }

    private boolean isJWManifestIdCandidate(String candidate) {
        if (candidate == null) {
            return false;
        }
        String value = candidate.trim();
        if (value.isEmpty()) {
            return false;
        }
        int length = value.length();
        if (length < 6 || length > 20) {
            return false;
        }
        boolean hasLetter = false;
        for (int i = 0; i < length; i++) {
            char ch = value.charAt(i);
            if (Character.isLetter(ch)) {
                hasLetter = true;
            }
            if (!Character.isLetterOrDigit(ch)) {
                return false;
            }
        }
        return hasLetter;
    }

    private String normalizeArtworkValue(Object candidate) {
        String raw = stringOrNull(candidate);
        if (raw == null) {
            return null;
        }
        if (raw.startsWith("http://") || raw.startsWith("https://")) {
            return raw;
        }
        if (raw.startsWith("//")) {
            return "https:" + raw;
        }
        String normalized = raw.startsWith("/") ? raw.substring(1) : raw;
        String lower = normalized.toLowerCase(Locale.US);
        boolean hasExtension = lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png") || lower.endsWith(".webp");
        if (!hasExtension) {
            normalized = normalized + ".jpeg";
        }
        return CLOUDINARY_BASE + normalized;
    }

    @SuppressWarnings("unchecked")
    private String resolveImageUrl(Map<String, Object> postData) {
        if (postData == null) {
            return null;
        }

        String[] primaryKeys = new String[]{"seriesImage", "image", "featuredImage", "icon", "artwork", "artworkUrl"};
        for (String key : primaryKeys) {
            String candidate = normalizeArtworkValue(postData.get(key));
            if (candidate != null) {
                return candidate;
            }
        }

        Object seriesObj = postData.get("series");
        if (seriesObj instanceof Map) {
            Map<String, Object> seriesMap = (Map<String, Object>) seriesObj;
            String[] seriesKeys = new String[]{"image", "featuredImage", "seriesImage"};
            for (String key : seriesKeys) {
                String candidate = normalizeArtworkValue(seriesMap.get(key));
                if (candidate != null) {
                    return candidate;
                }
            }
        }

        Object seoObj = postData.get("seo");
        if (seoObj instanceof Map) {
            Map<String, Object> seoMap = (Map<String, Object>) seoObj;
            String candidate = normalizeArtworkValue(seoMap.get("image"));
            if (candidate != null) {
                return candidate;
            }
        }

        Object authorsObj = postData.get("authors");
        if (authorsObj instanceof List) {
            List<?> authors = (List<?>) authorsObj;
            for (Object authorObj : authors) {
                if (authorObj instanceof Map) {
                    Map<String, Object> authorMap = (Map<String, Object>) authorObj;
                    String candidate = normalizeArtworkValue(authorMap.get("image"));
                    if (candidate != null) {
                        return candidate;
                    }
                }
            }
        }

        JWLog.d(TAG, "resolveImageUrl: no artwork found in postData");
        return null;
    }

    @SuppressWarnings("unchecked")
    private String resolvePlaybackUrl(Map<String, Object> postData) {
        if (postData == null) {
            return null;
        }

        String hlsUrl = stringOrNull(postData.get("hls_url"));
        if (hlsUrl != null) {
            return hlsUrl;
        }

        String s3Url = stringOrNull(postData.get("s3Url"));
        if (s3Url != null) {
            return s3Url;
        }

        Object playerSources = postData.get("playerSources");
        if (playerSources instanceof List) {
            List<?> sources = (List<?>) playerSources;
            for (Object sourceObj : sources) {
                if (sourceObj instanceof Map) {
                    Map<String, Object> sourceMap = (Map<String, Object>) sourceObj;
                    String file = stringOrNull(sourceMap.get("file"));
                    if (file != null) {
                        return file;
                    }
                }
            }
        }

        String mediaId = stringOrNull(postData.get("mediaId"));
        if (isJWManifestIdCandidate(mediaId)) {
            String synthesizedUrl = JW_MANIFEST_BASE + mediaId + ".m3u8";
            JWLog.d(TAG, "resolvePlaybackUrl: synthesized JW manifest URL=" + JWLog.safe(synthesizedUrl));
            return synthesizedUrl;
        }

        JWLog.w(TAG, "resolvePlaybackUrl: no playback URL found in postData");
        return null;
    }

    /**
     * Create and configure background player for media playback
     * Following the same pattern as RNJWPlayerView.setConfig()
     */
    private void createBackgroundPlayer(Map<String, Object> postData) {
        JWLog.d(TAG, "createBackgroundPlayer(postDataKeys=" + (postData != null ? postData.keySet() : null) + ")");
        try {
            // If a UI player is currently active, do not create a headless/background player
            if (PlaybackManager.getInstance().isUIActive()) {
                return;
            }
            // Ensure no other player is active and wait for cleanup to finish
            PlaybackManager.getInstance().stopAndCleanupCurrentPlayer();

            if (backgroundPlayer != null) {
                cleanupBackgroundPlayer();
            }
            
            String playbackUrl = resolvePlaybackUrl(postData);
            if (playbackUrl == null) {
                JWLog.e(TAG, "No playback URL available");
                return;
            }

            String title = stringOrNull(postData.get("title"));
            String mediaId = stringOrNull(postData.get("mediaId"));

            int duration = 0;
            Object durationObj = postData.get("duration");
            if (durationObj instanceof Number) {
                duration = ((Number) durationObj).intValue();
            }

            int startTime = 0;
            Object startTimeObj = postData.get("startTime");
            if (startTimeObj instanceof Number) {
                startTime = ((Number) startTimeObj).intValue();
            } else {
                Object timepointObj = postData.get("timepoint");
                if (timepointObj instanceof Number) {
                    startTime = ((Number) timepointObj).intValue();
                }
            }

            String imageUrl = resolveImageUrl(postData);
            JWLog.d(TAG, "Resolved background playback URL=" + JWLog.safe(playbackUrl) + ", image=" + JWLog.safe(imageUrl));
            
            // Create playlist item like RNJWPlayerView does with image
            PlaylistItem.Builder playlistBuilder = new PlaylistItem.Builder()
                    .file(playbackUrl)
                    .title(title != null ? title : "Unknown Title")
                    .mediaId(mediaId != null ? mediaId : "unknown");

            // Add duration if available
            if (duration > 0) {
                playlistBuilder.duration(duration);
            }

            // Add start time if available
            if (startTime > 0) {
                playlistBuilder.startTime(startTime);
            }
            
            // Add image if available
            if (imageUrl != null) {
                playlistBuilder.image(imageUrl);
                JWLog.d(TAG, "📱 JAVA: Added image to PlaylistItem: " + imageUrl);
            }
            
            PlaylistItem playlistItem = playlistBuilder.build();
            
            JWLog.d(TAG, "📱 JAVA: Created PlaylistItem");
            
            // Create player config for background audio
            PlayerConfig playerConfig = new PlayerConfig.Builder()
                    .playlist(java.util.Arrays.asList(playlistItem))
                    .autostart(true) // Auto-start for headless mode
                    .build();
            
            // Set JWPlayer license (required before creating player)
            try {
                // Use the same Android license as the main app
                String jwLicense = "mPb2iD4lDPvP42HIrush+pnBtg/q+9nUUCZfVw==";
                new LicenseUtil().setLicenseKey(context, jwLicense);
            } catch (Exception e) {
                JWLog.e(TAG, "Error setting JWPlayer license", e);
                // Continue anyway, license might already be set by main app
            }
            
            // Create RNJWPlayer view first (required pattern)
            backgroundPlayerView = new RNJWPlayer(context);
            
            // Get the actual JWPlayer from the view (pass this as LifecycleOwner)
            backgroundPlayer = backgroundPlayerView.getPlayer(this);

            // Reset readiness flags for fresh player
            isPlayerReady = false;
            hasStartedPlayback = false;
            
            // Register this new player as the active one
            PlaybackManager.getInstance().setActivePlayer(backgroundPlayer, this);

            // Add event listeners before setup
            backgroundPlayer.addListeners(this);
            
            // Setup the player with the config FIRST
            backgroundPlayer.setup(playerConfig);
            
            // Check if playlist was loaded
            if (backgroundPlayer.getPlaylist() != null && !backgroundPlayer.getPlaylist().isEmpty()) {
                PlaylistItem currentItem = backgroundPlayer.getPlaylist().get(0);
            } else {
                JWLog.w(TAG, "WARNING: Playlist is null or empty after setup!");
            }
            
            // Set up background audio service components AFTER player is configured
            serviceMediaApi = new ServiceMediaApi(backgroundPlayer);
            
            // Create notification helper (uses default ID 2005)
            NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            notificationHelper = new RNJWNotificationHelper.Builder(context, notificationManager).build();
            
            // Create media session helper for proper notification controls
            // The HybridMediaSessionCallback in MediaBrowserService protects onPlayFromMediaId
            // while still allowing RNJWMediaSessionHelper to handle playback controls
            mediaSessionHelper = new RNJWMediaSessionHelper(context, notificationHelper, serviceMediaApi);
            
            JWLog.d(TAG, "📱 JAVA: Background player creation completed successfully");
            // Artwork fetch gating (avoid duplicate network if metadata already has bitmap)
            try {
                // imageUrl already declared earlier in createBackgroundPlayer; reuse it here
                if (imageUrl != null) {
                    boolean needFetch = true;
                    if (sharedMediaSession != null) {
                        try {
                            MediaMetadataCompat md = sharedMediaSession.getController().getMetadata();
                            if (md != null && md.getBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART) != null) {
                                needFetch = false;
                                JWLog.d(TAG, "ARTWORK_DEBUG: existing bitmap present; skip fetch");
                            }
                        } catch (Exception metaEx) {
                            JWLog.w(TAG, "ARTWORK_DEBUG: metadata inspection failed: " + metaEx.getMessage());
                        }
                    }
                    if (needFetch) {
                        scheduleAlbumArtFetch(imageUrl);
                    } else {
                        JWLog.d(TAG, "ARTWORK_DEBUG: Skipping artwork fetch (bitmap already present)");
                    }
                }
            } catch (Exception artEx) {
                JWLog.w(TAG, "ARTWORK_DEBUG: gating error: " + artEx.getMessage());
            }
        } catch (Exception e) {
            JWLog.e(TAG, "Error creating background player", e);
            cleanupBackgroundPlayer();
        }
    }
    
    public static synchronized JWPlayerNativePlaybackHandler getInstance(Context context) {
        // JWLog.d(TAG, "getInstance(context=" + context + ")");
        if (instance == null) {
            instance = new JWPlayerNativePlaybackHandler(context);
        }
        return instance;
    }
    
    /**
     * Public method to stop and cleanup all background playback
     * Called when notification is dismissed or app needs to cleanup
     */
    public void stopAndCleanup() {
        JWLog.d(TAG, "📱 JAVA: stopAndCleanup called - stopping all background playback");
        
        saveCurrentSeekPosition();

        // Let the playback manager know this player is being destroyed.
        PlaybackManager.getInstance().clearPlayer(this);

        try {
            // Stop position updates
            stopPositionUpdates();
            
            // Clear playing info
            if (playingInfoManager != null) {
                playingInfoManager.clearPlayingInfo();
            }
            
            // Cleanup background player
            if (backgroundPlayer != null) {
                cleanupBackgroundPlayer();
            }
            
            // Update MediaSession to stopped state
            if (sharedMediaSession != null && sharedMediaSession.isActive()) {
                sharedMediaSession.setPlaybackState(new PlaybackStateCompat.Builder()
                    .setState(PlaybackStateCompat.STATE_STOPPED, 0, 1.0f)
                    .setActions(PlaybackStateCompat.ACTION_PLAY | 
                               PlaybackStateCompat.ACTION_PAUSE | 
                               PlaybackStateCompat.ACTION_STOP |
                               PlaybackStateCompat.ACTION_SKIP_TO_NEXT |
                               PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS |
                               PlaybackStateCompat.ACTION_SEEK_TO)
                    .build());
            }
            
            JWLog.d(TAG, "📱 JAVA: stopAndCleanup completed successfully");
        } catch (Exception e) {
            JWLog.e(TAG, "📱 JAVA: Error during stopAndCleanup", e);
        }
    }
    
    private void saveCurrentSeekPosition() {
        JWLog.d(TAG, "saveCurrentSeekPosition()");

        // Persist last known position before stopping to enable accurate UI resume
        try {
            long lastMs = 0L;
            try { lastMs = (long) (backgroundPlayer != null ? backgroundPlayer.getPosition() * 1000 : 0); } catch (Exception ignored) {}
            if (lastMs > 0) {
                Map<String, Object> info = playingInfoManager != null ? playingInfoManager.getCurrentPlayingInfo() : null;
                if (info != null) {
                    Object extIdObj = info.get("mediaId");
                    if (extIdObj instanceof String) {
                        String externalMediaId = (String) extIdObj;
                        if (externalMediaId != null && !externalMediaId.isEmpty()) {
                            // CRITICAL: Also update static cache in RNJWMediaSessionHelper
                            // This ensures the UI player gets the correct position on handoff
                            try {
                                RNJWMediaSessionHelper.cachePositionForHandoff(externalMediaId, lastMs);
                                JWLog.d(TAG, "Cached position in RNJWMediaSessionHelper: " + lastMs + "ms for mediaId=" + externalMediaId);
                            } catch (Exception cacheEx) {
                                JWLog.w(TAG, "Failed to cache position in RNJWMediaSessionHelper: " + cacheEx.getMessage());
                            }
                            
                            Class<?> mediaBrowserServiceClass = Class.forName("com.mediabrowser.MediaBrowserService");
                            java.lang.reflect.Method reportSeek = mediaBrowserServiceClass.getMethod("updateSeekPosition", String.class, long.class);
                            reportSeek.invoke(null, externalMediaId, lastMs);
                            
                            JWLog.d(TAG, "Persisted last headless position: " + lastMs + "ms for mediaId=" + externalMediaId);
                        }
                    } else {
                        JWLog.w(TAG, "Invalid mediaId type when persisting last headless position");
                    }
                } else {
                    JWLog.w(TAG, "No playing info available to persist last headless position");
                }
            }
        } catch (Exception persistLastEx) {
            JWLog.w(TAG, "Persist last headless position failed: " + persistLastEx.getMessage());
        }
    }

    /**
     * Public method to stop background playback (called from UI components)
     */
    public void stopBackgroundPlayback() {
        JWLog.d(TAG, "📱 JAVA: stopBackgroundPlayback called from UI");
        if (backgroundPlayer != null) {
            cleanupBackgroundPlayer();
        }
    }
    
    /**
     * Seek to a specific position in the background player
     */
    public void seekToPosition(long positionMs) {
        JWLog.d(TAG, "seekToPosition(positionMs=" + positionMs + ")");
        if (backgroundPlayer != null) {
            try {
                boolean shouldResume = isPlaying || (autoStartEnabled && !hasStartedPlayback);
                wasPlayingBeforeSeek = isPlaying;
                double positionSeconds = positionMs / 1000.0;
                backgroundPlayer.seek(positionSeconds);
                JWLog.d(TAG, "Background player seeked to: " + positionSeconds + "s (wasPlaying=" + wasPlayingBeforeSeek + " shouldResume=" + shouldResume + ")");
                if (shouldResume) {
                    mainHandler.postDelayed(() -> {
                        if (backgroundPlayer != null && (wasPlayingBeforeSeek || (autoStartEnabled && !hasStartedPlayback))) {
                            try {
                                JWLog.d(TAG, "Attempting autoplay resume after seek");
                                backgroundPlayer.play();
                            } catch (Exception e) {
                                JWLog.w(TAG, "Autoplay resume after seek failed: " + e.getMessage());
                            }
                        }
                    }, 150);
                }
            } catch (Exception e) {
                JWLog.e(TAG, "Error seeking background player", e);
            }
        } else {
            JWLog.w(TAG, "Cannot seek: background player is null");
        }
    }
    
    /**
     * Check if background player is active and playing
     */
    public boolean isBackgroundPlayerActive() {
        JWLog.d(TAG, "isBackgroundPlayerActive() -> " + (backgroundPlayer != null && isPlaying));
        return backgroundPlayer != null && isPlaying;
    }
    
    /**
     * Get current background player info for session coordination
     */
    public Map<String, Object> getCurrentBackgroundPlayerInfo() {
        // JWLog.d(TAG, "getCurrentBackgroundPlayerInfo()");
        if (backgroundPlayer != null && playingInfoManager.hasPendingMedia()) {
            Map<String, Object> currentInfo = playingInfoManager.getCurrentPlayingInfo();
            if (currentInfo != null) {
                Map<String, Object> playerInfo = new HashMap<>();
                playerInfo.put("mediaId", currentInfo.get("mediaId"));
                playerInfo.put("title", currentInfo.get("title"));
                playerInfo.put("isPlaying", isPlaying);
                try {
                    playerInfo.put("position", backgroundPlayer.getPosition());
                } catch (Exception e) {
                    playerInfo.put("position", 0.0);
                }
                return playerInfo;
            }
        }
        return null;
    }
    
    /**
     * Transfer playback to UI player (stop background, return current state)
     */
    public Map<String, Object> transferToUIPlayer() {
        JWLog.d(TAG, "transferToUIPlayer()");
        Map<String, Object> currentState = getCurrentBackgroundPlayerInfo();
        if (backgroundPlayer != null) {
            JWLog.d(TAG, "Transferring background player to UI player");
            // Stop background player but don't clean up session
            try {
                // Get current position before stopping
                long currentPositionMs = 0;
                try {
                    currentPositionMs = (long)(backgroundPlayer.getPosition() * 1000); // Convert to milliseconds
                } catch (Exception e) {
                    currentPositionMs = 0;
                }

                saveCurrentSeekPosition();
                
                backgroundPlayer.pause();
                backgroundPlayer.stop();
                backgroundPlayer = null;
                isPlaying = false;
                
                // Update session state to indicate transfer with current position
                if (sharedMediaSession != null) {
                    sharedMediaSession.setPlaybackState(new PlaybackStateCompat.Builder()
                        .setState(PlaybackStateCompat.STATE_PAUSED, currentPositionMs, 1.0f)
                        .setActions(PlaybackStateCompat.ACTION_PLAY | 
                                   PlaybackStateCompat.ACTION_PAUSE | 
                                   PlaybackStateCompat.ACTION_STOP |
                                   PlaybackStateCompat.ACTION_SEEK_TO)
                        .build());
                }
            } catch (Exception e) {
                JWLog.e(TAG, "Error during background player transfer", e);
            }
        }
        return currentState;
    }
    
    /**
     * Handle media item selection triggered from Android Auto or MediaBrowser
     * Now attempts to create actual background playback using JWPlayer ServiceMediaApi
     */
    public void handleHeadlessMediaSelection(String mediaId, String title, String subtitle, 
                                           String icon, Map<String, Object> extras) {
        JWLog.d(TAG, "handleHeadlessMediaSelection(mediaId=" + JWLog.safe(mediaId) + ", title=" + JWLog.safe(title) + ")");
        // Log current state
        if (playingInfoManager.hasPendingMedia()) {
            Map<String, Object> currentInfo = playingInfoManager.getCurrentPlayingInfo();
            String currentMediaId = currentInfo != null ? (String) currentInfo.get("mediaId") : "null";
        }
        
        try {
            // If UI is already active, command the UI player to load this media instead of creating headless
            if (PlaybackManager.getInstance().isUIActive()) {
                // Note: MediaBrowserService already emits onMediaItemSelected, so we don't need to emit it again here

                // If we have the post payload, also command the current UI JWPlayer directly for immediate response
                final String postJsonForUi = extras != null ? (String) extras.get("info") : null;
                try {
                    final JWPlayer uiPlayer = PlaybackManager.getInstance().getActivePlayerIfUI();
                    if (uiPlayer != null && postJsonForUi != null) {
                        Type t = new TypeToken<Map<String, Object>>(){}.getType();
                        final Map<String, Object> post = gson.fromJson(postJsonForUi, t);
                        final String playbackUrl = resolvePlaybackUrl(post);
                        final String artworkUrl = resolveImageUrl(post);
                        final int duration = post != null && post.get("duration") instanceof Number
                                ? ((Number) post.get("duration")).intValue()
                                : 0;

                        final String metadataArtwork = artworkUrl != null ? artworkUrl : icon;
                        updateMediaSessionMetadata(title, subtitle, metadataArtwork, post);

                        if (playbackUrl != null) {
                            final String finalArtworkUrl = artworkUrl;
                            final int finalDuration = duration;
                            final String finalPlaybackUrl = playbackUrl;
                            // Ensure player calls are made on the main thread
                            mainHandler.post(() -> {
                                try {
                                    PlaylistItem.Builder builder = new PlaylistItem.Builder()
                                            .file(finalPlaybackUrl)
                                            .title(title != null ? title : "Unknown Title 1")
                                            .mediaId(mediaId != null ? mediaId : "unknown");
                                    if (finalArtworkUrl != null) {
                                        builder.image(finalArtworkUrl);
                                    }
                                    if (finalDuration > 0) {
                                        builder.duration(finalDuration);
                                    }

                                    PlaylistItem item = builder.build();

                                    PlayerConfig cfg = new PlayerConfig.Builder()
                                            .playlist(java.util.Collections.singletonList(item))
                                            .autostart(true)
                                            .build();
                                    uiPlayer.setup(cfg);
                                    // Explicitly start playback for immediate response from AA (in case autostart is gated)
                                    try { uiPlayer.play(); } catch (Exception ignored) {}
                                } catch (Exception e) {
                                    JWLog.w(TAG, "UI player load failed: " + e.getMessage());
                                    fallbackToReactNative(mediaId, uiPlayer);
                                }
                            });
                        } else {
                            JWLog.w(TAG, "UI player load skipped: no playback URL in post data; falling back to React dispatch");
                            fallbackToReactNative(mediaId, uiPlayer);
                        }
                    } else {
                        if (uiPlayer == null) {
                            JWLog.w(TAG, "UI player load skipped: active UI player not available; falling back to React dispatch");
                        } else {
                            JWLog.w(TAG, "UI player load skipped: no post payload supplied; falling back to React dispatch");
                        }
                        fallbackToReactNative(mediaId, uiPlayer);
                    }
                } catch (Exception ex) {
                    JWLog.w(TAG, "Failed to instruct UI player to load selection: " + ex.getMessage());
                    fallbackToReactNative(mediaId, null);
                }
                return;
            }
            
            // Use PlaybackManager to stop any active player (UI or headless) and wait for idle
            PlaybackManager.getInstance().stopAndCleanupCurrentPlayer();
            
            // Emit stop event to React Native to stop any UI players
            emitStopEventToReactNative();
            
            // Extract post data from extras (if available)
            Map<String, Object> post = null;
            String postJson = extras != null ? (String) extras.get("info") : null;
            if (postJson != null) {
                JWLog.d(TAG, "📱 JAVA: Found post JSON in extras, parsing...");
                Type type = new TypeToken<Map<String, Object>>(){}.getType();
                post = gson.fromJson(postJson, type);
            } else {
                JWLog.w(TAG, "📱 JAVA: No post JSON found in extras");
            }

            String artworkUrl = stringOrNull(icon);
            if ((artworkUrl == null || artworkUrl.isEmpty()) && post != null) {
                artworkUrl = resolveImageUrl(post);
            }

            // Store the playing info for app restoration
            playingInfoManager.setCurrentPlayingInfo(mediaId, title, subtitle, artworkUrl, extras);
            JWLog.d(TAG, "📱 JAVA: Stored playing info for mediaId: " + mediaId);

            if (post != null) {
                String playbackUrl = resolvePlaybackUrl(post);
                JWLog.d(TAG, "📱 JAVA: Resolved playback URL=" + JWLog.safe(playbackUrl));

                updateMediaSessionMetadata(title, subtitle, artworkUrl, post);

                if (playbackUrl != null) {
                    JWLog.d(TAG, "📱 JAVA: Starting background playback for new media");
                    startBackgroundPlayback(post, title, subtitle);
                } else {
                    JWLog.w(TAG, "📱 JAVA: No valid URLs found for playback");
                }
            } else {
                updateMediaSessionMetadata(title, subtitle, artworkUrl, null);
            }
            
        } catch (Exception e) {
            JWLog.e(TAG, "📱 JAVA: Error handling headless media selection", e);
        }
    }
    
    /**
     * Start background playback using JWPlayer ServiceMediaApi
     * Following the same pattern as RNJWPlayerView with background audio
     */
    private void startBackgroundPlayback(Map<String, Object> postData, String title, String subtitle) {
        JWLog.d(TAG, "📱 JAVA: startBackgroundPlayback called for: " + JWLog.safe(title) + " / " + JWLog.safe(subtitle));
        
        try {
            // Request audio focus before starting playback
            if (!requestAudioFocus()) {
                JWLog.w(TAG, "📱 JAVA: Failed to get audio focus, delaying playback");
                return;
            }
            
            JWLog.d(TAG, "📱 JAVA: Audio focus granted, creating new background player");
            
            // Always create a new background player for the new media
            createBackgroundPlayer(postData);
            
            if (backgroundPlayer != null) {
                JWLog.d(TAG, "📱 JAVA: Background player created successfully");
                if (autoStartEnabled) {
                    startAutostartChain();
                }
            } else {
                JWLog.e(TAG, "📱 JAVA: Failed to create background player");
            }
            
        } catch (Exception e) {
            JWLog.e(TAG, "📱 JAVA: Error starting background playback", e);
        }
    }
    
    /**
     * Get comprehensive playback state for app handoff (includes position and post data)
     * This method is designed for React Native app wake-up scenarios
     */
    public WritableMap getComprehensivePlaybackState() {
        JWLog.d(TAG, "getComprehensivePlaybackState()");
        if (backgroundPlayer != null && playingInfoManager.hasPendingMedia()) {
            Map<String, Object> playingInfo = playingInfoManager.getCurrentPlayingInfo();
            if (playingInfo != null) {
                WritableMap result = Arguments.createMap();
                result.putString("mediaId", (String) playingInfo.get("mediaId"));
                result.putString("title", (String) playingInfo.get("title"));
                result.putString("subtitle", (String) playingInfo.get("subtitle"));
                result.putString("icon", (String) playingInfo.get("icon"));
                
                // Add current player state
                result.putBoolean("isPlaying", isPlaying);
                result.putBoolean("hasActivePlayer", true);
                
                // Add current position (critical for seamless handoff)
                try {
                    double currentPosition = backgroundPlayer.getPosition();
                    result.putDouble("currentPosition", currentPosition);
                    JWLog.d(TAG, "📱 JAVA: Current headless position: " + currentPosition + "s");
                } catch (Exception e) {
                    result.putDouble("currentPosition", 0.0);
                    JWLog.w(TAG, "📱 JAVA: Could not get current position: " + e.getMessage());
                }
                
                // Convert extras back to WritableMap (includes full post object)
                @SuppressWarnings("unchecked")
                Map<String, Object> extras = (Map<String, Object>) playingInfo.get("extras");
                if (extras != null) {
                    WritableMap extrasMap = Arguments.createMap();
                    for (Map.Entry<String, Object> entry : extras.entrySet()) {
                        Object value = entry.getValue();
                        if (value instanceof String) {
                            extrasMap.putString(entry.getKey(), (String) value);
                        } else if (value instanceof Integer) {
                            extrasMap.putInt(entry.getKey(), (Integer) value);
                        } else if (value instanceof Boolean) {
                            extrasMap.putBoolean(entry.getKey(), (Boolean) value);
                        } else if (value instanceof Double) {
                            extrasMap.putDouble(entry.getKey(), (Double) value);
                        }
                    }
                    result.putMap("extras", extrasMap);
                    
                    // Extract and add the post object for React Native openPlayer method
                    String postJson = (String) extras.get("info");
                    if (postJson != null) {
                        result.putString("postJson", postJson);
                        JWLog.d(TAG, "📱 JAVA: Added post JSON for handoff");
                    }
                }
                
                JWLog.d(TAG, "📱 JAVA: Comprehensive playback state prepared for handoff");
                return result;
            }
        }
        
        // Return null if no active headless playback
        JWLog.d(TAG, "📱 JAVA: No active headless playback found");
        return null;
    }

    /**
     * Get pending media info for app restoration (legacy method)
     */
    public WritableMap getPendingMediaInfo() {
        JWLog.d(TAG, "getPendingMediaInfo()");
        if (playingInfoManager.hasPendingMedia()) {
            Map<String, Object> playingInfo = playingInfoManager.getCurrentPlayingInfo();
            if (playingInfo != null) {
                WritableMap result = Arguments.createMap();
                result.putString("mediaId", (String) playingInfo.get("mediaId"));
                result.putString("title", (String) playingInfo.get("title"));
                result.putString("subtitle", (String) playingInfo.get("subtitle"));
                result.putString("icon", (String) playingInfo.get("icon"));
                
                // Convert extras back to WritableMap
                @SuppressWarnings("unchecked")
                Map<String, Object> extras = (Map<String, Object>) playingInfo.get("extras");
                if (extras != null) {
                    WritableMap extrasMap = Arguments.createMap();
                    for (Map.Entry<String, Object> entry : extras.entrySet()) {
                        Object value = entry.getValue();
                        if (value instanceof String) {
                            extrasMap.putString(entry.getKey(), (String) value);
                        } else if (value instanceof Integer) {
                            extrasMap.putInt(entry.getKey(), (Integer) value);
                        } else if (value instanceof Boolean) {
                            extrasMap.putBoolean(entry.getKey(), (Boolean) value);
                        }
                    }
                    result.putMap("extras", extrasMap);
                }
                
                return result;
            }
        }
        return null;
    }
    
    /**
     * Clear pending media info and cleanup background player
     */
    public void clearPendingMedia() {
        JWLog.d(TAG, "clearPendingMedia()");
        playingInfoManager.clearPlayingInfo();
        cleanupBackgroundPlayer();
    }
    
    /**
     * Get the current background player instance for MediaSession control
     */
    public JWPlayer getBackgroundPlayer() {
        JWLog.d(TAG, "getBackgroundPlayer() -> " + JWLog.id(backgroundPlayer));
        return backgroundPlayer;
    }
    
    /**
     * Control methods for MediaSession callbacks
     */
    public void playFromMediaSession() {
        JWLog.d(TAG, "playFromMediaSession()");
        if (backgroundPlayer != null) {
            backgroundPlayer.play();
        }
    }
    
    public void pauseFromMediaSession() {
        JWLog.d(TAG, "pauseFromMediaSession()");
        if (backgroundPlayer != null) {
            backgroundPlayer.pause();
        }
    }
    
    public void stopFromMediaSession() {
        JWLog.d(TAG, "stopFromMediaSession()");
        if (backgroundPlayer != null) {
            backgroundPlayer.stop();
            cleanupBackgroundPlayer();
        }
    }
    
    /**
     * Start position updates for MediaSession
     */
    private void startPositionUpdates() {
        JWLog.d(TAG, "startPositionUpdates()");
        if (positionUpdateTimer != null) {
            positionUpdateTimer.cancel();
        }
        
        positionUpdateTimer = new Timer();
        positionUpdateTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                updateMediaSessionPosition();
            }
        }, 1000, 1000); // Update every second
    }
    
    /**
     * Stop position updates
     */
    private void stopPositionUpdates() {
        JWLog.d(TAG, "stopPositionUpdates()");
        if (positionUpdateTimer != null) {
            positionUpdateTimer.cancel();
            positionUpdateTimer = null;
        }
    }
    
    /**
     * Update MediaSession with current position
     */
    private void updateMediaSessionPosition() {
        JWLog.d(TAG, "updateMediaSessionPosition()");
        if (backgroundPlayer != null) {
            try {
                long position = (long)(backgroundPlayer.getPosition() * 1000); // Convert to milliseconds
                // Keep MediaSession playback state in sync when available
                if (sharedMediaSession != null) {
                    sharedMediaSession.setPlaybackState(new PlaybackStateCompat.Builder()
                        .setState(isPlaying ? PlaybackStateCompat.STATE_PLAYING : PlaybackStateCompat.STATE_PAUSED, position, isPlaying ? 1.0f : 0.0f)
                        .setActions(PlaybackStateCompat.ACTION_PLAY | 
                                   PlaybackStateCompat.ACTION_PAUSE | 
                                   PlaybackStateCompat.ACTION_STOP |
                                   PlaybackStateCompat.ACTION_SEEK_TO |
                                   PlaybackStateCompat.ACTION_SKIP_TO_NEXT |
                                   PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS)
                        .build());
                }

                saveCurrentSeekPosition();
                    
            } catch (Exception e) {
                // Ignore errors during position updates
            }
        }
    }
    
    /**
     * Cleanup background player resources
     */
    private void cleanupBackgroundPlayer() {
        JWLog.d(TAG, "cleanupBackgroundPlayer()");
        try {
            JWLog.d(TAG, "🎵 JWPlayerNativePlaybackHandler: Cleaning up background player");
            
            // Stop position updates
            stopPositionUpdates();
            
            saveCurrentSeekPosition();
            
            // Release audio focus
            releaseAudioFocus();
            
            if (backgroundPlayer != null) {
                JWLog.d(TAG, "🎵 JWPlayerNativePlaybackHandler: 🔧 Player state before cleanup: " + backgroundPlayer.getState());
                
                // Stop the player before cleanup
                try {
                    backgroundPlayer.stop();
                    JWLog.d(TAG, "🎵 JWPlayerNativePlaybackHandler: 🔧 Player stopped successfully");
                } catch (Exception e) {
                    JWLog.w(TAG, "🎵 JWPlayerNativePlaybackHandler: 🔧 Error stopping player: " + e.getMessage());
                }
                
                // Remove event listeners
                backgroundPlayer.removeListeners(this);
                JWLog.d(TAG, "🎵 JWPlayerNativePlaybackHandler: 🔧 Event listeners removed");
                backgroundPlayer = null;
            }
            
            if (mediaSessionHelper != null) {
                // RNJWMediaSessionHelper will clean up its own resources
                mediaSessionHelper = null;
            }
            
            if (serviceMediaApi != null) {
                // Clean up service media api
                serviceMediaApi = null;
            }
            
            if (backgroundPlayerView != null) {
                // Clean up player view
                backgroundPlayerView = null;
            }
            
            if (notificationHelper != null) {
                notificationHelper = null;
            }
            
            // Mark lifecycle as stopped on main thread
            mainHandler.post(() -> {
                if (lifecycleRegistry.getCurrentState().isAtLeast(Lifecycle.State.STARTED)) {
                    lifecycleRegistry.markState(Lifecycle.State.CREATED);
                }
            });
            
            JWLog.d(TAG, "🎵 JWPlayerNativePlaybackHandler: Background player cleanup completed");
            
        } catch (Exception e) {
            JWLog.e(TAG, "🎵 JWPlayerNativePlaybackHandler: Error during background player cleanup", e);
        }
    }
    
    /**
     * Request audio focus for background playback
     * Following the same pattern as RNJWPlayerView
     */
    private boolean requestAudioFocus() {
        JWLog.d(TAG, "📱 JAVA: requestAudioFocus called - current hasAudioFocus: " + hasAudioFocus);
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (hasAudioFocus) {
                JWLog.d(TAG, "📱 JAVA: Audio focus already granted");
                return true;
            }

            if (audioManager != null) {
                JWLog.d(TAG, "📱 JAVA: Requesting audio focus for API 26+");
                
                AudioAttributes playbackAttributes = new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build();
                        
                focusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                        .setAudioAttributes(playbackAttributes)
                        .setAcceptsDelayedFocusGain(true)
                        .setOnAudioFocusChangeListener(this)
                        .build();

                int res = audioManager.requestAudioFocus(focusRequest);
                synchronized (focusLock) {
                    if (res == AudioManager.AUDIOFOCUS_REQUEST_FAILED) {
                        JWLog.w(TAG, "📱 JAVA: Audio focus request failed");
                        playbackNowAuthorized = false;
                        return false;
                    } else if (res == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                        JWLog.d(TAG, "📱 JAVA: Audio focus granted");
                        playbackNowAuthorized = true;
                        hasAudioFocus = true;
                        return true;
                    } else if (res == AudioManager.AUDIOFOCUS_REQUEST_DELAYED) {
                        JWLog.d(TAG, "📱 JAVA: Audio focus delayed");
                        playbackDelayed = true;
                        playbackNowAuthorized = false;
                        return false;
                    }
                }
            }
        } else {
            // For older Android versions, use deprecated API
            JWLog.d(TAG, "📱 JAVA: Requesting audio focus for API < 26");
            if (audioManager != null) {
                int res = audioManager.requestAudioFocus(this, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);
                JWLog.d(TAG, "📱 JAVA: Audio focus request result: " + res);
                if (res == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                    hasAudioFocus = true;
                    playbackNowAuthorized = true;
                    JWLog.d(TAG, "📱 JAVA: Audio focus granted for older API");
                    return true;
                }
            }
        }
        
        JWLog.w(TAG, "📱 JAVA: Audio focus request failed or not handled");
        return false;
    }
    
    /**
     * Release audio focus
     */
    private void releaseAudioFocus() {
        JWLog.d(TAG, "releaseAudioFocus()");
        if (audioManager != null && hasAudioFocus) {
            JWLog.d(TAG, "🎵 JWPlayerNativePlaybackHandler: Releasing audio focus");
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && focusRequest != null) {
                audioManager.abandonAudioFocusRequest(focusRequest);
            } else {
                audioManager.abandonAudioFocus(this);
            }
            
            hasAudioFocus = false;
            playbackNowAuthorized = false;
        }
    }
    
    /**
     * Audio focus change listener
     */
    @Override
    public void onAudioFocusChange(int focusChange) {
        JWLog.d(TAG, "📱 JAVA: onAudioFocusChange called with focusChange: " + focusChange);
        
        switch (focusChange) {
            case AudioManager.AUDIOFOCUS_GAIN:
                JWLog.d(TAG, "📱 JAVA: Audio focus gained");
                synchronized (focusLock) {
                    playbackNowAuthorized = true;
                    hasAudioFocus = true;
                    if (playbackDelayed) {
                        playbackDelayed = false;
                        if (backgroundPlayer != null) {
                            JWLog.d(TAG, "📱 JAVA: Resuming delayed playback");
                            backgroundPlayer.play();
                        }
                    }
                }
                break;
                
            case AudioManager.AUDIOFOCUS_LOSS:
                JWLog.d(TAG, "📱 JAVA: Audio focus lost");
                synchronized (focusLock) {
                    playbackNowAuthorized = false;
                    hasAudioFocus = false;
                    if (backgroundPlayer != null) {
                        JWLog.d(TAG, "📱 JAVA: Pausing player due to audio focus loss");
                        backgroundPlayer.pause();
                    }
                }
                break;
                
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
                JWLog.d(TAG, "📱 JAVA: Audio focus lost (transient)");
                synchronized (focusLock) {
                    playbackNowAuthorized = false;
                    if (backgroundPlayer != null) {
                        JWLog.d(TAG, "📱 JAVA: Pausing player due to transient focus loss");
                        backgroundPlayer.pause();
                    }
                }
                break;
                
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
                JWLog.d(TAG, "📱 JAVA: Audio focus lost (can duck)");
                // Could implement ducking here, for now just continue playing
                break;
        }
    }
    
    // JWPlayer Event Listeners - Enhanced for actual background playback
    @Override
    public void onReady(ReadyEvent readyEvent) {
        JWLog.d(TAG, "📱 JAVA: 🎵 onReady event - Background player is ready for playback");
        JWLog.d(TAG, "📱 JAVA: 🎵 onReady - Thread: " + Thread.currentThread().getName());
        JWLog.d(TAG, "📱 JAVA: 🎵 onReady - Timestamp: " + System.currentTimeMillis());
        
        if (backgroundPlayer != null && playbackNowAuthorized && hasAudioFocus) {
            JWLog.d(TAG, "📱 JAVA: 🎵 Player ready and authorized, should start playing automatically");
        } else {
            JWLog.d(TAG, "📱 JAVA: 🎵 Player ready but waiting - authorized: " + playbackNowAuthorized + ", hasAudioFocus: " + hasAudioFocus);
        }
    }
    
    @Override
    public void onPlay(PlayEvent playEvent) {
        JWLog.d(TAG, "📱 JAVA: ✅ onPlay event - Background playback started successfully!");
        
        // RNJWMediaSessionHelper will handle MediaSession state updates
        isPlaying = true;
        hasStartedPlayback = true; // mark first successful start
        autostartAttempts = 0; // reset attempts
        startPositionUpdates();
    }
    
    @Override
    public void onPause(PauseEvent pauseEvent) {
        JWLog.d(TAG, "📱 JAVA: ⏸️ onPause event - Background playback paused");
        
        // RNJWMediaSessionHelper will handle MediaSession state updates
        isPlaying = false;
        // keep position updates running for scrub accuracy
        startPositionUpdates();
    }
    
    @Override
    public void onError(ErrorEvent errorEvent) {
        JWLog.e(TAG, "📱 JAVA: ❌ onError event - Background playback error: " + errorEvent.getMessage());
        
        // Clear pending media on error
        playingInfoManager.clearPlayingInfo();
        
        // Cleanup background player on error
        cleanupBackgroundPlayer();
    }
    
    @Override
    public void onComplete(CompleteEvent completeEvent) {
        JWLog.d(TAG, "📱 JAVA: ✅ onComplete event - Background playback completed");
        
        // Clear the pending media since playback is complete
        playingInfoManager.clearPlayingInfo();
        
        // Cleanup background player after completion
        cleanupBackgroundPlayer();
    }
    
    /**
     * Update MediaSession metadata for now playing display
     */
    private void updateMediaSessionMetadata(String title, String subtitle, String iconUrl, Map<String, Object> post) {
        JWLog.d(TAG, "updateMediaSessionMetadata(title=" + JWLog.safe(title) + ", subtitle=" + JWLog.safe(subtitle) + ")");
        if (sharedMediaSession == null) {
            return;
        }
        
        try {
            if ((iconUrl == null || iconUrl.isEmpty()) && post != null) {
                iconUrl = resolveImageUrl(post);
            } else if (iconUrl != null) {
                iconUrl = normalizeArtworkValue(iconUrl);
            }

            MediaMetadataCompat.Builder metadataBuilder = new MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, title != null ? title : "Unknown Title")
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, subtitle != null ? subtitle : "Unknown Artist");
            
            // Add media ID if available
            if (post != null && post.containsKey("mediaId")) {
                metadataBuilder.putString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID, (String) post.get("mediaId"));
            }
            
            // Add duration if available
            if (post != null && post.containsKey("duration")) {
                Object durationObj = post.get("duration");
                if (durationObj instanceof Number) {
                    long durationMs = (long) (((Number) durationObj).doubleValue() * 1000);
                    metadataBuilder.putLong(MediaMetadataCompat.METADATA_KEY_DURATION, durationMs);
                }
            }
            
            // Add artwork if available
            if (iconUrl != null && !iconUrl.isEmpty()) {
                metadataBuilder.putString(MediaMetadataCompat.METADATA_KEY_ALBUM_ART_URI, iconUrl);
                metadataBuilder.putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON_URI, iconUrl);
            }
            
            // Update the MediaSession with new metadata
            sharedMediaSession.setMetadata(metadataBuilder.build());
            
            // Set initial playback state with position from timepoint
            long positionMs = 0;
            if (post != null && post.containsKey("timepoint")) {
                Object timepointObj = post.get("timepoint");
                if (timepointObj instanceof Number) {
                    positionMs = (long) (((Number) timepointObj).doubleValue() * 1000); // Convert seconds to milliseconds
                }
            }
            
            sharedMediaSession.setPlaybackState(new PlaybackStateCompat.Builder()
                .setState(PlaybackStateCompat.STATE_STOPPED, positionMs, 1.0f)
                .setActions(PlaybackStateCompat.ACTION_PLAY | 
                           PlaybackStateCompat.ACTION_PAUSE | 
                           PlaybackStateCompat.ACTION_STOP |
                           PlaybackStateCompat.ACTION_SEEK_TO |
                           PlaybackStateCompat.ACTION_SKIP_TO_NEXT |
                           PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS)
                .build());
        } catch (Exception e) {
            JWLog.e(TAG, "Error updating MediaSession metadata", e);
        }
    }
    
    /**
     * Emit stop event to React Native to stop any UI players
     */
    private void emitStopEventToReactNative() {
        JWLog.d(TAG, "emitStopEventToReactNative()");
        try {
            ReactContext reactContext = getReactContext();
            if (reactContext != null) {
                WritableMap eventData = Arguments.createMap();
                eventData.putString("action", "stop_ui_player");
                eventData.putString("reason", "background_playback_starting");
                
                reactContext.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
                    .emit("onStopUIPlayer", eventData);
                    
                JWLog.d(TAG, "📱 JAVA: Emitted stop UI player event");
            }
        } catch (Exception e) {
            JWLog.e(TAG, "Error emitting stop UI player event", e);
        }
    }
    
    /**
     * Fallback: dispatch media selection to React Native to let app-side loaders resolve playback
     */
    private void dispatchMediaItemToReactNative(String mediaId) {
        if (mediaId == null || mediaId.isEmpty()) {
            JWLog.w(TAG, "dispatchMediaItemToReactNative: skipped due to empty mediaId");
            return;
        }

        try {
            Class<?> mediaBrowserServiceClass = Class.forName("com.mediabrowser.MediaBrowserService");
            java.lang.reflect.Method sendToReactMethod = mediaBrowserServiceClass.getMethod("sendMediaItemToReactNative", String.class);
            sendToReactMethod.invoke(null, mediaId);
            JWLog.d(TAG, "dispatchMediaItemToReactNative: forwarded mediaId=" + JWLog.safe(mediaId));
        } catch (Exception e) {
            JWLog.w(TAG, "dispatchMediaItemToReactNative failed: " + e.getMessage());
        }
    }

    private void fallbackToReactNative(String mediaId, JWPlayer uiPlayer) {
        JWLog.d(TAG, "fallbackToReactNative(mediaId=" + JWLog.safe(mediaId) + ")");
        if (uiPlayer != null) {
            try { uiPlayer.pause(); } catch (Exception ignored) {}
            try { uiPlayer.stop(); } catch (Exception ignored) {}
        }
        emitStopEventToReactNative();
        dispatchMediaItemToReactNative(mediaId);
    }

    /**
     * Helper method to get ReactContext using multiple approaches
     */
    private ReactContext getReactContext() {
        JWLog.d(TAG, "getReactContext()");
        ReactContext reactContext = null;
        
        // Approach 1: Try getting from MediaItemsStore (MediaBrowser context)
        try {
            Class<?> mediaItemsStoreClass = Class.forName("com.mediabrowser.MediaItemsStore");
            Object instance = mediaItemsStoreClass.getMethod("getInstance").invoke(null);
            reactContext = (ReactContext) mediaItemsStoreClass.getMethod("getReactApplicationContext").invoke(instance);
        } catch (Exception e) {
            // Ignore - try next approach
        }
        
        // Approach 2: Try getting from application context if first approach failed
        if (reactContext == null) {
            Context appContext = context.getApplicationContext();
            if (appContext instanceof ReactApplication) {
                ReactApplication reactApp = (ReactApplication) appContext;
                ReactNativeHost reactHost = reactApp.getReactNativeHost();
                if (reactHost != null) {
                    ReactInstanceManager reactInstanceManager = reactHost.getReactInstanceManager();
                    if (reactInstanceManager != null) {
                        reactContext = reactInstanceManager.getCurrentReactContext();
                    }
                }
            }
        }
        
        return reactContext;
    }
    
    // LifecycleOwner implementation for JWPlayer
    @Override
    public Lifecycle getLifecycle() {
        return lifecycleRegistry;
    }

    private void scheduleAlbumArtFetch(String imageUrl) {
        JWLog.d(TAG, "scheduleAlbumArtFetch(url=" + JWLog.safe(imageUrl) + ")");
        artworkExecutor.submit(() -> {
            try {
                JWLog.d(TAG, "ARTWORK_DEBUG: fetching album art " + imageUrl);
                InputStream in = new URL(imageUrl).openStream();
                Bitmap bmp = BitmapFactory.decodeStream(in);
                if (bmp != null && sharedMediaSession != null) {
                    mainHandler.post(() -> {
                        try {
                            MediaMetadataCompat current = sharedMediaSession.getController().getMetadata();
                            MediaMetadataCompat.Builder builder = new MediaMetadataCompat.Builder(current);
                            builder.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, bmp);
                            builder.putBitmap(MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON, bmp);
                            sharedMediaSession.setMetadata(builder.build());
                            JWLog.d(TAG, "ARTWORK_DEBUG: album art applied to session");
                        } catch (Exception e) {
                            JWLog.w(TAG, "ARTWORK_DEBUG: failed applying bitmap: " + e.getMessage());
                        }
                    });
                }
            } catch (Exception e) {
                JWLog.w(TAG, "ARTWORK_DEBUG: fetch failed: " + e.getMessage());
            }
        });
    }

    private void attemptAutostart(String phase) {
        JWLog.d(TAG, "attemptAutostart(phase=" + phase + ")");
        if (!autoStartEnabled) return;
        if (backgroundPlayer == null) return;
        if (hasStartedPlayback) return; // already started
        if (!(playbackNowAuthorized && hasAudioFocus)) {
            JWLog.d(TAG, "\ud83d\udcf1 JAVA: Autostart phase " + phase + " skipped (not authorized yet)");
            return;
        }
        autostartAttempts++;
        try {
            JWLog.d(TAG, "\ud83d\udcf1 JAVA: Autostart " + phase + " attempt #" + autostartAttempts);
            backgroundPlayer.play();
        } catch (Exception e) {
            JWLog.w(TAG, "Autostart play() failed in phase " + phase + ": " + e.getMessage());
        }
    }

    private void scheduleAutostartRetry(String nextPhase, long delayMs) {
        JWLog.d(TAG, "scheduleAutostartRetry(nextPhase=" + nextPhase + ", delayMs=" + delayMs + ")");
        if (!autoStartEnabled) return;
        if (hasStartedPlayback) return;
        mainHandler.postDelayed(() -> attemptAutostart(nextPhase), delayMs);
    }

    private void startAutostartChain() {
        JWLog.d(TAG, "startAutostartChain()");
        attemptAutostart("initial");
        scheduleAutostartRetry("second", 400);
        scheduleAutostartRetry("third", 1200);
    }
}
