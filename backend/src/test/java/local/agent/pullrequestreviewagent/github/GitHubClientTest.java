package local.agent.pullrequestreviewagent.github;

import local.agent.pullrequestreviewagent.config.GitHubProperties;

import org.junit.jupiter.api.Test;

import org.springframework.http.MediaType;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;

import org.springframework.test.web.client.MockRestServiceServer;

import org.springframework.web.client.RestClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;

import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class GitHubClientTest {

    private final RestClient.Builder builder = RestClient.builder();
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    private final GitHubClient client =
            new GitHubClient(builder, new GitHubProperties("test-token", "https://api.github.com"));

    @Test
    void submitsAReviewWithTheVerdictAndInlineComments() {
        server.expect(requestTo("https://api.github.com/repos/acme/widgets/pulls/42/reviews"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer test-token"))
                .andExpect(jsonPath("$.commit_id").value("head-sha"))
                .andExpect(jsonPath("$.body").value("Looks good overall."))
                .andExpect(jsonPath("$.event").value("REQUEST_CHANGES"))
                .andExpect(jsonPath("$.comments[0].path").value("Foo.java"))
                .andExpect(jsonPath("$.comments[0].line").value(42))
                .andExpect(jsonPath("$.comments[0].body").value("Null check missing here."))
                .andRespond(withSuccess("""
                        {"id": 123, "html_url": "https://github.com/acme/widgets/pull/42#pullrequestreview-123", "state": "CHANGES_REQUESTED"}
                        """, MediaType.APPLICATION_JSON));

        GitHubClient.SubmittedReview result = client.submitReview(
                "acme", "widgets", 42, "head-sha", "Looks good overall.", "REQUEST_CHANGES",
                List.of(new GitHubClient.ReviewComment("Foo.java", 42, "Null check missing here.")));

        assertThat(result.id()).isEqualTo(123);
        assertThat(result.htmlUrl()).isEqualTo("https://github.com/acme/widgets/pull/42#pullrequestreview-123");
        assertThat(result.state()).isEqualTo("CHANGES_REQUESTED");
        server.verify();
    }

    @Test
    void submitsAReviewWithNoComments() {
        server.expect(requestTo("https://api.github.com/repos/acme/widgets/pulls/42/reviews"))
                .andExpect(jsonPath("$.event").value("APPROVE"))
                .andExpect(jsonPath("$.comments").isEmpty())
                .andRespond(withSuccess("""
                        {"id": 1, "html_url": "https://github.com/acme/widgets/pull/42#pullrequestreview-1", "state": "APPROVED"}
                        """, MediaType.APPLICATION_JSON));

        GitHubClient.SubmittedReview result = client.submitReview(
                "acme", "widgets", 42, "head-sha", "Ship it.", "APPROVE", List.of());

        assertThat(result.state()).isEqualTo("APPROVED");
    }

    @Test
    void wrapsAFailedReviewSubmissionInAGitHubApiException() {
        server.expect(requestTo("https://api.github.com/repos/acme/widgets/pulls/42/reviews"))
                .andRespond(withStatus(HttpStatus.UNPROCESSABLE_CONTENT)
                        .body("{\"message\": \"Validation Failed\"}")
                        .contentType(MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.submitReview(
                "acme", "widgets", 42, "head-sha", "body", "COMMENT", List.of()))
                .isInstanceOf(GitHubApiException.class);
    }
}
