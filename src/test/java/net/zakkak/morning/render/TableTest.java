package net.zakkak.morning.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

/** Spec 001 US1 scenario 5: cells wrap, rows grow, nothing is truncated. */
class TableTest {

    private static final String LONG_TITLE = "Record native-image builder in embedded CycloneDX SBOM so that reviewers can tell";

    private static Table sample() {
        return new Table(List.of("PR", "Title", "CI"))
                .add(List.of(new Cell("quarkusio/quarkus#56633", "https://github.com/quarkusio/quarkus/pull/56633"),
                        Cell.of(LONG_TITLE), Cell.of("passing")))
                .add(List.of(Cell.of("a/b#1"), Cell.of("short"), Cell.of("2 failing")));
    }

    private static String strip(String s) {
        return s.replaceAll("\u001b]8;;[^\u001b]*\u001b\\\\", "");
    }

    @Test
    void wrapsToWidthWithoutLosingText() {
        for (int width : new int[] { 40, 60, 80, 200 }) {
            String out = strip(sample().renderBox(width, true));
            for (String line : out.split("\n")) {
                assertTrue(Table.length(line) <= width, width + ": " + line);
            }
            StringBuilder titleColumn = new StringBuilder();
            for (String line : out.split("\n")) {
                String[] cells = line.split("│");
                if (cells.length > 2) {
                    titleColumn.append(cells[2].trim());
                }
            }
            String flat = titleColumn.toString().replace(" ", "");
            assertTrue(flat.contains(LONG_TITLE.replace(" ", "")), width + ": text lost or reordered\n" + out);
        }
    }

    @Test
    void naturalWidthWhenItFits() {
        String out = sample().renderBox(200, false);
        assertTrue(out.contains(LONG_TITLE), out);
        assertEquals(7, out.split("\n").length); // top, header, separator, row, separator, row, bottom
    }

    @Test
    void osc8LinksDoNotCountTowardsWidth() {
        String withLinks = sample().renderBox(60, true);
        String without = sample().renderBox(60, false);
        assertTrue(withLinks.contains(Table.OSC8_START + "https://github.com/quarkusio/quarkus/pull/56633"));
        assertEquals(without, strip(withLinks));
    }

    @Test
    void longWordsSplitAtPunctuation() {
        assertEquals(List.of("graalvm/", "graalvm-", "jdk21u"), Table.wrap("graalvm/graalvm-jdk21u", 9));
        assertEquals(List.of("abcdefgh", "ij"), Table.wrap("abcdefghij", 8));
        assertEquals(List.of("one", "two"), Table.wrap("one\ntwo", 20));
    }

    @Test
    void markdownLinksAndEscapes() {
        String md = new Table(List.of("PR", "Title")).add(List.of(new Cell("a/b#1", "https://x/1"), Cell.of("a | b\nc")))
                .renderMarkdown();
        assertTrue(md.contains("| [a/b#1](https://x/1) | a \\| b<br>c |"), md);
    }
}
