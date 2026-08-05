package local.agent.pullrequestreviewagent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * GitHub API access configuration, bound under the {@code github.*} prefix.
 * {@code token} needs at least read access to pull requests and contents (the {@code repo}
 * scope for a classic PAT, or Pull requests/Contents read permission for a fine-grained one).
 */
@ConfigurationProperties(prefix = "github")
public record GitHubProperties(
        @DefaultValue("") String token,
        @DefaultValue("https://api.github.com") String apiBaseUrl
) {
}
