package local.agent.pullrequestreviewagent.git;

import local.agent.pullrequestreviewagent.config.ReviewProperties;

import org.eclipse.jgit.errors.MissingObjectException;

import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.ObjectLoader;

import org.eclipse.jgit.treewalk.TreeWalk;

import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.revwalk.RevCommit;

import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;

import java.nio.charset.StandardCharsets;

import java.nio.file.Path;
import java.nio.file.Files;

import java.util.List;
import java.util.ArrayList;

import java.util.regex.Pattern;

import java.util.stream.Stream;

/**
 * Lets the review agent look beyond the diff hunks it was handed: read a whole file,
 * browse a directory, or search for other usages of a changed symbol. {@code ref} is a
 * branch/commit name to read from git, or {@code null} to read the working tree directly
 * (uncommitted and untracked changes, mirroring {@link GitDiffService#diffWorkingTree}).
 */
@Service
public class GitContentService {

    private static final List<Pattern> SKIPPED_DIRECTORIES = List.of(
            Pattern.compile("(^|.*/)(\\.git|node_modules|dist|build|target|vendor)(/.*|$)"));

    private final int maxFileBytes;
    private final int maxListedEntries;
    private final int maxSearchResults;
    private final int maxFilesScanned;

    public GitContentService(ReviewProperties properties) {
        this.maxFileBytes = properties.maxFileReadBytes();
        this.maxListedEntries = properties.maxListedEntries();
        this.maxSearchResults = properties.maxSearchResults();
        this.maxFilesScanned = properties.maxFilesScanned();
    }

    public String readFile(Repository repository, String ref, String path) {
        return resolveContent(repository, ref, normalizePath(path));
    }

    public List<String> listFiles(Repository repository, String ref, String directory) {
        String prefix = normalizePath(directory);
        List<String> paths = resolvePaths(repository, ref);
        List<String> matches = new ArrayList<>();
        for (String path : paths) {
            if (prefix.isEmpty() || path.equals(prefix) || path.startsWith(prefix + "/")) {
                matches.add(path);
                if (matches.size() >= maxListedEntries) {
                    matches.add("... (more entries omitted)");
                    break;
                }
            }
        }
        return matches;
    }

    public List<String> searchCode(Repository repository, String ref, String query) {
        List<String> paths = resolvePaths(repository, ref);
        List<String> results = new ArrayList<>();
        int scanned = 0;
        for (String path : paths) {
            if (scanned++ >= maxFilesScanned || results.size() >= maxSearchResults) {
                break;
            }
            String content = resolveContent(repository, ref, path);
            if (content == null || content.startsWith("(binary")) {
                continue;
            }
            String[] lines = content.split("\n", -1);
            for (int i = 0; i < lines.length && results.size() < maxSearchResults; i++) {
                if (lines[i].contains(query)) {
                    results.add(path + ":" + (i + 1) + ": " + lines[i].strip());
                }
            }
        }
        return results;
    }

    private String resolveContent(Repository repository, String ref, String path) {
        return ref == null ? readFromWorkingTree(repository, path) : readFromCommit(repository, ref, path);
    }

    private List<String> resolvePaths(Repository repository, String ref) {
        return ref == null ? walkWorkingTree(repository) : walkCommit(repository, ref);
    }

    private String readFromWorkingTree(Repository repository, String path) {
        Path root = repository.getWorkTree().toPath().normalize();
        Path resolved = root.resolve(path).normalize();
        if (!resolved.startsWith(root) || !Files.isRegularFile(resolved)) {
            return null;
        }
        try {
            return toText(readBounded(Files.newInputStream(resolved)), Files.size(resolved));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private String readFromCommit(Repository repository, String ref, String path) {
        ObjectId commitId = GitRefs.resolve(repository, ref);
        try (RevWalk revWalk = new RevWalk(repository)) {
            RevCommit commit = revWalk.parseCommit(commitId);
            try (TreeWalk treeWalk = TreeWalk.forPath(repository, path, commit.getTree())) {
                if (treeWalk == null) {
                    return null;
                }
                ObjectLoader loader = repository.open(treeWalk.getObjectId(0), Constants.OBJ_BLOB);
                return toText(readBounded(loader.openStream()), loader.getSize());
            }
        } catch (MissingObjectException e) {
            return null;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private List<String> walkWorkingTree(Repository repository) {
        Path root = repository.getWorkTree().toPath().normalize();
        try (Stream<Path> stream = Files.walk(root)) {
            List<String> paths = new ArrayList<>();
            stream.filter(Files::isRegularFile).forEach(file -> {
                String relative = root.relativize(file).toString().replace('\\', '/');
                if (!isSkipped(relative)) {
                    paths.add(relative);
                }
            });
            return paths;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private List<String> walkCommit(Repository repository, String ref) {
        ObjectId commitId = GitRefs.resolve(repository, ref);
        List<String> paths = new ArrayList<>();
        try (RevWalk revWalk = new RevWalk(repository);
             TreeWalk treeWalk = new TreeWalk(repository)) {
            RevCommit commit = revWalk.parseCommit(commitId);
            treeWalk.addTree(commit.getTree());
            treeWalk.setRecursive(true);
            while (treeWalk.next()) {
                if (!isSkipped(treeWalk.getPathString())) {
                    paths.add(treeWalk.getPathString());
                }
            }
            return paths;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private boolean isSkipped(String path) {
        return SKIPPED_DIRECTORIES.stream().anyMatch(pattern -> pattern.matcher(path).matches());
    }

    private byte[] readBounded(InputStream in) throws IOException {
        try (in) {
            return in.readNBytes(maxFileBytes);
        }
    }

    private String toText(byte[] bytes, long totalSize) {
        if (isBinary(bytes)) {
            return "(binary file, contents not shown)";
        }
        String text = new String(bytes, StandardCharsets.UTF_8);
        if (totalSize > bytes.length) {
            text += "\n... (file truncated, showing first " + bytes.length + " of " + totalSize + " bytes)";
        }
        return text;
    }

    private boolean isBinary(byte[] bytes) {
        int checkLength = Math.min(bytes.length, 8_000);
        for (int i = 0; i < checkLength; i++) {
            if (bytes[i] == 0) {
                return true;
            }
        }
        return false;
    }

    private String normalizePath(String path) {
        String stripped = path == null ? "" : path.strip();
        while (stripped.startsWith("/")) {
            stripped = stripped.substring(1);
        }
        return stripped;
    }
}
