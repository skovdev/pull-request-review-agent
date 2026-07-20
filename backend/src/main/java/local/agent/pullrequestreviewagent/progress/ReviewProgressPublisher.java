package local.agent.pullrequestreviewagent.progress;

/**
 * Reports what a review is doing as it happens, so a caller can show live progress
 * instead of a blank wait. Publishing is best-effort: a slow or absent listener must
 * never block or fail the review itself.
 */
@FunctionalInterface
public interface ReviewProgressPublisher {

    ReviewProgressPublisher NO_OP = message -> { };

    void publish(String message);
}
