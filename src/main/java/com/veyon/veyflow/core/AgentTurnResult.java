package com.veyon.veyflow.core;

import com.veyon.veyflow.state.ChatMessage;
import java.util.List;

/**
 * Represents the result of a single turn of processing by the ToolAgent.
 */
public class AgentTurnResult {
    private final String finalMessage;
    private final List<ChatMessage> chatHistory; // Full history
    private final List<ChatMessage> newMessages; // New messages in this turn
    private final List<ToolExecutionRecord> toolExecutionMetadata;

    public AgentTurnResult(
            String finalMessage, 
            List<ChatMessage> chatHistory, 
            List<ChatMessage> newMessages, 
            List<ToolExecutionRecord> toolExecutionMetadata) {
        this.finalMessage = finalMessage;
        this.chatHistory = chatHistory;
        this.newMessages = newMessages; 
        this.toolExecutionMetadata = toolExecutionMetadata;
    }

    public String getFinalMessage() {
        return finalMessage;
    }

    public List<ChatMessage> getChatHistory() {
        return chatHistory;
    }

    /**
     * Gets only the new messages generated during this execution turn.
     * This includes assistant responses and tool responses for this turn.
     */
    public List<ChatMessage> getNewMessages() { 
        return newMessages;
    }

    public List<ToolExecutionRecord> getToolExecutionMetadata() {
        return toolExecutionMetadata;
    }

    /**
     * Represents the metadata of a single tool execution.
     */
    public static class ToolExecutionRecord {
        private final String toolName;
        private final String arguments; // Assuming arguments are captured as a JSON string
        private final String result;    // Assuming result is captured as a JSON string

        public ToolExecutionRecord(String toolName, String arguments, String result) {
            this.toolName = toolName;
            this.arguments = arguments;
            this.result = result;
        }

        public String getToolName() {
            return toolName;
        }

        public String getArguments() {
            return arguments;
        }

        public String getResult() {
            return result;
        }
    }
}
