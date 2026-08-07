package local.agent.pullrequestreviewagent.review;

import local.agent.pullrequestreviewagent.github.ChangedFile;
import local.agent.pullrequestreviewagent.github.GitHubClient;
import local.agent.pullrequestreviewagent.github.UnifiedDiffLines;

import local.agent.pullrequestreviewagent.progress.ReviewProgressPublisher;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.Map;
import java.util.List;
import java.util.HashMap;
import java.util.Optional;
import java.util.ArrayList;

/**
 * Posts a finished {@link ReviewResult} back to the pull request as a real GitHub review, so the
 * verdict shows up where the PR author and team actually look rather than only in this app's UI.
 *
 * <p>Findings that GitHub won't let an inline comment anchor to (the file's diff was omitted, the
 * line isn't part of a hunk, or the file was deleted) are folded into the review body text
 * instead of being dropped, so nothing the model found is silently lost.
 */
@Component
public class GitHubReviewPublisher {

    private static final Logger log = LoggerFactory.getLogger(GitHubReviewPublisher.class);

    private final GitHubClient gitHubClient;

    public GitHubReviewPublisher(GitHubClient gitHubClient) {
        this.gitHubClient = gitHubClient;
    }

    /**
     * Never throws: a review that was already computed successfully should still reach the
     * caller even if GitHub rejects or times out the write, so failures are only logged and
     * reported through {@code progressPublisher}.
     *
     * @return the submitted review, or empty if posting failed
     */
    public Optional<GitHubClient.SubmittedReview> publish(String owner, String repo, int pullNumber, String headSha,
                                                            List<ChangedFile> changedFiles, ReviewResult result,
                                                            ReviewProgressPublisher progressPublisher) {
        Map<String, ChangedFile> changedFilesByPath = new HashMap<>();
        for (ChangedFile file : changedFiles) {
            changedFilesByPath.put(file.path(), file);
        }

        List<GitHubClient.ReviewComment> comments = new ArrayList<>();
        List<ReviewFinding> unanchored = new ArrayList<>();
        for (ReviewFinding finding : result.findings()) {
            if (isAnchorable(finding, changedFilesByPath)) {
                comments.add(new GitHubClient.ReviewComment(finding.file(), finding.line(), commentBody(finding)));
            } else {
                unanchored.add(finding);
            }
        }

        String body = unanchored.isEmpty() ? result.summary() : result.summary() + unanchoredSection(unanchored);

        progressPublisher.publish("Posting review to GitHub…");
        try {
            GitHubClient.SubmittedReview submitted = gitHubClient.submitReview(
                    owner, repo, pullNumber, headSha, body, result.recommendation().name(), comments);
            progressPublisher.publish("Review posted: " + submitted.htmlUrl());
            return Optional.of(submitted);
        } catch (RuntimeException e) {
            log.warn("Failed to post review to GitHub for {}/{}#{}", owner, repo, pullNumber, e);
            progressPublisher.publish("Could not post review to GitHub: " + e.getMessage());
            return Optional.empty();
        }
    }

    private boolean isAnchorable(ReviewFinding finding, Map<String, ChangedFile> changedFilesByPath) {
        if (finding.line() == null) {
            return false;
        }
        ChangedFile file = changedFilesByPath.get(finding.file());
        if (file == null || file.changeType() == ChangedFile.ChangeType.DELETED) {
            return false;
        }
        Set<Integer> commentableLines = UnifiedDiffLines.commentableNewLines(file.diff());
        return commentableLines.contains(finding.line());
    }

    private String commentBody(ReviewFinding finding) {
        StringBuilder body = new StringBuilder();
        body.append("**[").append(finding.severity()).append("] ").append(finding.title()).append("**\n\n");
        body.append(finding.description());
        if (finding.suggestion() != null && !finding.suggestion().isBlank()) {
            body.append("\n\nSuggestion: ").append(finding.suggestion());
        }
        return body.toString();
    }

    private String unanchoredSection(List<ReviewFinding> unanchored) {
        StringBuilder section = new StringBuilder("\n\n---\n**Additional findings** (couldn't be anchored to a specific diff line):\n");
        for (ReviewFinding finding : unanchored) {
            section.append("\n- **[").append(finding.severity()).append("]** `").append(finding.file()).append('`');
            if (finding.line() != null) {
                section.append(':').append(finding.line());
            }
            section.append(" — ").append(finding.title()).append(": ").append(finding.description());
        }
        return section.toString();
    }
}
