package local.agent.pullrequestreviewagent.github;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class UnifiedDiffLinesTest {

    @Test
    void includesAddedAndContextLinesFromTheHunk() {
        String diff = """
                @@ -10,3 +10,4 @@ void foo() {
                 context before
                -removed line
                +added line one
                +added line two
                 context after
                """;

        Set<Integer> lines = UnifiedDiffLines.commentableNewLines(diff);

        assertThat(lines).containsExactlyInAnyOrder(10, 11, 12, 13);
    }

    @Test
    void handlesMultipleHunksInTheSameDiff() {
        String diff = """
                @@ -1,2 +1,2 @@
                -old first line
                +new first line
                 unchanged
                @@ -50,2 +50,3 @@
                 context
                +inserted
                 more context
                """;

        Set<Integer> lines = UnifiedDiffLines.commentableNewLines(diff);

        assertThat(lines).containsExactlyInAnyOrder(1, 2, 50, 51, 52);
    }

    @Test
    void handlesAHunkHeaderWithNoLineCountShorthand() {
        String diff = """
                @@ -5 +5 @@
                +only new line
                """;

        Set<Integer> lines = UnifiedDiffLines.commentableNewLines(diff);

        assertThat(lines).containsExactly(5);
    }

    @Test
    void returnsAnEmptySetForNullDiff() {
        assertThat(UnifiedDiffLines.commentableNewLines(null)).isEmpty();
    }

    @Test
    void returnsAnEmptySetWhenThereAreNoHunks() {
        assertThat(UnifiedDiffLines.commentableNewLines("(diff omitted: file too large or binary, not shown by GitHub)"))
                .isEmpty();
    }

    @Test
    void ignoresTheNoNewlineAtEndOfFileMarker() {
        String diff = """
                @@ -1,1 +1,1 @@
                -old
                +new
                \\ No newline at end of file
                """;

        Set<Integer> lines = UnifiedDiffLines.commentableNewLines(diff);

        assertThat(lines).containsExactly(1);
    }
}
