package net.zakkak.morning.cli;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

import io.quarkus.picocli.runtime.annotations.TopCommand;
import jakarta.inject.Inject;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.ScopeType;

/**
 * The top command. Global options are inherited by every subcommand, so they can be given
 * before or after the subcommand name. They are setters so that the value always lands on
 * this instance, wherever it was matched.
 */
@TopCommand
@Command(name = "morning", mixinStandardHelpOptions = true, versionProvider = VersionProvider.class,
        description = "A deterministic daily developer routine on GitHub.",
        subcommands = { AllCommand.class, PrsCommand.class })
public class MorningCommand implements Callable<Integer> {

    private Path config;
    private final List<String> sections = new ArrayList<>();
    private OutputFormat format;
    private Integer width;
    private boolean noLlm;

    @Inject
    AllCommand all;

    @Option(names = "--config", paramLabel = "<path>", scope = ScopeType.INHERIT,
            description = "Routine configuration. Default: $XDG_CONFIG_HOME/morning/config.yaml, else ~/.config/morning/config.yaml")
    void setConfig(Path config) {
        this.config = config;
    }

    @Option(names = "--section", paramLabel = "<name>", scope = ScopeType.INHERIT,
            description = "Run only the named section; repeatable.")
    void addSection(List<String> names) {
        sections.addAll(names);
    }

    @Option(names = "--format", paramLabel = "tty|md|json", scope = ScopeType.INHERIT,
            description = "Output format. Default: tty when stdout is a terminal, else md.")
    void setFormat(OutputFormat format) {
        this.format = format;
    }

    @Option(names = "--width", paramLabel = "<n>", scope = ScopeType.INHERIT,
            description = "Table width. Default: $COLUMNS, then the terminal size, then 120.")
    void setWidth(int width) {
        this.width = width;
    }

    @Option(names = "--no-llm", scope = ScopeType.INHERIT,
            description = "Skip all LLM calls. (No LLM is used yet.)")
    void setNoLlm(boolean noLlm) {
        this.noLlm = noLlm;
    }

    public GlobalOptions options() {
        return new GlobalOptions(config, List.copyOf(sections), format, width, noLlm);
    }

    @Override
    public Integer call() {
        all.parent = this;
        return all.call();
    }
}
