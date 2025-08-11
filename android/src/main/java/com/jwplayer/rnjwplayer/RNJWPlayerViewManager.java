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

import java.util.Map;

import javax.annotation.Nonnull;

public class RNJWPlayerViewManager extends SimpleViewManager<RNJWPlayerView> {

  public static final String REACT_CLASS = "RNJWPlayerView";

  private final ReactApplicationContext mAppContext;
  private final JWPlayerNativePlaybackHandler nativePlaybackHandler;

  private static final String TAG = "RNJWPlayerViewManager";

  @Override
  public String getName() {
    return REACT_CLASS;
  }

  public RNJWPlayerViewManager(ReactApplicationContext context) {
    mAppContext = context;
    nativePlaybackHandler = JWPlayerNativePlaybackHandler.getInstance(context);
  }

  @Override
  public RNJWPlayerView createViewInstance(ThemedReactContext context) {
    return new RNJWPlayerView(context, mAppContext);
  }

  @ReactProp(name = "config")
  public void setConfig(RNJWPlayerView view, ReadableMap prop) {
    view.setConfig(prop);
  }

  @ReactProp(name = "controls")
  public void setControls(RNJWPlayerView view, Boolean controls) {
    if (view == null || view.mPlayerView == null) {
      return;
    }
    view.mPlayerView.getPlayer().setControls(controls);
  }

  public Map getExportedCustomBubblingEventTypeConstants() {
    return MapBuilder.builder()
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
  }

  @Override
  public void onDropViewInstance(@Nonnull RNJWPlayerView view) {
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
    try {
      nativePlaybackHandler.clearPendingMedia();
      promise.resolve(true);
    } catch (Exception e) {
      promise.reject("CLEAR_PENDING_MEDIA_ERROR", "Failed to clear pending media", e);
    }
  }

  /**
   * Handle media selection from headless mode
   */
  public void handleHeadlessMediaSelection(String mediaId, String title, String subtitle, 
                                         String icon, Map<String, Object> extras) {
    nativePlaybackHandler.handleHeadlessMediaSelection(mediaId, title, subtitle, icon, extras);
  }
}
