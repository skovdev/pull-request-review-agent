package local.agent.pullrequestreviewagent.tools;

import local.agent.pullrequestreviewagent.github.GitHubWorkspace;
import local.agent.pullrequestreviewagent.github.GitHubContentService;

import local.agent.pullrequestreviewagent.progress.ReviewProgressPublisher;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import java.util.List;
import java.util.ArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import static org.mockito.ArgumentMatchers.any;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RepositoryToolsTest {

    private final GitHubContentService gitHubContentService = mock(GitHubContentService.class);
    private final GitHubWorkspace workspace = mock(GitHubWorkspace.class);
    private final Path baseRoot = Path.of("/tmp/base-sha");
    private final Path headRoot = Path.of("/tmp/head-sha");
    private final List<String> progressMessages = new ArrayList<>();
    private final ReviewProgressPublisher publisher = progressMessages::add;

    RepositoryToolsTest() {
        when(workspace.rootFor("base-sha")).thenReturn(baseRoot);
        when(workspace.rootFor("head-sha")).thenReturn(headRoot);
    }

    @Test
    void readFileResolvesBaseSideToTheBaseSha() {
        RepositoryTools tools = new RepositoryTools(workspace, gitHubContentService, "base-sha", "head-sha", publisher, 20);
        when(gitHubContentService.readFile(baseRoot, "Foo.java")).thenReturn("base content");

        assertThat(tools.readFile("Foo.java", "base")).isEqualTo("base content");
    }

    @Test
    void readFileResolvesReviewSideToTheHeadSha() {
        RepositoryTools tools = new RepositoryTools(workspace, gitHubContentService, "base-sha", "head-sha", publisher, 20);
        when(gitHubContentService.readFile(headRoot, "Foo.java")).thenReturn("review content");

        assertThat(tools.readFile("Foo.java", "review")).isEqualTo("review content");
    }

    @Test
    void rejectsAnUnknownSide() {
        RepositoryTools tools = new RepositoryTools(workspace, gitHubContentService, "base-sha", "head-sha", publisher, 20);

        assertThatThrownBy(() -> tools.readFile("Foo.java", "bogus"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void throwsOnceTheToolCallBudgetIsExhausted() {
        RepositoryTools tools = new RepositoryTools(workspace, gitHubContentService, "base-sha", "head-sha", publisher, 2);
        when(gitHubContentService.readFile(any(), any())).thenReturn("content");

        tools.readFile("A.java", "base");
        tools.readFile("B.java", "base");

        assertThatThrownBy(() -> tools.readFile("C.java", "base"))
                .isInstanceOf(ToolBudgetExceededException.class);
    }

    @Test
    void publishesAProgressMessageForEachCall() {
        RepositoryTools tools = new RepositoryTools(workspace, gitHubContentService, "base-sha", "head-sha", publisher, 20);
        when(gitHubContentService.readFile(any(), any())).thenReturn("content");

        tools.readFile("Foo.java", "review");

        assertThat(progressMessages).containsExactly("Reading Foo.java (review)");
    }
}
