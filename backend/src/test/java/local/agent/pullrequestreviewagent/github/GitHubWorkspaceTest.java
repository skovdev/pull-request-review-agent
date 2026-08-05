package local.agent.pullrequestreviewagent.github;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.ByteArrayOutputStream;

import java.nio.charset.StandardCharsets;

import java.nio.file.Files;
import java.nio.file.Path;

import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class GitHubWorkspaceTest {

    private final GitHubClient gitHubClient = mock(GitHubClient.class);

    @Test
    void extractsFilesStrippingTheTopLevelArchiveDirectory() throws IOException {
        byte[] archive = zipOf(
                "acme-widgets-abc1234/src/Foo.java", "class Foo {}",
                "acme-widgets-abc1234/README.md", "readme");
        when(gitHubClient.downloadZipball("acme", "widgets", "sha1")).thenReturn(archive);

        try (GitHubWorkspace workspace = new GitHubWorkspace(gitHubClient, "acme", "widgets")) {
            Path root = workspace.rootFor("sha1");

            assertThat(Files.readString(root.resolve("src/Foo.java"))).isEqualTo("class Foo {}");
            assertThat(Files.readString(root.resolve("README.md"))).isEqualTo("readme");
        }
    }

    @Test
    void extractsEachShaOnlyOnce() throws IOException {
        byte[] archive = zipOf("acme-widgets-abc1234/a.txt", "a");
        when(gitHubClient.downloadZipball("acme", "widgets", "sha1")).thenReturn(archive);

        try (GitHubWorkspace workspace = new GitHubWorkspace(gitHubClient, "acme", "widgets")) {
            workspace.rootFor("sha1");
            workspace.rootFor("sha1");

            verify(gitHubClient, times(1)).downloadZipball("acme", "widgets", "sha1");
        }
    }

    @Test
    void ignoresZipEntriesThatWouldEscapeTheExtractionDirectory() throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (ZipOutputStream zipOut = new ZipOutputStream(buffer)) {
            zipOut.putNextEntry(new ZipEntry("acme-widgets-abc1234/../../etc/passwd"));
            zipOut.write("malicious".getBytes(StandardCharsets.UTF_8));
            zipOut.closeEntry();
            zipOut.putNextEntry(new ZipEntry("acme-widgets-abc1234/safe.txt"));
            zipOut.write("safe".getBytes(StandardCharsets.UTF_8));
            zipOut.closeEntry();
        }
        when(gitHubClient.downloadZipball("acme", "widgets", "sha1")).thenReturn(buffer.toByteArray());

        try (GitHubWorkspace workspace = new GitHubWorkspace(gitHubClient, "acme", "widgets")) {
            Path root = workspace.rootFor("sha1");

            assertThat(Files.readString(root.resolve("safe.txt"))).isEqualTo("safe");
            try (var listing = Files.walk(root)) {
                assertThat(listing.filter(Files::isRegularFile))
                        .extracting(root::relativize)
                        .extracting(Path::toString)
                        .containsExactly("safe.txt");
            }
        }
    }

    @Test
    void closeDeletesTheExtractedDirectory() throws IOException {
        byte[] archive = zipOf("acme-widgets-abc1234/a.txt", "a");
        when(gitHubClient.downloadZipball("acme", "widgets", "sha1")).thenReturn(archive);

        GitHubWorkspace workspace = new GitHubWorkspace(gitHubClient, "acme", "widgets");
        Path root = workspace.rootFor("sha1");
        workspace.close();

        assertThat(Files.exists(root)).isFalse();
    }

    private byte[] zipOf(String... nameContentPairs) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (ZipOutputStream zipOut = new ZipOutputStream(buffer)) {
            for (int i = 0; i < nameContentPairs.length; i += 2) {
                zipOut.putNextEntry(new ZipEntry(nameContentPairs[i]));
                zipOut.write(nameContentPairs[i + 1].getBytes(StandardCharsets.UTF_8));
                zipOut.closeEntry();
            }
        }
        return buffer.toByteArray();
    }
}
