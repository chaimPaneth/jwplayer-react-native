package com.jwplayer.rnjwplayer.session;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Deterministic regression harness for the Android Auto + PiP seek desynchronization documented in
 * AA_PIP_SEEK_DESYNC_FORENSICS_2026-09-17.md.
 *
 * <p>No Android, no JW SDK, no Robolectric, no JUnit, no sleeps. The clock is a plain counter. The
 * seek decisions are NOT reimplemented here: every accept/reject/clear/publish answer comes from the
 * production {@link SeekAcceptanceState} instance, which owns the decisions extracted out of
 * {@code RNJWMediaSessionHelper}. This class only (a) sequences the events in the order the helper
 * calls them and (b) records what reached each authoritative sink.
 *
 * <p>Run standalone:
 * <pre>
 *   cd android/src/main/java &amp;&amp; javac -d /tmp/seekout \
 *       com/jwplayer/rnjwplayer/session/SeekAcceptanceState.java \
 *       ../../test/java/com/jwplayer/rnjwplayer/session/SeekAcceptanceRegressionTest.java
 *   java -cp /tmp/seekout com.jwplayer.rnjwplayer.session.SeekAcceptanceRegressionTest
 * </pre>
 *
 * <p>Any failed regression exits nonzero. The harness does not execute Android or the JW SDK.
 */
public final class SeekAcceptanceRegressionTest {

    // ---------------------------------------------------------------- harness

    /** Monotonic fake clock. Nothing in this file reads real time. */
    private static final class Clock {
        private long nowMs;

        void advanceTo(long ms) {
            if (ms < nowMs) {
                throw new IllegalArgumentException("clock must be monotonic: " + ms + " < " + nowMs);
            }
            nowMs = ms;
        }

        long now() {
            return nowMs;
        }
    }

    /**
     * Records the authoritative sinks the forensic report distinguishes. Mirrors the helper's call
     * ORDER only; every decision is delegated to {@link SeekAcceptanceState}.
     */
    private static final class Harness {
        final Clock clock = new Clock();
        final SeekAcceptanceState state = new SeekAcceptanceState();
        final List<String> timeline = new ArrayList<>();

        /** MediaSession PlaybackState position, i.e. what Android Auto anchors on. */
        long sessionPositionMs = -1L;
        String sessionState = "NONE";
        /** Native resume cache (helper's static lastKnownPositionCache equivalent). */
        final Map<String, Long> resumeCache = new LinkedHashMap<>();
        /** Recovery snapshot (handler's rememberPlaybackPosition equivalent). */
        long recoverySnapshotMs = -1L;
        /** Handoff acknowledgement value. */
        long handoffAckMs = -1L;

        /** Raw player observation, controlled by the fixture. */
        long liveMs;
        boolean playing;
        String mediaId = "16357";
        /** Set from the onSeek echo, exactly as the helper does. */
        long lastRequestedSeekPositionMs = -1L;

        boolean protectionReleased;
        long protectionReleasedAtMs = -1L;
        String protectionReleaseReason;

        private final SeekAcceptanceState.ClearListener clearListener =
                new SeekAcceptanceState.ClearListener() {
                    @Override
                    public void onCleared(String reason, long clearedTargetMs, long ageMs) {
                        protectionReleased = true;
                        protectionReleasedAtMs = clock.now();
                        protectionReleaseReason = reason;
                        log("CLEAR target=" + clearedTargetMs + " age=" + ageMs + " reason=" + reason);
                    }
                };

        void log(String s) {
            timeline.add("t=" + clock.now() + "ms " + s);
        }

        /** MediaSession callback -> performSeekTo: the one authoritative dispatch. */
        void onSeekTo(long targetMs) {
            log("onSeekTo(" + targetMs + ")");
            SeekAcceptanceState.ArmOutcome outcome =
                    state.arm(targetMs, true, clock.now());
            log("arm(authoritative) accepted=" + outcome.accepted
                    + " target=" + state.targetInFlightMs());
            // The helper publishes the requested target immediately with an explicit override.
            publishWithOverride("PLAYING", targetMs);
            storeSeekPosition(targetMs);
        }

        /** JW's own onSeek echo. Non-authoritative; also writes lastRequestedSeekPositionMs. */
        void onSeekEcho(long offsetMs) {
            log("onSeek echo offset=" + offsetMs);
            lastRequestedSeekPositionMs = offsetMs;
            SeekAcceptanceState.ArmOutcome outcome =
                    state.arm(offsetMs, false, clock.now());
            log("arm(echo) accepted=" + outcome.accepted
                    + " target=" + state.targetInFlightMs()
                    + " armedAt=" + state.targetArmedAtMs());
        }

        /** A state publication with no explicit override: goes through the publish resolver. */
        long publishFromResolver(String newState) {
            boolean settled = "PLAYING".equals(newState) || "PAUSED".equals(newState);
            SeekAcceptanceState.PublishDecision decision = state.resolvePublish(
                liveMs, playing && "PLAYING".equals(newState), settled, clock.now(), clearListener);
            long resolved = decision.positionMs;
            log("resolvePublish outcome=" + decision.outcome + " live=" + decision.liveMs
                + " projected=" + decision.projectedMs + " age=" + decision.ageMs + " -> " + resolved);
            // Mirrors resolvePublishPositionMs: an ABANDONED target's release also gets written
            // into the resume cache so it reflects reality instead of the never-reached target.
            if (decision.outcome == SeekAcceptanceState.PublishOutcome.ABANDONED) {
                storeSeekPosition(resolved);
            }
            sessionPositionMs = resolved;
            sessionState = newState;
            log("SESSION <= " + resolved + " (" + newState + ")");
            return resolved;
        }

        void publishWithOverride(String newState, long positionMs) {
            sessionPositionMs = positionMs;
            sessionState = newState;
            log("SESSION <= " + positionMs + " (" + newState + ", override)");
        }

        /** Only the in-flight guard is production; other storeSeekPosition guards stay in helper. */
        void storeSeekPosition(long positionMs) {
            if (state.blocksStore(positionMs, clock.now())) {
                log("storeSeekPosition BLOCKED " + positionMs);
                return;
            }
            resumeCache.put(mediaId, positionMs);
            log("RESUME_CACHE[" + mediaId + "] <= " + positionMs);
        }

        /** JW completion callback. */
        long onSeeked(long eventPositionMs) {
            SeekAcceptanceState.SeekedDecision d =
                    state.resolveSeeked(eventPositionMs, lastRequestedSeekPositionMs);
            log("onSeeked event=" + eventPositionMs + " -> effective=" + d.positionMs
                    + " substituted=" + d.substitutedRequest);
            publishWithOverride("BUFFERING", d.positionMs);
            storeSeekPosition(d.positionMs);
            handoffAckMs = d.positionMs;
            return d.positionMs;
        }

        /** JW play callback: stores the RAW observation then republishes from the resolver. */
        void onPlay() {
            log("onPlay (raw live=" + liveMs + ")");
            storeSeekPosition(liveMs);
            if (!state.blocksStore(liveMs, clock.now())) {
                recoverySnapshotMs = liveMs;
                log("RECOVERY <= " + liveMs);
            }
            publishFromResolver("PLAYING");
        }

        Long cached() {
            return resumeCache.get(mediaId);
        }
    }

    // ------------------------------------------------------------ assertions

    private static final class Result {
        final String name;
        final boolean passed;
        final String detail;
        final boolean expectedToFail;
        final List<String> timeline;

        Result(String name, boolean passed, String detail, boolean expectedToFail,
                List<String> timeline) {
            this.name = name;
            this.passed = passed;
            this.detail = detail;
            this.expectedToFail = expectedToFail;
            this.timeline = timeline;
        }
    }

    private static final List<Result> RESULTS = new ArrayList<>();
    private static final List<String> POLICY = new ArrayList<>();
    private static final List<String> UNTESTABLE = new ArrayList<>();

    private static void record(String name, boolean passed, String detail, boolean expectedToFail,
            List<String> timeline) {
        RESULTS.add(new Result(name, passed, detail, expectedToFail, timeline));
    }

    private static String eq(String what, long expected, long actual) {
        return actual == expected ? null : what + " expected=" + expected + " actual=" + actual;
    }

    private static String firstFailure(String... checks) {
        for (String c : checks) {
            if (c != null) {
                return c;
            }
        }
        return null;
    }

    // -------------------------------------------------- primary regression

    /**
     * Seek #3 from the capture. Times are milliseconds relative to the original command at
     * 20:38:57.916, taken from the forensic report's failure timeline.
     */
    private static void seek3Regression() {
        Harness h = new Harness();
        h.mediaId = "16357";
        h.liveMs = 8736;      // pre-seek playhead
        h.playing = true;
        h.lastRequestedSeekPositionMs = -1L;

        // t=0 (57.916) AA command; helper arms and optimistically publishes the target.
        h.clock.advanceTo(0);
        h.onSeekTo(711215);

        // t=63 (57.979) agreeing echo. The original code re-stamped the arm time here.
        h.clock.advanceTo(63);
        h.onSeekEcho(711215);

        // t=68 (57.984) a transient AGREEING sample arrives while still BUFFERING.
        // The original reported age was 5 ms because the echo restarted the clock; real age since
        // the command is 68 ms. The defective implementation released protection here.
        h.clock.advanceTo(68);
        h.liveMs = 711215;
        long publishedAtRelease = h.publishFromResolver("BUFFERING");

        // t=1290 (59.206) completion finally arrives, reporting the STALE playhead.
        h.clock.advanceTo(1290);
        h.liveMs = 8736;
        h.onSeeked(8736);

        // t=1298 (59.214) onPlay stores the raw stale observation and republishes.
        h.clock.advanceTo(1298);
        h.onPlay();

        long expectedFloor = 711215;
        String failure = firstFailure(
                h.protectionReleased && h.protectionReleasedAtMs < 1290
                        ? "protection released at t=" + h.protectionReleasedAtMs
                            + "ms, BEFORE authoritative completion at t=1290ms"
                        : null,
                h.sessionPositionMs < expectedFloor
                        ? "MediaSession regressed to " + h.sessionPositionMs
                            + "ms (must remain >= requested target " + expectedFloor + "ms)"
                        : null,
                h.cached() != null && h.cached() < expectedFloor
                        ? "native resume cache regressed to " + h.cached()
                            + "ms (must remain >= " + expectedFloor + "ms)"
                        : null,
                h.recoverySnapshotMs >= 0 && h.recoverySnapshotMs < expectedFloor
                        ? "recovery snapshot regressed to " + h.recoverySnapshotMs + "ms"
                        : null);

        h.log("published-at-release=" + publishedAtRelease);
        record("SEEK3_REGRESSION", failure == null, failure, false, h.timeline);
    }

    /** Repair requirement 1: an agreeing echo must not restart the command deadline. */
    private static void echoMustNotResetDeadline() {
        Harness h = new Harness();
        h.liveMs = 8736;
        h.playing = true;
        h.clock.advanceTo(0);
        h.onSeekTo(711215);
        long armedAfterCommand = h.state.targetArmedAtMs();

        h.clock.advanceTo(63);
        h.onSeekEcho(711215);
        long armedAfterEcho = h.state.targetArmedAtMs();

        String failure = armedAfterEcho != armedAfterCommand
                ? "agreeing echo moved the command deadline from t=" + armedAfterCommand
                    + "ms to t=" + armedAfterEcho + "ms"
                : null;
        record("ECHO_MUST_NOT_RESET_DEADLINE", failure == null, failure, false, h.timeline);
    }

    private static void bufferingAgreementAfterCompletionMustNotRelease() {
        Harness harness = new Harness();
        harness.clock.advanceTo(0);
        harness.playing = false;
        harness.liveMs = 282532;
        harness.onSeekTo(1016830);
        harness.clock.advanceTo(1000);
        harness.onSeeked(282532);
        harness.clock.advanceTo(1010);
        harness.liveMs = 1016830;
        harness.publishFromResolver("BUFFERING");

        String failure = harness.protectionReleased
                ? "an agreeing BUFFERING sample released protection after a stale completion"
                : null;
        record("BUFFERING_AGREEMENT_AFTER_COMPLETION", failure == null, failure,
                false, harness.timeline);
    }

    // ------------------------------------------------------ boundary fixtures

    private static void normalForwardSeek() {
        Harness h = new Harness();
        h.liveMs = 100000;
        h.playing = true;
        h.clock.advanceTo(0);
        h.onSeekTo(200000);
        h.clock.advanceTo(40);
        h.onSeekEcho(200000);
        h.clock.advanceTo(900);
        h.liveMs = 200000;          // genuine landing
        h.onSeeked(200000);
        h.clock.advanceTo(950);
        h.onPlay();
        String failure = firstFailure(
                eq("session", 200000, h.sessionPositionMs),
            eq("resume cache", 200000, h.cached() == null ? -1 : h.cached()),
            h.protectionReleased ? null : "settled forward seek was never accepted");
        record("NORMAL_FORWARD_SEEK", failure == null, failure, false, h.timeline);
    }

    private static void normalBackwardSeek() {
        Harness h = new Harness();
        h.liveMs = 700000;
        h.playing = true;
        h.clock.advanceTo(0);
        h.onSeekTo(200000);
        h.clock.advanceTo(40);
        h.onSeekEcho(200000);
        h.clock.advanceTo(900);
        h.liveMs = 200000;
        h.onSeeked(200000);
        h.clock.advanceTo(950);
        h.onPlay();
        String failure = firstFailure(
                eq("session", 200000, h.sessionPositionMs),
            eq("resume cache", 200000, h.cached() == null ? -1 : h.cached()),
            h.protectionReleased ? null : "settled backward seek was never accepted");
        record("NORMAL_BACKWARD_SEEK", failure == null, failure, false, h.timeline);
    }

    private static void explicitSeekToZero() {
        Harness h = new Harness();
        h.liveMs = 500000;
        h.playing = true;
        h.clock.advanceTo(0);
        h.onSeekTo(0);
        boolean guardActive = h.state.blocksStore(500000, h.clock.now());
        SeekAcceptanceState.SeekedDecision d = h.state.resolveSeeked(500000, 0);
        h.log("blocksStore(stale 500000) while target=0 -> " + guardActive);
        h.log("resolveSeeked(event=500000, lastRequested=0) -> " + d.positionMs);
        SeekAcceptanceState.ArmOutcome staleEcho = h.state.arm(500000, false, 40L);
        h.clock.advanceTo(100L);
        h.liveMs = 0L;
        h.onSeeked(0L);
        h.onPlay();
        String failure = firstFailure(
            guardActive ? null : "zero target did not protect the store",
            eq("stale completion while seeking to zero", 0L, d.positionMs),
            staleEcho.accepted ? "stale echo overwrote a zero target" : null,
            eq("settled zero landing", 0L, h.sessionPositionMs),
            h.protectionReleased ? null : "settled zero seek was never accepted");
        record("EXPLICIT_SEEK_TO_ZERO", failure == null, failure, false, h.timeline);
    }

    /** Rapid A -> B, with A's delayed completion arriving after B was armed. */
    private static void rapidSeekDelayedCallbackFromA() {
        Harness h = new Harness();
        h.liveMs = 50000;
        h.playing = true;
        h.clock.advanceTo(0);
        h.onSeekTo(300000);
        h.clock.advanceTo(30);
        h.onSeekEcho(300000);
        h.clock.advanceTo(500);
        h.onSeekTo(800000);          // B supersedes A
        h.clock.advanceTo(530);
        h.onSeekEcho(800000);
        h.clock.advanceTo(700);
        h.onSeeked(300000);          // A's late completion
        String failure = h.sessionPositionMs < 800000
                ? "A's late completion moved authoritative state to " + h.sessionPositionMs
                    + "ms; B (800000ms) must remain authoritative"
                : firstFailure(eq("resume cache", 800000, h.cached() == null ? -1 : h.cached()));
        record("RAPID_A_TO_B_LATE_A_CALLBACK", failure == null, failure, false, h.timeline);
    }

    private static void seekWhilePaused() {
        Harness h = new Harness();
        h.liveMs = 400000;
        h.playing = false;
        h.clock.advanceTo(0);
        h.onSeekTo(120000);
        h.clock.advanceTo(2000);
        long published = h.publishFromResolver("PAUSED");
        String failure = eq("paused projection must not drift", 120000, published);
        record("SEEK_WHILE_PAUSED_NO_DRIFT", failure == null, failure, false, h.timeline);
    }

    private static void pausedSeekThenResumeMustAcceptActualLanding() {
        Harness harness = new Harness();
        harness.liveMs = 26142L;
        harness.playing = false;
        harness.onSeekTo(1379000L);
        harness.clock.advanceTo(300L);
        harness.onSeeked(26142L);
        harness.publishFromResolver("PAUSED");
        harness.clock.advanceTo(65000L);
        harness.liveMs = 1379000L;
        harness.playing = true;
        harness.onPlay();
        String failure = firstFailure(
                eq("actual resumed playhead", 1379000L, harness.sessionPositionMs),
                harness.protectionReleased ? null : "valid landing rejected after a long pause");
        record("PAUSED_SEEK_THEN_RESUME", failure == null, failure, false, harness.timeline);
    }

    private static void pausedCorrectionIsBoundedToCurrentSeek() {
        SeekAcceptanceState state = new SeekAcceptanceState();
        state.arm(1139045L, true, 0L);
        boolean prematureCorrection = state.claimPausedCorrection(668542L, true);
        state.resolveSeeked(668542L, 1139045L);
        boolean playingWasCorrected = state.claimPausedCorrection(668542L, false);
        boolean firstCorrection = state.claimPausedCorrection(668542L, true);
        boolean duplicateCorrection = state.claimPausedCorrection(668542L, true);
        state.arm(0L, true, 100L);
        state.resolveSeeked(1139045L, 0L);
        boolean zeroCorrection = state.claimPausedCorrection(1139045L, true);
        boolean agreeingCorrection = state.claimPausedCorrection(0L, true);
        String failure = firstFailure(
            prematureCorrection ? "paused correction ran before seek completion" : null,
                playingWasCorrected ? "playing seek requested a paused correction" : null,
                firstCorrection ? null : "stale paused completion was not corrected",
                duplicateCorrection ? "paused correction repeated for the same request" : null,
                zeroCorrection ? null : "new zero seek did not get its own correction",
                agreeingCorrection ? "agreeing paused completion was corrected" : null);
        record("PAUSED_CORRECTION_BOUNDED", failure == null, failure, false,
                new ArrayList<String>());
    }

    private static void playingCorrectionIsBoundedToCurrentSeek() {
        SeekAcceptanceState state = new SeekAcceptanceState();
        state.arm(575888L, true, 0L);
        boolean tooEarly = state.claimPlayingCorrection(7026L, 2_000L);
        boolean firstCorrection = state.claimPlayingCorrection(7026L, 3_001L);
        boolean duplicateCorrection = state.claimPlayingCorrection(7026L, 3_002L);
        state.resolveSeeked(575888L, 575888L);
        boolean afterCompletionCorrection = state.claimPlayingCorrection(7026L, 20_000L);
        String failure = firstFailure(
                tooEarly ? "playing correction fired before its minimum age" : null,
                firstCorrection ? null : "unconfirmed disagreeing target was never corrected",
                duplicateCorrection ? "playing correction repeated for the same request" : null,
                afterCompletionCorrection ? "a target whose completion WAS observed still got a playing correction" : null);
        record("PLAYING_CORRECTION_BOUNDED", failure == null, failure, false,
                new ArrayList<String>());
    }

    /**
     * Reproduces the 2026-09-19 capture. Returning to the app after a background track change, the
     * JS restore seek to 226.0s was echoed by JW as onSeek(position=226.0, offset=0.0) — a spurious
     * ZERO destination — with NO seek in flight, because the track switch had cleared the target.
     * The echo guard only ever protected an EXISTING target, so 0 was installed as the authoritative
     * session position: the correct live 226000ms was discarded as a "stale playhead", 0 was
     * published, and 0 was written into the item's resume cache, restarting the media at 0:00.
     * An echo carries no requested position of its own and must never create a target.
     */
    private static void echoMustNotCreateTarget() {
        SeekAcceptanceState state = new SeekAcceptanceState();
        SeekAcceptanceState.ArmOutcome spurious = state.arm(0L, false, 1_000L);
        long targetAfterEcho = state.targetInFlightMs();
        // With nothing armed, publishing must follow the live player, not the echo.
        SeekAcceptanceState.PublishDecision published =
                state.resolvePublish(226_000L, true, true, 1_400L, null);
        // onSeeked must then be free to trust its own event position.
        SeekAcceptanceState.SeekedDecision seeked = state.resolveSeeked(226_000L, -1L);
        // An echo that agrees with a real armed seek must still be accepted, without re-arming it.
        SeekAcceptanceState armed = new SeekAcceptanceState();
        armed.arm(226_000L, true, 0L);
        SeekAcceptanceState.ArmOutcome agreeing = armed.arm(226_000L, false, 500L);
        String failure = firstFailure(
                spurious.accepted ? "a spurious echo created a seek target with none in flight" : null,
                eq("no target armed by an echo", -1L, targetAfterEcho),
                published.outcome == SeekAcceptanceState.PublishOutcome.NO_TARGET ? null
                        : "publishing was anchored by an echo; outcome=" + published.outcome,
                eq("published the live position", 226_000L, published.positionMs),
                eq("onSeeked trusts its own event when nothing was requested", 226_000L,
                        seeked.positionMs),
                seeked.substitutedRequest ? "a bogus requested position replaced a correct event" : null,
                agreeing.accepted ? null : "an echo agreeing with a real armed seek was refused");
        record("ECHO_MUST_NOT_CREATE_TARGET", failure == null, failure, false,
                new ArrayList<String>());
    }

    /**
     * Reproduces the 2026-09-18 17:29 capture exactly. An AA seek to 1806529ms was dispatched into
     * a still-loading item and swallowed; 2.2s later that item's OWN load finished and surfaced as
     * onSeeked(position=0). Crediting an unrelated callback as this command completing switched off
     * BOTH recovery paths at once — canClaimPlayingCorrection and the ABANDONED release are each
     * gated on completion being absent — so the lost target was published as truth for 16s while
     * playback ran from the start of the new track. A completion 1.8 million ms away from the
     * target is not this seek completing.
     */
    private static void unrelatedCompletionMustNotCreditSeek() {
        SeekAcceptanceState state = new SeekAcceptanceState();
        state.arm(1_806_529L, true, 0L);
        // The item-load completion, not ours: 0ms against a 1806529ms target.
        SeekAcceptanceState.SeekedDecision seeked = state.resolveSeeked(0L, 1_806_529L);
        boolean reissueStillReachable = state.canClaimPlayingCorrection(1_016L, 4_000L);
        SeekAcceptanceState.PublishDecision abandoned = state.resolvePublish(
                13_299L, true, true, SeekAcceptanceState.ABANDON_UNCONFIRMED_MS + 1L, null);
        // An attributed completion must still switch the re-issue off, or a landed seek would be
        // re-seeked for no reason.
        SeekAcceptanceState attributed = new SeekAcceptanceState();
        attributed.arm(1_806_529L, true, 0L);
        attributed.resolveSeeked(1_806_540L, 1_806_529L);
        boolean reissueAfterRealCompletion = attributed.canClaimPlayingCorrection(1_016L, 4_000L);
        String failure = firstFailure(
                eq("unrelated completion still publishes the requested target", 1_806_529L,
                        seeked.positionMs),
                reissueStillReachable ? null
                        : "an unrelated onSeeked(0) disabled the re-issue for a swallowed seek",
                abandoned.outcome == SeekAcceptanceState.PublishOutcome.ABANDONED ? null
                        : "swallowed seek was not abandoned; outcome=" + abandoned.outcome,
                eq("abandonment follows the live player", 13_299L, abandoned.positionMs),
                reissueAfterRealCompletion
                        ? "a seek whose completion DID corroborate the target still got a re-issue"
                        : null);
        record("UNRELATED_COMPLETION_NOT_CREDITED", failure == null, failure, false,
                new ArrayList<String>());
    }

    /**
     * Reproduces the 2026-09-18 capture: an AA seek dispatched into a still-loading player is
     * silently swallowed by the SDK, so no onSeeked/onTime agreement ever arrives. Without F3 the
     * target is held past the trust window forever; with it, the target is abandoned once the
     * re-issue window has also had time to land, and publishing follows the live player again.
     */
    private static void swallowedSeekDuringLoadIsEventuallyAbandoned() {
        Harness harness = new Harness();
        harness.playing = true;
        harness.liveMs = 0L;
        harness.onSeekTo(575888L);
        harness.clock.advanceTo(SeekAcceptanceState.SEEK_TARGET_TRUST_WINDOW_MS);
        long stillHeld = harness.publishFromResolver("PLAYING");
        harness.clock.advanceTo(SeekAcceptanceState.ABANDON_UNCONFIRMED_MS + 1L);
        harness.liveMs = 7026L;
        long released = harness.publishFromResolver("PLAYING");
        String failure = firstFailure(
                eq("held before abandonment deadline", 575888L, stillHeld),
                eq("released to the live player once abandoned", 7026L, released),
                harness.protectionReleased ? null : "a never-confirmed seek was held forever instead of abandoned",
                eq("resume cache follows the live player after abandonment", 7026L,
                        harness.cached() == null ? -1 : harness.cached()));
        record("SWALLOWED_SEEK_EVENTUALLY_ABANDONED", failure == null, failure, false, harness.timeline);
    }

        private static void settledPausedSeekReleasesProtection() {
        Harness harness = new Harness();
        harness.playing = false;
        harness.liveMs = 400000;
        harness.onSeekTo(120000);
        harness.clock.advanceTo(900);
        harness.liveMs = 120000;
        harness.onSeeked(120000);
        harness.clock.advanceTo(950);
        long published = harness.publishFromResolver("PAUSED");
        String failure = firstFailure(
            eq("paused landing", 120000, published),
            harness.protectionReleased ? null : "settled paused seek was never accepted");
        record("SETTLED_PAUSED_SEEK_RELEASES", failure == null, failure, false, harness.timeline);
        }

        private static void staleStoreRemainsBlockedAfterTrustWindow() {
        Harness harness = new Harness();
        harness.playing = true;
        harness.liveMs = 282532;
        harness.onSeekTo(1016830);
        harness.clock.advanceTo(1000);
        harness.onSeeked(282532);
        harness.clock.advanceTo(16000);
        harness.onPlay();
        String failure = firstFailure(
            eq("protected resume cache", 1016830,
                harness.cached() == null ? -1 : harness.cached()),
            eq("rejected recovery observation", -1, harness.recoverySnapshotMs),
            harness.protectionReleased ? "unconfirmed seek expired into stale live data" : null);
        record("STALE_STORE_BLOCKED_PAST_TRUST_WINDOW", failure == null, failure,
            false, harness.timeline);
        }

    /** BUFFERING modelled as not-playing: projection freezes, so no buffering time is added. */
    private static void seekWhileBuffering() {
        Harness h = new Harness();
        h.liveMs = 400000;
        h.playing = false;          // isCurrentlyPlaying() is false while BUFFERING via jwPlayer
        h.clock.advanceTo(0);
        h.onSeekTo(120000);
        h.clock.advanceTo(1800);
        long frozen = h.publishFromResolver("BUFFERING");
        h.playing = true;
        h.clock.advanceTo(1810);
        long afterPlay = h.publishFromResolver("PLAYING");
        String failure = firstFailure(
            eq("buffering target", 120000, frozen),
            eq("unconfirmed playing target", 120000, afterPlay));
        record("BUFFERING_TIME_NOT_COUNTED", failure == null, failure, false, h.timeline);
    }

        private static void unconfirmedSeekDoesNotInventProgress() {
        Harness h = new Harness();
        h.liveMs = 10000;
        h.playing = true;
        h.clock.advanceTo(0);
        h.onSeekTo(600000);
        h.clock.advanceTo(2000);
        long projected = h.publishFromResolver("PLAYING");
        String failure = eq("unconfirmed target", 600000, projected);
        record("UNCONFIRMED_SEEK_NO_DRIFT", failure == null, failure, false, h.timeline);
    }

    /** An unavailable SDK position (-1 s) must not become a valid -1000 ms anchor. */
    private static void unavailablePositionMustNotBecomeAnchor() {
        Harness h = new Harness();
        h.playing = true;
        h.liveMs = -1000;           // helper computed (long)(-1.0 * 1000)
        h.clock.advanceTo(0);
        long published = h.publishFromResolver("PLAYING");
        String failure = eq("unknown position sentinel", -1L, published);
        record("UNAVAILABLE_POSITION_NOT_ANCHOR", failure == null, failure, false, h.timeline);
    }

    /** A rejected observation must not touch session, cache, recovery or handoff. */
    private static void rejectedObservationTouchesNothing() {
        Harness h = new Harness();
        h.liveMs = 8736;
        h.playing = true;
        h.clock.advanceTo(0);
        h.onSeekTo(711215);
        h.clock.advanceTo(40);
        h.onSeekEcho(711215);
        // Stale sample while protection is still armed and disagreeing.
        h.clock.advanceTo(200);
        h.liveMs = 8736;
        h.publishFromResolver("BUFFERING");
        h.storeSeekPosition(8736);
        h.log("handoffAck=" + h.handoffAckMs + " recovery=" + h.recoverySnapshotMs);
        String failure = firstFailure(
                h.sessionPositionMs < 711215
                        ? "session took the rejected sample: " + h.sessionPositionMs : null,
                h.cached() != null && h.cached() < 711215
                        ? "cache took the rejected sample: " + h.cached() : null,
                h.recoverySnapshotMs >= 0 && h.recoverySnapshotMs < 711215
                        ? "recovery took the rejected sample: " + h.recoverySnapshotMs : null,
                h.handoffAckMs >= 0 && h.handoffAckMs < 711215
                        ? "handoff ack took the rejected sample: " + h.handoffAckMs : null);
        record("REJECTED_OBSERVATION_TOUCHES_NOTHING", failure == null, failure, false, h.timeline);
    }

    private static void notesForUncoverableCases() {
        UNTESTABLE.add("OWNER/MEDIA/REQUEST GENERATION: SeekAcceptanceState has no player identity, "
                + "media generation or request sequence, so 'generation changes while a seek is "
            + "pending' is not exercised here. Helper owner/request fencing requires integration tests.");
        UNTESTABLE.add("HEADLESS PERIODIC WRITER: JWPlayerNativePlaybackHandler publishes and saves "
            + "raw positions on its own schedule. Single-owner dispatch and ordering against the "
            + "helper's TIME confirmations require the Android Auto device tests.");
        UNTESTABLE.add("STORE GUARD INTERACTIONS: only the in-flight disagreement guard was "
                + "extracted. The AA-handoff zero guard and the zero-overwrite guard remain inline "
                + "in storeSeekPosition and still require Android to exercise.");
    }

    // ------------------------------------------------------------------ main

    public static void main(String[] args) {
        seek3Regression();
        echoMustNotResetDeadline();
        bufferingAgreementAfterCompletionMustNotRelease();
        normalForwardSeek();
        normalBackwardSeek();
        explicitSeekToZero();
        rapidSeekDelayedCallbackFromA();
        seekWhilePaused();
        pausedSeekThenResumeMustAcceptActualLanding();
        pausedCorrectionIsBoundedToCurrentSeek();
        playingCorrectionIsBoundedToCurrentSeek();
        swallowedSeekDuringLoadIsEventuallyAbandoned();
        unrelatedCompletionMustNotCreditSeek();
        echoMustNotCreateTarget();
        settledPausedSeekReleasesProtection();
        staleStoreRemainsBlockedAfterTrustWindow();
        seekWhileBuffering();
        unconfirmedSeekDoesNotInventProgress();
        unavailablePositionMustNotBecomeAnchor();
        rejectedObservationTouchesNothing();
        notesForUncoverableCases();

        int unexpected = 0;
        int reproduced = 0;
        System.out.println("=== SEEK ACCEPTANCE REGRESSION HARNESS =========================");
        for (Result r : RESULTS) {
            String verdict;
            if (r.passed) {
                verdict = r.name.endsWith("_OBSERVED") ? "OBSERVED (known limitation)"
                        : r.expectedToFail ? "PASS (bug appears repaired)" : "PASS";
            } else if (r.expectedToFail) {
                verdict = "FAIL (regression reproduced - EXPECTED)";
                reproduced++;
            } else {
                verdict = "FAIL (UNEXPECTED)";
                unexpected++;
            }
            System.out.println(pad(r.name) + verdict);
            if (r.detail != null) {
                System.out.println("        -> " + r.detail);
            }
        }

        System.out.println();
        System.out.println("--- SEEK #3 DETERMINISTIC TIMELINE ----------------------------");
        for (Result r : RESULTS) {
            if (r.name.equals("SEEK3_REGRESSION")) {
                for (String line : r.timeline) {
                    System.out.println("  " + line);
                }
            }
        }

        if (!POLICY.isEmpty()) {
            System.out.println();
            System.out.println("--- KNOWN LIMITATIONS (correct behavior not asserted) --------");
            for (String policy : POLICY) {
                System.out.println("  * " + policy);
            }
        }

        System.out.println();
        System.out.println("--- NOT DETERMINISTICALLY TESTABLE WITHOUT REFACTORING -------");
        for (String u : UNTESTABLE) {
            System.out.println("  * " + u);
        }

        System.out.println();
        System.out.println("reproduced-as-expected=" + reproduced + "  unexpected-failures=" + unexpected);
        if (unexpected > 0) {
            System.out.println("RESULT: UNEXPECTED FAILURES PRESENT");
            System.exit(2);
        }
        if (reproduced > 0) {
            System.out.println("RESULT: BUG REPRODUCED - harness correctly fails on current implementation");
            System.exit(1);
        }
        System.out.println("RESULT: all asserted regression checks passed; Android integration requires separate validation");
    }

    private static String pad(String s) {
        StringBuilder b = new StringBuilder("  " + s);
        while (b.length() < 42) {
            b.append(' ');
        }
        return b.toString();
    }

    private SeekAcceptanceRegressionTest() {
    }
}
