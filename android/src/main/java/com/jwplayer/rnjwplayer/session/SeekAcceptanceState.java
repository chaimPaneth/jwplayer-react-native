package com.jwplayer.rnjwplayer.session;

/**
 * Protects a requested seek until a completion callback and a settled live observation agree.
 * Completion alone is not position validation. Callers supply monotonic milliseconds.
 */
final class SeekAcceptanceState {

    /** Tolerance for treating two positions as the same. Was a private constant on the helper. */
    static final long SEEKED_TARGET_AGREEMENT_MS = 3_000L;
    /** Backstop age after which the target is held but NOT released. */
    static final long SEEK_TARGET_TRUST_WINDOW_MS = 15_000L;
    /** Window during which a disagreeing non-authoritative echo is refused. */
    static final long SEEK_TARGET_ECHO_GUARD_MS = 2_000L;
    /** Minimum age before a settled-PLAYING re-issue is attempted for a never-confirmed seek. */
    static final long PLAYING_CORRECTION_MIN_AGE_MS = 3_000L;
    /**
     * Age at which a target that NEVER received a completion callback is abandoned and publishing
     * follows the live player again. Deliberately well past {@link #SEEK_TARGET_TRUST_WINDOW_MS} so
     * the settled-PLAYING re-issue (see {@code canClaimPlayingCorrection}) has time to land first.
     * Only applies when {@code seekCompletionAttributed} is false — a target whose completion WAS
     * corroborated but still disagrees with a stale live sample (measured JW 4.26.0 bug) is held
     * indefinitely, unchanged.
     */
    static final long ABANDON_UNCONFIRMED_MS = SEEK_TARGET_TRUST_WINDOW_MS + 10_000L;

    /** Receives the clear event so the caller can emit its existing SEEKTRACE diagnostic. */
    interface ClearListener {
        void onCleared(String reason, long clearedTargetMs, long ageMs);
    }

    private long seekTargetInFlightMs = -1L;
    private long seekTargetInFlightAtMs = 0L;
    /**
     * A completion callback arrived for the in-flight target, whatever position it reported.
     * Under JW 4.26.0 that position is routinely wrong, so this says only "the SDK answered".
     */
    private boolean seekCompletionSeen;
    /**
     * A completion callback arrived whose position actually CORROBORATES the in-flight target.
     *
     * Measured 2026-09-18 (capture logcat_android17_2026-09-18_17-29-06): a swallowed AA seek to
     * 1806529ms received an unrelated {@code onSeeked(position=0.0)} — the new item finishing its
     * load — 2.2s later. Crediting that as this command completing disabled BOTH recovery paths
     * ({@code canClaimPlayingCorrection} and the ABANDONED release are gated on the absence of
     * completion), so the lost target was published as truth for 16s while playback ran from 0.
     * Attribution is what separates the two: 0ms is not 1806529ms completing.
     */
    private boolean seekCompletionAttributed;
    private boolean pausedCorrectionIssued;
    private boolean playingCorrectionIssued;

    long targetInFlightMs() {
        return seekTargetInFlightMs;
    }

    long targetArmedAtMs() {
        return seekTargetInFlightAtMs;
    }

    /** Outcome of an arm attempt. Mirrors the original method's accept/refuse branches. */
    static final class ArmOutcome {
        final boolean accepted;
        final long targetMs;
        final long keptTargetMs;
        final long ageMs;

        private ArmOutcome(boolean accepted, long targetMs, long keptTargetMs, long ageMs) {
            this.accepted = accepted;
            this.targetMs = targetMs;
            this.keptTargetMs = keptTargetMs;
            this.ageMs = ageMs;
        }
    }

    /**
     * An agreeing echo acknowledges the existing request without changing its target or age.
     *
     * An echo may NEVER CREATE a target. Measured 2026-09-19 (capture
     * logcat_android17_2026-09-19_11-18-12): on returning to the app after a background track
     * change, the JS restore seek to 226.0s was echoed by JW as
     * {@code onSeek(position=226.0, offset=0.0)} — a spurious ZERO destination. No target was in
     * flight at that moment (the track switch had cleared it), so the echo guard below was skipped
     * entirely and 0 became the authoritative session position: the correct live 226000ms was then
     * discarded as a "stale playhead", 0 was published, and 0 was written into item 49085's resume
     * cache, which is what restarted the media at 0:00. Only {@code performSeekTo} — the one path
     * carrying a real requested position — may arm.
     */
    ArmOutcome arm(long targetMs, boolean authoritative, long nowMs) {
        if (targetMs < 0) {
            return new ArmOutcome(false, targetMs, seekTargetInFlightMs, -1L);
        }
        long existingMs = seekTargetInFlightMs;
        if (!authoritative) {
            if (existingMs < 0) {
                return new ArmOutcome(false, targetMs, existingMs, -1L);
            }
            long ageMs = Math.max(0L, nowMs - seekTargetInFlightAtMs);
            if (Math.abs(targetMs - existingMs) <= SEEKED_TARGET_AGREEMENT_MS) {
                return new ArmOutcome(true, existingMs, existingMs, ageMs);
            }
            if (ageMs <= SEEK_TARGET_ECHO_GUARD_MS
                    && Math.abs(targetMs - existingMs) > SEEKED_TARGET_AGREEMENT_MS) {
                return new ArmOutcome(false, targetMs, existingMs, ageMs);
            }
        }
        seekTargetInFlightMs = targetMs;
        seekTargetInFlightAtMs = nowMs;
        seekCompletionSeen = false;
        seekCompletionAttributed = false;
        pausedCorrectionIssued = false;
        playingCorrectionIssued = false;
        return new ArmOutcome(true, targetMs, targetMs, 0L);
    }

    boolean claimPausedCorrection(long eventPositionMs, boolean paused) {
        if (!paused || !seekCompletionSeen || pausedCorrectionIssued
            || seekTargetInFlightMs < 0L || eventPositionMs < 0L
                || Math.abs(eventPositionMs - seekTargetInFlightMs) <= SEEKED_TARGET_AGREEMENT_MS) {
            return false;
        }
        pausedCorrectionIssued = true;
        return true;
    }

    /**
     * Pure query (no mutation) so a high-frequency caller like {@code onTime} can check every tick
     * without posting redundant work. Allowed only for a target that never received a completion
     * callback at all — a swallowed seek, not the stale-JW-report case {@code resolvePublish}
     * already handles by holding.
     */
    boolean canClaimPlayingCorrection(long liveMs, long nowMs) {
        if (playingCorrectionIssued || seekCompletionAttributed
                || seekTargetInFlightMs < 0L || liveMs < 0L) {
            return false;
        }
        long ageMs = Math.max(0L, nowMs - seekTargetInFlightAtMs);
        return ageMs > PLAYING_CORRECTION_MIN_AGE_MS
                && Math.abs(liveMs - seekTargetInFlightMs) > SEEKED_TARGET_AGREEMENT_MS;
    }

    /** Bounded, one-shot re-issue for a target that never received a completion callback. */
    boolean claimPlayingCorrection(long liveMs, long nowMs) {
        if (!canClaimPlayingCorrection(liveMs, nowMs)) {
            return false;
        }
        playingCorrectionIssued = true;
        return true;
    }

    /** Which branch of the publish resolver produced the answer. */
    enum PublishOutcome {
        NO_TARGET,
        CLEARED_LIVE_CAUGHT_UP,
        HELD_PAST_TRUST_WINDOW,
        ABANDONED,
        PROJECTED
    }

    static final class PublishDecision {
        final PublishOutcome outcome;
        final long positionMs;
        final long liveMs;
        final long projectedMs;
        final long targetMs;
        final long ageMs;

        private PublishDecision(PublishOutcome outcome, long positionMs, long liveMs,
                long projectedMs, long targetMs, long ageMs) {
            this.outcome = outcome;
            this.positionMs = positionMs;
            this.liveMs = liveMs;
            this.projectedMs = projectedMs;
            this.targetMs = targetMs;
            this.ageMs = ageMs;
        }

        boolean releasedProtection() {
            return outcome == PublishOutcome.CLEARED_LIVE_CAUGHT_UP;
        }
    }

    PublishDecision resolvePublish(long liveMs, boolean playing, boolean settled,
            long nowMs, ClearListener listener) {
        long targetMs = seekTargetInFlightMs;
        if (targetMs < 0) {
            long positionMs = liveMs >= 0 ? liveMs : -1L;
            return new PublishDecision(
                    PublishOutcome.NO_TARGET, positionMs, liveMs, positionMs, targetMs, -1L);
        }

        long ageMs = Math.max(0L, nowMs - seekTargetInFlightAtMs);
        long projectedMs = targetMs;

        // The live player REACHING the target is itself proof the seek landed, so this no longer
        // requires a completion callback. It must not: under JW 4.26.0 the callback routinely
        // reports the stale pre-seek playhead (measured: onSeeked(17180ms) for a seek to
        // 1781695ms), so requiring attributed completion here would leave a seek that demonstrably
        // SUCCEEDED held until the abandon backstop, freezing Android Auto for 25s.
        if (settled && liveMs >= 0
            && Math.abs(liveMs - projectedMs) <= SEEKED_TARGET_AGREEMENT_MS) {
            String reason = "live-caught-up live=" + liveMs
                    + "ms projected=" + projectedMs + "ms age=" + ageMs + "ms";
            clear(reason, ageMs, listener);
            return new PublishDecision(
                    PublishOutcome.CLEARED_LIVE_CAUGHT_UP, liveMs, liveMs, projectedMs, targetMs, ageMs);
        }

        if (ageMs > SEEK_TARGET_TRUST_WINDOW_MS) {
            if (!seekCompletionAttributed && ageMs > ABANDON_UNCONFIRMED_MS) {
                String reason = "abandoned-unconfirmed live=" + liveMs + "ms target=" + projectedMs
                        + "ms age=" + ageMs + "ms";
                long releasedPositionMs = liveMs >= 0 ? liveMs : -1L;
                clear(reason, ageMs, listener);
                return new PublishDecision(PublishOutcome.ABANDONED, releasedPositionMs, liveMs,
                        projectedMs, targetMs, ageMs);
            }
            return new PublishDecision(
                    PublishOutcome.HELD_PAST_TRUST_WINDOW, projectedMs, liveMs, projectedMs, targetMs, ageMs);
        }

        return new PublishDecision(
                PublishOutcome.PROJECTED, projectedMs, liveMs, projectedMs, targetMs, ageMs);
    }

    static final class SeekedDecision {
        final long positionMs;
        final long requestedMs;
        final boolean substitutedRequest;

        private SeekedDecision(long positionMs, long requestedMs, boolean substitutedRequest) {
            this.positionMs = positionMs;
            this.requestedMs = requestedMs;
            this.substitutedRequest = substitutedRequest;
        }
    }

    SeekedDecision resolveSeeked(long eventPositionMs, long lastRequestedMs) {
        if (seekTargetInFlightMs >= 0 && eventPositionMs >= 0) {
            seekCompletionSeen = true;
            // Only a callback that lands ON the target proves THIS command completed. A callback
            // reporting somewhere else is either JW 4.26.0's stale pre-seek playhead or an
            // unrelated item-load completion; both must leave the target unconfirmed so the
            // re-issue and abandon paths stay reachable.
            if (Math.abs(eventPositionMs - seekTargetInFlightMs) <= SEEKED_TARGET_AGREEMENT_MS) {
                seekCompletionAttributed = true;
            }
        }
        long requestedMs = seekTargetInFlightMs >= 0 ? seekTargetInFlightMs : lastRequestedMs;
        if (eventPositionMs < 0) {
            return new SeekedDecision(requestedMs, requestedMs, false);
        }
        if (requestedMs < 0
                || Math.abs(eventPositionMs - requestedMs) <= SEEKED_TARGET_AGREEMENT_MS) {
            return new SeekedDecision(eventPositionMs, requestedMs, false);
        }
        if (Math.abs(eventPositionMs - requestedMs) > SEEKED_TARGET_AGREEMENT_MS) {
            return new SeekedDecision(requestedMs, requestedMs, true);
        }
        return new SeekedDecision(eventPositionMs, requestedMs, false);
    }

    boolean blocksStore(long positionMs, long nowMs) {
        return positionMs < 0 || seekTargetInFlightMs >= 0
            && Math.abs(positionMs - seekTargetInFlightMs) > SEEKED_TARGET_AGREEMENT_MS;
    }

    void clear(String reason, long ageMs, ClearListener listener) {
        long clearedTargetMs = seekTargetInFlightMs;
        seekTargetInFlightMs = -1L;
        seekTargetInFlightAtMs = 0L;
        seekCompletionSeen = false;
        seekCompletionAttributed = false;
        pausedCorrectionIssued = false;
        playingCorrectionIssued = false;
        if (listener != null) {
            listener.onCleared(reason, clearedTargetMs, ageMs);
        }
    }
}
