package local.agent.pullrequestreviewagent.review;

public record ReviewFinding(
        FindingSeverity severity,
        String file,
        Integer line,
        String title,
        String description,
        String suggestion
) {
}
