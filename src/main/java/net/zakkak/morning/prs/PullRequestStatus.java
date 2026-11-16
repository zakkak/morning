package net.zakkak.morning.prs;

import java.util.List;

/** A PR with its analysis. */
public record PullRequestStatus(PullRequestNode pr, PrGroup group, List<CheckFailure> failures, List<Action> actions) {
    public PullRequestStatus {
        failures = List.copyOf(failures);
        actions = List.copyOf(actions);
    }
}
