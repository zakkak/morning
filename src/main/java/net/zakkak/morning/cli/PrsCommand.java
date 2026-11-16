package net.zakkak.morning.cli;

import java.util.concurrent.Callable;

import jakarta.inject.Inject;
import net.zakkak.morning.config.MyPrsSection;
import picocli.CommandLine.Command;
import picocli.CommandLine.ParentCommand;

/** My open PRs, sorted by what needs me (specs/001-my-prs). */
@Command(name = "prs", description = "My open PRs, sorted by what needs me first.")
public class PrsCommand implements Callable<Integer> {

    @ParentCommand
    MorningCommand parent;

    @Inject
    SectionRunner runner;

    @Override
    public Integer call() {
        return runner.run(parent.options(), MyPrsSection.TYPE);
    }
}
