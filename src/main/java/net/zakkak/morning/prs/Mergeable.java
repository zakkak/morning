package net.zakkak.morning.prs;

/** GitHub's {@code MergeableState}. UNKNOWN means GitHub has not computed it yet. */
public enum Mergeable {
    MERGEABLE, CONFLICTING, UNKNOWN
}
