package net.zakkak.morning.cli;

import java.util.concurrent.Callable;

import jakarta.inject.Inject;
import picocli.CommandLine.Command;
import picocli.CommandLine.ParentCommand;

/** Every configured section, in config order. In 001 only my-prs sections run. */
@Command(name = "all", description = "Run every configured section, in config order.")
public class AllCommand implements Callable<Integer> {

    @ParentCommand
    MorningCommand parent;

    @Inject
    SectionRunner runner;

    @Override
    public Integer call() {
        return runner.run(parent.options(), null);
    }
}
