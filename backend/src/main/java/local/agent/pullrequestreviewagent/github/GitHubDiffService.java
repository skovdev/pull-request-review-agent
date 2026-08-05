package local.agent.pullrequestreviewagent.github;

import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Diffs two commits of a pull request via GitHub's compare API, which already resolves the
 * merge-base and detects renames/copies server-side, so no local clone or JGit-style diff
 * computation is needed.
 */
@Service
public class GitHubDiffService {

    private final GitHubClient gitHubClient;

    public GitHubDiffService(GitHubClient gitHubClient) {
        this.gitHubClient = gitHubClient;
    }

    public List<ChangedFile> diff(String owner, String repo, String baseSha, String headSha) {
        return gitHubClient.compare(owner, repo, baseSha, headSha).stream()
                .map(this::toChangedFile)
                .toList();
    }

    private ChangedFile toChangedFile(GitHubClient.CompareFile file) {
        ChangedFile.ChangeType changeType = mapStatus(file.status());
        String diff = file.patch() != null
                ? file.patch()
                : "(diff omitted: file too large or binary, not shown by GitHub)";
        return new ChangedFile(file.filename(), changeType, diff);
    }

    private ChangedFile.ChangeType mapStatus(String status) {
        return switch (status) {
            case "added" -> ChangedFile.ChangeType.ADDED;
            case "removed" -> ChangedFile.ChangeType.DELETED;
            case "renamed" -> ChangedFile.ChangeType.RENAMED;
            case "copied" -> ChangedFile.ChangeType.COPIED;
            default -> ChangedFile.ChangeType.MODIFIED;
        };
    }
}
