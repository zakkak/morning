package net.zakkak.morning.cli;

/**
 * A user-facing error that ends the run with an exit code. The message is printed to stderr
 * prefixed with "morning: ", so it MUST NOT contain secrets.
 */
public class MorningException extends RuntimeException {
    private final int exitCode;

    public MorningException(int exitCode, String message) {
        super(message);
        this.exitCode = exitCode;
    }

    public int exitCode() {
        return exitCode;
    }
}
