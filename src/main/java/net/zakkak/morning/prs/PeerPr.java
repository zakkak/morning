package net.zakkak.morning.prs;

import java.util.List;

/** Another open PR's head-commit checks, for the "fails on other PRs" rule. */
public record PeerPr(String id, int number, String baseRefName, String headRefOid, List<CheckContext> checks) {
    public PeerPr {
        checks = List.copyOf(checks);
    }

    static PeerPr of(PullRequestNode pr) {
        return new PeerPr(pr.id(), pr.number(), pr.baseRefName(), pr.headRefOid(), pr.checks());
    }
}
