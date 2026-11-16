package net.zakkak.morning.prs;

/** Deterministic classification of a failing check. Rules apply in this order. */
public enum FailureClass {
    /** The most recent base commit that ran a check with this name failed it too. */
    UNRELATED_FAILS_ON_BASE("unrelated (fails on base)"),
    /**
     * The check also fails on other open PRs to the same base: my own (rule A) or, when I have
     * no other PR there, other people's (rule C).
     */
    UNRELATED_FAILS_ON_OTHER_PRS("likely unrelated (fails on other PRs)"),
    /** An earlier attempt of this check on the same commit passed. */
    LIKELY_FLAKY("likely flaky"),
    /** No rule applied. Feature 002+ may ask an LLM; until then this is the answer. */
    NOT_ANALYSED("not analysed");

    private final String label;

    FailureClass(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    /** Evidence points away from my change: such failures don't put a PR in the CI-failure group. */
    public boolean unrelated() {
        return this == UNRELATED_FAILS_ON_BASE || this == UNRELATED_FAILS_ON_OTHER_PRS;
    }
}
