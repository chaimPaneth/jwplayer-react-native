package com.jwplayer.rnjwplayer.session;

import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.JavacTask;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.tools.JavaCompiler;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

/**
 * A seek JW makes by itself (its own seek bar) arms no target, and JW 4.26 then reports the
 * playhead it LEFT in onSeeked; only onSeek's offset is the target. The helper cached and
 * published that old playhead, a lock-screen skip back to the download started there, and the
 * app saved it over the right one.
 *
 * The rule's methods are extracted from the production helper and run in a plain-JVM harness, so
 * the fixtures exercise the shipped code. Source assertions pin where the rule is applied.
 *
 * Run from the repo root:
 *   javac -d <out> android/src/test/java/com/jwplayer/rnjwplayer/session/UnarmedSeekRegressionTest.java
 *   java -cp <out> com.jwplayer.rnjwplayer.session.UnarmedSeekRegressionTest .
 */
public final class UnarmedSeekRegressionTest {

    private static final String HELPER =
            "android/src/main/java/com/jwplayer/rnjwplayer/session/RNJWMediaSessionHelper.java";
    private static final String VIEW =
            "android/src/main/java/com/jwplayer/rnjwplayer/RNJWPlayerView.java";
    private static final String HANDLER =
            "android/src/main/java/com/jwplayer/rnjwplayer/JWPlayerNativePlaybackHandler.java";

    private static final String HARNESS = String.join("\n",
            "final class SystemClock {",
            "    static long now = 0L;",
            "    static long elapsedRealtime() { return now; }",
            "}",
            "final class JWLog {",
            "    static void d(String tag, String message) { }",
            "}",
            "final class SeekAcceptanceState {",
            "    static final long SEEKED_TARGET_AGREEMENT_MS = 3_000L;",
            "}",
            "public final class UnarmedSeekHarness {",
            "    static final String TAG = \"test\";",
            "    static String lastPlaylistItemMediaId = null;",
            "    /* PRODUCTION_MEMBERS */",
            "    static int passed = 0;",
            "    static int failed = 0;",
            "    static void check(String name, boolean ok) {",
            "        if (ok) { passed++; } else { failed++; System.out.println(\"FAIL \" + name); }",
            "    }",
            "    static boolean staleAt(UnarmedSeekHarness h, long atMs, long positionMs) {",
            "        SystemClock.now = atMs;",
            "        return h.isStalePlayheadOfUnarmedSeek(positionMs);",
            "    }",
            "    public static void main(String[] args) {",
            "        UnarmedSeekHarness h = new UnarmedSeekHarness();",
            "        lastPlaylistItemMediaId = \"10674\";",
            "        SystemClock.now = 1_000L;",
            "        check(\"NO_SEEK_NOTHING_STALE\", !h.isStalePlayheadOfUnarmedSeek(1_142_597L));",
            "        h.noteUnarmedSeek(1_142_523L, 1_100_000L);",
            "        check(\"MEASURED_0632_ONSEEKED_EVENT_IS_STALE\", staleAt(h, 1_076L, 1_142_597L));",
            "        check(\"MEASURED_0632_TRUE_SAMPLE_KEPT\", !staleAt(h, 1_123L, 1_100_027L));",
            "        check(\"STALE_PLAYHEAD_STILL_ADVANCING\", staleAt(h, 2_500L, 1_144_000L));",
            "        check(\"TARGET_PLUS_PLAYBACK_KEPT\", !staleAt(h, 5_000L, 1_104_000L));",
            "        check(\"WINDOW_EXPIRES\", !staleAt(h, 6_001L, 1_142_597L));",
            "        h.noteUnarmedSeek(1_142_523L, 1_100_000L);",
            "        lastPlaylistItemMediaId = \"10675\";",
            "        check(\"ANOTHER_ITEM_NEVER_JUDGED\", !staleAt(h, 6_100L, 1_142_597L));",
            "        lastPlaylistItemMediaId = \"10674\";",
            "        h.clearUnarmedSeek();",
            "        check(\"CLEARED_NOTHING_STALE\", !staleAt(h, 6_200L, 1_142_597L));",
            "        SystemClock.now = 10_000L;",
            "        h.noteUnarmedSeek(100_000L, 900_000L);",
            "        check(\"FORWARD_SEEK_LEFT_PLAYHEAD_STALE\", staleAt(h, 10_200L, 100_150L));",
            "        check(\"FORWARD_SEEK_TARGET_KEPT\", !staleAt(h, 10_200L, 900_100L));",
            "        SystemClock.now = 20_000L;",
            "        h.noteUnarmedSeek(1_142_000L, 1_138_000L);",
            "        check(\"SHORT_SEEK_REAL_SAMPLE_KEPT\", !staleAt(h, 20_500L, 1_138_500L));",
            "        check(\"SHORT_SEEK_OVERLAP_KEPT\", !staleAt(h, 22_500L, 1_141_000L));",
            "        System.out.println(\"passed=\" + passed + \"  failed=\" + failed);",
            "        if (failed > 0) { throw new AssertionError(failed + \" unarmed-seek fixtures failed\"); }",
            "    }",
            "}");

    public static void main(String[] args) throws Exception {
        Path root = Paths.get(args.length > 0 ? args[0] : ".");
        Path helper = root.resolve(HELPER);
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new IllegalStateException("Run with a JDK, not a JRE");
        }
        Map<String, String> members = new LinkedHashMap<>();
        for (String name : Arrays.asList("UNARMED_SEEK_WINDOW_MS", "unarmedSeekTargetMs",
                "unarmedSeekFromMs", "unarmedSeekAtMs", "unarmedSeekItemKey", "noteUnarmedSeek",
                "clearUnarmedSeek", "isStalePlayheadOfUnarmedSeek")) {
            members.put(name, null);
        }
        try (StandardJavaFileManager files = compiler.getStandardFileManager(null, null, null)) {
            JavacTask task = (JavacTask) compiler.getTask(null, files, null,
                    Collections.singletonList("-proc:none"), null,
                    files.getJavaFileObjects(helper.toFile()));
            for (CompilationUnitTree unit : task.parse()) {
                for (Tree declaration : unit.getTypeDecls()) {
                    if (!(declaration instanceof ClassTree)) {
                        continue;
                    }
                    for (Tree member : ((ClassTree) declaration).getMembers()) {
                        if (member instanceof MethodTree) {
                            String name = ((MethodTree) member).getName().toString();
                            if (members.containsKey(name)) {
                                members.put(name, member.toString());
                            }
                        } else if (member instanceof VariableTree) {
                            String name = ((VariableTree) member).getName().toString();
                            if (members.containsKey(name)) {
                                members.put(name, member.toString() + ";");
                            }
                        }
                    }
                }
            }
        }
        for (Map.Entry<String, String> entry : members.entrySet()) {
            if (entry.getValue() == null) {
                throw new AssertionError("Production member missing: " + entry.getKey());
            }
        }

        String helperSrc = read(helper);
        String viewSrc = read(root.resolve(VIEW));
        String handlerSrc = read(root.resolve(HANDLER));
        int sourceFailures = 0;
        sourceFailures += source("SRC_ONSEEK_RECORDS_ONLY_A_REAL_TARGET",
                helperSrc.contains("if (offsetMs >= UNARMED_SEEK_MIN_TARGET_MS && pendingSeekMs == null")
                        && helperSrc.contains("UNARMED_SEEK_MIN_TARGET_MS = 1_000L;")
                        && helperSrc.contains("noteUnarmedSeek(fromMs, offsetMs);"));
        sourceFailures += source("SRC_ONSEEKED_USES_THE_TARGET",
                helperSrc.contains("&& pendingSeekMs == null && isStalePlayheadOfUnarmedSeek(eventPositionMs)"));
        sourceFailures += source("SRC_STORE_BLOCKS_THE_LEFT_PLAYHEAD",
                helperSrc.contains("if (isStalePlayheadOfUnarmedSeek(position)) {"));
        sourceFailures += source("SRC_TRACK_SWITCH_CLEARS_IT",
                helperSrc.contains("clearSeekTargetInFlight(\"track-switch \" + lastPlaylistItemMediaId + \" -> \" + itemKey);\n"
                        + "                clearUnarmedSeek();"));
        sourceFailures += source("SRC_HANDOVER_KEEPS_THE_OUTGOING_POSITION",
                !viewSrc.contains("if (!replacingOwner && mPlayer != null && !isDestroying)")
                        && viewSrc.contains("RNJWMediaSessionHelper.rememberPositionOnClose(mPlayer);"));
        sourceFailures += source("SRC_BACKGROUND_CLEANUP_SKIPS_UNRESUMED",
                handlerSrc.contains("RNJWMediaSessionHelper.isBeforeResumeSeek(externalMediaId, lastMs)"));

        Path output = Files.createTempDirectory("unarmed-seek-test-");
        Path generated = output.resolve("UnarmedSeekHarness.java");
        Files.write(generated, HARNESS.replace("/* PRODUCTION_MEMBERS */",
                String.join("\n", members.values())).getBytes(StandardCharsets.UTF_8));
        int result = compiler.run(null, null, null, "-d", output.toString(), generated.toString());
        if (result != 0) {
            throw new AssertionError("Production-member harness compilation failed: " + result);
        }
        try (URLClassLoader loader = new URLClassLoader(new java.net.URL[] {output.toUri().toURL()})) {
            Class<?> test = loader.loadClass("UnarmedSeekHarness");
            test.getMethod("main", String[].class).invoke(null, (Object) new String[0]);
        }
        System.out.println("source assertions: " + (6 - sourceFailures) + "/6");
        if (sourceFailures > 0) {
            throw new AssertionError(sourceFailures + " source assertions failed");
        }
    }

    private static String read(Path path) throws Exception {
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }

    private static int source(String name, boolean ok) {
        if (!ok) {
            System.out.println("FAIL " + name);
        }
        return ok ? 0 : 1;
    }
}
