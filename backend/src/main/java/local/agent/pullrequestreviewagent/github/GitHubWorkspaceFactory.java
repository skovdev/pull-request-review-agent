package local.agent.pullrequestreviewagent.github;

import org.springframework.stereotype.Service;

@Service
public class GitHubWorkspaceFactory {

    private final GitHubClient gitHubClient;

    public GitHubWorkspaceFactory(GitHubClient gitHubClient) {
        this.gitHubClient = gitHubClient;
    }

    public GitHubWorkspace create(String owner, String repo) {
        return new GitHubWorkspace(gitHubClient, owner, repo);
    }
}
