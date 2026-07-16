package local.agent.pullrequestreviewagent.git;

import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Service
public class GitRepositoryService {

    public Repository openRepository(String repositoryPath) {
        Path path = Path.of(repositoryPath);
        if (!Files.isDirectory(path)) {
            throw new GitRepositoryException("Repository path does not exist: " + repositoryPath);
        }
        try {
            return new FileRepositoryBuilder()
                    .findGitDir(path.toFile())
                    .setMustExist(true)
                    .build();
        } catch (IOException e) {
            throw new GitRepositoryException("Not a git repository: " + repositoryPath, e);
        }
    }
}
