package io.tracegraph.cli;

/** ANSI styling vocabulary for the CLI's visual language. */
final class Ansi {

    static final String RESET = "\u001B[0m";
    static final String BOLD = "\u001B[1m";
    static final String DIM = "\u001B[2m";
    static final String INVERSE = "\u001B[7m";
    static final String GREEN = "\u001B[32m";
    static final String RED = "\u001B[31m";
    static final String YELLOW = "\u001B[33m";
    static final String CYAN = "\u001B[36m";
    static final String MAGENTA = "\u001B[35m";
    static final String GRAY = "\u001B[90m";

    private Ansi() {}

    static String bold(String s) {
        return BOLD + s + RESET;
    }

    static String dim(String s) {
        return DIM + s + RESET;
    }

    static String color(String code, String s) {
        return code + s + RESET;
    }

    static String statusColor(String status) {
        return switch (status == null ? "" : status) {
            case "COMPLETED" -> GREEN;
            case "FAILED" -> RED;
            case "INTERRUPTED", "TERMINATED" -> YELLOW;
            default -> CYAN;
        };
    }

    /** Visible length of {@code s} with ANSI escapes stripped. */
    static int visibleLength(String s) {
        return strip(s).length();
    }

    static String strip(String s) {
        return s.replaceAll("\u001B\\[[0-9;]*m", "");
    }

    /** Pads {@code s} with spaces to {@code width} visible columns (truncating if longer). */
    static String padVisible(String s, int width) {
        int visible = visibleLength(s);
        if (visible > width) {
            return truncateVisible(s, width);
        }
        return s + " ".repeat(width - visible);
    }

    static String truncateVisible(String s, int width) {
        StringBuilder out = new StringBuilder();
        int visible = 0;
        int i = 0;
        while (i < s.length() && visible < width) {
            char c = s.charAt(i);
            if (c == '\u001B') {
                int end = s.indexOf('m', i);
                if (end < 0) break;
                out.append(s, i, end + 1);
                i = end + 1;
            } else {
                out.append(c);
                visible++;
                i++;
            }
        }
        return out + RESET;
    }
}
