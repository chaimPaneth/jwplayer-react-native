//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package com.jwplayer.rnjwplayer.session;

import android.content.Context;
import android.graphics.Bitmap;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.MediaControllerCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.util.Log;
import android.view.KeyEvent;
import android.content.BroadcastReceiver;
import android.content.Intent;
import android.content.IntentFilter;
import androidx.core.content.ContextCompat;

import com.jwplayer.rnjwplayer.misc.a;
import com.jwplayer.rnjwplayer.misc.b;
import com.jwplayer.rnjwplayer.misc.c;
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
import com.longtailvideo.jwplayer.o.f;
import com.mediabrowser.MediaSessionSingleton;

public class RNJWMediaSessionHelper implements AdvertisingEvents.OnAdCompleteListener, AdvertisingEvents.OnAdErrorListener, AdvertisingEvents.OnAdPlayListener, AdvertisingEvents.OnAdSkippedListener, VideoPlayerEvents.OnBufferListener, VideoPlayerEvents.OnErrorListener, VideoPlayerEvents.OnPauseListener, VideoPlayerEvents.OnPlayListener, VideoPlayerEvents.OnPlaylistCompleteListener, VideoPlayerEvents.OnPlaylistItemListener {
    private JWPlayer c;
    private f d;
    com.jwplayer.rnjwplayer.misc.b a;
    private ServiceMediaApi e;
    private final RNJWNotificationHelper f;
    final Context b;
    private final com.jwplayer.rnjwplayer.misc.a g;

    private BroadcastReceiver mediaButtonFallbackReceiver;

    private static final String TAG = "RNJWMediaSessionHelper";
    
    // Static reference to track the active instance for delegation from MediaBrowserService
    private static RNJWMediaSessionHelper activeInstance = null;

    // MediaSession callback to handle transport controls on Android 13/14+
    private final MediaSessionCompat.Callback mediaSessionCallback = new MediaSessionCompat.Callback() {
        @Override
        public void onPlay() {
            try {
                if (e != null) {
                    e.onPlay();
                } else if (c != null) {
                    c.play();
                }
            } catch (Exception ex) {
                Log.w(TAG, "mediaSessionCallback onPlay error: " + ex.getMessage());
            }
            if (c != null) {
                updatePlaybackState(c, PlaybackStateCompat.STATE_PLAYING);
            }
        }

        @Override
        public void onPause() {
            try {
                if (e != null) {
                    e.onPause();
                } else if (c != null) {
                    c.pause();
                }
            } catch (Exception ex) {
                Log.w(TAG, "mediaSessionCallback onPause error: " + ex.getMessage());
            }
            if (c != null) {
                updatePlaybackState(c, PlaybackStateCompat.STATE_PAUSED);
            }
        }

        @Override
        public void onStop() {
            try {
                if (e != null) {
                    e.onStop();
                } else if (c != null) {
                    c.stop();
                }
            } catch (Exception ex) {
                Log.w(TAG, "mediaSessionCallback onStop error: " + ex.getMessage());
            }
            if (c != null) {
                updatePlaybackState(c, PlaybackStateCompat.STATE_STOPPED);
            }
        }

        @Override
        public void onSeekTo(long pos) {
            performSeekTo(pos);
            if (c != null) {
                // Keep state (playing vs paused) consistent after seek
                int st = (c.getState() == PlayerState.PLAYING)
                        ? PlaybackStateCompat.STATE_PLAYING
                        : PlaybackStateCompat.STATE_PAUSED;
                updatePlaybackState(c, st, pos);
            }
        }

        @Override
        public void onSkipToNext() {
            try {
                if (e != null) {
                    e.onSkipToNext();
                }
            } catch (Exception ex) {
                Log.w(TAG, "mediaSessionCallback onSkipToNext error: " + ex.getMessage());
            }
        }

        @Override
        public void onSkipToPrevious() {
            try {
                if (e != null) {
                    e.onSkipToPrevious();
                }
            } catch (Exception ex) {
                Log.w(TAG, "mediaSessionCallback onSkipToPrevious error: " + ex.getMessage());
            }
        }

        @Override
        public void onPlayFromMediaId(String mediaId, android.os.Bundle extras) {
            try {
                handleMediaItemSelection(mediaId, extras);
            } catch (Exception ex) {
                Log.w(TAG, "mediaSessionCallback onPlayFromMediaId error: " + ex.getMessage());
            }
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
                                    if (a != null && a.a != null) {
                                        PlaybackStateCompat playbackState = a.a.getController().getPlaybackState();
                                        if (playbackState != null) {
                                            int state = playbackState.getState();
                                            isPlaying = (state == PlaybackStateCompat.STATE_PLAYING || 
                                                    state == PlaybackStateCompat.STATE_BUFFERING);
                                        }
                                    }
                                } catch (Exception ex) {
                                    // Fallback to JWPlayer state if MediaSession state unavailable
                                    if (c != null) {
                                        isPlaying = (c.getState() == PlayerState.PLAYING);
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

    public RNJWMediaSessionHelper(Context context, RNJWNotificationHelper notificationHelper, ServiceMediaApi serviceMediaApi) {
        this(context, notificationHelper, serviceMediaApi, new a());
    }

    private RNJWMediaSessionHelper(Context context, RNJWNotificationHelper notificationHelper, ServiceMediaApi serviceMediaApi, a bgaFactory) {
        this.b = context;
        this.f = notificationHelper;
        this.g = bgaFactory;
        
        // Set this as the active instance for delegation
        activeInstance = this;
        
        this.setupServiceMediaApi(serviceMediaApi);
    }

    final void setupServiceMediaApi(ServiceMediaApi serviceMediaApi) {
        this.cleanup();
        if (serviceMediaApi != null) {
            this.c = serviceMediaApi.getPlayer();
            Context var10001 = this.b;
            String var3 = RNJWMediaSessionHelper.class.getSimpleName();
            Context var2 = var10001;
            this.a =  new b(MediaSessionSingleton.getInstance(var2));
//            this.a = new b(new MediaSessionCompat(var2, var3));
            this.e = serviceMediaApi;

            // Attach callback (was previously intentionally omitted)
            try {
                if (this.a != null && this.a.a != null) {
                    this.a.a.setCallback(mediaSessionCallback);
                }
            } catch (Exception cbEx) {
                Log.w(TAG, "Failed to set MediaSession callback: " + cbEx.getMessage());
            }

            setupMediaButtonFallback(var2);
            
            // Check if background player is active and coordinate
            try {
                Class<?> handlerClass = Class.forName("com.jwplayer.rnjwplayer.JWPlayerNativePlaybackHandler");
                java.lang.reflect.Method getInstanceMethod = handlerClass.getMethod("getInstance", Context.class);
                Object handlerInstance = getInstanceMethod.invoke(null, this.b);
                
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
            
            this.c.addListeners(this, new EventType[]{EventType.PLAY, EventType.PAUSE, EventType.BUFFER, EventType.ERROR, EventType.PLAYLIST_ITEM, EventType.PLAYLIST_COMPLETE, EventType.AD_PLAY, EventType.AD_SKIPPED, EventType.AD_COMPLETE, EventType.AD_ERROR});
            JWPlayer var4 = this.c;
            this.updatePlaylistItem(var4.getPlaylistItem());
            this.updatePlayerState(var4.getState());
        }

    }

    private void updatePlaybackState(JWPlayer player, int state) {
        updatePlaybackState(player, state, null);
    }

    private void updatePlaybackState(JWPlayer player, int state, Long overridePositionMs) {
        if (this.a == null || this.a.a == null || player == null) {
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

        if (e != null) {
            try {
                long caps = this.e.getNotificationCapabilities();
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
            this.a.a.setPlaybackState(builder.build());
            this.a.a.setActive(true);
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
                
                KeyEvent ke = intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT);
                if (ke == null || ke.getAction() != KeyEvent.ACTION_DOWN) return;
                int kc = ke.getKeyCode();

                // Fallback handling if session callback not invoked
                try {
                    MediaSessionCompat session = (a != null) ? a.a : null;
                    MediaControllerCompat.TransportControls tc = (session != null)
                            ? session.getController().getTransportControls() : null;

                    boolean handled = false;
                    if (kc == KeyEvent.KEYCODE_MEDIA_PLAY
                            || kc == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
                            || kc == KeyEvent.KEYCODE_MEDIA_PAUSE) {

                        PlayerState ps = (c != null) ? c.getState() : null;

                        if (kc == KeyEvent.KEYCODE_MEDIA_PLAY ||
                                (kc == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE && ps != PlayerState.PLAYING)) {
                            if (tc != null) tc.play(); else if (c != null) c.play();
                            handled = true;
                        } else if (kc == KeyEvent.KEYCODE_MEDIA_PAUSE ||
                                (kc == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE && ps == PlayerState.PLAYING)) {
                            if (tc != null) tc.pause(); else if (c != null) c.pause();
                            handled = true;
                        }
                    }
                    if (handled) {
                        int st = (c != null && c.getState() == PlayerState.PLAYING)
                                ? PlaybackStateCompat.STATE_PLAYING : PlaybackStateCompat.STATE_PAUSED;
                        updatePlaybackState(c, st);
                    }
                } catch (Exception ex) {
                    Log.w(TAG, "Fallback media button handling error " + ex.getMessage());
                }
            }
        };
        try {
            IntentFilter f = new IntentFilter(Intent.ACTION_MEDIA_BUTTON);
            ContextCompat.registerReceiver(
                    ctx,
                    mediaButtonFallbackReceiver,
                    f,
                    ContextCompat.RECEIVER_EXPORTED);
        } catch (Exception ex) {
            Log.w(TAG, "setupMediaButtonFallback: register failed " + ex.getMessage());
        }
    }

    final void cleanup() {
        RNJWNotificationHelper var1;
        if (this.a != null) {
             // Unregister fallback receiver if present
            if (mediaButtonFallbackReceiver != null) {
                try {
                    b.unregisterReceiver(mediaButtonFallbackReceiver);
                } catch (Exception unregEx) {
                    Log.w(TAG, "Failed to unregister media button fallback receiver: " + unregEx.getMessage());
                }
                mediaButtonFallbackReceiver = null;
            }

            this.a.a.setActive(false);
            this.e = null;
            
            // Clear active instance if this is the active one
            if (activeInstance == this) {
                activeInstance = null;
            }
            var1 = null;

            try {
                this.a.a.setCallback(null);
            } catch (Exception ignore) {}
            this.a.a.release();
            this.a = null;
        }

        if (this.c != null) {
            this.c.removeListeners(this, new EventType[]{EventType.PLAY, EventType.PAUSE, EventType.BUFFER, EventType.ERROR, EventType.PLAYLIST_ITEM, EventType.PLAYLIST_COMPLETE, EventType.AD_PLAY, EventType.AD_SKIPPED, EventType.AD_COMPLETE, EventType.AD_ERROR});
            (var1 = this.f).a.cancel(var1.b);
            if (this.d != null) {
                this.d.cancel(true);
                this.d = null;
            }

            this.c = null;
        }

    }

    private void updatePlayerState(PlayerState playerState) {
        com.jwplayer.rnjwplayer.misc.c var5 = this.a.a();
        c.a var2 = new c.a(var5);
        long var3 = this.e.getNotificationCapabilities();
        var2.a.setActions(var3 | PlaybackStateCompat.ACTION_SEEK_TO);
        byte var8 = 0;
        switch (playerState) {
            case PLAYING:
                var8 = PlaybackStateCompat.STATE_PLAYING;
                break;
            case PAUSED:
                var8 = PlaybackStateCompat.STATE_PAUSED;
                break;
            case BUFFERING:
                var8 = PlaybackStateCompat.STATE_BUFFERING;
                break;
            case ERROR:
                var8 = PlaybackStateCompat.STATE_ERROR;
                break;
            case IDLE:
            default:
                var8 = PlaybackStateCompat.STATE_STOPPED;
        }
        long var9 = (long)this.c.getPosition() * 1000; // (long)(this.c.getPosition() * 1000.0);
        var2.a.setState(var8, var9, 1.0F);
        b var10000 = this.a;
        c var10 = new c(var2.a.build());
        var10000.a.setPlaybackState(var10.a);
        boolean var7 = playerState != PlayerState.ERROR && playerState != PlayerState.IDLE;
        this.a.a.setActive(var7);
        if (var7) {
            this.f.a(this.b, this.a, this.e);
        } else {
            RNJWNotificationHelper var11;
            (var11 = this.f).a.cancel(var11.b);
        }
    }

    private void updatePlaylistItem(PlaylistItem playlistItem) {
        if (playlistItem == null || this.c == null) {
            return;
        }

        PlaylistItem currentItem = this.c.getPlaylistItem();

        long duration = 0L;
        if (currentItem != null && currentItem.getDuration() != null && currentItem.getDuration() > 0) {
            duration = (long) (currentItem.getDuration() * 1000);
        }

        MediaMetadataCompat.Builder builder = new MediaMetadataCompat.Builder();

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

        builder.putLong("android.media.metadata.DURATION", duration);

        if (this.a != null && this.a.a != null) {
            this.a.a.setMetadata(builder.build());
        }

        if (this.d != null) {
            this.d.cancel(true);
            this.d = null;
        }

        if (playlistItem.getImage() != null && !playlistItem.getImage().isEmpty()) {
            f.a var3 = this::updateAlbumArt;
            this.d = new f(var3);
            this.d.execute(new String[]{playlistItem.getImage()});
        }
    }

    public void onPlaylistItem(PlaylistItemEvent playlistItemEvent) {
        this.updatePlaylistItem(playlistItemEvent.getPlaylistItem());
    }

    public void onError(ErrorEvent errorEvent) {
        this.updatePlayerState(PlayerState.ERROR);
        this.a.a.release();
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
        updatePlaybackState(c, PlaybackStateCompat.STATE_BUFFERING);
    }

    public void onPause(PauseEvent pauseEvent) {
        this.updatePlayerState(PlayerState.PAUSED);
        updatePlaybackState(c, PlaybackStateCompat.STATE_PAUSED);
    }

    public void onPlay(PlayEvent playEvent) {
        this.updatePlayerState(PlayerState.PLAYING);
        updatePlaybackState(c, PlaybackStateCompat.STATE_PLAYING);
    }

    public void onPlaylistComplete(PlaylistCompleteEvent playlistCompleteEvent) {
        if (this.a == null || this.a.a == null) return;

        try {
            // Build a PAUSED playback state
            // This keeps the progress bar interactive and shows correct button state
            com.jwplayer.rnjwplayer.misc.c capsWrapper = this.a.a();
            c.a stateBuilder = new c.a(capsWrapper);

            // Include seek actions so progress bar remains interactive
            long actions = PlaybackStateCompat.ACTION_PLAY
                    | PlaybackStateCompat.ACTION_PLAY_FROM_MEDIA_ID
                    | PlaybackStateCompat.ACTION_SEEK_TO;

            if (this.e != null) {
                try {
                    long caps = this.e.getNotificationCapabilities();
                    actions |= caps;
                } catch (Exception ex) {
                    Log.w(TAG, "Could not get service capabilities: " + ex.getMessage());
                }
            }

            stateBuilder.a.setActions(actions);

            // Get the total duration for positioning at the end
            long totalDurationMs = 0;
            long positionMs = 0;
            
            try {
                if (this.c != null && this.c.getPlaylistItem() != null && this.c.getPlaylistItem().getDuration() != null) {
                    totalDurationMs = (long)(this.c.getPlaylistItem().getDuration() * 1000);
                    positionMs = totalDurationMs; // Position at the end
                } else {
                    // Fallback: try to get current position
                    positionMs = this.c != null ? (long)(this.c.getPosition() * 1000) : 0L;
                }
            } catch (Exception ex) {
                Log.w(TAG, "Could not get duration/position for completion: " + ex.getMessage());
                positionMs = 0;
            }

            // Set to PAUSED state with normal playback rate so progress bar stays interactive
            // Position at the end, but with rate 1.0f so seeking works
            stateBuilder.a.setState(PlaybackStateCompat.STATE_PAUSED, positionMs, 1.0f);

            this.a.a.setPlaybackState(new c(stateBuilder.a.build()).a);

            // Keep session active so UI remains available
            this.a.a.setActive(true);

            // Explicitly show notification (like the a(PlayerState) method does)
            this.f.a(this.b, this.a, this.e);

            try {
                updatePlaybackState(c, PlaybackStateCompat.STATE_PAUSED);
            } catch (Exception ignore) {}
        } catch (Exception ex) {
            Log.w(TAG, "Failed to set completion state", ex);
        }
    }

    void updateAlbumArt(Bitmap bitmap) {
        if (this.a != null) {
            MediaMetadataCompat var2;
            MediaMetadataCompat.Builder var4;
            (var4 = (var2 = this.a.a.getController().getMetadata()) == null ? new MediaMetadataCompat.Builder() : new MediaMetadataCompat.Builder(var2)).putBitmap("android.media.metadata.ART", bitmap);
            b var10000 = this.a;
            MediaMetadataCompat var3 = var4.build();
            var10000.a.setMetadata(var3);
        }
    }
    
    /**
     * Handle media item selection from Android Auto
     * This handles both MediaBrowser logic (React Native notification) and JWPlayer logic (actual playback)
     */
    private void handleMediaItemSelection(String mediaId, android.os.Bundle extras) {
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
            Object handlerInstance = getInstanceMethod.invoke(null, this.b);
            
            if (handlerInstance != null) {
                // Extract media info from extras
                String title = extras != null ? extras.getString("title", "Unknown Title") : "Unknown Title";
                String subtitle = extras != null ? extras.getString("subtitle", "") : "";
                String icon = extras != null ? extras.getString("icon", "") : "";
                
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
     * Handle seek to position for both UI and background players
     */
    private void performSeekTo(long positionMs) {
        try {
            // Try to seek in background player first
            Class<?> handlerClass = Class.forName("com.jwplayer.rnjwplayer.JWPlayerNativePlaybackHandler");
            java.lang.reflect.Method getInstanceMethod = handlerClass.getMethod("getInstance", Context.class);
            Object handlerInstance = getInstanceMethod.invoke(null, this.b);
            
            if (handlerInstance != null) {
                // Check if background player exists and seek
                java.lang.reflect.Method seekMethod = handlerClass.getMethod("seekToPosition", long.class);
                seekMethod.invoke(handlerInstance, positionMs);
            }
        } catch (Exception e) { }       
        
        // Always seek UI player too (or as fallback) so its reported position updates promptly
        if (this.c != null) {
            try {
                this.c.seek(positionMs / 1000.0);
            } catch (Exception uiSeekError) {
                Log.e(TAG, "UI player seek failed", uiSeekError);
            }
        }
    }
    
    /**
     * Static methods for MediaBrowserService to delegate to active instance
     */
    public static boolean handlePlayFromMediaId(String mediaId, android.os.Bundle extras) {
        if (activeInstance != null) {
            try {
                activeInstance.handleMediaItemSelection(mediaId, extras);
                return true;
            } catch (Exception e) {
                Log.e(TAG, "Error in static handlePlayFromMediaId", e);
                return false;
            }
        }
        return false;
    }
    
    public static boolean handlePlay() {
        if (activeInstance != null && activeInstance.e != null) {
            try {
                activeInstance.e.onPlay();
                return true;
            } catch (Exception e) {
                Log.e(TAG, "Error in static handlePlay", e);
                return false;
            }
        }
        return false;
    }
    
    public static boolean handlePause() {
        if (activeInstance != null && activeInstance.e != null) {
            try {
                activeInstance.e.onPause();
                return true;
            } catch (Exception e) {
                Log.e(TAG, "Error in static handlePause", e);
                return false;
            }
        }
        return false;
    }
    
    public static boolean handleStop() {
        if (activeInstance != null && activeInstance.e != null) {
            try {
                activeInstance.e.onStop();
                return true;
            } catch (Exception e) {
                Log.e(TAG, "Error in static handleStop", e);
                return false;
            }
        }
        return false;
    }
    
    public static boolean handleSkipToNext() {
        if (activeInstance != null && activeInstance.e != null) {
            try {
                activeInstance.e.onSkipToNext();
                return true;
            } catch (Exception e) {
                Log.e(TAG, "Error in static handleSkipToNext", e);
                return false;
            }
        }
        return false;
    }
    
    public static boolean handleSkipToPrevious() {
        if (activeInstance != null && activeInstance.e != null) {
            try {
                activeInstance.e.onSkipToPrevious();
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
                if (activeInstance.e != null) {
                    try {
                        activeInstance.e.onSeekTo(position);
                    } catch (Exception se) {
                        Log.w(TAG, "Service seek delegation failed: " + se.getMessage());
                    }
                }

                // Update playback state immediately with requested position
                if (activeInstance.c != null) {
                    int st = (activeInstance.c.getState() == PlayerState.PLAYING)
                            ? PlaybackStateCompat.STATE_PLAYING
                            : PlaybackStateCompat.STATE_PAUSED;
                    activeInstance.updatePlaybackState(activeInstance.c, st, position);
                }
                return true;
            } catch (Exception e) {
                Log.e(TAG, "Error in static handleSeekTo", e);
                return false;
            }
        }
        return false;
    }
}
