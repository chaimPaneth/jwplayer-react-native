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
import com.jwplayer.pub.api.events.TimeEvent;
import com.jwplayer.pub.api.events.listeners.AdvertisingEvents;
import com.jwplayer.pub.api.events.listeners.VideoPlayerEvents;
import com.jwplayer.pub.api.media.playlists.PlaylistItem;
import com.jwplayer.rnjwplayer.utils.JWLog;

import org.json.JSONException;
import org.json.JSONObject;

public class RNJWMediaSessionHelper implements AdvertisingEvents.OnAdCompleteListener, AdvertisingEvents.OnAdErrorListener, AdvertisingEvents.OnAdPlayListener, AdvertisingEvents.OnAdSkippedListener, VideoPlayerEvents.OnBufferListener, VideoPlayerEvents.OnErrorListener, VideoPlayerEvents.OnPauseListener, VideoPlayerEvents.OnPlayListener, VideoPlayerEvents.OnPlaylistCompleteListener, VideoPlayerEvents.OnPlaylistItemListener, VideoPlayerEvents.OnSeekListener, VideoPlayerEvents.OnSeekedListener, VideoPlayerEvents.OnTimeListener {
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

    // --- Background-streaming keep-alive locks -------------------------------------------------
    // Held only while audio is actively playing/buffering so a STREAMED shiur keeps playing when
    // the screen is off under Battery Saver. Without a high-performance Wi-Fi lock, Android puts
    // the Wi-Fi radio into aggressive power-save (DTIM multiplier) on screen-off + Battery Saver;
    // once the pre-buffer drains (~50s) HLS segment loads fail with UnknownHostException and the
    // player fatally errors. This mirrors what ExoPlayer's setWakeMode(WAKE_MODE_NETWORK) does,
    // which the JWPlayer SDK does not expose to the RN wrapper. Released on pause/stop/error/
    // cleanup so idle playback never drains the battery. Only matters for network media; local
    // downloads are unaffected either way.
    private android.net.wifi.WifiManager.WifiLock wifiLock;
    private android.os.PowerManager.WakeLock wakeLock;
    private static final String WIFI_LOCK_TAG = "RNJWPlayer:wifi";
    private static final String WAKE_LOCK_TAG = "RNJWPlayer:wake";

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

    // [PLAYLIST-ADVANCE-FIX] RC-5 / 1d: dedupe rapid duplicate playFromMediaId dispatches for the
    // SAME item. The auto-advance was delivered twice ~1.3s apart, creating two background players
    // (ExoPlayer churn) while the network was blocked. A second selection of the same mediaId within
    // the window is dropped as already-in-flight.
    private static volatile String lastSelectionMediaId = null;
    private static volatile long lastSelectionAtMs = 0L;
    private static final long SELECTION_DEDUPE_WINDOW_MS = 3000L;

    // Stores the postId-format mediaId originally selected from Android Auto
    // (e.g. "175936" or "post-175936"). Unlike `externalMediaId`, this is NOT
    // overwritten by JW playlist callbacks (onPlaylistItem / storeSeekPosition)
    // which legitimately track the JW Player media ID (e.g. "J56s1rL4").
    // Used when emitting Next/Previous events so the JS HeadlessTaskHandler can
    // resolve the correct postId via SeriesNavigationService.
    private static String androidAutoSelectedMediaId = null;

    // App-provided post-id mediaId captured from the RN playlist prop at config
    // time (RNJWPlayerView.setConfig), BEFORE JW's JsonHelper config parser drops
    // it. For JW-hosted video the JW-inferred id is a content UUID (e.g. "1RrnrpNg"),
    // not a numeric OU post id, so onPlaylistComplete would otherwise emit an id the
    // JS HeadlessTaskHandler cannot map to a post — breaking background/locked
    // auto-advance for video. This preserves the numeric post id for the completion
    // event. Like androidAutoSelectedMediaId, it is NOT overwritten by JW callbacks.
    private static String appProvidedMediaId = null;

    // Whether the app's most recent setAppProvidedMediaId CHANGED the post or merely re-asserted
    // the one it already held. The distinction is the difference between a user action and stale
    // state: setConfig re-runs on every player rebuild (notably a PIP/lock return) and re-asserts
    // the app's current post, whereas navigating to another post moves the id. RNJWPlayerView
    // needs it to decide whether an incoming config that switches item is an instruction or a
    // regression -- see shouldKeepLiveItemForForegroundRebuild().
    private static boolean lastAppPushChangedTrack = false;

    /**
     * True when the app's last-asserted post is NOT the item Android Auto selected.
     *
     * Both ids are written only by authoritative events (an explicit Android Auto selection, or
     * the app declaring which post it is loading), so a divergence means one side is behind. When
     * it coincides with the app merely re-asserting its previous post
     * ({@link #didLastAppPushChangeTrack()} == false), the app is the stale one.
     */
    public static boolean isAppTrackStaleVsAndroidAuto() {
        return appProvidedMediaId != null
                && androidAutoSelectedMediaId != null
                && !appProvidedMediaId.equals(androidAutoSelectedMediaId);
    }

    /** @see #lastAppPushChangedTrack */
    public static boolean didLastAppPushChangeTrack() {
        return lastAppPushChangedTrack;
    }

    /**
     * One-line dump of the track-identity state the foreground-rebuild gates decide on.
     *
     * Added 2026-08-30 because a user report -- opening a Series B item while in PiP loaded a
     * Series A item instead -- could not be attributed from the log. The gate returns early on
     * four separate conditions and only logged the one that refused, so a refusal that never
     * happened and a refusal that happened for a different reason were indistinguishable.
     */
    public static String describeTrackState() {
        return "appProvidedMediaId=" + appProvidedMediaId
                + ", androidAutoSelectedMediaId=" + androidAutoSelectedMediaId
                + ", lastAppPushChangedTrack=" + lastAppPushChangedTrack
                + ", appTrackStaleVsAA=" + isAppTrackStaleVsAndroidAuto();
    }

    // --- cross-track start leak -------------------------------------------------------
    // The media id the last onPlaylistItem was for, and when it last CHANGED. Needed
    // because an explicit start supplied by JS is only about the item JS believes is
    // current: when the native layer advances by itself (background auto-advance, an
    // Android Auto selection), JS keeps reporting the position of the item it still
    // thinks is playing, and that value then lands on the NEW item.
    //
    // Observed 2026-08-30 13:35 on a locked phone: item 87674 completed naturally at
    // 27:57 of 28:57, the app advanced to 87728, and the resume resolved twice --
    //   13:35:52.136  savedPositionMs=0ms (MediaItemsResumeProvider), playlistStartMs=0
    //                 -> resumeMs=0                                     (correct)
    //   13:35:52.728  savedPositionMs=0ms, playlistStartMs=12000
    //                 -> resumeMs=12000  (explicit start wins)          (WRONG)
    // and 12000ms was then PERSISTED as 87728's saved position. The user lost the first
    // 12 seconds of a fresh daf, and it would resume there next time. 12000 was RN's own
    // stale live-position tracker, which had been reading a rebuilding player.
    private static String lastPlaylistItemMediaId = null;
    private static long trackSwitchedAtMs = 0L;
    /**
     * NO LONGER AN AUTHORITY (2026-09-22). This window used to decide whether an explicit JS start
     * could be trusted across a track change. It was replaced by the identity/provenance latch below
     * because a timing window is not ownership: in the operator's own reproduction the window had
     * closed, so the outgoing item's playhead was applied to the incoming item. `trackSwitchedAtMs`
     * and this constant are retained for diagnostics only — nothing gates on them.
     */
    private static final long CROSS_TRACK_START_WINDOW_MS = 10_000L;
    /**
     * INVARIANT 2: the item we most recently switched to and for which no explicit start has yet been
     * vetted, plus whether React itself caused that switch. Together these decide whether an explicit
     * start is React's own intent for the incoming item or an adoption carrying the OUTGOING item's
     * playhead. Cleared once a start has been evaluated for that item.
     */
    private static volatile String crossTrackUnverifiedMediaId = null;
    private static volatile boolean crossTrackSwitchWasReactInitiated = false;
    /** Agreement tolerance: below this the two sources are saying the same thing. */
    private static final long CROSS_TRACK_AGREEMENT_MS = 3_000L;
    private final SeekAcceptanceState seekState = new SeekAcceptanceState();
    /**
     * Authoritative position outside the seek window. See {@link PositionModel} — it accepts an SDK
     * sample only when the sample agrees with where playback must be, or when a command we issued
     * explains the disagreement, and it reacquires on bounded coherent disagreement so it can never
     * be stranded on a stale anchor.
     */
    private final PositionModel positionModel = new PositionModel();
    // Throttles the per-publish seek-disagreement diagnostics (PROJECTED/HELD/UNCONFIRMED): these
    // are emitted on every resolvePublishPositionMs/onTime call while a target is pending, which
    // measured 2026-09-18 at ~20Hz and wrapped the entire logcat main buffer in well under 1s.
    private static final long SEEK_DISAGREEMENT_LOG_INTERVAL_MS = 1_000L;
    private long lastSeekDisagreementLogAtMs = 0L;
    /** DIAGNOSTIC: at most one POSITION_TRUTH line per second (onTime ticks far faster). */
    private static final long POSITION_TRUTH_LOG_INTERVAL_MS = 1_000L;
    private long lastPositionTruthLogAtMs = 0L;
    /**
     * DIAGNOSTIC: how far a published position must fall BEHIND the anchor already being served
     * before it is flagged. Above the 3s agreement tolerance so ordinary jitter and rebuffer
     * rounding stay quiet, low enough to catch the measured 1213ms-after-953931ms collapse.
     */
    private static final long ANCHOR_REGRESSION_LOG_THRESHOLD_MS = 5_000L;
    /**
     * How far the position a controller is DISPLAYING may drift from the model before the session is
     * corrected. Above the model's own 1.5s agreement tolerance so ordinary jitter never triggers a
     * write, far below the 42-minute divergence measured on 2026-09-19.
     */
    private static final long RECONCILE_DIVERGENCE_THRESHOLD_MS = 3_000L;
    private long lastSeekDisagreementLogTargetMs = Long.MIN_VALUE;

    /**
     * TEMPORARY DIAGNOSTICS -- remove with the other [AAPIP] logging once the Android Auto
     * desync work is confirmed.
     *
     * Reports the ids native currently believes are playing, regardless of WHICH player owns
     * playback. `checkForActiveHeadlessPlayback` cannot answer this: it only describes the
     * background player, and returns "no active headless playback" whenever the UI player owns
     * the session -- which is exactly the case during locked-phone Android Auto skips, so JS
     * had no way to notice its React state had gone stale.
     *
     * Index 0 = resolved app-post id (the one JS can map to a post), 1 = androidAutoSelectedMediaId,
     * 2 = externalMediaId, 3 = appProvidedMediaId. Any element may be null.
     */
    public static String[] getPlaybackIdentitySnapshot() {
        String resolved = null;
        if (isAppPostMediaId(androidAutoSelectedMediaId)) {
            resolved = androidAutoSelectedMediaId.trim();
        } else if (isAppPostMediaId(externalMediaId)) {
            resolved = externalMediaId.trim();
        } else if (isAppPostMediaId(appProvidedMediaId)) {
            resolved = appProvidedMediaId.trim();
        }
        return new String[] {
            resolved, androidAutoSelectedMediaId, externalMediaId, appProvidedMediaId
        };
    }

    /** TEMPORARY DIAGNOSTICS -- live position in ms of whichever player is active, or -1. */
    public static long getLivePositionMsSnapshot() {
        RNJWMediaSessionHelper helper = activeInstance;
        if (helper == null || helper.jwPlayer == null) return -1L;
        try {
            return (long) (helper.jwPlayer.getPosition() * 1000.0);
        } catch (Exception e) {
            return -1L;
        }
    }

    /** TEMPORARY DIAGNOSTICS -- player state of whichever player is active, or "none". */
    public static String getPlayerStateSnapshot() {
        RNJWMediaSessionHelper helper = activeInstance;
        if (helper == null || helper.jwPlayer == null) return "none";
        try {
            return String.valueOf(helper.jwPlayer.getState());
        } catch (Exception e) {
            return "unknown";
        }
    }

    /**
     * Stores the app-provided post-id mediaId (numeric or "post-<n>") taken from the
     * RN playlist prop. Called from RNJWPlayerView.setConfig before the JW config
     * parser strips it. Non-app-post ids (e.g. JW content UUIDs) are ignored.
     */
    public static void setAppProvidedMediaId(String mediaId) {
        if (isAppPostMediaId(mediaId)) {
            String incoming = mediaId.trim();
            // Only an actual app-driven track CHANGE may retire androidAutoSelectedMediaId.
            // setConfig re-runs whenever the player view is rebuilt — notably on returning
            // from PIP — and then re-asserts the SAME post the app already held. Retiring on
            // such a no-op re-assert clobbered a newer Android Auto selection made while the
            // app was backgrounded (locked phone + AA skip), so the app snapped back to the
            // pre-PIP track. Comparing against the previous appProvidedMediaId distinguishes
            // the two: a real app-driven change (background auto-advance, the case this
            // retire exists for) moves the id, a rebuild re-assert does not.
            boolean appTrackChanged = !incoming.equals(appProvidedMediaId);
            lastAppPushChangedTrack = appTrackChanged;
            appProvidedMediaId = incoming;
            // The app just told us which post it is loading, which makes this the freshest
            // authoritative id we have — so retire a now-stale androidAutoSelectedMediaId.
            // That field is only written on an explicit Android Auto selection or when the
            // live JW item happens to expose an app-post id; for JW-hosted media the live id
            // is a content UUID, so an app-driven track change (notably background
            // auto-advance) used to leave it pointing at the PREVIOUS post. Because both
            // resolvers check it FIRST, a skip then navigated from the wrong post — landing
            // back on the track already playing and restarting it at 0:00 — and completion
            // could advance to the just-finished track in a loop. An explicit Android Auto
            // selection still wins: finishMediaItemSelection/handlePlayFromMediaId write this
            // field after the fact.
            if (appTrackChanged && !appProvidedMediaId.equals(androidAutoSelectedMediaId)) {
                JWLog.d(TAG, "setAppProvidedMediaId: retiring stale androidAutoSelectedMediaId="
                        + androidAutoSelectedMediaId + " -> " + appProvidedMediaId);
                androidAutoSelectedMediaId = appProvidedMediaId;
            } else if (!appTrackChanged
                    && !appProvidedMediaId.equals(androidAutoSelectedMediaId)) {
                JWLog.d(TAG, "setAppProvidedMediaId: KEEPING newer androidAutoSelectedMediaId="
                        + androidAutoSelectedMediaId + " (app re-asserted unchanged "
                        + appProvidedMediaId + ", likely a player rebuild)");
            }
            JWLog.d(TAG, "setAppProvidedMediaId: appProvidedMediaId=" + appProvidedMediaId
                    + ", androidAutoSelectedMediaId=" + androidAutoSelectedMediaId
                    + ", appTrackChanged=" + appTrackChanged);
        }
    }

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

        // Same fallback resolveMediaIdForCompletion already has, and for the same
        // reason: when playback was started from the app (not picked in Android Auto)
        // the live id is a JW content UUID, so both checks above miss and we would hand
        // the UUID to JS. The JS skip handler only accepts an app post id ("123" /
        // "post-<n>"), rejects the UUID, and does nothing — while the native fallback
        // below still runs and restarts the current item at 0:00, because suppression
        // also requires an app-post id. Falling back to the id the app supplied on the
        // playlist item fixes both halves: JS can resolve the post and advance, and the
        // native fallback is correctly suppressed in favour of the RN-owned queue.
        if (isAppPostMediaId(appProvidedMediaId)) {
            return appProvidedMediaId.trim();
        }

        return androidAutoSelectedMediaId != null ? androidAutoSelectedMediaId : externalMediaId;
    }

    /**
     * Resolves the mediaId to emit on playlist completion (auto-advance on finish).
     * Prefers an app-post-format id (numeric / "post-<n>") so the JS
     * HeadlessTaskHandler can map it to a post and advance. This matters most when
     * the screen is locked / app is backgrounded, where this MediaSession completion
     * event is the ONLY working advance path (the JS view-event path is dormant).
     * For JW-hosted video the live externalMediaId is a JW content UUID, so we fall
     * back to the app-provided id captured at config time. Pure read: no side effects.
     */
    private String resolveMediaIdForCompletion() {
        if (isAppPostMediaId(androidAutoSelectedMediaId)) {
            return androidAutoSelectedMediaId.trim();
        }
        if (isAppPostMediaId(externalMediaId)) {
            return externalMediaId.trim();
        }
        if (isAppPostMediaId(appProvidedMediaId)) {
            return appProvidedMediaId.trim();
        }
        return externalMediaId;
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

    // The app supplies a duration with every playlist item, and that is what first reaches the
    // media session metadata. When it understates the real media length, Android Auto renders a
    // position past the end of a progress bar that is already pinned at 100% (observed: a 2448s
    // stream declared as 2436s showed "40:44 / 40:36"). Once the player knows the real length we
    // republish it. Only material disagreements are acted on, so a normal item never republishes.
    private static final long DURATION_REFRESH_MIN_DELTA_MS = 1500L;
    private long lastRepublishedDurationMs = -1L;

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

    /**
     * Republish the session's DURATION metadata once the player knows the real media length.
     *
     * The value published by {@link #updatePlaylistItem} comes from the playlist item, i.e. from
     * whatever the app declared. Reads the live length straight off the player rather than through
     * {@link #getCurrentDurationMs()}, which falls back to the cached declared value and so can
     * never expose a disagreement.
     */
    private void refreshSessionDurationFromPlayer(String reason) {
        if (this.mediaSessionStateProvider == null || this.mediaSessionStateProvider.mediaSessionCompat == null) {
            return;
        }

        double durationSeconds = -1.0;
        try {
            if (jwPlayer != null) {
                durationSeconds = jwPlayer.getDuration();
            } else if (serviceMediaApi != null && serviceMediaApi.getPlayer() != null) {
                durationSeconds = serviceMediaApi.getPlayer().getDuration();
            }
        } catch (Exception durationEx) {
            JWLog.w(TAG, "refreshSessionDurationFromPlayer: duration lookup failed " + durationEx.getMessage());
            return;
        }

        if (durationSeconds <= 0) {
            return; // player does not know the length yet
        }
        long liveDurationMs = (long) (durationSeconds * 1000L);

        MediaMetadataCompat existing;
        try {
            existing = this.mediaSessionStateProvider.mediaSessionCompat.getController().getMetadata();
        } catch (Exception metadataEx) {
            JWLog.w(TAG, "refreshSessionDurationFromPlayer: metadata read failed " + metadataEx.getMessage());
            return;
        }
        if (existing == null) {
            return; // nothing published yet; updatePlaylistItem owns the first publish
        }

        long publishedDurationMs = existing.getLong("android.media.metadata.DURATION");
        if (publishedDurationMs <= 0) {
            return;
        }

        long delta = Math.abs(liveDurationMs - publishedDurationMs);
        if (delta < DURATION_REFRESH_MIN_DELTA_MS) {
            JWLog.d(TAG, "DURATION_REFRESH: live duration " + liveDurationMs + "ms agrees with published "
                    + publishedDurationMs + "ms (delta=" + delta + "ms) reason=" + reason + " -> no change");
            return;
        }

        // Guard against churn from a live duration that wobbles while the stream is still parsing.
        if (lastRepublishedDurationMs > 0
                && Math.abs(liveDurationMs - lastRepublishedDurationMs) < DURATION_REFRESH_MIN_DELTA_MS) {
            return;
        }

        JWLog.w(TAG, "DURATION_REFRESH: live duration " + liveDurationMs + "ms disagrees with app-declared "
                + publishedDurationMs + "ms (delta=" + delta + "ms) reason=" + reason
                + " -> republishing metadata with the live value");

        try {
            MediaMetadataCompat.Builder builder = new MediaMetadataCompat.Builder(existing);
            builder.putLong("android.media.metadata.DURATION", liveDurationMs);
            this.mediaSessionStateProvider.mediaSessionCompat.setMetadata(builder.build());
            lastKnownDurationMs = liveDurationMs;
            lastRepublishedDurationMs = liveDurationMs;

            if (this.serviceMediaApi != null
                    && this.rnjwNotificationHelper != null
                    && this.mediaSessionStateProvider.mediaSessionCompat.isActive()) {
                this.rnjwNotificationHelper.showNotification(this.context, this.mediaSessionStateProvider, this.serviceMediaApi);
            }
        } catch (Exception publishEx) {
            JWLog.w(TAG, "refreshSessionDurationFromPlayer: republish failed " + publishEx.getMessage());
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

        // Never persist a position that disagrees with a seek still in flight. JW 4.26.0 echo events
        // carry the stale playhead (or a spurious 0), and those were overwriting the real resume
        // point: measured 2026-09-16, a seek to 457027ms persisted 0ms, one to 763518ms persisted
        // 1726ms, and a BACKWARD seek to 260403ms persisted 716038ms. Symmetric on purpose — the
        // stale playhead sits ahead of the target on a backward seek. Not gated on the AA handoff,
        // because the corruption was reproduced on an ordinary user seek.
        if (seekState.blocksStore(position, SystemClock.elapsedRealtime())) {
            JWLog.d(TAG, "storeSeekPosition: BLOCKED storing " + position
                    + "ms disagreeing with in-flight seek target " + seekState.targetInFlightMs() + "ms");
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
        if (position == 0 && seekState.targetInFlightMs() != 0L && lastRequestedSeekPositionMs != 0L) {
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
        MediaSessionCompat newSession = RNJWSharedMediaSession.get(self.context);

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
        if (serviceMediaApi != null) {
            if (this.serviceMediaApi != null && this.serviceMediaApi != serviceMediaApi) {
                cleanup();
                activeInstance = this;
            }
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
        this.mediaSessionStateProvider =  new MediaSessionStateProvider(RNJWSharedMediaSession.get(currentContext));
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
        
        this.jwPlayer.addListeners(this, new EventType[]{EventType.PLAY, EventType.PAUSE, EventType.BUFFER, EventType.ERROR, EventType.PLAYLIST_ITEM, EventType.PLAYLIST_COMPLETE, EventType.AD_PLAY, EventType.AD_SKIPPED, EventType.AD_COMPLETE, EventType.AD_ERROR, EventType.SEEK, EventType.SEEKED, EventType.TIME});
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
            // DIAGNOSTIC: a publish that silently does not happen is indistinguishable in a capture
            // from one that was never attempted. Measured 2026-09-19 (capture
            // logcat_android17_2026-09-19_12-19-29): after returning to the app the helper emitted
            // ZERO state writes for 14s while playback continued, so Android Auto extrapolated from
            // a stale anchor and showed a different clock from the app — and nothing said why.
            JWLog.w(TAG, "PUBSKIP[helper.updatePlaybackState] state=" + state
                    + " override=" + overridePositionMs
                    + " reason=" + (player == null ? "player-null"
                        : this.mediaSessionStateProvider == null ? "stateProvider-null"
                        : "mediaSession-null")
                    + " activeInstance=" + (activeInstance == this ? "self" : JWLog.id(activeInstance))
                    + " self=" + JWLog.id(this));
            return;
        }

        long positionMs;
        if (overridePositionMs != null) {
            positionMs = overridePositionMs;
        } else {
            positionMs = resolvePublishPositionMs(player, state);
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

        float speed = state == PlaybackStateCompat.STATE_PLAYING && seekState.targetInFlightMs() < 0
            ? currentSpeed : 0.0f;

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
            logPlaybackStateWrite("helper.updatePlaybackState", state, positionMs, speed,
                    overridePositionMs);
            this.mediaSessionStateProvider.mediaSessionCompat.setPlaybackState(builder.build());
            this.mediaSessionStateProvider.mediaSessionCompat.setActive(true);
        } catch (Exception ex) {
            JWLog.w(TAG, "updatePlaybackState: set failed " + ex.getMessage());
        }

        // Keep the Wi-Fi radio and CPU awake only while actively playing or buffering, so a
        // streamed shiur survives screen-off under Battery Saver. Every playback-state push
        // funnels through this method, so this single toggle covers play/pause/stop/error and
        // the network-recovery paths. Released for every other state to protect battery.
        setPlaybackLocksActive(state == PlaybackStateCompat.STATE_PLAYING
                || state == PlaybackStateCompat.STATE_BUFFERING);

        // By the time we publish PLAYING the player normally knows the real media length, so this
        // is the first point at which an understated app-declared duration can be corrected. No-op
        // unless the two disagree materially.
        if (state == PlaybackStateCompat.STATE_PLAYING) {
            refreshSessionDurationFromPlayer("playing-state-publish");
        }
    }

    /**
     * Acquire or release the background-streaming keep-alive locks (high-performance Wi-Fi lock +
     * partial CPU wake lock). Safe to call repeatedly: acquisition/release is guarded by
     * {@code isHeld()} so it is idempotent and does not spam logs. Never throws to the caller.
     */
    private void setPlaybackLocksActive(boolean active) {
        try {
            if (active) {
                acquirePlaybackLocks();
            } else {
                releasePlaybackLocks();
            }
        } catch (Exception e) {
            JWLog.w(TAG, "setPlaybackLocksActive(" + active + ") failed: " + e.getMessage());
        }
    }

    // Playback wake locks are intentionally held without a timeout: a shiur can run for well
    // over an hour and is released deterministically on pause/stop/error/cleanup. This matches
    // ExoPlayer's own WakeLockManager behavior.
    @android.annotation.SuppressLint("WakelockTimeout")
    private void acquirePlaybackLocks() {
        Context appContext = (this.context != null) ? this.context.getApplicationContext() : null;
        if (appContext == null) {
            return;
        }

        // High-performance Wi-Fi lock: prevents the radio from entering the aggressive power-save
        // mode (DTIM throttling) that Battery Saver + screen-off imposes, which otherwise kills the
        // streaming connection with UnknownHostException once the buffer drains. WIFI_MODE_FULL_HIGH_PERF
        // is intentional over WIFI_MODE_FULL_LOW_LATENCY: low-latency mode only engages while the app
        // is foreground with the screen on -- the opposite of the locked-screen case we must support.
        if (wifiLock == null) {
            android.net.wifi.WifiManager wifiManager =
                    (android.net.wifi.WifiManager) appContext.getSystemService(Context.WIFI_SERVICE);
            if (wifiManager != null) {
                wifiLock = wifiManager.createWifiLock(
                        android.net.wifi.WifiManager.WIFI_MODE_FULL_HIGH_PERF, WIFI_LOCK_TAG);
                if (wifiLock != null) {
                    wifiLock.setReferenceCounted(false);
                }
            }
        }
        if (wifiLock != null && !wifiLock.isHeld()) {
            wifiLock.acquire();
            JWLog.d(TAG, "acquirePlaybackLocks: WifiLock (FULL_HIGH_PERF) acquired");
        }

        // Partial CPU wake lock: keeps the CPU running so buffering/decoding continues during
        // Doze / Battery Saver while the screen is off. Mirrors ExoPlayer's WAKE_MODE_NETWORK.
        if (wakeLock == null) {
            android.os.PowerManager powerManager =
                    (android.os.PowerManager) appContext.getSystemService(Context.POWER_SERVICE);
            if (powerManager != null) {
                wakeLock = powerManager.newWakeLock(
                        android.os.PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG);
                if (wakeLock != null) {
                    wakeLock.setReferenceCounted(false);
                }
            }
        }
        if (wakeLock != null && !wakeLock.isHeld()) {
            wakeLock.acquire();
            JWLog.d(TAG, "acquirePlaybackLocks: partial WakeLock acquired");
        }
    }

    private void releasePlaybackLocks() {
        if (wifiLock != null && wifiLock.isHeld()) {
            try {
                wifiLock.release();
                JWLog.d(TAG, "releasePlaybackLocks: WifiLock released");
            } catch (Exception e) {
                JWLog.w(TAG, "releasePlaybackLocks: WifiLock release failed: " + e.getMessage());
            }
        }
        if (wakeLock != null && wakeLock.isHeld()) {
            try {
                wakeLock.release();
                JWLog.d(TAG, "releasePlaybackLocks: WakeLock released");
            } catch (Exception e) {
                JWLog.w(TAG, "releasePlaybackLocks: WakeLock release failed: " + e.getMessage());
            }
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

        // RNJWPlayerView (the UI layer) independently owns an AudioFocusRequest for the
        // SAME JWPlayer instance whenever it is attached (foreground, PiP, or backgrounded-
        // but-not-yet-destroyed). If we request our own OS-level focus here while the UI
        // already holds it, the system evicts the UI's request and delivers AUDIOFOCUS_LOSS
        // to RNJWPlayerView's listener, which unconditionally pauses mPlayer -- even though
        // the app never actually lost focus overall. This happens on every new playlist item
        // (updatePlaylistItem -> requestAudioFocusForPlayback) and on performPlay/seek, so it
        // can spuriously pause playback mid-background or right after an auto-advance.
        // Defer to the UI's existing grant instead of competing for a second one.
        if (PlaybackManager.getInstance().hasUiAudioFocus()) {
            JWLog.d(TAG, "requestAudioFocusForPlayback: UI already holds audio focus, skipping duplicate OS request");
            currentlyHasFocus = true;
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
        if (positionMs >= 0 && !seekState.blocksStore(positionMs, SystemClock.elapsedRealtime())) {
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
        detachForTransfer();
    }

    /**
     * Detaches an outgoing player without publishing STATE_NONE, clearing metadata, cancelling
     * the notification, or deactivating the shared MediaSession. The started service owns those
     * session-level decisions while a successor player is being attached.
     */
    public final void detachForTransfer() {
        JWLog.d(TAG, "detachForTransfer() - preserving MediaSession for owner replacement");

        if (activeInstance == this) {
            activeInstance = null;
        }

        releaseAudioFocus();
        releasePlaybackLocks();
        teardownNetworkCallback();

        if (mediaButtonFallbackReceiver != null) {
            try {
                context.unregisterReceiver(mediaButtonFallbackReceiver);
            } catch (Exception unregEx) {
                JWLog.w(TAG, "detachForTransfer() - failed to unregister fallback receiver: " + unregEx.getMessage());
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
                                    EventType.SEEK, EventType.SEEKED, EventType.TIME});
                JWLog.d(TAG, "detachForTransfer() - removed player listeners");
            } catch (Exception e) {
                JWLog.w(TAG, "detachForTransfer() - error removing listeners: " + e.getMessage());
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
        releasePlaybackLocks();
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
                new EventType[]{EventType.PLAY, EventType.PAUSE, EventType.BUFFER, EventType.ERROR, EventType.PLAYLIST_ITEM, EventType.PLAYLIST_COMPLETE, EventType.AD_PLAY, EventType.AD_SKIPPED, EventType.AD_COMPLETE, EventType.AD_ERROR, EventType.SEEK, EventType.SEEKED, EventType.TIME});
            (notificationHelper = this.rnjwNotificationHelper).notificationManager.cancel(notificationHelper.notificationId);
            // Forced to ERROR level deliberately — see updatePlayerState's cancel tag. Field reads
            // and string literals only; no method calls inside the concatenation.
            JWLog.e("RNJWNotificationHelper", "cancel(id=" + notificationHelper.notificationId
                    + ", owner=RNJWMediaSessionHelper.softCleanup, mediaStyle=true"
                    + ", branch=hasPlayer)");
            this.jwPlayer = null;
        } else {
            // Even if jwPlayer is null, make sure the media notification is hidden
            try {
                (notificationHelper = this.rnjwNotificationHelper).notificationManager.cancel(notificationHelper.notificationId);
                JWLog.e("RNJWNotificationHelper", "cancel(id=" + notificationHelper.notificationId
                        + ", owner=RNJWMediaSessionHelper.softCleanup, mediaStyle=true"
                        + ", branch=noPlayer)");
            } catch (Exception ignore) {}
        }
    }

    /**
     * Resume position for a media selection, in ms, or -1 when genuinely unknown.
     *
     * Prefers the "timepoint" carried in the selection's extras, then falls back to the position
     * already stored on the device for that media id. The fallback matters because an Android Auto
     * skip hands over a post from the series-navigation query, which returns a duration but NO
     * stored position (measured: tp=undefined on every skip dispatch while dur was populated).
     * With no resume target, hasPendingAndroidAutoResume stays false in onPlaylistItem and the
     * track starts at 0:00 — the "always 0:00 when skipping from Android Auto while the app is
     * backgrounded or headless" report. MediaItemsResumeProvider already holds the position for the
     * browse-tree item and onPlaylistItem already trusts it, so this arms the existing resume
     * machinery for every selection path, with no network call added to the skip.
     */
    private static long resolveSelectionResumeMs(String mediaId, Bundle extras) {
        long resumeMs = extractResumePosition(extras);
        if (resumeMs >= 0 || !isAppPostMediaId(mediaId)) {
            return resumeMs;
        }
        long providerResumeMs = queryResumeViaReflection(mediaId);
        if (providerResumeMs >= 0) {
            JWLog.d(TAG, "resolveSelectionResumeMs: extras carried no timepoint; using resume"
                    + " provider position " + providerResumeMs + "ms for mediaId=" + mediaId);
            return providerResumeMs;
        }
        return resumeMs;
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
        // Routed through resolvePublishPositionMs, NOT a raw getPosition(): measured 2026-09-16,
        // this writer had the last word after every Android Auto seek and published the PREVIOUS
        // seek's position (606296ms after a seek to 861057ms), which is what made the head unit
        // display one seek behind for the rest of the session.
        long positionMs = 0L;
        try {
            positionMs = this.jwPlayer != null
                    ? resolvePublishPositionMs(this.jwPlayer, playbackState) : 0L;
        } catch (Exception ex) {
            positionMs = 0L;
        }

        float speed = playbackState == PlaybackStateCompat.STATE_PLAYING && seekState.targetInFlightMs() < 0
            ? currentSpeed : 0.0F;
        playbackStateBuilder.builder
            .setState(playbackState, positionMs, speed);

        PlaybackStateCompatWrapper updatedPlaybackState =  new PlaybackStateCompatWrapper(playbackStateBuilder.builder.build());
        logPlaybackStateWrite("helper.updatePlayerState(" + playerState + ")",
                playbackState, positionMs, speed, null);
        this.mediaSessionStateProvider.mediaSessionCompat.setPlaybackState(updatedPlaybackState.playbackStateCompat);
        boolean isActive = playerState != PlayerState.ERROR && playerState != PlayerState.IDLE;
        this.mediaSessionStateProvider.mediaSessionCompat.setActive(isActive);
        if (isActive) {
            this.rnjwNotificationHelper.showNotification(this.context, this.mediaSessionStateProvider, this.serviceMediaApi);
        } else {
            RNJWNotificationHelper currentNotificationHelper;
            (currentNotificationHelper = this.rnjwNotificationHelper).notificationManager.cancel(currentNotificationHelper.notificationId);
            // Forced to ERROR level deliberately: this is the prime suspect for the media
            // notification vanishing, and it must stay visible at MODE=ERROR for the next capture.
            // The two operands are enum reference compares, not method calls — playerState here is
            // necessarily ERROR or IDLE, so the pair identifies the trigger exactly. Nothing inside
            // the concatenation does work, which Java would evaluate regardless of the log level.
            JWLog.e("RNJWNotificationHelper", "cancel(id=" + currentNotificationHelper.notificationId
                    + ", owner=RNJWMediaSessionHelper.updatePlayerState, mediaStyle=true"
                    + ", stateIsError=" + (playerState == PlayerState.ERROR)
                    + ", stateIsIdle=" + (playerState == PlayerState.IDLE) + ")");
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
        JWLog.d(TAG, "metadata-update-before mediaId="
            + playlistItem.getMediaId()
            + ", title=" + playlistItem.getTitle()
            + ", previousMediaId=" + previousMediaId
            + ", previousTitle=" + previousTitle
            + ", " + getCurrentPlaybackDebugInfo("metadata-update"));

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
            // This publish puts the app-declared value back in charge, so allow the player's real
            // length to override it again once it is known for this item.
            lastRepublishedDurationMs = -1L;
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

    /**
     * DIAGNOSTIC: the most recent command that asked for an item to load, and where it came from.
     *
     * Added 2026-09-19. In capture logcat_android17_2026-09-19_12-19-29, finishing a series-A item
     * and immediately picking a series-B item on Android Auto loaded series A's NEXT item instead —
     * twice. Two independent load commands (RN's completion advance and the AA selection) race, and
     * the log showed only the resulting item titles, so which command won had to be inferred. These
     * fields let onPlaylistItem state it outright.
     */
    private static volatile String lastLoadIntentOrigin = null;
    private static volatile String lastLoadIntentMediaId = null;
    private static volatile long lastLoadIntentAtMs = 0L;
    private static volatile String previousLoadIntentOrigin = null;
    private static volatile String previousLoadIntentMediaId = null;

    /**
     * DIAGNOSTIC ONLY (2026-09-21): monotonic ordering for load intents, so which command owns the
     * item that actually loaded is read off an identity rather than inferred from timestamps.
     */
    private static volatile long loadIntentSeqCounter = 0L;
    private static volatile long lastLoadIntentSeq = 0L;
    private static volatile long previousLoadIntentSeq = 0L;

    /** DIAGNOSTIC ONLY (2026-09-21): last completion, kept separate from any load intent. */
    private static volatile String lastCompletionMediaId = null;
    private static volatile long lastCompletionAtMs = 0L;
    private static volatile long completionSeq = 0L;

    // ---------------------------------------------------------------------------------------------
    // INVARIANT 1 — a deferred transport command may not cross a playback-intent boundary.
    //
    // Measured 2026-09-22 (capture pip_swap_20-39-12): a MediaSession `next` issued for item 10644
    // while PiP was active is dispatched to RN, which defers it for the whole PiP session. On PiP
    // exit React re-asserted 10644 (load intent seq 14), and only THEN did the deferred skip execute
    // — MediaBrowser.playFromMediaId(10643) -> aa-selection/10643 seq 15 superseding the newer
    // assertion, loading the WRONG item.
    //
    // Ownership is captured at dispatch: the token, the base item the skip was issued for, and the
    // load-intent sequence current at that moment. When a NEWER authoritative playback intent is
    // established, the claim is killed at its registry (MediaBrowserService.pendingSkipAcks), so the
    // JS owner's atomic consume fails and it never calls playFromMediaId.
    //
    // No wall-clock term: validity is ownership, not age. Entering or leaving PiP is not authority —
    // only a newer playback intent is.
    // ---------------------------------------------------------------------------------------------
    private static volatile String pendingSkipToken = null;
    private static volatile String pendingSkipBaseMediaId = null;
    private static volatile long pendingSkipIntentSeq = -1L;
    private static volatile String pendingSkipDirection = null;

    /** Records the ownership of a skip we just handed to RN. */
    private static void notePendingSkip(
            String skipToken, String baseMediaId, String direction, long ownerIntentSeq) {
        if (skipToken == null) {
            return;
        }
        // The service emits the JS event before returning the token. If another authoritative
        // intent arrived during that hand-off, the command is already stale: invalidate it now
        // rather than arming it under the newer sequence.
        if (lastLoadIntentSeq != ownerIntentSeq) {
            try {
                Class<?> svc = Class.forName("com.mediabrowser.MediaBrowserService");
                java.lang.reflect.Method invalidate =
                        svc.getMethod("invalidateSkip", String.class, String.class);
                invalidate.invoke(null, skipToken,
                        "intent-changed-during-dispatch:" + ownerIntentSeq + "->" + lastLoadIntentSeq);
            } catch (Exception e) {
                JWLog.w(TAG, "SKIPGUARD: could not invalidate dispatch-raced token=" + skipToken
                        + ": " + e.getMessage());
            }
            JWLog.d(TAG, "SKIPGUARD[dispatch-raced] skipToken=" + skipToken
                    + " direction=" + direction + " baseMediaId=" + baseMediaId
                    + " ownerIntentSeq=" + ownerIntentSeq
                    + " currentIntentSeq=" + lastLoadIntentSeq);
            return;
        }
        pendingSkipToken = skipToken;
        pendingSkipBaseMediaId = baseMediaId;
        pendingSkipIntentSeq = ownerIntentSeq;
        pendingSkipDirection = direction;
        JWLog.d(TAG, "SKIPGUARD[armed] skipToken=" + skipToken
                + " direction=" + direction
                + " baseMediaId=" + baseMediaId
                + " ownerIntentSeq=" + pendingSkipIntentSeq);
    }

    /**
     * Kills a pending skip whose owning playback intent has been superseded.
     *
     * Called whenever a new authoritative load intent is recorded. The comparison is purely on the
     * monotonic intent sequence, so it is deterministic and independent of elapsed time. The skip's
     * OWN resulting load does not trip this, because a valid skip claims (removes) its token before
     * loading, so there is no live claim left to kill.
     */
    private static void supersedePendingSkipIfAny(long newIntentSeq, String newOrigin, String newMediaId) {
        String token = pendingSkipToken;
        if (token == null || pendingSkipIntentSeq < 0 || newIntentSeq <= pendingSkipIntentSeq) {
            return;
        }
        boolean killed = false;
        try {
            Class<?> svc = Class.forName("com.mediabrowser.MediaBrowserService");
            java.lang.reflect.Method invalidate =
                    svc.getMethod("invalidateSkip", String.class, String.class);
            Object result = invalidate.invoke(null, token,
                    "superseded-by:" + newOrigin + "/" + newMediaId + "#" + newIntentSeq);
            killed = (result instanceof Boolean) && (Boolean) result;
        } catch (Exception e) {
            JWLog.w(TAG, "SKIPGUARD: could not invalidate skipToken=" + token + ": " + e.getMessage());
        }
        if (killed) {
            JWLog.d(TAG, "SKIPGUARD[superseded] skipToken=" + token
                    + " direction=" + pendingSkipDirection
                    + " baseMediaId=" + pendingSkipBaseMediaId
                    + " ownerIntentSeq=" + pendingSkipIntentSeq
                    + " supersededBy=" + newOrigin + "/" + newMediaId + "#" + newIntentSeq);
        }
        pendingSkipToken = null;
        pendingSkipBaseMediaId = null;
        pendingSkipIntentSeq = -1L;
        pendingSkipDirection = null;
    }

    /**
     * An app-originated seek (RNJWPlayerModule.seekTo) writes STRAIGHT to the player and never passes
     * through {@link #performSeekTo}, so the model would not learn about it — measured 2026-09-19
     * 19:07 during the automated soak: a scrubber tap moved the player back 9 minutes and the session
     * kept serving the old projection, rejecting reality for five ticks until bounded reacquisition
     * converged. Reacquisition doing its job is the right backstop, but a seek we can SEE is a
     * command, not an anomaly, so it should anchor the model immediately.
     *
     * Position-only: this does not touch the seek-acceptance target, because a JS seek carries no
     * MediaSession request to protect.
     */
    public static void noteAppOriginatedSeek(long positionMs) {
        RNJWMediaSessionHelper helper = activeInstance;
        if (helper == null || positionMs < 0) {
            return;
        }
        helper.positionModel.onCommandedPosition(positionMs, SystemClock.elapsedRealtime());
        JWLog.d(TAG, "POSMODEL[app-seek] anchored to commanded " + positionMs + "ms");
    }

    /**
     * DIAGNOSTIC ONLY: records that an item COMPLETED and JS was told.
     *
     * This is deliberately NOT a load intent. Until 2026-09-21 completion was recorded through
     * {@link #noteLoadIntent} as origin "rn-completion-advance", which made it masquerade as a load
     * command: at completion RN has chosen nothing, so the marker always carried mediaId=null, always
     * preceded the user's Android Auto tap, and was always superseded by it. Every traced race
     * therefore read as "the AA selection won" whether or not a later RN load overwrote it, because
     * the RN load itself recorded no intent at all. Completion now has its own marker and leaves the
     * load-intent fields untouched, so an RN load can be attributed to its real entry point.
     *
     * Changes no behaviour.
     */
    public static void noteCompletionEvent(String completedMediaId) {
        lastCompletionMediaId = completedMediaId;
        lastCompletionAtMs = SystemClock.elapsedRealtime();
        completionSeq++;
        if (!JWLog.isVerbose()) {
            return;
        }
        String[] ids = getPlaybackIdentitySnapshot();
        JWLog.d(TAG, "LOADTRACE[completion] origin=rn-completion-event seq=" + completionSeq
                + " completedMediaId=" + completedMediaId
                + " aaId=" + ids[1] + " externalId=" + ids[2] + " appId=" + ids[3]
                + " standingIntent=" + lastLoadIntentOrigin + "/" + lastLoadIntentMediaId
                + "#" + lastLoadIntentSeq
                + " (not a load command; JS decides the advance)");
    }

    /**
     * DIAGNOSTIC ONLY: records who asked for a load, and with what identity. Changes no behaviour.
     *
     * `seq` is a monotonic counter, so two intents can be ordered without reading timestamps — the
     * final {@code LOADTRACE[loaded]} names the seq that owns it. `sinceCompletion` states, as a
     * fact rather than an inference, how long before this load the last completion fired; it is
     * reported, never used to classify the load, because native cannot know whether an RN load is an
     * automatic advance or a user action.
     */
    public static void noteLoadIntent(String origin, String mediaId) {
        previousLoadIntentOrigin = lastLoadIntentOrigin;
        previousLoadIntentMediaId = lastLoadIntentMediaId;
        previousLoadIntentSeq = lastLoadIntentSeq;
        lastLoadIntentOrigin = origin;
        lastLoadIntentMediaId = mediaId;
        lastLoadIntentAtMs = SystemClock.elapsedRealtime();
        lastLoadIntentSeq = ++loadIntentSeqCounter;
        // INVARIANT 1: a newer authoritative playback intent kills any skip claim created under an
        // older one. Runs before the log below so the rejection and its cause appear in order.
        supersedePendingSkipIfAny(lastLoadIntentSeq, origin, mediaId);
        if (!JWLog.isVerbose()) {
            return;
        }
        String[] ids = getPlaybackIdentitySnapshot();
        long sinceCompletionMs = lastCompletionAtMs > 0
                ? SystemClock.elapsedRealtime() - lastCompletionAtMs : -1L;
        JWLog.d(TAG, "LOADTRACE[intent] origin=" + origin + " mediaId=" + mediaId
                + " seq=" + lastLoadIntentSeq
                + " supersedes=" + previousLoadIntentOrigin + "/" + previousLoadIntentMediaId
                + "#" + previousLoadIntentSeq
                + " aaId=" + ids[1] + " externalId=" + ids[2] + " appId=" + ids[3]
                + " sinceCompletion=" + sinceCompletionMs + "ms"
                + " lastCompletedMediaId=" + lastCompletionMediaId);
    }

    /** DIAGNOSTIC ONLY: says which intent the item that actually loaded corresponds to. */
    private void logLoadOutcome(PlaylistItem item) {
        if (!JWLog.isVerbose()) {
            return;
        }
        String loadedId = item != null ? item.getMediaId() : null;
        String loadedTitle = item != null ? item.getTitle() : null;
        long sinceIntentMs = lastLoadIntentAtMs > 0
                ? SystemClock.elapsedRealtime() - lastLoadIntentAtMs : -1L;
        String[] ids = getPlaybackIdentitySnapshot();
        boolean matchesIntent = lastLoadIntentMediaId != null
                && (lastLoadIntentMediaId.equals(loadedId)
                    || lastLoadIntentMediaId.equals(ids[2]) || lastLoadIntentMediaId.equals(ids[1]));
        // A load that matches the SUPERSEDED intent rather than the current one is the signature of
        // a stale command winning — the exact shape O1 claims. Stated, not inferred.
        boolean matchesPrevious = previousLoadIntentMediaId != null
                && (previousLoadIntentMediaId.equals(loadedId)
                    || previousLoadIntentMediaId.equals(ids[2]) || previousLoadIntentMediaId.equals(ids[1]));
        JWLog.d(TAG, "LOADTRACE[loaded] title=" + loadedTitle + " itemMediaId=" + loadedId
                + " externalId=" + ids[2] + " aaId=" + ids[1] + " appId=" + ids[3]
                + " lastIntent=" + lastLoadIntentOrigin + "/" + lastLoadIntentMediaId
                + "#" + lastLoadIntentSeq
                + " (" + sinceIntentMs + "ms ago)"
                + " previousIntent=" + previousLoadIntentOrigin + "/" + previousLoadIntentMediaId
                + "#" + previousLoadIntentSeq
                + " matchesLastIntent=" + matchesIntent
                + " matchesPreviousIntent=" + matchesPrevious
                + " lastCompletion=" + lastCompletionMediaId + "#" + completionSeq
                + " sinceCompletion=" + (lastCompletionAtMs > 0
                        ? (SystemClock.elapsedRealtime() - lastCompletionAtMs) : -1L) + "ms");
    }

    public void onPlaylistItem(PlaylistItemEvent playlistItemEvent) {
        JWLog.d(TAG, "onPlaylistItem(event.item=" + JWLog.playlistItemInfo(playlistItemEvent != null ? playlistItemEvent.getPlaylistItem() : null) + ")", true);
        logLoadOutcome(playlistItemEvent != null ? playlistItemEvent.getPlaylistItem() : null);
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

        // Note when the item the player is on actually CHANGES, so the resume decision
        // below can tell a same-track foreground rebuild (where JS's explicit start is the
        // live position and must win) from a fresh track (where it is the PREVIOUS item's
        // position). Keyed on the item the SDK just handed us, not on externalMediaId,
        // which handlePlayFromMediaId may already have moved.
        String itemKey = (inferredMediaId != null && !inferredMediaId.trim().isEmpty())
                ? inferredMediaId.trim()
                : extractPrimarySourceFile(incomingItem);
        if (itemKey != null && !itemKey.equals(lastPlaylistItemMediaId)) {
            if (lastPlaylistItemMediaId != null) {
                trackSwitchedAtMs = System.currentTimeMillis();
                // INVARIANT 2: arm the identity-bound latch and record WHO caused this switch.
                // React-initiated means the load intent that produced this item came from React
                // itself (rn-setConfig / rn-loadPlaylist for THIS item), in which case an explicit
                // start arriving with it is React's own intent. Anything else (aa-selection, a
                // deferred skip, an advance) means a later React start is an ADOPTION and must prove
                // the position belongs to the incoming item. No elapsed-time term.
                crossTrackUnverifiedMediaId = itemKey;
                crossTrackSwitchWasReactInitiated =
                        lastLoadIntentOrigin != null
                        && lastLoadIntentOrigin.startsWith("rn-")
                        && lastLoadIntentMediaId != null
                        && lastLoadIntentMediaId.equals(itemKey);
                JWLog.d(TAG, "onPlaylistItem: track switched " + lastPlaylistItemMediaId
                        + " -> " + itemKey
                        + " (POSGUARD armed; switchInitiatedByReact="
                        + crossTrackSwitchWasReactInitiated
                        + ", causingIntent=" + lastLoadIntentOrigin + "/" + lastLoadIntentMediaId
                        + "#" + lastLoadIntentSeq + ")");
                // A seek target belongs to the item it was requested on; never carry it across.
                clearSeekTargetInFlight("track-switch " + lastPlaylistItemMediaId + " -> " + itemKey);
                // Nothing about the previous item's position is meaningful for the new one.
                // Measured 2026-09-19 16:40: a track boundary produced live=229942ms against a
                // session already reset to 0, a one-tick divergence of 229942ms. Resetting here
                // means the new item's first sample SEEDS the model rather than being judged
                // against the old item's projection.
                positionModel.reset();
            }
            lastPlaylistItemMediaId = itemKey;
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
            
            // GROUND TRUTH for the resume position is the JS explicit start (playlistStartMs).
            // PlayerCore refreshes it to the LIVE playback position on every foreground
            // transition (it reads lastKnownPositionSeconds, which is updated continuously from
            // native onTime). The static cache, by contrast, is only written on explicit seeks,
            // so it goes STALE during continuous background playback: it holds the advance/seek
            // time position (e.g. 0ms or 11005ms), NOT the live position (e.g. 139000ms).
            // Therefore, whenever JS supplied an explicit start it MUST win over the static
            // cache; the cache is only a fallback for when there is no explicit start at all.
            // (Generalizes the earlier stale-zero guard — the stale cached value is not always 0.)
            //
            // EXCEPT across a track change. That rule assumes JS's value describes the item the
            // player is on, which stops being true the moment the native layer advances by
            // itself: JS keeps reporting the OLD item's position, and it then lands on the new
            // item (see the trackSwitchedAtMs note above -- 12000ms applied to a fresh daf and
            // persisted). Inside the switch window the app's own per-item record wins instead,
            // because that is the only source that is scoped to THIS item. Same-track rebuilds
            // are untouched, which is what the foreground rewind guard depends on.
            // INVARIANT 2 (2026-09-22): position ownership is bound to ITEM IDENTITY and PROVENANCE,
            // never to elapsed time.
            //
            // Measured (capture pip_swap_20-39-12): a native-originated switch 10644 -> 10643 was
            // followed by React ADOPTING 10643 and pushing starttime=648.0 — the outgoing item's
            // playhead — while 10643's own resume provider reported 0ms. The old trigger was
            // "within CROSS_TRACK_START_WINDOW_MS (10s) of the switch", which happened to be open in
            // that capture and CLOSED in the operator's own reproduction, where the stale position
            // was therefore applied. A timing window is not ownership.
            //
            // The trigger is now: this is the first start resolved for an item we switched to, AND
            // that switch was NOT initiated by React. Provenance is read from the load-intent
            // recorder — if React's own rn-setConfig/rn-loadPlaylist for THIS item caused the switch,
            // its explicit start is React's own intent and is honoured; if the switch came from
            // native (aa-selection / a deferred skip / an advance), a start arriving afterwards is an
            // adoption and must prove it belongs to the incoming item.
            //
            // The remedy is unchanged and was already identity-correct: fall back to the INCOMING
            // item's own saved position, never to the outgoing item's.
            boolean startUnverifiedForThisItem = crossTrackUnverifiedMediaId != null
                    && crossTrackUnverifiedMediaId.equals(lastPlaylistItemMediaId)
                    && !crossTrackSwitchWasReactInitiated;
            boolean crossTrackStartSuspect = startUnverifiedForThisItem
                    && hasExplicitStart
                    && savedPositionMs >= 0
                    && Math.abs(playlistStartMs - savedPositionMs) > CROSS_TRACK_AGREEMENT_MS;
            if (hasExplicitStart && crossTrackUnverifiedMediaId != null
                    && crossTrackUnverifiedMediaId.equals(lastPlaylistItemMediaId)) {
                JWLog.d(TAG, "POSGUARD[evaluate] item=" + lastPlaylistItemMediaId
                        + " explicitStart=" + playlistStartMs + "ms"
                        + " itemOwnSaved=" + savedPositionMs + "ms"
                        + " switchInitiatedByReact=" + crossTrackSwitchWasReactInitiated
                        + " verdict=" + (crossTrackStartSuspect ? "REJECT-cross-item" : "accept"));
                // Vetted once for this item: a later same-item reconfigure is not a cross-item case.
                crossTrackUnverifiedMediaId = null;
            }

            if (crossTrackStartSuspect) {
                resumeMs = savedPositionMs;
                JWLog.w(TAG, "onPlaylistItem: cross-track explicit start REFUSED for mediaId="
                        + externalMediaId + " -- JS asked for " + playlistStartMs
                        + "ms within " + CROSS_TRACK_START_WINDOW_MS
                        + "ms of a track change, but this item's own recorded position is "
                        + savedPositionMs + "ms (" + positionSource + "); using that."
                        + " JS was still reporting the previous item's position.");
            } else if (hasExplicitStart && playlistStartMs >= 0) {
                resumeMs = playlistStartMs;
                JWLog.d(TAG, "onPlaylistItem: using explicit JS starttime resumeMs=" + resumeMs + "ms (live position; preferred over static cache savedPositionMs=" + savedPositionMs + "ms)");
            } else if (savedPositionMs >= 0) {
                // Fallback: static cache / resume provider only when JS supplied no explicit start.
                resumeMs = savedPositionMs;
                JWLog.d(TAG, "onPlaylistItem: no explicit start; using saved position resumeMs=" + resumeMs + "ms (static cache fallback)");
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

            // An echo carries no requested position of its own, so it may only CORROBORATE a seek
            // we actually issued. Measured 2026-09-19 (capture logcat_android17_2026-09-19_11-18-12):
            // returning to the app after a background track change, the JS restore seek to 226.0s
            // was echoed as onSeek(position=226.0, offset=0.0). Writing that spurious zero here made
            // it onSeeked's fallback "requested" position, so the CORRECT event position of 226000ms
            // was discarded as a stale playhead and 0 was published and cached — restarting the
            // media at 0:00. With no armed target, leaving this field at -1 lets onSeeked trust the
            // event itself, which for a JS-initiated seek is the only honest source.
            long armedTargetMs = seekState.targetInFlightMs();
            boolean echoCorroboratesArmedSeek = armedTargetMs >= 0
                    && Math.abs(offsetMs - armedTargetMs) <= SeekAcceptanceState.SEEKED_TARGET_AGREEMENT_MS;
            if (echoCorroboratesArmedSeek) {
                lastRequestedSeekPositionMs = offsetMs;
            } else if (armedTargetMs < 0) {
                JWLog.d(TAG, "onSeek: echo offset=" + offsetMs
                        + "ms corroborates no armed seek; leaving lastRequested="
                        + lastRequestedSeekPositionMs + "ms untouched");
            }
            lastSeekRequestedWhilePaused = !isCurrentlyPlaying();
            // Shield the MediaSession from the stale live position JW 4.26.0 reports for seconds
            // after a seek. Refused when it would downgrade a fresher performSeekTo target, and
            // ignored outright when no seek is in flight.
            noteSeekTargetInFlight(offsetMs, "onSeek-echo", false);
        }
    }

    /**
     * Position to publish to the MediaSession when no explicit override was supplied.
     *
     * JW 4.26.0 regression, measured on device 2026-09-16 with Android Auto + PiP: after a seek to
     * 538312ms, {@code getPosition()} still returned the PRE-seek playhead (912ms) for at least
     * 1.5s — spanning both the {@code onSeeked} callback and the state publication that follows it.
     * Android Auto anchors its clock on {@code (position, updateTime, speed)} and extrapolates, and
     * because nothing re-publishes while the phone sits in PiP, that stale anchor is what the head
     * unit counts up from — showing ~0:01 climbing while the audio really played from 8:58. It only
     * corrected on PiP exit, when a rebuild finally published the true position (545000ms).
     *
     * So while a seek is in flight and the live position has not yet ARRIVED at the target, the
     * target is published instead. The test is proximity, not direction: measured 2026-09-16 on a
     * BACKWARD seek (716038ms -> 260403ms), a "has the player reached or passed the target" test
     * concluded the seek had landed — the stale playhead was ahead of the target — cleared the
     * override and republished 716038ms, so Android Auto stuck at the previous position and counted
     * on from it. Only once the live position is actually AT the target has the seek demonstrably
     * landed. Agreement is accepted only after completion and outside buffering. The trust window
     * is diagnostic, not permission to persist a still-disagreeing sample.
     */
    private long resolvePublishPositionMs(JWPlayer player, int playbackState) {
        long liveMs;
        PlayerState liveState = null;
        try {
            liveMs = (long) (player.getPosition() * 1000);
            liveState = player.getState();
        } catch (Exception e) {
            liveMs = -1L;
        }

        boolean playing = playbackState == PlaybackStateCompat.STATE_PLAYING
                && liveState == PlayerState.PLAYING;
        boolean settled = (playbackState == PlaybackStateCompat.STATE_PLAYING
                || playbackState == PlaybackStateCompat.STATE_PAUSED)
                && (liveState == PlayerState.PLAYING || liveState == PlayerState.PAUSED);
        SeekAcceptanceState.PublishDecision decision = seekState.resolvePublish(
                liveMs, playing, settled, SystemClock.elapsedRealtime(), seekClearListener);

        if (decision.outcome == SeekAcceptanceState.PublishOutcome.NO_TARGET) {
            // No seek command is in flight, so nothing EXPLAINS a disagreement between the SDK and
            // where playback must be. This is the window every remaining regression lived in:
            // measured 2026-09-19 14:19, a confirmed seek to 2568766ms was followed 1.3s later by
            // getPosition() reverting to the pre-seek playhead (12524ms), which was published and —
            // because publication is event-driven — stayed Android Auto's anchor while the player
            // ran on 42 minutes ahead. The model decides here instead of the raw sample.
            long nowMs = SystemClock.elapsedRealtime();
            syncModelPlaybackState(playbackState, nowMs);
            PositionModel.Decision modelDecision = positionModel.observe(liveMs, nowMs);
            logPositionModelDecision("publish", modelDecision);
            return modelDecision.positionMs >= 0 ? modelDecision.positionMs : liveMs;
        }

        if (decision.releasedProtection() || decision.outcome == SeekAcceptanceState.PublishOutcome.ABANDONED) {
            // The seek window just ended. Anchor the model on the position actually settled upon so
            // the first post-release sample is judged against reality, not against a stale model.
            positionModel.onCommandedPosition(decision.positionMs, SystemClock.elapsedRealtime());
        }

        if (decision.outcome == SeekAcceptanceState.PublishOutcome.ABANDONED) {
            // A target that NEVER received a completion callback (measured 2026-09-18: an AA
            // seek dispatched while the new track was still loading is silently swallowed by the
            // SDK, so onSeeked/onTime agreement never arrives). Unlike HELD_PAST_TRUST_WINDOW this
            // releases the anchor and follows the live player, so Android Auto does not stay
            // stuck at the lost target forever. storeSeekPosition here so the resume cache also
            // reflects reality instead of a target that was never reached.
            JWLog.w(TAG, "SEEKTRACE[ABANDONED] target=" + decision.targetMs + "ms live=" + decision.liveMs
                    + "ms age=" + decision.ageMs + "ms -> following live position");
            storeSeekPosition(decision.positionMs);
        } else if (shouldLogSeekDisagreement(decision.targetMs)) {
            if (decision.outcome == SeekAcceptanceState.PublishOutcome.HELD_PAST_TRUST_WINDOW) {
                // Deliberately does NOT fall back to liveMs. Measured 2026-09-16: after a seek to
                // 861057ms this player kept reporting 606296ms (the PREVIOUS seek plus elapsed) while
                // the audio and the PiP overlay both played the new position, so liveMs is the wrong
                // value, not merely a late one. The anchor is released only by a new seek or a track
                // switch. Rate-limited: logged at most once per second per target (see
                // SEEK_DISAGREEMENT_LOG_INTERVAL_MS) so a stuck target stays visible without
                // flooding the capture.
                JWLog.d(TAG, "SEEKTRACE[UNCONFIRMED] target=" + decision.targetMs + "ms held past trust window"
                        + " (age=" + decision.ageMs + "ms, live=" + decision.liveMs + "ms) -> publishing projected "
                        + decision.projectedMs + "ms");
            } else if (decision.outcome == SeekAcceptanceState.PublishOutcome.PROJECTED) {
                JWLog.d(TAG, "SEEKTRACE[UNCONFIRMED] live=" + decision.liveMs + "ms disagrees with seek target "
                        + decision.targetMs + "ms (age=" + decision.ageMs + "ms) -> publishing projected "
                        + decision.projectedMs + "ms");
            }
        }

        return decision.positionMs;
    }

    /** At most one disagreement log per target per {@link #SEEK_DISAGREEMENT_LOG_INTERVAL_MS}. */
    private boolean shouldLogSeekDisagreement(long targetMs) {
        long now = SystemClock.elapsedRealtime();
        boolean sameTarget = targetMs == lastSeekDisagreementLogTargetMs;
        if (sameTarget && now - lastSeekDisagreementLogAtMs < SEEK_DISAGREEMENT_LOG_INTERVAL_MS) {
            return false;
        }
        lastSeekDisagreementLogAtMs = now;
        lastSeekDisagreementLogTargetMs = targetMs;
        return true;
    }

    /**
     * Records a seek target so a stale live position cannot be published while it is in flight.
     *
     * @param authoritative true for {@code performSeekTo}, the single choke point every real seek
     *     passes through. An authoritative target is always accepted — otherwise a user seeking to
     *     0:00 straight after a large seek would be refused. A non-authoritative JW echo is refused
     *     when it disagrees with a fresher target in either direction.
     */
    private void noteSeekTargetInFlight(long targetMs, String source, boolean authoritative) {
        SeekAcceptanceState.ArmOutcome outcome =
            seekState.arm(targetMs, authoritative, SystemClock.elapsedRealtime());
        if (!outcome.accepted) {
            if (targetMs >= 0 && outcome.keptTargetMs < 0) {
                // Measured 2026-09-19: returning to the app after a background track change, JW
                // echoed the JS restore seek to 226.0s as onSeek(position=226.0, offset=0.0). With
                // no target in flight that spurious zero used to BECOME the session's position and
                // was written into the item's resume cache, restarting playback at 0:00. An echo
                // carries no requested position of its own, so it may only acknowledge a target.
                JWLog.d(TAG, "noteSeekTargetInFlight: IGNORED " + targetMs + "ms from " + source
                        + "; no seek in flight — an echo may not create a target");
            } else if (targetMs >= 0) {
                // Measured 2026-09-16: performSeekTo(457027) was issued correctly, then JW emitted
                // its own onSeek(offset=0.0) 43ms later while the player was PAUSED/reloading.
                // Taking that echo as the target wiped the real one and 0 was published and stored.
                // Symmetric, because on a backward seek a stale echo sits AHEAD of the target.
                JWLog.d(TAG, "noteSeekTargetInFlight: REFUSED disagreeing " + targetMs
                        + "ms from " + source + "; keeping in-flight " + outcome.keptTargetMs
                        + "ms (age=" + outcome.ageMs + "ms — spurious JW echo)");
            }
            return;
        }
        JWLog.d(TAG, "noteSeekTargetInFlight(" + targetMs + "ms, source=" + source + ")");
    }

    /**
     * Keeps the model's advance/freeze state in step with what is actually being published, so the
     * projection does not keep advancing through a pause or a rebuffer.
     */
    private void syncModelPlaybackState(int playbackState, long nowMs) {
        if (playbackState == PlaybackStateCompat.STATE_PLAYING) {
            positionModel.onPlaying(nowMs);
        } else {
            positionModel.onNotPlaying(nowMs);
        }
    }

    /** One line per non-trivial model decision. ACCEPTED is the quiet common case. */
    private void logPositionModelDecision(String source, PositionModel.Decision d) {
        if (!JWLog.isVerbose() || d == null) {
            return;
        }
        switch (d.verdict) {
            case REJECTED:
                JWLog.d(TAG, "POSMODEL[" + source + "] REJECTED sample=" + d.sampleMs
                        + "ms deviation=" + d.deviationMs + "ms -> publishing projected "
                        + d.projectedMs + "ms (streak=" + d.rejectStreak + "/"
                        + PositionModel.REACQUIRE_AFTER_REJECTS + ")");
                break;
            case REACQUIRED:
                JWLog.w(TAG, "POSMODEL[" + source + "] REACQUIRED to sample=" + d.sampleMs
                        + "ms after " + d.rejectStreak + " coherent disagreements"
                        + " (projection was " + d.projectedMs + "ms, deviation=" + d.deviationMs + "ms)");
                break;
            case SEEDED:
                JWLog.d(TAG, "POSMODEL[" + source + "] SEEDED at " + d.sampleMs + "ms");
                break;
            case UNAVAILABLE:
                JWLog.d(TAG, "POSMODEL[" + source + "] sample unavailable -> projected "
                        + d.projectedMs + "ms");
                break;
            default:
                break;
        }
    }

    /**
     * Corrects the MediaSession when the anchor it is serving has diverged from the authoritative
     * model.
     *
     * This is NOT a periodic republish. Measured 2026-09-19 12:22 and again at 14:19: a single bogus
     * sample became the served anchor and, because publication is driven only by state-change
     * events, nothing ever corrected it — Android Auto extrapolated from 1213ms while the player ran
     * on from 953931ms, and the two clocks stayed 42 minutes apart, both counting. The write below
     * happens ONLY when the served position actually contradicts the model, so a healthy session
     * publishes nothing extra; the forensic report's objection to blind periodic republishing is
     * respected.
     *
     * Runs before {@code onTime}'s own gates on purpose: those gates drop the tick when the event
     * position and {@code getPosition()} disagree by more than 3s, which is exactly the anomaly
     * window this has to survive.
     */
    private void reconcileServedAnchor() {
        if (activeInstance != this || jwPlayer == null) {
            return;
        }
        if (seekState.targetInFlightMs() >= 0L) {
            // A seek is in flight; SeekAcceptanceState owns publication until it settles.
            return;
        }
        long nowMs = SystemClock.elapsedRealtime();
        long liveMs;
        PlayerState liveState;
        try {
            liveMs = (long) (jwPlayer.getPosition() * 1000d);
            liveState = jwPlayer.getState();
        } catch (Exception e) {
            return;
        }
        if (liveState != PlayerState.PLAYING && liveState != PlayerState.PAUSED) {
            return;
        }
        int playbackState = liveState == PlayerState.PLAYING
                ? PlaybackStateCompat.STATE_PLAYING : PlaybackStateCompat.STATE_PAUSED;
        syncModelPlaybackState(playbackState, nowMs);
        PositionModel.Decision decision = positionModel.observe(liveMs, nowMs);
        logPositionModelDecision("reconcile", decision);
        if (decision.positionMs < 0) {
            return;
        }

        long servedMs = -1L;
        int servedState = -1;
        long servedUpdateTimeMs = 0L;
        float servedSpeed = 0f;
        try {
            PlaybackStateCompat served = this.mediaSessionStateProvider != null
                    && this.mediaSessionStateProvider.mediaSessionCompat != null
                    ? this.mediaSessionStateProvider.mediaSessionCompat
                            .getController().getPlaybackState()
                    : null;
            if (served != null) {
                servedMs = served.getPosition();
                servedState = served.getState();
                servedUpdateTimeMs = served.getLastPositionUpdateTime();
                servedSpeed = served.getPlaybackSpeed();
            }
        } catch (Throwable ignored) {
            return;
        }
        if (servedMs < 0) {
            return;
        }
        // A controller shows position + (now - updateTime) * speed, so compare against what it is
        // DISPLAYING now, not against the value as originally written. Without this, a stale anchor
        // whose extrapolation has drifted hours away would still look "close" on the stored number.
        long servedProjectedMs = servedMs;
        if (servedState == PlaybackStateCompat.STATE_PLAYING && servedUpdateTimeMs > 0) {
            long elapsed = Math.max(0L, nowMs - servedUpdateTimeMs);
            servedProjectedMs = servedMs + (long) (elapsed * (servedSpeed > 0f ? servedSpeed : 1f));
        }
        long divergenceMs = Math.abs(decision.positionMs - servedProjectedMs);
        if (divergenceMs <= RECONCILE_DIVERGENCE_THRESHOLD_MS) {
            return;
        }
        JWLog.w(TAG, "POSMODEL[reconcile] served=" + servedProjectedMs + "ms diverged "
                + divergenceMs + "ms from the model's " + decision.positionMs
                + "ms (verdict=" + decision.verdict + ") -> republishing");
        updatePlaybackState(jwPlayer, playbackState, decision.positionMs);
    }

    /**
     * DIAGNOSTIC: one line per second carrying every position the system holds, side by side, so a
     * single capture answers "who disagrees with whom" without correlating four log streams.
     *
     * Added 2026-09-19 after a capture in which Android Auto and the app displayed two different
     * clocks (capture logcat_android17_2026-09-19_12-19-29): a transient getPosition() of 1213ms was
     * published as the anchor at 12:22:03 and, because the helper publishes only on state-change
     * events, it stayed the anchor for 14s while the player ran on from 953931ms. Nothing in the log
     * compared the live player against what the MediaSession controller was actually serving, so the
     * divergence was visible only by hand-correlating PIPTRACE against STATEWRITE.
     *
     * Reads nothing over IPC and publishes nothing — purely observational.
     */
    private void logPositionTruth(String phase) {
        if (!JWLog.isVerbose()) {
            return;
        }
        long now = SystemClock.elapsedRealtime();
        if (now - lastPositionTruthLogAtMs < POSITION_TRUTH_LOG_INTERVAL_MS) {
            return;
        }
        lastPositionTruthLogAtMs = now;
        long liveMs = -1L;
        String liveState = "n/a";
        double durationSeconds = -1d;
        try {
            if (jwPlayer != null) {
                liveMs = (long) (jwPlayer.getPosition() * 1000d);
                PlayerState st = jwPlayer.getState();
                liveState = st != null ? st.name() : "null";
                durationSeconds = jwPlayer.getDuration();
            }
        } catch (Throwable t) {
            liveState = "err:" + t.getClass().getSimpleName();
        }
        long servedMs = -1L;
        int servedState = -1;
        float servedSpeed = 0f;
        try {
            PlaybackStateCompat served = this.mediaSessionStateProvider != null
                    && this.mediaSessionStateProvider.mediaSessionCompat != null
                    ? this.mediaSessionStateProvider.mediaSessionCompat
                            .getController().getPlaybackState()
                    : null;
            if (served != null) {
                servedMs = served.getPosition();
                servedState = served.getState();
                servedSpeed = served.getPlaybackSpeed();
            }
        } catch (Throwable ignored) {}
        String[] ids = getPlaybackIdentitySnapshot();
        long targetMs = seekState.targetInFlightMs();
        long targetAgeMs = seekState.targetArmedAtMs() > 0 ? now - seekState.targetArmedAtMs() : -1L;
        JWLog.d(TAG, "POSITION_TRUTH[" + phase + "]"
                + " live=" + liveMs + "ms(" + liveState + ")"
                + " served=" + servedMs + "ms(state=" + servedState + ",speed=" + servedSpeed + ")"
                + " delta=" + (liveMs >= 0 && servedMs >= 0 ? (liveMs - servedMs) : Long.MIN_VALUE) + "ms"
                + " target=" + targetMs + "ms(age=" + targetAgeMs + "ms)"
                + " pendingSeek=" + pendingSeekMs + " applied=" + pendingSeekApplied
                + " lastRequested=" + lastRequestedSeekPositionMs + "ms"
                + " duration=" + durationSeconds + "s"
                + " resolvedId=" + ids[0] + " aaId=" + ids[1]
                + " externalId=" + ids[2] + " appId=" + ids[3]
                + " self=" + JWLog.id(this)
                + " active=" + (activeInstance == this ? "self" : JWLog.id(activeInstance)));
    }

    /**
     * DIAGNOSTIC: flags a publish whose position moves materially BACKWARDS from the anchor already
     * being served, with no seek in flight to explain it. Measured 2026-09-19: a rebuffer produced a
     * transient 1213ms reading right after a confirmed seek to 953931ms, and publishing it made
     * Android Auto extrapolate from 1.2s for the rest of the session. Logs only — the write still
     * proceeds, so this changes no behaviour.
     */
    private void logAnchorRegression(String source, long newPositionMs, long previousPositionMs,
            int newState, int previousState) {
        if (!JWLog.isVerbose() || newPositionMs < 0 || previousPositionMs < 0) {
            return;
        }
        long regressionMs = previousPositionMs - newPositionMs;
        if (regressionMs < ANCHOR_REGRESSION_LOG_THRESHOLD_MS) {
            return;
        }
        JWLog.w(TAG, "ANCHOR_REGRESSION[" + source + "] published " + newPositionMs
                + "ms, " + regressionMs + "ms BEHIND the served anchor " + previousPositionMs
                + "ms with target=" + seekState.targetInFlightMs()
                + "ms pendingSeek=" + pendingSeekMs
                + " newState=" + newState + " previousState=" + previousState
                + " — no seek in flight explains this jump");
    }

    /**
     * DIAGNOSTIC: records the exact helper path about to write the shared MediaSession, including
     * the state it will overwrite. This does not alter the state or position.
     */
    private void logPlaybackStateWrite(
            String source, int state, long positionMs, float speed, Long overridePositionMs) {
        if (!JWLog.isVerbose()) {
            return;
        }
        int previousState = -1;
        long previousPositionMs = -1L;
        try {
            PlaybackStateCompat previous = this.mediaSessionStateProvider != null
                    && this.mediaSessionStateProvider.mediaSessionCompat != null
                    ? this.mediaSessionStateProvider.mediaSessionCompat.getController().getPlaybackState()
                    : null;
            if (previous != null) {
                previousState = previous.getState();
                previousPositionMs = previous.getPosition();
            }
        } catch (Throwable ignored) {}
        long ageMs = seekState.targetArmedAtMs() > 0
            ? SystemClock.elapsedRealtime() - seekState.targetArmedAtMs()
                : -1L;
        JWLog.d(TAG, "STATEWRITE[" + source + "] newState=" + state
                + " newPosition=" + positionMs + "ms speed=" + speed
                + " override=" + overridePositionMs
                + " target=" + seekState.targetInFlightMs() + "ms targetAge=" + ageMs
                + "ms previousState=" + previousState
                + " previousPosition=" + previousPositionMs + "ms");
        logAnchorRegression(source, positionMs, previousPositionMs, state, previousState);
    }

    /**
     * Drops any in-flight seek target; the live player position becomes authoritative again.
     * DIAGNOSTIC: reason and both positions are mandatory because the 2026-09-16 capture proved
     * the final wrong MediaSession write happened only AFTER this target disappeared.
     */
    /** Emits the existing SEEKTRACE diagnostic when {@link SeekAcceptanceState} drops the target. */
    private final SeekAcceptanceState.ClearListener seekClearListener =
            new SeekAcceptanceState.ClearListener() {
                @Override
                public void onCleared(String reason, long clearedTargetMs, long ageMs) {
                    logSeekTargetCleared(reason, clearedTargetMs, ageMs);
                }
            };

    private void logSeekTargetCleared(String reason, long clearedTargetMs, long ageMs) {
        if (!JWLog.isVerbose()) {
            return;
        }
        long liveMs = -1L;
        String state = "n/a";
        try {
            if (jwPlayer != null) {
                liveMs = (long) (jwPlayer.getPosition() * 1000L);
                PlayerState playerState = jwPlayer.getState();
                state = playerState != null ? playerState.name() : "null";
            }
        } catch (Throwable t) {
            state = "err:" + t.getClass().getSimpleName();
        }
        JWLog.d(TAG, "SEEKTRACE[CLEAR] reason=" + reason
                + " target=" + clearedTargetMs + "ms age=" + ageMs
                + "ms live=" + liveMs + "ms state=" + state
                + " lastRequested=" + lastRequestedSeekPositionMs + "ms");
    }

    /**
     * Drops any in-flight seek target; the live player position becomes authoritative again.
     * DIAGNOSTIC: reason and both positions are mandatory because the 2026-09-16 capture proved
     * the final wrong MediaSession write happened only AFTER this target disappeared.
     */
    private void clearSeekTargetInFlight(String reason) {
        long ageMs = seekState.targetArmedAtMs() > 0
                ? SystemClock.elapsedRealtime() - seekState.targetArmedAtMs()
                : -1L;
        seekState.clear(reason, ageMs, seekClearListener);
    }

    /**
     * Decides what position an {@code onSeeked} event really represents.
     *
     * JW 4.26.0 regression, measured on device 2026-09-16 (Android Auto seek taken while the phone
     * was in PiP): the head unit requested 763518ms, {@code onSeek} correctly reported
     * {@code offset=763.518}, and then {@code onSeeked} arrived with {@code position=1.727} — the
     * playhead the freshly-started track happened to be at when the user dragged the scrubber, NOT
     * the seek target. The previous expression trusted ANY non-zero event position, so 1726ms was
     * both published to the MediaSession and written into the resume cache: Android Auto then
     * extrapolated from ~0:01 and counted upward while the audio really played from 12:43, and the
     * saved resume position was corrupted to 1726ms. The pre-existing spurious-zero guard only
     * covers {@code eventPositionMs == 0}, so a small non-zero value slipped straight past it.
     *
     * Arbitration: the live player CANNOT settle this — measured 2026-09-16, {@code getPosition()}
     * returned the same stale 912ms as the event, a full 1.5s after a seek to 538312ms. So an event
     * that DISAGREES with the seek we just requested is treated as the stale playhead and the
     * request wins. The test is symmetric: an earlier directional version only corrected events
     * landing behind the target, which silently let a backward seek through (716038ms reported for a
     * seek to 260403ms) and left Android Auto stuck at the previous position. With no recent
     * request, or an event that agrees with it, behaviour is unchanged.
     */
    private long resolveSeekedPosition(long eventPositionMs) {
        // Prefer the in-flight target: lastRequestedSeekPositionMs is written from JW's own onSeek
        // echo, which was measured carrying a spurious 0 for a seek really issued to 457027ms.
        SeekAcceptanceState.SeekedDecision decision =
                seekState.resolveSeeked(eventPositionMs, lastRequestedSeekPositionMs);
        if (decision.substitutedRequest) {
            JWLog.d(TAG, "resolveSeekedPosition: event=" + eventPositionMs
                    + "ms DISAGREES with requested=" + decision.requestedMs
                    + "ms (stale playhead) -> using requested");
        }
        return decision.positionMs;
    }

    @Override
    public void onTime(TimeEvent timeEvent) {
        // DIAGNOSTIC FIRST: emitted before every gate below, because the gates are exactly what a
        // capture needs to see through. onTime is the only signal that ticks throughout steady
        // playback, so this is the one place a per-second truth line can come from. Rate-limited
        // inside, and it publishes nothing.
        logPositionTruth("onTime");
        reconcileServedAnchor();
        if (activeInstance != this || jwPlayer == null || timeEvent == null
                || seekState.targetInFlightMs() < 0L) {
            return;
        }
        PlayerState playerState = jwPlayer.getState();
        if (playerState != PlayerState.PLAYING && playerState != PlayerState.PAUSED) {
            return;
        }
        double eventSeconds = timeEvent.getPosition();
        double liveSeconds = jwPlayer.getPosition();
        if (Double.isNaN(eventSeconds) || Double.isInfinite(eventSeconds) || eventSeconds < 0d
                || Double.isNaN(liveSeconds) || Double.isInfinite(liveSeconds) || liveSeconds < 0d
                || Math.abs(eventSeconds - liveSeconds) * 1000d > SeekAcceptanceState.SEEKED_TARGET_AGREEMENT_MS) {
            return;
        }
        int playbackState = playerState == PlayerState.PLAYING
                ? PlaybackStateCompat.STATE_PLAYING : PlaybackStateCompat.STATE_PAUSED;
        long targetMs = seekState.targetInFlightMs();
        long liveMs = (long) (liveSeconds * 1000d);
        long acceptedPositionMs = resolvePublishPositionMs(jwPlayer, playbackState);
        if (seekState.targetInFlightMs() >= 0L) {
            // Still unconfirmed. A seek dispatched while the player was loading (measured
            // 2026-09-18: AA track change + seek while locked) never gets an onSeeked/onTime
            // agreement at all, so bound it with one guarded re-issue instead of holding forever.
            if (playerState == PlayerState.PLAYING
                    && seekState.canClaimPlayingCorrection(liveMs, SystemClock.elapsedRealtime())) {
                correctUnconfirmedPlayingSeek();
            }
            return;
        }
        if (pendingSeekMs != null
                && Math.abs(pendingSeekMs - targetMs) <= SeekAcceptanceState.SEEKED_TARGET_AGREEMENT_MS) {
            pendingSeekMs = null;
            pendingSeekApplied = true;
            resetAndroidAutoFlag();
        }
        storeSeekPosition(acceptedPositionMs);
        updatePlaybackState(jwPlayer, playbackState, acceptedPositionMs);
        JWLog.d(TAG, "SEEKTRACE[CONFIRMED_TIME] target=" + targetMs
                + "ms actual=" + acceptedPositionMs + "ms state=" + playerState);
    }

    @Override
    public void onSeeked(SeekedEvent seekedEvent) {
        double positionSeconds = seekedEvent != null ? seekedEvent.getPosition() : 0.0;
        long eventPositionMs = (long) (positionSeconds * 1000L);
        int androidVersion = Build.VERSION.SDK_INT;

        // CRITICAL: completely ignore spurious 0 seeks whenever a non-zero resume is pending.
        // Android 12/14 emit a second seek-to-0 after the correct resume seek - this must be blocked.
        // Guard: only suppress the 0 event when the RESUME TARGET itself is > 0.
        // If target IS 0 (fresh/never-played media), position=0 is correct — don't loop.
        //
        // NOT gated on isPlayingFromAndroidAuto any more. Reproduced 2026-08-30 on a PiP-exit
        // return: the app re-asserted the item, resetAndroidAutoFlag() had already cleared the AA
        // flag ("Not Android Auto handoff (isPlayingFromAndroidAuto=false), applying pending seek
        // normally"), the resume seek to 354000ms was issued while the player was still IDLE, and
        // the SDK swallowed it. onSeeked then arrived with position=0.0 while
        // effectivePositionMs fell back to lastRequestedSeekPositionMs=354000, so the completion
        // block below saw delta=0, marked the seek applied and cleared it. The player was really at
        // 0 and playback restarted from the beginning -- storeSeekPosition even tried to persist 0
        // and only its own zero-overwrite guard prevented losing the position. The AA path had this
        // protection all along; the foreground-rebuild path did not.
        if (pendingSeekMs != null && pendingSeekMs > 0 && eventPositionMs == 0) {
            JWLog.d(TAG, "onSeeked: IGNORING spurious 0 seek (pendingSeekMs=" + pendingSeekMs
                    + ", applied=" + pendingSeekApplied + ", fromAndroidAuto="
                    + isPlayingFromAndroidAuto + ")");
            // Re-seek only when the player can actually honour it. Re-issuing while IDLE is what
            // got swallowed in the first place, so an unready player is left to
            // applyPendingSeekWhenReady, which runs on the next ready/play callback.
            if (!pendingSeekApplied && jwPlayer != null) {
                PlayerState stateNow = null;
                try {
                    stateNow = jwPlayer.getState();
                } catch (Exception ignore) {
                    // treat an unreadable state as not ready
                }
                boolean ready = stateNow != null
                        && stateNow != PlayerState.IDLE
                        && stateNow != PlayerState.ERROR;
                if (ready) {
                    JWLog.d(TAG, "onSeeked: Re-seeking to pendingSeekMs=" + pendingSeekMs
                            + " after spurious 0 (state=" + stateNow + ")");
                    suppressNextSeekCallback = true;
                    lastRequestedSeekPositionMs = pendingSeekMs;
                    jwPlayer.seek(pendingSeekMs / 1000.0);
                } else {
                    JWLog.d(TAG, "onSeeked: player not ready (state=" + stateNow + "); KEEPING"
                            + " pendingSeekMs=" + pendingSeekMs
                            + " for applyPendingSeekWhenReady instead of re-seeking now");
                }
            }
            return; // Don't process the spurious 0 event at all
        }

        long effectivePositionMs = resolveSeekedPosition(eventPositionMs);
        effectivePositionMs = sanitizeSeekPosition(effectivePositionMs);

        JWLog.d(TAG, "onSeeked(position=" + positionSeconds + ", effectiveMs=" + effectivePositionMs + ", Android=" + androidVersion + ")");

        correctSettledPausedSeek();

        // Android Auto handoff correction: Two-phase defense strategy
        // Phase 1 (above): Block spurious seek-to-0 entirely (Android 12/14 quirk)
        // Phase 2 (below): Counter-based correction for other wrong positions
        // - Track onSeeked attempts when position is wrong (but not 0)
        // - Allow first attempt, correct on second if still wrong
        // - Immediately accept correct positions
        if (isPlayingFromAndroidAuto && pendingSeekMs != null && !pendingSeekApplied) {
            autoHandoffSeekAttempts++;
            long deltaFromTarget = Math.abs(eventPositionMs - pendingSeekMs);
            
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
            long deltaFromTarget = Math.abs(eventPositionMs - pendingSeekMs);
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
            long delta = Math.abs(eventPositionMs - pendingSeekMs);
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
        if (effectivePositionMs <= 0 && seekState.targetInFlightMs() != 0L
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

    private void correctSettledPausedSeek() {
        if (activeInstance != this || jwPlayer == null || seekState.targetInFlightMs() < 0L) {
            return;
        }
        final JWPlayer seekOwner = jwPlayer;
        final long correctionMs = seekState.targetInFlightMs();
        final long requestAtMs = seekState.targetArmedAtMs();
        mainHandler.post(() -> {
            if (activeInstance != this || jwPlayer != seekOwner
                    || seekState.targetInFlightMs() != correctionMs
                    || seekState.targetArmedAtMs() != requestAtMs
                    || seekOwner.getState() != PlayerState.PAUSED
                    || !seekState.claimPausedCorrection((long) (seekOwner.getPosition() * 1000d), true)) {
                return;
            }
            suppressNextSeekCallback = true;
            lastRequestedSeekPositionMs = correctionMs;
            lastSeekRequestedWhilePaused = true;
            JWLog.d(TAG, "SEEKTRACE[PAUSED_CORRECTION] target=" + correctionMs + "ms");
            seekOwner.seek(correctionMs / 1000d);
        });
    }

    /**
     * Bounded, one-shot re-issue for a target that is still unconfirmed while the player has been
     * settled PLAYING for a while (see {@link SeekAcceptanceState#canClaimPlayingCorrection}).
     * Mirrors {@link #correctSettledPausedSeek()}: the actual claim happens inside the posted
     * runnable, not before posting, so concurrent onTime ticks cannot double-dispatch.
     */
    private void correctUnconfirmedPlayingSeek() {
        if (activeInstance != this || jwPlayer == null || seekState.targetInFlightMs() < 0L) {
            return;
        }
        final JWPlayer seekOwner = jwPlayer;
        final long correctionMs = seekState.targetInFlightMs();
        final long requestAtMs = seekState.targetArmedAtMs();
        mainHandler.post(() -> {
            if (activeInstance != this || jwPlayer != seekOwner
                    || seekState.targetInFlightMs() != correctionMs
                    || seekState.targetArmedAtMs() != requestAtMs
                    || seekOwner.getState() != PlayerState.PLAYING
                    || !seekState.claimPlayingCorrection((long) (seekOwner.getPosition() * 1000d),
                            SystemClock.elapsedRealtime())) {
                return;
            }
            suppressNextSeekCallback = true;
            lastRequestedSeekPositionMs = correctionMs;
            JWLog.d(TAG, "SEEKTRACE[PLAYING_CORRECTION] target=" + correctionMs + "ms");
            seekOwner.seek(correctionMs / 1000d);
        });
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
        correctSettledPausedSeek();
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
            String completionMediaId = resolveMediaIdForCompletion();
            JWLog.d(TAG, "onPlaylistComplete: notifying React Native via MediaBrowserService with mediaId=" + completionMediaId
                + " (externalMediaId=" + externalMediaId
                + ", androidAutoSelectedMediaId=" + androidAutoSelectedMediaId
                + ", appProvidedMediaId=" + appProvidedMediaId + ")");
            Class<?> mediaBrowserServiceClass = Class.forName("com.mediabrowser.MediaBrowserService");
            java.lang.reflect.Method sendCompleteMethod = mediaBrowserServiceClass.getMethod("sendPlaylistCompleteToReactNative", String.class);
            sendCompleteMethod.invoke(null, completionMediaId);
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

    private static long queryResumeViaReflection(String mediaId) {
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

    /**
     * Mirrors {@code applyPendingSeekWhenReady}'s own readiness check for the UI-attached player
     * (duration known, or state already BUFFERING/PLAYING/PAUSED) so {@code performSeekTo} can
     * decide, before dispatching, whether {@code seek()} would actually land or be silently
     * swallowed by a still-IDLE/loading player. Falls back to the headless/background player's own
     * readiness signal when no UI player is attached.
     */
    private boolean isAttachedPlayerReadyForSeek(PlayerState uiPlayerState) {
        if (jwPlayer != null) {
            double duration = 0;
            try {
                duration = jwPlayer.getDuration();
            } catch (Exception ignored) {
                // fall through with duration == 0
            }
            return isStateReadyForSeek(uiPlayerState, duration);
        }
        return jwPlayerNativePlaybackHandler != null
                && jwPlayerNativePlaybackHandler.isBackgroundPlayerReadyForSeek();
    }

    /**
     * Single definition of "a {@code seek()} issued right now will actually land".
     *
     * Measured 2026-09-18 (capture logcat_android17_2026-09-18_17-29-06): an AA seek to
     * 1806529ms was dispatched 0.7s after an AA track change with {@code playerState=BUFFERING},
     * logged as {@code SEEKTRACE[DISPATCH] route=immediate}, and was silently swallowed — the
     * player went on to play the new item from 0 while the session published the lost target for
     * 16s. The previous predicate accepted BUFFERING unconditionally, so it admitted exactly the
     * case it was written to reject.
     *
     * PLAYING/PAUSED prove the item is loaded, so a seek lands. BUFFERING is ambiguous: it covers
     * both "rebuffering an item already loaded" (seek lands) and "loading a just-selected item"
     * (seek is swallowed). A known duration is what separates them — a freshly selected item
     * reports 0 until its manifest is parsed, which is why {@code duration > 0} must be required
     * there instead of being an independent alternative.
     */
    private static boolean isStateReadyForSeek(PlayerState state, double durationSeconds) {
        if (state == PlayerState.PLAYING || state == PlayerState.PAUSED) {
            return true;
        }
        return state == PlayerState.BUFFERING && durationSeconds > 0;
    }

    private void applyPendingSeekWhenReady(PlaylistItem item) {
        JWLog.d(TAG, "applyPendingSeekWhenReady(pendingSeekMs=" + pendingSeekMs + ", applied=" + pendingSeekApplied + ", item=" + JWLog.playlistItemInfo(item) + ")");
        if (pendingSeekMs == null || pendingSeekMs < 0 || jwPlayer == null || pendingSeekApplied) return;

        // Do not block on id mismatches: items can legitimately differ across domains
        double duration = 0;
        try { duration = jwPlayer.getDuration(); } catch (Exception ignored) {}
        PlayerState st = jwPlayer.getState();

        if (isStateReadyForSeek(st, duration)) {
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
                    // A single-item playlist has nowhere to skip to: JW's internal skip reloads
                    // the CURRENT item from the start, which is destructive rather than a no-op --
                    // the listener loses their place and the session keeps publishing PLAYING at
                    // position 0, which presents as a progress bar moving with no audio. The JS
                    // queue owner is the only one that can navigate here, so if it did not ack,
                    // do nothing instead of restarting the track.
                    int playlistSize = -1;
                    try {
                        if (jwPlayer != null && jwPlayer.getPlaylist() != null) {
                            playlistSize = jwPlayer.getPlaylist().size();
                        }
                    } catch (Exception sizeEx) {
                        JWLog.w(TAG, "skip-fallback: could not read playlist size: " + sizeEx.getMessage());
                    }
                    if (playlistSize >= 0 && playlistSize <= 1) {
                        JWLog.w(TAG, "skip-fallback suppressed: single-item RN-owned queue"
                                + " (playlistSize=" + playlistSize + ") skipToken=" + skipToken
                                + " command=" + direction);
                        return;
                    }
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
        JWLog.d(TAG, "external-skip-entry " + getCurrentPlaybackDebugInfo("next"));

        // Prefer the current React playlist's app post ID; fall back to the last
        // Android Auto selection when JW only exposes its internal media ID.
        String mediaIdForSkip = resolveMediaIdForSkip("next");
        long skipOwnerIntentSeq = lastLoadIntentSeq;
        boolean notifiedReactNative = false;
        String skipToken = null;

        // Notify React Native about skip to next via MediaBrowserService (using reflection)
        // This allows RN to fetch the next post from series and load it
        try {
            Class<?> mediaBrowserServiceClass = Class.forName("com.mediabrowser.MediaBrowserService");
            java.lang.reflect.Method sendSkipNextMethod = mediaBrowserServiceClass.getMethod(
                    "sendSkipToNextEventToReactNative", String.class, long.class);
            Object result = sendSkipNextMethod.invoke(null, mediaIdForSkip, skipOwnerIntentSeq);
            skipToken = (result instanceof String) ? (String) result : null;
            notifiedReactNative = (skipToken != null);
            JWLog.d(TAG, "external-skip-rn-dispatch command=next, mediaIdForSkip=" + mediaIdForSkip
                    + ", skipToken=" + skipToken + ", ownerIntentSeq=" + skipOwnerIntentSeq);
            notePendingSkip(skipToken, mediaIdForSkip, "next", skipOwnerIntentSeq);
        } catch (Exception e) {
            JWLog.w(TAG, "performSkipToNext: Could not notify MediaBrowserService: " + e.getMessage());
        }

        if (notifiedReactNative && isAppPostMediaId(mediaIdForSkip)) {
            JWLog.d(TAG, "external-skip-native-fallback-skipped command=next, reason=rn-owned-app-post-queue, mediaIdForSkip=" + mediaIdForSkip);
            // Schedule a 300 ms fallback: if RN has not acknowledged the skip, fire native skip
            scheduleSkipFallback(skipToken, "next");
            return;
        }

        // Also try JWPlayer's internal skip (for playlists within a single post)
        try {
            if (serviceMediaApi != null) {
                JWLog.d(TAG, "external-skip-native-fallback command=next, mediaIdForSkip=" + mediaIdForSkip);
                serviceMediaApi.onSkipToNext();
            }
        } catch (Exception ex) {
            JWLog.w(TAG, "mediaSessionCallback onSkipToNext error: " + ex.getMessage());
        }
    }

    private void performSkipToPrevious() {
        JWLog.d(TAG, "performSkipToPrevious()");
        JWLog.d(TAG, "external-skip-entry " + getCurrentPlaybackDebugInfo("previous"));

        // Prefer the current React playlist's app post ID. See performSkipToNext().
        String mediaIdForSkip = resolveMediaIdForSkip("previous");
        long skipOwnerIntentSeq = lastLoadIntentSeq;
        boolean notifiedReactNative = false;
        String skipToken = null;

        // Notify React Native about skip to previous via MediaBrowserService (using reflection)
        // This allows RN to fetch the previous post from series and load it
        try {
            Class<?> mediaBrowserServiceClass = Class.forName("com.mediabrowser.MediaBrowserService");
            java.lang.reflect.Method sendSkipPrevMethod = mediaBrowserServiceClass.getMethod(
                    "sendSkipToPreviousEventToReactNative", String.class, long.class);
            Object result = sendSkipPrevMethod.invoke(null, mediaIdForSkip, skipOwnerIntentSeq);
            skipToken = (result instanceof String) ? (String) result : null;
            notifiedReactNative = (skipToken != null);
            JWLog.d(TAG, "external-skip-rn-dispatch command=previous, mediaIdForSkip=" + mediaIdForSkip
                    + ", skipToken=" + skipToken + ", ownerIntentSeq=" + skipOwnerIntentSeq);
            notePendingSkip(skipToken, mediaIdForSkip, "previous", skipOwnerIntentSeq);
        } catch (Exception e) {
            JWLog.w(TAG, "performSkipToPrevious: Could not notify MediaBrowserService: " + e.getMessage());
        }

        if (notifiedReactNative && isAppPostMediaId(mediaIdForSkip)) {
            JWLog.d(TAG, "external-skip-native-fallback-skipped command=previous, reason=rn-owned-app-post-queue, mediaIdForSkip=" + mediaIdForSkip);
            // Schedule a 300 ms fallback: if RN has not acknowledged the skip, fire native skip
            scheduleSkipFallback(skipToken, "previous");
            return;
        }

        // Also try JWPlayer's internal skip (for playlists within a single post)
        try {
            if (serviceMediaApi != null) {
                JWLog.d(TAG, "external-skip-native-fallback command=previous, mediaIdForSkip=" + mediaIdForSkip);
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

        // Authoritative source of truth for the session clock. Every seek — Android Auto, in-app,
        // and the AA handoff — passes through here with the real requested position, whereas JW's
        // own onSeek/onSeeked echoes and getPosition() were all measured reporting stale or
        // spurious values under 4.26.0.
        noteSeekTargetInFlight(safePositionMs, "performSeekTo", true);
        // A seek we issued is the one input that is authoritative by construction, so it re-anchors
        // the model outright: the disagreement it creates with the old projection is explained.
        positionModel.onCommandedPosition(safePositionMs, SystemClock.elapsedRealtime());

        PlayerState previousState = null;
        if (jwPlayer != null) {
            try {
                previousState = jwPlayer.getState();
            } catch (Exception ignore) {
                previousState = null;
            }
        }

        // Measured 2026-09-18: a seek dispatched while the just-selected item is still IDLE/loading
        // (locked-screen AA track change immediately followed by an AA seek) is silently swallowed
        // by the JW SDK — no exception, no onSeeked callback, ever. Gate the actual dispatch on the
        // same readiness signal applyPendingSeekWhenReady already uses, and route a not-ready seek
        // through the pending-seek mechanism instead of losing it.
        boolean playerReady = isAttachedPlayerReadyForSeek(previousState);

        Long previousPendingSeekMs = pendingSeekMs;
        boolean supersedesPendingSeek =
                previousPendingSeekMs != null && previousPendingSeekMs.longValue() != safePositionMs;
        if (!playerReady) {
            // REPLACE, never cancel: a bare "pendingSeekMs = null" here (the pre-fix behavior)
            // destroyed the only fallback that could re-issue this seek once the player becomes
            // ready, since noteSeekTargetInFlight above already armed the trust-window hold — the
            // dropped seek would then also never confirm, freezing Android Auto's displayed
            // position at the lost target permanently (see the F2/F3 correction below).
            pendingSeekMs = safePositionMs;
            pendingSeekApplied = false;
        } else if (supersedesPendingSeek) {
            pendingSeekMs = null;
            pendingSeekApplied = true;
        }
        // An equal pending value means applyPendingSeekWhenReady is the caller; leaving it in place
        // keeps onSeeked's spurious-zero guard and the AA handoff counter armed until confirmation.
        if (supersedesPendingSeek) {
            resetAndroidAutoFlag();
        }
        JWLog.d(TAG, "SEEKTRACE[DISPATCH] target=" + safePositionMs + "ms route="
                + (playerReady ? "immediate" : "pending") + " playerState=" + previousState);

        boolean shouldRequestFocus = false;
        double safePositionSeconds = safePositionMs / 1000.0;

        if (playerReady) {
            if (this.jwPlayer != null) {
                try {
                    this.jwPlayer.seek(safePositionSeconds);
                    JWLog.d(TAG, "Performed seek in attached player to " + safePositionSeconds + " s");
                } catch (Exception uiSeekError) {
                    JWLog.e(TAG, "UI player seek failed: " + uiSeekError.getMessage());
                }
            } else {
                try {
                    jwPlayerNativePlaybackHandler.seekToPosition(safePositionMs);
                    JWLog.d(TAG, "Performed seek in background player to " + safePositionMs + " ms");
                } catch (Exception handlerSeekError) {
                    JWLog.w(TAG, "Background player seek failed: " + handlerSeekError.getMessage());
                }
            }
        } else {
            JWLog.d(TAG, "performSeekTo: player not ready (state=" + previousState
                    + "); deferring dispatch to applyPendingSeekWhenReady");
        }

        if (jwPlayer != null) {
            // Keep state (playing vs paused) consistent after seek using the prior state snapshot
                boolean wasPlaying = !pausedByUser
                    && (previousState == PlayerState.PLAYING || previousState == PlayerState.BUFFERING);
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
        noteLoadIntent("aa-selection", mediaId);

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
            // Determine if the RN UI owner is actually in Android PiP. The host Activity is
            // authoritative here because the RN/JW PiP callback can be deferred until after this
            // 500ms selection continuation runs.
            boolean isPip = false;
            try {
                PlaybackManager pm = PlaybackManager.getInstance();
                isPip = pm.isUIInPictureInPictureMode();
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

            pendingSeekMs = resolveSelectionResumeMs(mediaId, extras);
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

        // [PLAYLIST-ADVANCE-FIX] RC-5 / 1d: drop a duplicate selection of the same mediaId that
        // arrives within the dedupe window (double advance dispatch). Return true so the caller
        // treats it as handled -- the in-flight selection will play the item.
        long nowMs = SystemClock.elapsedRealtime();
        if (mediaId != null && mediaId.equals(lastSelectionMediaId)
                && (nowMs - lastSelectionAtMs) < SELECTION_DEDUPE_WINDOW_MS) {
            JWLog.d(TAG, "[PLAYLIST-ADVANCE-FIX] handlePlayFromMediaId: dropping duplicate selection for mediaId="
                    + mediaId + " within " + (nowMs - lastSelectionAtMs) + "ms");
            return true;
        }
        lastSelectionMediaId = mediaId;
        lastSelectionAtMs = nowMs;

        pendingSeekMs = resolveSelectionResumeMs(mediaId, extras);
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
