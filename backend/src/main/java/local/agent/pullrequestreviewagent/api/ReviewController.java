package local.agent.pullrequestreviewagent.api;

import local.agent.pullrequestreviewagent.review.ReviewResult;
import local.agent.pullrequestreviewagent.review.ReviewService;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestMapping;

@RestController
@RequestMapping("/api/reviews")
public class ReviewController {

    private final ReviewService reviewService;

    public ReviewController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    @PostMapping
    public ReviewResponse startReview(@RequestBody StartReviewRequest request) {
        ReviewResult reviewResult = reviewService.review(
                request.repositoryPath(),
                request.baseBranch(),
                request.reviewBranch());
        return ReviewResponse.from(reviewResult);
    }
}
