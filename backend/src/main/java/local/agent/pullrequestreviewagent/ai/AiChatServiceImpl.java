package local.agent.pullrequestreviewagent.ai;

import local.agent.pullrequestreviewagent.config.ReviewProperties;

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
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
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
            }
        }
        log.error("AI chat call failed after {} attempts", maxAttempts, lastFailure);
        throw new AiChatException("Failed to communicate with AI model after " + maxAttempts + " attempts", lastFailure);
    }
}
