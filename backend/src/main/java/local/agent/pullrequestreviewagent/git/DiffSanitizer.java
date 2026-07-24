package local.agent.pullrequestreviewagent.git;

import local.agent.pullrequestreviewagent.config.ReviewProperties;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Keeps the diff sent to the AI model within a sane size and free of noise:
 * dependency lockfiles, build/vendor output and binary files are rarely worth
 * reviewing and just burn tokens, and an unbounded diff can silently exceed
 * the model's context window.
 */
@Component
public class DiffSanitizer {

    private static final List<Pattern> NOISE_PATH_PATTERNS = List.of(
            Pattern.compile(".*/?package-lock\\.json$"),
            Pattern.compile(".*/?npm-shrinkwrap\\.json$"),
            Pattern.compile(".*/?yarn\\.lock$"),
            Pattern.compile(".*/?pnpm-lock\\.yaml$"),
            Pattern.compile(".*/?Gemfile\\.lock$"),
            Pattern.compile(".*/?poetry\\.lock$"),
            Pattern.compile(".*/?Cargo\\.lock$"),
            Pattern.compile(".*/?go\\.sum$"),
            Pattern.compile(".*\\.min\\.(js|css)$"),
            Pattern.compile(".*\\.map$"),
            Pattern.compile("(^|.*/)(node_modules|dist|build|target|vendor)/.*")
    );

    private final int maxCharsPerFile;
    private final int maxTotalChars;

    public DiffSanitizer(ReviewProperties properties) {
        this.maxCharsPerFile = properties.maxDiffCharsPerFile();
        this.maxTotalChars = properties.maxDiffTotalChars();
    }

    public List<ChangedFile> sanitize(List<ChangedFile> changedFiles) {
        List<ChangedFile> result = new ArrayList<>();
        int remainingBudget = maxTotalChars;

        for (ChangedFile file : changedFiles) {
            if (isNoise(file.path())) {
                result.add(withDiff(file, "(diff omitted: dependency lockfile or build artifact)"));
                continue;
            }
            if (isBinary(file.diff())) {
                result.add(withDiff(file, "(binary file changed)"));
                continue;
            }

            String diff = truncate(file.diff());
            if (diff.length() > remainingBudget) {
                result.add(withDiff(file, "(diff omitted: total diff size budget exceeded)"));
                continue;
            }

            remainingBudget -= diff.length();
            result.add(withDiff(file, diff));
        }

        return result;
    }

    private boolean isNoise(String path) {
        return NOISE_PATH_PATTERNS.stream().anyMatch(pattern -> pattern.matcher(path).matches());
    }

    private boolean isBinary(String diff) {
        return diff.contains("Binary files differ");
    }

    private String truncate(String diff) {
        if (diff.length() <= maxCharsPerFile) {
            return diff;
        }
        int omitted = diff.length() - maxCharsPerFile;
        return diff.substring(0, maxCharsPerFile) + "\n... (diff truncated, " + omitted + " more characters)";
    }

    private ChangedFile withDiff(ChangedFile file, String diff) {
        return new ChangedFile(file.path(), file.changeType(), diff);
    }
}
