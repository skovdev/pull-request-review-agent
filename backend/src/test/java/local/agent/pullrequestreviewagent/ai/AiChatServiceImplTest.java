package local.agent.pullrequestreviewagent.ai;

import com.openai.errors.UnauthorizedException;

import local.agent.pullrequestreviewagent.config.ReviewProperties;

import local.agent.pullrequestreviewagent.tools.ToolBudgetExceededException;

import org.junit.jupiter.api.Test;

import org.mockito.Answers;

import org.springframework.ai.chat.client.ChatClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class AiChatServiceImplTest {

    private final ChatClient chatClient = mock(ChatClient.class, Answers.RETURNS_DEEP_STUBS);
    private final ReviewProperties properties =
            new ReviewProperties(6_000, 60_000, 8_000, 200, 50, 2_000, 20, 3, 300_000, false);
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
    void throwsAiChatExceptionAfterExhaustingAllAttemptsOnARetryableFailure() {
        RuntimeException failure = new RuntimeException("model unavailable");
        when(chatClient.prompt().system("system").user("user").tools().call().entity(String.class))
                .thenThrow(failure);

        assertThatThrownBy(() -> aiChatService.chat("system", "user", String.class))
                .isInstanceOf(AiChatException.class)
                .hasMessageContaining("3 attempt(s)")
                .hasCause(failure);

        verify(chatClient.prompt().system("system").user("user").tools().call(), times(3)).entity(String.class);
    }

    @Test
    void doesNotRetryAnUnauthorizedFailure() {
        UnauthorizedException failure = mock(UnauthorizedException.class);
        when(chatClient.prompt().system("system").user("user").tools().call().entity(String.class))
                .thenThrow(failure);

        assertThatThrownBy(() -> aiChatService.chat("system", "user", String.class))
                .isInstanceOf(AiChatException.class)
                .hasMessageContaining("1 attempt(s)")
                .hasCause(failure);

        verify(chatClient.prompt().system("system").user("user").tools().call(), times(1)).entity(String.class);
    }

    @Test
    void doesNotRetryOnceTheToolBudgetIsExhausted() {
        ToolBudgetExceededException failure = new ToolBudgetExceededException("budget exceeded");
        when(chatClient.prompt().system("system").user("user").tools().call().entity(String.class))
                .thenThrow(failure);

        assertThatThrownBy(() -> aiChatService.chat("system", "user", String.class))
                .isInstanceOf(AiChatException.class)
                .hasMessageContaining("1 attempt(s)")
                .hasCause(failure);

        verify(chatClient.prompt().system("system").user("user").tools().call(), times(1)).entity(String.class);
    }
}
