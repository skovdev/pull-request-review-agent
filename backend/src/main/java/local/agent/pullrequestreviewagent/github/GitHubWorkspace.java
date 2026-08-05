package local.agent.pullrequestreviewagent.github;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.ByteArrayInputStream;

import java.nio.file.Path;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

import java.util.Map;
import java.util.Comparator;

import java.util.concurrent.ConcurrentHashMap;

import java.util.stream.Stream;

import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Materializes a source snapshot for either side of a review by downloading GitHub's zipball
 * archive for a commit SHA and extracting it into a per-review temp directory, so
 * {@link GitHubContentService} can read/list/search it as plain files rather than making one
 * API call per file. Extraction is lazy and memoized per SHA: a side the model never asks
 * about is never downloaded. One instance is created per review and discarded after, mirroring
 * how a JGit {@code Repository} used to be opened and closed per review.
 */
public class GitHubWorkspace implements AutoCloseable {

    private final GitHubClient gitHubClient;
    private final String owner;
    private final String repo;
    private final Path root;
    private final Map<String, Path> extractedRoots = new ConcurrentHashMap<>();

    public GitHubWorkspace(GitHubClient gitHubClient, String owner, String repo) {
        this.gitHubClient = gitHubClient;
        this.owner = owner;
        this.repo = repo;
        try {
            this.root = Files.createTempDirectory("pr-review-");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Returns the extraction directory for the given commit SHA, downloading and extracting
     * its zipball archive on first use.
     */
    public Path rootFor(String sha) {
        return extractedRoots.computeIfAbsent(sha, this::extract);
    }

    private Path extract(String sha) {
        byte[] archive = gitHubClient.downloadZipball(owner, repo, sha);
        Path dir = root.resolve(sha);
        try {
            Files.createDirectories(dir);
            extractInto(dir, archive);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to extract archive for " + owner + "/" + repo + "@" + sha, e);
        }
        return dir;
    }

    /**
     * GitHub zipballs wrap every entry in a single {@code {owner}-{repo}-{shortsha}/} directory;
     * that prefix is stripped so extracted paths are already repository-relative.
     */
    private void extractInto(Path dir, byte[] archive) throws IOException {
        try (ZipInputStream zipIn = new ZipInputStream(new ByteArrayInputStream(archive))) {
            ZipEntry entry;
            while ((entry = zipIn.getNextEntry()) != null) {
                String relative = stripTopLevelDirectory(entry.getName());
                if (relative.isEmpty()) {
                    continue;
                }
                Path target = dir.resolve(relative).normalize();
                if (!target.startsWith(dir)) {
                    continue;
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(target);
                } else {
                    Files.createDirectories(target.getParent());
                    Files.copy(zipIn, target, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    private String stripTopLevelDirectory(String entryName) {
        int slash = entryName.indexOf('/');
        return slash < 0 ? "" : entryName.substring(slash + 1);
    }

    @Override
    public void close() {
        try (Stream<Path> paths = Files.walk(root)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                    // best-effort cleanup of a temp directory
                }
            });
        } catch (IOException ignored) {
            // best-effort cleanup of a temp directory
        }
    }
}
