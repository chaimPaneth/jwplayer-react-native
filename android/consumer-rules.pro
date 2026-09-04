# Consumer ProGuard rules for @jwplayer/jwplayer-react-native (OU headless fork).
#
# react-native-android-media-browser reaches into this library by reflection only — it declares no
# Gradle or npm dependency on it, so R8 cannot see these call sites and would otherwise strip or
# rename the targets. These rules travel with the AAR into every consuming app, which makes the
# contract a property of this library rather than of each app's proguard config.
#
# Scoped to the reflected members on purpose: the point is a minimal keep surface, not -keep *.
#
# Census derived from media-browser's Class.forName + getMethod pairs and verified against the
# declarations in this repo. Reflection sites: MediaBrowserService.java:158, 307, 731, 931, 1139,
# 1174, 2049 and MediaBrowserModule.java:872. Re-derive with:
#   grep -rn -oE 'Class\.forName\("com\.jwplayer[^"]*"|getMethod\("[A-Za-z0-9_]+"' <media-browser>/android/src

# Headless playback entry points, reflected from MediaBrowserService.
-keep class com.jwplayer.rnjwplayer.JWPlayerNativePlaybackHandler {
    public static *** getInstance(...);
    public *** handleHeadlessMediaSelection(...);
    public *** stopAndCleanup(...);
}

# "Is anything playing right now?" — gates the guarded session release in onDestroy.
-keep class com.jwplayer.rnjwplayer.PlaybackManager {
    public static *** getInstance(...);
    public *** hasActivePlayer(...);
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
