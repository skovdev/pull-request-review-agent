package local.agent.pullrequestreviewagent.github;

import local.agent.pullrequestreviewagent.config.ReviewProperties;

import org.junit.jupiter.api.Test;

import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;

import java.nio.charset.StandardCharsets;

import java.nio.file.Path;
import java.nio.file.Files;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GitHubContentServiceTest {

    private final GitHubContentService gitHubContentService =
            new GitHubContentService(new ReviewProperties(6_000, 60_000, 8_000, 200, 50, 2_000, 20, 3, 300_000, false));

    @TempDir
    Path root;

    @Test
    void readFileReadsContentFromTheExtractedSnapshot() throws IOException {
        write("src/Foo.java", "class Foo {}");

        assertThat(gitHubContentService.readFile(root, "src/Foo.java")).isEqualTo("class Foo {}");
    }

    @Test
    void readFileReturnsNullWhenPathDoesNotExist() {
        assertThat(gitHubContentService.readFile(root, "missing.txt")).isNull();
    }

    @Test
    void readFileRefusesToEscapeTheRoot() throws IOException {
        write("a.txt", "a");

        assertThat(gitHubContentService.readFile(root, "../../etc/passwd")).isNull();
    }

    @Test
    void listFilesFiltersByDirectoryPrefix() throws IOException {
        write("src/main/Foo.java", "class Foo {}");
        write("src/test/FooTest.java", "class FooTest {}");
        write("README.md", "readme");

        List<String> mainFiles = gitHubContentService.listFiles(root, "src/main");

        assertThat(mainFiles).containsExactly("src/main/Foo.java");
    }

    @Test
    void listFilesWithEmptyDirectoryListsWholeTree() throws IOException {
        write("a.txt", "a");
        write("b.txt", "b");

        assertThat(gitHubContentService.listFiles(root, ""))
                .containsExactlyInAnyOrder("a.txt", "b.txt");
    }

    @Test
    void searchCodeFindsMatchesWithLineNumbers() throws IOException {
        write("src/Foo.java", "class Foo {\n    void bar() {}\n}\n");
        write("src/Other.java", "class Other {}\n");

        List<String> results = gitHubContentService.searchCode(root, "void bar");

        assertThat(results).hasSize(1);
        assertThat(results.get(0)).startsWith("src/Foo.java:2:");
    }

    @Test
    void searchCodeSkipsVendoredDirectories() throws IOException {
        write("node_modules/dep/index.js", "target text");
        write("src/Foo.java", "target text");

        List<String> results = gitHubContentService.searchCode(root, "target text");

        assertThat(results).extracting(result -> result.split(":")[0]).containsExactly("src/Foo.java");
    }

    private void write(String fileName, String content) throws IOException {
        Path filePath = root.resolve(fileName);
        Files.createDirectories(filePath.getParent());
        Files.writeString(filePath, content, StandardCharsets.UTF_8);
    }
}
