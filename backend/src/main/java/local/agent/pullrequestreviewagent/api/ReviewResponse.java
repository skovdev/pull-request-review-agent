package local.agent.pullrequestreviewagent.api;

import local.agent.pullrequestreviewagent.review.ReviewFinding;
import local.agent.pullrequestreviewagent.review.ReviewResult;

import java.util.List;

public record ReviewResponse(String summary, String recommendation, List<FindingResponse> findings) {

    public static ReviewResponse from(ReviewResult result) {
        return new ReviewResponse(
                result.summary(),
                result.recommendation().name(),
                result.findings().stream().map(FindingResponse::from).toList());
    }

    public record FindingResponse(
            String severity,
            String file,
            Integer line,
            String title,
            String description,
            String suggestion
    ) {
        public static FindingResponse from(ReviewFinding finding) {
            return new FindingResponse(
                    finding.severity().name(),
                    finding.file(),
                    finding.line(),
                    finding.title(),
                    finding.description(),
                    finding.suggestion());
        }
    }
}
