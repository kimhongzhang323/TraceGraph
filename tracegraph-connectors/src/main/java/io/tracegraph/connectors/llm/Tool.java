package io.tracegraph.connectors.llm;

/**
 * A tool that can be invoked by an LLM agent via tool calling.
 * <p>
 * Implementations should be stateless or thread-safe; the runtime may invoke
 * the same tool concurrently for parallel tool calls.
 */
@FunctionalInterface
public interface Tool {

    /**
     * Execute the tool with the given raw JSON arguments.
     *
     * @param arguments JSON string of arguments as specified by the LLM
     * @return textual result to feed back to the LLM
     * @throws Exception if tool execution fails; the framework will wrap this into a
     *                   {@link ToolResult#error(String, String)} automatically
     */
    String execute(String arguments) throws Exception;
}
