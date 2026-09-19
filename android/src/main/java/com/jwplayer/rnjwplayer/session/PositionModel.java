package com.jwplayer.rnjwplayer.session;

/**
 * The authoritative playback position, and the single decision about whether a position reported by
 * the JW SDK may be believed.
 *
 * <h3>Why this exists</h3>
 * JW SDK 4.26.0 was measured reporting a wrong position in five distinct ways on this device, each
 * discovered only from a capture: {@code onSeeked} carrying the stale pre-seek playhead
 * (2026-09-16), {@code getPosition()} staying stale for seconds after a seek (2026-09-16),
 * {@code onSeek} echoing a spurious {@code offset=0.0} (2026-09-16 and 2026-09-19),
 * {@code getPosition()} briefly returning the seek target and then reverting (2026-09-19 14:19),
 * and a rebuffer producing a transient near-zero sample (2026-09-19 12:22). Each was previously
 * answered with its own guard, and each fix was followed by a capture exposing the next anomaly.
 *
 * That list cannot be assumed complete, so this class does not enumerate anomalies. It maintains a
 * model of where playback must be and accepts a sample only when the sample agrees with the model,
 * or when a command we issued explains the disagreement. An anomaly nobody has catalogued yet is
 * therefore rejected by default rather than becoming the next regression.
 *
 * <h3>Why it cannot lie forever</h3>
 * A model that rejects everything would be as wrong as trusting everything: an external seek, a
 * source we never observe, or a genuine SDK reset would leave the model stranded and the head unit
 * reading fiction. So rejection is BOUNDED. Consecutive rejections whose samples are coherent with
 * each other — advancing at the playback rate, i.e. reality moving sensibly somewhere the model did
 * not predict — converge on REACQUIRE after {@link #REACQUIRE_AFTER_REJECTS} of them, and the
 * sample becomes the new anchor. A single wild transient never reacquires, because the next sample
 * does not agree with it and the streak restarts. At a 1 Hz reconciliation that bounds divergence to
 * roughly {@link #REACQUIRE_AFTER_REJECTS} seconds.
 *
 * All times are caller-supplied monotonic milliseconds; this class reads no clock and no SDK.
 */
final class PositionModel {

    /** A sample within this distance of the projection is treated as agreeing with it. */
    static final long AGREEMENT_TOLERANCE_MS = 1_500L;

    /**
     * Consecutive coherent disagreeing samples required before reality overrides the model.
     * Five at the measured ~1 Hz reconciliation bounds visible divergence to about five seconds,
     * while being far more than any transient observed: the longest measured single-sample lie
     * lasted one tick, and the longest measured multi-sample lie (stale {@code getPosition()} after
     * a seek) resolved inside 1.5 s.
     */
    static final int REACQUIRE_AFTER_REJECTS = 5;

    /**
     * Two consecutive rejected samples count as coherent when the second is where the first would
     * have travelled to at the playback rate, within this tolerance. Slightly wider than
     * {@link #AGREEMENT_TOLERANCE_MS} because these samples are being compared across a full
     * reconciliation interval rather than against a continuously-updated anchor.
     */
    static final long COHERENCE_TOLERANCE_MS = 2_000L;

    /** What happened to a sample. */
    enum Verdict {
        /** No anchor yet — the sample seeds the model. */
        SEEDED,
        /** The sample agrees with the projection; the model now tracks it. */
        ACCEPTED,
        /** The sample disagrees and was discarded; the projection is authoritative. */
        REJECTED,
        /** Disagreement persisted coherently; reality wins and the sample becomes the anchor. */
        REACQUIRED,
        /** Nothing to decide (model frozen and no sample, or the sample was unusable). */
        UNAVAILABLE
    }

    static final class Decision {
        /** The position that should be published. Never negative once the model is seeded. */
        final long positionMs;
        final Verdict verdict;
        final long sampleMs;
        final long projectedMs;
        /** How far the sample sat from the projection, absolute. -1 when not applicable. */
        final long deviationMs;
        /** Consecutive coherent rejections so far, including this one. */
        final int rejectStreak;

        private Decision(Verdict verdict, long positionMs, long sampleMs, long projectedMs,
                long deviationMs, int rejectStreak) {
            this.verdict = verdict;
            this.positionMs = positionMs;
            this.sampleMs = sampleMs;
            this.projectedMs = projectedMs;
            this.deviationMs = deviationMs;
            this.rejectStreak = rejectStreak;
        }
    }

    private long anchorMs = -1L;
    private long anchorAtMs = 0L;
    private double rate = 1.0d;
    private boolean playing = false;
    private boolean seeded = false;

    private int rejectStreak = 0;
    private long lastRejectedMs = -1L;
    private long lastRejectedAtMs = 0L;
    /**
     * Set by {@link #reset()}: a track change is expected to be followed by a COMMANDED resume
     * position, so until that arrives a sample must not seed the model.
     *
     * Measured 2026-09-19 17:25 on the device: 3.3s after a track switch, {@code getPosition()} still
     * reported the PREVIOUS item's playhead (66014 ms) while the session had already reset to 0, and
     * an unconditional seed took that stale value as the new item's anchor. Seeding is the one
     * decision with no projection to check against, so it is the one place a wrong sample cannot be
     * caught later — hence this wait.
     */
    private boolean awaitingCommandedSeed = false;
    private int samplesSinceReset = 0;

    boolean isSeeded() {
        return seeded;
    }

    long anchorPositionMs() {
        return anchorMs;
    }

    int currentRejectStreak() {
        return rejectStreak;
    }

    /** Where playback must be now: the anchor plus elapsed time at rate, frozen when not playing. */
    long projectedMs(long nowMs) {
        if (!seeded) {
            return -1L;
        }
        if (!playing) {
            return anchorMs;
        }
        long elapsed = Math.max(0L, nowMs - anchorAtMs);
        return anchorMs + (long) (elapsed * rate);
    }

    /**
     * A position we COMMANDED: a seek we dispatched, a track change's resume point, an explicit
     * start. This is the one input that is authoritative by construction, so it re-anchors the model
     * outright and clears any rejection streak — the disagreement it creates is explained.
     */
    void onCommandedPosition(long positionMs, long nowMs) {
        if (positionMs < 0) {
            return;
        }
        anchorMs = positionMs;
        anchorAtMs = nowMs;
        seeded = true;
        awaitingCommandedSeed = false;
        samplesSinceReset = 0;
        clearRejectStreak();
    }

    /**
     * Playback started or resumed: the model begins advancing from where it was frozen.
     *
     * Deliberately does NOT clear the rejection streak, and is a no-op when already playing.
     * Measured 2026-09-19 18:08: the helper syncs this state before EVERY observe, so clearing the
     * streak here reset it on every tick and reacquisition could never reach its bound — the model
     * published a 23-minute fiction indefinitely with `streak=1/5` on every line. A pause or resume
     * also does not make a disagreeing sample agree: the anchor is carried across both transitions,
     * so a disagreement survives them. Only a COMMAND, a reset, or an agreeing sample clears it.
     */
    void onPlaying(long nowMs) {
        if (playing) {
            return;
        }
        if (seeded) {
            anchorAtMs = nowMs;
        }
        playing = true;
    }

    /** Playback paused, buffered or stopped: freeze the model at its current projection. */
    void onNotPlaying(long nowMs) {
        if (!playing) {
            return;
        }
        if (seeded) {
            anchorMs = projectedMs(nowMs);
            anchorAtMs = nowMs;
        }
        playing = false;
    }

    /** Playback rate changed; the projection must advance at the new rate from here. */
    void onRateChanged(double newRate, long nowMs) {
        if (newRate <= 0d || Double.isNaN(newRate) || Double.isInfinite(newRate)) {
            return;
        }
        if (seeded && playing) {
            anchorMs = projectedMs(nowMs);
            anchorAtMs = nowMs;
        }
        rate = newRate;
    }

    /** A track change invalidates the model entirely; nothing about the old item carries over. */
    void reset() {
        anchorMs = -1L;
        anchorAtMs = 0L;
        playing = false;
        seeded = false;
        rate = 1.0d;
        awaitingCommandedSeed = true;
        samplesSinceReset = 0;
        clearRejectStreak();
    }

    /**
     * Judge one position sample from the SDK.
     *
     * @param sampleMs the SDK-reported position, or negative when unavailable
     * @param nowMs    monotonic now
     */
    Decision observe(long sampleMs, long nowMs) {
        if (sampleMs < 0) {
            // An unavailable reading must never become an anchor. Measured 2026-09-17: a -1s
            // reading published as a valid -1000ms position.
            long projected = projectedMs(nowMs);
            return new Decision(Verdict.UNAVAILABLE, projected, sampleMs, projected, -1L, rejectStreak);
        }
        if (!seeded) {
            if (awaitingCommandedSeed && samplesSinceReset < REACQUIRE_AFTER_REJECTS) {
                // A track change is expected to deliver a commanded resume position. Until it does,
                // refuse to seed: the sample here may still be the PREVIOUS item's playhead. Bounded
                // by the same convergence budget as reacquisition, so a track change that never
                // delivers a command still seeds rather than stalling forever.
                samplesSinceReset++;
                return new Decision(Verdict.UNAVAILABLE, -1L, sampleMs, -1L, -1L, rejectStreak);
            }
            anchorMs = sampleMs;
            anchorAtMs = nowMs;
            seeded = true;
            awaitingCommandedSeed = false;
            samplesSinceReset = 0;
            clearRejectStreak();
            return new Decision(Verdict.SEEDED, sampleMs, sampleMs, sampleMs, 0L, 0);
        }

        long projected = projectedMs(nowMs);
        long deviation = Math.abs(sampleMs - projected);

        if (deviation <= AGREEMENT_TOLERANCE_MS) {
            // Reality agrees. Track it exactly rather than keeping a drifting projection, so the
            // model never accumulates error against the real playhead.
            anchorMs = sampleMs;
            anchorAtMs = nowMs;
            clearRejectStreak();
            return new Decision(Verdict.ACCEPTED, sampleMs, sampleMs, projected, deviation, 0);
        }

        // Disagreement. Is it coherent with the PREVIOUS disagreement — i.e. is reality moving
        // sensibly somewhere the model did not predict, rather than emitting one wild value?
        boolean coherent = false;
        if (rejectStreak > 0 && lastRejectedMs >= 0) {
            long expectedFromLastReject = lastRejectedMs
                    + (long) (Math.max(0L, nowMs - lastRejectedAtMs) * (playing ? rate : 0d));
            coherent = Math.abs(sampleMs - expectedFromLastReject) <= COHERENCE_TOLERANCE_MS;
        }

        rejectStreak = coherent ? rejectStreak + 1 : 1;
        lastRejectedMs = sampleMs;
        lastRejectedAtMs = nowMs;

        if (rejectStreak >= REACQUIRE_AFTER_REJECTS) {
            // Bounded reacquisition: reality has disagreed coherently for long enough that the model
            // is the thing that is wrong. Adopt it.
            anchorMs = sampleMs;
            anchorAtMs = nowMs;
            int streak = rejectStreak;
            clearRejectStreak();
            return new Decision(Verdict.REACQUIRED, sampleMs, sampleMs, projected, deviation, streak);
        }

        return new Decision(Verdict.REJECTED, projected, sampleMs, projected, deviation, rejectStreak);
    }

    private void clearRejectStreak() {
        rejectStreak = 0;
        lastRejectedMs = -1L;
        lastRejectedAtMs = 0L;
    }
}
