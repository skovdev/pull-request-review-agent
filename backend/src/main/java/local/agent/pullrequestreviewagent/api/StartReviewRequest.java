package local.agent.pullrequestreviewagent.api;

/**
 * @param reviewBranch branch to review, or blank/null to review the current
 *                      working tree's uncommitted and untracked changes instead
 */
public record StartReviewRequest(
        String repositoryPath,
        String baseBranch,
        String reviewBranch) {
}
