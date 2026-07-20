package local.agent.pullrequestreviewagent.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.ai.chat.client.ChatClient;

import org.springframework.stereotype.Service;

@Service
public class AiChatServiceImpl implements AiChatService {

    private static final Logger log = LoggerFactory.getLogger(AiChatServiceImpl.class);
    private static final int MAX_ATTEMPTS = 3;

    private final ChatClient chatClient;

    public AiChatServiceImpl(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    @Override
    public <T> T chat(String systemPrompt, String userPrompt, Class<T> responseType, Object... tools) {
        log.info("Sending prompts to AI model");
        Exception lastFailure = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                return chatClient.prompt()
                        .system(systemPrompt)
                        .user(userPrompt)
                        .tools(tools)
                        .call()
                        .entity(responseType);
            } catch (Exception e) {
                lastFailure = e;
                log.warn("AI chat call failed (attempt {}/{})", attempt, MAX_ATTEMPTS, e);
            }
        }
        log.error("AI chat call failed after {} attempts", MAX_ATTEMPTS, lastFailure);
        throw new AiChatException("Failed to communicate with AI model after " + MAX_ATTEMPTS + " attempts", lastFailure);
    }
}
