package local.agent.pullrequestreviewagent.github;

import com.fasterxml.jackson.annotation.JsonProperty;

import local.agent.pullrequestreviewagent.config.GitHubProperties;

import org.springframework.stereotype.Service;

import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.io.IOException;

import java.net.URI;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import java.util.List;
import java.util.Arrays;

/**
 * Thin wrapper over the three GitHub REST endpoints the review pipeline needs: pull request
 * metadata, a file-level diff between two commits, and a full source snapshot at a commit.
 *
 * <p>The archive download ({@link #downloadZipball}) is done with a raw {@link HttpClient}
 * rather than {@link RestClient}: GitHub answers it with a redirect to a signed
 * {@code codeload.github.com} URL, and the redirect must be followed as an explicit second
 * request (carrying the same auth) rather than relying on undocumented automatic-redirect
 * header-forwarding behavior.
 */
@Service
public class GitHubClient {

    private static final String API_VERSION = "2022-11-28";
    private static final int MAX_REDIRECTS = 5;

    private final RestClient restClient;
    private final HttpClient httpClient;
    private final String token;
    private final String apiBaseUrl;

    public GitHubClient(RestClient.Builder restClientBuilder, GitHubProperties properties) {
        this.token = properties.token();
        this.apiBaseUrl = properties.apiBaseUrl();
        this.restClient = restClientBuilder
                .baseUrl(apiBaseUrl)
                .defaultHeader("Accept", "application/vnd.github+json")
                .defaultHeader("X-GitHub-Api-Version", API_VERSION)
                .defaultHeader("Authorization", "Bearer " + token)
                .build();
        this.httpClient = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    public PullRequestContext getPullRequest(String owner, String repo, int pullNumber) {
        PullRequestResponse response = get(
                "/repos/{owner}/{repo}/pulls/{number}", PullRequestResponse.class, owner, repo, pullNumber);
        if (response == null || response.base() == null || response.head() == null) {
            throw new GitHubApiException("Malformed pull request response for " + owner + "/" + repo + "#" + pullNumber);
        }
        return new PullRequestContext(response.base().ref(), response.base().sha(), response.head().ref(), response.head().sha());
    }

    public List<CompareFile> compare(String owner, String repo, String base, String head) {
        CompareResponse response = get(
                "/repos/{owner}/{repo}/compare/{base}...{head}", CompareResponse.class, owner, repo, base, head);
        return response == null || response.files() == null ? List.of() : response.files();
    }

    public byte[] downloadZipball(String owner, String repo, String ref) {
        URI uri = URI.create(apiBaseUrl + "/repos/" + owner + "/" + repo + "/zipball/" + ref);
        try {
            HttpResponse<byte[]> response = send(uri);
            int redirects = 0;
            while (response.statusCode() / 100 == 3) {
                if (redirects++ >= MAX_REDIRECTS) {
                    throw new GitHubApiException("Too many redirects downloading archive for " + owner + "/" + repo + "@" + ref);
                }
                String location = response.headers().firstValue("Location")
                        .orElseThrow(() -> new GitHubApiException(
                                "Redirect without Location header downloading archive for " + owner + "/" + repo + "@" + ref));
                response = send(URI.create(location));
            }
            if (response.statusCode() != 200) {
                throw new GitHubApiException(
                        "Failed to download archive for " + owner + "/" + repo + "@" + ref + ": HTTP " + response.statusCode());
            }
            return response.body();
        } catch (IOException e) {
            throw new GitHubApiException("Failed to download archive for " + owner + "/" + repo + "@" + ref, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new GitHubApiException("Interrupted downloading archive for " + owner + "/" + repo + "@" + ref, e);
        }
    }

    private HttpResponse<byte[]> send(URI uri) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(uri)
                .header("Authorization", "Bearer " + token)
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", API_VERSION)
                .GET()
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
    }

    private <T> T get(String uriTemplate, Class<T> responseType, Object... uriVariables) {
        try {
            return restClient.get()
                    .uri(uriTemplate, uriVariables)
                    .retrieve()
                    .body(responseType);
        } catch (RestClientException e) {
            throw new GitHubApiException(
                    "GitHub API request failed: " + uriTemplate + " " + Arrays.toString(uriVariables), e);
        }
    }

    private record PullRequestResponse(Ref base, Ref head) {
        private record Ref(String ref, String sha) {
        }
    }

    private record CompareResponse(List<CompareFile> files) {
    }

    public record CompareFile(
            String filename,
            String status,
            @JsonProperty("previous_filename") String previousFilename,
            String patch
    ) {
    }
}
