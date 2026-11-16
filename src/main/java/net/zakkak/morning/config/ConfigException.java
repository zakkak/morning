package net.zakkak.morning.config;

import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

/** The configuration is missing or invalid. */
public class ConfigException extends RuntimeException {
    private final Path file;
    private final List<ConfigError> errors;

    ConfigException(Path file, List<ConfigError> errors) {
        super("invalid configuration " + file + ": "
                + errors.stream().map(ConfigError::toString).collect(Collectors.joining("; ")));
        this.file = file;
        this.errors = List.copyOf(errors);
    }

    ConfigException(Path file, String message) {
        super(message);
        this.file = file;
        this.errors = List.of();
    }

    public Path file() {
        return file;
    }

    public List<ConfigError> errors() {
        return errors;
    }
}
