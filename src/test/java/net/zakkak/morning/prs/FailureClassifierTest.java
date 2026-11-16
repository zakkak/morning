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
        return FailureClassifier.classify(FAILING, base, attempts).failureClass();
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
        CheckFailure f = FailureClassifier.classify(FAILING, base, CheckAttempts.EMPTY);
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
        List<CheckFailure> failures = FailureClassifier.classify(pr, BaseHistory.EMPTY, CheckAttempts.EMPTY);
        assertEquals(List.of("b"), failures.stream().map(f -> f.check().name()).toList());
    }

    @Test
    void outcomeMapping() {
        assertEquals(Outcome.FAILURE, Outcome.ofCheckRun("COMPLETED", "TIMED_OUT"));
        assertEquals(Outcome.FAILURE, Outcome.ofCheckRun("COMPLETED", "CANCELLED"));
        assertEquals(Outcome.FAILURE, Outcome.ofCheckRun("COMPLETED", "STARTUP_FAILURE"));
        assertEquals(Outcome.FAILURE, Outcome.ofCheckRun("COMPLETED", "ACTION_REQUIRED"));
        assertEquals(Outcome.PENDING, Outcome.ofCheckRun("IN_PROGRESS", null));
        assertEquals(Outcome.SKIPPED, Outcome.ofCheckRun("COMPLETED", "SKIPPED"));
        assertEquals(Outcome.NEUTRAL, Outcome.ofCheckRun("COMPLETED", "NEUTRAL"));
        assertEquals(Outcome.FAILURE, Outcome.ofStatus("ERROR"));
        assertEquals(Outcome.PENDING, Outcome.ofStatus("EXPECTED"));
    }
}
