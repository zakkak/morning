package net.zakkak.morning.prs;

/** What the PR needs from me, in display order. Several may apply. */
public enum Action {
    DRAFT("draft"),
    ADDRESS_REVIEW("address review"),
    FIX_CI("fix CI"),
    RERUN_FLAKY("rerun flaky"),
    RESOLVE_CONFLICT("resolve conflict"),
    WAIT_FOR_CI("wait for CI"),
    AWAITING_REVIEW("awaiting review"),
    READY_TO_MERGE("ready to merge");

    private final String label;

    Action(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
