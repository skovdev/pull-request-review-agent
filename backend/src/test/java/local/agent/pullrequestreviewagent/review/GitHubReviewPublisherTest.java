package local.agent.pullrequestreviewagent.review;

import local.agent.pullrequestreviewagent.github.ChangedFile;
import local.agent.pullrequestreviewagent.github.GitHubClient;
import local.agent.pullrequestreviewagent.github.GitHubApiException;

import local.agent.pullrequestreviewagent.progress.ReviewProgressPublisher;

import org.junit.jupiter.api.Test;

import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;
import java.util.ArrayList;

import static org.assertj.core.api.Assertions.assertThat;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

class GitHubReviewPublisherTest {

    private static final String DIFF = """
            @@ -10,2 +10,3 @@
             context
            +added line
             more context
            """;

    private final GitHubClient gitHubClient = mock(GitHubClient.class);
    private final GitHubReviewPublisher publisher = new GitHubReviewPublisher(gitHubClient);
    private final List<String> progressMessages = new ArrayList<>();
    private final ReviewProgressPublisher progressPublisher = progressMessages::add;

    @SuppressWarnings("unchecked")
    private ArgumentCaptor<List<GitHubClient.ReviewComment>> commentsCaptor() {
        return ArgumentCaptor.forClass(List.class);
    }

    @Test
    void anchorsAFindingWhoseLineIsPartOfTheDiffHunk() {
        ChangedFile changedFile = new ChangedFile("Foo.java", ChangedFile.ChangeType.MODIFIED, DIFF);
        ReviewFinding finding = new ReviewFinding(
                FindingSeverity.MAJOR, "Foo.java", 11, "Null check missing", "Explanation", "Add a check");
        ReviewResult result = new ReviewResult("Overall looks fine.", Recommendation.REQUEST_CHANGES, List.of(finding));
        when(gitHubClient.submitReview(any(), any(), anyInt(), any(), any(), any(), any())).thenReturn(
                new GitHubClient.SubmittedReview(1, "https://github.com/acme/widgets/pull/42#review-1", "CHANGES_REQUESTED"));

        publisher.publish("acme", "widgets", 42, "head-sha", List.of(changedFile), result, progressPublisher);

        ArgumentCaptor<List<GitHubClient.ReviewComment>> comments = commentsCaptor();
        verify(gitHubClient).submitReview(
                eq("acme"), eq("widgets"), eq(42), eq("head-sha"), any(), eq("REQUEST_CHANGES"), comments.capture());
        assertThat(comments.getValue()).hasSize(1);
        assertThat(comments.getValue().get(0).path()).isEqualTo("Foo.java");
        assertThat(comments.getValue().get(0).line()).isEqualTo(11);
        assertThat(comments.getValue().get(0).body())
                .contains("Null check missing").contains("Explanation").contains("Add a check");
    }

    @Test
    void foldsAFindingOnAnUncommentableLineIntoTheReviewBodyInstead() {
        ChangedFile changedFile = new ChangedFile("Foo.java", ChangedFile.ChangeType.MODIFIED, DIFF);
        ReviewFinding finding = new ReviewFinding(
                FindingSeverity.MINOR, "Foo.java", 999, "Out of hunk", "Not in the diff", null);
        ReviewResult result = new ReviewResult("Overall looks fine.", Recommendation.COMMENT, List.of(finding));
        when(gitHubClient.submitReview(any(), any(), anyInt(), any(), any(), any(), any())).thenReturn(
                new GitHubClient.SubmittedReview(2, "https://github.com/acme/widgets/pull/42#review-2", "COMMENTED"));

        publisher.publish("acme", "widgets", 42, "head-sha", List.of(changedFile), result, progressPublisher);

        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<List<GitHubClient.ReviewComment>> comments = commentsCaptor();
        verify(gitHubClient).submitReview(any(), any(), anyInt(), any(), body.capture(), any(), comments.capture());
        assertThat(comments.getValue()).isEmpty();
        assertThat(body.getValue()).contains("Overall looks fine.").contains("Out of hunk").contains("`Foo.java`:999");
    }

    @Test
    void foldsAFindingOnADeletedFileIntoTheReviewBody() {
        ChangedFile changedFile = new ChangedFile("Gone.java", ChangedFile.ChangeType.DELETED, DIFF);
        ReviewFinding finding = new ReviewFinding(
                FindingSeverity.NIT, "Gone.java", 10, "Dead code removed", "Fine either way", null);
        ReviewResult result = new ReviewResult("Summary.", Recommendation.APPROVE, List.of(finding));
        when(gitHubClient.submitReview(any(), any(), anyInt(), any(), any(), any(), any()))
                .thenReturn(new GitHubClient.SubmittedReview(3, "url", "APPROVED"));

        publisher.publish("acme", "widgets", 42, "head-sha", List.of(changedFile), result, progressPublisher);

        ArgumentCaptor<List<GitHubClient.ReviewComment>> comments = commentsCaptor();
        verify(gitHubClient).submitReview(any(), any(), anyInt(), any(), any(), any(), comments.capture());
        assertThat(comments.getValue()).isEmpty();
    }

    @Test
    void returnsEmptyAndReportsProgressWhenGitHubRejectsTheReview() {
        ReviewResult result = new ReviewResult("Summary.", Recommendation.APPROVE, List.of());
        when(gitHubClient.submitReview(any(), any(), anyInt(), any(), any(), any(), any()))
                .thenThrow(new GitHubApiException("422 from GitHub"));

        Optional<GitHubClient.SubmittedReview> outcome =
                publisher.publish("acme", "widgets", 42, "head-sha", List.of(), result, progressPublisher);

        assertThat(outcome).isEmpty();
        assertThat(progressMessages).anyMatch(message -> message.contains("Could not post review to GitHub"));
    }

    @Test
    void returnsTheSubmittedReviewOnSuccess() {
        ReviewResult result = new ReviewResult("Summary.", Recommendation.APPROVE, List.of());
        GitHubClient.SubmittedReview submitted =
                new GitHubClient.SubmittedReview(4, "https://github.com/x/y/pull/1#review-4", "APPROVED");
        when(gitHubClient.submitReview(any(), any(), anyInt(), any(), any(), any(), any())).thenReturn(submitted);

        Optional<GitHubClient.SubmittedReview> outcome =
                publisher.publish("acme", "widgets", 42, "head-sha", List.of(), result, progressPublisher);

        assertThat(outcome).contains(submitted);
        assertThat(progressMessages).anyMatch(message -> message.contains(submitted.htmlUrl()));
    }
}
