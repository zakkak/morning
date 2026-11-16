package net.zakkak.morning.prs;

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

    /** Failures needed on peers: one of my own PRs, or two of other people's. */
    int minFailures() {
        return mine ? 1 : 2;
    }
}
