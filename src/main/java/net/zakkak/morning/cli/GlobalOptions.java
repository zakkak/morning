package net.zakkak.morning.cli;

import java.nio.file.Path;
import java.util.List;

/** Parsed global options. Nullable fields mean "not given". */
public record GlobalOptions(Path config, List<String> sections, OutputFormat format, Integer width,
        boolean noLlm) {
}
