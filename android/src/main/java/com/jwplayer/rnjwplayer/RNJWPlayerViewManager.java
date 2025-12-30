package com.jwplayer.rnjwplayer;

import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.common.MapBuilder;
import com.facebook.react.uimanager.SimpleViewManager;
import com.facebook.react.uimanager.ThemedReactContext;
import com.facebook.react.uimanager.annotations.ReactProp;
import com.mediabrowser.MediaSessionSingleton;
import android.support.v4.media.session.MediaSessionCompat;
import com.jwplayer.rnjwplayer.utils.JWLog;

import java.util.Map;

import javax.annotation.Nonnull;

public class RNJWPlayerViewManager extends SimpleViewManager<RNJWPlayerView> {

  public static final String REACT_CLASS = "RNJWPlayerView";

  private final ReactApplicationContext mAppContext;
  private final JWPlayerNativePlaybackHandler nativePlaybackHandler;

  private static final String TAG = "RNJWPlayerViewManager";

  @Override
  public String getName() {
    JWLog.d(TAG, "getName()");
    return REACT_CLASS;
  }

  public RNJWPlayerViewManager(ReactApplicationContext context) {
    JWLog.d(TAG, "<init>(context=" + context + ")");
    mAppContext = context;
    nativePlaybackHandler = JWPlayerNativePlaybackHandler.getInstance(context);
  }

  @Override
  public RNJWPlayerView createViewInstance(ThemedReactContext context) {
    JWLog.d(TAG, "createViewInstance(context=" + context + ")");
    return new RNJWPlayerView(context, mAppContext);
  }

  @ReactProp(name = "config")
  public void setConfig(RNJWPlayerView view, ReadableMap prop) {
    JWLog.d(TAG, "setConfig(view=" + JWLog.id(view) + ", hasProp=" + (prop != null) + ")");
    view.setConfig(prop);
  }

  @ReactProp(name = "controls")
  public void setControls(RNJWPlayerView view, Boolean controls) {
    JWLog.d(TAG, "setControls(view=" + JWLog.id(view) + ", controls=" + controls + ")");
    if (view == null || view.mPlayerView == null) {
      JWLog.w(TAG, "setControls skipped: view or mPlayerView is null");
      return;
    }
    view.mPlayerView.getPlayer().setControls(controls);
  }

  /**
   * Recreates the player with a new configuration, handling cleanup and PiP state.
   * This method ensures proper cleanup and state restoration during configuration changes.
   *
   * @param view The RNJWPlayerView instance
   * @param config The new configuration to apply
   */
  @ReactProp(name = "recreatePlayerWithConfig")
  public void recreatePlayerWithConfig(RNJWPlayerView view, ReadableMap config) {
    if (view == null || view.mPlayerView == null) {
      return;
    }
    view.mPlayerView.getPlayer().stop();
    view.setConfig(config);
  }

  public Map getExportedCustomBubblingEventTypeConstants() {
    JWLog.d(TAG, "getExportedCustomBubblingEventTypeConstants()");
    Map map = MapBuilder.builder()
            .put(
                    "topPlayerError",
                    MapBuilder.of(
                            "phasedRegistrationNames",
                            MapBuilder.of("bubbled", "onPlayerError")))
            .put("topSetupPlayerError",
                    MapBuilder.of(
                            "phasedRegistrationNames",
                            MapBuilder.of("bubbled", "onSetupPlayerError")))
            .put("topPlayerAdError",
                    MapBuilder.of(
                            "phasedRegistrationNames",
                            MapBuilder.of("bubbled", "onPlayerAdError")))
            .put("topPlayerAdWarning",
                    MapBuilder.of(
                            "phasedRegistrationNames",
                            MapBuilder.of("bubbled", "onPlayerAdWarning")))
            .put("topAdEvent",
                    MapBuilder.of(
                            "phasedRegistrationNames",
                            MapBuilder.of("bubbled", "onAdEvent")))
            .put("topAdTime",
                    MapBuilder.of(
                            "phasedRegistrationNames",
                            MapBuilder.of("bubbled", "onAdTime")))
            .put("topTime",
                    MapBuilder.of(
                            "phasedRegistrationNames",
                            MapBuilder.of("bubbled", "onTime")))
            .put("topBuffer",
                    MapBuilder.of(
                            "phasedRegistrationNames",
                            MapBuilder.of("bubbled", "onBuffer")))
            .put("topFullScreen",
                    MapBuilder.of(
                            "phasedRegistrationNames",
                            MapBuilder.of("bubbled", "onFullScreen")))
            .put("topFullScreenExitRequested",
                    MapBuilder.of(
                            "phasedRegistrationNames",
                            MapBuilder.of("bubbled", "onFullScreenExitRequested")))
            .put("topFullScreenRequested",
                    MapBuilder.of(
                            "phasedRegistrationNames",
                            MapBuilder.of("bubbled", "onFullScreenRequested")))
            .put("topFullScreenExit",
                    MapBuilder.of(
                            "phasedRegistrationNames",
                            MapBuilder.of("bubbled", "onFullScreenExit")))
            .put("topPause",
                    MapBuilder.of(
                            "phasedRegistrationNames",
                            MapBuilder.of("bubbled", "onPause")))
            .put("topPlay",
                    MapBuilder.of(
                            "phasedRegistrationNames",
                            MapBuilder.of("bubbled", "onPlay")))
            .put("topComplete",
                    MapBuilder.of(
                            "phasedRegistrationNames",
                            MapBuilder.of("bubbled", "onComplete")))
            .put("topPlaylistComplete",
                    MapBuilder.of(
                            "phasedRegistrationNames",
                            MapBuilder.of("bubbled", "onPlaylistComplete")))
            .put("topPlaylistItem",
                    MapBuilder.of(
                            "phasedRegistrationNames",
                            MapBuilder.of("bubbled", "onPlaylistItem")))
            .put("topSeek",
                    MapBuilder.of(
                            "phasedRegistrationNames",
                            MapBuilder.of("bubbled", "onSeek")))
            .put("topSeeked",
                    MapBuilder.of(
                            "phasedRegistrationNames",
                            MapBuilder.of("bubbled", "onSeeked")))
            .put("topRateChanged",
                    MapBuilder.of(
                            "phasedRegistrationNames",
                            MapBuilder.of("bubbled", "onRateChanged")))
            .put("topControlBarVisible",
                    MapBuilder.of(
                            "phasedRegistrationNames",
                            MapBuilder.of("bubbled", "onControlBarVisible")))
            .put("topOnPlayerReady",
                    MapBuilder.of(
                            "phasedRegistrationNames",
                            MapBuilder.of("bubbled", "onPlayerReady")))
            .put("topBeforePlay",
                    MapBuilder.of(
                            "phasedRegistrationNames",
                            MapBuilder.of("bubbled", "onBeforePlay")))
            .put("topBeforeComplete",
                    MapBuilder.of(
                            "phasedRegistrationNames",
                            MapBuilder.of("bubbled", "onBeforeComplete")))
            .put("topAdPlay",
                    MapBuilder.of(
                            "phasedRegistrationNames",
                            MapBuilder.of("bubbled", "onAdPlay")))
            .put("topAdPause",
                    MapBuilder.of(
                            "phasedRegistrationNames",
                            MapBuilder.of("bubbled", "onAdPause")))
            .put("topAudioTracks",
                    MapBuilder.of(
                            "phasedRegistrationNames",
                            MapBuilder.of("bubbled", "onAudioTracks")))
            .put("topCaptionsChanged",
                    MapBuilder.of(
                            "phasedRegistrationNames",
                            MapBuilder.of("bubbled", "onCaptionsChanged")))
            .put("topCaptionsList",
                    MapBuilder.of(
                            "phasedRegistrationNames",
                            MapBuilder.of("bubbled", "onCaptionsList")))
            .put("topCasting",
                    MapBuilder.of(
                            "phasedRegistrationNames",
                            MapBuilder.of("bubbled", "onCasting")))
            .put("topFirstFrame",
                    MapBuilder.of(
                            "phasedRegistrationNames",
                            MapBuilder.of("bubbled", "onLoaded")))
            .put("topBeforeNextPlaylistItem",
                    MapBuilder.of(
                            "phasedRegistrationNames",
                            MapBuilder.of("bubbled", "onBeforeNextPlaylistItem")))
            .build();
    try {
        JWLog.d(TAG, "exported events count=" + (map != null ? map.size() : -1));
    } catch (Throwable ignored) {}
    return map;
  }

  @Override
  public void onDropViewInstance(@Nonnull RNJWPlayerView view) {
    JWLog.d(TAG, "onDropViewInstance(view=" + JWLog.id(view) + ")");
    view.destroyPlayer();
    super.onDropViewInstance(view);
    view = null;
  }

  /**
   * Get pending media info from headless mode for app restoration
   */
  @ReactMethod
  public void getPendingMediaInfo(Promise promise) {
    try {
      JWLog.d(TAG, "getPendingMediaInfo()");
      WritableMap pendingMedia = nativePlaybackHandler.getPendingMediaInfo();
      JWLog.d(TAG, "getPendingMediaInfo -> hasPending=" + (pendingMedia != null));
      promise.resolve(pendingMedia);
    } catch (Exception e) {
      JWLog.e(TAG, "getPendingMediaInfo error", e);
      promise.reject("GET_PENDING_MEDIA_ERROR", "Failed to get pending media info", e);
    }
  }

  /**
   * Clear pending media info after handling
   */
  @ReactMethod
  public void clearPendingMedia(Promise promise) {
    try {
      JWLog.d(TAG, "clearPendingMedia()");
      nativePlaybackHandler.clearPendingMedia();
      JWLog.d(TAG, "clearPendingMedia -> true");
      promise.resolve(true);
    } catch (Exception e) {
      JWLog.e(TAG, "clearPendingMedia error", e);
      promise.reject("CLEAR_PENDING_MEDIA_ERROR", "Failed to clear pending media", e);
    }
  }

  /**
   * Handle media selection from headless mode
   */
  public void handleHeadlessMediaSelection(String mediaId, String title, String subtitle, 
                                         String icon, Map<String, Object> extras) {
    JWLog.d(TAG, "handleHeadlessMediaSelection(mediaId=" + JWLog.safe(mediaId) + ", title=" + JWLog.safe(title) + ")");
    nativePlaybackHandler.handleHeadlessMediaSelection(mediaId, title, subtitle, icon, extras);
  }
}
