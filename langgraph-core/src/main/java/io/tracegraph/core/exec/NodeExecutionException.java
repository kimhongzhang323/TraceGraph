package io.tracegraph.core.exec;

public class NodeExecutionException extends RuntimeException {

    private final String nodeName;

    public NodeExecutionException(String nodeName, Throwable cause) {
        super("Node '" + nodeName + "' threw " + cause.getClass().getSimpleName() + ": " + cause.getMessage(), cause);
        this.nodeName = nodeName;
    }

    public String nodeName() {
        return nodeName;
    }
}
