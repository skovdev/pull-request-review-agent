package local.agent.pullrequestreviewagent.diff;

public record ChangedFile(String path, ChangeType changeType, String diff) {

    public enum ChangeType {
        ADDED,
        MODIFIED,
        DELETED,
        RENAMED,
        COPIED
    }
}
