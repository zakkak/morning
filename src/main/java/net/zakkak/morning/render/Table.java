package net.zakkak.morning.render;

import java.util.ArrayList;
import java.util.List;

/**
 * A minimal table renderer (architecture.md, "Rendering"): cells wrap to fit the width and rows
 * grow taller; nothing is truncated. Widths count code points, so wide characters (CJK, emoji)
 * may misalign — a known limitation.
 */
public final class Table {

    static final String OSC8_START = "\u001b]8;;";
    static final String OSC8_END = "\u001b\\";
    private static final int MIN_COLUMN = 10;
    private static final int MAX_WORD = 40;

    private final List<String> headers;
    private final List<List<Cell>> rows = new ArrayList<>();

    public Table(List<String> headers) {
        this.headers = List.copyOf(headers);
    }

    public Table add(List<Cell> row) {
        if (row.size() != headers.size()) {
            throw new IllegalArgumentException("row has " + row.size() + " cells, expected " + headers.size());
        }
        rows.add(List.copyOf(row));
        return this;
    }

    /** Box-drawn table no wider than {@code width} where possible; OSC-8 links when asked. */
    public String renderBox(int width, boolean links) {
        int[] widths = columnWidths(width);
        StringBuilder out = new StringBuilder();
        border(out, widths, '┌', '┬', '┐');
        List<Cell> headerCells = headers.stream().map(Cell::of).toList();
        line(out, headerCells, widths, false);
        border(out, widths, '├', '┼', '┤');
        for (int r = 0; r < rows.size(); r++) {
            if (r > 0) {
                border(out, widths, '├', '┼', '┤');
            }
            line(out, rows.get(r), widths, links);
        }
        border(out, widths, '└', '┴', '┘');
        return out.toString();
    }

    /** GitHub-flavoured markdown; viewers wrap cells themselves. */
    public String renderMarkdown() {
        StringBuilder out = new StringBuilder("|");
        headers.forEach(h -> out.append(' ').append(escapeMd(h)).append(" |"));
        out.append("\n|");
        headers.forEach(h -> out.append("---|"));
        out.append('\n');
        for (List<Cell> row : rows) {
            out.append('|');
            for (Cell c : row) {
                String text = escapeMd(c.text()).replace("\n", "<br>");
                out.append(' ').append(c.url() == null || text.isEmpty() ? text : "[" + text + "](" + c.url() + ")")
                        .append(" |");
            }
            out.append('\n');
        }
        return out.toString();
    }

    /**
     * Natural widths when they fit. Otherwise shrink the widest column first, in two passes: down
     * to its longest word (capped at {@value #MAX_WORD}) so words stay whole, then down to
     * {@value #MIN_COLUMN}. Below that the table overflows rather than truncating.
     */
    int[] columnWidths(int width) {
        int n = headers.size();
        int[] natural = new int[n];
        int[] word = new int[n];
        for (int i = 0; i < n; i++) {
            natural[i] = Math.max(1, longestLine(headers.get(i)));
            word[i] = longestWord(headers.get(i));
            for (List<Cell> row : rows) {
                natural[i] = Math.max(natural[i], longestLine(row.get(i).text()));
                word[i] = Math.max(word[i], longestWord(row.get(i).text()));
            }
        }
        int available = width - (3 * n + 1);
        int[] widths = natural.clone();
        int[] keepWords = new int[n];
        int[] floor = new int[n];
        for (int i = 0; i < n; i++) {
            keepWords[i] = Math.min(natural[i], Math.max(MIN_COLUMN, Math.min(word[i], MAX_WORD)));
            floor[i] = Math.min(natural[i], MIN_COLUMN);
        }
        shrink(widths, keepWords, available);
        shrink(widths, floor, available);
        return widths;
    }

    private static void shrink(int[] widths, int[] min, int available) {
        int sum = sum(widths);
        while (sum > available) {
            int widest = -1;
            for (int i = 0; i < widths.length; i++) {
                if (widths[i] > min[i] && (widest < 0 || widths[i] > widths[widest])) {
                    widest = i;
                }
            }
            if (widest < 0) {
                return;
            }
            widths[widest]--;
            sum--;
        }
    }

    private static void border(StringBuilder out, int[] widths, char left, char mid, char right) {
        out.append(left);
        for (int i = 0; i < widths.length; i++) {
            out.append("─".repeat(widths[i] + 2)).append(i == widths.length - 1 ? right : mid);
        }
        out.append('\n');
    }

    private static void line(StringBuilder out, List<Cell> cells, int[] widths, boolean links) {
        List<List<String>> wrapped = new ArrayList<>();
        int height = 1;
        for (int i = 0; i < cells.size(); i++) {
            List<String> lines = wrap(cells.get(i).text(), widths[i]);
            wrapped.add(lines);
            height = Math.max(height, lines.size());
        }
        for (int l = 0; l < height; l++) {
            out.append('│');
            for (int i = 0; i < cells.size(); i++) {
                List<String> lines = wrapped.get(i);
                String text = l < lines.size() ? lines.get(l) : "";
                String url = cells.get(i).url();
                String shown = links && url != null && !text.isEmpty() ? OSC8_START + url + OSC8_END + text + OSC8_START + OSC8_END
                        : text;
                out.append(' ').append(shown).append(" ".repeat(Math.max(0, widths[i] - length(text)))).append(" │");
            }
            out.append('\n');
        }
    }

    /** Greedy word wrap; words longer than the width are split, at punctuation where possible. Explicit newlines are kept. */
    static List<String> wrap(String text, int width) {
        List<String> lines = new ArrayList<>();
        for (String paragraph : text.split("\n", -1)) {
            StringBuilder current = new StringBuilder();
            for (String word : paragraph.trim().split("\\s+")) {
                if (word.isEmpty()) {
                    continue;
                }
                while (length(word) > width) {
                    if (!current.isEmpty()) {
                        lines.add(current.toString());
                        current.setLength(0);
                    }
                    int cut = breakPoint(word, width);
                    lines.add(word.substring(0, cut));
                    word = word.substring(cut);
                }
                if (current.isEmpty()) {
                    current.append(word);
                } else if (length(current) + 1 + length(word) <= width) {
                    current.append(' ').append(word);
                } else {
                    lines.add(current.toString());
                    current.setLength(0);
                    current.append(word);
                }
            }
            lines.add(current.toString());
        }
        return lines;
    }

    /** Split after the last '/', '-', ',', '#' or '.' that fits, else at the width. */
    private static int breakPoint(String word, int width) {
        int hard = word.offsetByCodePoints(0, width);
        for (int i = hard; i > hard / 3; i--) {
            if ("/-,#.".indexOf(word.charAt(i - 1)) >= 0) {
                return i;
            }
        }
        return hard;
    }

    private static int longestLine(String text) {
        int max = 0;
        for (String line : text.split("\n", -1)) {
            max = Math.max(max, length(line.trim()));
        }
        return max;
    }

    private static int longestWord(String text) {
        int max = 0;
        for (String w : text.split("\\s+")) {
            max = Math.max(max, length(w));
        }
        return max;
    }

    static int length(CharSequence s) {
        return Character.codePointCount(s, 0, s.length());
    }

    private static int sum(int[] values) {
        int s = 0;
        for (int v : values) {
            s += v;
        }
        return s;
    }

    private static String escapeMd(String s) {
        return s.replace("\\", "\\\\").replace("|", "\\|");
    }
}
