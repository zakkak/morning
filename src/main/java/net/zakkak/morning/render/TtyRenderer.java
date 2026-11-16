package net.zakkak.morning.render;

import net.zakkak.morning.prs.MyPrsReport;

/** Terminal output: a wrapped box table, with OSC-8 hyperlinks when stdout is a terminal. */
public final class TtyRenderer {
    private TtyRenderer() {
    }

    public static String render(MyPrsReport report, int width, boolean links) {
        StringBuilder out = new StringBuilder(MyPrsView.heading(report)).append('\n');
        if (report.prs().isEmpty()) {
            out.append("No open PRs\n");
        } else {
            out.append(MyPrsView.table(report).renderBox(width, links));
        }
        report.warnings().forEach(w -> out.append("note: ").append(w).append('\n'));
        return out.toString();
    }
}
