package net.zakkak.morning.prs;

/** GitHub's {@code PullRequestReviewDecision}; null on a PR means no review policy applies. */
public enum ReviewDecision {
    APPROVED, CHANGES_REQUESTED, REVIEW_REQUIRED
}
