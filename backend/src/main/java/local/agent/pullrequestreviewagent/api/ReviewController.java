package local.agent.pullrequestreviewagent.api;

import local.agent.pullrequestreviewagent.config.ReviewProperties;

import local.agent.pullrequestreviewagent.progress.ReviewProgressPublisher;

import local.agent.pullrequestreviewagent.review.ReviewResult;
import local.agent.pullrequestreviewagent.review.ReviewService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.http.MediaType;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;

import java.util.concurrent.Executor;

/**
 * Streams a review as Server-Sent Events instead of returning one JSON blob, so the
 * frontend can show live progress (diffing, tool calls, ...) while the model works
 * rather than a blank spinner for however long the review takes.
 *
 * <p>Events: {@code progress} (plain text status update, may fire any number of times),
 * {@code result} (JSON {@link ReviewResponse}, terminal), {@code error} (plain text
 * failure message, terminal).
 */
@RestController
@RequestMapping("/api/reviews")
public class ReviewController {

    private static final Logger log = LoggerFactory.getLogger(ReviewController.class);

    private final ReviewService reviewService;
    private final Executor reviewExecutor;
    private final long sseEmitterTimeoutMs;

    public ReviewController(ReviewService reviewService, Executor reviewExecutor, ReviewProperties properties) {
        this.reviewService = reviewService;
        this.reviewExecutor = reviewExecutor;
        this.sseEmitterTimeoutMs = properties.sseEmitterTimeoutMs();
    }

    @PostMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter startReview(@RequestBody StartReviewRequest request) {
        SseEmitter emitter = new SseEmitter(sseEmitterTimeoutMs);
        reviewExecutor.execute(() -> runReview(request, emitter));
        return emitter;
    }

    private void runReview(StartReviewRequest request, SseEmitter emitter) {
        try {
            ReviewProgressPublisher progressPublisher = message -> sendEvent(emitter, "progress", message);
            ReviewResult result = reviewService.review(
                    request.repositoryPath(),
                    request.baseBranch(),
                    request.reviewBranch(),
                    progressPublisher);
            sendEvent(emitter, "result", ReviewResponse.from(result));
            emitter.complete();
        } catch (RuntimeException ex) {
            log.warn("Review failed", ex);
            sendEvent(emitter, "error", ex.getMessage() != null ? ex.getMessage() : "Review failed");
            emitter.complete();
        }
    }

    private void sendEvent(SseEmitter emitter, String name, Object data) {
        try {
            emitter.send(SseEmitter.event().name(name).data(data));
        } catch (IOException | IllegalStateException e) {
            log.debug("Could not send SSE event, client likely disconnected", e);
        }
    }
}
