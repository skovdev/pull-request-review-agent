package local.agent.pullrequestreviewagent.tools;

import local.agent.pullrequestreviewagent.git.GitContentService;

import local.agent.pullrequestreviewagent.progress.ReviewProgressPublisher;

import org.eclipse.jgit.lib.Repository;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.ArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import static org.mockito.ArgumentMatchers.any;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RepositoryToolsTest {

    private final GitContentService gitContentService = mock(GitContentService.class);
    private final Repository repository = mock(Repository.class);
    private final List<String> progressMessages = new ArrayList<>();
    private final ReviewProgressPublisher publisher = progressMessages::add;

    @Test
    void readFileResolvesBaseSideToTheBaseBranch() {
        RepositoryTools tools = new RepositoryTools(repository, gitContentService, "main", "feature", publisher, 20);
        when(gitContentService.readFile(repository, "main", "Foo.java")).thenReturn("base content");

        assertThat(tools.readFile("Foo.java", "base")).isEqualTo("base content");
    }

    @Test
    void readFileResolvesReviewSideToTheReviewRef() {
        RepositoryTools tools = new RepositoryTools(repository, gitContentService, "main", "feature", publisher, 20);
        when(gitContentService.readFile(repository, "feature", "Foo.java")).thenReturn("review content");

        assertThat(tools.readFile("Foo.java", "review")).isEqualTo("review content");
    }

    @Test
    void rejectsAnUnknownSide() {
        RepositoryTools tools = new RepositoryTools(repository, gitContentService, "main", "feature", publisher, 20);

        assertThatThrownBy(() -> tools.readFile("Foo.java", "bogus"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void throwsOnceTheToolCallBudgetIsExhausted() {
        RepositoryTools tools = new RepositoryTools(repository, gitContentService, "main", "feature", publisher, 2);
        when(gitContentService.readFile(any(), any(), any())).thenReturn("content");

        tools.readFile("A.java", "base");
        tools.readFile("B.java", "base");

        assertThatThrownBy(() -> tools.readFile("C.java", "base"))
                .isInstanceOf(ToolBudgetExceededException.class);
    }

    @Test
    void publishesAProgressMessageForEachCall() {
        RepositoryTools tools = new RepositoryTools(repository, gitContentService, "main", "feature", publisher, 20);
        when(gitContentService.readFile(any(), any(), any())).thenReturn("content");

        tools.readFile("Foo.java", "review");

        assertThat(progressMessages).containsExactly("Reading Foo.java (review)");
    }
}
