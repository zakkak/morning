package net.zakkak.morning.cli;

/** Process exit codes (specs/001-my-prs/contracts/cli.md). */
public final class ExitCodes {
    /** Success, including "no open PRs". */
    public static final int OK = 0;
    /** At least one section failed at run time; the other sections were still rendered. */
    public static final int SECTION_FAILED = 1;
    /** Usage, configuration or credential error, reported before any API call. */
    public static final int USAGE = 2;

    private ExitCodes() {
    }
}
