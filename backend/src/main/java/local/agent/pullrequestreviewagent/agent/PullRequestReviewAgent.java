package local.agent.pullrequestreviewagent.agent;

import local.agent.pullrequestreviewagent.ai.AiChatService;
import local.agent.pullrequestreviewagent.git.ChangedFile;
import local.agent.pullrequestreviewagent.review.ReviewResult;
import local.agent.pullrequestreviewagent.tools.RepositoryTools;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class PullRequestReviewAgent {

    private final AiChatService aiChatService;
    private final ReviewPromptFactory promptFactory;

    public PullRequestReviewAgent(AiChatService aiChatService, ReviewPromptFactory promptFactory) {
        this.aiChatService = aiChatService;
        this.promptFactory = promptFactory;
    }

    public ReviewResult review(String baseBranch, String reviewBranch, List<ChangedFile> changedFiles,
                                RepositoryTools repositoryTools) {
        return aiChatService.chat(
                promptFactory.systemPrompt(),
                promptFactory.userPrompt(baseBranch, reviewBranch, changedFiles),
                ReviewResult.class,
                repositoryTools);
    }
}
