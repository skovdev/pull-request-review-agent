package local.agent.pullrequestreviewagent.git;

public record ChangedFile(String path, ChangeType changeType, String diff) {

    public enum ChangeType {
        ADDED,
        MODIFIED,
        DELETED,
        RENAMED,
        COPIED
    }
}
