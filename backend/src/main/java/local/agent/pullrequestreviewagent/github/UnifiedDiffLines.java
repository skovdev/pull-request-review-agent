package local.agent.pullrequestreviewagent.github;

import java.util.HashSet;
import java.util.Set;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Determines which line numbers on the new-file side of a unified diff GitHub will actually let
 * a pull request review comment anchor to. GitHub rejects (422) any inline comment whose line
 * isn't part of a hunk in the diff it computed, so this must be checked against the same patch
 * text GitHub returned rather than assumed from the file's full contents.
 */
public final class UnifiedDiffLines {

    private static final Pattern HUNK_HEADER = Pattern.compile("^@@ -\\d+(?:,\\d+)? \\+(\\d+)(?:,\\d+)? @@.*$");

    private UnifiedDiffLines() {
    }

    /**
     * @return the new-file line numbers that appear inside a hunk of {@code diff} — both added
     * ({@code +}) and unchanged context ({@code ' '}) lines qualify, matching what GitHub's diff
     * view lets a reviewer click to comment on. Empty if {@code diff} is null, has no hunks, or
     * is one of this pipeline's own placeholder strings for an omitted/binary diff.
     */
    public static Set<Integer> commentableNewLines(String diff) {
        Set<Integer> lines = new HashSet<>();
        if (diff == null) {
            return lines;
        }

        int newLine = 0;
        boolean inHunk = false;
        for (String line : diff.split("\n", -1)) {
            Matcher header = HUNK_HEADER.matcher(line);
            if (header.matches()) {
                newLine = Integer.parseInt(header.group(1));
                inHunk = true;
                continue;
            }
            if (!inHunk || line.isEmpty()) {
                continue;
            }
            switch (line.charAt(0)) {
                case '+', ' ' -> lines.add(newLine++);
                case '-' -> {
                    // old-side-only line; doesn't exist on the new side, so the new-file
                    // line counter doesn't advance
                }
                case '\\' -> {
                    // "\ No newline at end of file" marker
                }
                default -> inHunk = false; // not a recognized diff line; stop trusting hunk state
            }
        }
        return lines;
    }
}
