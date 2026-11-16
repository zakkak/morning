package net.zakkak.morning.prs;

import java.time.Instant;
import java.util.List;

/** Small builders for unit tests. */
final class Fixtures {
    private Fixtures() {
    }

    static CheckContext check(String name, Outcome outcome) {
        return new CheckContext(name, CheckContext.Kind.CHECK_RUN, outcome, null, "github-actions");
    }

    static PullRequestNode pr(int number, String updatedAt, ReviewDecision review, Mergeable mergeable, boolean draft,
            CheckContext... checks) {
        return new PullRequestNode("PR_" + number, number, "title " + number, "https://example.com/" + number, draft,
                mergeable, review, Instant.parse(updatedAt), "example-org/alpha", "REPO", "main", "REF",
                String.valueOf(number).repeat(40).substring(0, 40),
                null, List.of(checks), false, null);
    }

    static PeerPr peer(int number, String base, CheckContext... checks) {
        return new PeerPr("PEER_" + number, number, base, "p".repeat(39) + number % 10, List.of(checks));
    }

    static BaseCommit commit(String oid, String date, CheckContext... checks) {
        return new BaseCommit(oid, Instant.parse(date), List.of(checks));
    }
}
