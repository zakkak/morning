package net.zakkak.morning.prs;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * The PRs a failing check is compared with.
 *
 * @param mine true for my own other PRs (rule A), false for other people's (rule C)
 */
public record Peers(List<PeerPr> prs, boolean mine) {
    public static final Peers NONE = new Peers(List.of(), true);

    public Peers {
        prs = List.copyOf(prs);
    }

    /**
     * Peer results count only if produced within this long of my failing result, so a failure
     * from before a since-fixed base breakage cannot clear mine (research R11).
     */
    static final Duration WINDOW = Duration.ofDays(14);

    /**
     * Mine (rule A): at least 1 failure and failures &gt;= passes. Others' (rule C): at least 2
     * failures and strictly more failures than passes.
     */
    boolean enough(int failures, int passes) {
        return mine ? failures >= 1 && failures >= passes : failures >= 2 && failures > passes;
    }

    static boolean withinWindow(Instant mine, Instant peer) {
        return mine != null && peer != null && Duration.between(peer, mine).abs().compareTo(WINDOW) <= 0;
    }
}
