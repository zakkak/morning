package net.zakkak.morning.prs;

import static net.zakkak.morning.prs.Fixtures.check;
import static net.zakkak.morning.prs.Fixtures.commit;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

/** FR-003; spec 001 US1 scenarios 2–4. */
class FailureClassifierTest {

    private static final CheckContext FAILING = check("build", Outcome.FAILURE);

    private static FailureClass classify(BaseHistory base, CheckAttempts attempts) {
        return FailureClassifier.classify(FAILING, base, attempts, Peers.NONE).failureClass();
    }

    /** Classifies PR #1's failing "build" check against the given peers. */
    private static CheckFailure withPeers(boolean mine, PeerPr... peers) {
        PullRequestNode pr = Fixtures.pr(1, "2026-11-01T00:00:00Z", null, Mergeable.MERGEABLE, false, FAILING);
        return FailureClassifier.classify(pr, BaseHistory.EMPTY, CheckAttempts.EMPTY, new Peers(List.of(peers), mine))
                .get(0);
    }

    private static BaseHistory base(BaseCommit... commits) {
        return new BaseHistory("REF", List.of(commits));
    }

    @Test
    void failsOnMostRecentBaseCommitWithSameCheck() {
        BaseHistory base = base(
                commit("c3c3c3c3c3", "2026-11-12T00:00:00Z"),
                commit("c2c2c2c2c2", "2026-11-11T00:00:00Z", check("build", Outcome.FAILURE)),
                commit("c1c1c1c1c1", "2026-11-10T00:00:00Z", check("build", Outcome.SUCCESS)));
        CheckFailure f = FailureClassifier.classify(FAILING, base, CheckAttempts.EMPTY, Peers.NONE);
        assertEquals(FailureClass.UNRELATED_FAILS_ON_BASE, f.failureClass());
        assertEquals("failed on base c2c2c2c (2026-11-11)", f.evidence());
    }

    @Test
    void passedOnMostRecentBaseCommitIsNotUnrelated() {
        BaseHistory base = base(
                commit("c2", "2026-11-11T00:00:00Z", check("build", Outcome.SUCCESS)),
                commit("c1", "2026-11-10T00:00:00Z", check("build", Outcome.FAILURE)));
        assertEquals(FailureClass.NOT_ANALYSED, classify(base, CheckAttempts.EMPTY));
    }

    @Test
    void pendingOnBaseIsSkipped() {
        BaseHistory base = base(
                commit("c2", "2026-11-11T00:00:00Z", check("build", Outcome.PENDING)),
                commit("c1", "2026-11-10T00:00:00Z", check("build", Outcome.FAILURE)));
        assertEquals(FailureClass.UNRELATED_FAILS_ON_BASE, classify(base, CheckAttempts.EMPTY));
    }

    @Test
    void noBaseCounterpartIsNeverUnrelated() {
        BaseHistory base = base(commit("c1", "2026-11-10T00:00:00Z", check("other", Outcome.FAILURE)));
        assertEquals(FailureClass.NOT_ANALYSED, classify(base, CheckAttempts.EMPTY));
        assertEquals(FailureClass.NOT_ANALYSED, classify(BaseHistory.EMPTY, CheckAttempts.EMPTY));
    }

    @Test
    void earlierPassingAttemptIsLikelyFlaky() {
        CheckAttempts attempts = new CheckAttempts("PR",
                Map.of("build", List.of(Outcome.SUCCESS, Outcome.FAILURE)));
        assertEquals(FailureClass.LIKELY_FLAKY, classify(BaseHistory.EMPTY, attempts));
    }

    @Test
    void onlyFailedAttemptsAreNotFlaky() {
        CheckAttempts attempts = new CheckAttempts("PR", Map.of("build", List.of(Outcome.FAILURE, Outcome.FAILURE)));
        assertEquals(FailureClass.NOT_ANALYSED, classify(BaseHistory.EMPTY, attempts));
    }

    @Test
    void failingOnBaseWinsOverFlaky() {
        BaseHistory base = base(commit("c1", "2026-11-10T00:00:00Z", check("build", Outcome.FAILURE)));
        CheckAttempts attempts = new CheckAttempts("PR", Map.of("build", List.of(Outcome.SUCCESS)));
        assertEquals(FailureClass.UNRELATED_FAILS_ON_BASE, classify(base, attempts));
    }

    @Test
    void onlyFailingChecksAreClassified() {
        PullRequestNode pr = Fixtures.pr(1, "2026-11-01T00:00:00Z", null, Mergeable.MERGEABLE, false,
                check("a", Outcome.SUCCESS), check("b", Outcome.FAILURE), check("c", Outcome.PENDING));
        List<CheckFailure> failures = FailureClassifier.classify(pr, BaseHistory.EMPTY, CheckAttempts.EMPTY,
                Peers.NONE);
        assertEquals(List.of("b"), failures.stream().map(f -> f.check().name()).toList());
    }

    @Test
    void cancelledChecksAreNotFailures() {
        PullRequestNode pr = Fixtures.pr(1, "2026-11-01T00:00:00Z", null, Mergeable.MERGEABLE, false,
                check("a", Outcome.CANCELLED));
        assertEquals(List.of(), FailureClassifier.classify(pr, BaseHistory.EMPTY, CheckAttempts.EMPTY, Peers.NONE));
    }

    @Test
    void ruleA_failsOnOneOfMyOtherPrs() {
        CheckFailure f = withPeers(true, Fixtures.peer(2, "main", check("build", Outcome.FAILURE)));
        assertEquals(FailureClass.UNRELATED_FAILS_ON_OTHER_PRS, f.failureClass());
        assertEquals("also fails on my #2", f.evidence());
    }

    @Test
    void ruleA_morePassesThanFailuresIsNotEnough() {
        assertEquals(FailureClass.NOT_ANALYSED, withPeers(true,
                Fixtures.peer(2, "main", check("build", Outcome.FAILURE)),
                Fixtures.peer(3, "main", check("build", Outcome.SUCCESS)),
                Fixtures.peer(4, "main", check("build", Outcome.SUCCESS))).failureClass());
    }

    @Test
    void ruleA_ignoresOtherBasesSameHeadAndUnfinishedChecks() {
        PullRequestNode pr = Fixtures.pr(1, "2026-11-01T00:00:00Z", null, Mergeable.MERGEABLE, false, FAILING);
        PeerPr sameHead = new PeerPr("PEER_9", 9, "main", pr.headRefOid(), List.of(check("build", Outcome.FAILURE)));
        assertEquals(FailureClass.NOT_ANALYSED, withPeers(true,
                Fixtures.peer(2, "release", check("build", Outcome.FAILURE)),
                Fixtures.peer(3, "main", check("build", Outcome.CANCELLED)),
                Fixtures.peer(4, "main", check("build", Outcome.PENDING))).failureClass());
        assertEquals(List.of(FailureClass.NOT_ANALYSED), FailureClassifier.classify(pr, BaseHistory.EMPTY,
                CheckAttempts.EMPTY, new Peers(List.of(sameHead), true)).stream().map(CheckFailure::failureClass).toList());
    }

    @Test
    void ruleC_needsTwoOfOtherPeoplesPrs() {
        assertEquals(FailureClass.NOT_ANALYSED,
                withPeers(false, Fixtures.peer(2, "main", check("build", Outcome.FAILURE))).failureClass());
        CheckFailure f = withPeers(false,
                Fixtures.peer(2, "main", check("build", Outcome.FAILURE)),
                Fixtures.peer(3, "main", check("build", Outcome.FAILURE)),
                Fixtures.peer(4, "main", check("build", Outcome.SUCCESS)),
                Fixtures.peer(5, "main", check("other", Outcome.FAILURE)));
        assertEquals(FailureClass.UNRELATED_FAILS_ON_OTHER_PRS, f.failureClass());
        assertEquals("also fails on 2 of 3 other open PRs that ran it (#2, #3)", f.evidence());
    }

    @Test
    void baseWinsOverPeersAndPeersWinOverFlaky() {
        BaseHistory base = base(commit("c1", "2026-11-10T00:00:00Z", check("build", Outcome.FAILURE)));
        Peers peers = new Peers(List.of(Fixtures.peer(2, "main", check("build", Outcome.FAILURE))), true);
        CheckAttempts flaky = new CheckAttempts("PR", Map.of("build", List.of(Outcome.SUCCESS)));
        assertEquals(FailureClass.UNRELATED_FAILS_ON_BASE,
                FailureClassifier.classify(FAILING, base, flaky, peers).failureClass());
        assertEquals(FailureClass.UNRELATED_FAILS_ON_OTHER_PRS,
                FailureClassifier.classify(FAILING, BaseHistory.EMPTY, flaky, peers).failureClass());
    }

    @Test
    void outcomeMapping() {
        assertEquals(Outcome.FAILURE, Outcome.ofCheckRun("COMPLETED", "TIMED_OUT"));
        assertEquals(Outcome.CANCELLED, Outcome.ofCheckRun("COMPLETED", "CANCELLED"));
        assertEquals(Outcome.FAILURE, Outcome.ofCheckRun("COMPLETED", "STARTUP_FAILURE"));
        assertEquals(Outcome.FAILURE, Outcome.ofCheckRun("COMPLETED", "ACTION_REQUIRED"));
        assertEquals(Outcome.PENDING, Outcome.ofCheckRun("IN_PROGRESS", null));
        assertEquals(Outcome.SKIPPED, Outcome.ofCheckRun("COMPLETED", "SKIPPED"));
        assertEquals(Outcome.NEUTRAL, Outcome.ofCheckRun("COMPLETED", "NEUTRAL"));
        assertEquals(Outcome.FAILURE, Outcome.ofStatus("ERROR"));
        assertEquals(Outcome.PENDING, Outcome.ofStatus("EXPECTED"));
    }
}
