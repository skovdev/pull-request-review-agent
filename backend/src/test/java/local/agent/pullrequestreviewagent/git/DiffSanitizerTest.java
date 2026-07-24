package local.agent.pullrequestreviewagent.git;

import local.agent.pullrequestreviewagent.config.ReviewProperties;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.ArrayList;

import static org.assertj.core.api.Assertions.assertThat;

class DiffSanitizerTest {

    private final DiffSanitizer diffSanitizer =
            new DiffSanitizer(new ReviewProperties(6_000, 60_000, 8_000, 200, 50, 2_000, 20, 3, 300_000));

    @Test
    void omitsLockfileDiffs() {
        ChangedFile lockfile = new ChangedFile("package-lock.json", ChangedFile.ChangeType.MODIFIED, "huge diff content");

        List<ChangedFile> result = diffSanitizer.sanitize(List.of(lockfile));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).diff()).contains("omitted");
        assertThat(result.get(0).path()).isEqualTo("package-lock.json");
    }

    @Test
    void replacesBinaryFileDiffs() {
        ChangedFile image = new ChangedFile("logo.png", ChangedFile.ChangeType.ADDED,
                "diff --git a/logo.png b/logo.png\nBinary files differ");

        List<ChangedFile> result = diffSanitizer.sanitize(List.of(image));

        assertThat(result.get(0).diff()).isEqualTo("(binary file changed)");
    }

    @Test
    void truncatesOversizedSingleFileDiffs() {
        String hugeDiff = "x".repeat(10_000);
        ChangedFile file = new ChangedFile("Big.java", ChangedFile.ChangeType.MODIFIED, hugeDiff);

        List<ChangedFile> result = diffSanitizer.sanitize(List.of(file));

        assertThat(result.get(0).diff()).hasSizeLessThan(hugeDiff.length());
        assertThat(result.get(0).diff()).contains("truncated");
    }

    @Test
    void dropsLaterFilesOnceTotalBudgetIsExceeded() {
        // Each diff sits exactly at the per-file cap (so it isn't truncated) and
        // ten of them exactly exhaust the total budget, forcing the 11th out.
        String maxSizedDiff = "x".repeat(6_000);
        List<ChangedFile> files = new ArrayList<>();
        for (int i = 0; i < 11; i++) {
            files.add(new ChangedFile("File" + i + ".java", ChangedFile.ChangeType.MODIFIED, maxSizedDiff));
        }

        List<ChangedFile> result = diffSanitizer.sanitize(files);

        for (int i = 0; i < 10; i++) {
            assertThat(result.get(i).diff()).isEqualTo(maxSizedDiff);
        }
        assertThat(result.get(10).diff()).contains("budget exceeded");
    }

    @Test
    void passesThroughNormalDiffsUnchanged() {
        ChangedFile file = new ChangedFile("Service.java", ChangedFile.ChangeType.MODIFIED, "@@ -1,3 +1,4 @@\n+new line");

        List<ChangedFile> result = diffSanitizer.sanitize(List.of(file));

        assertThat(result.get(0).diff()).isEqualTo("@@ -1,3 +1,4 @@\n+new line");
    }
}
