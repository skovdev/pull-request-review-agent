package local.agent.pullrequestreviewagent;

import org.springframework.boot.SpringApplication;

import org.springframework.boot.autoconfigure.SpringBootApplication;

import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class PullRequestReviewAgentApplication {

    public static void main(String[] args) {
        SpringApplication.run(PullRequestReviewAgentApplication.class, args);
    }

}
