package local.agent.pullrequestreviewagent.review;

import local.agent.pullrequestreviewagent.agent.PullRequestReviewAgent;

import local.agent.pullrequestreviewagent.git.ChangedFile;
import local.agent.pullrequestreviewagent.git.DiffSanitizer;
import local.agent.pullrequestreviewagent.git.GitDiffService;
import local.agent.pullrequestreviewagent.git.GitRepositoryService;

import local.agent.pullrequestreviewagent.progress.ReviewProgressPublisher;

import local.agent.pullrequestreviewagent.tools.RepositoryTools;
import local.agent.pullrequestreviewagent.tools.RepositoryToolsFactory;

import org.eclipse.jgit.lib.Repository;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class ReviewServiceTest {

    private final GitRepositoryService gitRepositoryService = mock(GitRepositoryService.class);
    private final GitDiffService gitDiffService = mock(GitDiffService.class);
    private final DiffSanitizer diffSanitizer = mock(DiffSanitizer.class);
    private final PullRequestReviewAgent reviewAgent = mock(PullRequestReviewAgent.class);
    private final RepositoryToolsFactory repositoryToolsFactory = mock(RepositoryToolsFactory.class);
    private final Repository repository = mock(Repository.class);

    private final ReviewService reviewService = new ReviewService(
            gitRepositoryService, gitDiffService, diffSanitizer, reviewAgent, repositoryToolsFactory);

    @Test
    void rejectsABlankRepositoryPath() {
        assertThatThrownBy(() -> reviewService.review(" ", "main", "feature", ReviewProgressPublisher.NO_OP))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("repositoryPath");
    }

    @Test
    void rejectsABlankBaseBranch() {
        assertThatThrownBy(() -> reviewService.review("/repo", " ", "feature", ReviewProgressPublisher.NO_OP))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("baseBranch");
    }

    @Test
    void returnsApproveWithNoFindingsWhenThereAreNoDifferences() {
        when(gitRepositoryService.openRepository("/repo")).thenReturn(repository);
        when(gitDiffService.diff(repository, "main", "feature")).thenReturn(List.of());

        ReviewResult result = reviewService.review("/repo", "main", "feature", ReviewProgressPublisher.NO_OP);

        assertThat(result.recommendation()).isEqualTo(Recommendation.APPROVE);
        assertThat(result.findings()).isEmpty();
        assertThat(result.summary()).contains("No differences");
        verify(diffSanitizer, never()).sanitize(any());
        verify(reviewAgent, never()).review(any(), any(), any(), any());
    }

    @Test
    void reviewsTheWorkingTreeWhenReviewBranchIsBlank() {
        ChangedFile changedFile = new ChangedFile("Foo.java", ChangedFile.ChangeType.MODIFIED, "diff");
        RepositoryTools tools = mock(RepositoryTools.class);
        ReviewResult expected = new ReviewResult("summary", Recommendation.COMMENT, List.of());

        when(gitRepositoryService.openRepository("/repo")).thenReturn(repository);
        when(gitDiffService.diffWorkingTree(repository, "main")).thenReturn(List.of(changedFile));
        when(diffSanitizer.sanitize(List.of(changedFile))).thenReturn(List.of(changedFile));
        when(repositoryToolsFactory.create(eq(repository), eq("main"), isNull(), any())).thenReturn(tools);
        when(reviewAgent.review(eq("main"), any(), eq(List.of(changedFile)), eq(tools))).thenReturn(expected);

        ReviewResult result = reviewService.review("/repo", "main", "  ", ReviewProgressPublisher.NO_OP);

        assertThat(result).isEqualTo(expected);
        verify(gitDiffService, never()).diff(any(), any(), any());
    }

    @Test
    void reviewsTheGivenBranchWhenReviewBranchIsProvided() {
        ChangedFile changedFile = new ChangedFile("Foo.java", ChangedFile.ChangeType.MODIFIED, "diff");
        RepositoryTools tools = mock(RepositoryTools.class);
        ReviewResult expected = new ReviewResult("summary", Recommendation.APPROVE, List.of());

        when(gitRepositoryService.openRepository("/repo")).thenReturn(repository);
        when(gitDiffService.diff(repository, "main", "feature")).thenReturn(List.of(changedFile));
        when(diffSanitizer.sanitize(List.of(changedFile))).thenReturn(List.of(changedFile));
        when(repositoryToolsFactory.create(eq(repository), eq("main"), eq("feature"), any())).thenReturn(tools);
        when(reviewAgent.review(eq("main"), eq("feature"), eq(List.of(changedFile)), eq(tools))).thenReturn(expected);

        ReviewResult result = reviewService.review("/repo", "main", "feature", ReviewProgressPublisher.NO_OP);

        assertThat(result).isEqualTo(expected);
        verify(gitDiffService, never()).diffWorkingTree(any(), any());
    }
}
