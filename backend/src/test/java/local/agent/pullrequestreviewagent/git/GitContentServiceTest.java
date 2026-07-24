package local.agent.pullrequestreviewagent.git;

import local.agent.pullrequestreviewagent.config.ReviewProperties;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.Repository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;

import java.nio.charset.StandardCharsets;

import java.nio.file.Path;
import java.nio.file.Files;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GitContentServiceTest {

    private final GitContentService gitContentService =
            new GitContentService(new ReviewProperties(6_000, 60_000, 8_000, 200, 50, 2_000, 20, 3, 300_000));

    @TempDir
    Path repoDir;

    private Git git;
    private Repository repository;
    private String mainBranch;

    @BeforeEach
    void setUp() throws Exception {
        git = Git.init().setDirectory(repoDir.toFile()).call();
        repository = git.getRepository();
        mainBranch = repository.getBranch();
    }

    @AfterEach
    void tearDown() {
        git.close();
    }

    @Test
    void readFileReadsContentFromACommittedRef() throws Exception {
        writeAndCommit("src/Foo.java", "class Foo {}", "add Foo");

        String content = gitContentService.readFile(repository, mainBranch, "src/Foo.java");

        assertThat(content).isEqualTo("class Foo {}");
    }

    @Test
    void readFileReturnsNullWhenPathDoesNotExistOnThatRef() throws Exception {
        writeAndCommit("a.txt", "a", "initial commit");

        assertThat(gitContentService.readFile(repository, mainBranch, "missing.txt")).isNull();
    }

    @Test
    void readFileWithNullRefReadsUncommittedWorkingTreeChanges() throws Exception {
        writeAndCommit("a.txt", "original", "initial commit");
        Files.writeString(repoDir.resolve("a.txt"), "edited but not committed", StandardCharsets.UTF_8);

        assertThat(gitContentService.readFile(repository, mainBranch, "a.txt"))
                .isEqualTo("original");
        assertThat(gitContentService.readFile(repository, null, "a.txt"))
                .isEqualTo("edited but not committed");
    }

    @Test
    void readFileRefusesToEscapeTheWorkingTreeRoot() throws Exception {
        writeAndCommit("a.txt", "a", "initial commit");

        assertThat(gitContentService.readFile(repository, null, "../../etc/passwd")).isNull();
    }

    @Test
    void listFilesFiltersByDirectoryPrefix() throws Exception {
        writeAndCommit("src/main/Foo.java", "class Foo {}", "add Foo");
        writeAndCommit("src/test/FooTest.java", "class FooTest {}", "add FooTest");
        writeAndCommit("README.md", "readme", "add readme");

        List<String> mainFiles = gitContentService.listFiles(repository, mainBranch, "src/main");

        assertThat(mainFiles).containsExactly("src/main/Foo.java");
    }

    @Test
    void listFilesWithEmptyDirectoryListsWholeTree() throws Exception {
        writeAndCommit("a.txt", "a", "initial commit");
        writeAndCommit("b.txt", "b", "add b");

        assertThat(gitContentService.listFiles(repository, mainBranch, ""))
                .containsExactlyInAnyOrder("a.txt", "b.txt");
    }

    @Test
    void searchCodeFindsMatchesWithLineNumbers() throws Exception {
        writeAndCommit("src/Foo.java", "class Foo {\n    void bar() {}\n}\n", "add Foo");
        writeAndCommit("src/Other.java", "class Other {}\n", "add Other");

        List<String> results = gitContentService.searchCode(repository, mainBranch, "void bar");

        assertThat(results).hasSize(1);
        assertThat(results.get(0)).startsWith("src/Foo.java:2:");
    }

    @Test
    void searchCodeSkipsVendoredDirectories() throws Exception {
        writeAndCommit("node_modules/dep/index.js", "target text", "add dependency");
        writeAndCommit("src/Foo.java", "target text", "add Foo");

        List<String> results = gitContentService.searchCode(repository, mainBranch, "target text");

        assertThat(results).extracting(result -> result.split(":")[0]).containsExactly("src/Foo.java");
    }

    private void writeAndCommit(String fileName, String content, String message) throws IOException {
        try {
            Path filePath = repoDir.resolve(fileName);
            Files.createDirectories(filePath.getParent());
            Files.writeString(filePath, content, StandardCharsets.UTF_8);
            git.add().addFilepattern(fileName).call();
            git.commit().setMessage(message).setSign(false).call();
        } catch (Exception e) {
            throw new IOException(e);
        }
    }
}
