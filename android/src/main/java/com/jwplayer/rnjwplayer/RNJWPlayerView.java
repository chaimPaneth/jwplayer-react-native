package com.jwplayer.rnjwplayer;


import android.app.Activity;
import android.app.ActivityManager;
import android.app.NotificationManager;
import android.app.PictureInPictureParams;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.os.Build;
import android.os.SystemClock;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.View.MeasureSpec;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.Gravity;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
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
import com.jwplayer.pub.api.events.PlaylistItemMetadataChangedEvent;
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
import com.jwplayer.pub.api.media.playlists.MediaSource;
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
        VideoPlayerEvents.OnPlaylistItemMetadataChangedListener,
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
    private long mMediaGeneration = 0L;
    private Consumer<PictureInPictureModeChangedInfo> mPipListener = null;
    private Boolean mLastHandledPipState = null;
    private OnBackPressedCallback mPipBackCallback = null;
    // Remembers the player's controls-enabled state from just before PiP entry so it
    // can be restored on exit. Null when not in PiP / when this view did not own the
    // PiP transition.
    private Boolean mControlsBeforePip = null;
    // Thin progress line drawn inside the PiP window. Separate from JW's control bar, which is
    // fully hidden for the duration of PiP because it renders at unscaled size in the small
    // window. See showPipProgress().
    private PipProgressView mPipProgressView = null;
    private Runnable mPipProgressTick = null;
    private static final long PIP_PROGRESS_TICK_MS = 500L;
    private static final int PIP_PROGRESS_HEIGHT_DP = 3;
    // Visibility of JW's own UI overlay views as captured on PiP entry, so it can be put back on
    // exit. Populated by hideJwUiForPip(), which is the actual enforcement for "no JW control
    // overlay inside the PiP window" — the SDK's controls flag proved insufficient.
    private final Map<View, Integer> mJwUiVisibilitySnapshot = new LinkedHashMap<>();
    private ViewTreeObserver.OnGlobalLayoutListener mPipUiEnforcer = null;
    // ── Foreground-rebuild playhead snapshot ────────────────────────────────────────────────
    // The live playhead captured the moment the app comes back to the foreground (PiP exit /
    // unlock / background return), BEFORE the RN layer re-pushes its declarative config. On that
    // return the app re-sends the playlist for the track that is already playing, and the
    // playlist-only fast path in setConfig() rebuilds the player at the item's starttime — so a
    // starttime that is behind the real playhead rewinds playback. See
    // resolveForegroundRebuildStartOverrideSec() for why the JS-supplied value cannot be trusted
    // here and why native is the only layer that can tell.
    private String mForegroundRebuildFile = null;
    private long mForegroundRebuildPositionMs = -1L;
    private long mForegroundRebuildCapturedAtMs = 0L;
    // The snapshot is only meant to cover the config burst that immediately follows the
    // foreground transition (observed: three pushes within ~85ms of onHostResume). Anything later
    // is an ordinary update and must be honoured as sent.
    private static final long FOREGROUND_REBUILD_SNAPSHOT_TTL_MS = 15_000L;
    // Ignore sub-second disagreement: the SDK's own resume rounding routinely differs from the
    // requested start by a few hundred ms and that is not a rewind.
    private static final long FOREGROUND_REBUILD_MIN_REWIND_MS = 2_000L;
    // Identity of the item the player is on, and the one before it. Maintained from the SDK's
    // own playlist-item events, so it reflects native/SDK advances the RN layer never saw.
    private String mCurrentItemFile = null;
    private String mPreviousItemFile = null;
    // A stale revert is only credible as part of the foreground config burst itself (measured at
    // ~0.5s after the transition). Kept much tighter than the rewind TTL so a real user tap made
    // seconds after returning to the app is never mistaken for stale state.
    private static final long FOREGROUND_REVERT_WINDOW_MS = 6_000L;
    // Host-app opt-in (config prop "pipVideoOnly", default false): when true, Picture-in-Picture is
    // only offered for media that actually has a video track. Audio-only playback then behaves
    // like iOS — no PiP window, background playback continues through the media session /
    // notification. See updatePipRegistration().
    private boolean pipVideoOnly = false;
    // Whether the current media has a video track: TRUE once JW has positively reported video,
    // null while unproven. Reset on every new item. Never set to FALSE — this SDK emits no signal
    // that proves audio-only (see noteTrackMetadata), so "unproven" is the audio case in practice
    // and isPipAllowedForCurrentMedia() denies PiP for it while pipVideoOnly is on.
    private Boolean mHasVideoTrack = null;
    // Guards the per-item raw track-metadata log line so repeated MetaEvents do not spam it.
    private boolean mLoggedTrackMetaForItem = false;
    // Last registration state actually pushed to the SDK, so repeated meta events do not churn
    // register/deregister calls.
    private Boolean mPipRegisteredForVideo = null;
    // Mirrors the config prop "pipEnabled" so updatePipRegistration knows whether PiP is on at all.
    private boolean mPipEnabled = false;
    // Add completion handler field
    PlaylistItemDecision itemUpdatePromise = null;

    // Flag to prevent race conditions during player destruction
    private volatile boolean isDestroying = false;

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

    private void releaseMediaService(boolean transfer, String reason) {
        if (mMediaServiceController != null) {
            if (transfer) {
                mMediaServiceController.prepareForTransfer(reason);
            } else {
                mMediaServiceController.stopAndUnbind(reason);
            }
            mMediaServiceController = null;
        }
    }

    /**
     * Temporary workaround for a JW Android SDK limitation: setPlaylistItemMetadata updates
     * the MediaSessionCompat metadata but does not rebuild the foreground-service Notification,
     * so the lock-screen / shade continues to show the old title / description / poster.
     *
     * Cycling the MediaServiceController forces MediaService.doStartForeground() to run
     * NotificationHelper.createNotification() again, which reads the (already-updated) session
     * text and rebuilds the visible notification. Expect a brief notification flicker and a
     * momentary allowBackgroundAudio(false)→(true) transition while the service rebinds.
     *
     * Known limitation: the poster image is downloaded asynchronously by the SDK's internal
     * MediaSessionHelper, and cycling the service resets that helper — so the rebuilt
     * notification typically shows the previous poster. The poster refreshes on the next
     * playback state change (pause/play, seek).
     *
     * Remove this workaround once the JW Android SDK refreshes the notification natively.
     */
    void refreshBackgroundAudioNotification() {
        if (!backgroundAudioEnabled || mPlayer == null || mActivity == null) {
            return;
        }
        releaseMediaService(false, "metadata-refresh");
        setupMediaSessionHelper();
        doBindService();
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
        // Drop the PiP progress overlay and its ticker before anything else so the 2Hz poll can
        // never touch a released player.
        hidePipProgress();
        stopPipUiEnforcer();
        mJwUiVisibilitySnapshot.clear();
        // The snapshot describes a player instance that is going away; a rebuilt player must not
        // inherit it (file identity and the TTL would usually reject it, but do not rely on that).
        clearForegroundRebuildSnapshot();
        boolean replacingOwner = PlaybackManager.getInstance().isTransitioning();
        releaseMediaService(
            replacingOwner,
            replacingOwner ? "ui-player-replacement" : "ui-player-destroyed");

        if (mPlayer != null && !isDestroying) {
            isDestroying = true;

            // Disable touch events immediately to prevent race conditions
            if (mPlayerView != null) {
                Handler mainHandler = new Handler(Looper.getMainLooper());
                mainHandler.post(() -> {
                    if (mPlayerView != null) {
                        mPlayerView.setClickable(false);
                        mPlayerView.setFocusable(false);
                        mPlayerView.setEnabled(false);
                        mPlayerView.setOnTouchListener(null);
                    }
                });
            }

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
                    EventType.PLAYLIST_ITEM_METADATA_CHANGED,
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
            PlaybackManager.getInstance().setUiAudioFocus(false);

            doUnbindService();

            isDestroying = false; // Reset flag for potential reuse
        } else {
            JWLog.d(TAG, "destroyPlayer() skipped: mPlayer is null or already destroying");
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
                    EventType.PLAYLIST_ITEM_METADATA_CHANGED,
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

        // Tripwire, not a guard: if pipVideoOnly is on and PiP was entered anyway, then neither
        // withdrawing the SDK registration nor clearing autoEnterEnabled stopped it, and the entry
        // point is somewhere else entirely. Says so explicitly instead of leaving it to be inferred
        // from the absence of other log lines.
        if (isInPip && pipVideoOnly && !isPipAllowedForCurrentMedia()) {
            JWLog.w(TAG, "handlePipChange: ENTERED PiP despite pipVideoOnly gate"
                    + " (hasVideoTrack=" + mHasVideoTrack
                    + ", registeredForPip=" + mPipRegisteredForVideo
                    + ", pipEnabled=" + mPipEnabled + ")");
        }

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

        // Snapshot the real playhead BEFORE the RN layer re-pushes its config. On PiP exit the app
        // re-sends the playlist for the track already playing, and if its starttime is stale the
        // rebuild would rewind playback. See resolveForegroundRebuildStartOverrideSec().
        if (!isInPip) {
            captureForegroundRebuildPlayhead("pip-exit");
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
            JWLog.d(TAG, "applyPipChange(isInPip=" + isInPip + ") controls=" + safeGetControls()
                    + " controlsBeforePip=" + mControlsBeforePip);

            // PiP controls handling.
            //
            // Android PiP scales the host activity (and the embedded player view) down into a
            // small window; the player's control overlay shrinks with it. JW auto-hides
            // controls while playing, but when paused it keeps the full overlay visible, so in
            // the tiny PiP window it renders oversized/misaligned (the reported "wrong scale").
            // We hide controls for the duration of PiP and restore them on exit.
            //
            // ORDER MATTERS around onPictureInPictureModeChanged(): the JW SDK (re)builds its
            // control UI *inside* that call from the current getControls() state. On EXIT we
            // must re-enable controls BEFORE notifying the SDK; otherwise JW rebuilds the
            // normal-mode UI with no control bar, and a later setControls(true) does not bring
            // it back.
            //
            // Host apps drive controls in one of two ways and this handles both:
            //  - apps that enable JW's native controls -> setControls(false/true) hides/restores,
            //    and the nudgeControlsVisible() call below re-renders the control bar after the
            //    player view is reparented on exit;
            //  - apps that keep native controls disabled and drive visibility entirely via their
            //    own forceControlsVisibility calls (e.g. the OU apps, where getControls() is
            //    always false) -> setControls() is a no-op for them either way, and
            //    nudgeControlsVisible() is SKIPPED (see isNativeControlsEnabled()) because forcing
            //    JW's own control bar visible for a frame would show its default Play/Pause glyph
            //    even though the app's configured setting says controls should stay off.
            //
            // The hide is NOT a one-shot: controls stay suppressed for the whole PiP session and
            // any request arriving in the meantime is deferred into mControlsBeforePip. See
            // setControlsRequested().
            if (!isInPip && mControlsBeforePip != null) {
                JWLog.d(TAG, "applyPipChange: restoring controls=" + mControlsBeforePip + " before SDK notify");
                applyControlsToPlayer(mControlsBeforePip);
                mControlsBeforePip = null;
            }

            // Tell the JWP SDK we are toggling so it can handle toolbar / internal setup
            mPlayer.onPictureInPictureModeChanged(isInPip, newConfig);

            PlaybackManager.getInstance().setUiInPip(isInPip);

            // On ENTER, hide controls so the PiP window is video-only. Done after the SDK
            // notify (runtime hide of the existing control views, which works).
            if (isInPip) {
                if (mControlsBeforePip == null) {
                    mControlsBeforePip = mPlayer.getControls();
                }
                JWLog.d(TAG, "applyPipChange: hiding controls for PiP (was " + mControlsBeforePip + ")");
                applyControlsToPlayer(false);
            }

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
                // Actual visual enforcement: take JW's overlay views out by visibility, and keep
                // re-asserting for the rest of the PiP session (the SDK puts them back on its own
                // after setup() and on item completion). Must run AFTER mLastHandledPipState=true
                // so enforcePipUiHidden() sees PiP as active.
                hideJwUiForPip();
                startPipUiEnforcer();
                // Added after the player view so it stacks on top of the video. Replaces the
                // seek bar lost when the whole control container is hidden for PiP.
                showPipProgress(rootView);
            } else {
                // Exiting Picture in Picture
                hidePipProgress();
                stopPipUiEnforcer();
                restoreJwUiAfterPip();

                // Exit without a prior enter snapshot means this view instance did
                // not own the PiP transition. Skip reparenting to avoid applying an
                // invalid layout state (observed as app-sized minimization artifacts).
                if (rootViewVisibilitySnapshot.isEmpty()) {
                    JWLog.w(TAG, "applyPipChange: visibility snapshot empty on exit, skipping player reparent");
                    mLastHandledPipState = false;
                    if (isNativeControlsEnabled()) {
                        nudgeControlsVisible();
                    }
                    return;
                }

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

                // Controls were already re-enabled before the SDK notify above; nudge the
                // control bar to render now that the player view is laid out again. Only
                // when native controls are actually configured on — apps that keep them
                // off (forceControlsVisibility-driven) must not have JW's own control bar
                // momentarily forced visible. See isNativeControlsEnabled().
                if (isNativeControlsEnabled()) {
                    nudgeControlsVisible();
                }
            }
        } catch (Throwable t) {
            JWLog.e(TAG, "applyPipChange: unexpected error: " + t.getMessage());
        }
    }

    /**
     * Reads the player's current controls-enabled state without throwing, for logging.
     */
    private String safeGetControls() {
        try {
            return String.valueOf(mPlayer != null && mPlayer.getControls());
        } catch (Throwable t) {
            return "?";
        }
    }

    /**
     * Returns whether this view currently owns an active Picture-in-Picture session, i.e.
     * whether JW's native control bar must stay suppressed. Deliberately keyed on
     * {@link #mLastHandledPipState} — the same state that owns {@link #mControlsBeforePip} — so
     * the hide and the restore can never desync. A view that did NOT own the PiP enter never
     * hid controls and therefore must not suppress them either.
     */
    private boolean isPipSuppressingControls() {
        return Boolean.TRUE.equals(mLastHandledPipState);
    }

    /**
     * Applies a host-app request to enable/disable JW's native controls, honouring the
     * Picture-in-Picture suppression window.
     *
     * Android PiP scales the whole activity — including the embedded player view — down into a
     * small floating window, so a normal-size JW control bar renders oversized on top of the
     * video. That is the reported "the app's own control elements show up inside the PiP window,
     * overlapping the PiP window's own controls".
     *
     * The single setControls(false) at PiP ENTER (see applyPipChange) is not sufficient, because
     * the host app re-asserts controls DURING the PiP session. Measured sequence for the All Daf
     * report (logcat 2026-08-28 10:25):
     *
     *   10:25:10.900  RNJWPlayerView:   onComplete()                              // item ends in PiP
     *   10:25:11.787  RNJWPlayerModule: setControls(reactTag=4118, show=true)     // app expands sheet
     *   10:25:29.467  RNJWPlayerView:   applyPipChange(isInPip=false) controls=true controlsBeforePip=false
     *
     * On completion the app opens the next item and expands its player sheet
     * (all-mobile-shared usePlayerUIControls.restorePlayer -> setPlayerControls(true)), which
     * switched JW's control bar back on while the window was still in PiP. Opening a single item
     * and then entering PiP never hit this, which is why the original fix appeared to work.
     *
     * The request is DEFERRED, not dropped: it becomes the new restore target so PiP exit applies
     * the app's latest intent. That also repairs the mirror-image bug visible on the last log line
     * above — exit restored the stale pre-PiP value (false) and left the expanded player with no
     * controls even though the app had asked for them.
     */
    public void setControlsRequested(boolean show) {
        if (isPipSuppressingControls()) {
            JWLog.d(TAG, "setControlsRequested(" + show + ") deferred: PiP active, recorded as"
                    + " restore-on-exit target (was " + mControlsBeforePip + ")");
            mControlsBeforePip = show;
            // Re-assert suppression: harmless when already hidden, and covers the case where
            // something else flipped the SDK's controls flag on inside the PiP session.
            applyControlsToPlayer(false);
            return;
        }
        applyControlsToPlayer(show);
    }

    /**
     * Re-applies the controls state after a {@code setup()} call rebuilt the player UI. Both the
     * playlist-only fast path and {@link #reconfigurePlayer} force PLAYER_CONTROLS_CONTAINER back
     * into the UiConfig before setup(), which can leave JW's control bar enabled again — including
     * for a new media item opened while the window is in PiP.
     *
     * This is an internal restore of the LIVE state, not a host-app intent, so it must never
     * overwrite the PiP restore target ({@link #mControlsBeforePip}); it only re-asserts the
     * suppression while PiP is active.
     */
    private void reapplyControlsAfterSetup(boolean liveControlsState) {
        boolean effective = liveControlsState && !isPipSuppressingControls();
        if (effective != liveControlsState) {
            JWLog.d(TAG, "reapplyControlsAfterSetup: PiP active -> forcing controls off"
                    + " (live state was " + liveControlsState + ")");
        }
        applyControlsToPlayer(effective);
    }

    /**
     * Single place that touches the SDK's controls flag, so every path is logged and none can
     * throw through to a caller. Callers decide the PiP-effective value.
     */
    private void applyControlsToPlayer(boolean show) {
        try {
            if (mPlayer != null) {
                mPlayer.setControls(show);
            } else {
                JWLog.w(TAG, "applyControlsToPlayer(" + show + ") skipped: mPlayer is null");
            }
        } catch (Throwable t) {
            JWLog.w(TAG, "applyControlsToPlayer(" + show + ") failed: " + t.getMessage());
        }
    }

    /**
     * Records the live playhead of the track that is currently loaded, for use by the config
     * rebuild that immediately follows a foreground transition. Called from the PiP-exit and
     * host-resume callbacks, both of which run BEFORE the RN layer re-pushes its config.
     *
     * Reads the SDK, not any cached value: this is the only position in the process that is
     * guaranteed to still be correct after a spell of locked-screen background playback.
     *
     * Keeps the FURTHEST position seen for the same file inside the TTL window, because the two
     * callers fire a few tens of ms apart and the later one can read a player the rebuild has
     * already begun to tear down (position 0).
     */
    private void captureForegroundRebuildPlayhead(String reason) {
        if (mPlayer == null) {
            return;
        }
        try {
            String file = currentPlayerItemFile();
            long positionMs = (long) (mPlayer.getPosition() * 1000d);
            if (file == null || positionMs <= 0L) {
                JWLog.d(TAG, "captureForegroundRebuildPlayhead(" + reason + "): nothing to capture"
                        + " (file=" + file + ", positionMs=" + positionMs + ")");
                return;
            }
            boolean sameFileInWindow = file.equals(mForegroundRebuildFile)
                    && isForegroundRebuildSnapshotFresh();
            if (sameFileInWindow && positionMs <= mForegroundRebuildPositionMs) {
                JWLog.d(TAG, "captureForegroundRebuildPlayhead(" + reason + "): keeping furthest"
                        + " existing snapshot " + mForegroundRebuildPositionMs + "ms (read "
                        + positionMs + "ms)");
                return;
            }
            mForegroundRebuildFile = file;
            mForegroundRebuildPositionMs = positionMs;
            mForegroundRebuildCapturedAtMs = SystemClock.elapsedRealtime();
            JWLog.d(TAG, "captureForegroundRebuildPlayhead(" + reason + "): positionMs="
                    + positionMs + " file=" + file);
        } catch (Throwable t) {
            JWLog.w(TAG, "captureForegroundRebuildPlayhead(" + reason + ") failed: "
                    + t.getMessage());
        }
    }

    private boolean isForegroundRebuildSnapshotFresh() {
        return mForegroundRebuildCapturedAtMs > 0L
                && (SystemClock.elapsedRealtime() - mForegroundRebuildCapturedAtMs)
                        <= FOREGROUND_REBUILD_SNAPSHOT_TTL_MS;
    }

    /**
     * Returns the start position (seconds) the same-track rebuild should actually use, or null to
     * honour the incoming config unchanged.
     *
     * Why this is needed. On a foreground return the app re-sends its declarative config with the
     * playlist item's starttime refreshed to what JS believes the live position is. JS derives that
     * from a polled sample of the playhead, and that sample stops advancing while the screen is
     * locked and the activity is stopped — so after a spell of background playback it holds the
     * position from when the app was last awake. The fast path then rebuilds the player at that
     * value and playback jumps backwards. Reproduced 2026-08-28: an item auto-advanced inside PiP
     * played on to 8:17 with the screen locked, and the rebuild on unlock reloaded it at 2:07
     * (config starttime 127, logcat 17:21:00.680 "playlistStartMs=127000ms (from extras)").
     *
     * Movement is corrected in ONE direction only. A request to start LATER than the live playhead
     * is honoured untouched — that is a genuine seek or an Android Auto handoff resume, and both
     * must keep working. Only a request to start EARLIER than where playback already is gets
     * pinned to the live playhead, because on this path that can only be stale state: a real
     * backwards seek arrives through seekTo() on the bridge, never as a playlist config push.
     *
     * Scoped by file identity rather than by comparing successive props, because the props are not
     * a reliable anchor here — the observed burst pushed a different mediaId first and corrected
     * itself on the next push, so a prop-to-prop "same track" test is defeated by the flap while
     * the file the PLAYER is on is not.
     */
    private Double resolveForegroundRebuildStartOverrideSec(String incomingFile,
                                                           Double requestedStartSec) {
        if (mForegroundRebuildFile == null || mForegroundRebuildPositionMs <= 0L) {
            return null;
        }
        if (!isForegroundRebuildSnapshotFresh()) {
            JWLog.d(TAG, "resolveForegroundRebuildStartOverride: snapshot expired, clearing");
            clearForegroundRebuildSnapshot();
            return null;
        }
        if (incomingFile == null || !mForegroundRebuildFile.equals(incomingFile)) {
            return null;
        }
        long requestedMs = requestedStartSec == null ? 0L : (long) (requestedStartSec * 1000d);
        if (requestedMs + FOREGROUND_REBUILD_MIN_REWIND_MS >= mForegroundRebuildPositionMs) {
            return null;
        }
        double overrideSec = mForegroundRebuildPositionMs / 1000d;
        JWLog.d(TAG, "resolveForegroundRebuildStartOverride: pinning same-track rebuild to live"
                + " playhead " + mForegroundRebuildPositionMs + "ms, ignoring requested start "
                + requestedMs + "ms (rewind of " + (mForegroundRebuildPositionMs - requestedMs)
                + "ms)");
        return overrideSec;
    }

    private void clearForegroundRebuildSnapshot() {
        mForegroundRebuildFile = null;
        mForegroundRebuildPositionMs = -1L;
        mForegroundRebuildCapturedAtMs = 0L;
    }

    /**
     * File URL of the item the player is currently on, or null when it cannot be determined.
     * Prefers the item's own file and falls back to its first media source, which is the shape
     * the media-session layer already relies on (see extractPrimarySourceFile there).
     */
    private String currentPlayerItemFile() {
        try {
            if (mPlayer == null) {
                return null;
            }
            return playlistItemFile(mPlayer.getPlaylistItem());
        } catch (Throwable ignored) {
            // Fall through to null: the override is skipped and the config is honoured as sent.
        }
        return null;
    }

    /** File URL of a PlaylistItem: its own file, else its first media source. */
    private String playlistItemFile(PlaylistItem item) {
        try {
            if (item == null) {
                return null;
            }
            String file = item.getFile();
            if (file != null && !file.trim().isEmpty()) {
                return file.trim();
            }
            List<MediaSource> sources = item.getSources();
            if (sources != null && !sources.isEmpty() && sources.get(0) != null) {
                String sourceFile = sources.get(0).getFile();
                if (sourceFile != null && !sourceFile.trim().isEmpty()) {
                    return sourceFile.trim();
                }
            }
        } catch (Throwable ignored) {
            // Treated as unknown.
        }
        return null;
    }

    /**
     * True when an incoming playlist-only update is the RN layer re-asserting the item the
     * player already advanced AWAY from, during the config burst that follows a foreground
     * transition. That push is stale state, not an instruction.
     *
     * Reproduced 2026-08-29 with the automated 12-step harness: an item was skipped to inside
     * PiP, played on to 3:47, and on PiP exit the RN layer re-pushed the PREVIOUS item, which
     * the fast path then loaded and seeked to the new item's playhead (logcat 23:01:18.695
     * "app re-asserted unchanged 137108, likely a playlist re-push" -> 23:01:18.915
     * "seekTo(time=195.0)"). The user sees the elapsed time jump and the wrong title.
     *
     * The media-session layer already defends itself against exactly this ("KEEPING newer
     * androidAutoSelectedMediaId"); this extends the same scepticism to the player rebuild.
     *
     * Deliberately narrow, so a real user action is never swallowed: it fires only for the ONE
     * file we just left, only while the player is genuinely on a different item, and only inside
     * a short window after the foreground transition. Tapping any OTHER item is unaffected.
     */
    private boolean isStaleRevertPush(String incomingFile) {
        if (incomingFile == null || mPreviousItemFile == null || mCurrentItemFile == null) {
            return false;
        }
        // A push that CHANGES the app's post is an instruction, never a stale re-assert -- even
        // when it names the item we just left. Reproduced 2026-08-30 (locked-screen run, logcat
        // 09:56:39.272): Android Auto had moved to Chulin 121, the app's own rebuild had already
        // dragged the player onto its stale post (Chullin 122), and RN's NEXT push -- the
        // correction, carrying appTrackChanged=true -- looked exactly like a revert to the
        // previous item from here. Refusing it cemented the wrong track on screen. Without this
        // exit the guard turns a self-healing flap into a permanent regression.
        if (com.jwplayer.rnjwplayer.session.RNJWMediaSessionHelper.didLastAppPushChangeTrack()) {
            return false;
        }
        if (!incomingFile.equals(mPreviousItemFile) || incomingFile.equals(mCurrentItemFile)) {
            return false;
        }
        if (mForegroundRebuildCapturedAtMs <= 0L
                || SystemClock.elapsedRealtime() - mForegroundRebuildCapturedAtMs
                        > FOREGROUND_REVERT_WINDOW_MS) {
            return false;
        }
        String live = currentPlayerItemFile();
        return live != null && live.equals(mCurrentItemFile);
    }

    /**
     * True when an incoming config would switch the player AWAY from the item Android Auto is on,
     * because the app is re-asserting a post it has not noticed moving. Applies to EVERY setConfig
     * branch, which is what distinguishes it from isStaleRevertPush().
     *
     * Why a second, broader gate was needed. isStaleRevertPush() only guards the playlist-only
     * fast path. The locked-screen repro on 2026-08-30 never reached that path: more than the
     * playlist differed, so setConfig took requiresPlayerRecreation() -> reconfigurePlayer(),
     * which had no gate at all. The damage sequence from that capture:
     *
     *   09:56:31.315  onStartedWakingUp                       (phone unlocked, step 9)
     *   09:56:34.481  captureForegroundRebuildPlayhead(pip-exit): positionMs=606366
     *                 file=.../48677.m3u8                      (live item = Chulin 121, from AA)
     *   09:56:34.572  setAppProvidedMediaId: KEEPING newer androidAutoSelectedMediaId=48677
     *                 (app re-asserted unchanged 262485, likely a player rebuild)
     *   09:56:34.573  "Reconfiguring existing player without recreation"
     *   09:56:35.210  onPlaylistItem: item identity .../48677.m3u8 -> .../TwmDOHJk.m3u8
     *                 PlaylistItem JSON: {"title":"Chullin 122 (uned..."}   <- WRONG ITEM LOADED
     *
     * The media-session layer had already diagnosed it in the line above ("KEEPING newer") and
     * then let the rebuild proceed anyway. This gate acts on that diagnosis.
     *
     * Deliberately narrow, so opening a new item is never swallowed: it fires only inside the
     * short window after a foreground transition, only when the player is genuinely on another
     * file, and only when the app did NOT change its own post id -- a real navigation always does.
     */
    private boolean shouldKeepLiveItemForForegroundRebuild(String incomingFile) {
        // GATE_TRACE: every exit below is logged with the inputs. Without this the gate was a
        // black box -- four silent early returns and a log line only on refusal -- so a user
        // report of "opened Series B, got Series A" could not be attributed to this gate or
        // cleared of it. Do not remove: this is the only place the decision is observable.
        //
        // Built only when verbose logging is actually on, so production does no work here. The
        // REFUSE case still logs its own unconditional warning below.
        final boolean trace = JWLog.isVerbose();
        String traceMsg = null;
        if (trace) {
            String liveForTrace = null;
            try {
                liveForTrace = currentPlayerItemFile();
            } catch (Throwable ignore) {
                // fall through with null; the trace still records the rest
            }
            traceMsg = "GATE_TRACE shouldKeepLiveItem: incoming=" + incomingFile
                    + ", live=" + liveForTrace
                    + ", snapshotFresh=" + isForegroundRebuildSnapshotFresh()
                    + ", snapshotAgeMs=" + (mForegroundRebuildCapturedAtMs <= 0L ? -1L
                            : SystemClock.elapsedRealtime() - mForegroundRebuildCapturedAtMs)
                    + ", " + com.jwplayer.rnjwplayer.session.RNJWMediaSessionHelper
                            .describeTrackState();
        }

        if (incomingFile == null || mPlayer == null) {
            if (trace) { JWLog.d(TAG, traceMsg + " -> ALLOW (no incoming file or no player)"); }
            return false;
        }
        if (!isForegroundRebuildSnapshotFresh()) {
            if (trace) {
                JWLog.d(TAG, traceMsg + " -> ALLOW (no fresh foreground snapshot; not a rebuild"
                        + " window)");
            }
            return false;
        }
        String live = currentPlayerItemFile();
        if (live == null || live.equals(incomingFile)) {
            if (trace) { JWLog.d(TAG, traceMsg + " -> ALLOW (player already on the incoming file)"); }
            return false;
        }
        if (com.jwplayer.rnjwplayer.session.RNJWMediaSessionHelper.didLastAppPushChangeTrack()) {
            if (trace) {
                JWLog.d(TAG, traceMsg + " -> ALLOW (app changed its own post id: genuine"
                        + " navigation)");
            }
            return false;
        }
        if (!com.jwplayer.rnjwplayer.session.RNJWMediaSessionHelper
                .isAppTrackStaleVsAndroidAuto()) {
            if (trace) {
                JWLog.d(TAG, traceMsg + " -> ALLOW (app post agrees with the Android Auto"
                        + " selection)");
            }
            return false;
        }
        JWLog.w(TAG, "setConfig IGNORED: app re-asserted a stale post while the player is on the"
                + " Android Auto selection -- keeping live item " + live
                + ", refusing switch to " + incomingFile);
        if (trace) { JWLog.w(TAG, traceMsg + " -> REFUSE"); }
        return true;
    }

    /** First playlist item's startTime (seconds) as sent by RN, or null when absent. */
    private Double firstPlaylistStartTimeFromConfig(ReadableMap configProp) {
        try {
            if (configProp != null && configProp.hasKey("playlist")) {
                ReadableArray playlist = configProp.getArray("playlist");
                if (playlist != null && playlist.size() > 0) {
                    ReadableMap first = playlist.getMap(0);
                    if (first != null) {
                        // JS sends the SDK's lowercase spelling; the legacy path uses camelCase.
                        for (String key : new String[]{"starttime", "startTime"}) {
                            if (first.hasKey(key) && !first.isNull(key)) {
                                return first.getDouble(key);
                            }
                        }
                    }
                }
            }
        } catch (Throwable ignored) {
            // Treated as "no explicit start".
        }
        return null;
    }

    /**
     * Hides JW's entire UI overlay for the duration of PiP by VIEW VISIBILITY, not by the SDK's
     * controls flag.
     *
     * Why not setControls(false): it does not hold. Measured on 2026-08-28 with the controls flag
     * already false and PiP suppression active, the SDK put its control bar back up on its own
     * less than a second later:
     *
     *   11:38:09.001  reassertControlsAfterExternalSetup(controlsBeforeSetup=false) pipSuppressing=true
     *   11:38:09.986  onControlBarVisibilityChanged(visible=true)
     *
     * Every setControls() callsite in this library was already routed through the PiP gate at that
     * point, so the flag is simply not the thing that governs this overlay once JW rebuilds its UI
     * (notably after the native next-item setup(), and again when an item completes and JW shows
     * its replay/idle overlay). Screenshots IMG_0653 / IMG_0654.
     *
     * jwplayerview.xml is a FrameLayout with exactly two children: ControlsContainerView (holding
     * SideSeek, Logo, Overlay -> Error/CenterControls/Controlbar/NextUp/Chapters/Menu/CastingMenu/
     * Playlist/VastAds) and an ads container. The video surface is NOT inside it.
     *
     * Hiding ControlsContainerView outright is WRONG though, and produced the follow-up report
     * "audio in PiP is now just a black window": JW draws the audio poster/thumbnail as
     * {@code overlay_poster_img} INSIDE OverlayView, which is inside that container. Audio-only
     * media has no video surface, so with the container gone the PiP window had nothing left to
     * draw. ControlsContainerView and OverlayView are therefore passed THROUGH (kept visible and
     * descended into), and only their non-poster content is hidden — the poster image survives,
     * while OverlayView's title/description text, which renders at unscaled size in the small
     * window, does not.
     *
     * Matching is by class name rather than by R id so this does not depend on the generated R
     * package, and descent stops at the first non-pass-through match so we hide containers rather
     * than leaves. JW recreates these views on setup(), so this is re-asserted from several
     * triggers — see enforcePipUiHidden().
     */
    private void hideJwUiForPip() {
        if (mPlayerView == null) {
            return;
        }
        try {
            List<View> targets = new ArrayList<>();
            collectPipHideTargets(mPlayerView, false, targets);
            int hidden = 0;
            for (View target : targets) {
                if (!mJwUiVisibilitySnapshot.containsKey(target)) {
                    mJwUiVisibilitySnapshot.put(target, target.getVisibility());
                }
                if (target.getVisibility() != View.GONE) {
                    target.setVisibility(View.GONE);
                    hidden++;
                }
            }
            if (hidden > 0) {
                JWLog.d(TAG, "hideJwUiForPip: hid " + hidden + " JW UI view(s) of "
                        + targets.size() + " found (poster preserved)");
            }
        } catch (Throwable t) {
            JWLog.w(TAG, "hideJwUiForPip failed: " + t.getMessage());
        }
    }

    /** Restores the visibility JW's UI views had when PiP was entered. */
    private void restoreJwUiAfterPip() {
        if (mJwUiVisibilitySnapshot.isEmpty()) {
            return;
        }
        int restored = 0;
        for (Map.Entry<View, Integer> entry : mJwUiVisibilitySnapshot.entrySet()) {
            try {
                entry.getKey().setVisibility(entry.getValue());
                restored++;
            } catch (Throwable ignored) {
                // View detached between PiP enter and exit; nothing to restore.
            }
        }
        mJwUiVisibilitySnapshot.clear();
        JWLog.d(TAG, "restoreJwUiAfterPip: restored " + restored + " JW UI view(s)");
    }

    /**
     * Collects the views to hide for PiP.
     *
     * ControlsContainerView and OverlayView are pass-through: kept visible so the audio poster
     * they wrap keeps drawing, and descended into. Any other {@code com.jwplayer.ui.views.*} view
     * is hidden as a whole (its descendants come along). Plain views are only hidden when they sit
     * INSIDE a passed-through OverlayView — that is the one place where hiding non-JW views is
     * correct (title / description text). Everywhere else plain views are left alone, which is what
     * keeps the video surface and the ads container untouched.
     *
     * @param insideOverlay true once descent has entered an OverlayView
     */
    private void collectPipHideTargets(View root, boolean insideOverlay, List<View> out) {
        if (root == null) {
            return;
        }
        String className = root.getClass().getName();
        if (className.startsWith("com.jwplayer.ui.views.")) {
            String simpleName = root.getClass().getSimpleName();
            boolean passThrough = "ControlsContainerView".equals(simpleName)
                    || "OverlayView".equals(simpleName);
            if (passThrough) {
                if (root instanceof ViewGroup) {
                    boolean overlay = insideOverlay || "OverlayView".equals(simpleName);
                    ViewGroup group = (ViewGroup) root;
                    for (int i = 0; i < group.getChildCount(); i++) {
                        collectPipHideTargets(group.getChildAt(i), overlay, out);
                    }
                }
                return;
            }
            out.add(root);
            return;
        }
        if (insideOverlay) {
            if (!isPosterView(root)) {
                out.add(root);
            }
            return;
        }
        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;
            for (int i = 0; i < group.getChildCount(); i++) {
                collectPipHideTargets(group.getChildAt(i), false, out);
            }
        }
    }

    /**
     * True for JW's poster / thumbnail image views, matched on the resource entry name
     * ({@code overlay_poster_img} and friends) so the audio artwork keeps rendering in PiP.
     */
    private boolean isPosterView(View view) {
        try {
            int id = view.getId();
            if (id == View.NO_ID) {
                return false;
            }
            String name = view.getResources().getResourceEntryName(id);
            return name != null && name.contains("poster");
        } catch (Throwable ignored) {
            return false;
        }
    }

    /** Re-asserts the PiP overlay hide, but only while this view owns an active PiP session. */
    private void enforcePipUiHidden() {
        if (isPipSuppressingControls()) {
            hideJwUiForPip();
        }
    }

    /**
     * Watches the PiP window's layout passes and re-hides JW's overlay whenever the SDK rebuilds
     * or re-shows it. A layout listener is used rather than polling alone so the overlay is gone
     * within the same frame it would have appeared in — the "ugly UI flashes while the next media
     * loads" report.
     */
    private void startPipUiEnforcer() {
        stopPipUiEnforcer();
        if (mPlayerView == null) {
            return;
        }
        try {
            mPipUiEnforcer = this::enforcePipUiHidden;
            mPlayerView.getViewTreeObserver().addOnGlobalLayoutListener(mPipUiEnforcer);
        } catch (Throwable t) {
            JWLog.w(TAG, "startPipUiEnforcer failed: " + t.getMessage());
            mPipUiEnforcer = null;
        }
    }

    private void stopPipUiEnforcer() {
        if (mPipUiEnforcer == null) {
            return;
        }
        try {
            if (mPlayerView != null) {
                mPlayerView.getViewTreeObserver().removeOnGlobalLayoutListener(mPipUiEnforcer);
            }
        } catch (Throwable t) {
            JWLog.w(TAG, "stopPipUiEnforcer failed: " + t.getMessage());
        }
        mPipUiEnforcer = null;
    }

    /**
     * True when Picture-in-Picture may be used for whatever is currently loaded.
     *
     * Always true unless the host app opted into {@code pipVideoOnly}, in which case PiP is
     * withheld until JW has POSITIVELY confirmed a video track.
     *
     * The default is deny-until-proven-video, not allow-until-proven-audio. Measured on-device
     * (All Daf, 2026-08-28) JW reports {@code width=-1 height=-1 videoMimeType="" audioMimeType=""}
     * for audio-only media — i.e. it emits no positive audio signal at all, and an audio item is
     * indistinguishable from an item whose metadata has not arrived yet. Any rule that tries to
     * detect audio therefore cannot fire, which is why the two previous attempts (null mime check,
     * then blank-mime check) both left audio entering PiP. Video, by contrast, IS positively
     * detectable via its decoded dimensions, so the gate keys off the signal that actually exists.
     */
    public boolean isPipAllowedForCurrentMedia() {
        if (!pipVideoOnly) {
            return true;
        }
        return Boolean.TRUE.equals(mHasVideoTrack);
    }

    /**
     * Applies {@code pipVideoOnly} by registering / deregistering the activity with the JW SDK.
     *
     * This is the only lever that covers the case the user actually hits: PiP is not entered by
     * this library on backgrounding — {@code mPlayer.registerActivityForPip(...)} hands that to the
     * SDK, which auto-enters PiP when the activity is folded away. Gating only the explicit
     * entry points (the back-button callback and the togglePIP bridge method) would therefore have
     * left audio media still popping into PiP on fold, so registration itself is withdrawn.
     *
     * Never runs while a PiP session is active: tearing down the registration mid-session would
     * strand the window.
     */
    private void updatePipRegistration() {
        if (mPlayer == null || mActivity == null || !mPipEnabled) {
            JWLog.d(TAG, "updatePipRegistration: skipped (mPlayer=" + (mPlayer != null)
                    + " mActivity=" + (mActivity != null) + " mPipEnabled=" + mPipEnabled + ")");
            return;
        }
        if (isPipSuppressingControls()) {
            JWLog.d(TAG, "updatePipRegistration: skipped, PiP session already active");
            return;
        }
        boolean allow = isPipAllowedForCurrentMedia();
        if (mPipRegisteredForVideo != null && mPipRegisteredForVideo == allow) {
            return;
        }
        try {
            if (allow) {
                mPlayer.registerActivityForPip(mActivity, mActivity.getSupportActionBar());
                registerPipBackCallback();
                JWLog.d(TAG, "updatePipRegistration: PiP enabled (pipVideoOnly=" + pipVideoOnly
                        + ", hasVideoTrack=" + mHasVideoTrack + ")");
            } else {
                mPlayer.deregisterActivityForPip();
                unregisterPipBackCallback();
                disableSystemAutoEnterPip();
                JWLog.d(TAG, "updatePipRegistration: PiP withheld, no confirmed video track"
                        + " (pipVideoOnly=true, hasVideoTrack=" + mHasVideoTrack + ")");
            }
            mPipRegisteredForVideo = allow;
        } catch (Throwable t) {
            JWLog.w(TAG, "updatePipRegistration failed: " + t.getMessage());
        }
    }

    /**
     * Second, independent lever against auto-entering PiP on fold.
     *
     * Deregistering with the SDK is the primary mechanism, but it only helps if the SDK enters PiP
     * via its own lifecycle observer. On Android 12+ an activity can also be auto-entered by the
     * system itself when {@code PictureInPictureParams.autoEnterEnabled} is set, which survives
     * anything done at the SDK level. Clearing it here means both routes are closed regardless of
     * which one the SDK actually uses.
     *
     * Only ever clears the flag — the allow path deliberately does not set it, because overwriting
     * the SDK's params would drop the aspect ratio / source rect hint it configures for the video
     * PiP window. Re-registering restores whatever the SDK wants.
     */
    private void disableSystemAutoEnterPip() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || mActivity == null) {
            return;
        }
        try {
            mActivity.setPictureInPictureParams(
                    new PictureInPictureParams.Builder().setAutoEnterEnabled(false).build());
            JWLog.d(TAG, "disableSystemAutoEnterPip: autoEnterEnabled=false");
        } catch (Throwable t) {
            JWLog.w(TAG, "disableSystemAutoEnterPip failed: " + t.getMessage());
        }
    }

    /**
     * Records that the current media has a video track from JW's track metadata, then
     * re-evaluates the PiP registration.
     *
     * Only ever resolves to TRUE. MetaEvent fires repeatedly with partial payloads (ID3 cues), and
     * for audio-only media this SDK reports {@code -1x-1} with blank mime types — which is byte for
     * byte what it also reports before a video item's tracks are decoded. There is consequently no
     * payload that proves "audio", so no negative branch exists here; absence of proof is handled
     * by the deny default in {@link #isPipAllowedForCurrentMedia()}.
     */
    private void noteTrackMetadata(int width, int height, String videoMimeType, String audioMimeType) {
        // One line per playlist item, not per MetaEvent (which fires repeatedly with ID3 cues).
        // Kept permanently: the whole pipVideoOnly gate rests on what this SDK actually reports
        // here, and that turned out to be neither documented nor intuitive.
        if (!mLoggedTrackMetaForItem) {
            mLoggedTrackMetaForItem = true;
            JWLog.d(TAG, "trackMeta: " + width + "x" + height + " videoMime='" + videoMimeType
                    + "' audioMime='" + audioMimeType + "'");
        }
        boolean hasVideo = (width > 0 && height > 0) || !isBlank(videoMimeType);
        if (!hasVideo || Boolean.TRUE.equals(mHasVideoTrack)) {
            return;
        }
        mHasVideoTrack = Boolean.TRUE;
        JWLog.d(TAG, "noteTrackMetadata: video track confirmed (" + width + "x" + height
                + ", video=" + videoMimeType + ", audio=" + audioMimeType + ")");
        updatePipRegistration();
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    /**
     * Forgets the current media's track answer; called when a new item is loaded.
     *
     * Re-evaluates registration immediately: under deny-until-proven-video, dropping back to
     * "unknown" must actively withdraw PiP, otherwise a video item's registration would still be
     * live when the next item turns out to be audio.
     */
    private void resetVideoTrackDetection() {
        if (mHasVideoTrack != null) {
            JWLog.d(TAG, "resetVideoTrackDetection: clearing hasVideoTrack for new item");
        }
        mHasVideoTrack = null;
        mLoggedTrackMetaForItem = false;
        updatePipRegistration();
    }

    /**
     * Adds a thin progress line to the PiP window and starts updating it.     *
     * Why this exists: JW's own control bar carries the seek bar, but it is hidden for the whole
     * PiP session (see setControlsRequested) because Android scales the activity into the small
     * window and the control bar would render at unscaled size on top of the video. Hiding it took
     * the progress indicator with it — the regression reported on 2026-08-28 ("before, a progress
     * indicator was displayed inside PiP"), introduced by the PiP control-overlay fix add294e
     * (2026-06-23).
     *
     * Rather than un-hide JW's control bar, this draws a PiP-appropriate 3dp line pinned to the
     * bottom of the PiP window. It is added to the same rootView the player view is reparented
     * into, AFTER the player view, so it stacks on top of the video. Colours follow the host app's
     * {@code styling.colors.timeslider} prop so it matches the in-app seek bar accent.
     *
     * @param rootView the activity content view the player view was just reparented into
     */
    private void showPipProgress(ViewGroup rootView) {
        if (rootView == null) {
            return;
        }
        hidePipProgress();
        try {
            PipProgressView bar = new PipProgressView(getContext());
            bar.setColors(resolvePipProgressColor(), resolvePipRailColor());
            int heightPx = Math.max(2, Math.round(
                    PIP_PROGRESS_HEIGHT_DP * getResources().getDisplayMetrics().density));
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT, heightPx);
            lp.gravity = Gravity.BOTTOM;
            rootView.addView(bar, lp);
            mPipProgressView = bar;
            JWLog.d(TAG, "showPipProgress: PiP progress line attached (" + heightPx + "px)");
            startPipProgressTicks();
        } catch (Throwable t) {
            // Never let a cosmetic overlay break the PiP transition.
            JWLog.w(TAG, "showPipProgress failed: " + t.getMessage());
            mPipProgressView = null;
        }
    }

    /** Removes the PiP progress line and stops its ticker. Safe to call when nothing is showing. */
    private void hidePipProgress() {
        stopPipProgressTicks();
        PipProgressView bar = mPipProgressView;
        mPipProgressView = null;
        if (bar == null) {
            return;
        }
        try {
            if (bar.getParent() instanceof ViewGroup) {
                ((ViewGroup) bar.getParent()).removeView(bar);
            }
            JWLog.d(TAG, "hidePipProgress: PiP progress line detached");
        } catch (Throwable t) {
            JWLog.w(TAG, "hidePipProgress failed: " + t.getMessage());
        }
    }

    private void startPipProgressTicks() {
        stopPipProgressTicks();
        if (mPlayerView == null) {
            return;
        }
        mPipProgressTick = new Runnable() {
            @Override
            public void run() {
                if (mPipProgressView == null) {
                    return;
                }
                updatePipProgress();
                // Backstop for any path that neither lays out nor fires a control-bar event.
                enforcePipUiHidden();
                if (mPipProgressView != null && mPlayerView != null) {
                    mPlayerView.postDelayed(this, PIP_PROGRESS_TICK_MS);
                }
            }
        };
        mPlayerView.post(mPipProgressTick);
    }

    private void stopPipProgressTicks() {
        if (mPipProgressTick != null && mPlayerView != null) {
            mPlayerView.removeCallbacks(mPipProgressTick);
        }
        mPipProgressTick = null;
    }

    /**
     * Polls the player position rather than listening for time events: the ticker only runs while
     * the PiP line is attached, 2Hz is plenty for a 3dp bar, and polling stays correct across the
     * native next-item loads that replace the playlist underneath us.
     */
    private void updatePipProgress() {
        PipProgressView bar = mPipProgressView;
        if (bar == null) {
            return;
        }
        double position = -1d;
        double duration = -1d;
        try {
            if (mPlayer != null) {
                position = mPlayer.getPosition();
                duration = mPlayer.getDuration();
            }
        } catch (Throwable ignored) {
            // Player torn down mid-tick; fall through to the unknown-duration branch.
        }
        if (duration <= 0d || position < 0d) {
            // Live stream, or duration not known yet: show the rail only.
            bar.setFraction(-1f);
            return;
        }
        bar.setFraction((float) Math.min(1d, position / duration));
    }

    private int resolvePipProgressColor() {
        Integer configured = timesliderColor("progress");
        return configured != null ? configured : Color.WHITE;
    }

    private int resolvePipRailColor() {
        Integer configured = timesliderColor("rail");
        // Default to a translucent white track so the line reads over any video content.
        return configured != null ? configured : Color.argb(0x59, 0xFF, 0xFF, 0xFF);
    }

    /** Reads one colour out of the host app's {@code styling.colors.timeslider} prop. */
    private Integer timesliderColor(String key) {
        try {
            if (mColors != null && mColors.hasKey("timeslider")) {
                ReadableMap timeslider = mColors.getMap("timeslider");
                if (timeslider != null && timeslider.hasKey(key) && !timeslider.isNull(key)) {
                    return Color.parseColor("#" + timeslider.getString(key));
                }
            }
        } catch (Throwable ignored) {
            // Fall back to the defaults above.
        }
        return null;
    }

    /**
     * Minimal two-rect progress line: rail across the full width, fill up to the current
     * fraction. A negative fraction means "duration unknown" and draws the rail only.
     */
    private static class PipProgressView extends View {
        private final Paint railPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private float fraction = -1f;

        PipProgressView(Context context) {
            super(context);
        }

        void setColors(int progressColor, int railColor) {
            fillPaint.setColor(progressColor);
            railPaint.setColor(railColor);
            invalidate();
        }

        void setFraction(float value) {
            float next = value < 0f ? -1f : Math.min(1f, value);
            if (Math.abs(next - fraction) < 0.0005f) {
                return;
            }
            fraction = next;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            int width = getWidth();
            int height = getHeight();
            if (width <= 0 || height <= 0) {
                return;
            }
            canvas.drawRect(0f, 0f, width, height, railPaint);
            if (fraction > 0f) {
                canvas.drawRect(0f, 0f, width * fraction, height, fillPaint);
            }
        }
    }

    /**
     * Public entry point for code that called {@code setup()} on THIS view's player from outside
     * the view — currently the headless / Android Auto next-item load in
     * {@link JWPlayerNativePlaybackHandler}, which builds a bare PlayerConfig and therefore
     * re-enables JW's control bar with default styling.
     *
     * Two bugs came out of that path, both reported against All Daf on 2026-08-28:
     *  - when the next item loaded while the window was in PiP, the full-size control overlay
     *    (title, subtitle, time labels, settings gear, fullscreen, "...") reappeared inside the
     *    small PiP window (IMG_0651);
     *  - the same overlay flashed for a moment on every native advance even outside PiP, and a
     *    collapsed/mini player silently regained JW's control bar.
     *
     * Callers must capture {@code getControls()} BEFORE their setup() call and pass it here
     * immediately after, on the same main-thread runnable, so no frame is drawn in between.
     */
    public void reassertControlsAfterExternalSetup(boolean controlsBeforeSetup) {
        JWLog.d(TAG, "reassertControlsAfterExternalSetup(controlsBeforeSetup=" + controlsBeforeSetup
                + ") pipSuppressing=" + isPipSuppressingControls());
        reapplyControlsAfterSetup(controlsBeforeSetup);
        // setup() rebuilds JW's UI, so the overlay views this view had taken GONE are brand new
        // and visible again. Re-hide them in the same pass — the flag alone does not hold.
        enforcePipUiHidden();
    }

    /**
     * Returns whether this player instance is currently configured with JW's native
     * controls enabled — the durable host-app setting driven by config/setControls(),
     * NOT the transient show/hide state reported by ControlBarVisibilityEvent.
     *
     * Used to gate the PiP-exit forceControlsVisibility nudge (see nudgeControlsVisible):
     * apps that enable JW's native controls need the nudge to re-render their control
     * bar after the player view is reparented on PiP exit. Apps that keep native
     * controls disabled and drive all visibility themselves via forceControlsVisibility
     * (e.g. the OU apps, where getControls() is always false) must NOT receive this
     * nudge — calling setForceControlsVisibility(true) momentarily shows JW's own
     * default control bar / play-pause glyph even though the app's configured setting
     * says controls should stay off, which is visible as a large Play/Pause icon
     * flashing on PiP return.
     *
     * Safe to call at the point nudgeControlsVisible() would be invoked: on the normal
     * exit path controls have already been restored to the pre-PiP configured value via
     * setControls(mControlsBeforePip) a few lines above; on the "didn't own the PiP
     * enter" early-return path controls were never touched by this instance, so
     * getControls() already reflects the real configured value either way.
     */
    private boolean isNativeControlsEnabled() {
        try {
            return mPlayer != null && mPlayer.getControls();
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * Posts a force-visibility nudge (show, then release back to normal auto-hide) on the
     * next frame so JW re-renders the control bar after the player view has been
     * re-attached and laid out. Mirrors the original post-PiP "controls got lost"
     * workaround. The actual re-enable of controls happens earlier, before the SDK is
     * notified of the PiP exit (see applyPipChange).
     *
     * Callers MUST gate this with isNativeControlsEnabled() — see that method's doc.
     */
    private void nudgeControlsVisible() {
        if (mPlayerView == null) {
            return;
        }
        mPlayerView.post(() -> {
            try {
                mPlayer.setForceControlsVisibility(true);
                mPlayer.setForceControlsVisibility(false);
            } catch (Throwable ignored) {
                // player may have been torn down between PiP exit and this callback
            }
        });
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
        mControlsBeforePip = null;
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
                        if (!isPipAllowedForCurrentMedia()) {
                            // pipVideoOnly: audio-only media takes the normal back path, so the
                            // activity finishes and playback continues via the media session.
                            JWLog.d(TAG, "PiP back-callback: skipping PiP, audio-only media"
                                    + " and pipVideoOnly=true");
                            fallbackToDefaultBack();
                            return;
                        }
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
        // PUSH_TRACE: the identity and start position RN is asking for, logged BEFORE any gate
        // runs. Needed because "which item did RN actually request, and when" was not recoverable
        // from the log -- only the gate's verdict was. Skipped entirely unless verbose logging is
        // on, so production never walks the playlist map to build it.
        if (prop != null && JWLog.isVerbose()) {
            try {
                String pushedFile = null;
                Double pushedStart = null;
                String pushedTitle = null;
                int playlistSize = -1;
                if (prop.hasKey("playlist") && !prop.isNull("playlist")) {
                    com.facebook.react.bridge.ReadableArray pl = prop.getArray("playlist");
                    if (pl != null) {
                        playlistSize = pl.size();
                        if (pl.size() > 0) {
                            ReadableMap first = pl.getMap(0);
                            if (first != null) {
                                if (first.hasKey("file") && !first.isNull("file")) {
                                    pushedFile = first.getString("file");
                                }
                                if (first.hasKey("title") && !first.isNull("title")) {
                                    pushedTitle = first.getString("title");
                                }
                                if (first.hasKey("starttime") && !first.isNull("starttime")) {
                                    pushedStart = first.getDouble("starttime");
                                }
                            }
                        }
                    }
                }
                JWLog.d(TAG, "PUSH_TRACE setConfig: file=" + pushedFile
                        + ", title=" + pushedTitle
                        + ", starttime=" + pushedStart
                        + ", live=" + currentPlayerItemFile()
                        + ", playlistSize=" + playlistSize
                        + ", " + com.jwplayer.rnjwplayer.session.RNJWMediaSessionHelper
                                .describeTrackState());
            } catch (Throwable t) {
                JWLog.d(TAG, "PUSH_TRACE setConfig: unavailable (" + t.getMessage() + ")");
            }
        }
        if (prop != null && prop.hasKey("androidHandoffGeneration")
                && !prop.isNull("androidHandoffGeneration")) {
            mMediaGeneration = (long) prop.getDouble("androidHandoffGeneration");
        } else {
            mMediaGeneration = 0L;
        }
        // Developer opt-in, read on every config push so it can be toggled at runtime. Default
        // false keeps today's behaviour (PiP for audio as well as video).
        if (prop != null && prop.hasKey("pipVideoOnly") && !prop.isNull("pipVideoOnly")) {
            boolean requested = prop.getBoolean("pipVideoOnly");
            if (requested != pipVideoOnly) {
                pipVideoOnly = requested;
                JWLog.d(TAG, "setConfig: pipVideoOnly=" + pipVideoOnly);
                mPipRegisteredForVideo = null;
                updatePipRegistration();
            }
        }
        if (mConfig == null || !mConfig.equals(prop)) {
            // Capture the app-provided post-id mediaId from the raw playlist prop BEFORE
            // JW's JsonHelper config parser drops it (createPlayerView/buildPlayerConfig
            // return the parsed JW config directly, stripping mediaId). This preserves the
            // numeric OU post id for the MediaSession completion event so background/locked
            // auto-advance works for JW-hosted video, whose JW-inferred mediaId is a content
            // UUID rather than a post id. See RNJWMediaSessionHelper.resolveMediaIdForCompletion.
            if (prop != null && prop.hasKey("playlist")) {
                try {
                    ReadableArray playlistArr = prop.getArray("playlist");
                    if (playlistArr != null && playlistArr.size() > 0) {
                        ReadableMap firstItem = playlistArr.getMap(0);
                        if (firstItem != null && firstItem.hasKey("mediaId")) {
                            com.jwplayer.rnjwplayer.session.RNJWMediaSessionHelper
                                    .setAppProvidedMediaId(firstItem.getString("mediaId"));
                        }
                    }
                } catch (Exception e) {
                    JWLog.w(TAG, "setConfig: failed to capture app-provided mediaId: " + e.getMessage());
                }
            }

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

            // Before choosing a branch: refuse a foreground rebuild that would drag the player
            // off the item Android Auto is on. Both the fast path below and the reconfigure path
            // further down would otherwise load the app's stale post -- and the reconfigure path
            // is the one the locked-screen repro actually took. See
            // shouldKeepLiveItemForForegroundRebuild() for the captured sequence.
            //
            // mConfig is still advanced to `prop` so this push is not re-evaluated on the next
            // diff; only the PLAYER is left alone. RN's own corrective push (which carries a
            // changed post id) is unaffected and will be honoured normally.
            if (shouldKeepLiveItemForForegroundRebuild(firstPlaylistFileFromConfig(prop))) {
                if (prop.hasKey("playlist")) {
                    mPlaylistProp = prop.getArray("playlist");
                }
                mConfig = prop;
                return;
            }

            // Only playlist changed -> update config without stop/recreate
            if (mConfig != null && isOnlyDiff(prop, "playlist") && mPlayer != null) {
                JWLog.d(TAG, "Playlist-only change detected -> applying fast update");

                // Distinguish a same-track foreground rebuild (PiP/lock/background resume-sync
                // only rewrites the playlist item's starttime) from a genuine new-track open
                // (different file). mConfig still holds the PREVIOUS prop here -- it is updated
                // to `prop` only at the very end of setConfig -- so compare the two directly.
                // Both sides read the RN prop's "file", giving a consistent, normalization-free
                // comparison (see firstPlaylistFileFromConfig).
                String previousFirstFile = firstPlaylistFileFromConfig(mConfig);

                // IMPORTANT: ensure mPlaylistProp is updated from NEW prop
                if (prop.hasKey("playlist")) {
                    mPlaylistProp = prop.getArray("playlist");
                }
                String newFirstFile = firstPlaylistFileFromConfig(prop);
                boolean sameTrack = previousFirstFile != null && previousFirstFile.equals(newFirstFile);

                // Pin a same-track rebuild to the live playhead when the incoming starttime is
                // behind it. Keyed on the file the PLAYER is on rather than on `sameTrack` above,
                // because a foreground return can push a corrective burst in which one push names
                // a different item — that flap defeats a prop-to-prop comparison but not the
                // player's own identity. Null means "honour the config as sent".
                Double requestedStartSec = firstPlaylistStartTimeFromConfig(prop);
                Double startOverrideSec =
                        resolveForegroundRebuildStartOverrideSec(newFirstFile, requestedStartSec);

                // Stale foreground re-push of the item we already advanced away from: rebuilding
                // would swap the listener back to the previous item AND carry the current item's
                // playhead onto it. Leave the player alone -- it is already on the right item at
                // the right position. mConfig is still updated so this push is not re-evaluated.
                if (isStaleRevertPush(newFirstFile)) {
                    JWLog.w(TAG, "Playlist-only fast update IGNORED: stale re-push of the previous"
                            + " item (" + newFirstFile + ") while the player is on " + mCurrentItemFile
                            + " -- keeping live item, refusing revert"
                            + " (requestedStart=" + requestedStartSec + "s)");
                    mConfig = prop;
                    return;
                }

                PlayerConfig oldConfig = mPlayer.getConfig();
                // Capture the controls-enabled state BEFORE setup() so we can restore the
                // caller's intended visibility after forcing the UI group below.
                boolean currentControlsState = mPlayer.getControls();

                // Preserve a user-initiated pause across a same-track foreground rebuild.
                //
                // On PiP/lock exit the app re-sends the declarative config with only the
                // playlist item's starttime changed (the resume-sync in PlayerCore), so this
                // fast path runs every time. oldConfig.getAutostart() perpetuates whatever
                // autostart this player instance was ORIGINALLY created with -- when the app's
                // autoplay preference is on, that is `true`, so setup() below would auto-RESUME
                // playback on every single PiP/foreground return even though the user explicitly
                // PAUSED while in PiP. Confirmed via logcat: with autoPlay preference on, native
                // onPlay() fires straight out of this setup() call with NO JS bridge call
                // (seekTo/play) anywhere in between -- this path has zero JS-side visibility, so
                // it cannot be fixed from the RN layer alone.
                //
                // userPaused is set by onPause() (and the pause()/stop() bridge) and cleared by
                // onPlay(); a system/interruption pause does NOT set it (see onPause). When it is
                // set AND we are rebuilding the SAME track (not opening a new one), force autostart
                // off so the player reloads at the resume position but stays paused. A genuine
                // new-track open (different file) keeps the autoplay preference, so tapping a new
                // item still starts playback even if the previous track was paused. A was-playing
                // return keeps autostart because userPaused is false there.
                boolean autostart = oldConfig.getAutostart();
                if (autostart && userPaused && sameTrack) {
                    JWLog.d(TAG, "Playlist-only fast update: same track + userPaused -> forcing autostart=false to preserve user pause");
                    autostart = false;
                }
                // If controls were previously turned off (e.g. app's collapsed/mini player
                // calls setControls(false)), the SDK's live uiConfig no longer includes
                // PLAYER_CONTROLS_CONTAINER. Carrying that uiConfig forward as-is into setup()
                // permanently drops the UI group -- there is no way to recover it afterward via
                // setControls(true) (see createUiConfigWithControlsContainer doc). This left the
                // player with no controls/pause button after next/prev while collapsed, then
                // expanding back to full view. Always ensure the UI group is present at setup()
                // time, and re-apply the real on/off state via setControls() afterward.
                PlayerConfig config = new PlayerConfig.Builder()
                        .autostart(autostart)
                        .nextUpOffset(oldConfig.getNextUpOffset())
                        .repeat(oldConfig.getRepeat())
                        .relatedConfig(oldConfig.getRelatedConfig())
                        .displayDescription(oldConfig.getDisplayDescription())
                        .displayTitle(oldConfig.getDisplayTitle())
                        .advertisingConfig(oldConfig.getAdvertisingConfig())
                        .stretching(oldConfig.getStretching())
                        .uiConfig(createUiConfigWithControlsContainer(mPlayer, oldConfig.getUiConfig()))
                        .playlist(Util.createPlaylist(mPlaylistProp, startOverrideSec))
                        .allowCrossProtocolRedirects(oldConfig.getAllowCrossProtocolRedirects())
                        .preload(oldConfig.getPreload())
                        .useTextureView(oldConfig.useTextureView())
                        .thumbnailPreview(oldConfig.getThumbnailPreview())
                        .mute(oldConfig.getMute())
                        .build();

                mPlayer.setup(config);

                // Restore the real controls visibility now that the UI group is guaranteed
                // to be present (setup() above may have re-enabled the control bar). Routed
                // through reapplyControlsAfterSetup so a new item opened while the window is
                // in PiP cannot bring the oversized control bar back into the PiP window.
                reapplyControlsAfterSetup(currentControlsState);

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
        
        // Now manage controls state via API (after setup, when UI groups are in clean state).
        // Both branches go through the PiP-aware helpers so a reconfigure triggered while the
        // window is in PiP cannot re-enable the oversized control bar (see setControlsRequested).
        if (prop.hasKey("controls")) {
            // Developer explicitly set controls in props - use that value
            setControlsRequested(prop.getBoolean("controls"));
        } else if (!currentControlsState) {
            // Controls were off before reconfigure and no explicit prop provided
            // Restore the off state (after ensuring UI groups are visible)
            reapplyControlsAfterSetup(false);
        }
        // Note: If controls were on and no prop provided, they'll stay on (default from configureUI)
        
        // Restore fullscreen state if needed
        // The fullscreen view is still active but internals need to be notified
        if (wasFullscreen) {
            mPlayer.setFullscreen(true, true);
        }
    }

    /**
     * Checks for IMA configuration when IMA is disabled and logs a warning.
     * 
     * @param obj The JSONObject to check (for JSON parser path)
     * @param prop The ReadableMap to check (for legacy builder path)
     */
    private void checkAndWarnImaConfig(JSONObject obj, ReadableMap prop) {
        if (BuildConfig.USE_IMA) {
            return; // IMA is enabled, no warning needed
        }
        
        String clientValue = getClientValue(obj, prop);
        
        if (clientValue != null && isImaClient(clientValue)) {
            String warningMessage = "⚠️ Google IMA advertising is not enabled. " +
                "To use IMA ads, add 'RNJWPlayerUseGoogleIMA = true' to your app/build.gradle ext {} block. " +
                "Current client: " + clientValue + ". Player will load without ads.";
            // Merge fix (headless <- headless-v1.6.0): upstream used a raw android.util.Log here,
            // which the headless line does not import -- all logging is funnelled through JWLog so
            // it can be gated centrally. Routed through JWLog.w to keep that invariant.
            JWLog.w(TAG, warningMessage);
        }
    }
    
    /**
     * Extracts the client value from either JSONObject or ReadableMap
     */
    private String getClientValue(JSONObject obj, ReadableMap prop) {
        // Check JSON object (for JSON parser path)
        if (obj != null && obj.has("advertising")) {
            try {
                JSONObject advertising = obj.getJSONObject("advertising");
                if (advertising.has("client")) {
                    return advertising.getString("client");
                } else if (advertising.has("adClient")) {
                    return advertising.getString("adClient");
                }
            } catch (Exception e) {
                // Silently continue if we can't parse
            }
        }
        
        // Check ReadableMap (for legacy builder path)
        if (prop != null && prop.hasKey("advertising")) {
            ReadableMap advertising = prop.getMap("advertising");
            if (advertising != null) {
                if (advertising.hasKey("client")) {
                    return advertising.getString("client");
                } else if (advertising.hasKey("adClient")) {
                    return advertising.getString("adClient");
                }
            }
        }
        
        return null;
    }
    
    /**
     * Checks if a client value indicates IMA usage
     */
    private boolean isImaClient(String clientValue) {
        if (clientValue == null) {
            return false;
        }
        return "ima".equalsIgnoreCase(clientValue) || 
               "ima_dai".equalsIgnoreCase(clientValue) ||
               "GoogleIMA".equalsIgnoreCase(clientValue) || 
               "GoogleIMADAI".equalsIgnoreCase(clientValue) ||
               "IMA_DAI".equalsIgnoreCase(clientValue) || 
               "googima".equalsIgnoreCase(clientValue);
    }
    
    /**
     * Checks if advertising config contains IMA when IMA is disabled.
     * Used to determine if we should skip configureAdvertising() in legacy builder.
     */
    private boolean shouldSkipAdvertising(ReadableMap prop) {
        if (BuildConfig.USE_IMA || !prop.hasKey("advertising")) {
            return false;
        }
        
        ReadableMap advertising = prop.getMap("advertising");
        if (advertising == null) {
            return false;
        }
        
        String clientValue = null;
        if (advertising.hasKey("client")) {
            clientValue = advertising.getString("client");
        } else if (advertising.hasKey("adClient")) {
            clientValue = advertising.getString("adClient");
        }
        
        return isImaClient(clientValue);
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
                
                // Check for IMA config and log warning if IMA is disabled
                // Don't modify JSON - let parser handle it internally
                checkAndWarnImaConfig(obj, null);
                
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
        
        // Check and warn about IMA config, then conditionally configure advertising
        checkAndWarnImaConfig(null, prop);
        if (!shouldSkipAdvertising(prop)) {
            configureAdvertising(configBuilder, prop);
        }
        
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
     * Used by setConfig()'s playlist-only fast path to avoid a full player
     * stop/recreate on next/prev navigation.
     */
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

    /**
     * Returns the first playlist item's "file" URL from a raw RN config prop, or null when it
     * cannot be determined. Used by the playlist-only fast path in {@link #setConfig} to tell a
     * same-track foreground rebuild (resume-sync only changes starttime) apart from a genuine
     * new-track open. Reads the RN prop directly rather than the SDK's parsed PlaylistItem so
     * both sides of the comparison use the identical, unnormalized string.
     *
     * Returning null on any surprise is intentional: it disables the pause-preserving autostart
     * override for that update rather than risking an incorrect suppression of playback.
     */
    private String firstPlaylistFileFromConfig(ReadableMap configProp) {
        try {
            if (configProp != null && configProp.hasKey("playlist")) {
                ReadableArray playlist = configProp.getArray("playlist");
                if (playlist != null && playlist.size() > 0) {
                    ReadableMap first = playlist.getMap(0);
                    if (first != null && first.hasKey("file") && !first.isNull("file")) {
                        return first.getString("file");
                    }
                }
            }
        } catch (Throwable ignored) {
            // Fall through to null (see method doc).
        }
        return null;
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
                
                // Check for IMA config and log warning if IMA is disabled
                // Don't modify JSON - let parser handle it internally
                checkAndWarnImaConfig(obj, null);
                
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
            
            // Check and warn about IMA config, then conditionally configure advertising
            checkAndWarnImaConfig(null, prop);
            if (!shouldSkipAdvertising(prop)) {
                configureAdvertising(configBuilder, prop);
            }
            
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
            // PiP-aware: a player recreated while the window is in PiP must stay control-less
            // and only adopt the prop's value on PiP exit (see setControlsRequested).
            setControlsRequested(prop.getBoolean("controls"));
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

        // Start and bind the mediaPlayback FGS before setup() can begin remote source loading.
        // This keeps UI -> headless replacement eligible under Battery Saver and also satisfies
        // Android 15's requirement that an app be top-visible or already running an FGS before
        // background audio focus is requested.
        audioManager = (AudioManager) simpleContext.getSystemService(Context.AUDIO_SERVICE);
        if (prop.hasKey("backgroundAudioEnabled")) {
            backgroundAudioEnabled = prop.getBoolean("backgroundAudioEnabled");
        }
        setupMediaSessionHelper();
        if (backgroundAudioEnabled) {
            doBindService();
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
            mPipEnabled = prop.getBoolean("pipEnabled");
            if (mPipEnabled) {
                registerReceiver();
                // Registration itself is decided by updatePipRegistration so the pipVideoOnly
                // opt-in can withhold PiP for audio-only media.
                mPipRegisteredForVideo = null;
                updatePipRegistration();
            } else {
                mPlayer.deregisterActivityForPip();
                unRegisterReceiver();
                unregisterPipBackCallback();
                mPipRegisteredForVideo = null;
            }
        }

        // Legacy styling support
        // NOTE: This isn't the ideal way to do this on Android. All drawables/colors/themes should
        // be targeted using styling. See https://docs.jwplayer.com/players/docs/android-styling-guide
        applyLegacyStyling();

        setupPlayerView(backgroundAudioEnabled, playlistItemCallbackEnabled);
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
                .owner("ui", mMediaGeneration)
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
                        PlaybackManager.getInstance().setUiAudioFocus(true);
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
                PlaybackManager.getInstance().setUiAudioFocus(true);
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
                    PlaybackManager.getInstance().setUiAudioFocus(false);
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
                        PlaybackManager.getInstance().setUiAudioFocus(false);
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
        event.putInt("type", Util.getAdEventTypeValue(Util.AdEventType.JWAdEventTypeLoaded));
        getReactContext().getJSModule(RCTEventEmitter.class).receiveEvent(getId(), "topAdEvent", event);
    }

    @Override
    public void onAdLoadedXml(AdLoadedXmlEvent adLoadedXmlEvent) {
        JWLog.d(TAG, "onAdLoadedXml(client=" + Util.getAdEventClientValue(adLoadedXmlEvent) + ")");
        WritableMap event = Arguments.createMap();
        event.putString("message", "onAdEvent");
        event.putInt("client", Util.getAdEventClientValue(adLoadedXmlEvent));
        event.putInt("type", Util.getAdEventTypeValue(Util.AdEventType.JWAdEventTypeLoadedXml));
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
        event.putInt("type", Util.getAdEventTypeValue(Util.AdEventType.JWAdEventTypeAdBreakIgnored));
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
        // The SDK announcing its control bar is up is the most direct signal that the PiP overlay
        // suppression needs re-asserting — this is the event that exposed setControls(false) as
        // insufficient (see hideJwUiForPip).
        if (controlBarVisibilityEvent.isVisible()) {
            enforcePipUiHidden();
        }
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
        // Remember what we advanced AWAY from. Used by the fast path to recognise a stale
        // foreground re-push: the RN layer does not always learn about a native/SDK item
        // advance, so on the next foreground transition it can re-assert the item that was
        // playing BEFORE the advance. See resolveStaleRevertPush().
        String incomingFile = playlistItemFile(playlistItemEvent.getPlaylistItem());
        if (incomingFile != null && !incomingFile.equals(mCurrentItemFile)) {
            mPreviousItemFile = mCurrentItemFile;
            mCurrentItemFile = incomingFile;
            JWLog.d(TAG, "onPlaylistItem: item identity " + mPreviousItemFile + " -> " + mCurrentItemFile);
        }
        // A new item may change audio-only vs video, so the pipVideoOnly answer is re-derived from
        // the next MetaEvent rather than carried over.
        resetVideoTrackDetection();
        // Ideally done in onFirstFrame instead
        // if (backgroundAudioEnabled) {
        //     doBindService();
        // }

        currentPlayingIndex = playlistItemEvent.getIndex();

        WritableMap event = Arguments.createMap();
        event.putString("message", "onPlaylistItem");
        event.putInt("index", playlistItemEvent.getIndex());
        String json = JsonHelper.toJson(playlistItemEvent.getPlaylistItem());
        JWLog.d(TAG, "PlaylistItem JSON: " + json);
        event.putString("playlistItem", json);
        getReactContext().getJSModule(RCTEventEmitter.class).receiveEvent(getId(), "topPlaylistItem", event);
    }

    @Override
    public void onPlaylist(PlaylistEvent playlistEvent) {
        JWLog.d(TAG, "onPlaylist()");
        WritableMap event = Arguments.createMap();
        event.putString("message", "onPlaylist");
        java.util.List<PlaylistItem> items = playlistEvent.getPlaylist();
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < items.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append(JsonHelper.toJson(items.get(i)));
        }
        sb.append("]");
        event.putString("playlist", sb.toString());
        getReactContext().getJSModule(RCTEventEmitter.class).receiveEvent(getId(), "topPlaylist", event);
    }

    @Override
    public void onPlaylistItemMetadataChanged(PlaylistItemMetadataChangedEvent playlistItemMetadataChangedEvent) {
        WritableMap event = Arguments.createMap();
        event.putString("message", "onPlaylistItemMetadataChanged");
        event.putInt("index", playlistItemMetadataChangedEvent.getIndex());
        event.putString("playlistItem", JsonHelper.toJson(playlistItemMetadataChangedEvent.getPlaylistItem()));
        getReactContext().getJSModule(RCTEventEmitter.class).receiveEvent(getId(), "topPlaylistItemMetadataChanged", event);
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
        if (mPlayer != null) {
            event.putDouble("at", mPlayer.getPosition());
        }
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
        // Feeds the pipVideoOnly gate: Metadata carries the decoded track dimensions and mime
        // types, which is the only reliable way to tell an audio-only item from a video one before
        // the user folds the app. See noteTrackMetadata().
        try {
            com.jwplayer.pub.api.media.meta.Metadata metadata =
                    metaEvent != null ? metaEvent.getMetadata() : null;
            if (metadata != null) {
                noteTrackMetadata(metadata.getWidth(), metadata.getHeight(),
                        metadata.getVideoMimeType(), metadata.getAudioMimeType());
            }
        } catch (Throwable t) {
            JWLog.w(TAG, "onMeta: track metadata read failed: " + t.getMessage());
        }
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
            releaseMediaService(false, "cast-started");
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
                    .owner("ui", mMediaGeneration)
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
        // Same reason as the PiP-exit capture: this runs before the RN config re-push, and covers
        // the plain lock-screen / background return where no PiP transition occurred.
        captureForegroundRebuildPlayhead("host-resume");
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
