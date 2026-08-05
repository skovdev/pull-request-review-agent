package local.agent.pullrequestreviewagent.review;

import local.agent.pullrequestreviewagent.agent.PullRequestReviewAgent;

import local.agent.pullrequestreviewagent.github.ChangedFile;
import local.agent.pullrequestreviewagent.github.DiffSanitizer;
import local.agent.pullrequestreviewagent.github.GitHubClient;
import local.agent.pullrequestreviewagent.github.GitHubWorkspace;
import local.agent.pullrequestreviewagent.github.GitHubDiffService;
import local.agent.pullrequestreviewagent.github.PullRequestContext;
import local.agent.pullrequestreviewagent.github.GitHubWorkspaceFactory;

import local.agent.pullrequestreviewagent.progress.ReviewProgressPublisher;

import local.agent.pullrequestreviewagent.tools.RepositoryTools;
import local.agent.pullrequestreviewagent.tools.RepositoryToolsFactory;

import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ReviewService {

    private final GitHubClient gitHubClient;
    private final GitHubWorkspaceFactory gitHubWorkspaceFactory;
    private final GitHubDiffService gitHubDiffService;
    private final DiffSanitizer diffSanitizer;
    private final PullRequestReviewAgent reviewAgent;
    private final RepositoryToolsFactory repositoryToolsFactory;

    public ReviewService(GitHubClient gitHubClient,
                          GitHubWorkspaceFactory gitHubWorkspaceFactory,
                          GitHubDiffService gitHubDiffService,
                          DiffSanitizer diffSanitizer,
                          PullRequestReviewAgent reviewAgent,
                          RepositoryToolsFactory repositoryToolsFactory) {
        this.gitHubClient = gitHubClient;
        this.gitHubWorkspaceFactory = gitHubWorkspaceFactory;
        this.gitHubDiffService = gitHubDiffService;
        this.diffSanitizer = diffSanitizer;
        this.reviewAgent = reviewAgent;
        this.repositoryToolsFactory = repositoryToolsFactory;
    }

    /**
     * @param progressPublisher notified as the review progresses; use {@link ReviewProgressPublisher#NO_OP}
     *                          if progress updates aren't needed
     */
    public ReviewResult review(String owner, String repo, int pullNumber, ReviewProgressPublisher progressPublisher) {
        if (owner == null || owner.isBlank()) {
            throw new IllegalArgumentException("owner must not be blank");
        }
        if (repo == null || repo.isBlank()) {
            throw new IllegalArgumentException("repo must not be blank");
        }
        if (pullNumber <= 0) {
            throw new IllegalArgumentException("pullNumber must be positive");
        }

        progressPublisher.publish("Fetching pull request " + owner + "/" + repo + "#" + pullNumber + "…");
        PullRequestContext pullRequest = gitHubClient.getPullRequest(owner, repo, pullNumber);

        try (GitHubWorkspace workspace = gitHubWorkspaceFactory.create(owner, repo)) {
            progressPublisher.publish(
                    "Computing diff between " + pullRequest.baseRef() + " and " + pullRequest.headRef() + "…");
            List<ChangedFile> changedFiles =
                    gitHubDiffService.diff(owner, repo, pullRequest.baseSha(), pullRequest.headSha());
            if (changedFiles.isEmpty()) {
                return new ReviewResult(
                        "No differences found between %s and %s.".formatted(pullRequest.baseRef(), pullRequest.headRef()),
                        Recommendation.APPROVE,
                        List.of());
            }
            List<ChangedFile> sanitizedFiles = diffSanitizer.sanitize(changedFiles);
            progressPublisher.publish(sanitizedFiles.size() + " file(s) changed. Asking the model to review…");
            RepositoryTools repositoryTools = repositoryToolsFactory.create(
                    workspace, pullRequest.baseSha(), pullRequest.headSha(), progressPublisher);
            return reviewAgent.review(pullRequest.baseRef(), pullRequest.headRef(), sanitizedFiles, repositoryTools);
        }
    }
}
