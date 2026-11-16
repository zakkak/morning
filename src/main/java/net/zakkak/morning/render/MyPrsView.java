package net.zakkak.morning.render;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import net.zakkak.morning.prs.Action;
import net.zakkak.morning.prs.CheckFailure;
import net.zakkak.morning.prs.FailureClass;
import net.zakkak.morning.prs.MyPrsReport;
import net.zakkak.morning.prs.Outcome;
import net.zakkak.morning.prs.PullRequestNode;
import net.zakkak.morning.prs.PullRequestStatus;

/** The my-prs report as a table; shared by the tty and markdown renderers. */
final class MyPrsView {

    static final int NAMES_PER_CLASS = 3;
    static final List<String> HEADERS = List.of("PR", "Title", "Review", "CI", "Failing checks", "Merge", "Action");

    private MyPrsView() {
    }

    static Table table(MyPrsReport report) {
        Table table = new Table(HEADERS);
        for (PullRequestStatus s : report.prs()) {
            PullRequestNode pr = s.pr();
            table.add(List.of(
                    new Cell(pr.repo() + "#" + pr.number(), pr.url()),
                    Cell.of(pr.title()),
                    Cell.of(review(pr)),
                    Cell.of(ci(pr)),
                    Cell.of(failures(s.failures())),
                    Cell.of(merge(pr)),
                    Cell.of(s.actions().stream().map(Action::label).collect(Collectors.joining(", ")))));
        }
        return table;
    }

    static String heading(MyPrsReport report) {
        int n = report.prs().size();
        return report.section() + " (" + n + " open)";
    }

    private static String review(PullRequestNode pr) {
        if (pr.reviewDecision() == null) {
            return "—";
        }
        return switch (pr.reviewDecision()) {
            case APPROVED -> "approved";
            case CHANGES_REQUESTED -> "changes requested";
            case REVIEW_REQUIRED -> "review required";
        };
    }

    private static String ci(PullRequestNode pr) {
        long failing = pr.checks().stream().filter(c -> c.outcome() == Outcome.FAILURE).count();
        long pending = pr.checks().stream().filter(c -> c.outcome() == Outcome.PENDING).count();
        if (pr.checks().isEmpty()) {
            return "none";
        }
        if (failing > 0) {
            return failing + " failing" + (pending > 0 ? ", " + pending + " pending" : "");
        }
        return pending > 0 ? pending + " pending" : "passing";
    }

    /** One line per class, at most {@value #NAMES_PER_CLASS} check names each; JSON has them all. */
    static String failures(List<CheckFailure> failures) {
        Map<FailureClass, List<String>> byClass = new EnumMap<>(FailureClass.class);
        for (CheckFailure f : failures) {
            byClass.computeIfAbsent(f.failureClass(), k -> new ArrayList<>()).add(f.check().name());
        }
        List<String> lines = new ArrayList<>();
        byClass.forEach((cls, names) -> {
            String shown = String.join("; ", names.subList(0, Math.min(NAMES_PER_CLASS, names.size())));
            String more = names.size() > NAMES_PER_CLASS ? " (+" + (names.size() - NAMES_PER_CLASS) + " more)" : "";
            lines.add(cls.label() + ": " + shown + more);
        });
        return String.join("\n", lines);
    }

    private static String merge(PullRequestNode pr) {
        return switch (pr.mergeable()) {
            case MERGEABLE -> "ok";
            case CONFLICTING -> "conflict";
            case UNKNOWN -> "?";
        };
    }
}
