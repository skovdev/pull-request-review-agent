package local.agent.pullrequestreviewagent.tools;

import local.agent.pullrequestreviewagent.git.GitContentService;
import local.agent.pullrequestreviewagent.progress.ReviewProgressPublisher;
import org.eclipse.jgit.lib.Repository;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 * Read-only repository access exposed to the review model as tools, so it can look past
 * the diff hunks it was handed when it needs more context. One instance is created per
 * review and is not reused, so {@link #remainingCalls} bounds the total tool calls a
 * single review can make regardless of how many rounds the model runs.
 */
public class RepositoryTools {

    private static final int MAX_CALLS = 20;
    private static final String SIDE_DESCRIPTION =
            "Which side of the review to read from: \"base\" for the base branch, or \"review\" for the branch/changes under review.";

    private final Repository repository;
    private final GitContentService gitContentService;
    private final String baseBranch;
    private final String reviewRef;
    private final ReviewProgressPublisher progressPublisher;
    private final AtomicInteger remainingCalls = new AtomicInteger(MAX_CALLS);

    /**
     * @param reviewRef branch/commit name for the review side, or {@code null} when the
     *                  review side is the working tree (uncommitted/untracked changes)
     */
    public RepositoryTools(Repository repository, GitContentService gitContentService,
                            String baseBranch, String reviewRef, ReviewProgressPublisher progressPublisher) {
        this.repository = repository;
        this.gitContentService = gitContentService;
        this.baseBranch = baseBranch;
        this.reviewRef = reviewRef;
        this.progressPublisher = progressPublisher;
    }

    @Tool(description = "Read the full contents of a file, to see context beyond the diff hunks such as " +
            "imports, surrounding methods, or the rest of a function. Returns null if the file does not exist on that side.")
    public String readFile(@ToolParam(description = "Repository-relative file path, e.g. src/main/Foo.java") String path,
                            @ToolParam(description = SIDE_DESCRIPTION) String side) {
        return withBudget("Reading " + path + " (" + side + ")",
                () -> gitContentService.readFile(repository, refFor(side), path));
    }

    @Tool(description = "List files under a directory, to discover related files (tests, callers, config) near a changed file.")
    public List<String> listFiles(@ToolParam(description = "Repository-relative directory path, empty string for the repository root") String directory,
                                   @ToolParam(description = SIDE_DESCRIPTION) String side) {
        String label = directory == null || directory.isBlank() ? "repository root" : directory;
        return withBudget("Listing " + label + " (" + side + ")",
                () -> gitContentService.listFiles(repository, refFor(side), directory));
    }

    @Tool(description = "Search for a literal substring across all text files in the repository, to find other " +
            "usages or callers of a changed symbol. Returns matches as \"path:line: content\".")
    public List<String> searchCode(@ToolParam(description = "Literal text to search for") String query,
                                    @ToolParam(description = SIDE_DESCRIPTION) String side) {
        return withBudget("Searching for \"" + query + "\" (" + side + ")",
                () -> gitContentService.searchCode(repository, refFor(side), query));
    }

    private String refFor(String side) {
        if ("base".equalsIgnoreCase(side)) {
            return baseBranch;
        }
        return reviewRef;
    }

    private <T> T withBudget(String progressMessage, Supplier<T> call) {
        if (remainingCalls.getAndDecrement() <= 0) {
            throw new ToolBudgetExceededException(
                    "Tool call budget exceeded (max " + MAX_CALLS + " per review); answer with what you already have.");
        }
        progressPublisher.publish(progressMessage);
        return call.get();
    }
}
