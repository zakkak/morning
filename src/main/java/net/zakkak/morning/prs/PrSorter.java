package net.zakkak.morning.prs;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Groups, derives actions and sorts (FR-004, data-model.md "Analysis"). */
public final class PrSorter {

    private static final Comparator<PullRequestStatus> ORDER = Comparator
            .comparing(PullRequestStatus::group)
            .thenComparing(s -> s.pr().updatedAt());

    private PrSorter() {
    }

    public static PullRequestStatus analyse(PullRequestNode pr, List<CheckFailure> failures) {
        return new PullRequestStatus(pr, group(pr, failures), failures, actions(pr, failures));
    }

    public static List<PullRequestStatus> sort(List<PullRequestStatus> statuses) {
        List<PullRequestStatus> sorted = new ArrayList<>(statuses);
        sorted.sort(ORDER);
        return sorted;
    }

    static PrGroup group(PullRequestNode pr, List<CheckFailure> failures) {
        if (pr.reviewDecision() == ReviewDecision.CHANGES_REQUESTED) {
            return PrGroup.CHANGES_REQUESTED;
        }
        if (failures.stream().anyMatch(f -> !f.failureClass().unrelated())) {
            return PrGroup.CI_FAILURE;
        }
        return PrGroup.OTHER;
    }

    static List<Action> actions(PullRequestNode pr, List<CheckFailure> failures) {
        List<Action> actions = new ArrayList<>();
        boolean mine = failures.stream().anyMatch(f -> f.failureClass() == FailureClass.NOT_ANALYSED);
        boolean flaky = failures.stream().anyMatch(f -> f.failureClass() == FailureClass.LIKELY_FLAKY);
        boolean pending = pr.checks().stream().anyMatch(c -> c.outcome() == Outcome.PENDING);
        if (pr.isDraft()) {
            actions.add(Action.DRAFT);
        }
        if (pr.reviewDecision() == ReviewDecision.CHANGES_REQUESTED) {
            actions.add(Action.ADDRESS_REVIEW);
        }
        if (mine) {
            actions.add(Action.FIX_CI);
        }
        if (flaky) {
            actions.add(Action.RERUN_FLAKY);
        }
        if (pr.mergeable() == Mergeable.CONFLICTING) {
            actions.add(Action.RESOLVE_CONFLICT);
        }
        if (pending) {
            actions.add(Action.WAIT_FOR_CI);
        }
        if (!pr.isDraft() && pr.reviewDecision() == ReviewDecision.REVIEW_REQUIRED) {
            actions.add(Action.AWAITING_REVIEW);
        }
        if (!pr.isDraft() && pr.reviewDecision() == ReviewDecision.APPROVED && pr.mergeable() == Mergeable.MERGEABLE
                && !mine && !flaky && !pending) {
            actions.add(Action.READY_TO_MERGE);
        }
        return actions;
    }
}
