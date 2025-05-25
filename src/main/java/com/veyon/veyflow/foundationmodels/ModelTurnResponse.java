package com.veyon.veyflow.foundationmodels;

import com.veyon.veyflow.tools.ToolCall;
import java.util.List;
import java.util.Collections;

/**
 * Represents a single turn's response from a foundation model.
 * This object standardizes the output from different model providers.
 */
public class ModelTurnResponse {
    private final String assistantContent;
    private final List<ToolCall> toolCalls;
    // private final String finishReason; // Optional: for future use
    // private final Map<String, Object> usageMetadata; // Optional: for future use

    public ModelTurnResponse(String assistantContent, List<ToolCall> toolCalls) {
        this.assistantContent = assistantContent;
        this.toolCalls = (toolCalls != null) ? Collections.unmodifiableList(toolCalls) : Collections.emptyList();
    }

    /**
     * Gets the textual content from the assistant, if any.
     * Can be null if the assistant only made tool calls or provided no text.
     * @return The assistant's textual content, or null.
     */
    public String getAssistantContent() {
        return assistantContent;
    }

    /**
     * Gets the list of tool calls requested by the assistant.
     * Returns an empty list if no tool calls were made.
     * @return An unmodifiable list of ToolCall objects.
     */
    public List<ToolCall> getToolCalls() {
        return toolCalls;
    }

    // Optional getters for finishReason and usageMetadata if added later
}
