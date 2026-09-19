package com.jwplayer.rnjwplayer.session;

import java.util.ArrayList;
import java.util.List;

/**
 * Deterministic harness for {@link PositionModel}: the authoritative position and its BOUNDED
 * reacquisition. Pure JVM, injected clock, no Android and no SDK.
 *
 * Every fixture is anchored to a measured device capture where possible; the citation is in the
 * fixture's comment. Run:
 *
 * <pre>
 *   javac -d /tmp/pm android/src/main/java/com/jwplayer/rnjwplayer/session/PositionModel.java \
 *                    android/src/test/java/com/jwplayer/rnjwplayer/session/PositionModelRegressionTest.java
 *   java -cp /tmp/pm com.jwplayer.rnjwplayer.session.PositionModelRegressionTest
 * </pre>
 */
public final class PositionModelRegressionTest {

    private static final List<String> FAILURES = new ArrayList<>();
    private static final List<String> PASSES = new ArrayList<>();

    private static void record(String name, String failure) {
        if (failure == null) {
            PASSES.add(name);
        } else {
            FAILURES.add(name + ": " + failure);
        }
    }

    private static String eq(String what, long expected, long actual) {
        return expected == actual ? null : (what + " expected " + expected + " got " + actual);
    }

    private static String firstFailure(String... candidates) {
        for (String c : candidates) {
            if (c != null) {
                return c;
            }
        }
        return null;
    }

    // ---------------------------------------------------------------- fixtures

    /** Steady playback: every sample agrees, the model tracks it exactly, nothing is rejected. */
    private static void steadyPlaybackTracksReality() {
        PositionModel m = new PositionModel();
        m.onPlaying(0L);
        m.observe(8_077L, 0L);
        String failure = null;
        for (int i = 1; i <= 10 && failure == null; i++) {
            long now = i * 1_000L;
            PositionModel.Decision d = m.observe(8_077L + i * 1_000L, now);
            if (d.verdict != PositionModel.Verdict.ACCEPTED) {
                failure = "tick " + i + " was " + d.verdict + " instead of ACCEPTED";
            } else {
                failure = eq("published position at tick " + i, 8_077L + i * 1_000L, d.positionMs);
            }
        }
        record("STEADY_PLAYBACK_TRACKS_REALITY", failure);
    }

    /**
     * Reproduces the 2026-09-19 18:08 device failure. The helper syncs playback state before EVERY
     * observe, so if that sync clears the rejection streak, reacquisition can never reach its bound:
     * the capture showed a bad commanded resume of 1391000ms held against real samples of 0, 33,
     * 81 … 59305ms with `streak=1/5` on every line, publishing a 23-minute fiction indefinitely.
     */
    private static void repeatedStateSyncMustNotDisableReacquisition() {
        PositionModel m = new PositionModel();
        m.onPlaying(0L);
        m.onCommandedPosition(1_391_000L, 0L);      // a WRONG command, exactly as the app issued
        int reacquiredAtTick = -1;
        for (int i = 1; i <= PositionModel.REACQUIRE_AFTER_REJECTS + 3 && reacquiredAtTick < 0; i++) {
            long now = i * 1_000L;
            m.onPlaying(now);                        // the idempotent sync the helper performs
            PositionModel.Decision d = m.observe(i * 1_000L, now);   // reality: ~0 and climbing
            if (d.verdict == PositionModel.Verdict.REACQUIRED) {
                reacquiredAtTick = i;
            }
        }
        record("REPEATED_STATE_SYNC_MUST_NOT_DISABLE_REACQUISITION", firstFailure(
                reacquiredAtTick > 0 ? null
                        : "repeated state sync reset the streak; the model would publish fiction forever",
                reacquiredAtTick <= PositionModel.REACQUIRE_AFTER_REJECTS + 1 ? null
                        : "reacquired only at tick " + reacquiredAtTick + ", beyond the bound"));
    }

    /** A pause/resume mid-disagreement must not reset the convergence budget either. */
    private static void pauseResumeDoesNotResetRejectStreak() {
        PositionModel m = new PositionModel();
        m.onPlaying(0L);
        m.onCommandedPosition(900_000L, 0L);
        m.observe(1_000L, 1_000L);
        m.observe(2_000L, 2_000L);
        int streakBefore = m.currentRejectStreak();
        m.onNotPlaying(2_500L);
        m.onPlaying(3_000L);
        int streakAfter = m.currentRejectStreak();
        record("PAUSE_RESUME_DOES_NOT_RESET_REJECT_STREAK", firstFailure(
                streakBefore >= 2 ? null : "streak did not accumulate, was " + streakBefore,
                eq("streak preserved across pause/resume", streakBefore, streakAfter)));
    }

    /**
     * The 2026-09-19 14:19 regression. A confirmed seek to 2568766ms is followed 1.3s later by
     * getPosition() reverting to the PRE-SEEK playhead (12524ms). That sample must be rejected and
     * the projection published, instead of becoming the anchor Android Auto extrapolates from.
     */
    private static void postSeekRevertIsRejected() {
        PositionModel m = new PositionModel();
        m.onPlaying(0L);
        m.onCommandedPosition(2_568_766L, 0L);
        PositionModel.Decision d = m.observe(12_524L, 1_300L);
        String failure = firstFailure(
                d.verdict == PositionModel.Verdict.REJECTED ? null
                        : "pre-seek playhead was " + d.verdict + " instead of REJECTED",
                eq("published the projection, not the bogus sample", 2_570_066L, d.positionMs),
                d.rejectStreak == 1 ? null : "reject streak should start at 1, was " + d.rejectStreak);
        record("POST_SEEK_REVERT_REJECTED", failure);
    }

    /**
     * The 2026-09-19 12:22 regression: a rebuffer emits one transient near-zero sample 1.3s after a
     * confirmed seek to 953931ms, then reality resumes correctly. The transient must be rejected AND
     * must not poison the streak, so the recovery sample is accepted immediately.
     */
    private static void rebufferTransientRejectedThenRecovers() {
        PositionModel m = new PositionModel();
        m.onPlaying(0L);
        m.onCommandedPosition(953_931L, 0L);
        PositionModel.Decision transient1 = m.observe(1_213L, 1_300L);
        PositionModel.Decision recovery = m.observe(955_431L, 1_500L);
        String failure = firstFailure(
                transient1.verdict == PositionModel.Verdict.REJECTED ? null
                        : "transient was " + transient1.verdict,
                eq("projection published during the transient", 955_231L, transient1.positionMs),
                recovery.verdict == PositionModel.Verdict.ACCEPTED ? null
                        : "recovery sample was " + recovery.verdict + " instead of ACCEPTED",
                eq("model tracks the recovery", 955_431L, recovery.positionMs));
        record("REBUFFER_TRANSIENT_REJECTED_THEN_RECOVERS", failure);
    }

    /**
     * BOUNDED REACQUISITION — the addition this phase required. If reality disagrees COHERENTLY,
     * the model is the thing that is wrong (an external seek, an unobserved source change) and it
     * must converge on reality within a bounded number of ticks rather than publishing fiction
     * forever.
     */
    private static void coherentDisagreementReacquiresWithinBound() {
        PositionModel m = new PositionModel();
        m.onPlaying(0L);
        m.observe(100_000L, 0L);
        // Reality jumps to 500s and then advances normally from there — the model did not command it.
        int reacquiredAtTick = -1;
        PositionModel.Decision last = null;
        for (int i = 1; i <= PositionModel.REACQUIRE_AFTER_REJECTS + 2; i++) {
            long now = i * 1_000L;
            last = m.observe(500_000L + (i - 1) * 1_000L, now);
            if (last.verdict == PositionModel.Verdict.REACQUIRED && reacquiredAtTick < 0) {
                reacquiredAtTick = i;
            }
        }
        String failure = firstFailure(
                reacquiredAtTick > 0 ? null : "model never reacquired; it would publish fiction forever",
                reacquiredAtTick <= PositionModel.REACQUIRE_AFTER_REJECTS ? null
                        : "reacquired at tick " + reacquiredAtTick + ", beyond the bound of "
                          + PositionModel.REACQUIRE_AFTER_REJECTS,
                eq("streak at reacquisition", PositionModel.REACQUIRE_AFTER_REJECTS,
                        reacquiredAtTick > 0 ? PositionModel.REACQUIRE_AFTER_REJECTS : -1));
        record("COHERENT_DISAGREEMENT_REACQUIRES_WITHIN_BOUND", failure);
    }

    /** After reacquisition the model must be converged: the next sample is simply ACCEPTED. */
    private static void reacquisitionConvergesImmediately() {
        PositionModel m = new PositionModel();
        m.onPlaying(0L);
        m.observe(100_000L, 0L);
        for (int i = 1; i <= PositionModel.REACQUIRE_AFTER_REJECTS; i++) {
            m.observe(500_000L + (i - 1) * 1_000L, i * 1_000L);
        }
        long nextTick = (PositionModel.REACQUIRE_AFTER_REJECTS + 1) * 1_000L;
        long nextSample = 500_000L + PositionModel.REACQUIRE_AFTER_REJECTS * 1_000L;
        PositionModel.Decision after = m.observe(nextSample, nextTick);
        record("REACQUISITION_CONVERGES_IMMEDIATELY", firstFailure(
                after.verdict == PositionModel.Verdict.ACCEPTED ? null
                        : "post-reacquisition sample was " + after.verdict,
                eq("converged position", nextSample, after.positionMs),
                eq("deviation after convergence", 0L, after.deviationMs)));
    }

    /**
     * The other half of bounded reacquisition: INCOHERENT noise must never reacquire, however long
     * it lasts. Each wild sample restarts the streak, so the model holds indefinitely — which is the
     * behaviour that protects against the five catalogued SDK anomalies.
     */
    private static void incoherentNoiseNeverReacquires() {
        PositionModel m = new PositionModel();
        m.onPlaying(0L);
        m.observe(100_000L, 0L);
        long[] noise = {0L, 900_000L, 5L, 450_000L, 12L, 777_000L, 3L, 250_000L, 9L, 600_000L};
        String failure = null;
        for (int i = 0; i < noise.length && failure == null; i++) {
            PositionModel.Decision d = m.observe(noise[i], (i + 1) * 1_000L);
            if (d.verdict == PositionModel.Verdict.REACQUIRED) {
                failure = "reacquired on incoherent noise at sample " + i + " (" + noise[i] + "ms)";
            } else if (d.verdict != PositionModel.Verdict.REJECTED) {
                failure = "noise sample " + i + " was " + d.verdict + " instead of REJECTED";
            }
        }
        if (failure == null) {
            failure = eq("streak never exceeded 1 under incoherent noise", 1, m.currentRejectStreak());
        }
        record("INCOHERENT_NOISE_NEVER_REACQUIRES", failure);
    }

    /** A commanded position always wins outright — that disagreement is explained by construction. */
    private static void commandedPositionAlwaysWins() {
        PositionModel m = new PositionModel();
        m.onPlaying(0L);
        m.observe(10_000L, 0L);
        m.observe(900_000L, 1_000L);   // rejected, streak 1
        m.onCommandedPosition(2_000_000L, 1_500L);
        PositionModel.Decision d = m.observe(2_000_500L, 2_000L);
        record("COMMANDED_POSITION_ALWAYS_WINS", firstFailure(
                eq("streak cleared by the command", 0, m.currentRejectStreak()),
                d.verdict == PositionModel.Verdict.ACCEPTED ? null
                        : "sample agreeing with the commanded anchor was " + d.verdict,
                eq("anchor moved to the command", 2_000_500L, d.positionMs)));
    }

    /** While paused the model is frozen: it must not advance, and a stale sample must not drag it. */
    private static void pausedModelIsFrozen() {
        PositionModel m = new PositionModel();
        m.onPlaying(0L);
        m.observe(60_000L, 0L);
        m.onNotPlaying(1_000L);
        long projectedAfter10s = m.projectedMs(11_000L);
        PositionModel.Decision d = m.observe(61_000L, 11_000L);
        record("PAUSED_MODEL_IS_FROZEN", firstFailure(
                eq("frozen projection does not advance", 61_000L, projectedAfter10s),
                d.verdict == PositionModel.Verdict.ACCEPTED ? null
                        : "sample at the frozen position was " + d.verdict));
    }

    /** Resuming restarts the clock from the frozen anchor, not from the pause's wall time. */
    private static void resumeAdvancesFromFrozenAnchor() {
        PositionModel m = new PositionModel();
        m.onPlaying(0L);
        m.observe(60_000L, 0L);
        m.onNotPlaying(1_000L);
        m.onPlaying(30_000L);                       // paused for 29s
        long projected = m.projectedMs(32_000L);     // 2s of playback since resume
        record("RESUME_ADVANCES_FROM_FROZEN_ANCHOR",
                eq("projection after resume", 63_000L, projected));
    }

    /** Projection must honour the playback rate. Rates were untestable before this model existed. */
    private static void projectionHonoursRate() {
        PositionModel half = new PositionModel();
        half.onPlaying(0L);
        half.observe(100_000L, 0L);
        half.onRateChanged(0.5d, 0L);
        PositionModel dbl = new PositionModel();
        dbl.onPlaying(0L);
        dbl.observe(100_000L, 0L);
        dbl.onRateChanged(2.0d, 0L);
        record("PROJECTION_HONOURS_RATE", firstFailure(
                eq("0.5x after 10s", 105_000L, half.projectedMs(10_000L)),
                eq("2.0x after 10s", 120_000L, dbl.projectedMs(10_000L))));
    }

    /** An unavailable reading must never become an anchor (2026-09-17: -1s published as -1000ms). */
    private static void unavailableSampleNeverAnchors() {
        PositionModel m = new PositionModel();
        m.onPlaying(0L);
        m.observe(50_000L, 0L);
        PositionModel.Decision d = m.observe(-1_000L, 1_000L);
        record("UNAVAILABLE_SAMPLE_NEVER_ANCHORS", firstFailure(
                d.verdict == PositionModel.Verdict.UNAVAILABLE ? null
                        : "negative sample was " + d.verdict,
                eq("projection published instead", 51_000L, d.positionMs),
                eq("anchor untouched", 50_000L, m.anchorPositionMs())));
    }

    /**
     * A track change invalidates everything, and must NOT seed from the next sample: measured
     * 2026-09-19 17:25, {@code getPosition()} still reported the PREVIOUS item's playhead (66014 ms)
     * 3.3 s after the switch while the session had already reset to 0. Seeding is the one decision
     * with no projection to check against, so a wrong sample there cannot be caught later.
     */
    private static void trackChangeDoesNotSeedFromStaleSample() {
        PositionModel m = new PositionModel();
        m.onPlaying(0L);
        m.observe(66_069L, 0L);          // healthy on the old item
        m.reset();
        PositionModel.Decision stale = m.observe(66_014L, 3_300L);   // old item's playhead
        boolean seededByStale = m.isSeeded();
        m.onCommandedPosition(0L, 3_400L);                            // the new item's resume point
        PositionModel.Decision real = m.observe(40L, 3_500L);         // new item really at ~0
        PositionModel.Decision stillStale = m.observe(66_051L, 4_400L);
        record("TRACK_CHANGE_DOES_NOT_SEED_FROM_STALE_SAMPLE", firstFailure(
                stale.verdict == PositionModel.Verdict.UNAVAILABLE ? null
                        : "stale post-reset sample was " + stale.verdict + " instead of withheld",
                seededByStale ? "model seeded on the previous item's playhead" : null,
                stale.positionMs < 0 ? null : "withheld sample still published " + stale.positionMs,
                real.verdict == PositionModel.Verdict.ACCEPTED ? null
                        : "new item's real position was " + real.verdict,
                stillStale.verdict == PositionModel.Verdict.REJECTED ? null
                        : "old item's lingering playhead was " + stillStale.verdict));
    }

    /**
     * The other side of that wait: it is BOUNDED. If a track change never delivers a commanded
     * resume position, the model must seed from samples rather than stalling unseeded forever.
     */
    private static void resetSeedsWithinBoundWithoutCommand() {
        PositionModel m = new PositionModel();
        m.onPlaying(0L);
        m.observe(10_000L, 0L);
        m.reset();
        m.onPlaying(1_000L);
        int seededAtTick = -1;
        for (int i = 1; i <= PositionModel.REACQUIRE_AFTER_REJECTS + 2 && seededAtTick < 0; i++) {
            PositionModel.Decision d = m.observe(5_000L + i * 1_000L, 1_000L + i * 1_000L);
            if (d.verdict == PositionModel.Verdict.SEEDED) {
                seededAtTick = i;
            }
        }
        record("RESET_SEEDS_WITHIN_BOUND_WITHOUT_COMMAND", firstFailure(
                seededAtTick > 0 ? null : "model never seeded; it would stall unseeded forever",
                seededAtTick <= PositionModel.REACQUIRE_AFTER_REJECTS + 1 ? null
                        : "seeded only at tick " + seededAtTick + ", beyond the bound"));
    }

    /** A cold model must not publish a negative position before it has ever seen a sample. */
    private static void unseededModelPublishesNothingInvalid() {
        PositionModel m = new PositionModel();
        PositionModel.Decision d = m.observe(-1L, 0L);
        record("UNSEEDED_MODEL_PUBLISHES_NOTHING_INVALID", firstFailure(
                d.verdict == PositionModel.Verdict.UNAVAILABLE ? null
                        : "unseeded negative sample was " + d.verdict,
                d.positionMs < 0 ? null : "unseeded model invented position " + d.positionMs,
                m.isSeeded() ? "unseeded model reported itself seeded" : null));
    }

    public static void main(String[] args) {
        steadyPlaybackTracksReality();
        repeatedStateSyncMustNotDisableReacquisition();
        pauseResumeDoesNotResetRejectStreak();
        postSeekRevertIsRejected();
        rebufferTransientRejectedThenRecovers();
        coherentDisagreementReacquiresWithinBound();
        reacquisitionConvergesImmediately();
        incoherentNoiseNeverReacquires();
        commandedPositionAlwaysWins();
        pausedModelIsFrozen();
        resumeAdvancesFromFrozenAnchor();
        projectionHonoursRate();
        unavailableSampleNeverAnchors();
        trackChangeDoesNotSeedFromStaleSample();
        resetSeedsWithinBoundWithoutCommand();
        unseededModelPublishesNothingInvalid();

        System.out.println("=== POSITION MODEL REGRESSION HARNESS ==========================");
        for (String p : PASSES) {
            System.out.printf("  %-46s PASS%n", p);
        }
        for (String f : FAILURES) {
            System.out.printf("  %-46s FAIL%n", f);
        }
        System.out.println("---------------------------------------------------------------");
        System.out.printf("passed=%d  failed=%d%n", PASSES.size(), FAILURES.size());
        System.out.println("bounds: agreement=" + PositionModel.AGREEMENT_TOLERANCE_MS
                + "ms  coherence=" + PositionModel.COHERENCE_TOLERANCE_MS
                + "ms  reacquire-after=" + PositionModel.REACQUIRE_AFTER_REJECTS + " ticks");
        if (!FAILURES.isEmpty()) {
            System.exit(1);
        }
    }
}
