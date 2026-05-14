package com.jwplayer.rnjwplayer;


import android.app.Activity;
import android.app.ActivityManager;
import android.app.NotificationManager;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.View.MeasureSpec;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.PictureInPictureModeChangedInfo;
import androidx.core.util.Consumer;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleEventObserver;
import androidx.lifecycle.LifecycleObserver;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.LifecycleRegistry;

import com.facebook.react.ReactActivity;
import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.LifecycleEventListener;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReadableArray;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.ReadableType;
import com.facebook.react.bridge.WritableArray;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.common.MapBuilder;
import com.facebook.react.uimanager.ThemedReactContext;
import com.facebook.react.uimanager.events.RCTEventEmitter;
import com.google.common.collect.ImmutableMap;
import com.google.gson.Gson;
import com.jwplayer.pub.api.JWPlayer;
import com.jwplayer.pub.api.JsonHelper;
import com.jwplayer.pub.api.PlayerState;
import com.jwplayer.pub.api.UiGroup;
import com.jwplayer.pub.api.background.MediaService;
import com.jwplayer.pub.api.background.MediaServiceController;
import com.jwplayer.pub.api.background.ServiceMediaApi;
import com.jwplayer.pub.api.configuration.PlayerConfig;
import com.jwplayer.pub.api.configuration.UiConfig;
import com.jwplayer.pub.api.configuration.ads.AdvertisingConfig;
import com.jwplayer.pub.api.events.AdBreakEndEvent;
import com.jwplayer.pub.api.events.AdBreakIgnoredEvent;
import com.jwplayer.pub.api.events.AdBreakStartEvent;
import com.jwplayer.pub.api.events.AdClickEvent;
import com.jwplayer.pub.api.events.AdCompanionsEvent;
import com.jwplayer.pub.api.events.AdCompleteEvent;
import com.jwplayer.pub.api.events.AdErrorEvent;
import com.jwplayer.pub.api.events.AdImpressionEvent;
import com.jwplayer.pub.api.events.AdLoadedEvent;
import com.jwplayer.pub.api.events.AdLoadedXmlEvent;
import com.jwplayer.pub.api.events.AdMetaEvent;
import com.jwplayer.pub.api.events.AdPauseEvent;
import com.jwplayer.pub.api.events.AdPlayEvent;
import com.jwplayer.pub.api.events.AdRequestEvent;
import com.jwplayer.pub.api.events.AdScheduleEvent;
import com.jwplayer.pub.api.events.AdSkippedEvent;
import com.jwplayer.pub.api.events.AdStartedEvent;
import com.jwplayer.pub.api.events.AdTimeEvent;
import com.jwplayer.pub.api.events.AdViewableImpressionEvent;
import com.jwplayer.pub.api.events.AdWarningEvent;
import com.jwplayer.pub.api.events.AudioTrackChangedEvent;
import com.jwplayer.pub.api.events.AudioTracksEvent;
import com.jwplayer.pub.api.events.BeforeCompleteEvent;
import com.jwplayer.pub.api.events.BeforePlayEvent;
import com.jwplayer.pub.api.events.BufferEvent;
import com.jwplayer.pub.api.events.CaptionsChangedEvent;
import com.jwplayer.pub.api.events.CaptionsListEvent;
import com.jwplayer.pub.api.events.CastEvent;
import com.jwplayer.pub.api.events.CompleteEvent;
import com.jwplayer.pub.api.events.ControlBarVisibilityEvent;
import com.jwplayer.pub.api.events.ControlsEvent;
import com.jwplayer.pub.api.events.DisplayClickEvent;
import com.jwplayer.pub.api.events.ErrorEvent;
import com.jwplayer.pub.api.events.EventType;
import com.jwplayer.pub.api.events.FirstFrameEvent;
import com.jwplayer.pub.api.events.FullscreenEvent;
import com.jwplayer.pub.api.events.IdleEvent;
import com.jwplayer.pub.api.events.MetaEvent;
import com.jwplayer.pub.api.events.PauseEvent;
import com.jwplayer.pub.api.events.PipCloseEvent;
import com.jwplayer.pub.api.events.PipOpenEvent;
import com.jwplayer.pub.api.events.PlayEvent;
import com.jwplayer.pub.api.events.PlaybackRateChangedEvent;
import com.jwplayer.pub.api.events.PlaylistCompleteEvent;
import com.jwplayer.pub.api.events.PlaylistEvent;
import com.jwplayer.pub.api.events.PlaylistItemEvent;
import com.jwplayer.pub.api.events.ReadyEvent;
import com.jwplayer.pub.api.events.SeekEvent;
import com.jwplayer.pub.api.events.SeekedEvent;
import com.jwplayer.pub.api.events.SetupErrorEvent;
import com.jwplayer.pub.api.events.TimeEvent;
import com.jwplayer.pub.api.events.listeners.AdvertisingEvents;
import com.jwplayer.pub.api.events.listeners.CastingEvents;
import com.jwplayer.pub.api.events.listeners.PipPluginEvents;
import com.jwplayer.pub.api.events.listeners.VideoPlayerEvents;
import com.jwplayer.pub.api.fullscreen.ExtensibleFullscreenHandler;
import com.jwplayer.pub.api.fullscreen.FullscreenDialog;
import com.jwplayer.pub.api.fullscreen.FullscreenHandler;
import com.jwplayer.pub.api.fullscreen.delegates.DeviceOrientationDelegate;
import com.jwplayer.pub.api.fullscreen.delegates.DialogLayoutDelegate;
import com.jwplayer.pub.api.fullscreen.delegates.SystemUiDelegate;
import com.jwplayer.pub.api.license.LicenseUtil;
import com.jwplayer.pub.api.media.captions.Caption;
import com.jwplayer.pub.api.media.playlists.PlaylistItem;
import com.jwplayer.rnjwplayer.session.RNJWMediaServiceController;
import com.jwplayer.rnjwplayer.utils.JWLog;
import com.jwplayer.ui.views.CueMarkerSeekbar;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class RNJWPlayerView extends RelativeLayout implements
        VideoPlayerEvents.OnFullscreenListener,
        VideoPlayerEvents.OnReadyListener,
        VideoPlayerEvents.OnPlayListener,
        VideoPlayerEvents.OnPauseListener,
        VideoPlayerEvents.OnCompleteListener,
        VideoPlayerEvents.OnIdleListener,
        VideoPlayerEvents.OnErrorListener,
        VideoPlayerEvents.OnSetupErrorListener,
        VideoPlayerEvents.OnBufferListener,
        VideoPlayerEvents.OnTimeListener,
        VideoPlayerEvents.OnPlaylistListener,
        VideoPlayerEvents.OnPlaylistItemListener,
        VideoPlayerEvents.OnPlaylistCompleteListener,
        VideoPlayerEvents.OnAudioTracksListener,
        VideoPlayerEvents.OnAudioTrackChangedListener,
        VideoPlayerEvents.OnControlsListener,
        VideoPlayerEvents.OnControlBarVisibilityListener,
        VideoPlayerEvents.OnDisplayClickListener,
        VideoPlayerEvents.OnFirstFrameListener,
        VideoPlayerEvents.OnSeekListener,
        VideoPlayerEvents.OnSeekedListener,
        VideoPlayerEvents.OnPlaybackRateChangedListener,
        VideoPlayerEvents.OnCaptionsListListener,
        VideoPlayerEvents.OnCaptionsChangedListener,
        VideoPlayerEvents.OnMetaListener,
        VideoPlayerEvents.PlaylistItemCallbackListener,

        CastingEvents.OnCastListener,

        PipPluginEvents.OnPipCloseListener,
        PipPluginEvents.OnPipOpenListener,

        AdvertisingEvents.OnBeforePlayListener,
        AdvertisingEvents.OnBeforeCompleteListener,
        AdvertisingEvents.OnAdPauseListener,
        AdvertisingEvents.OnAdPlayListener,
        AdvertisingEvents.OnAdRequestListener,
        AdvertisingEvents.OnAdScheduleListener,
        AdvertisingEvents.OnAdStartedListener,
        AdvertisingEvents.OnAdBreakStartListener,
        AdvertisingEvents.OnAdBreakEndListener,
        AdvertisingEvents.OnAdClickListener,
        AdvertisingEvents.OnAdCompleteListener,
        AdvertisingEvents.OnAdCompanionsListener,
        AdvertisingEvents.OnAdErrorListener,
        AdvertisingEvents.OnAdImpressionListener,
        AdvertisingEvents.OnAdMetaListener,
        AdvertisingEvents.OnAdSkippedListener,
        AdvertisingEvents.OnAdTimeListener,
        AdvertisingEvents.OnAdViewableImpressionListener,
        AdvertisingEvents.OnAdBreakIgnoredListener,
        AdvertisingEvents.OnAdWarningListener,
        AdvertisingEvents.OnAdLoadedListener,
        AdvertisingEvents.OnAdLoadedXmlListener,

        AudioManager.OnAudioFocusChangeListener,

        LifecycleEventListener, LifecycleOwner {
    public RNJWPlayer mPlayerView = null;
    public JWPlayer mPlayer = null;

    private ViewGroup mRootView;

    // Props
    ReadableMap mConfig = null;
    ReadableArray mPlaylistProp = null;
    ReadableMap mColors = null;

    Boolean backgroundAudioEnabled = false;

    Boolean landscapeOnFullScreen = false;
    Boolean fullScreenOnLandscape = false;
    Boolean portraitOnExitFullScreen = false;
    Boolean exitFullScreenOnPortrait = false;
    Boolean playerInModal = false;

    Number currentPlayingIndex;

    private static final String TAG = "RNJWPlayerView";

    static ReactActivity mActivity;

    Window mWindow;

    public static AudioManager audioManager;

    final Object focusLock = new Object();

    AudioFocusRequest focusRequest;

    boolean hasAudioFocus = false;
    boolean playbackDelayed = false;
    boolean playbackNowAuthorized = false;
    boolean userPaused = false;
    boolean wasInterrupted = false;

    private static int sessionDepth = 0;
    boolean isInBackground = false;

    private final ReactApplicationContext mAppContext;

    private ThemedReactContext mThemedReactContext;

    private RNJWMediaServiceController mMediaServiceController;
    private Consumer<PictureInPictureModeChangedInfo> mPipListener = null;
    private Boolean mLastHandledPipState = null;
    private OnBackPressedCallback mPipBackCallback = null;

    // Add completion handler field
    PlaylistItemDecision itemUpdatePromise = null;

    private void doBindService() {
        if (mMediaServiceController != null) {
            if (!isBackgroundAudioServiceRunning()) {
                // This may not be your expected behavior, but is necessary to avoid crashing
                // Do not use multiple player instances with background audio enabled

                // don't rebind me if the service is already active with a player.
                mMediaServiceController.bindService();
            }
        }
    }

    private void doUnbindService() {
        if (mMediaServiceController != null) {
            mMediaServiceController.unbindService();
            mMediaServiceController = null;
        }
    }

    private static boolean contextHasBug(Context context) {
        return context == null ||
                context.getResources() == null ||
                context.getResources().getConfiguration() == null;
    }

    private static Context getNonBuggyContext(ThemedReactContext reactContext,
                                              ReactApplicationContext appContext) {
        Context superContext = reactContext;
        if (!contextHasBug(appContext.getCurrentActivity())) {
            superContext = appContext.getCurrentActivity();
        } else if (contextHasBug(superContext)) {
            // we have the bug! let's try to find a better context to use
            if (!contextHasBug(reactContext.getCurrentActivity())) {
                superContext = reactContext.getCurrentActivity();
            } else if (!contextHasBug(reactContext.getApplicationContext())) {
                superContext = reactContext.getApplicationContext();
            } else {
                // ¯\_(ツ)_/¯
            }
        }
        return superContext;
    }

    private boolean isBackgroundAudioServiceRunning() {
        ActivityManager manager = (ActivityManager) mAppContext.getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (MediaService.class.getName().equals(service.service.getClassName())) {
                JWLog.w(TAG, "MediaService is already running with another player loaded. To avoid crashing, this player, "
                        + mPlayerView.getTag() + "  will not be loaded into the background service.");
                return true;
            }
        }
        return false;
    }

    public RNJWPlayerView(ThemedReactContext reactContext, ReactApplicationContext appContext) {
        super(getNonBuggyContext(reactContext, appContext));
        mAppContext = appContext;

        registry.setCurrentState(Lifecycle.State.CREATED);
        mThemedReactContext = reactContext;

        mActivity = (ReactActivity) getActivity();
        if (mActivity != null) {
            mWindow = mActivity.getWindow();
        }

        if (mActivity != null) {
            mActivity.getLifecycle().addObserver(lifecycleObserver);
        }

        mRootView = mActivity.findViewById(android.R.id.content);

        getReactContext().addLifecycleEventListener(this);

        // Constructor entry log
        JWLog.d(TAG, "RNJWPlayerView() constructed. activity=" + JWLog.id(mActivity));
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
        
        // Standard React Native layout handling
        // Since we're no longer constantly swapping views, this is simpler
        if (mPlayerView != null) {
            mPlayerView.layout(0, 0, r - l, b - t);
        }
    }

    private LifecycleObserver lifecycleObserver = new LifecycleEventObserver() {
        @Override
        public void onStateChanged(@NonNull LifecycleOwner source, @NonNull Lifecycle.Event event) {
            if (event.getTargetState() == Lifecycle.State.DESTROYED) {
                return; // no op: handled elsewhere
            }
            registry.setCurrentState(event.getTargetState());
            JWLog.d(TAG, "lifecycle.onStateChanged targetState=" + event.getTargetState());
        }
    };

    public ReactApplicationContext getAppContext() {
        return mAppContext;
    }

    public ThemedReactContext getReactContext() {
        return mThemedReactContext;
    }

    public Activity getActivity() {
        if (!contextHasBug(mAppContext.getCurrentActivity())) {
            return mAppContext.getCurrentActivity();
        } else if (contextHasBug(mThemedReactContext)) {
            if (!contextHasBug(mThemedReactContext.getCurrentActivity())) {
                return mThemedReactContext.getCurrentActivity();
            } else if (!contextHasBug(mThemedReactContext.getApplicationContext())) {
                return (Activity) mThemedReactContext.getApplicationContext();
            }
        }

        return mThemedReactContext.getReactApplicationContext().getCurrentActivity();
    }

    // The registry for lifecycle events. Required by player object. Main use case if for garbage collection / teardown
    private final LifecycleRegistry registry = new LifecycleRegistry(this);

    @NonNull
    @Override
    public Lifecycle getLifecycle() {
        return registry;
    }

    public void destroyPlayer() {
        JWLog.d(TAG, "destroyPlayer() mPlayer=" + JWLog.id(mPlayer));
        if (mPlayer != null) {
            unRegisterReceiver();
            unregisterPipBackCallback();

            // Let the playback manager know this player is being destroyed.
            PlaybackManager.getInstance().clearPlayer(this);

            // If we are casting we need to break the cast session as there is no simple
            // way to reconnect to an existing session if the player that created it is dead

            // If this doesn't match your use case, using a single player object and load content
            // into it rather than creating a new player for every piece of content.
            mPlayer.stop();

            // Ensure MediaSession reflects a non-playing state when UI player is destroyed
            try {
                com.jwplayer.rnjwplayer.session.RNJWMediaSessionHelper.handleDestroy();
            } catch (Throwable t) {
                JWLog.w(TAG, "Failed to update MediaSession on destroy: " + t.getMessage());
            }

            // send signal to JW SDK player is destroyed
            registry.setCurrentState(Lifecycle.State.DESTROYED);

            // Stop listening to activities lifecycle
            mActivity.getLifecycle().removeObserver(lifecycleObserver);
            mPlayer.deregisterActivityForPip();

            // Remove playlist item callback listener
            mPlayer.removePlaylistItemCallbackListener();

            mPlayer.removeListeners(this,
                    // VideoPlayerEvents
                    EventType.READY,
                    EventType.PLAY,
                    EventType.PAUSE,
                    EventType.COMPLETE,
                    EventType.IDLE,
                    EventType.ERROR,
                    EventType.SETUP_ERROR,
                    EventType.BUFFER,
                    EventType.TIME,
                    EventType.PLAYLIST,
                    EventType.PLAYLIST_ITEM,
                    EventType.PLAYLIST_COMPLETE,
                    EventType.FIRST_FRAME,
                    EventType.CONTROLS,
                    EventType.CONTROLBAR_VISIBILITY,
                    EventType.DISPLAY_CLICK,
                    EventType.FULLSCREEN,
                    EventType.SEEK,
                    EventType.SEEKED,
                    EventType.PLAYBACK_RATE_CHANGED,
                    EventType.CAPTIONS_LIST,
                    EventType.CAPTIONS_CHANGED,
                    EventType.META,

                    // Ad events
                    EventType.BEFORE_PLAY,
                    EventType.BEFORE_COMPLETE,
                    EventType.AD_BREAK_START,
                    EventType.AD_BREAK_END,
                    EventType.AD_BREAK_IGNORED,
                    EventType.AD_CLICK,
                    EventType.AD_COMPANIONS,
                    EventType.AD_COMPLETE,
                    EventType.AD_ERROR,
                    EventType.AD_IMPRESSION,
                    EventType.AD_WARNING,
                    EventType.AD_LOADED,
                    EventType.AD_LOADED_XML,
                    EventType.AD_META,
                    EventType.AD_PAUSE,
                    EventType.AD_PLAY,
                    EventType.AD_REQUEST,
                    EventType.AD_SCHEDULE,
                    EventType.AD_SKIPPED,
                    EventType.AD_STARTED,
                    EventType.AD_TIME,
                    EventType.AD_VIEWABLE_IMPRESSION,
                    // Cast event
                    EventType.CAST,
                    // Pip events
                    EventType.PIP_CLOSE,
                    EventType.PIP_OPEN
            );

            mPlayer = null;
            
            // Remove the old player view from the view hierarchy to prevent
            // the old UI controls from receiving touch events (fixes GitHub issue #188 crash)
            if (mPlayerView != null) {
                removeView(mPlayerView);
                mPlayerView = null;
            }

            getReactContext().removeLifecycleEventListener(this);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (audioManager != null && focusRequest != null) {
                    audioManager.abandonAudioFocusRequest(focusRequest);
                }
            } else {
                if (audioManager != null) {
                    audioManager.abandonAudioFocus(this);
                }
            }

            audioManager = null;
            hasAudioFocus = false;

            doUnbindService();
        } else {
            JWLog.d(TAG, "destroyPlayer() skipped: mPlayer is null");
        }
    }

    /**
     * Destroy any existing background player from JWPlayerNativePlaybackHandler
     * Similar to destroyPlayer() but for background players to prevent conflicts
     */
    private void destroyBackgroundPlayer() {
        JWLog.d(TAG, "destroyBackgroundPlayer() attempting to clean any background player");
        // THIS METHOD IS DEPRECATED AND REPLACED BY PlaybackManager
        // Kept for reference during transition, should be removed later.
        try {
            Class<?> handlerClass = Class.forName("com.jwplayer.rnjwplayer.JWPlayerNativePlaybackHandler");
            java.lang.reflect.Method getInstanceMethod = handlerClass.getMethod("getInstance", Context.class);
            Object handlerInstance = getInstanceMethod.invoke(null, getContext());
            
            if (handlerInstance != null) {
                // Check if background player is active
                java.lang.reflect.Method isActiveMethod = handlerClass.getMethod("isBackgroundPlayerActive");
                Boolean isActive = (Boolean) isActiveMethod.invoke(handlerInstance);
                
                if (isActive != null && isActive) {
                    JWLog.d(TAG, "Found active background player, destroying it to prevent conflicts");
                    
                    // Get background player info before destroying for logging
                    try {
                        java.lang.reflect.Method getInfoMethod = handlerClass.getMethod("getCurrentBackgroundPlayerInfo");
                        Object backgroundInfo = getInfoMethod.invoke(handlerInstance);
                        if (backgroundInfo instanceof java.util.Map) {
                            java.util.Map<String, Object> bgInfo = (java.util.Map<String, Object>) backgroundInfo;
                            JWLog.d(TAG, "Destroying background player playing: " + bgInfo.get("title"));
                        }
                    } catch (Exception infoError) {
                        // Ignore info retrieval errors
                    }
                    
                    // Stop and cleanup background player completely (similar to destroyPlayer)
                    java.lang.reflect.Method stopMethod = handlerClass.getMethod("stopAndCleanup");
                    stopMethod.invoke(handlerInstance);
                    
                    JWLog.d(TAG, "Successfully destroyed background player to prevent dual playback");
                } else {
                    JWLog.d(TAG, "No active background player found, proceeding with UI player setup");
                }
            }
        } catch (Exception e) {
            JWLog.w(TAG, "Could not check/destroy background player: " + e.getMessage());
            // Continue with UI player setup even if background player check fails
        }
    }

    public void setupPlayerView(Boolean backgroundAudioEnabled, Boolean playlistItemCallbackEnabled) {
        JWLog.d(TAG, "setupPlayerView(backgroundAudioEnabled=" + backgroundAudioEnabled + ", playlistItemCallbackEnabled=" + playlistItemCallbackEnabled + ")");
        if (mPlayer != null) {

            mPlayer.addListeners(this,
                    // VideoPlayerEvents
                    EventType.READY,
                    EventType.PLAY,
                    EventType.PAUSE,
                    EventType.COMPLETE,
                    EventType.IDLE,
                    EventType.ERROR,
                    EventType.SETUP_ERROR,
                    EventType.BUFFER,
                    EventType.TIME,
                    EventType.AUDIO_TRACKS,
                    EventType.AUDIO_TRACK_CHANGED,
                    EventType.PLAYLIST,
                    EventType.PLAYLIST_ITEM,
                    EventType.PLAYLIST_COMPLETE,
                    EventType.FIRST_FRAME,
                    EventType.CONTROLS,
                    EventType.CONTROLBAR_VISIBILITY,
                    EventType.DISPLAY_CLICK,
                    EventType.FULLSCREEN,
                    EventType.SEEK,
                    EventType.SEEKED,
                    EventType.PLAYBACK_RATE_CHANGED,
                    EventType.CAPTIONS_LIST,
                    EventType.CAPTIONS_CHANGED,
                    EventType.META,
                    // Ad events
                    EventType.BEFORE_PLAY,
                    EventType.BEFORE_COMPLETE,
                    EventType.AD_BREAK_START,
                    EventType.AD_BREAK_END,
                    EventType.AD_BREAK_IGNORED,
                    EventType.AD_CLICK,
                    EventType.AD_COMPANIONS,
                    EventType.AD_COMPLETE,
                    EventType.AD_ERROR,
                    EventType.AD_IMPRESSION,
                    EventType.AD_WARNING,
                    EventType.AD_LOADED,
                    EventType.AD_LOADED_XML,
                    EventType.AD_META,
                    EventType.AD_PAUSE,
                    EventType.AD_PLAY,
                    EventType.AD_REQUEST,
                    EventType.AD_SCHEDULE,
                    EventType.AD_SKIPPED,
                    EventType.AD_STARTED,
                    EventType.AD_TIME,
                    EventType.AD_VIEWABLE_IMPRESSION,
                    // Cast event
                    EventType.CAST,
                    // Pip events
                    EventType.PIP_CLOSE,
                    EventType.PIP_OPEN
            );

            if (playerInModal) {
                mPlayer.setFullscreenHandler(createModalFullscreenHandler());
            } else {
                mPlayer.setFullscreenHandler(new fullscreenHandler());
            }
            mPlayer.allowBackgroundAudio(backgroundAudioEnabled);

            if (playlistItemCallbackEnabled) {
                mPlayer.setPlaylistItemCallbackListener(this);
            }
        }
    }

    public void resolveNextPlaylistItem(ReadableMap playlistItem) {
        JWLog.d(TAG, "resolveNextPlaylistItem(playlistItem=" + JWLog.safe(playlistItem) + ") promisePending=" + (itemUpdatePromise != null));
        if (itemUpdatePromise == null) {
            return;
        }

        if (playlistItem == null) {
            itemUpdatePromise.continuePlayback();
            itemUpdatePromise = null;
            return;
        }

        try {
            PlaylistItem updatedPlaylistItem = Util.getPlaylistItem(playlistItem);
            itemUpdatePromise.modify(updatedPlaylistItem);
        } catch (Exception exception) {
            itemUpdatePromise.continuePlayback();
        }

        itemUpdatePromise = null;
    }

    /**
     * Helper to build the a generic `ExtensibleFullscreenHandler` with small tweaks to play nice with Modals
     *
     * @return {@link ExtensibleFullscreenHandler}
     */
    private ExtensibleFullscreenHandler createModalFullscreenHandler() {
        JWLog.d(TAG, "createModalFullscreenHandler()");
        DeviceOrientationDelegate delegate = getDeviceOrientationDelegate();
        FullscreenDialog dialog = new FullscreenDialog(
                mActivity,
                mActivity,
                android.R.style.Theme_Black_NoTitleBar_Fullscreen
        );

        return new ExtensibleFullscreenHandler(
                new DialogLayoutDelegate(
                        mPlayerView,
                        dialog
                ),
                delegate,
                new SystemUiDelegate(
                        mActivity,
                        mActivity.getLifecycle(),
                        new Handler(),
                        dialog.getWindow().getDecorView()
                )
        ) {
            @Override
            public void onFullscreenRequested() {
                JWLog.d(TAG, "ModalFullscreenHandler.onFullscreenRequested()");
                // if landscape is priorty we have to turn off full-screen portrait before allowing
                // the default call for full-screen
                mPlayer.allowFullscreenPortrait(!landscapeOnFullScreen);
                super.onFullscreenRequested();
                // safely set it back on UI thread after work can be finished
                final Handler handler = new Handler(Looper.getMainLooper());
                handler.postDelayed(() -> {
                    if (mPlayer != null) {
                        mPlayer.allowFullscreenPortrait(true);
                    }
                }, 100);
                WritableMap eventEnterFullscreen = Arguments.createMap();
                eventEnterFullscreen.putString("message", "onFullscreenRequested");
                getReactContext().getJSModule(RCTEventEmitter.class).receiveEvent(
                        getId(),
                        "topFullScreenRequested",
                        eventEnterFullscreen);
            }

            @Override
            public void onFullscreenExitRequested() {
                JWLog.d(TAG, "ModalFullscreenHandler.onFullscreenExitRequested()");
                super.onFullscreenExitRequested();

                WritableMap eventExitFullscreen = Arguments.createMap();
                eventExitFullscreen.putString("message", "onFullscreenExitRequested");
                getReactContext().getJSModule(RCTEventEmitter.class).receiveEvent(
                        getId(),
                        "topFullScreenExitRequested",
                        eventExitFullscreen);
            }
        };
    }

    /**
     * Add logic here for your custom orientation implementation
     *
     * @return Default {@link DeviceOrientationDelegate}
     */
    private DeviceOrientationDelegate getDeviceOrientationDelegate() {
        JWLog.d(TAG, "getDeviceOrientationDelegate()");
        DeviceOrientationDelegate delegate = new DeviceOrientationDelegate(
                mActivity,
                mActivity.getLifecycle(),
                new Handler()
        ) {
            @Override
            public void setFullscreen(boolean fullscreen) {
                super.setFullscreen(fullscreen);
            }

            @Override
            public void onAllowRotationChanged(boolean allowRotation) {
                super.onAllowRotationChanged(allowRotation);
            }

            @Override
            protected void doRotation(boolean fullscreen, boolean allowFullscreenPortrait) {
                super.doRotation(fullscreen, allowFullscreenPortrait);
            }

            @Override
            protected void doRotationListener() {
                super.doRotationListener();
            }

            @Override
            public void onAllowFullscreenPortrait(boolean allowFullscreenPortrait) {
                super.onAllowFullscreenPortrait(allowFullscreenPortrait);
            }
        };
        delegate.onAllowRotationChanged(true);
        return delegate;
    }

    @Override
    public void onBeforeNextPlaylistItem(PlaylistItemDecision playlistItemDecision, PlaylistItem nextItem, int indexOfNextItem) {
        JWLog.d(TAG, "onBeforeNextPlaylistItem(playlistItemDecision=" + JWLog.safe(playlistItemDecision) + ", nextItem=" + JWLog.safe(nextItem) + ", indexOfNextItem=" + indexOfNextItem + ")");
        WritableMap event = Arguments.createMap();
        Gson gson = new Gson();
        event.putString("message", "onBeforeNextPlaylistItem");
        event.putInt("index", indexOfNextItem);
        event.putString("playlistItem", gson.toJson(nextItem));
        getReactContext().getJSModule(RCTEventEmitter.class).receiveEvent(getId(), "topBeforeNextPlaylistItem", event);

        itemUpdatePromise = playlistItemDecision;
    }

    private class fullscreenHandler implements FullscreenHandler {
        ViewGroup mPlayerViewContainer = (ViewGroup) mPlayerView.getParent();
        private View mDecorView;

        @Override
        public void onFullscreenRequested() {
            JWLog.d(TAG, "fullscreenHandler.onFullscreenRequested()");
            mDecorView = mActivity.getWindow().getDecorView();

            // Hide system ui
            mDecorView.setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION // hides bottom bar
                            | View.SYSTEM_UI_FLAG_FULLSCREEN // hides top bar
                            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY // prevents navigation bar from overriding
                    // exit-full-screen button. Swipe from side to access nav bar.
            );

            // Enter landscape mode for fullscreen videos
            if (landscapeOnFullScreen) {
                mActivity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
            }

            mPlayerViewContainer = (ViewGroup) mPlayerView.getParent();

            // Remove the JWPlayerView from the list item.
            if (mPlayerViewContainer != null) {
                mPlayerViewContainer.removeView(mPlayerView);
            }

            // Add the JWPlayerView to the RootView as soon as the UI thread is ready.
            mRootView.post(new Runnable() {
                @Override
                public void run() {
                    mRootView.addView(mPlayerView, new ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT));
                }
            });

            WritableMap eventEnterFullscreen = Arguments.createMap();
            eventEnterFullscreen.putString("message", "onFullscreenRequested");
            getReactContext().getJSModule(RCTEventEmitter.class).receiveEvent(
                    getId(),
                    "topFullScreenRequested",
                    eventEnterFullscreen);
        }

        @Override
        public void onFullscreenExitRequested() {
            JWLog.d(TAG, "fullscreenHandler.onFullscreenExitRequested()");
            mDecorView.setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_VISIBLE // clear the hide system flags
            );

            // Enter portrait mode
            if (portraitOnExitFullScreen) {
                mActivity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
            }

            // Remove the player view from the root ViewGroup.
            mRootView.removeView(mPlayerView);

            // As soon as the UI thread has finished processing the current message queue it
            // should add the JWPlayerView back to the list item.
            mPlayerViewContainer.post(new Runnable() {
                @Override
                public void run() {
                    // View may not have been removed properly (especially if returning from PiP)
                    mPlayerViewContainer.removeView(mPlayerView);

                    mPlayerViewContainer.addView(mPlayerView, new ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT));
                    // returning from full-screen portrait requires a different measure
                    if (mActivity.getResources().getConfiguration().orientation == ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                    ) {
                        mPlayerView.layout(mPlayerView.getLeft(), mPlayerViewContainer.getTop(),
                                mPlayerViewContainer.getMeasuredWidth(), mPlayerViewContainer.getBottom());
                    } else {
                        mPlayerView.layout(mPlayerViewContainer.getLeft(), mPlayerViewContainer.getTop(),
                                mPlayerViewContainer.getRight(), mPlayerViewContainer.getBottom());
                    }
                }
            });

            WritableMap eventExitFullscreen = Arguments.createMap();
            eventExitFullscreen.putString("message", "onFullscreenExitRequested");
            getReactContext().getJSModule(RCTEventEmitter.class).receiveEvent(
                    getId(),
                    "topFullScreenExitRequested",
                    eventExitFullscreen);
        }

        @Override
        public void onAllowRotationChanged(boolean b) {
        }

        @Override
        public void onAllowFullscreenPortraitChanged(boolean allowFullscreenPortrait) {
        }

        @Override
        public void updateLayoutParams(ViewGroup.LayoutParams layoutParams) {
        }

        @Override
        public void setUseFullscreenLayoutFlags(boolean b) {
        }
    }

    private final Map<View, Integer> rootViewVisibilitySnapshot = new LinkedHashMap<>();

    /**
     * Handles a Picture-in-Picture mode change for this view's host activity.
     *
     * Invoked from the {@link androidx.activity.ComponentActivity} listener registered
     * in {@link #registerPipListener()}. The listener fires synchronously inside the
     * activity's lifecycle pass, so we re-post the actual reparenting work to the
     * player view's message queue. This preserves the deferred timing the previous
     * BroadcastReceiver implementation relied on (the JWPlayer SDK calculates the PiP
     * window aspect off the current View; reparenting too early caused the host UI to
     * be minimized as a whole instead of just the player).
     *
     * Listener-based delivery is in-process only, so this method can never be called
     * from another app. That eliminates the previous cross-app PiP crash entirely
     * without requiring any host-app MainActivity changes.
     */
    private void handlePipChange(boolean isInPip, Configuration newConfig) {
        JWLog.d(TAG, "handlePipChange(isInPip=" + isInPip + ")");

        // Ignore duplicate callbacks for the same PiP state; they can arrive during
        // config churn and should not re-run view reparenting.
        if (mLastHandledPipState != null && mLastHandledPipState == isInPip) {
            JWLog.d(TAG, "handlePipChange: duplicate state " + isInPip + ", ignoring");
            return;
        }

        if (mPlayer == null || mPlayerView == null || mActivity == null || mActivity.isFinishing()) {
            JWLog.w(TAG, "handlePipChange: invalid state, ignoring");
            return;
        }

        // Defer the layout work until after the activity finishes its lifecycle pass.
        // Running synchronously from inside onPictureInPictureModeChanged() reparents
        // the view before the system has measured the PiP window, which causes the
        // entire activity content to render at PiP size on exit (visible as the host
        // app being "minimized" instead of just the player).
        final Configuration configToApply = newConfig;
        mPlayerView.post(() -> applyPipChange(isInPip, configToApply));
    }

    private void applyPipChange(boolean isInPip, Configuration newConfig) {
        if (mPlayer == null || mPlayerView == null || mActivity == null || mActivity.isFinishing()) {
            JWLog.w(TAG, "applyPipChange: invalid state, ignoring");
            return;
        }
        if (mLastHandledPipState != null && mLastHandledPipState == isInPip) {
            return;
        }

        try {
            // Tell the JWP SDK we are toggling so it can handle toolbar / internal setup
            mPlayer.onPictureInPictureModeChanged(isInPip, newConfig);

            PlaybackManager.getInstance().setUiInPip(isInPip);

            View decorView = mActivity.getWindow().getDecorView();
            ViewGroup rootView = decorView.findViewById(android.R.id.content);
            if (rootView == null) return;

            ViewGroup.LayoutParams layoutParams = new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT);

            if (isInPip) {
                // Going into Picture in Picture
                ViewGroup parent = (ViewGroup) mPlayerView.getParent();

                // Remove the player view temporarily
                if (parent != null) {
                    parent.removeView(mPlayerView);
                }

                // Hide all views but player view and keep a handle on them for later
                rootViewVisibilitySnapshot.clear();
                for (int i = 0; i < rootView.getChildCount(); i++) {
                    View child = rootView.getChildAt(i);
                    if (child != mPlayerView) {
                        rootViewVisibilitySnapshot.put(child, child.getVisibility());
                        child.setVisibility(View.GONE);
                    }
                }
                // Add player view back (the JWP SDK has already calculated the PiP size/aspect off the View)
                rootView.addView(mPlayerView, layoutParams);
                mLastHandledPipState = true;
            } else {
                // Exiting Picture in Picture

                // Exit without a prior enter snapshot means this view instance did
                // not own the PiP transition. Skip reparenting to avoid applying an
                // invalid layout state (observed as app-sized minimization artifacts).
                if (rootViewVisibilitySnapshot.isEmpty()) {
                    JWLog.w(TAG, "applyPipChange: visibility snapshot empty on exit, skipping player reparent");
                    mLastHandledPipState = false;
                    return;
                }

                // Toggle controls to ensure we don't lose them -- weird UX bug fix where controls got lost
                mPlayer.setForceControlsVisibility(true);
                mPlayer.setForceControlsVisibility(false);

                // If player was in fullscreen when going into PiP, we need to force it back out
                if (mPlayer.getFullscreen()) {
                    mPlayer.setFullscreen(false, true);
                }

                // Strip player view
                rootView.removeView(mPlayerView);

                // Restore visibility for the views that were hidden on PiP enter.
                // Use the keyed snapshot so we never index out of bounds.
                for (Map.Entry<View, Integer> entry : rootViewVisibilitySnapshot.entrySet()) {
                    if (entry.getKey().getParent() == rootView) {
                        entry.getKey().setVisibility(entry.getValue());
                    }
                }
                rootViewVisibilitySnapshot.clear();
                // Add player view back in main spot
                addView(mPlayerView, 0, layoutParams);
                mLastHandledPipState = false;
            }
        } catch (Throwable t) {
            JWLog.e(TAG, "applyPipChange: unexpected error: " + t.getMessage());
        }
    }

    /**
     * Registers an in-process Picture-in-Picture mode change listener on the host
     * activity. Replaces the previous BroadcastReceiver approach, which required a
     * matching {@code sendBroadcast} from the host app's {@code MainActivity} and
     * was vulnerable to cross-app delivery when multiple apps embedded this library
     * on the same device.
     *
     * Method name kept as {@code registerReceiver} for callsite compatibility.
     */
    private void registerReceiver() {
        JWLog.d(TAG, "registerReceiver() -> PiP listener");
        if (mActivity == null || mPipListener != null) {
            return;
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            // PiP is unsupported below API 26; nothing to listen for.
            return;
        }
        mPipListener = info -> handlePipChange(
                info.isInPictureInPictureMode(),
                info.getNewConfig());
        mActivity.addOnPictureInPictureModeChangedListener(mPipListener);
    }

    private void unRegisterReceiver() {
        JWLog.d(TAG, "unRegisterReceiver() -> PiP listener");
        if (mPipListener != null && mActivity != null) {
            try {
                mActivity.removeOnPictureInPictureModeChangedListener(mPipListener);
            } catch (Throwable ignored) {
                // listener already removed or activity tearing down
            }
            mPipListener = null;
        }
        rootViewVisibilitySnapshot.clear();
        mLastHandledPipState = null;
    }

    /**
     * Registers an OnBackPressedCallback that intercepts the back press / back gesture
     * and routes the activity into Picture-in-Picture mode while media is playing.
     *
     * Why this is needed: JWPlayer SDK's registerActivityForPip() hooks into
     * Activity.onUserLeaveHint(), which Android only fires for Home / app-switch
     * navigation. The back gesture (or back button) does NOT fire onUserLeaveHint;
     * it calls Activity.finish() directly. Once the activity is finishing, it is
     * too late to enter PiP from onPause(). We must intercept the back press
     * BEFORE the activity finishes.
     */
    private void registerPipBackCallback() {
        JWLog.d(TAG, "registerPipBackCallback()");
        if (mActivity == null || mPipBackCallback != null) {
            return;
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return; // PiP API requires API 26+
        }
        mPipBackCallback = new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (mPlayer == null || mActivity == null) {
                    fallbackToDefaultBack();
                    return;
                }
                try {
                    if (mActivity.isInPictureInPictureMode()) {
                        // Already in PiP — let default back behavior proceed
                        fallbackToDefaultBack();
                        return;
                    }
                    PlayerState state = mPlayer.getState();
                    if (state == PlayerState.PLAYING || state == PlayerState.BUFFERING) {
                        JWLog.d(TAG, "PiP back-callback: entering PiP (state=" + state + ")");
                        mPlayer.enterPictureInPictureMode();
                        // Do NOT call default back — activity stays alive and transitions to PiP
                        return;
                    }
                } catch (Throwable t) {
                    JWLog.w(TAG, "PiP back-callback: failed to enter PiP: " + t.getMessage());
                }
                fallbackToDefaultBack();
            }

            private void fallbackToDefaultBack() {
                // Disable this callback and re-dispatch so the system performs the
                // default back behavior (finish the activity).
                setEnabled(false);
                try {
                    mActivity.getOnBackPressedDispatcher().onBackPressed();
                } finally {
                    setEnabled(true);
                }
            }
        };
        mActivity.getOnBackPressedDispatcher().addCallback(mActivity, mPipBackCallback);
    }

    private void unregisterPipBackCallback() {
        JWLog.d(TAG, "unregisterPipBackCallback()");
        if (mPipBackCallback != null) {
            mPipBackCallback.remove();
            mPipBackCallback = null;
        }
    }

    /**
     * Creates a UiConfig that ensures PLAYER_CONTROLS_CONTAINER is always shown.
     * If controls are not shown, the PLAYER_CONTROLS_CONTAINER UI Group is not displayed.
     * This logic ensures that the PLAYER_CONTROLS_CONTAINER UI Group is displayed regardless if controls are shown or not.
     * There is no way to recover controls if you do not show this UiGroup.
     * But you are able to hide the controls still if it is shown.
     */
    private UiConfig createUiConfigWithControlsContainer(JWPlayer player, UiConfig originalUiConfig) {
        JWLog.d(TAG, "createUiConfigWithControlsContainer(player=" + JWLog.safe(player) + ", originalUiConfig=" + JWLog.safe(originalUiConfig) + ")");
        if (!player.getControls()) {
            return new UiConfig.Builder(originalUiConfig).show(UiGroup.PLAYER_CONTROLS_CONTAINER).build();
        } else {
            return originalUiConfig;
        }
    }

    private PlayerConfig applyHiddenUiGroups(PlayerConfig config, ReadableMap prop) {
        if (config == null || prop == null || !prop.hasKey("hideUIGroups")) {
            return config;
        }

        ReadableArray uiGroupsArray = prop.getArray("hideUIGroups");
        if (uiGroupsArray == null) {
            return config;
        }

        UiConfig.Builder uiConfigBuilder = config.getUiConfig() != null
                ? new UiConfig.Builder(config.getUiConfig())
                : new UiConfig.Builder().displayAllControls();

        for (int i = 0; i < uiGroupsArray.size(); i++) {
            if (uiGroupsArray.getType(i) == ReadableType.String) {
                UiGroup uiGroup = GROUP_TYPES.get(uiGroupsArray.getString(i));
                if (uiGroup != null) {
                    uiConfigBuilder.hide(uiGroup);
                }
            }
        }

        UiConfig uiConfig = uiConfigBuilder.show(UiGroup.PLAYER_CONTROLS_CONTAINER).build();
        return new PlayerConfig.Builder(config).uiConfig(uiConfig).build();
    }

    /**
     * Main entry point for setting/updating player configuration.
     * Uses a smart approach: only recreate the player view when absolutely necessary,
     * otherwise reconfigure the existing player instance.
     * 
     * This follows JWPlayer SDK's intended usage pattern and significantly reduces overhead.
     */
    public void setConfig(ReadableMap prop) {
        JWLog.d(TAG, "setConfig(propKeys=" + (prop != null ? prop.toHashMap().keySet() : null) + ")");
        if (mConfig == null || !mConfig.equals(prop)) {
            // Set license key if provided
            if (prop.hasKey("license")) {
                new LicenseUtil().setLicenseKey(getReactContext(), prop.getString("license"));
            } else {
                JWLog.e(TAG, "JW SDK license not set");
            }

            // First time setup - need to create player view
            if (mPlayer == null) {
                this.createPlayerView(prop);
                mConfig = prop;
                return;
            }

            // Only playlist changed -> update config without stop/recreate
            if (mConfig != null && isOnlyDiff(prop, "playlist") && mPlayer != null) {
                JWLog.d(TAG, "Playlist-only change detected -> applying fast update");
                
                // IMPORTANT: ensure mPlaylistProp is updated from NEW prop
                if (prop.hasKey("playlist")) {
                    mPlaylistProp = prop.getArray("playlist");
                }

                PlayerConfig oldConfig = mPlayer.getConfig();
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
                        .playlist(Util.createPlaylist(mPlaylistProp))
                        .allowCrossProtocolRedirects(oldConfig.getAllowCrossProtocolRedirects())
                        .preload(oldConfig.getPreload())
                        .useTextureView(oldConfig.useTextureView())
                        .thumbnailPreview(oldConfig.getThumbnailPreview())
                        .mute(oldConfig.getMute())
                        .build();

                mPlayer.setup(config);

                mConfig = prop;
                return;
            }
            
            // Check if we need full player recreation (rare cases only)
            if (requiresPlayerRecreation(prop)) {
                JWLog.d(TAG, "Player recreation required - destroying and recreating player view");
                this.destroyPlayer();
                this.createPlayerView(prop);
            } else {
                // Normal case: reconfigure existing player without recreation
                JWLog.d(TAG, "Reconfiguring existing player without recreation");
                this.reconfigurePlayer(prop);
            }
        }

        mConfig = prop;
    }

    /**
     * Determines if a config change requires full player view recreation.
     * Only return true for changes that genuinely cannot be handled by reconfiguration.
     * 
     * Currently, the JWPlayer SDK can handle almost all config changes via setup(),
     * so we only recreate for critical changes like license updates.
     */
    private boolean requiresPlayerRecreation(ReadableMap prop) {
        JWLog.d(TAG, "requiresPlayerRecreation(prop=" + JWLog.safe(prop) + ")");
        if (mConfig == null || mPlayer == null) {
            return true;
        }
        
        // License change requires recreation
        if (prop.hasKey("license") && mConfig.hasKey("license")) {
            String newLicense = prop.getString("license");
            String oldLicense = mConfig.getString("license");
            if (newLicense != null && !newLicense.equals(oldLicense)) {
                return true;
            }
        }
        
        // Add other cases here if needed in the future
        // For example: switching between playerView and playerViewController modes
        
        return false;
    }

    /**
     * Reconfigures the existing player instance with new settings.
     * This is the preferred path for config updates as it preserves the player instance
     * and video surface, following JWPlayer SDK's design intent.
     * 
     * Based on the pattern used in loadPlaylist() and loadPlaylistWithUrl().
     */
    private void reconfigurePlayer(ReadableMap prop) {
        JWLog.d(TAG, "reconfigurePlayer(prop=" + JWLog.safe(prop) + ")");
        if (mPlayer == null) {
            JWLog.e(TAG, "Cannot reconfigure - player is null");
            return;
        }

        PlayerConfig oldConfig = mPlayer.getConfig();
        boolean wasFullscreen = mPlayer.getFullscreen();
        boolean currentControlsState = mPlayer.getControls();
        
        // Stop playback before reconfiguration to avoid issues (Issue #188 fix)
        mPlayer.stop();
        
        // Build new configuration
        PlayerConfig newConfig = buildPlayerConfig(prop, oldConfig);
        newConfig = applyHiddenUiGroups(newConfig, prop);
        
        // ALWAYS ensure PLAYER_CONTROLS_CONTAINER is shown in UiConfig after setup.
        // This prevents issues where controls are off and JWPlayer SDK hides UI groups,
        // leaving them in a state where setControls(true) won't work.
        // We'll manage controls state via setControls() API after setup for clean state management.
        UiConfig fixedUiConfig = new UiConfig.Builder(newConfig.getUiConfig())
            .show(UiGroup.PLAYER_CONTROLS_CONTAINER)
            .build();
        newConfig = new PlayerConfig.Builder(newConfig)
            .uiConfig(fixedUiConfig)
            .build();
        
        // Apply new configuration to existing player
        mPlayer.setup(newConfig);
        
        // Now manage controls state via API (after setup, when UI groups are in clean state)
        if (prop.hasKey("controls")) {
            // Developer explicitly set controls in props - use that value
            mPlayer.setControls(prop.getBoolean("controls"));
        } else if (!currentControlsState) {
            // Controls were off before reconfigure and no explicit prop provided
            // Restore the off state (after ensuring UI groups are visible)
            mPlayer.setControls(false);
        }
        // Note: If controls were on and no prop provided, they'll stay on (default from configureUI)
        
        // Restore fullscreen state if needed
        // The fullscreen view is still active but internals need to be notified
        if (wasFullscreen) {
            mPlayer.setFullscreen(true, true);
        }
    }

    /**
     * Builds a PlayerConfig from React Native props, preserving relevant old config values.
     * This ensures smooth transitions when reconfiguring the player.
     */
    private PlayerConfig buildPlayerConfig(ReadableMap prop, PlayerConfig oldConfig) {
        JWLog.d(TAG, "buildPlayerConfig(prop=" + JWLog.safe(prop) + ", oldConfig=" + JWLog.safe(oldConfig) + ")");
        PlayerConfig.Builder configBuilder = new PlayerConfig.Builder();
        
        // Try to parse as JW config first
        JSONObject obj;
        PlayerConfig jwConfig = null;
        Boolean forceLegacy = prop.hasKey("forceLegacyConfig") ? prop.getBoolean("forceLegacyConfig") : false;
        Boolean isJwConfig = false;

        if (!forceLegacy) {
            try {
                obj = MapUtil.toJSONObject(prop);
                jwConfig = JsonHelper.parseConfigJson(obj);
                isJwConfig = true;
                return jwConfig;  // Return directly if valid JW config
            } catch (Exception ex) {
                JWLog.d(TAG, "Not a JW config format, using legacy builder");
                isJwConfig = false;
            }
        }

        // Legacy config building
        configurePlaylist(configBuilder, prop);
        configureBasicSettings(configBuilder, prop);
        configureStyling(configBuilder, prop);
        configureAdvertising(configBuilder, prop);
        configureUI(configBuilder, prop);

        // Preserve important settings that RN props may not include every time
        if (oldConfig != null) {
            // Only copy if props did NOT specify them explicitly (so props win)
            // Note: these keys are not currently supported in legacy props, so we always preserve.

            configBuilder
                .allowCrossProtocolRedirects(oldConfig.getAllowCrossProtocolRedirects())
                .preload(oldConfig.getPreload())
                .useTextureView(oldConfig.useTextureView())
                .thumbnailPreview(oldConfig.getThumbnailPreview())
                .mute(oldConfig.getMute());

            // relatedConfig / nextUpOffset also need preserving if not driven by props
            configBuilder.relatedConfig(oldConfig.getRelatedConfig());

            // If your legacy props don't set nextUpOffset directly (only nextUpStyle),
            // preserving nextUpOffset can prevent resets:
            configBuilder.nextUpOffset(oldConfig.getNextUpOffset());
        }
        
        return configBuilder.build();
    }

    /**
     * Utility method to check if only a specific key differs between configs.
     * This is kept for potential future optimizations or debugging, but is no longer
     * used in the main setConfig flow since we now reconfigure the player for all changes.
     * 
     * @deprecated Consider using reconfigurePlayer() for all config changes instead
     */
    @Deprecated
    public boolean isOnlyDiff(ReadableMap prop, String keyName) {
        if (mConfig == null || prop == null) {
            return false;
        }
        
        // Convert ReadableMap to HashMap
        Map<String, Object> mConfigMap = mConfig.toHashMap();
        Map<String, Object> propMap = prop.toHashMap();

        Map<String, Object> differences = new HashMap<>();

        // Find keys in mConfig that aren't in prop or have different values
        for (Map.Entry<String, Object> entry : mConfigMap.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();

            if (!propMap.containsKey(key) || !propMap.get(key).equals(value)) {
                differences.put(key, value);
            }
        }

        // Find keys in prop that aren't in mConfig
        for (String key : propMap.keySet()) {
            if (!mConfigMap.containsKey(key)) {
                differences.put(key, propMap.get(key));
            }
        }

        return differences.size() == 1 && differences.containsKey(keyName);
    }

    private boolean playlistNotTheSame(ReadableMap prop) {
        JWLog.d(TAG, "playlistNotTheSame()" );
        return prop.hasKey("playlist") && mPlaylistProp != prop.getArray("playlist") && !Arrays
                .deepEquals(new ReadableArray[]{mPlaylistProp}, new ReadableArray[]{prop.getArray("playlist")});
    }

    private void configurePlaylist(PlayerConfig.Builder configBuilder, ReadableMap prop) {
        JWLog.d(TAG, "configurePlaylist(configBuilder=" + JWLog.safe(configBuilder) + ", prop=" + JWLog.safe(prop) + ")");
        if (playlistNotTheSame(prop)) {
            List<PlaylistItem> playlist = new ArrayList<>();
            mPlaylistProp = prop.getArray("playlist");
            if (mPlaylistProp != null && mPlaylistProp.size() > 0) {
                int j = 0;
                while (mPlaylistProp.size() > j) {
                    ReadableMap playlistItem = mPlaylistProp.getMap(j);
                    PlaylistItem newPlayListItem = Util.getPlaylistItem((playlistItem));
                    playlist.add(newPlayListItem);
                    j++;
                }
            }
            configBuilder.playlist(playlist);
        }
    }

    private void configureBasicSettings(PlayerConfig.Builder configBuilder, ReadableMap prop) {
        JWLog.d(TAG, "configureBasicSettings(configBuilder=" + JWLog.safe(configBuilder) + ", prop=" + JWLog.safe(prop) + ")");
        if (prop.hasKey("autostart")) {
            boolean autostart = prop.getBoolean("autostart");
            configBuilder.autostart(autostart);
        }

        if (prop.hasKey("nextUpStyle")) {
            ReadableMap nextUpStyle = prop.getMap("nextUpStyle");
            if (nextUpStyle != null && nextUpStyle.hasKey("offsetSeconds")
                    && nextUpStyle.hasKey("offsetPercentage")) {
                int offsetSeconds = nextUpStyle.getInt("offsetSeconds");
                int offsetPercentage = nextUpStyle.getInt("offsetPercentage");
                configBuilder.nextUpOffset(offsetSeconds).nextUpOffsetPercentage(offsetPercentage);
            }
        }

        if (prop.hasKey("repeat")) {
            boolean repeat = prop.getBoolean("repeat");
            configBuilder.repeat(repeat);
        }

        if (prop.hasKey("stretching")) {
            String stretching = prop.getString("stretching");
            configBuilder.stretching(stretching);
        }
    }

    private void configureStyling(PlayerConfig.Builder configBuilder, ReadableMap prop) {
        JWLog.d(TAG, "configureStyling(configBuilder=" + JWLog.safe(configBuilder) + ", prop=" + JWLog.safe(prop) + ")");
        if (prop.hasKey("styling")) {
            ReadableMap styling = prop.getMap("styling");
            if (styling != null) {
                if (styling.hasKey("displayDescription")) {
                    boolean displayDescription = styling.getBoolean("displayDescription");
                    configBuilder.displayDescription(displayDescription);
                }

                if (styling.hasKey("displayTitle")) {
                    boolean displayTitle = styling.getBoolean("displayTitle");
                    configBuilder.displayTitle(displayTitle);
                }

                if (styling.hasKey("colors")) {
                    mColors = styling.getMap("colors");
                }
            }
        }
    }

    private void configureAdvertising(PlayerConfig.Builder configBuilder, ReadableMap prop) {
        JWLog.d(TAG, "configureAdvertising(configBuilder=" + JWLog.safe(configBuilder) + ", prop=" + JWLog.safe(prop) + ")");
        if (prop.hasKey("advertising")) {
            ReadableMap ads = prop.getMap("advertising");
            AdvertisingConfig advertisingConfig = RNJWPlayerAds.getAdvertisingConfig(ads);
            if (advertisingConfig != null) {
                configBuilder.advertisingConfig(advertisingConfig);
            }
        }
    }

    private void configureUI(PlayerConfig.Builder configBuilder, ReadableMap prop) {
        JWLog.d(TAG, "configureUI(configBuilder=" + JWLog.safe(configBuilder) + ", prop=" + JWLog.safe(prop) + ")");
        // Handle controls property - default to true if not specified
        boolean controls = true; // Default to showing controls
        if (prop.hasKey("controls")) {
            controls = prop.getBoolean("controls");
        }
        
        if (!controls) {
            UiConfig uiConfig = new UiConfig.Builder().hideAllControls().build();
            configBuilder.uiConfig(uiConfig);
        } else {
            // Explicitly show controls and ensure controls container is visible
            // This ensures controls work even if setControls() is called later
            UiConfig uiConfig = new UiConfig.Builder()
                .displayAllControls()
                .show(UiGroup.PLAYER_CONTROLS_CONTAINER)
                .build();
            configBuilder.uiConfig(uiConfig);
        }

        if (prop.hasKey("hideUIGroups")) {
            ReadableArray uiGroupsArray = prop.getArray("hideUIGroups");
            UiConfig.Builder hideConfigBuilder = new UiConfig.Builder().displayAllControls();
            for (int i = 0; i < uiGroupsArray.size(); i++) {
                if (uiGroupsArray.getType(i) == ReadableType.String) {
                    UiGroup uiGroup = GROUP_TYPES.get(uiGroupsArray.getString(i));
                    if (uiGroup != null) {
                        hideConfigBuilder.hide(uiGroup);
                    }
                }
            }
            UiConfig hideJwControlbarUiConfig = hideConfigBuilder.build();
            configBuilder.uiConfig(hideJwControlbarUiConfig);
        }
    }

    /**
     * Creates a new player view and initializes it with the provided configuration.
     * This should only be called for initial setup or when full recreation is required.
     * 
     * Note: This method calls destroyPlayer() first to ensure clean state.
     */
    private void createPlayerView(ReadableMap prop) {
        JWLog.d(TAG, "createPlayerView(prop=" + JWLog.safe(prop) + ")");
        PlayerConfig.Builder configBuilder = new PlayerConfig.Builder();

        JSONObject obj;
        PlayerConfig jwConfig = null;
        Boolean forceLegacy = prop.hasKey("forceLegacyConfig") ? prop.getBoolean("forceLegacyConfig") : false;
        Boolean playlistItemCallbackEnabled = prop.hasKey("playlistItemCallbackEnabled") ? prop.getBoolean("playlistItemCallbackEnabled") : false;
        Boolean isJwConfig = false;

        if (!forceLegacy) {
            try {
                obj = MapUtil.toJSONObject(prop);
                jwConfig = JsonHelper.parseConfigJson(obj);
                isJwConfig = true;
            } catch (Exception ex) {
                JWLog.e(TAG, ex.toString());
                isJwConfig = false; // not a valid jw config. Try to setup in legacy
            }
        }

        if (!isJwConfig) {
            configurePlaylist(configBuilder, prop);
            configureBasicSettings(configBuilder, prop);
            configureStyling(configBuilder, prop);
            configureAdvertising(configBuilder, prop);
            configureUI(configBuilder, prop);
        }

        Context simpleContext = getNonBuggyContext(getReactContext(), getAppContext());

        PlaybackManager.getInstance().stopAndCleanupCurrentPlayer();

        // Ensure clean state before creating new player view
        this.destroyPlayer();

        // Create new player view
        mPlayerView = new RNJWPlayer(simpleContext);
        mPlayerView.setFocusable(true);
        mPlayerView.setFocusableInTouchMode(true);

        // Set layout parameters
        setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 
                ViewGroup.LayoutParams.MATCH_PARENT));
        mPlayerView.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT));
        
        // Add to view hierarchy - React Native will handle layout
        addView(mPlayerView);

        // Ensure we have a valid state before applying to the player
        registry.setCurrentState(registry.getCurrentState()); // This is a hack to ensure player and view know the lifecycle state

        // Get player instance
        mPlayer = mPlayerView.getPlayer(this);

        // Register this new player view as the active player
        PlaybackManager.getInstance().setActivePlayer(mPlayer, this);

        // Apply view-specific props
        if (prop.hasKey("controls")) {
            mPlayerView.getPlayer().setControls(prop.getBoolean("controls"));
        }

        if (prop.hasKey("fullScreenOnLandscape")) {
            fullScreenOnLandscape = prop.getBoolean("fullScreenOnLandscape");
            mPlayerView.fullScreenOnLandscape = fullScreenOnLandscape;
        }

        if (prop.hasKey("landscapeOnFullScreen")) {
            landscapeOnFullScreen = prop.getBoolean("landscapeOnFullScreen");
        }

        if (prop.hasKey("portraitOnExitFullScreen")) {
            portraitOnExitFullScreen = prop.getBoolean("portraitOnExitFullScreen");
        }

        if (prop.hasKey("playerInModal")) {
            playerInModal = prop.getBoolean("playerInModal");
        }

        if (prop.hasKey("exitFullScreenOnPortrait")) {
            exitFullScreenOnPortrait = prop.getBoolean("exitFullScreenOnPortrait");
            mPlayerView.exitFullScreenOnPortrait = exitFullScreenOnPortrait;
        }

        // Setup player with config
        if (isJwConfig) {
            mPlayer.setup(applyHiddenUiGroups(jwConfig, prop));
        } else {
            PlayerConfig playerConfig = configBuilder.build();
            mPlayer.setup(playerConfig);
        }

        // Configure PiP if enabled
        if (mActivity != null && prop.hasKey("pipEnabled")) {
            boolean pipEnabled = prop.getBoolean("pipEnabled");
            if (pipEnabled) {
                registerReceiver();
                mPlayer.registerActivityForPip(mActivity, mActivity.getSupportActionBar());
                registerPipBackCallback();
            } else {
                mPlayer.deregisterActivityForPip();
                unRegisterReceiver();
                unregisterPipBackCallback();
            }
        }

        // Legacy styling support
        // NOTE: This isn't the ideal way to do this on Android. All drawables/colors/themes should
        // be targeted using styling. See https://docs.jwplayer.com/players/docs/android-styling-guide
        applyLegacyStyling();

        // Setup audio
        audioManager = (AudioManager) simpleContext.getSystemService(Context.AUDIO_SERVICE);

        if (prop.hasKey("backgroundAudioEnabled")) {
            backgroundAudioEnabled = prop.getBoolean("backgroundAudioEnabled");
        }

        setupPlayerView(backgroundAudioEnabled, playlistItemCallbackEnabled);

        setupMediaSessionHelper();
    }
    
    /**
     * Get the context to use for MediaSession operations
     */
    private Context getMediaSessionContext() {
        JWLog.d(TAG, "getMediaSessionContext()");
        return getNonBuggyContext(getReactContext(), getAppContext());
    }
    
    private void setupMediaSessionHelper() {
        JWLog.d(TAG, "setupMediaSessionHelper(backgroundAudioEnabled=" + backgroundAudioEnabled + ")");
        if (!backgroundAudioEnabled) {
            return;
        }

        // Prepare dependencies
        Context context = getMediaSessionContext();
        ServiceMediaApi serviceMediaApi = new ServiceMediaApi(mPlayer);
        com.jwplayer.rnjwplayer.session.RNJWNotificationHelper notificationHelper =
                new com.jwplayer.rnjwplayer.session.RNJWNotificationHelper.Builder(
                        context,
                        (NotificationManager) mActivity.getSystemService(Context.NOTIFICATION_SERVICE)
                ).build();

        // Single-source-of-truth: let the Builder own RNJWMediaSessionHelper creation.
        // Do NOT instantiate RNJWMediaSessionHelper here.
        JWLog.d(TAG, "Setting up MediaServiceController (Builder will create MediaSessionHelper)");
        mMediaServiceController = new RNJWMediaServiceController.Builder(mActivity, mPlayer)
                .serviceMediaApi(serviceMediaApi)
                .notificationHelper(notificationHelper)
                .build();
    }

    /**
     * Applies legacy color/styling customizations.
     * Extracted to separate method for clarity.
     */
    private void applyLegacyStyling() {
        if (mColors == null) {
            return;
        }

        if (mColors.hasKey("backgroundColor")) {
            mPlayerView.setBackgroundColor(Color.parseColor("#" + mColors.getString("backgroundColor")));
        }

        if (mColors.hasKey("timeslider")) {
            CueMarkerSeekbar seekBar = findViewById(com.longtailvideo.jwplayer.R.id.controlbar_seekbar);
            ReadableMap timeslider = mColors.getMap("timeslider");
            if (timeslider != null && seekBar != null) {
                LayerDrawable progressDrawable = (LayerDrawable) seekBar.getProgressDrawable();

                if (timeslider.hasKey("progress")) {
                    Drawable processDrawable = progressDrawable.findDrawableByLayerId(android.R.id.progress);
                    processDrawable.setColorFilter(
                            Color.parseColor("#" + timeslider.getString("progress")),
                            PorterDuff.Mode.SRC_IN);
                }

                if (timeslider.hasKey("buffer")) {
                    Drawable secondaryProgressDrawable = progressDrawable
                            .findDrawableByLayerId(android.R.id.secondaryProgress);
                    secondaryProgressDrawable.setColorFilter(
                            Color.parseColor("#" + timeslider.getString("buffer")),
                            PorterDuff.Mode.SRC_IN);
                }

                if (timeslider.hasKey("rail")) {
                    Drawable backgroundDrawable = progressDrawable.findDrawableByLayerId(android.R.id.background);
                    backgroundDrawable.setColorFilter(
                            Color.parseColor("#" + timeslider.getString("rail")),
                            PorterDuff.Mode.SRC_IN);
                }

                if (timeslider.hasKey("thumb")) {
                    seekBar.getThumb().setColorFilter(
                            Color.parseColor("#" + timeslider.getString("thumb")),
                            PorterDuff.Mode.SRC_IN);
                }
            }
        }
    }

    // Audio Focus

    public void requestAudioFocus() {
        JWLog.d(TAG, "requestAudioFocus() apiLevel=" + Build.VERSION.SDK_INT + ", hasAudioFocus=" + hasAudioFocus);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (hasAudioFocus) {
                return;
            }

            if (audioManager != null) {
                AudioAttributes playbackAttributes = new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC) // CONTENT_TYPE_SPEECH
                        .build();
                focusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                        .setAudioAttributes(playbackAttributes)
                        .setAcceptsDelayedFocusGain(true)
                        // .setWillPauseWhenDucked(true)
                        .setOnAudioFocusChangeListener(this)
                        .build();

                int res = audioManager.requestAudioFocus(focusRequest);
                JWLog.d(TAG, "requestAudioFocus result=" + res);
                synchronized (focusLock) {
                    if (res == AudioManager.AUDIOFOCUS_REQUEST_FAILED) {
                        playbackNowAuthorized = false;
                    } else if (res == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                        playbackNowAuthorized = true;
                        hasAudioFocus = true;
                    } else if (res == AudioManager.AUDIOFOCUS_REQUEST_DELAYED) {
                        playbackDelayed = true;
                        playbackNowAuthorized = false;
                    }
                }
            }
        } else {
            int result = 0;
            if (audioManager != null) {
                if (hasAudioFocus) {
                    return;
                }

                result = audioManager.requestAudioFocus(this,
                        // Use the music stream.
                        AudioManager.STREAM_MUSIC,
                        // Request permanent focus.
                        AudioManager.AUDIOFOCUS_GAIN);
            }
            JWLog.d(TAG, "requestAudioFocus (legacy) result=" + result);
            if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                hasAudioFocus = true;
            }
        }
    }


    public void lowerApiOnAudioFocus(int focusChange) {
        JWLog.d(TAG, "lowerApiOnAudioFocus(focusChange=" + focusChange + ")");
        if (mPlayer != null) {
            int initVolume = mPlayer.getVolume();

            switch (focusChange) {
                case AudioManager.AUDIOFOCUS_GAIN:
                    if (!userPaused) {
                        setVolume(initVolume);

                        boolean autostart = mPlayer.getConfig().getAutostart();
                        if (autostart) {
                            mPlayer.play();
                        }
                    }
                    break;
                case AudioManager.AUDIOFOCUS_LOSS:
                    mPlayer.pause();
                    wasInterrupted = true;
                    hasAudioFocus = false;
                    break;
                case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
                    mPlayer.pause();
                    wasInterrupted = true;
                    break;
                case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
                    setVolume(initVolume / 2);
                    break;
            }
        }
    }

    public void onAudioFocusChange(int focusChange) {
        JWLog.d(TAG, "onAudioFocusChange(focusChange=" + focusChange + ")");
        if (mPlayer != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                int initVolume = mPlayer.getVolume();

                switch (focusChange) {
                    case AudioManager.AUDIOFOCUS_GAIN:
                        if (playbackDelayed || !userPaused) {
                            synchronized (focusLock) {
                                playbackDelayed = false;
                            }

                            setVolume(initVolume);

                            boolean autostart = mPlayer.getConfig().getAutostart();
                            if (autostart) {
                                mPlayer.play();
                            }
                        }
                        break;
                    case AudioManager.AUDIOFOCUS_LOSS:
                        mPlayer.pause();
                        synchronized (focusLock) {
                            wasInterrupted = true;
                            playbackDelayed = false;
                        }
                        hasAudioFocus = false;
                        break;
                    case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
                        mPlayer.pause();
                        synchronized (focusLock) {
                            wasInterrupted = true;
                            playbackDelayed = false;
                        }
                        break;
                    case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
                        setVolume(initVolume / 2);
                        break;
                }
            } else {
                lowerApiOnAudioFocus(focusChange);
            }
        }
    }

    private void setVolume(int volume) {
        JWLog.d(TAG, "setVolume(volume=" + volume + ") mute=" + mPlayer.getMute());
        if (!mPlayer.getMute()) {
            mPlayer.setVolume(volume);
        }
    }

    private void updateWakeLock(boolean enable) {
        JWLog.d(TAG, "updateWakeLock(enable=" + enable + ", isInBackground=" + isInBackground + ")");
        if (mWindow != null) {
            if (enable && !isInBackground) {
                mWindow.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            } else {
                mWindow.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            }
        }
    }

    // Ad events

    @Override
    public void onAdLoaded(AdLoadedEvent adLoadedEvent) {
        JWLog.d(TAG, "onAdLoaded(client=" + Util.getAdEventClientValue(adLoadedEvent) + ")");
        WritableMap event = Arguments.createMap();
        event.putString("message", "onAdEvent");
        event.putInt("client", Util.getAdEventClientValue(adLoadedEvent));
        getReactContext().getJSModule(RCTEventEmitter.class).receiveEvent(getId(), "topAdEvent", event);
    }

    @Override
    public void onAdLoadedXml(AdLoadedXmlEvent adLoadedXmlEvent) {
        JWLog.d(TAG, "onAdLoadedXml(client=" + Util.getAdEventClientValue(adLoadedXmlEvent) + ")");
        WritableMap event = Arguments.createMap();
        event.putString("message", "onAdEvent");
        event.putInt("client", Util.getAdEventClientValue(adLoadedXmlEvent));
        getReactContext().getJSModule(RCTEventEmitter.class).receiveEvent(getId(), "topAdEvent", event);
    }

    @Override
    public void onAdPause(AdPauseEvent adPauseEvent) {
        JWLog.d(TAG, "onAdPause(reason=" + adPauseEvent.getAdPauseReason() + ")");
        WritableMap event = Arguments.createMap();
        event.putString("message", "onAdEvent");
        event.putString("reason", adPauseEvent.getAdPauseReason().toString());
        event.putInt("client", Util.getAdEventClientValue(adPauseEvent));
        event.putInt("type", Util.getAdEventTypeValue(Util.AdEventType.JWAdEventTypePause));
        getReactContext().getJSModule(RCTEventEmitter.class).receiveEvent(getId(), "topAdEvent", event);
    }

    @Override
    public void onAdPlay(AdPlayEvent adPlayEvent) {
        JWLog.d(TAG, "onAdPlay(reason=" + adPlayEvent.getAdPlayReason() + ")");
        WritableMap event = Arguments.createMap();
        event.putString("message", "onAdEvent");
        event.putString("reason", adPlayEvent.getAdPlayReason().toString());
        event.putInt("client", Util.getAdEventClientValue(adPlayEvent));
        event.putInt("type", Util.getAdEventTypeValue(Util.AdEventType.JWAdEventTypePlay));
        getReactContext().getJSModule(RCTEventEmitter.class).receiveEvent(getId(), "topAdEvent", event);
    }

    @Override
    public void onAdBreakEnd(AdBreakEndEvent adBreakEndEvent) {
        JWLog.d(TAG, "onAdBreakEnd(client=" + Util.getAdEventClientValue(adBreakEndEvent) + ")");
        WritableMap event = Arguments.createMap();
        event.putString("message", "onAdEvent");
        event.putInt("client", Util.getAdEventClientValue(adBreakEndEvent));
        event.putInt("type", Util.getAdEventTypeValue(Util.AdEventType.JWAdEventTypeAdBreakEnd));
        getReactContext().getJSModule(RCTEventEmitter.class).receiveEvent(getId(), "topAdEvent", event);
    }

    @Override
    public void onAdBreakStart(AdBreakStartEvent adBreakStartEvent) {
        JWLog.d(TAG, "onAdBreakStart(client=" + Util.getAdEventClientValue(adBreakStartEvent) + ")");
        WritableMap event = Arguments.createMap();
        event.putString("message", "onAdEvent");
        event.putInt("client", Util.getAdEventClientValue(adBreakStartEvent));
        event.putInt("type", Util.getAdEventTypeValue(Util.AdEventType.JWAdEventTypeAdBreakStart));
        getReactContext().getJSModule(RCTEventEmitter.class).receiveEvent(getId(), "topAdEvent", event);
    }

    @Override
    public void onAdBreakIgnored(AdBreakIgnoredEvent adBreakIgnoredEvent) {
        JWLog.d(TAG, "onAdBreakIgnored(client=" + Util.getAdEventClientValue(adBreakIgnoredEvent) + ")");
        WritableMap event = Arguments.createMap();
        event.putString("message", "onAdEvent");
        event.putInt("client", Util.getAdEventClientValue(adBreakIgnoredEvent));
        // missing type code
        getReactContext().getJSModule(RCTEventEmitter.class).receiveEvent(getId(), "topAdEvent", event);
    }

    @Override
    public void onAdClick(AdClickEvent adClickEvent) {
        JWLog.d(TAG, "onAdClick(client=" + Util.getAdEventClientValue(adClickEvent) + ")");
        WritableMap event = Arguments.createMap();
        event.putString("message", "onAdEvent");
        event.putInt("client", Util.getAdEventClientValue(adClickEvent));
        event.putInt("type", Util.getAdEventTypeValue(Util.AdEventType.JWAdEventTypeClicked));
        getReactContext().getJSModule(RCTEventEmitter.class).receiveEvent(getId(), "topAdEvent", event);
    }

    @Override
    public void onAdCompanions(AdCompanionsEvent adCompanionsEvent) {
        JWLog.d(TAG, "onAdCompanions(client=" + Util.getAdEventClientValue(adCompanionsEvent) + ")");
        WritableMap event = Arguments.createMap();
        event.putString("message", "onAdEvent");
        event.putInt("client", Util.getAdEventClientValue(adCompanionsEvent));
        event.putInt("type", Util.getAdEventTypeValue(Util.AdEventType.JWAdEventTypeCompanion));
        getReactContext().getJSModule(RCTEventEmitter.class).receiveEvent(getId(), "topAdEvent", event);
    }

    @Override
    public void onAdComplete(AdCompleteEvent adCompleteEvent) {
        JWLog.d(TAG, "onAdComplete(client=" + Util.getAdEventClientValue(adCompleteEvent) + ")");
        WritableMap event = Arguments.createMap();
        event.putString("message", "onAdEvent");
        event.putInt("client", Util.getAdEventClientValue(adCompleteEvent));
        event.putInt("type", Util.getAdEventTypeValue(Util.AdEventType.JWAdEventTypeComplete));
        getReactContext().getJSModule(RCTEventEmitter.class).receiveEvent(getId(), "topAdEvent", event);
    }

    @Override
    public void onAdError(AdErrorEvent adErrorEvent) {
        JWLog.d(TAG, "onAdError(code=" + adErrorEvent.getCode() + ", adErrorCode=" + adErrorEvent.getAdErrorCode() + ", message=" + adErrorEvent.getMessage() + ")");
        WritableMap event = Arguments.createMap();
        event.putString("message", "onPlayerAdError");
        event.putInt("code", adErrorEvent.getCode());
        event.putInt("adErrorCode", adErrorEvent.getAdErrorCode());
        event.putString("error", adErrorEvent.getMessage());
        getReactContext().getJSModule(RCTEventEmitter.class).receiveEvent(getId(), "topPlayerAdError", event);
    }

    @Override
    public void onAdWarning(AdWarningEvent adWarningEvent) {
        JWLog.d(TAG, "onAdWarning(code=" + adWarningEvent.getCode() + ", adErrorCode=" + adWarningEvent.getAdErrorCode() + ")");
        WritableMap event = Arguments.createMap();
        event.putString("message", "onPlayerAdWarning");
        event.putInt("code", adWarningEvent.getCode());
        event.putInt("adErrorCode", adWarningEvent.getAdErrorCode());
        event.putString("warning", adWarningEvent.getMessage());
        getReactContext().getJSModule(RCTEventEmitter.class).receiveEvent(getId(), "topPlayerAdWarning", event);
    }

    @Override
    public void onAdImpression(AdImpressionEvent adImpressionEvent) {
        JWLog.d(TAG, "onAdImpression(client=" + Util.getAdEventClientValue(adImpressionEvent) + ")");
        WritableMap event = Arguments.createMap();
        event.putString("message", "onAdEvent");
        event.putInt("client", Util.getAdEventClientValue(adImpressionEvent));
        event.putInt("type", Util.getAdEventTypeValue(Util.AdEventType.JWAdEventTypeImpression));
        getReactContext().getJSModule(RCTEventEmitter.class).receiveEvent(getId(), "topAdEvent", event);
    }

    @Override
    public void onAdMeta(AdMetaEvent adMetaEvent) {
        JWLog.d(TAG, "onAdMeta(client=" + Util.getAdEventClientValue(adMetaEvent) + ")");
        WritableMap event = Arguments.createMap();
        event.putString("message", "onAdEvent");
        event.putInt("client", Util.getAdEventClientValue(adMetaEvent));
        event.putInt("type", Util.getAdEventTypeValue(Util.AdEventType.JWAdEventTypeMeta));
        getReactContext().getJSModule(RCTEventEmitter.class).receiveEvent(getId(), "topAdEvent", event);
    }

    @Override
    public void onAdRequest(AdRequestEvent adRequestEvent) {
        JWLog.d(TAG, "onAdRequest(client=" + Util.getAdEventClientValue(adRequestEvent) + ")");
        WritableMap event = Arguments.createMap();
        event.putString("message", "onAdEvent");
        event.putInt("client", Util.getAdEventClientValue(adRequestEvent));
        event.putInt("type", Util.getAdEventTypeValue(Util.AdEventType.JWAdEventTypeRequest));
        getReactContext().getJSModule(RCTEventEmitter.class).receiveEvent(getId(), "topAdEvent", event);
    }

    @Override
    public void onAdSchedule(AdScheduleEvent adScheduleEvent) {
        JWLog.d(TAG, "onAdSchedule(client=" + Util.getAdEventClientValue(adScheduleEvent) + ")");
        WritableMap event = Arguments.createMap();
        event.putString("message", "onAdEvent");
        event.putInt("client", Util.getAdEventClientValue(adScheduleEvent));
        event.putInt("type", Util.getAdEventTypeValue(Util.AdEventType.JWAdEventTypeSchedule));
        getReactContext().getJSModule(RCTEventEmitter.class).receiveEvent(getId(), "topAdEvent", event);
    }

    @Override
    public void onAdSkipped(AdSkippedEvent adSkippedEvent) {
        JWLog.d(TAG, "onAdSkipped(client=" + Util.getAdEventClientValue(adSkippedEvent) + ")");
        WritableMap event = Arguments.createMap();
        event.putString("message", "onAdEvent");
        event.putInt("client", Util.getAdEventClientValue(adSkippedEvent));
        event.putInt("type", Util.getAdEventTypeValue(Util.AdEventType.JWAdEventTypeSkipped));
        getReactContext().getJSModule(RCTEventEmitter.class).receiveEvent(getId(), "topAdEvent", event);
    }

    @Override
    public void onAdStarted(AdStartedEvent adStartedEvent) {
        JWLog.d(TAG, "onAdStarted(client=" + Util.getAdEventClientValue(adStartedEvent) + ")");
        WritableMap event = Arguments.createMap();
        event.putString("message", "onAdEvent");
        event.putInt("client", Util.getAdEventClientValue(adStartedEvent));
        event.putInt("type", Util.getAdEventTypeValue(Util.AdEventType.JWAdEventTypeStarted));
        getReactContext().getJSModule(RCTEventEmitter.class).receiveEvent(getId(), "topAdEvent", event);
    }

    @Override
    public void onAdTime(AdTimeEvent adTimeEvent) {
        JWLog.d(TAG, "onAdTime(position=" + adTimeEvent.getPosition() + ", duration=" + adTimeEvent.getDuration() + ")");
        WritableMap event = Arguments.createMap();
        event.putString("message", "onAdTime");
        event.putDouble("position", adTimeEvent.getPosition());
        event.putDouble("duration", adTimeEvent.getDuration());
        getReactContext().getJSModule(RCTEventEmitter.class).receiveEvent(getId(), "topAdTime", event);
    }

    @Override
    public void onAdViewableImpression(AdViewableImpressionEvent adViewableImpressionEvent) {
        JWLog.d(TAG, "onAdViewableImpression()");
        // send everything?
    }

    @Override
    public void onBeforeComplete(BeforeCompleteEvent beforeCompleteEvent) {
        JWLog.d(TAG, "onBeforeComplete()");
        WritableMap event = Arguments.createMap();
        event.putString("message", "onBeforeComplete");
        getReactContext().getJSModule(RCTEventEmitter.class).receiveEvent(getId(), "topBeforeComplete", event);

        updateWakeLock(false);
    }

    @Override
    public void onBeforePlay(BeforePlayEvent beforePlayEvent) {
        JWLog.d(TAG, "onBeforePlay()");
        // Ideally done in onFirstFrame instead
        // if (backgroundAudioEnabled) {
        //     doBindService();
        // }

        WritableMap event = Arguments.createMap();
        event.putString("message", "onBeforePlay");
        getReactContext().getJSModule(RCTEventEmitter.class).receiveEvent(getId(), "topBeforePlay", event);
    }

    // Audio Events

    @Override
    public void onAudioTracks(AudioTracksEvent audioTracksEvent) {
        JWLog.d(TAG, "onAudioTracks() count=" + (audioTracksEvent.getAudioTracks() != null ? audioTracksEvent.getAudioTracks().size() : 0));
        WritableMap event = Arguments.createMap();
        event.putString("message", "onAudioTracks");
        getReactContext().getJSModule(RCTEventEmitter.class).receiveEvent(getId(), "topAudioTracks", event);
    }

    @Override
    public void onAudioTrackChanged(AudioTrackChangedEvent audioTrackChangedEvent) {
        JWLog.d(TAG, "onAudioTrackChanged(index=" + audioTrackChangedEvent.getCurrentTrack() + ")");

    }

    // Captions Events

    @Override
    public void onCaptionsChanged(CaptionsChangedEvent captionsChangedEvent) {
        JWLog.d(TAG, "onCaptionsChanged(index=" + captionsChangedEvent.getCurrentTrack() + ")");
        WritableMap event = Arguments.createMap();
        event.putString("message", "onCaptionsChanged");
        event.putInt("index", captionsChangedEvent.getCurrentTrack());
        getReactContext().getJSModule(RCTEventEmitter.class).receiveEvent(getId(), "topCaptionsChanged", event);
    }

    @Override
    public void onCaptionsList(CaptionsListEvent captionsListEvent) {
        JWLog.d(TAG, "onCaptionsList(count=" + (captionsListEvent.getCaptions() != null ? captionsListEvent.getCaptions().size() : 0) + ")");
        WritableMap event = Arguments.createMap();
        List<Caption> captionTrackList = captionsListEvent.getCaptions();
        WritableArray captionTracks = Arguments.createArray();
        if (captionTrackList != null) {
            for(int i = 0; i < captionTrackList.size(); i++) {
                WritableMap captionTrack = Arguments.createMap();
                Caption track = captionTrackList.get(i);
                captionTrack.putString("file", track.getFile());
                captionTrack.putString("label", track.getLabel());
                captionTrack.putBoolean("default", track.isDefault());
                captionTracks.pushMap(captionTrack);
            }
        }
        event.putString("message", "onCaptionsList");
        event.putInt("index", captionsListEvent.getCurrentCaptionIndex());
        event.putArray("tracks", captionTracks);
        getReactContext().getJSModule(RCTEventEmitter.class).receiveEvent(getId(), "topCaptionsList", event);

    }

    // Player Events

    @Override
    public void onBuffer(BufferEvent bufferEvent) {
        JWLog.d(TAG, "onBuffer()");
        WritableMap event = Arguments.createMap();
        event.putString("message", "onBuffer");
        getReactContext().getJSModule(RCTEventEmitter.class).receiveEvent(getId(), "topBuffer", event);

        updateWakeLock(true);
    }

    @Override
    public void onComplete(CompleteEvent completeEvent) {
        JWLog.d(TAG, "onComplete()");
        WritableMap event = Arguments.createMap();
        event.putString("message", "onComplete");
        getReactContext().getJSModule(RCTEventEmitter.class).receiveEvent(getId(), "topComplete", event);

        updateWakeLock(false);
    }

    @Override
    public void onControlBarVisibilityChanged(ControlBarVisibilityEvent controlBarVisibilityEvent) {
        JWLog.d(TAG, "onControlBarVisibilityChanged(visible=" + controlBarVisibilityEvent.isVisible() + ")");
        WritableMap event = Arguments.createMap();
        event.putString("message", "onControlBarVisible");
        event.putBoolean("visible", controlBarVisibilityEvent.isVisible());
        getReactContext().getJSModule(RCTEventEmitter.class).receiveEvent(getId(), "topControlBarVisible", event);

        updateWakeLock(true);
    }

    @Override
    public void onControls(ControlsEvent controlsEvent) {
        JWLog.d(TAG, "onControls()");

    }

    @Override
    public void onDisplayClick(DisplayClickEvent displayClickEvent) {
        JWLog.d(TAG, "onDisplayClick()");
        com.jwplayer.rnjwplayer.session.RNJWMediaSessionHelper.noteUserPlaybackGesture("display-click");

    }

    @Override
    public void onError(ErrorEvent errorEvent) {
        JWLog.d(TAG, "onError(code=" + errorEvent.getErrorCode() + ", message=" + errorEvent.getMessage() + ")");
        WritableMap event = Arguments.createMap();
        event.putString("message", "onError");
        Exception ex = errorEvent.getException();
        if (ex != null) {
            event.putString("error", ex.toString());
            event.putString("description", errorEvent.getMessage());
            event.putInt("errorCode", errorEvent.getErrorCode());
        }
        getReactContext().getJSModule(RCTEventEmitter.class).receiveEvent(getId(), "topPlayerError", event);

        updateWakeLock(false);
    }

    @Override
    public void onFirstFrame(FirstFrameEvent firstFrameEvent) {
        JWLog.d(TAG, "onFirstFrame(loadTime=" + firstFrameEvent.getLoadTime() + ")");
        if (backgroundAudioEnabled) {
            doBindService();
            requestAudioFocus();
        }
        WritableMap onFirstFrame = Arguments.createMap();
        onFirstFrame.putString("message", "onLoaded");
        onFirstFrame.putDouble("loadTime", firstFrameEvent.getLoadTime());
        getReactContext().getJSModule(RCTEventEmitter.class).receiveEvent(
                getId(),
                "topFirstFrame",
                onFirstFrame);
    }

    @Override
    public void onFullscreen(FullscreenEvent fullscreenEvent) {
        JWLog.d(TAG, "onFullscreen(fullscreen=" + fullscreenEvent.getFullscreen() + ")");
        if (fullscreenEvent.getFullscreen()) {
            if (mPlayerView != null) {
                mPlayerView.requestFocus();
            }

            WritableMap eventExitFullscreen = Arguments.createMap();
            eventExitFullscreen.putString("message", "onFullscreen");
            getReactContext().getJSModule(RCTEventEmitter.class).receiveEvent(
                    getId(),
                    "topFullScreen",
                    eventExitFullscreen);
        } else {
            WritableMap eventExitFullscreen = Arguments.createMap();
            eventExitFullscreen.putString("message", "onFullscreenExit");
            getReactContext().getJSModule(RCTEventEmitter.class).receiveEvent(
                    getId(),
                    "topFullScreenExit",
                    eventExitFullscreen);
        }
    }

    @Override
    public void onIdle(IdleEvent idleEvent) {
        JWLog.d(TAG, "onIdle()");

    }

    @Override
    public void onPause(PauseEvent pauseEvent) {
        JWLog.d(TAG, "onPause()", true);
        WritableMap event = Arguments.createMap();
        event.putString("message", "onPause");
        getReactContext().getJSModule(RCTEventEmitter.class).receiveEvent(getId(), "topPause", event);

        updateWakeLock(false);

        if (!wasInterrupted) {
            userPaused = true;
        }
    }

    @Override
    public void onPlay(PlayEvent playEvent) {
        JWLog.d(TAG, "onPlay()");

        if (backgroundAudioEnabled) {
            requestAudioFocus();
        }

        WritableMap event = Arguments.createMap();
        event.putString("message", "onPlay");
        getReactContext().getJSModule(RCTEventEmitter.class).receiveEvent(getId(), "topPlay", event);

        updateWakeLock(true);

        userPaused = false;
        wasInterrupted = false;
    }

    @Override
    public void onPlaylistComplete(PlaylistCompleteEvent playlistCompleteEvent) {
        JWLog.d(TAG, "onPlaylistComplete()");
        WritableMap event = Arguments.createMap();
        event.putString("message", "onPlaylistComplete");
        getReactContext().getJSModule(RCTEventEmitter.class).receiveEvent(getId(), "topPlaylistComplete", event);

        updateWakeLock(false);
    }

    @Override
    public void onPlaylistItem(PlaylistItemEvent playlistItemEvent) {
        JWLog.d(TAG, "onPlaylistItem(index=" + playlistItemEvent.getIndex() + ")");
        // Ideally done in onFirstFrame instead
        // if (backgroundAudioEnabled) {
        //     doBindService();
        // }

        currentPlayingIndex = playlistItemEvent.getIndex();

        WritableMap event = Arguments.createMap();
        event.putString("message", "onPlaylistItem");
        event.putInt("index", playlistItemEvent.getIndex());
        Gson gson = new Gson();
        String json = gson.toJson(playlistItemEvent.getPlaylistItem());
        JWLog.d(TAG, "PlaylistItem JSON: " + json);
        event.putString("playlistItem", json);
        getReactContext().getJSModule(RCTEventEmitter.class).receiveEvent(getId(), "topPlaylistItem", event);
    }

    @Override
    public void onPlaylist(PlaylistEvent playlistEvent) {
        JWLog.d(TAG, "onPlaylist()");

    }

    @Override
    public void onReady(ReadyEvent readyEvent) {
        JWLog.d(TAG, "onReady()");
        WritableMap event = Arguments.createMap();
        event.putString("message", "onPlayerReady");
        getReactContext().getJSModule(RCTEventEmitter.class).receiveEvent(getId(), "topOnPlayerReady", event);

        updateWakeLock(true);
    }

    @Override
    public void onSeek(SeekEvent seekEvent) {
        JWLog.d(TAG, "onSeek(position=" + seekEvent.getPosition() + ", offset=" + seekEvent.getOffset() + ")");
        WritableMap event = Arguments.createMap();
        event.putString("message", "onSeek");
        event.putDouble("position", seekEvent.getPosition());
        event.putDouble("offset", seekEvent.getOffset());
        getReactContext().getJSModule(RCTEventEmitter.class).receiveEvent(getId(), "topSeek", event);
    }

    @Override
    public void onSeeked(SeekedEvent seekedEvent) {
        JWLog.d(TAG, "onSeeked(position=" + seekedEvent.getPosition() + ")");
        WritableMap event = Arguments.createMap();
        event.putString("message", "onSeeked");
        event.putDouble("position", seekedEvent.getPosition());
        getReactContext().getJSModule(RCTEventEmitter.class).receiveEvent(getId(), "topSeeked", event);
    }

    @Override
    public void onPlaybackRateChanged(PlaybackRateChangedEvent playbackRateChangedEvent) {
        JWLog.d(TAG, "onPlaybackRateChanged(rate=" + playbackRateChangedEvent.getPlaybackRate() + ")");

        // Keep MediaSession speed in sync so Android Auto icon updates immediately
        // when speed is changed from the phone app UI (which bypasses MediaBrowserService).
        float newRate = (float) playbackRateChangedEvent.getPlaybackRate();
        if (newRate > 0) {
            com.jwplayer.rnjwplayer.session.RNJWMediaSessionHelper.currentSpeed = newRate;
            try {
                Class<?> mbsClass = Class.forName("com.mediabrowser.MediaBrowserService");
                java.lang.reflect.Method setSpeed = mbsClass.getMethod("setPlaybackSpeedFromSync", float.class);
                setSpeed.invoke(null, newRate);
            } catch (Exception ignored) {}
        }

        WritableMap event = Arguments.createMap();
        event.putString("message", "onRateChanged");
        event.putDouble("rate", playbackRateChangedEvent.getPlaybackRate());
        getReactContext().getJSModule(RCTEventEmitter.class).receiveEvent(getId(), "topRateChanged", event);
    }

    @Override
    public void onSetupError(SetupErrorEvent setupErrorEvent) {
        JWLog.d(TAG, "onSetupError(code=" + setupErrorEvent.getCode() + ", message=" + setupErrorEvent.getMessage() + ")");
        WritableMap event = Arguments.createMap();
        event.putString("message", "onSetupError");
        event.putString("errorMessage", setupErrorEvent.getMessage());
        event.putInt("errorCode", setupErrorEvent.getCode());
        getReactContext().getJSModule(RCTEventEmitter.class).receiveEvent(getId(), "topSetupPlayerError", event);

        updateWakeLock(false);
    }

    @Override
    public void onTime(TimeEvent timeEvent) {
        // JWLog.d(TAG, "onTime(position=" + timeEvent.getPosition() + ", duration=" + timeEvent.getDuration() + ")");
        WritableMap event = Arguments.createMap();
        event.putString("message", "onTime");
        event.putDouble("position", timeEvent.getPosition());
        event.putDouble("duration", timeEvent.getDuration());
        getReactContext().getJSModule(RCTEventEmitter.class).receiveEvent(getId(), "topTime", event);
    }

    @Override
    public void onMeta(MetaEvent metaEvent) {
        JWLog.d(TAG, "onMeta()");

    }

    // Picture in Picture events

    @Override
    public void onPipClose(PipCloseEvent pipCloseEvent) {
        JWLog.d(TAG, "onPipClose()");

    }

    @Override
    public void onPipOpen(PipOpenEvent pipOpenEvent) {
        JWLog.d(TAG, "onPipOpen()");

    }

    // Casting events

    private boolean mIsCastActive = false;

    /**
     * Get if this player-view is currently casting
     *
     * @return true if casting
     */
    public boolean getIsCastActive() {
        JWLog.d(TAG, "getIsCastActive() -> " + mIsCastActive);
        return mIsCastActive;
    }

    @Override
    public void onCast(CastEvent castEvent) {
        JWLog.d(TAG, "onCast(device=" + castEvent.getDeviceName() + ", active=" + castEvent.isActive() + ")");
        WritableMap event = Arguments.createMap();
        event.putString("message", "onCasting");
        event.putString("device", castEvent.getDeviceName());
        event.putBoolean("active", castEvent.isActive());
        event.putBoolean("available", castEvent.isAvailable());
        getReactContext().getJSModule(RCTEventEmitter.class).receiveEvent(getId(), "topCasting", event);
        mIsCastActive = castEvent.isActive();
        // stop/start the background audio service if it's running and we're casting
        if (castEvent.isActive()) {
            doUnbindService();
        } else {
            if (backgroundAudioEnabled) {
                Context simpleContext = getNonBuggyContext(getReactContext(), getAppContext());
                ServiceMediaApi serviceMediaApi = new ServiceMediaApi(mPlayer);
                com.jwplayer.rnjwplayer.session.RNJWNotificationHelper notificationHelper = new com.jwplayer.rnjwplayer.session.RNJWNotificationHelper.Builder(simpleContext, (NotificationManager) mActivity.getSystemService(Context.NOTIFICATION_SERVICE)).build();
                com.jwplayer.rnjwplayer.session.RNJWMediaSessionHelper rNJWMediaSessionHelper = new com.jwplayer.rnjwplayer.session.RNJWMediaSessionHelper(simpleContext, notificationHelper, serviceMediaApi);
                mMediaServiceController = new RNJWMediaServiceController.Builder(mActivity, mPlayer)
                        .serviceMediaApi(serviceMediaApi)
                        .mediaSessionHelper(rNJWMediaSessionHelper)
                        .notificationHelper(notificationHelper)
                        .build();

                doBindService();
            }
        }
    }

    // LifecycleEventListener

    @Override
    public void onHostResume() {
        JWLog.d(TAG, "onHostResume()");
        sessionDepth++;
        if (sessionDepth == 1) {
            isInBackground = false;
        }

        JWLog.d(TAG, "onHostResume() sessionDepth=" + sessionDepth + ", isInBackground=" + isInBackground);
        // Notify playback routing that UI is foregrounded again
        try {
            PlaybackManager.getInstance().setUiInBackground(false);
        } catch (Throwable t) {
            JWLog.w(TAG, "Failed to notify PlaybackManager of foreground: " + t.getMessage());
        }
    }

    @Override
    public void onHostPause() {
        JWLog.d(TAG, "onHostPause()");
        if (sessionDepth > 0)
            sessionDepth--;
        if (sessionDepth == 0) {
            isInBackground = true;
        }

        JWLog.d(TAG, "onHostPause() sessionDepth=" + sessionDepth + ", isInBackground=" + isInBackground);
        // Notify playback routing that UI is backgrounded
        try {
            PlaybackManager.getInstance().setUiInBackground(true);
        } catch (Throwable t) {
            JWLog.w(TAG, "Failed to notify PlaybackManager of background: " + t.getMessage());
        }
    }

    @Override
    public void onHostDestroy() {
        JWLog.d(TAG, "onHostDestroy()");
        this.destroyPlayer();
    }

    // utils
    private final Map<String, Integer> CLIENT_TYPES = MapBuilder.of(
            "vast", 0,
            "ima", 1,
            "ima_dai", 2);

    private final Map<String, UiGroup> GROUP_TYPES = ImmutableMap.<String, UiGroup>builder()
            .put("overlay", UiGroup.OVERLAY)
            .put("control_bar", UiGroup.CONTROLBAR)
            .put("center_controls", UiGroup.CENTER_CONTROLS)
            .put("next_up", UiGroup.NEXT_UP)
            .put("error", UiGroup.ERROR)
            .put("playlist", UiGroup.PLAYLIST)
            .put("controls_container", UiGroup.PLAYER_CONTROLS_CONTAINER)
            .put("settings_menu", UiGroup.SETTINGS_MENU)
            .put("quality_submenu", UiGroup.SETTINGS_QUALITY_SUBMENU)
            .put("captions_submenu", UiGroup.SETTINGS_CAPTIONS_SUBMENU)
            .put("playback_submenu", UiGroup.SETTINGS_PLAYBACK_SUBMENU)
            .put("audiotracks_submenu", UiGroup.SETTINGS_AUDIOTRACKS_SUBMENU)
            .put("casting_menu", UiGroup.CASTING_MENU).build();
}
