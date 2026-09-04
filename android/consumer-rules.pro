# Consumer ProGuard rules for @jwplayer/jwplayer-react-native (OU headless fork).
#
# react-native-android-media-browser reaches into this library by reflection only — it declares no
# Gradle or npm dependency on it, so R8 cannot see these call sites and would otherwise strip or
# rename the targets. These rules travel with the AAR into every consuming app, which makes the
# contract a property of this library rather than of each app's proguard config.
#
# Scoped to the reflected members on purpose: the point is a minimal keep surface, not -keep *.
#
# Census covers BOTH directions of reflection: media-browser's Class.forName + getMethod pairs
# into this library, and this library's reflective calls into its own classes by string name.
# Every entry is verified against the declarations in this repo. Re-derive with:
#   grep -rn -oE 'Class\.forName\("com\.jwplayer[^"]*"|getMethod\("[A-Za-z0-9_]+"' <media-browser>/android/src
#   grep -rn -oE 'getMethod\("[A-Za-z0-9_]+"|getDeclaredMethod\("[A-Za-z0-9_]+"' android/src

# Headless playback entry points, reflected from MediaBrowserService.
# The last three are reflected from inside this library itself, by string name:
# RNJWMediaSessionHelper reads the playback state before a transfer, and
# RNJWPlayerView probes the background player. R8 renames them without a keep.
-keep class com.jwplayer.rnjwplayer.JWPlayerNativePlaybackHandler {
    public static *** getInstance(...);
    public *** handleHeadlessMediaSelection(...);
    public *** stopAndCleanup(...);
    public *** getComprehensivePlaybackState(...);
    public *** isBackgroundPlayerActive(...);
    public *** getCurrentBackgroundPlayerInfo(...);
}

# "Is anything playing right now?" — gates the guarded session release in onDestroy.
# isUIActive / getActivePlayerIfUI are reflected from RNJWPlayerModule.
-keep class com.jwplayer.rnjwplayer.PlaybackManager {
    public static *** getInstance(...);
    public *** hasActivePlayer(...);
    public *** isUIActive(...);
    public *** getActivePlayerIfUI(...);
}

# Transport callbacks and the session re-point after a release/recreate.
-keep class com.jwplayer.rnjwplayer.session.RNJWMediaSessionHelper {
    public *** refreshSessionReference(...);
    public *** getActualPlaybackRate(...);
    public *** handlePlay(...);
    public *** handlePause(...);
    public *** handleStop(...);
    public *** handleSeekTo(...);
    public *** handleSetSpeed(...);
    public *** handleSkipToNext(...);
    public *** handleSkipToPrevious(...);
    public *** handlePlayFromMediaId(...);
}

# Reflected from PlaybackManager via handlerToCleanup.getClass().getMethod("destroyPlayer").
-keep class com.jwplayer.rnjwplayer.RNJWPlayerView {
    public *** destroyPlayer(...);
}
