package net.zakkak.morning.prs;

import java.time.Instant;
import java.util.List;

/** A PR as fetched by {@code my-prs.graphql}, with all head-commit checks (after paging). */
public record PullRequestNode(
        String id,
        int number,
        String title,
        String url,
        boolean isDraft,
        Mergeable mergeable,
        ReviewDecision reviewDecision,
        Instant updatedAt,
        String repo,
        String repoId,
        String baseRefName,
        String baseRefId,
        String headRefOid,
        RollupState rollupState,
        List<CheckContext> checks,
        boolean checksHasNextPage,
        String checksEndCursor) {

    public PullRequestNode {
        checks = List.copyOf(checks);
    }

    public PullRequestNode withMoreChecks(List<CheckContext> more, boolean hasNextPage, String endCursor) {
        List<CheckContext> all = new java.util.ArrayList<>(checks);
        all.addAll(more);
        return new PullRequestNode(id, number, title, url, isDraft, mergeable, reviewDecision, updatedAt, repo,
                repoId, baseRefName, baseRefId, headRefOid, rollupState, all, hasNextPage, endCursor);
    }

    public boolean hasFailingCheck() {
        return checks.stream().anyMatch(c -> c.outcome() == Outcome.FAILURE);
    }
}
