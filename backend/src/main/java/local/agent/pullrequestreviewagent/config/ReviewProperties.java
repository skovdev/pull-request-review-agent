package local.agent.pullrequestreviewagent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Tuning knobs for the review pipeline: diff size/truncation budgets, repository
 * read limits, the agent's tool-call budget, AI call retries, and the SSE emitter
 * timeout. Centralized here (bound under the {@code review.*} prefix) so every
 * operational limit can be overridden via configuration without a code change.
 *
 * <p>{@code postReviewToGitHub} defaults to {@code false}: posting a review is a
 * write visible to the PR author and team, not a read-only lookup like everything
 * else this app does, so it's opt-in rather than a side effect of every run.
 */
@ConfigurationProperties(prefix = "review")
public record ReviewProperties(
        @DefaultValue("6000") int maxDiffCharsPerFile,
        @DefaultValue("60000") int maxDiffTotalChars,
        @DefaultValue("8000") int maxFileReadBytes,
        @DefaultValue("200") int maxListedEntries,
        @DefaultValue("50") int maxSearchResults,
        @DefaultValue("2000") int maxFilesScanned,
        @DefaultValue("20") int maxToolCallsPerReview,
        @DefaultValue("3") int maxChatAttempts,
        @DefaultValue("300000") long sseEmitterTimeoutMs,
        @DefaultValue("false") boolean postReviewToGitHub
) {
}
