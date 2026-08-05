package local.agent.pullrequestreviewagent.github;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GitHubDiffServiceTest {

    private final GitHubClient gitHubClient = mock(GitHubClient.class);
    private final GitHubDiffService gitHubDiffService = new GitHubDiffService(gitHubClient);

    @Test
    void mapsEachGitHubStatusToAChangeType() {
        when(gitHubClient.compare("acme", "widgets", "base-sha", "head-sha")).thenReturn(List.of(
                new GitHubClient.CompareFile("Added.java", "added", null, "@@ added @@"),
                new GitHubClient.CompareFile("Removed.java", "removed", null, "@@ removed @@"),
                new GitHubClient.CompareFile("Modified.java", "modified", null, "@@ modified @@"),
                new GitHubClient.CompareFile("Renamed.java", "renamed", "Old.java", "@@ renamed @@"),
                new GitHubClient.CompareFile("Copied.java", "copied", "Source.java", "@@ copied @@")
        ));

        List<ChangedFile> result = gitHubDiffService.diff("acme", "widgets", "base-sha", "head-sha");

        assertThat(result).extracting(ChangedFile::path)
                .containsExactly("Added.java", "Removed.java", "Modified.java", "Renamed.java", "Copied.java");
        assertThat(result).extracting(ChangedFile::changeType)
                .containsExactly(
                        ChangedFile.ChangeType.ADDED,
                        ChangedFile.ChangeType.DELETED,
                        ChangedFile.ChangeType.MODIFIED,
                        ChangedFile.ChangeType.RENAMED,
                        ChangedFile.ChangeType.COPIED);
    }

    @Test
    void fallsBackToAPlaceholderWhenGitHubOmitsThePatch() {
        when(gitHubClient.compare("acme", "widgets", "base-sha", "head-sha")).thenReturn(List.of(
                new GitHubClient.CompareFile("Huge.java", "modified", null, null)
        ));

        List<ChangedFile> result = gitHubDiffService.diff("acme", "widgets", "base-sha", "head-sha");

        assertThat(result.get(0).diff()).contains("omitted");
    }
}
