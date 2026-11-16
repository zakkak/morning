package net.zakkak.morning.cli;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import net.zakkak.morning.config.CiStatusSection;
import net.zakkak.morning.config.ConfigException;
import net.zakkak.morning.config.ConfigLoader;
import net.zakkak.morning.config.MorningConfig;
import net.zakkak.morning.config.MyPrsSection;
import net.zakkak.morning.config.ReviewCandidatesSection;
import net.zakkak.morning.config.Section;
import net.zakkak.morning.config.SummarySection;
import net.zakkak.morning.github.CredentialsException;
import net.zakkak.morning.github.GitHubCredentials;
import net.zakkak.morning.github.GitHubException;
import net.zakkak.morning.github.GraphQLClientProducer;
import net.zakkak.morning.github.GraphQLQueries;
import net.zakkak.morning.github.TokenResolver;
import net.zakkak.morning.prs.MyPrsFetcher;
import net.zakkak.morning.prs.MyPrsReport;
import net.zakkak.morning.render.JsonRenderer;
import net.zakkak.morning.render.MarkdownRenderer;
import net.zakkak.morning.render.TerminalInfo;
import net.zakkak.morning.render.TtyRenderer;

/**
 * Loads the config, selects sections, resolves the token, then runs and renders each section.
 * Everything that can fail with exit code 2 happens before the first API call.
 */
@ApplicationScoped
public class SectionRunner {

    /** Section types not implemented yet, and the feature that will implement them. */
    private static final Map<String, String> PLANNED = Map.of(
            CiStatusSection.TYPE, "002",
            ReviewCandidatesSection.TYPE, "003",
            SummarySection.TYPE, "004");

    @Inject
    ConfigLoader configLoader;
    @Inject
    TokenResolver tokenResolver;
    @Inject
    GraphQLClientProducer clients;
    @Inject
    MyPrsFetcher myPrs;

    PrintStream out = System.out;
    PrintStream err = System.err;

    /**
     * @param onlyType run only sections of this type (e.g. {@code my-prs}), or null for all
     */
    public int run(GlobalOptions options, String onlyType) {
        try {
            return doRun(options, onlyType);
        } catch (MorningException e) {
            err.println("morning: " + e.getMessage());
            return e.exitCode();
        }
    }

    private int doRun(GlobalOptions options, String onlyType) {
        MorningConfig config;
        try {
            config = configLoader.load(options.config());
        } catch (ConfigException e) {
            if (e.errors().isEmpty()) {
                throw new MorningException(ExitCodes.USAGE, e.getMessage());
            }
            String errors = e.errors().stream().map(Object::toString).collect(Collectors.joining("\n  "));
            throw new MorningException(ExitCodes.USAGE, "invalid configuration " + e.file() + ": " + errors);
        }

        List<Section> selected = select(config.sections(), options.sections(), onlyType);
        List<MyPrsSection> runnable = new ArrayList<>();
        for (Section s : selected) {
            if (s instanceof MyPrsSection p) {
                runnable.add(p);
            } else {
                err.println("morning: section \"" + s.name() + "\": " + s.type()
                        + " is not supported yet (planned in feature " + PLANNED.get(s.type()) + ")");
            }
        }
        if (runnable.isEmpty()) {
            return ExitCodes.OK;
        }

        GitHubCredentials credentials;
        try {
            credentials = tokenResolver.resolve();
        } catch (CredentialsException e) {
            throw new MorningException(ExitCodes.USAGE, e.getMessage());
        }

        OutputFormat format = options.format() != null ? options.format()
                : TerminalInfo.stdoutIsTerminal() ? OutputFormat.tty : OutputFormat.md;
        int exit = ExitCodes.OK;
        try (GraphQLQueries github = clients.open(credentials)) {
            for (MyPrsSection section : runnable) {
                try {
                    MyPrsReport report = myPrs.fetch(section, github);
                    out.print(render(report, format, options.width()));
                    out.flush();
                } catch (GitHubException e) {
                    err.println("morning: section \"" + section.name() + "\": " + e.getMessage());
                    exit = ExitCodes.SECTION_FAILED;
                }
            }
        }
        return exit;
    }

    private static String render(MyPrsReport report, OutputFormat format, Integer width) {
        return switch (format) {
            case json -> JsonRenderer.render(report);
            case md -> MarkdownRenderer.render(report);
            case tty -> TtyRenderer.render(report, TerminalInfo.width(width), TerminalInfo.stdoutIsTerminal());
        };
    }

    static List<Section> select(List<Section> sections, List<String> names, String onlyType) {
        List<Section> selected = new ArrayList<>();
        if (names.isEmpty()) {
            selected.addAll(sections);
        } else {
            for (String name : names) {
                Section match = sections.stream().filter(s -> s.name().equals(name)).findFirst()
                        .orElseThrow(() -> new MorningException(ExitCodes.USAGE, "no section named \"" + name
                                + "\"; configured: " + sections.stream().map(s -> "\"" + s.name() + "\"")
                                        .collect(Collectors.joining(", "))));
                if (onlyType != null && !match.type().equals(onlyType)) {
                    throw new MorningException(ExitCodes.USAGE, "section \"" + name + "\" is a " + match.type()
                            + " section, not " + onlyType);
                }
                if (!selected.contains(match)) {
                    selected.add(match);
                }
            }
        }
        if (onlyType != null) {
            selected.removeIf(s -> !s.type().equals(onlyType));
            if (selected.isEmpty()) {
                throw new MorningException(ExitCodes.USAGE, "no " + onlyType + " section configured");
            }
        }
        return selected;
    }
}
