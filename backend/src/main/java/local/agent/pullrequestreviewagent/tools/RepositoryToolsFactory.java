package local.agent.pullrequestreviewagent.tools;

import local.agent.pullrequestreviewagent.config.ReviewProperties;

import local.agent.pullrequestreviewagent.github.GitHubWorkspace;
import local.agent.pullrequestreviewagent.github.GitHubContentService;

import local.agent.pullrequestreviewagent.progress.ReviewProgressPublisher;

import org.springframework.stereotype.Component;

@Component
public class RepositoryToolsFactory {

    private final GitHubContentService gitHubContentService;
    private final int maxToolCallsPerReview;

    public RepositoryToolsFactory(GitHubContentService gitHubContentService, ReviewProperties properties) {
        this.gitHubContentService = gitHubContentService;
        this.maxToolCallsPerReview = properties.maxToolCallsPerReview();
    }

    public RepositoryTools create(GitHubWorkspace workspace, String baseSha, String headSha,
                                   ReviewProgressPublisher progressPublisher) {
        return new RepositoryTools(workspace, gitHubContentService, baseSha, headSha, progressPublisher, maxToolCallsPerReview);
    }
}
