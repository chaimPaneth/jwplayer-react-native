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
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.support.v4.media.MediaBrowserCompat;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.MediaControllerCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.util.Log;
import android.view.KeyEvent;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.jwplayer.rnjwplayer.misc.MediaServiceFactory;
import com.jwplayer.rnjwplayer.misc.MediaSessionStateProvider;
import com.jwplayer.rnjwplayer.misc.PlaybackStateCompatWrapper;
import com.jwplayer.pub.api.JWPlayer;
import com.jwplayer.pub.api.PlayerState;
import com.jwplayer.pub.api.background.ServiceMediaApi;
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
import com.jwplayer.pub.api.events.listeners.AdvertisingEvents;
import com.jwplayer.pub.api.events.listeners.VideoPlayerEvents;
import com.jwplayer.pub.api.media.playlists.PlaylistItem;
import com.mediabrowser.MediaSessionSingleton;

import org.json.JSONException;
import org.json.JSONObject;

public class RNJWMediaSessionHelper implements AdvertisingEvents.OnAdCompleteListener, AdvertisingEvents.OnAdErrorListener, AdvertisingEvents.OnAdPlayListener, AdvertisingEvents.OnAdSkippedListener, VideoPlayerEvents.OnBufferListener, VideoPlayerEvents.OnErrorListener, VideoPlayerEvents.OnPauseListener, VideoPlayerEvents.OnPlayListener, VideoPlayerEvents.OnPlaylistCompleteListener, VideoPlayerEvents.OnPlaylistItemListener {
    private static final String TAG = "RNJWMediaSessionHelper";

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
    private static final long FOCUS_LOSS_IGNORE_WINDOW_MS = 1000; // 1 second

    // Static reference to track the active instance for delegation from MediaBrowserService
    private static RNJWMediaSessionHelper activeInstance = null;

    // Pending seek info
    private static Long pendingSeekMs = null;
    private static boolean pendingSeekApplied = false;

    private static String externalMediaId = null;

    // MediaSession callback to handle transport controls on Android 13/14+
    private final MediaSessionCompat.Callback mediaSessionCallback = new MediaSessionCompat.Callback() {
        @Override
        public void onPlay() {
            performPlay();
        }

        @Override
        public void onPause() {
            performPause();
        }

        @Override
        public void onStop() {
            performStop();
        }

        @Override
        public void onSeekTo(long position) {
            performSeekTo(position);            
        }

        @Override
        public void onSkipToNext() {
            performSkipToNext();
        }

        @Override
        public void onSkipToPrevious() {
            performSkipToPrevious();
        }

        @Override
        public void onPlayFromMediaId(String mediaId, Bundle extras) {
            performMediaItemSelection(mediaId, extras);
        }

        @Override
        public boolean onMediaButtonEvent(Intent mediaButtonIntent) {
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
                Log.w(TAG, "mediaSessionCallback onMediaButtonEvent error: " + ex.getMessage());
            }
            return super.onMediaButtonEvent(mediaButtonIntent);
        }
    };

    /** 
     * Attempts to capture the current playback position (from JWPlayer or MediaSession) 
     * and forward it to MediaBrowserService.
     */
    private void captureAndStoreSeekPosition() {
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

        storeSeekPosition(positionMs);
    }

    public void storeSeekPosition(long position) {
        if (externalMediaId == null) {
            Log.d(TAG, "storeSeekPosition: externalMediaId is null");
            return;
        }
        
        try {
            Class<?> mbs = Class.forName("com.mediabrowser.MediaBrowserService");
            java.lang.reflect.Method reportSeek =
                    mbs.getMethod("updateSeekPosition", String.class, long.class);
            reportSeek.invoke(null, externalMediaId, position); // position in ms
        } catch (Exception ignore) {
            // Safe to ignore; just don't break the seek
            Log.w(TAG, "Could not report seek to React Native: " + ignore.getMessage());
        }
    }

    public RNJWMediaSessionHelper(Context context, RNJWNotificationHelper notificationHelper, ServiceMediaApi serviceMediaApi) {
        this(context, notificationHelper, serviceMediaApi, new MediaServiceFactory());
    }

    private RNJWMediaSessionHelper(Context context, RNJWNotificationHelper notificationHelper, ServiceMediaApi serviceMediaApi, MediaServiceFactory bgaFactory) {
        this.context = context;
        this.rnjwNotificationHelper = notificationHelper;
        this.mediaServiceFactory = bgaFactory;
        
        // Set this as the active instance for delegation
        activeInstance = this;
        
        this.setupServiceMediaApi(serviceMediaApi);
    }

    final void setupServiceMediaApi(ServiceMediaApi serviceMediaApi) {
        this.cleanup();
        if (serviceMediaApi != null) {
            this.serviceMediaApi = serviceMediaApi;

            initServiceMediaApi();
        }
    }

    private void initServiceMediaApi() {
        Log.d(TAG, "initServiceMediaApi");

        if (activeInstance != this) {
            Log.w(TAG, "initServiceMediaApi: This instance is not active; skipping initialization");
            return;
        }

        if (this.mediaSessionStateProvider != null) {
            Log.w(TAG, "initServiceMediaApi: MediaSession already initialized");
            return;
        }

        this.jwPlayer = serviceMediaApi.getPlayer();
        Context currentContext = this.context;
        this.mediaSessionStateProvider =  new MediaSessionStateProvider(MediaSessionSingleton.getInstance(currentContext));
//            String simpleName = RNJWMediaSessionHelper.class.getSimpleName();
//            this.a = new b(new MediaSessionCompat(currentContext, simpleName));

        // Attach callback (was previously intentionally omitted)
        try {
            if (this.mediaSessionStateProvider != null && this.mediaSessionStateProvider.mediaSessionCompat != null) {
                this.mediaSessionStateProvider.mediaSessionCompat.setCallback(mediaSessionCallback);
            }
        } catch (Exception cbEx) {
            Log.w(TAG, "Failed to set MediaSession callback: " + cbEx.getMessage());
        }

        setupMediaButtonFallback(currentContext);
        
        // Check if background player is active and coordinate
        try {
            Class<?> handlerClass = Class.forName("com.jwplayer.rnjwplayer.JWPlayerNativePlaybackHandler");
            java.lang.reflect.Method getInstanceMethod = handlerClass.getMethod("getInstance", Context.class);
            Object handlerInstance = getInstanceMethod.invoke(null, this.context);
            
            if (handlerInstance != null) {
                // Check if background player is active
                java.lang.reflect.Method isActiveMethod = handlerClass.getMethod("isBackgroundPlayerActive");
                Boolean isBackgroundActive = (Boolean) isActiveMethod.invoke(handlerInstance);
                
                if (isBackgroundActive != null && isBackgroundActive) {                       
                    // Get background player info
                    java.lang.reflect.Method getInfoMethod = handlerClass.getMethod("getCurrentBackgroundPlayerInfo");
                    Object backgroundInfo = getInfoMethod.invoke(handlerInstance);
                    
                    if (backgroundInfo instanceof java.util.Map) {
                        java.util.Map<String, Object> bgInfo = (java.util.Map<String, Object>) backgroundInfo;
                        // Transfer background player to UI if they're playing the same content
                        java.lang.reflect.Method transferMethod = handlerClass.getMethod("transferToUIPlayer");
                        Object transferResult = transferMethod.invoke(handlerInstance);                            
                    }
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Could not coordinate with background player: " + e.getMessage());
        }
        
        // DON'T set callback here - MediaBrowserService already has the callback set
        // and it will delegate to us when needed via static methods or fallback to direct handling
        
        this.jwPlayer.addListeners(this, new EventType[]{EventType.PLAY, EventType.PAUSE, EventType.BUFFER, EventType.ERROR, EventType.PLAYLIST_ITEM, EventType.PLAYLIST_COMPLETE, EventType.AD_PLAY, EventType.AD_SKIPPED, EventType.AD_COMPLETE, EventType.AD_ERROR});
        JWPlayer currentJwPlayer = this.jwPlayer;
        this.updatePlaylistItem(currentJwPlayer.getPlaylistItem());
        this.updatePlayerState(currentJwPlayer.getState());
    }

    private void updatePlaybackState(JWPlayer player, int state) {
        updatePlaybackState(player, state, null);
    }

    private void updatePlaybackState(JWPlayer player, int state, Long overridePositionMs) {
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
                PlaybackStateCompat.ACTION_PLAY_FROM_MEDIA_ID;

        if (serviceMediaApi != null) {
            try {
                long caps = this.serviceMediaApi.getNotificationCapabilities();
                actions |= caps;
            } catch (Exception ex) {
                Log.w(TAG, "updatePlaybackState: capabilities read failed " + ex.getMessage());
            }
        }

        float speed = (state == PlaybackStateCompat.STATE_PLAYING) ? 1.0f : 0.0f;

        PlaybackStateCompat.Builder builder = new PlaybackStateCompat.Builder()
                .setState(state, positionMs, speed)
                .setActions(actions);

        try {
            this.mediaSessionStateProvider.mediaSessionCompat.setPlaybackState(builder.build());
            this.mediaSessionStateProvider.mediaSessionCompat.setActive(true);
        } catch (Exception ex) {
            Log.w(TAG, "updatePlaybackState: set failed " + ex.getMessage());
        }
    }

    private void setupMediaButtonFallback(Context ctx) {
        if (mediaButtonFallbackReceiver != null) return;

        mediaButtonFallbackReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
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
                    Log.w(TAG, "Fallback media button handling error " + ex.getMessage());
                }
            }
        };
        try {
            IntentFilter intentFilter = new IntentFilter(Intent.ACTION_MEDIA_BUTTON);
            ContextCompat.registerReceiver(
                    ctx,
                    mediaButtonFallbackReceiver,
                    intentFilter,
                    ContextCompat.RECEIVER_EXPORTED);
        } catch (Exception ex) {
            Log.w(TAG, "setupMediaButtonFallback: register failed " + ex.getMessage());
        }
    }

    // Audio focus management
    private boolean requestAudioFocusForPlayback(Context ctx) {
        // If this is from Android Auto, let Android Auto handle audio focus
        if (isPlayingFromAndroidAuto) {
            currentlyHasFocus = true; // Assume we have focus
            return true;
        }

        // Don't request if we already have focus
        if (currentlyHasFocus) {
            return true;
        }
        
        lastFocusRequestTime = System.currentTimeMillis();
        
        if (audioManager == null) {
            audioManager = (android.media.AudioManager) ctx.getSystemService(Context.AUDIO_SERVICE);
        }
        if (audioManager == null) {
            Log.w(TAG, "AudioManager unavailable - cannot request focus");
            return false;
        }

        int resultRequestAudioFocus = android.media.AudioManager.AUDIOFOCUS_REQUEST_FAILED;

        if (android.os.Build.VERSION.SDK_INT >= 26) {
            if (audioFocusRequest == null) {
                audioFocusRequest = new android.media.AudioFocusRequest.Builder(android.media.AudioManager.AUDIOFOCUS_GAIN)
                    .setOnAudioFocusChangeListener(fc -> {
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
            Log.w(TAG, "Audio focus request denied: " + resultRequestAudioFocus);
        }
        return resultRequestAudioFocus == android.media.AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
    }

    private void handleAudioFocusChange(int focusChange) {
        long currentTime = System.currentTimeMillis();
        boolean currentlyPlaying = isCurrentlyPlaying();
        long timeSinceLastRequest = currentTime - lastFocusRequestTime;

        switch (focusChange) {
            case android.media.AudioManager.AUDIOFOCUS_GAIN:
                currentlyHasFocus = true;
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
                        Log.w(TAG, "Error resuming after focus gain: " + ignore.getMessage());
                    }
                }
                break;
                
            case android.media.AudioManager.AUDIOFOCUS_LOSS:
                // Ignore focus loss if it happens too soon after requesting focus
                currentlyHasFocus = false;
                if (timeSinceLastRequest < FOCUS_LOSS_IGNORE_WINDOW_MS) {
                    return;
                }
                
                if (isCurrentlyPlaying()) {
                    wasPlayingBeforeFocusLoss = true;
                    pausePlayback();
                }
                break;
                
            case android.media.AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
                currentlyHasFocus = false;
                // Also ignore transient loss if too soon
                if (timeSinceLastRequest < FOCUS_LOSS_IGNORE_WINDOW_MS) {
                    return;
                }
                
                if (isCurrentlyPlaying()) {
                    wasPlayingBeforeFocusLoss = true;
                    pausePlayback();
                }
                break;
                
            case android.media.AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
                break;
                
            default:
                break;
        }
    }

    private boolean isCurrentlyPlaying() {
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
        Log.d(TAG, "Pausing playback due to audio focus loss");        

        // Don't pause during any seek operation
        if (isAnySeekInProgress()) {
            Log.d(TAG, "Seek in progress - skipping pause");
            return;
        }
        
        try {
            if (mediaSessionStateProvider != null && mediaSessionStateProvider.mediaSessionCompat != null) {
                mediaSessionStateProvider.mediaSessionCompat
                    .getController()
                    .getTransportControls()
                    .pause();
            } else if (jwPlayer != null) {
                jwPlayer.pause();
            }
        } catch (Exception ignore) {
            Log.w(TAG, "Error pausing after focus loss: " + ignore.getMessage());
        }
    }

    private void releaseAudioFocus() {
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
                Log.w(TAG, "Error releasing audio focus: " + ex.getMessage());
            }
            audioManager = null;
        }
        
        currentlyHasFocus = false;
        wasPlayingBeforeFocusLoss = false;
    }

    // Reset the flag when playback ends or changes
    private void resetAndroidAutoFlag() {
        isPlayingFromAndroidAuto = false;
    }
    
    // End Audio focus management

    private boolean hasAudioFocus() {
        // This is a simplified check - Android doesn't provide a direct way to query focus state
        // You could track this with a boolean field updated in focus change listener
        return audioManager != null && (audioFocusRequest != null || legacyFocusChangeListener != null);
    }

    private boolean isAnySeekInProgress() {
        // Check both pending seek (media selection) and recent manual seek
        boolean hasPendingSeek = pendingSeekMs != null && !pendingSeekApplied;
        // boolean hasRecentManualSeek = (System.currentTimeMillis() - lastManualSeekTime) < MANUAL_SEEK_PROTECTION_MS;
        
        return hasPendingSeek; // || hasRecentManualSeek;
    }

    final void cleanup() {
        if (this.mediaSessionStateProvider != null) {
            // Clear active instance if this is the active one
            if (activeInstance == this) {
                activeInstance = null;
            }
        }

        softCleanup();
    }

    private final void softCleanup() {
        // Reset AA flag and release audio focus first
        resetAndroidAutoFlag();
        releaseAudioFocus();

        RNJWNotificationHelper notificationHelper;

        // --- MediaSession soft close (no release) ---
        if (this.mediaSessionStateProvider != null && this.mediaSessionStateProvider.mediaSessionCompat != null) {
            // Unregister fallback receiver if present
            if (mediaButtonFallbackReceiver != null) {
                try {
                    context.unregisterReceiver(mediaButtonFallbackReceiver);
                } catch (Exception unregEx) {
                    Log.w(TAG, "Failed to unregister media button fallback receiver: " + unregEx.getMessage());
                }
                mediaButtonFallbackReceiver = null;
            }

            try {
                // 1) Publish a no‑playback state so controllers/AA drop Now Playing
                PlaybackStateCompat.Builder stateBuilder = new PlaybackStateCompat.Builder()
                        .setState(PlaybackStateCompat.STATE_NONE, 0L, 0f)
                        .setActions(0L);
                this.mediaSessionStateProvider.mediaSessionCompat.setPlaybackState(stateBuilder.build());
            } catch (Exception ex) {
                Log.w(TAG, "softCleanup: setPlaybackState failed " + ex.getMessage());
            }

            try {
                // 2) Clear metadata/queue so no stale UI remains
                this.mediaSessionStateProvider.mediaSessionCompat.setMetadata(null);
                try {
                    this.mediaSessionStateProvider.mediaSessionCompat.setQueue(null);
                } catch (Throwable ignore) { /* setQueue may be unsupported in some paths */ }
            } catch (Exception ex) {
                Log.w(TAG, "softCleanup: clearing metadata/queue failed " + ex.getMessage());
            }

            try {
                // 3) Deactivate the session (keep it alive for reuse)
                this.mediaSessionStateProvider.mediaSessionCompat.setActive(false);
            } catch (Exception ex) {
                Log.w(TAG, "softCleanup: setActive(false) failed " + ex.getMessage());
            }

            // IMPORTANT: Do NOT call release() here; keep the session object for future use
            // this.serviceMediaApi can be cleared to avoid stale references
            this.serviceMediaApi = null;
        }

        // --- Player/notification cleanup ---
        if (this.jwPlayer != null) {
            this.jwPlayer.removeListeners(this,
                    new EventType[]{EventType.PLAY, EventType.PAUSE, EventType.BUFFER, EventType.ERROR, EventType.PLAYLIST_ITEM, EventType.PLAYLIST_COMPLETE, EventType.AD_PLAY, EventType.AD_SKIPPED, EventType.AD_COMPLETE, EventType.AD_ERROR});
            (notificationHelper = this.rnjwNotificationHelper).notificationManager.cancel(notificationHelper.notificationId);
            this.jwPlayer = null;
        } else {
            // Even if jwPlayer is null, make sure the media notification is hidden
            try {
                (notificationHelper = this.rnjwNotificationHelper).notificationManager.cancel(notificationHelper.notificationId);
            } catch (Throwable ignore) {}
        }
    }

    private static long extractResumePosition(Bundle extras) {
       if (extras == null) return -1;

        // Parse JSON payload from extras: "info"
        String infoJson = extras.getString("info", null);

        if (infoJson != null) {
            try {
                JSONObject obj = new JSONObject(infoJson);

                Double sec = readSeconds(obj, "timepoint");

                if (sec != null && sec > 0) {
                    long ms = (long) (sec * 1000L);
                    return ms;
                }
            } catch (Exception e) {
                Log.w(TAG, "Failed parsing extras JSON for resume: " + e.getMessage());
            }
        }
        return 0L;
    }

    /** Helper: reads a seconds value from JSON by key, accepting numbers or numeric strings. */
    private static Double readSeconds(JSONObject obj, String key) {
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

    private void updatePlayerState(PlayerState playerState) {
        PlaybackStateCompatWrapper currentPlaybackState = this.mediaSessionStateProvider.getPlaybackState();
        PlaybackStateCompatWrapper.Builder playbackStateBuilder = new PlaybackStateCompatWrapper.Builder(currentPlaybackState);
        long notificationCapabilities = this.serviceMediaApi.getNotificationCapabilities();
        playbackStateBuilder.builder.setActions(notificationCapabilities | PlaybackStateCompat.ACTION_SEEK_TO);
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

        float speed = (playbackState == PlaybackStateCompat.STATE_PLAYING) ? 1.0F : 0.0F;
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
        if (playlistItem == null || this.jwPlayer == null) {
            return;
        }

        PlaylistItem currentItem = this.jwPlayer.getPlaylistItem();

        // Keep last known or existing metadata
        MediaMetadataCompat existing = (this.mediaSessionStateProvider != null && this.mediaSessionStateProvider.mediaSessionCompat != null)
                ? this.mediaSessionStateProvider.mediaSessionCompat.getController().getMetadata()
                : null;
        MediaMetadataCompat.Builder builder = (existing == null)
                ? new MediaMetadataCompat.Builder()
                : new MediaMetadataCompat.Builder(existing);

        builder.putString("android.media.metadata.DISPLAY_TITLE", 
            playlistItem.getTitle() != null ? playlistItem.getTitle() : "");

        builder.putString("android.media.metadata.DISPLAY_SUBTITLE", 
            playlistItem.getDescription() != null ? playlistItem.getDescription() : "");

        builder.putString("android.media.metadata.MEDIA_ID", 
            playlistItem.getMediaId() != null ? playlistItem.getMediaId() : "");

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
        }

        if (playlistItem.getImage() != null && !playlistItem.getImage().isEmpty()) {
            this.updateAlbumArt(playlistItem.getImage());
        }
    }

    public void onPlaylistItem(PlaylistItemEvent playlistItemEvent) {
        this.updatePlaylistItem(playlistItemEvent.getPlaylistItem());
        // Try to apply pending seek when the item switches
        // Resolve the app-level mediaId to look up the MediaBrowser item
        if (externalMediaId != null) {
            long resumeMs = queryResumeViaReflection(externalMediaId); // contract: -1 = absent

            if (resumeMs >= 0) {
                pendingSeekMs = resumeMs;
                pendingSeekApplied = false;
                applyPendingSeekWhenReady(playlistItemEvent.getPlaylistItem());
            }
        }
    }

    public void onError(ErrorEvent errorEvent) {
        this.updatePlayerState(PlayerState.ERROR);
        this.mediaSessionStateProvider.mediaSessionCompat.release();
    }

    public void onAdComplete(AdCompleteEvent adCompleteEvent) {
    }

    public void onAdSkipped(AdSkippedEvent adSkippedEvent) {
    }

    public void onAdPlay(AdPlayEvent adPlayEvent) {
    }

    public void onAdError(AdErrorEvent adErrorEvent) {
    }

    public void onBuffer(BufferEvent bufferEvent) {
        this.updatePlayerState(PlayerState.BUFFERING);
        updatePlaybackState(jwPlayer, PlaybackStateCompat.STATE_BUFFERING);
        // Try to apply pending seek while buffering
        applyPendingSeekWhenReady(jwPlayer != null ? jwPlayer.getPlaylistItem() : null);
    }

    public void onPause(PauseEvent pauseEvent) {
        this.updatePlayerState(PlayerState.PAUSED);
        updatePlaybackState(jwPlayer, PlaybackStateCompat.STATE_PAUSED);
    }

    public void onPlay(PlayEvent playEvent) {
        this.updatePlayerState(PlayerState.PLAYING);
        updatePlaybackState(jwPlayer, PlaybackStateCompat.STATE_PLAYING);
        // Try to apply pending seek as soon as playback starts
        applyPendingSeekWhenReady(jwPlayer != null ? jwPlayer.getPlaylistItem() : null);
    }

    public void onPlaylistComplete(PlaylistCompleteEvent playlistCompleteEvent) {
        resetAndroidAutoFlag();

        if (this.mediaSessionStateProvider == null || this.mediaSessionStateProvider.mediaSessionCompat == null) return;

        try {
            // Build a PAUSED playback state
            // This keeps the progress bar interactive and shows correct button state
            PlaybackStateCompatWrapper capsWrapper = this.mediaSessionStateProvider.getPlaybackState();
            PlaybackStateCompatWrapper.Builder stateBuilder = new PlaybackStateCompatWrapper.Builder(capsWrapper);

            // Include seek actions so progress bar remains interactive
            long actions = PlaybackStateCompat.ACTION_PLAY
                    | PlaybackStateCompat.ACTION_PLAY_FROM_MEDIA_ID
                    | PlaybackStateCompat.ACTION_SEEK_TO;

            if (this.serviceMediaApi != null) {
                try {
                    long caps = this.serviceMediaApi.getNotificationCapabilities();
                    actions |= caps;
                } catch (Exception ex) {
                    Log.w(TAG, "Could not get service capabilities: " + ex.getMessage());
                }
            }

            stateBuilder.builder.setActions(actions);

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
                Log.w(TAG, "Could not get duration/position for completion: " + ex.getMessage());
                positionMs = 0;
            }

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
            Log.w(TAG, "Failed to set completion state", ex);
        }
    }

    void updateAlbumArt(String bitmapPath) {
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
        if (mediaId == null || mediaId.isEmpty()) return 0L;
        try {
            Class<?> c = Class.forName("com.mediabrowser.MediaItemsResumeProvider");
            java.lang.reflect.Method m = c.getMethod("getResumePositionMs", String.class);
            Object out = m.invoke(null, mediaId);
            return (out instanceof Number) ? ((Number) out).longValue() : 0L;
        } catch (Throwable t) {
            Log.d(TAG, "Resume provider unavailable: " + t.getMessage());
            return 0L;
        }
    }

    private void applyPendingSeekWhenReady(PlaylistItem item) {
        if (pendingSeekMs == null || jwPlayer == null || pendingSeekApplied) return;

        // Do not block on id mismatches: items can legitimately differ across domains
        double duration = 0;
        try { duration = jwPlayer.getDuration(); } catch (Exception ignored) {}
        PlayerState st = jwPlayer.getState();

        if (duration > 0 || st == PlayerState.BUFFERING || st == PlayerState.PLAYING || st == PlayerState.PAUSED) {
            performSeekTo(pendingSeekMs);
            pendingSeekApplied = true;
            pendingSeekMs = null;
        } else {
            Log.d(TAG, "Pending seek not applied; player not ready yet. Duration: " + duration + ", State: " + st);
        }
    }
    
    private void performPlay() {
        boolean focusGranted = requestAudioFocusForPlayback(context);
        if (!focusGranted) {
            Log.w(TAG, "Audio focus not granted - proceeding anyway");
        }

        try {
            if (serviceMediaApi != null) {
                serviceMediaApi.onPlay();
            } else if (jwPlayer != null) {
                jwPlayer.play();
            }
            if (jwPlayer != null) {
                updatePlaybackState(jwPlayer, PlaybackStateCompat.STATE_PLAYING);
            }
        } catch (Exception e) {
            Log.w(TAG, "performPlay error: " + e.getMessage());
        }
    }

    private void performPause() {
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
            Log.w(TAG, "performPause error: " + e.getMessage());
        }

        captureAndStoreSeekPosition();
    }

    private void performStop() {
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
            Log.w(TAG, "mediaSessionCallback onStop error: " + ex.getMessage());
        }       

        captureAndStoreSeekPosition();
    }

    private void performSkipToNext() {
        try {
            if (serviceMediaApi != null) {
                serviceMediaApi.onSkipToNext();
            }
        } catch (Exception ex) {
            Log.w(TAG, "mediaSessionCallback onSkipToNext error: " + ex.getMessage());
        }
    }

    private void performSkipToPrevious() {
        try {
            if (serviceMediaApi != null) {
                serviceMediaApi.onSkipToPrevious();
            }
        } catch (Exception ex) {
            Log.w(TAG, "mediaSessionCallback onSkipToPrevious error: " + ex.getMessage());
        }
    }

    /**
     * Handle seek to position for both UI and background players
     */
    private void performSeekTo(long positionMs) {
        boolean shouldRequestFocus = false;

        try {
            // Try to seek in background player first
            Class<?> handlerClass = Class.forName("com.jwplayer.rnjwplayer.JWPlayerNativePlaybackHandler");
            java.lang.reflect.Method getInstanceMethod = handlerClass.getMethod("getInstance", Context.class);
            Object handlerInstance = getInstanceMethod.invoke(null, this.context);
            
            if (handlerInstance != null) {
                // Check if background player exists and seek
                java.lang.reflect.Method seekMethod = handlerClass.getMethod("seekToPosition", long.class);
                seekMethod.invoke(handlerInstance, positionMs);
                Log.d(TAG, "Performed seek in background player to " + positionMs + " ms");
            }
        } catch (Exception e) { }
        
        long position = (long) (positionMs / 1000.0);

        // Always seek UI player too (or as fallback) so its reported position updates promptly
        if (this.jwPlayer != null) {
            try {
                this.jwPlayer.seek(position);
                Log.d(TAG, "Performed seek in UI player to " + position + " m");
            } catch (Exception uiSeekError) {
                Log.e(TAG, "UI player seek failed", uiSeekError);
            }
        }

        if (jwPlayer != null) {
            // Keep state (playing vs paused) consistent after seek
            int playerState = (jwPlayer.getState() == PlayerState.PLAYING)
                    ? PlaybackStateCompat.STATE_PLAYING
                    : PlaybackStateCompat.STATE_PAUSED;

            shouldRequestFocus = (playerState == PlaybackStateCompat.STATE_PLAYING);
            updatePlaybackState(jwPlayer, playerState, position);
        }

        if (shouldRequestFocus) {
            boolean focusGranted = requestAudioFocusForPlayback(context);
            if (!focusGranted) {
                Log.w(TAG, "Audio focus not granted for seek during playback");
            }
        }

        storeSeekPosition(positionMs);
    }

    /**
     * Handle media item selection from Android Auto
     * This handles both MediaBrowser logic (React Native notification) and JWPlayer logic (actual playback)
     */
    private void performMediaItemSelection(String mediaId, Bundle extras) {
        initServiceMediaApi();
        
        // Mark that this is from Android Auto
        isPlayingFromAndroidAuto = true;

        try {
            // First, call MediaBrowserService static method to send to React Native
            try {
                Class<?> mediaBrowserServiceClass = Class.forName("com.mediabrowser.MediaBrowserService");
                java.lang.reflect.Method sendToReactMethod = mediaBrowserServiceClass.getMethod("sendMediaItemToReactNative", String.class);
                sendToReactMethod.invoke(null, mediaId);
            } catch (Exception e) {
                Log.w(TAG, "Could not call MediaBrowserService.sendMediaItemToReactNative: " + e.getMessage());
            }
            
            // Then, handle JWPlayer logic - start actual playback
            Class<?> handlerClass = Class.forName("com.jwplayer.rnjwplayer.JWPlayerNativePlaybackHandler");
            java.lang.reflect.Method getInstanceMethod = handlerClass.getMethod("getInstance", Context.class);
            Object handlerInstance = getInstanceMethod.invoke(null, this.context);
            
            if (handlerInstance != null) {
                // Extract media info from extras
                String title = extras != null ? extras.getString("title", "Unknown Title") : "Unknown Title";
                String subtitle = extras != null ? extras.getString("subtitle", "") : "";
                String icon = extras != null ? extras.getString("icon", "") : "";
                
                pendingSeekMs = extractResumePosition(extras);
                pendingSeekApplied = false;

                externalMediaId = mediaId;

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
                
                // Call handleHeadlessMediaSelection
                java.lang.reflect.Method handleMethod = handlerClass.getMethod("handleHeadlessMediaSelection", 
                    String.class, String.class, String.class, String.class, java.util.Map.class);
                handleMethod.invoke(handlerInstance, mediaId, title, subtitle, icon, extrasMap);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error handling media item selection", e);
        }
    }

    /**
     * Static methods for MediaBrowserService to delegate to active instance
     */
    public static boolean handlePlayFromMediaId(String mediaId, Bundle extras) {
        pendingSeekMs = extractResumePosition(extras);
        pendingSeekApplied = false;
        
        externalMediaId = mediaId;

        if (activeInstance != null) {
            try {
                activeInstance.performMediaItemSelection(mediaId, extras);
                return true;
            } catch (Exception e) {
                Log.e(TAG, "Error in static handlePlayFromMediaId", e);
                return false;
            }
        }
        return false;
    }
    
    public static boolean handlePlay() {
        if (activeInstance != null) {
            try {
                activeInstance.performPlay();
                return true;
            } catch (Exception e) {
                Log.e(TAG, "Error in static handlePlay", e);
                return false;
            }
        }
        return false;
    }
    
    public static boolean handlePause() {
        if (activeInstance != null) {
            try {
                activeInstance.performPause();
                return true;
            } catch (Exception e) {
                Log.e(TAG, "Error in static handlePause", e);
                return false;
            }
        }
        return false;
    }
    
    public static boolean handleStop() {
        if (activeInstance != null) {
            try {
                activeInstance.performStop();
                return true;
            } catch (Exception e) {
                Log.e(TAG, "Error in static handleStop", e);
                return false;
            }
        }
        return false;
    }

    public static boolean handleDestroy() {
        if (activeInstance != null) {
            try {
                activeInstance.softCleanup();
                return true;
            } catch (Exception e) {
                Log.e(TAG, "Error in static handleDestroy", e);
                return false;
            }
        }
        return false;
    }
    
    public static boolean handleSkipToNext() {
        if (activeInstance != null) {
            try {
                activeInstance.performSkipToNext();
                return true;
            } catch (Exception e) {
                Log.e(TAG, "Error in static handleSkipToNext", e);
                return false;
            }
        }
        return false;
    }
    
    public static boolean handleSkipToPrevious() {
        if (activeInstance != null) {
            try {
                activeInstance.performSkipToPrevious();
                return true;
            } catch (Exception e) {
                Log.e(TAG, "Error in static handleSkipToPrevious", e);
                return false;
            }
        }
        return false;
    }
    
    public static boolean handleSeekTo(long position) {
        if (activeInstance != null) {
            try {
                activeInstance.performSeekTo(position);
                return true;
            } catch (Exception e) {
                Log.e(TAG, "Error in static handleSeekTo", e);
                return false;
            }
        }
        return false;
    }
}
