package com.veyon.veyflow.core;

import java.util.List;
import java.util.Map;
import java.util.Collections;

/**
 * Represents the result of a ToolAgent interaction, including the final message
 * from the language model and metadata about any tools that were executed.
 */
public class ToolAgentResult {

    private final String finalMessage;
    private final List<ToolExecutionRecord> toolExecutionMetadata;

    /**
     * Constructs a new ToolAgentResult.
     *
     * @param finalMessage The final textual message from the language model.
     * @param toolExecutionMetadata A list of records detailing each tool execution that occurred.
     */
    public ToolAgentResult(String finalMessage, List<ToolExecutionRecord> toolExecutionMetadata) {
        this.finalMessage = finalMessage;
        this.toolExecutionMetadata = toolExecutionMetadata != null ? Collections.unmodifiableList(toolExecutionMetadata) : Collections.emptyList();
    }

    /**
     * Gets the final textual message from the language model after all tool calls (if any) are processed.
     * @return The final message.
     */
    public String getFinalMessage() {
        return finalMessage;
    }

    /**
     * Gets the metadata for all tool executions that occurred during the agent's processing loop.
     * @return An unmodifiable list of tool execution records. Returns an empty list if no tools were executed.
     */
    public List<ToolExecutionRecord> getToolExecutionMetadata() {
        return toolExecutionMetadata;
    }

    /**
     * Represents a record of a single tool execution.
     */
    public static class ToolExecutionRecord {
        private final String toolCallId; // ID from the LLM or generated
        private final String toolName;   // e.g., "WeatherToolService.getWeather"
        private final Map<String, Object> arguments;
        private final Object response;   // Could be String, JsonObject, custom object, etc.

        /**
         * Constructs a new ToolExecutionRecord.
         *
         * @param toolCallId The unique identifier for the tool call (often provided by the LLM).
         * @param toolName The name of the tool/method that was invoked.
         * @param arguments The arguments passed to the tool.
         * @param response The response returned by the tool.
         */
        public ToolExecutionRecord(String toolCallId, String toolName, Map<String, Object> arguments, Object response) {
            this.toolCallId = toolCallId;
            this.toolName = toolName;
            this.arguments = arguments != null ? Collections.unmodifiableMap(arguments) : Collections.emptyMap();
            this.response = response;
        }

        public String getToolCallId() {
            return toolCallId;
        }

        public String getToolName() {
            return toolName;
        }

        public Map<String, Object> getArguments() {
            return arguments;
        }

        public Object getResponse() {
            return response;
        }
    }
}
