package local.agent.pullrequestreviewagent.ai;

public class AiChatException extends RuntimeException {

    public AiChatException(String message) {
        super(message);
    }

    public AiChatException(String message, Throwable cause) {
        super(message, cause);
    }
}
