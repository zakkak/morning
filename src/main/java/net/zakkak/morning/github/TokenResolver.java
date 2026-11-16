package net.zakkak.morning.github;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import jakarta.enterprise.context.ApplicationScoped;

/**
 * Finds the GitHub token: {@code GITHUB_TOKEN}, then {@code GH_TOKEN}, then {@code gh auth token}
 * (research R7). Never reads config files.
 */
@ApplicationScoped
public class TokenResolver {

    static final String NOT_FOUND = "GitHub token not found. Set GITHUB_TOKEN or GH_TOKEN, or log in with `gh auth login`.";
    private static final long GH_TIMEOUT_SECONDS = 10;

    private final Function<String, String> env;
    private final List<String> ghCommand;

    public TokenResolver() {
        this(System::getenv, List.of("gh", "auth", "token"));
    }

    TokenResolver(Function<String, String> env, List<String> ghCommand) {
        this.env = env;
        this.ghCommand = ghCommand;
    }

    public GitHubCredentials resolve() {
        for (String name : List.of("GITHUB_TOKEN", "GH_TOKEN")) {
            String value = env.apply(name);
            if (value != null && !value.isBlank()) {
                return new GitHubCredentials(value.trim());
            }
        }
        String fromGh = fromGhCli();
        if (fromGh != null) {
            return new GitHubCredentials(fromGh);
        }
        throw new CredentialsException(NOT_FOUND);
    }

    private String fromGhCli() {
        Process process;
        try {
            process = new ProcessBuilder(ghCommand)
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .redirectInput(ProcessBuilder.Redirect.from(new java.io.File("/dev/null")))
                    .start();
        } catch (IOException e) {
            return null; // gh not installed
        }
        try (InputStream out = process.getInputStream()) {
            byte[] bytes = out.readNBytes(4096);
            if (!process.waitFor(GH_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return null;
            }
            String token = new String(bytes, StandardCharsets.UTF_8).trim();
            return process.exitValue() == 0 && !token.isEmpty() ? token : null;
        } catch (IOException e) {
            process.destroyForcibly();
            return null;
        } catch (InterruptedException e) {
            process.destroyForcibly();
            Thread.currentThread().interrupt();
            return null;
        }
    }
}
