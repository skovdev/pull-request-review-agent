package local.agent.pullrequestreviewagent.api;

import local.agent.pullrequestreviewagent.config.AsyncConfig;
import local.agent.pullrequestreviewagent.config.ReviewProperties;

import local.agent.pullrequestreviewagent.progress.ReviewProgressPublisher;

import local.agent.pullrequestreviewagent.review.ReviewResult;
import local.agent.pullrequestreviewagent.review.ReviewService;
import local.agent.pullrequestreviewagent.review.Recommendation;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.boot.context.properties.EnableConfigurationProperties;

import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;

import org.springframework.context.annotation.Import;

import org.springframework.http.MediaType;

import org.springframework.test.context.bean.override.mockito.MockitoBean;

import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;

import static org.mockito.Mockito.when;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;

@WebMvcTest(ReviewController.class)
@Import(AsyncConfig.class)
@EnableConfigurationProperties(ReviewProperties.class)
class ReviewControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ReviewService reviewService;

    @Test
    void streamsProgressEventsFollowedByTheResult() throws Exception {
        when(reviewService.review(anyString(), anyString(), any(), any())).thenAnswer(invocation -> {
            ReviewProgressPublisher publisher = invocation.getArgument(3);
            publisher.publish("Computing diff between main and feature…");
            publisher.publish("Reading Foo.java (review)");
            return new ReviewResult("Looks good", Recommendation.APPROVE, List.of());
        });

        MvcResult mvcResult = mockMvc.perform(post("/api/reviews")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"repositoryPath":"/repo","baseBranch":"main","reviewBranch":"feature"}
                                """))
                .andExpect(request().asyncStarted())
                .andReturn();
        mvcResult.getAsyncResult(5_000);

        mockMvc.perform(asyncDispatch(mvcResult))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM));

        String body = mvcResult.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(body)
                .contains("event:progress")
                .contains("data:Computing diff between main and feature…")
                .contains("data:Reading Foo.java (review)")
                .contains("event:result")
                .contains("\"recommendation\":\"APPROVE\"");
    }

    @Test
    void sendsAnErrorEventWhenTheReviewFails() throws Exception {
        when(reviewService.review(anyString(), anyString(), any(), any()))
                .thenThrow(new IllegalArgumentException("baseBranch must not be blank"));

        MvcResult mvcResult = mockMvc.perform(post("/api/reviews")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"repositoryPath":"/repo","baseBranch":""}
                                """))
                .andExpect(request().asyncStarted())
                .andReturn();
        mvcResult.getAsyncResult(5_000);

        mockMvc.perform(asyncDispatch(mvcResult)).andExpect(status().isOk());

        assertThat(mvcResult.getResponse().getContentAsString())
                .contains("event:error")
                .contains("data:baseBranch must not be blank");
    }
}
