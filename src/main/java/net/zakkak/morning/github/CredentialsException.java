package net.zakkak.morning.github;

/** No usable GitHub token. */
public class CredentialsException extends RuntimeException {
    public CredentialsException(String message) {
        super(message);
    }
}
