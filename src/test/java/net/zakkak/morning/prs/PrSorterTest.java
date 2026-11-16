package net.zakkak.morning.prs;

import static net.zakkak.morning.prs.Fixtures.check;
import static net.zakkak.morning.prs.Fixtures.pr;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

/** FR-004, SC-002; spec 001 US1 scenarios 1–3. */
class PrSorterTest {

    private static CheckFailure failure(FailureClass cls) {
        return new CheckFailure(check("x", Outcome.FAILURE), cls, null);
    }

    @Test
    void groupsThenOldestUpdateFirst() {
        PullRequestStatus changes = PrSorter.analyse(
                pr(1, "2026-11-10T00:00:00Z", ReviewDecision.CHANGES_REQUESTED, Mergeable.MERGEABLE, false), List.of());
        PullRequestStatus ciNew = PrSorter.analyse(pr(2, "2026-11-09T00:00:00Z", null, Mergeable.MERGEABLE, false),
                List.of(failure(FailureClass.NOT_ANALYSED)));
        PullRequestStatus ciOld = PrSorter.analyse(pr(3, "2026-11-01T00:00:00Z", null, Mergeable.MERGEABLE, false),
                List.of(failure(FailureClass.LIKELY_FLAKY)));
        PullRequestStatus unrelated = PrSorter.analyse(pr(4, "2026-10-01T00:00:00Z", null, Mergeable.MERGEABLE, false),
                List.of(failure(FailureClass.UNRELATED_FAILS_ON_BASE)));
        PullRequestStatus elsewhere = PrSorter.analyse(pr(6, "2026-08-01T00:00:00Z", null, Mergeable.MERGEABLE, false),
                List.of(failure(FailureClass.UNRELATED_FAILS_ON_OTHER_PRS)));
        PullRequestStatus green = PrSorter.analyse(pr(5, "2026-09-01T00:00:00Z", null, Mergeable.MERGEABLE, false),
                List.of());

        List<PullRequestStatus> sorted = PrSorter.sort(List.of(green, unrelated, ciNew, changes, ciOld, elsewhere));

        assertEquals(List.of(1, 3, 2, 6, 5, 4), sorted.stream().map(s -> s.pr().number()).toList());
        assertEquals(List.of(PrGroup.CHANGES_REQUESTED, PrGroup.CI_FAILURE, PrGroup.CI_FAILURE, PrGroup.OTHER,
                PrGroup.OTHER, PrGroup.OTHER), sorted.stream().map(PullRequestStatus::group).toList());
    }

    @Test
    void changesRequestedWinsOverCiFailure() {
        PullRequestNode p = pr(1, "2026-11-10T00:00:00Z", ReviewDecision.CHANGES_REQUESTED, Mergeable.MERGEABLE, false);
        assertEquals(PrGroup.CHANGES_REQUESTED, PrSorter.group(p, List.of(failure(FailureClass.NOT_ANALYSED))));
    }

    @Test
    void cancelledChecksDoNotAskToFixCi() {
        assertEquals(List.of(Action.AWAITING_REVIEW),
                PrSorter.actions(pr(7, "2026-11-10T00:00:00Z", ReviewDecision.REVIEW_REQUIRED, Mergeable.MERGEABLE,
                        false, check("c", Outcome.CANCELLED)), List.of()));
    }

    @Test
    void actions() {
        assertEquals(List.of(Action.ADDRESS_REVIEW, Action.FIX_CI, Action.RESOLVE_CONFLICT),
                PrSorter.actions(pr(1, "2026-11-10T00:00:00Z", ReviewDecision.CHANGES_REQUESTED,
                        Mergeable.CONFLICTING, false), List.of(failure(FailureClass.NOT_ANALYSED))));
        assertEquals(List.of(Action.DRAFT, Action.WAIT_FOR_CI),
                PrSorter.actions(pr(2, "2026-11-10T00:00:00Z", ReviewDecision.REVIEW_REQUIRED, Mergeable.UNKNOWN,
                        true, check("b", Outcome.PENDING)), List.of()));
        assertEquals(List.of(Action.AWAITING_REVIEW),
                PrSorter.actions(pr(3, "2026-11-10T00:00:00Z", ReviewDecision.REVIEW_REQUIRED, Mergeable.MERGEABLE,
                        false), List.of()));
        assertEquals(List.of(Action.READY_TO_MERGE),
                PrSorter.actions(pr(4, "2026-11-10T00:00:00Z", ReviewDecision.APPROVED, Mergeable.MERGEABLE, false,
                        check("b", Outcome.SUCCESS)), List.of()));
        assertEquals(List.of(Action.READY_TO_MERGE),
                PrSorter.actions(pr(5, "2026-11-10T00:00:00Z", ReviewDecision.APPROVED, Mergeable.MERGEABLE, false),
                        List.of(failure(FailureClass.UNRELATED_FAILS_ON_BASE))));
        assertEquals(List.of(Action.RERUN_FLAKY),
                PrSorter.actions(pr(6, "2026-11-10T00:00:00Z", ReviewDecision.APPROVED, Mergeable.MERGEABLE, false),
                        List.of(failure(FailureClass.LIKELY_FLAKY))));
    }
}
