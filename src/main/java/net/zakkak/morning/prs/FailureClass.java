package net.zakkak.morning.prs;

/** Deterministic classification of a failing check. Rules apply in this order. */
public enum FailureClass {
    /** The most recent base commit that ran a check with this name failed it too. */
    UNRELATED_FAILS_ON_BASE("unrelated (fails on base)"),
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
}
