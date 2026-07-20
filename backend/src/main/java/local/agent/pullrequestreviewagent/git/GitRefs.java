package local.agent.pullrequestreviewagent.git;

import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;

import java.io.IOException;

/**
 * Resolves a branch name to a commit, falling back to the remote-tracking ref so
 * branches that only exist on {@code origin} (common for a base branch like {@code main}
 * in a shallow or freshly-cloned checkout) still resolve.
 */
final class GitRefs {

    private GitRefs() {
    }

    static ObjectId resolve(Repository repository, String branch) {
        try {
            ObjectId id = repository.resolve(branch);
            if (id == null) {
                id = repository.resolve("refs/remotes/origin/" + branch);
            }
            if (id == null) {
                throw new GitRepositoryException("Unknown branch: " + branch);
            }
            return id;
        } catch (IOException e) {
            throw new GitRepositoryException("Failed to resolve branch: " + branch, e);
        }
    }
}
