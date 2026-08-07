package local.agent.pullrequestreviewagent.ai;

import com.openai.errors.BadRequestException;
import com.openai.errors.NotFoundException;
import com.openai.errors.UnauthorizedException;
import com.openai.errors.PermissionDeniedException;
import com.openai.errors.UnprocessableEntityException;

import local.agent.pullrequestreviewagent.config.ReviewProperties;

import local.agent.pullrequestreviewagent.tools.ToolBudgetExceededException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.ai.chat.client.ChatClient;

import org.springframework.stereotype.Service;

@Service
public class AiChatServiceImpl implements AiChatService {

    private static final Logger log = LoggerFactory.getLogger(AiChatServiceImpl.class);

    private final ChatClient chatClient;
    private final int maxAttempts;

    public AiChatServiceImpl(ChatClient chatClient, ReviewProperties properties) {
        this.chatClient = chatClient;
        this.maxAttempts = properties.maxChatAttempts();
    }

    @Override
    public <T> T chat(String systemPrompt, String userPrompt, Class<T> responseType, Object... tools) {
        log.info("Sending prompts to AI model");
        Exception lastFailure = null;
        int attemptsMade = 0;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            attemptsMade = attempt;
            try {
                return chatClient.prompt()
                        .system(systemPrompt)
                        .user(userPrompt)
                        .tools(tools)
                        .call()
                        .entity(responseType);
            } catch (Exception e) {
                lastFailure = e;
                log.warn("AI chat call failed (attempt {}/{})", attempt, maxAttempts, e);
                if (!isRetryable(e)) {
                    break;
                }
            }
        }
        log.error("AI chat call failed after {} attempt(s)", attemptsMade, lastFailure);
        throw new AiChatException("Failed to communicate with AI model after " + attemptsMade + " attempt(s)", lastFailure);
    }

    /**
     * Some failures will fail identically on every retry, so retrying just adds latency (and,
     * once tool calls were already made, wasted tokens) without any chance of success: the tool
     * call budget is shared across attempts and exhausted for good, and these OpenAI errors are
     * all deterministic 4xx responses to the same request rather than transient conditions.
     * Everything else (rate limits, server errors, network failures, structured-output parsing
     * failures) is assumed to be worth retrying.
     */
    private boolean isRetryable(Exception e) {
        return switch (e) {
            case ToolBudgetExceededException ignored -> false;
            case UnauthorizedException ignored -> false;
            case PermissionDeniedException ignored -> false;
            case NotFoundException ignored -> false;
            case BadRequestException ignored -> false;
            case UnprocessableEntityException ignored -> false;
            default -> true;
        };
    }
}
