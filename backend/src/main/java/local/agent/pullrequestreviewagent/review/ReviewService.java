package local.agent.pullrequestreviewagent.review;

import local.agent.pullrequestreviewagent.agent.PullRequestReviewAgent;
import local.agent.pullrequestreviewagent.git.ChangedFile;
import local.agent.pullrequestreviewagent.git.DiffSanitizer;
import local.agent.pullrequestreviewagent.git.GitDiffService;
import local.agent.pullrequestreviewagent.git.GitRepositoryService;
import org.eclipse.jgit.lib.Repository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ReviewService {

    private final GitRepositoryService gitRepositoryService;
    private final GitDiffService gitDiffService;
    private final DiffSanitizer diffSanitizer;
    private final PullRequestReviewAgent reviewAgent;

    public ReviewService(GitRepositoryService gitRepositoryService,
                          GitDiffService gitDiffService,
                          DiffSanitizer diffSanitizer,
                          PullRequestReviewAgent reviewAgent) {
        this.gitRepositoryService = gitRepositoryService;
        this.gitDiffService = gitDiffService;
        this.diffSanitizer = diffSanitizer;
        this.reviewAgent = reviewAgent;
    }

    /**
     * @param reviewBranch branch to review, or blank/null to review the current
     *                     working tree's uncommitted and untracked changes instead
     */
    public ReviewResult review(String repositoryPath, String baseBranch, String reviewBranch) {
        if (repositoryPath == null || repositoryPath.isBlank()) {
            throw new IllegalArgumentException("repositoryPath must not be blank");
        }
        if (baseBranch == null || baseBranch.isBlank()) {
            throw new IllegalArgumentException("baseBranch must not be blank");
        }

        boolean workingTree = reviewBranch == null || reviewBranch.isBlank();
        String reviewLabel = workingTree ? "working tree (uncommitted changes)" : reviewBranch;

        try (Repository repository = gitRepositoryService.openRepository(repositoryPath)) {
            List<ChangedFile> changedFiles = workingTree
                    ? gitDiffService.diffWorkingTree(repository, baseBranch)
                    : gitDiffService.diff(repository, baseBranch, reviewBranch);
            if (changedFiles.isEmpty()) {
                return new ReviewResult(
                        "No differences found between %s and %s.".formatted(baseBranch, reviewLabel),
                        Recommendation.APPROVE,
                        List.of());
            }
            List<ChangedFile> sanitizedFiles = diffSanitizer.sanitize(changedFiles);
            return reviewAgent.review(baseBranch, reviewLabel, sanitizedFiles);
        }
    }
}
