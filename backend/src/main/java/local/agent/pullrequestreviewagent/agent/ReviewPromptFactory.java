package local.agent.pullrequestreviewagent.agent;

import local.agent.pullrequestreviewagent.git.ChangedFile;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ReviewPromptFactory {

    public String systemPrompt() {
        return """
                You are an expert software engineer performing a pull request code review.
                You will be given the name of the base branch and the review branch, along with
                the unified diff of every file changed between them.

                Review the changes for correctness bugs, security issues, missing authorization
                or validation checks, and other problems a careful senior engineer would flag.
                Do not comment on pure style preferences unless they affect correctness or safety.

                Respond with:
                - a short summary of what the branch changes and your overall assessment
                - a recommendation: APPROVE if the changes are safe to merge as-is, COMMENT if
                  there are non-blocking suggestions, or REQUEST_CHANGES if there are issues that
                  must be fixed before merging
                - a list of findings, each referencing the specific file and line the issue was
                  found on, ordered from most to least severe. Use an empty list if there are
                  no findings.
                """;
    }

    public String userPrompt(String baseBranch, String reviewBranch, List<ChangedFile> changedFiles) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("Base branch: ").append(baseBranch).append('\n');
        prompt.append("Review branch: ").append(reviewBranch).append('\n');
        prompt.append("Files changed: ").append(changedFiles.size()).append("\n\n");

        for (ChangedFile file : changedFiles) {
            prompt.append("File: ").append(file.path())
                    .append(" (").append(file.changeType()).append(")\n");
            prompt.append("```diff\n").append(file.diff()).append("\n```\n\n");
        }

        return prompt.toString();
    }
}
