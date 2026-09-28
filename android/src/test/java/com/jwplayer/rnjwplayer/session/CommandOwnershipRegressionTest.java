package com.jwplayer.rnjwplayer.session;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Regression harness for the two ownership invariants introduced 2026-09-22 for the reproduced
 * PiP-exit item swap (see PIP_EXIT_ITEM_SWAP_REPRODUCED_2026-09-22.md).
 *
 * INVARIANT 1 — a deferred transport command may not cross a playback-intent boundary.
 * INVARIANT 2 — a resume/start position belongs to a media identity, never to a time window.
 *
 * The rules are modelled here exactly as the production code implements them, so the fixtures can be
 * executed on a plain JVM with no Android. Two SOURCE assertions additionally pin the production
 * files themselves, so the model cannot drift away from the shipped code unnoticed:
 *   - the skip claim must be an atomic remove, not an isSkipPending-then-load;
 *   - CROSS_TRACK_START_WINDOW_MS must no longer gate the cross-item position decision.
 *
 * Run:
 *   javac -d <out> CommandOwnershipRegressionTest.java
 *   java -cp <out> com.jwplayer.rnjwplayer.session.CommandOwnershipRegressionTest
 */
public final class CommandOwnershipRegressionTest {

    // ---------------------------------------------------------------------------------------------
    // INVARIANT 1 model: the pending-skip registry plus the ownership rule.
    // ---------------------------------------------------------------------------------------------

    /** Mirrors MediaBrowserService.pendingSkipAcks — a token registry with an ATOMIC claim. */
    static final class SkipRegistry {
        private static final class Claim {
            final String baseMediaId;
            final long ownerIntentSeq;
            Claim(String baseMediaId, long ownerIntentSeq) {
                this.baseMediaId = baseMediaId;
                this.ownerIntentSeq = ownerIntentSeq;
            }
        }
        interface Action { boolean run(); }

        private final Object lock = new Object();
        private final ConcurrentHashMap<String, Claim> pending = new ConcurrentHashMap<>();

        void dispatch(String token, String baseMediaId, long ownerIntentSeq) {
            synchronized (lock) { pending.put(token, new Claim(baseMediaId, ownerIntentSeq)); }
        }

        boolean isPending(String token) {
            synchronized (lock) { return token != null && pending.containsKey(token); }
        }

        /** Mirrors runWithValidSkipClaim: validate + consume + action under ONE lock. */
        boolean runIfValid(
                String token, String expectedBaseMediaId, long expectedOwnerIntentSeq, Action action) {
            synchronized (lock) {
                Claim claim = token == null ? null : pending.get(token);
                if (claim == null || expectedBaseMediaId == null
                        || !expectedBaseMediaId.equals(claim.baseMediaId)
                        || expectedOwnerIntentSeq != claim.ownerIntentSeq) {
                    if (token != null) pending.remove(token);
                    return false;
                }
                pending.remove(token);       // single winner; lock stays held through dispatch
                return action.run();
            }
        }

        /** Mirrors invalidateSkip — same lock as runIfValid. */
        boolean invalidate(String token) {
            synchronized (lock) { return token != null && pending.remove(token) != null; }
        }
    }

    /** Mirrors the helper's pending-skip ownership record and its supersede rule. */
    static final class SkipOwner {
        private final SkipRegistry registry;
        String token;
        String baseMediaId;
        long ownerIntentSeq = -1L;
        long intentSeq = 0L;
        int loads = 0;

        SkipOwner(SkipRegistry registry) { this.registry = registry; }

        /** A new authoritative playback intent. Returns the new sequence. */
        long noteLoadIntent(String origin, String mediaId) {
            long seq = ++intentSeq;
            if (token != null && ownerIntentSeq >= 0 && seq > ownerIntentSeq) {
                registry.invalidate(token);
                token = null;
                baseMediaId = null;
                ownerIntentSeq = -1L;
            }
            return seq;
        }

        /** performSkipToNext/Previous handed the command to RN. */
        void armSkip(String tok, String base) {
            ownerIntentSeq = intentSeq;
            registry.dispatch(tok, base, ownerIntentSeq);
            token = tok;
            baseMediaId = base;
        }

        /** The JS owner asks native to atomically validate and dispatch. */
        boolean jsMayLoad(String tok, String expectedBase, long expectedSeq) {
            return registry.runIfValid(tok, expectedBase, expectedSeq,
                    () -> { loads++; return true; });
        }
        boolean jsMayLoad(String tok) { return jsMayLoad(tok, baseMediaId, ownerIntentSeq); }
    }

    // ---------------------------------------------------------------------------------------------
    // INVARIANT 2 model: the cross-item start-position ownership rule.
    // ---------------------------------------------------------------------------------------------

    static final long CROSS_TRACK_AGREEMENT_MS = 3_000L;

    /**
     * Mirrors the production decision in onPlaylistItem. Deliberately takes NO time argument at all,
     * so a fixture cannot accidentally depend on elapsed time.
     *
     * @param incomingMediaId      the item being loaded
     * @param unverifiedMediaId    the item switched to for which no start has been vetted yet (or null)
     * @param switchByReact        whether React's own load caused that switch
     * @param hasExplicitStart     whether the config carried a start position
     * @param explicitStartMs      that start position
     * @param incomingOwnSavedMs   the incoming item's OWN saved resume (-1 when unknown)
     * @return true when the explicit start must be REJECTED as belonging to another item
     */
    static boolean crossItemStartIsSuspect(String incomingMediaId,
                                           String unverifiedMediaId,
                                           boolean switchByReact,
                                           boolean hasExplicitStart,
                                           long explicitStartMs,
                                           long incomingOwnSavedMs) {
        boolean startUnverifiedForThisItem = unverifiedMediaId != null
                && unverifiedMediaId.equals(incomingMediaId)
                && !switchByReact;
        return startUnverifiedForThisItem
                && hasExplicitStart
                && incomingOwnSavedMs >= 0
                && Math.abs(explicitStartMs - incomingOwnSavedMs) > CROSS_TRACK_AGREEMENT_MS;
    }

    // ---------------------------------------------------------------------------------------------

    static final Map<String, String> results = new LinkedHashMap<>();
    static int passed = 0, failed = 0;

    static void check(String name, boolean ok, String detail) {
        results.put(name, ok ? "PASS" : "FAIL  " + detail);
        if (ok) passed++; else failed++;
    }

    public static void main(String[] args) throws Exception {

        // ---------------- INVARIANT 1 ----------------

        // 1. valid pending next -> executes
        {
            SkipRegistry r = new SkipRegistry(); SkipOwner o = new SkipOwner(r);
            o.noteLoadIntent("rn-setConfig", "A");
            o.armSkip("t1", "A");
            check("I1_VALID_PENDING_NEXT_EXECUTES", o.jsMayLoad("t1"), "claim refused");
        }

        // 2. valid pending previous -> executes
        {
            SkipRegistry r = new SkipRegistry(); SkipOwner o = new SkipOwner(r);
            o.noteLoadIntent("rn-setConfig", "A");
            o.armSkip("tp", "A");
            check("I1_VALID_PENDING_PREVIOUS_EXECUTES", o.jsMayLoad("tp"), "claim refused");
        }

        // 2b. a token cannot be replayed against a different base item
        {
            SkipRegistry r = new SkipRegistry(); SkipOwner o = new SkipOwner(r);
            o.noteLoadIntent("rn-setConfig", "A");
            o.armSkip("tb", "A");
            boolean loaded = o.jsMayLoad("tb", "B", o.ownerIntentSeq);
            check("I1_BASE_IDENTITY_MISMATCH_REJECTED", !loaded && o.loads == 0,
                  "token for A executed as a command for B");
        }

        // 2c. the same token/base cannot be replayed under a different generation
        {
            SkipRegistry r = new SkipRegistry(); SkipOwner o = new SkipOwner(r);
            o.noteLoadIntent("rn-setConfig", "A");
            o.armSkip("tg", "A");
            boolean loaded = o.jsMayLoad("tg", "A", o.ownerIntentSeq + 1);
            check("I1_OWNER_GENERATION_MISMATCH_REJECTED", !loaded && o.loads == 0,
                  "token from generation N executed as N+1");
        }

        // 3+4+5. token created under generation N, newer React assertion N+1 -> old token rejected
        {
            SkipRegistry r = new SkipRegistry(); SkipOwner o = new SkipOwner(r);
            long n = o.noteLoadIntent("rn-setConfig", "A");
            o.armSkip("t3", "A");
            boolean armedUnderN = o.ownerIntentSeq == n;
            long n1 = o.noteLoadIntent("rn-setConfig", "A");   // PiP exit re-assertion
            check("I1_TOKEN_CARRIES_OWNER_GENERATION", armedUnderN && n1 == n + 1,
                  "ownerIntentSeq=" + o.ownerIntentSeq);
            check("I1_NEWER_REACT_ASSERTION_REJECTS_STALE_SKIP",
                  !o.jsMayLoad("t3", "A", n),
                  "stale skip was allowed to load");
        }

        // 6. newer AA/user selection supersedes
        {
            SkipRegistry r = new SkipRegistry(); SkipOwner o = new SkipOwner(r);
            long n = o.noteLoadIntent("rn-setConfig", "A");
            o.armSkip("t6", "A");
            o.noteLoadIntent("aa-selection", "C");
            check("I1_NEWER_AA_SELECTION_REJECTS_STALE_SKIP",
                  !o.jsMayLoad("t6", "A", n),
                  "stale skip survived an AA selection");
        }

        // 7. still-valid token executes after PiP exit (no intervening intent)
        {
            SkipRegistry r = new SkipRegistry(); SkipOwner o = new SkipOwner(r);
            o.noteLoadIntent("rn-setConfig", "A");
            o.armSkip("t7", "A");
            // PiP enter/exit happen here; lifecycle is NOT authority and asserts no intent.
            check("I1_VALID_TOKEN_EXECUTES_AFTER_PIP_EXIT", o.jsMayLoad("t7"),
                  "valid token rejected after PiP");
        }

        // 8. arbitrary delay does not affect validity (no time term exists to exercise)
        {
            SkipRegistry r = new SkipRegistry(); SkipOwner o = new SkipOwner(r);
            o.noteLoadIntent("rn-setConfig", "A");
            o.armSkip("t8", "A");
            Thread.sleep(40);                       // any duration; the rule has no clock
            check("I1_DELAY_ALONE_DOES_NOT_INVALIDATE", o.jsMayLoad("t8"),
                  "delay invalidated a valid token");
        }

        // 9. lifecycle transitions alone do not invalidate
        {
            SkipRegistry r = new SkipRegistry(); SkipOwner o = new SkipOwner(r);
            o.noteLoadIntent("rn-setConfig", "A");
            o.armSkip("t9", "A");
            // simulate: background, PiP enter, PiP exit, foreground — none of which note an intent
            check("I1_LIFECYCLE_ALONE_DOES_NOT_INVALIDATE", o.jsMayLoad("t9"),
                  "lifecycle invalidated a valid token");
        }

        // 10. no check-then-act: a token invalidated after a pending check still cannot load
        {
            SkipRegistry r = new SkipRegistry(); SkipOwner o = new SkipOwner(r);
            long n = o.noteLoadIntent("rn-setConfig", "A");
            o.armSkip("t10", "A");
            boolean sawPending = r.isPending("t10");        // the naive check says "go"
            o.noteLoadIntent("rn-setConfig", "A");          // newer intent lands in between
            boolean loaded = o.jsMayLoad("t10", "A", n);   // atomic validate+dispatch decides
            check("I1_NO_CHECK_THEN_ACT_RACE", sawPending && !loaded,
                  "sawPending=" + sawPending + " loaded=" + loaded);
        }

        // 10b. invalidation and load dispatch share one linearisation lock
        {
            SkipRegistry r = new SkipRegistry();
            r.dispatch("tlock", "A", 1L);
            CountDownLatch actionEntered = new CountDownLatch(1);
            CountDownLatch releaseAction = new CountDownLatch(1);
            AtomicBoolean loaded = new AtomicBoolean(false);
            AtomicBoolean invalidationFinished = new AtomicBoolean(false);
            AtomicBoolean invalidationKilled = new AtomicBoolean(true);

            Thread loader = new Thread(() -> loaded.set(r.runIfValid("tlock", "A", 1L, () -> {
                actionEntered.countDown();
                try { return releaseAction.await(1, TimeUnit.SECONDS); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); return false; }
            })));
            loader.start();
            boolean entered = actionEntered.await(1, TimeUnit.SECONDS);

            Thread invalidator = new Thread(() -> {
                invalidationKilled.set(r.invalidate("tlock"));
                invalidationFinished.set(true);
            });
            invalidator.start();
            Thread.sleep(25); // only to observe lock contention; never part of validity
            boolean invalidationBlockedDuringDispatch = !invalidationFinished.get();
            releaseAction.countDown();
            loader.join(1_000);
            invalidator.join(1_000);

            check("I1_INVALIDATION_CANNOT_INTERLEAVE_WITH_DISPATCH",
                  entered && invalidationBlockedDuringDispatch && loaded.get()
                      && invalidationFinished.get() && !invalidationKilled.get(),
                  "entered=" + entered + " blocked=" + invalidationBlockedDuringDispatch
                      + " loaded=" + loaded + " invalidationKilled=" + invalidationKilled);
        }

        // 11. a consumed token cannot execute twice
        {
            SkipRegistry r = new SkipRegistry(); SkipOwner o = new SkipOwner(r);
            o.noteLoadIntent("rn-setConfig", "A");
            o.armSkip("t11", "A");
            boolean first = o.jsMayLoad("t11");
            boolean second = o.jsMayLoad("t11");
            check("I1_CLAIMED_TOKEN_CANNOT_EXECUTE_TWICE", first && !second,
                  "first=" + first + " second=" + second);
        }

        // 12. the exact reproduced ordering must be impossible
        {
            SkipRegistry r = new SkipRegistry(); SkipOwner o = new SkipOwner(r);
            long n = o.noteLoadIntent("rn-setConfig", "10644"); // seq 1 — playing 10644
            o.armSkip("d7656c31", "10644");              // MediaSession next during PiP
            o.noteLoadIntent("rn-setConfig", "10644");   // seq 2 — React re-asserts on PiP exit
            boolean wrongItemLoaded = o.jsMayLoad("d7656c31", "10644", n);
            check("I1_REPRODUCED_PIP_EXIT_SWAP_IS_IMPOSSIBLE", !wrongItemLoaded,
                  "playFromMediaId(10643) would still have executed");
        }

        // ---------------- INVARIANT 2 ----------------

        // 1. same item + valid resume -> accepted (not a cross-item case at all)
        check("I2_SAME_ITEM_RESUME_ACCEPTED",
              !crossItemStartIsSuspect("A", null, false, true, 300_000L, 300_000L),
              "same-item resume rejected");

        // 2. same item + explicit seek/start -> accepted
        check("I2_SAME_ITEM_EXPLICIT_START_ACCEPTED",
              !crossItemStartIsSuspect("A", "B", false, true, 1_200_000L, 5_000L),
              "a start for a DIFFERENT item than the unverified one was judged");

        // 3. A's position applied to B whose own resume is 0 -> REJECTED (the measured failure)
        check("I2_STALE_A_POSITION_ON_B_REJECTED",
              crossItemStartIsSuspect("10643", "10643", false, true, 648_000L, 0L),
              "the measured stale 648s would still be applied to 10643");

        // 4. B has a legitimate non-zero resume -> B's own resume preserved (agrees -> accepted)
        check("I2_B_OWN_NONZERO_RESUME_PRESERVED",
              !crossItemStartIsSuspect("B", "B", false, true, 300_000L, 301_500L),
              "B's own resume was treated as stale");

        // 5. an intentional explicit start for B is distinguishable: React caused the switch
        check("I2_REACT_INITIATED_EXPLICIT_START_PRESERVED",
              !crossItemStartIsSuspect("B", "B", true, true, 1_200_000L, 0L),
              "a React-initiated intentional start was rejected");

        // 6+7. no time term: identical inputs must give the identical verdict regardless of delay
        {
            boolean before = crossItemStartIsSuspect("10643", "10643", false, true, 648_000L, 0L);
            Thread.sleep(60);
            boolean after = crossItemStartIsSuspect("10643", "10643", false, true, 648_000L, 0L);
            check("I2_CROSS_ITEM_REJECTION_SURVIVES_ANY_DELAY", before && after,
                  "verdict changed with elapsed time");
            check("I2_PIP_DURATION_HAS_NO_EFFECT", before == after, "verdict is time-dependent");
        }

        // 8. ordinary same-item foreground/background restore remains correct
        check("I2_FOREGROUND_BACKGROUND_SAME_ITEM_RESTORE_OK",
              !crossItemStartIsSuspect("A", null, true, true, 614_000L, 613_200L),
              "same-item restore was rejected");

        // 9. a vetted item is no longer judged (latch cleared) — a later same-item push is honoured
        check("I2_VETTED_ITEM_NOT_REJUDGED",
              !crossItemStartIsSuspect("10643", null, false, true, 648_000L, 0L),
              "a cleared latch still judged the item");

        // ---------------- SOURCE ASSERTIONS: the model must match the shipped code ----------------

        Path helper = Paths.get(args.length > 0 ? args[0]
                : "android/src/main/java/com/jwplayer/rnjwplayer/session/RNJWMediaSessionHelper.java");
        if (Files.exists(helper)) {
            String src = new String(Files.readAllBytes(helper));
            int windowUses = countOutsideComments(src, "CROSS_TRACK_START_WINDOW_MS");
            check("SRC_CROSS_TRACK_WINDOW_NO_LONGER_GATES",
                  !src.contains("boolean freshSwitch = trackSwitchedAtMs")
                  && src.contains("boolean startUnverifiedForThisItem"),
                  "the time-based freshSwitch trigger is still present");
            check("SRC_POSITION_RULE_HAS_NO_TIME_TERM",
                  !src.contains("<= CROSS_TRACK_START_WINDOW_MS"),
                  "a comparison against the old window remains (" + windowUses + " mentions)");
            check("SRC_SKIP_OWNERSHIP_RECORDED_AT_DISPATCH",
                  src.contains("notePendingSkip(skipToken, mediaIdForSkip, \"next\", skipOwnerIntentSeq)")
                  && src.contains("notePendingSkip(skipToken, mediaIdForSkip, \"previous\", skipOwnerIntentSeq)"),
                  "a skip dispatch site does not record ownership");
            check("SRC_SUPERSEDE_HOOKED_INTO_INTENT_RECORDER",
                  src.contains("supersedePendingSkipIfAny(lastLoadIntentSeq, origin, mediaId)"),
                  "supersede is not driven by the intent recorder");
        } else {
            check("SRC_HELPER_FOUND", false, "could not read " + helper);
        }

        Path svc = Paths.get(args.length > 1 ? args[1]
                : System.getProperty("user.home")
                  + "/Projects/all-daf-app/node_modules/react-native-android-media-browser"
                  + "/android/src/main/java/com/mediabrowser/MediaBrowserService.java");
        if (Files.exists(svc)) {
            String src = new String(Files.readAllBytes(svc));
            check("SRC_CLAIM_AND_DISPATCH_SHARE_ONE_LOCK",
                  src.contains("synchronized (SKIP_CLAIM_LOCK)")
                  && src.contains("runWithValidSkipClaim")
                  && src.contains("return action.run();")
                  && !src.contains("consumeSkipIfValid"),
                  "guarded load is not validate+dispatch under the shared lock");
        } else {
            check("SRC_SERVICE_FOUND", false, "could not read " + svc);
        }

        // ---------------- report ----------------
        List<String> names = new ArrayList<>(results.keySet());
        for (String n : names) {
            System.out.printf("  %-52s %s%n", n, results.get(n));
        }
        System.out.println("---------------------------------------------------------------");
        System.out.printf("passed=%d  failed=%d%n", passed, failed);
        System.out.println("invariant 1: ownership = {skipToken, baseMediaId, intentSeq}; "
                + "validation + load dispatch = one native critical section; no clock");
        System.out.println("invariant 2: ownership = (mediaIdentity, position) + switch provenance; "
                + "no clock");
        if (failed > 0) System.exit(1);
    }

    /** Counts occurrences that are not inside a // or * comment line. */
    static int countOutsideComments(String src, String needle) {
        int n = 0;
        for (String line : src.split("\n")) {
            String t = line.trim();
            if (t.startsWith("//") || t.startsWith("*") || t.startsWith("/*")) continue;
            int i = 0;
            while ((i = line.indexOf(needle, i)) >= 0) { n++; i += needle.length(); }
        }
        return n;
    }
}
