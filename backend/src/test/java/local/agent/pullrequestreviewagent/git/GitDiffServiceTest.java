package local.agent.pullrequestreviewagent.git;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.Repository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GitDiffServiceTest {

    private final GitDiffService gitDiffService = new GitDiffService();

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
    void diffIgnoresCommitsThatLandedOnBaseAfterTheReviewBranchWasCut() throws Exception {
        writeAndCommit("a.txt", "a", "initial commit");
        git.branchCreate().setName("feature").call();

        checkout("feature");
        writeAndCommit("b.txt", "b", "add b on feature");

        checkout(mainBranch);
        writeAndCommit("c.txt", "c", "add c on main after branching");

        List<ChangedFile> changedFiles = gitDiffService.diff(repository, mainBranch, "feature");

        assertThat(changedFiles).extracting(ChangedFile::path).containsExactly("b.txt");
    }

    @Test
    void diffWorkingTreePicksUpUncommittedAndUntrackedChanges() throws Exception {
        writeAndCommit("a.txt", "a", "initial commit");
        git.branchCreate().setName("feature").call();
        checkout("feature");

        // uncommitted, untracked file - never staged or committed
        Files.writeString(repoDir.resolve("d.txt"), "d", StandardCharsets.UTF_8);

        List<ChangedFile> changedFiles = gitDiffService.diffWorkingTree(repository, mainBranch);

        assertThat(changedFiles).extracting(ChangedFile::path).containsExactly("d.txt");
        assertThat(changedFiles.get(0).changeType()).isEqualTo(ChangedFile.ChangeType.ADDED);
    }

    @Test
    void diffDetectsExactRenameBetweenBranches() throws Exception {
        String content = "duplicate detection needs content long enough to be recognized reliably as a rename\n";
        writeAndCommit("orig.txt", content, "add orig");
        git.branchCreate().setName("feature").call();
        checkout("feature");
        renameFile("orig.txt", "renamed.txt", content, "rename orig to renamed");

        List<ChangedFile> changedFiles = gitDiffService.diff(repository, mainBranch, "feature");

        assertThat(changedFiles).hasSize(1);
        assertThat(changedFiles.get(0).path()).isEqualTo("renamed.txt");
        assertThat(changedFiles.get(0).changeType()).isEqualTo(ChangedFile.ChangeType.RENAMED);
        assertThat(changedFiles.get(0).diff())
                .contains("rename from orig.txt")
                .contains("rename to renamed.txt");
    }

    @Test
    void diffDetectsCopyWhenMultipleAddsMatchOneDelete() throws Exception {
        String content = "shared content used to validate copy detection semantics\n";
        writeAndCommit("orig.txt", content, "add orig");
        git.branchCreate().setName("feature").call();
        checkout("feature");

        git.rm().addFilepattern("orig.txt").call();
        Files.writeString(repoDir.resolve("copy1.txt"), content, StandardCharsets.UTF_8);
        Files.writeString(repoDir.resolve("copy2.txt"), content, StandardCharsets.UTF_8);
        git.add().addFilepattern("copy1.txt").call();
        git.add().addFilepattern("copy2.txt").call();
        git.commit().setMessage("split orig into two copies").setSign(false).call();

        List<ChangedFile> changedFiles = gitDiffService.diff(repository, mainBranch, "feature");

        assertThat(changedFiles).extracting(ChangedFile::changeType)
                .containsExactlyInAnyOrder(ChangedFile.ChangeType.RENAMED, ChangedFile.ChangeType.COPIED);
    }

    @Test
    void diffWorkingTreeDetectsRenameWithUncommittedEdits() throws Exception {
        // Renaming AND editing the file only in the working tree (never staged or committed)
        // exercises content-based rename matching against on-disk content that was never
        // written to the git object database.
        String original = "line one\nline two\nline three\nline four\nline five\n";
        writeAndCommit("orig.txt", original, "add orig");

        Files.delete(repoDir.resolve("orig.txt"));
        Files.writeString(repoDir.resolve("renamed.txt"), original + "line six\n", StandardCharsets.UTF_8);

        List<ChangedFile> changedFiles = gitDiffService.diffWorkingTree(repository, mainBranch);

        assertThat(changedFiles).hasSize(1);
        assertThat(changedFiles.get(0).path()).isEqualTo("renamed.txt");
        assertThat(changedFiles.get(0).changeType()).isEqualTo(ChangedFile.ChangeType.RENAMED);
        assertThat(changedFiles.get(0).diff())
                .contains("rename from orig.txt")
                .contains("rename to renamed.txt");
    }

    private void renameFile(String oldName, String newName, String content, String message) throws Exception {
        git.rm().addFilepattern(oldName).call();
        Files.writeString(repoDir.resolve(newName), content, StandardCharsets.UTF_8);
        git.add().addFilepattern(newName).call();
        git.commit().setMessage(message).setSign(false).call();
    }

    private void checkout(String branch) throws Exception {
        git.checkout().setName(branch).call();
    }

    private void writeAndCommit(String fileName, String content, String message) throws IOException {
        try {
            Files.writeString(repoDir.resolve(fileName), content, StandardCharsets.UTF_8);
            git.add().addFilepattern(fileName).call();
            git.commit().setMessage(message).setSign(false).call();
        } catch (Exception e) {
            throw new IOException(e);
        }
    }
}
