package net.zakkak.morning.prs;

import java.util.List;

/** The last commits of a base branch (by Ref node ID), newest first. */
public record BaseHistory(String refId, List<BaseCommit> commits) {
    public static final BaseHistory EMPTY = new BaseHistory(null, List.of());

    public BaseHistory {
        commits = List.copyOf(commits);
    }
}
