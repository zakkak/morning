package net.zakkak.morning.prs;

/** The normalised result of one check (data-model.md, CheckContext). */
public enum Outcome {
    SUCCESS, FAILURE, PENDING, NEUTRAL, SKIPPED;

    /** CheckRun: anything not COMPLETED is pending; failing conclusions collapse to FAILURE. */
    static Outcome ofCheckRun(String status, String conclusion) {
        if ((status != null && !"COMPLETED".equals(status)) || conclusion == null) {
            return PENDING;
        }
        return switch (conclusion) {
            case "SUCCESS" -> SUCCESS;
            case "FAILURE", "TIMED_OUT", "CANCELLED", "STARTUP_FAILURE", "ACTION_REQUIRED" -> FAILURE;
            case "NEUTRAL" -> NEUTRAL;
            default -> SKIPPED; // SKIPPED, STALE
        };
    }

    /** StatusContext (commit statuses from external CI). */
    static Outcome ofStatus(String state) {
        return switch (state == null ? "" : state) {
            case "SUCCESS" -> SUCCESS;
            case "FAILURE", "ERROR" -> FAILURE;
            default -> PENDING; // PENDING, EXPECTED
        };
    }
}
