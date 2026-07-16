package local.agent.pullrequestreviewagent.git;

public class GitRepositoryException extends RuntimeException {

    public GitRepositoryException(String message) {
        super(message);
    }

    public GitRepositoryException(String message, Throwable cause) {
        super(message, cause);
    }
}
