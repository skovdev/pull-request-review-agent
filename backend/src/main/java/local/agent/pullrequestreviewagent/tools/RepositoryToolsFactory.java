package local.agent.pullrequestreviewagent.tools;

import local.agent.pullrequestreviewagent.git.GitContentService;
import local.agent.pullrequestreviewagent.progress.ReviewProgressPublisher;
import org.eclipse.jgit.lib.Repository;
import org.springframework.stereotype.Component;

@Component
public class RepositoryToolsFactory {

    private final GitContentService gitContentService;

    public RepositoryToolsFactory(GitContentService gitContentService) {
        this.gitContentService = gitContentService;
    }

    /**
     * @param reviewRef branch/commit name for the review side, or {@code null} when the
     *                  review side is the working tree (uncommitted/untracked changes)
     */
    public RepositoryTools create(Repository repository, String baseBranch, String reviewRef,
                                   ReviewProgressPublisher progressPublisher) {
        return new RepositoryTools(repository, gitContentService, baseBranch, reviewRef, progressPublisher);
    }
}
