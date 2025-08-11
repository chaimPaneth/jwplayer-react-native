//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package com.jwplayer.rnjwplayer.session;

import android.content.Context;
import android.graphics.Bitmap;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;

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
    
    // Static reference to track the active instance for delegation from MediaBrowserService
    private static RNJWMediaSessionHelper activeInstance = null;

    public RNJWMediaSessionHelper(Context context, RNJWNotificationHelper notificationHelper, ServiceMediaApi serviceMediaApi) {
        this(context, notificationHelper, serviceMediaApi, new a());
    }

    private RNJWMediaSessionHelper(Context context, RNJWNotificationHelper notificationHelper, ServiceMediaApi serviceMediaApi, a bgaFactory) {
        this.b = context;
        this.f = notificationHelper;
        this.g = bgaFactory;
        
        // Set this as the active instance for delegation
        activeInstance = this;
        
        this.a(serviceMediaApi);
    }

    final void a(ServiceMediaApi var1) {
        this.a();
        if (var1 != null) {
            this.c = var1.getPlayer();
            Context var10001 = this.b;
            String var3 = RNJWMediaSessionHelper.class.getSimpleName();
            Context var2 = var10001;
            this.a =  new b(MediaSessionSingleton.getInstance(var2));
//            this.a = new b(new MediaSessionCompat(var2, var3));
            this.e = var1;
            
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
                        android.util.Log.d("RNJWMediaSessionHelper", "Background player detected, coordinating session...");
                        
                        // Get background player info
                        java.lang.reflect.Method getInfoMethod = handlerClass.getMethod("getCurrentBackgroundPlayerInfo");
                        Object backgroundInfo = getInfoMethod.invoke(handlerInstance);
                        
                        if (backgroundInfo instanceof java.util.Map) {
                            java.util.Map<String, Object> bgInfo = (java.util.Map<String, Object>) backgroundInfo;
                            android.util.Log.d("RNJWMediaSessionHelper", "Background player info: " + bgInfo.toString());
                            
                            // Transfer background player to UI if they're playing the same content
                            java.lang.reflect.Method transferMethod = handlerClass.getMethod("transferToUIPlayer");
                            Object transferResult = transferMethod.invoke(handlerInstance);
                            
                            android.util.Log.d("RNJWMediaSessionHelper", "Transferred background player to UI");
                        }
                    }
                }
            } catch (Exception e) {
                android.util.Log.w("RNJWMediaSessionHelper", "Could not coordinate with background player: " + e.getMessage());
            }
            
            // DON'T set callback here - MediaBrowserService already has the callback set
            // and it will delegate to us when needed via static methods or fallback to direct handling
            android.util.Log.d("RNJWMediaSessionHelper", "Using existing MediaSession from MediaBrowserService with existing callback");
            
            this.c.addListeners(this, new EventType[]{EventType.PLAY, EventType.PAUSE, EventType.BUFFER, EventType.ERROR, EventType.PLAYLIST_ITEM, EventType.PLAYLIST_COMPLETE, EventType.AD_PLAY, EventType.AD_SKIPPED, EventType.AD_COMPLETE, EventType.AD_ERROR});
            JWPlayer var4 = this.c;
            this.a(var4.getPlaylistItem());
            this.a(var4.getState());
        }

    }

    final void a() {
        RNJWNotificationHelper var1;
        if (this.a != null) {
            this.a.a.setActive(false);
            this.e = null;
            
            // Clear active instance if this is the active one
            if (activeInstance == this) {
                activeInstance = null;
            }
            var1 = null;
            this.a.a.setCallback(var1);
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

    private void a(PlayerState var1) {
        com.jwplayer.rnjwplayer.misc.c var5 = this.a.a();
        c.a var2 = new c.a(var5);
        long var3 = this.e.getNotificationCapabilities();
        var2.a.setActions(var3);
        byte var8 = 0;
        switch (var1) {
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
        boolean var7 = var1 != PlayerState.ERROR && var1 != PlayerState.IDLE;
        this.a.a.setActive(var7);
        if (var7) {
            this.f.a(this.b, this.a, this.e);
        } else {
            RNJWNotificationHelper var11;
            (var11 = this.f).a.cancel(var11.b);
        }
    }

    private void a(PlaylistItem var1) {
        MediaMetadataCompat var2 = null;
        
        // Check if there's already metadata from background player that should be preserved
        MediaMetadataCompat existingMetadata = null;
        long existingDuration = 0;
        String existingMediaId = null;
        
        if (this.a != null && this.a.a != null && this.a.a.getController() != null) {
            existingMetadata = this.a.a.getController().getMetadata();
            if (existingMetadata != null) {
                existingDuration = existingMetadata.getLong(MediaMetadataCompat.METADATA_KEY_DURATION);
                existingMediaId = existingMetadata.getString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID);
            }
        }
        
        long duration = (this.c.getPlaylistItem() != null && this.c.getPlaylistItem().getDuration() != null && this.c.getPlaylistItem().getDuration() > 0) ? (long) (this.c.getPlaylistItem().getDuration() * 1000) : 0L;
        
        // If we have existing metadata with duration and it's for the same media item, preserve the duration
        if (existingDuration > 0 && existingMediaId != null && var1.getMediaId() != null && existingMediaId.equals(var1.getMediaId())) {
            duration = existingDuration;
            android.util.Log.d("RNJWMediaSessionHelper", "Preserving duration from background player: " + duration + "ms for mediaId: " + existingMediaId);
        }
        
        var2 = (null == null ? new MediaMetadataCompat.Builder() : new MediaMetadataCompat.Builder(var2)).putString("android.media.metadata.DISPLAY_TITLE", var1.getTitle()).putString("android.media.metadata.DISPLAY_SUBTITLE", var1.getDescription()).putString("android.media.metadata.MEDIA_ID", var1.getMediaId()).putString("android.media.metadata.ARTIST", this.c.getPlaylistItem().getDescription()).putString("android.media.metadata.TITLE", this.c.getPlaylistItem().getTitle()).putLong("android.media.metadata.DURATION", duration).build();
        this.a.a.setMetadata(var2);
        if (this.d != null) {
            this.d.cancel(true);
            this.d = null;
        }

        f.a var3 = this::a;
        this.d = new f(var3);
        this.d.execute(new String[]{var1.getImage()});
    }

    public void onPlaylistItem(PlaylistItemEvent playlistItemEvent) {
        this.a(playlistItemEvent.getPlaylistItem());
    }

    public void onError(ErrorEvent errorEvent) {
        this.a(PlayerState.ERROR);
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
        this.a(PlayerState.BUFFERING);
    }

    public void onPause(PauseEvent pauseEvent) {
        this.a(PlayerState.PAUSED);
    }

    public void onPlay(PlayEvent playEvent) {
        this.a(PlayerState.PLAYING);
    }

    public void onPlaylistComplete(PlaylistCompleteEvent playlistCompleteEvent) {
        this.a.a.setActive(false);
        this.a.a.release();
    }

    void a(Bitmap var1) {
        if (this.a != null) {
            MediaMetadataCompat var2;
            MediaMetadataCompat.Builder var4;
            (var4 = (var2 = this.a.a.getController().getMetadata()) == null ? new MediaMetadataCompat.Builder() : new MediaMetadataCompat.Builder(var2)).putBitmap("android.media.metadata.ART", var1);
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
                android.util.Log.d("RNJWMediaSessionHelper", "Called MediaBrowserService.sendMediaItemToReactNative for: " + mediaId);
            } catch (Exception e) {
                android.util.Log.w("RNJWMediaSessionHelper", "Could not call MediaBrowserService.sendMediaItemToReactNative: " + e.getMessage());
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
                
                android.util.Log.d("RNJWMediaSessionHelper", "Called JWPlayerNativePlaybackHandler for: " + mediaId);
            }
        } catch (Exception e) {
            android.util.Log.e("RNJWMediaSessionHelper", "Error handling media item selection", e);
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
                android.util.Log.d("RNJWMediaSessionHelper", "Seeked background player to: " + positionMs + "ms");
            }
        } catch (Exception e) {
            android.util.Log.w("RNJWMediaSessionHelper", "Could not seek background player: " + e.getMessage());
            
            // Fallback: try to seek UI player if available
            if (this.c != null) {
                try {
                    double positionSeconds = positionMs / 1000.0;
                    this.c.seek(positionSeconds);
                    android.util.Log.d("RNJWMediaSessionHelper", "Seeked UI player to: " + positionSeconds + "s");
                } catch (Exception uiSeekError) {
                    android.util.Log.e("RNJWMediaSessionHelper", "Could not seek UI player", uiSeekError);
                }
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
                android.util.Log.e("RNJWMediaSessionHelper", "Error in static handlePlayFromMediaId", e);
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
                android.util.Log.e("RNJWMediaSessionHelper", "Error in static handlePlay", e);
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
                android.util.Log.e("RNJWMediaSessionHelper", "Error in static handlePause", e);
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
                android.util.Log.e("RNJWMediaSessionHelper", "Error in static handleStop", e);
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
                android.util.Log.e("RNJWMediaSessionHelper", "Error in static handleSkipToNext", e);
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
                android.util.Log.e("RNJWMediaSessionHelper", "Error in static handleSkipToPrevious", e);
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
                    activeInstance.e.onSeekTo(position);
                }
                return true;
            } catch (Exception e) {
                android.util.Log.e("RNJWMediaSessionHelper", "Error in static handleSeekTo", e);
                return false;
            }
        }
        return false;
    }
}
