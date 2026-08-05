package local.agent.pullrequestreviewagent.github;

import local.agent.pullrequestreviewagent.config.ReviewProperties;

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
 * Lets the review agent look beyond the diff hunks it was handed: read a whole file, browse a
 * directory, or search for other usages of a changed symbol. {@code root} is the directory a
 * {@link GitHubWorkspace} extracted a commit's zipball into, so this operates on plain files
 * with no further network calls.
 */
@Service
public class GitHubContentService {

    private static final List<Pattern> SKIPPED_DIRECTORIES = List.of(
            Pattern.compile("(^|.*/)(node_modules|dist|build|target|vendor)(/.*|$)"));

    private final int maxFileBytes;
    private final int maxListedEntries;
    private final int maxSearchResults;
    private final int maxFilesScanned;

    public GitHubContentService(ReviewProperties properties) {
        this.maxFileBytes = properties.maxFileReadBytes();
        this.maxListedEntries = properties.maxListedEntries();
        this.maxSearchResults = properties.maxSearchResults();
        this.maxFilesScanned = properties.maxFilesScanned();
    }

    public String readFile(Path root, String path) {
        Path resolved = resolve(root, path);
        if (resolved == null) {
            return null;
        }
        try {
            return toText(readBounded(Files.newInputStream(resolved)), Files.size(resolved));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public List<String> listFiles(Path root, String directory) {
        String prefix = normalizePath(directory);
        List<String> paths = walk(root);
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

    public List<String> searchCode(Path root, String query) {
        List<String> paths = walk(root);
        List<String> results = new ArrayList<>();
        int scanned = 0;
        for (String path : paths) {
            if (scanned++ >= maxFilesScanned || results.size() >= maxSearchResults) {
                break;
            }
            String content = readFile(root, path);
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

    private Path resolve(Path root, String path) {
        Path normalizedRoot = root.normalize();
        Path resolved = normalizedRoot.resolve(normalizePath(path)).normalize();
        if (!resolved.startsWith(normalizedRoot) || !Files.isRegularFile(resolved)) {
            return null;
        }
        return resolved;
    }

    private List<String> walk(Path root) {
        Path normalizedRoot = root.normalize();
        try (Stream<Path> stream = Files.walk(normalizedRoot)) {
            List<String> paths = new ArrayList<>();
            stream.filter(Files::isRegularFile).forEach(file -> {
                String relative = normalizedRoot.relativize(file).toString().replace('\\', '/');
                if (!isSkipped(relative)) {
                    paths.add(relative);
                }
            });
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
