package net.zakkak.morning.prs;

import java.time.Instant;
import java.util.List;

/** A commit on a PR's base branch, with its checks (empty when it has none). */
public record BaseCommit(String oid, Instant committedDate, List<CheckContext> checks) {
    public BaseCommit {
        checks = List.copyOf(checks);
    }
}
