package com.jwplayer.rnjwplayer;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Deterministic plain-JVM coverage for the PiP ownership boundary fixed in
 * PlaybackManager and the existing native identity guard used by RNJWPlayerView.setConfig().
 *
 * This deliberately has no Android or JWPlayer test dependency. The small owner model mirrors
 * the production predicate, while source assertions pin the production integration points.
 * Run with:
 *   javac -d <out> PipOwnershipRegressionTest.java
 *   java -cp <out> com.jwplayer.rnjwplayer.PipOwnershipRegressionTest <repo-root>
 */
public final class PipOwnershipRegressionTest {
    private static final class OwnerModel {
        boolean uiOwner = true;
        boolean uiInBackground;
        boolean cachedPipActive;
        boolean activityInPip;
        String owner = "ui";
        String currentMediaId = "A";
        int cleanupCalls;

        boolean isUiActive() {
            return uiOwner && (!uiInBackground || cachedPipActive || activityInPip);
        }

        void selectFromAndroidAuto(String mediaId) {
            if (isUiActive()) {
                // Existing UI-owner path: setup/load on the same JW player instance.
                currentMediaId = mediaId;
                return;
            }
            cleanupCalls++;
            owner = "headless";
            currentMediaId = mediaId;
        }
    }

    private static final class IdentityModel {
        String nativeSelected;
        String livePlayer;
        String appProvided;

        boolean rejectStaleForegroundConfig(String incoming) {
            return nativeSelected != null
                    && nativeSelected.equals(livePlayer)
                    && !nativeSelected.equals(appProvided)
                    && !nativeSelected.equals(incoming);
        }
    }

    private static int passed;
    private static int failed;
    private static final List<String> failures = new ArrayList<>();

    private static void check(String name, boolean condition) {
        if (condition) {
            passed++;
        } else {
            failed++;
            failures.add(name);
        }
    }

    private static String read(Path root, String relative) throws Exception {
        return new String(Files.readAllBytes(root.resolve(relative)), StandardCharsets.UTF_8);
    }

    private static void sourceAssertions(Path root) throws Exception {
        String playback = read(root,
                "android/src/main/java/com/jwplayer/rnjwplayer/PlaybackManager.java");
        String view = read(root,
                "android/src/main/java/com/jwplayer/rnjwplayer/RNJWPlayerView.java");
        String mediaSession = read(root,
                "android/src/main/java/com/jwplayer/rnjwplayer/session/RNJWMediaSessionHelper.java");
        String nativeHandler = read(root,
                "android/src/main/java/com/jwplayer/rnjwplayer/JWPlayerNativePlaybackHandler.java");

        check("SOURCE_ACTIVITY_PIP_QUERY",
                playback.contains("isActivityInPictureInPictureMode")
                        && view.contains("isInPictureInPictureMode()"));
        check("SOURCE_ACTIVITY_PIP_PARTICIPATES_IN_UI_OWNER",
                playback.contains("|| actualPip")
                        && playback.contains("isUIInPictureInPictureMode"));
        check("SOURCE_SELECTION_USES_AUTHORITATIVE_PIP",
                mediaSession.contains("pm.isUIInPictureInPictureMode()"));
        check("SOURCE_UI_BRANCH_PRECEDES_HEADLESS_CLEANUP",
                nativeHandler.indexOf("if (PlaybackManager.getInstance().isUIActive())")
                        < nativeHandler.indexOf("PlaybackManager.getInstance().stopAndCleanupCurrentPlayer();"));
        check("SOURCE_STALE_IDENTITY_GUARD_REMAINS_NARROW",
                view.contains("shouldKeepLiveItemForForegroundRebuild")
                        && view.contains("isAppTrackStaleVsAndroidAuto"));
        check("SOURCE_NATIVE_SELECTION_RECORDED_BEFORE_HANDLER",
                mediaSession.indexOf("androidAutoSelectedMediaId = mediaId;")
                        < mediaSession.indexOf("jwPlayerNativePlaybackHandler.handleHeadlessMediaSelection"));
    }

    public static void main(String[] args) throws Exception {
        Path root = Paths.get(args.length == 0 ? "." : args[0]);
        sourceAssertions(root);

        // 1. Foreground UI + AA selection keeps the UI owner.
        OwnerModel foreground = new OwnerModel();
        foreground.selectFromAndroidAuto("B");
        check("FOREGROUND_UI_OWNER_RETAINED",
                foreground.owner.equals("ui") && foreground.currentMediaId.equals("B")
                        && foreground.cleanupCalls == 0);

        // 2. Background, not PiP, keeps the historical headless route.
        OwnerModel background = new OwnerModel();
        background.uiInBackground = true;
        background.selectFromAndroidAuto("B");
        check("NON_PIP_BACKGROUND_REMAINS_HEADLESS",
                background.owner.equals("headless") && background.cleanupCalls == 1);

        // 3. Cached pipActive=false must not defeat Activity-reported PiP.
        OwnerModel deferredPipCallback = new OwnerModel();
        deferredPipCallback.uiInBackground = true;
        deferredPipCallback.cachedPipActive = false;
        deferredPipCallback.activityInPip = true;
        deferredPipCallback.selectFromAndroidAuto("B");
        check("ACTUAL_ACTIVITY_PIP_RETAINS_UI_OWNER",
                deferredPipCallback.owner.equals("ui")
                        && deferredPipCallback.cleanupCalls == 0
                        && deferredPipCallback.currentMediaId.equals("B"));

        // 4. A -> B in PiP uses the same UI owner and loads B in place.
        check("PIP_LOAD_IS_IN_PLACE",
                deferredPipCallback.owner.equals("ui")
                        && deferredPipCallback.currentMediaId.equals("B"));

        // 5. Native B is recorded/live before stale React A returns; A is rejected.
        IdentityModel identity = new IdentityModel();
        identity.nativeSelected = "B";
        identity.livePlayer = "B";
        identity.appProvided = "A";
        check("STALE_REACT_IDENTITY_REJECTED",
                identity.rejectStaleForegroundConfig("A"));

        // 6. Normal PiP enter/exit retains the owner and does not invoke cleanup.
        OwnerModel normalExit = new OwnerModel();
        normalExit.uiInBackground = true;
        normalExit.activityInPip = true;
        normalExit.selectFromAndroidAuto("B");
        normalExit.activityInPip = false;
        normalExit.uiInBackground = false;
        check("NORMAL_PIP_ENTER_EXIT_UNCHANGED",
                normalExit.owner.equals("ui") && normalExit.cleanupCalls == 0
                        && normalExit.currentMediaId.equals("B"));

        System.out.println(passed + " passed, " + failed + " failed");
        if (failed != 0) {
            for (String failure : failures) {
                System.err.println("FAIL: " + failure);
            }
            System.exit(1);
        }
    }
}
