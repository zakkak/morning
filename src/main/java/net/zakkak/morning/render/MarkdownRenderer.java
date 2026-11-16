package net.zakkak.morning.render;

import net.zakkak.morning.prs.MyPrsReport;

/** {@code --format md}. */
public final class MarkdownRenderer {
    private MarkdownRenderer() {
    }

    public static String render(MyPrsReport report) {
        StringBuilder out = new StringBuilder("## ").append(MyPrsView.heading(report)).append("\n\n");
        if (report.prs().isEmpty()) {
            out.append("No open PRs\n");
        } else {
            out.append(MyPrsView.table(report).renderMarkdown());
        }
        report.warnings().forEach(w -> out.append("\n> note: ").append(w).append('\n'));
        return out.toString();
    }
}
