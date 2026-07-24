package local.agent.pullrequestreviewagent.ai;

import local.agent.pullrequestreviewagent.config.ReviewProperties;

import org.junit.jupiter.api.Test;

import org.mockito.Answers;

import org.springframework.ai.chat.client.ChatClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AiChatServiceImplTest {

    private final ChatClient chatClient = mock(ChatClient.class, Answers.RETURNS_DEEP_STUBS);
    private final ReviewProperties properties =
            new ReviewProperties(6_000, 60_000, 8_000, 200, 50, 2_000, 20, 3, 300_000);
    private final AiChatServiceImpl aiChatService = new AiChatServiceImpl(chatClient, properties);

    @Test
    void returnsTheResultOnceACallSucceeds() {
        when(chatClient.prompt().system("system").user("user").tools().call().entity(String.class))
                .thenThrow(new RuntimeException("transient failure"))
                .thenReturn("review result");

        String result = aiChatService.chat("system", "user", String.class);

        assertThat(result).isEqualTo("review result");
    }

    @Test
    void throwsAiChatExceptionAfterExhaustingAllAttempts() {
        RuntimeException failure = new RuntimeException("model unavailable");
        when(chatClient.prompt().system("system").user("user").tools().call().entity(String.class))
                .thenThrow(failure);

        assertThatThrownBy(() -> aiChatService.chat("system", "user", String.class))
                .isInstanceOf(AiChatException.class)
                .hasMessageContaining("3 attempts")
                .hasCause(failure);
    }
}
