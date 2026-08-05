package local.agent.pullrequestreviewagent.api;

/**
 * @param owner      GitHub organization or user that owns the repository, e.g. "spring-projects"
 * @param repo       repository name, e.g. "spring-framework"
 * @param pullNumber pull request number to review
 */
public record StartReviewRequest(
        String owner,
        String repo,
        int pullNumber) {
}
