package local.agent.pullrequestreviewagent.github;

/**
 * The base/head refs and commit SHAs of a pull request as reported by GitHub. Refs (branch
 * names) are what gets shown to the reviewer and the model; SHAs are what the diff and content
 * lookups are actually pinned to, so a base branch moving mid-review can't change the result.
 */
public record PullRequestContext(String baseRef, String baseSha, String headRef, String headSha) {
}
