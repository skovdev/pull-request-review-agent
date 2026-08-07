package local.agent.pullrequestreviewagent.review;

import local.agent.pullrequestreviewagent.agent.PullRequestReviewAgent;

import local.agent.pullrequestreviewagent.config.ReviewProperties;

import local.agent.pullrequestreviewagent.github.ChangedFile;
import local.agent.pullrequestreviewagent.github.GitHubClient;
import local.agent.pullrequestreviewagent.github.DiffSanitizer;
import local.agent.pullrequestreviewagent.github.GitHubWorkspace;
import local.agent.pullrequestreviewagent.github.GitHubDiffService;
import local.agent.pullrequestreviewagent.github.PullRequestContext;
import local.agent.pullrequestreviewagent.github.GitHubWorkspaceFactory;

import local.agent.pullrequestreviewagent.progress.ReviewProgressPublisher;

import local.agent.pullrequestreviewagent.tools.RepositoryTools;
import local.agent.pullrequestreviewagent.tools.RepositoryToolsFactory;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class ReviewServiceTest {

    private static final ReviewProperties PROPERTIES_WITH_POSTING_DISABLED =
            new ReviewProperties(6_000, 60_000, 8_000, 200, 50, 2_000, 20, 3, 300_000, false);
    private static final ReviewProperties PROPERTIES_WITH_POSTING_ENABLED =
            new ReviewProperties(6_000, 60_000, 8_000, 200, 50, 2_000, 20, 3, 300_000, true);

    private final GitHubClient gitHubClient = mock(GitHubClient.class);
    private final GitHubWorkspaceFactory gitHubWorkspaceFactory = mock(GitHubWorkspaceFactory.class);
    private final GitHubDiffService gitHubDiffService = mock(GitHubDiffService.class);
    private final DiffSanitizer diffSanitizer = mock(DiffSanitizer.class);
    private final PullRequestReviewAgent reviewAgent = mock(PullRequestReviewAgent.class);
    private final RepositoryToolsFactory repositoryToolsFactory = mock(RepositoryToolsFactory.class);
    private final GitHubReviewPublisher gitHubReviewPublisher = mock(GitHubReviewPublisher.class);
    private final GitHubWorkspace workspace = mock(GitHubWorkspace.class);

    private final ReviewService reviewService = new ReviewService(
            gitHubClient, gitHubWorkspaceFactory, gitHubDiffService, diffSanitizer, reviewAgent,
            repositoryToolsFactory, gitHubReviewPublisher, PROPERTIES_WITH_POSTING_DISABLED);

    @Test
    void rejectsABlankOwner() {
        assertThatThrownBy(() -> reviewService.review(" ", "repo", 1, ReviewProgressPublisher.NO_OP))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("owner");
    }

    @Test
    void rejectsABlankRepo() {
        assertThatThrownBy(() -> reviewService.review("owner", " ", 1, ReviewProgressPublisher.NO_OP))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("repo");
    }

    @Test
    void rejectsANonPositivePullNumber() {
        assertThatThrownBy(() -> reviewService.review("owner", "repo", 0, ReviewProgressPublisher.NO_OP))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("pullNumber");
    }

    @Test
    void returnsApproveWithNoFindingsWhenThereAreNoDifferences() {
        PullRequestContext pullRequest = new PullRequestContext("main", "base-sha", "feature", "head-sha");
        when(gitHubClient.getPullRequest("owner", "repo", 42)).thenReturn(pullRequest);
        when(gitHubWorkspaceFactory.create("owner", "repo")).thenReturn(workspace);
        when(gitHubDiffService.diff("owner", "repo", "base-sha", "head-sha")).thenReturn(List.of());

        ReviewResult result = reviewService.review("owner", "repo", 42, ReviewProgressPublisher.NO_OP);

        assertThat(result.recommendation()).isEqualTo(Recommendation.APPROVE);
        assertThat(result.findings()).isEmpty();
        assertThat(result.summary()).contains("No differences");
        verify(diffSanitizer, never()).sanitize(any());
        verify(reviewAgent, never()).review(any(), any(), any(), any());
    }

    @Test
    void reviewsThePullRequestAndClosesTheWorkspaceAfterward() {
        ChangedFile changedFile = new ChangedFile("Foo.java", ChangedFile.ChangeType.MODIFIED, "diff");
        PullRequestContext pullRequest = new PullRequestContext("main", "base-sha", "feature", "head-sha");
        RepositoryTools tools = mock(RepositoryTools.class);
        ReviewResult expected = new ReviewResult("summary", Recommendation.COMMENT, List.of());

        when(gitHubClient.getPullRequest("owner", "repo", 42)).thenReturn(pullRequest);
        when(gitHubWorkspaceFactory.create("owner", "repo")).thenReturn(workspace);
        when(gitHubDiffService.diff("owner", "repo", "base-sha", "head-sha")).thenReturn(List.of(changedFile));
        when(diffSanitizer.sanitize(List.of(changedFile))).thenReturn(List.of(changedFile));
        when(repositoryToolsFactory.create(eq(workspace), eq("base-sha"), eq("head-sha"), any())).thenReturn(tools);
        when(reviewAgent.review(eq("main"), eq("feature"), eq(List.of(changedFile)), eq(tools))).thenReturn(expected);

        ReviewResult result = reviewService.review("owner", "repo", 42, ReviewProgressPublisher.NO_OP);

        assertThat(result).isEqualTo(expected);
        verify(workspace).close();
        verify(gitHubReviewPublisher, never()).publish(any(), any(), anyInt(), any(), any(), any(), any());
    }

    @Test
    void postsTheReviewToGitHubWhenPostingIsEnabled() {
        ChangedFile changedFile = new ChangedFile("Foo.java", ChangedFile.ChangeType.MODIFIED, "diff");
        PullRequestContext pullRequest = new PullRequestContext("main", "base-sha", "feature", "head-sha");
        RepositoryTools tools = mock(RepositoryTools.class);
        ReviewResult expected = new ReviewResult("summary", Recommendation.COMMENT, List.of());

        ReviewService reviewServiceWithPostingEnabled = new ReviewService(
                gitHubClient, gitHubWorkspaceFactory, gitHubDiffService, diffSanitizer, reviewAgent,
                repositoryToolsFactory, gitHubReviewPublisher, PROPERTIES_WITH_POSTING_ENABLED);

        when(gitHubClient.getPullRequest("owner", "repo", 42)).thenReturn(pullRequest);
        when(gitHubWorkspaceFactory.create("owner", "repo")).thenReturn(workspace);
        when(gitHubDiffService.diff("owner", "repo", "base-sha", "head-sha")).thenReturn(List.of(changedFile));
        when(diffSanitizer.sanitize(List.of(changedFile))).thenReturn(List.of(changedFile));
        when(repositoryToolsFactory.create(eq(workspace), eq("base-sha"), eq("head-sha"), any())).thenReturn(tools);
        when(reviewAgent.review(eq("main"), eq("feature"), eq(List.of(changedFile)), eq(tools))).thenReturn(expected);

        reviewServiceWithPostingEnabled.review("owner", "repo", 42, ReviewProgressPublisher.NO_OP);

        verify(gitHubReviewPublisher).publish(
                "owner", "repo", 42, "head-sha", List.of(changedFile), expected, ReviewProgressPublisher.NO_OP);
    }
}
