package local.agent.pullrequestreviewagent.ai;

/**
 * Provider-agnostic AI chat service. Sends a system prompt and a user prompt
 * to the configured AI model and maps the response onto the given type.
 */
public interface AiChatService {

    /**
     * Sends the given prompts to the AI model and maps the response onto {@code responseType}.
     *
     * @throws AiChatException if the underlying AI model call fails
     */
    <T> T chat(String systemPrompt, String userPrompt, Class<T> responseType);
}
