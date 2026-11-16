package net.zakkak.morning.config;

/**
 * One configuration problem.
 *
 * @param path YAML path such as {@code morning.sections[1].include-orgs}
 * @param message what is wrong; never contains a secret value
 */
public record ConfigError(String path, String message) {
    @Override
    public String toString() {
        return path.isEmpty() ? message : path + ": " + message;
    }
}
