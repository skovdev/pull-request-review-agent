package local.agent.pullrequestreviewagent.tools;

/**
 * Thrown when a review exhausts its per-review tool call budget. Spring AI's default
 * tool execution handling turns an unchecked exception's message into the tool result
 * text sent back to the model, rather than failing the whole review, so the model sees
 * this as a normal (if unhelpful) tool response and can finish with what it already has.
 */
public class ToolBudgetExceededException extends RuntimeException {

    public ToolBudgetExceededException(String message) {
        super(message);
    }
}
