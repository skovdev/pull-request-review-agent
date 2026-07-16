package local.agent.pullrequestreviewagent.review;

import java.util.List;

public record ReviewResult(
        String summary,
        Recommendation recommendation,
        List<ReviewFinding> findings
) {
}
