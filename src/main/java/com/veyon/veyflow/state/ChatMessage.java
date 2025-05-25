package com.veyon.veyflow.state;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.veyon.veyflow.tools.ToolCall;

/**
 * Represents a chat message in the agent framework.
 * This class is used for building the conversation history and is serializable.
 */
public class ChatMessage {
    public enum Role {
        USER,
        ASSISTANT,
        SYSTEM,
        TOOL
    }

    private String id;
    private Role role;
    private String content;
    private Map<String, Object> metadata;
    private ZonedDateTime timestamp;
    private String toolName;
    private String toolResponse;
    private List<ToolCall> toolCalls;

    /**
     * Creates a new chat message.
     * 
     * @param role The role of the message sender
     * @param content The content of the message
     */
    public ChatMessage(Role role, String content) {
        this.role = role;
        this.content = content;
        this.metadata = new HashMap<>();
        this.timestamp = ZonedDateTime.now();
        this.toolCalls = new ArrayList<>();
    }

    /**
     * Creates a new chat message with ID.
     * 
     * @param id The message ID
     * @param role The role of the message sender
     * @param content The content of the message
     */
    public ChatMessage(String id, Role role, String content) {
        this(role, content);
        this.id = id;
    }

    /**
     * Creates a new tool message.
     * 
     * @param toolName The name of the tool
     * @param toolResponse The response from the tool
     * @return A new ChatMessage with TOOL role
     */
    public static ChatMessage toolMessage(String toolName, String toolResponse) {
        ChatMessage message = new ChatMessage(Role.TOOL, toolResponse);
        message.toolName = toolName;
        message.toolResponse = toolResponse;
        return message;
    }

    /**
     * Gets the message ID.
     * 
     * @return Message ID
     */
    public String getId() {
        return id;
    }

    /**
     * Sets the message ID.
     * 
     * @param id Message ID
     * @return This message instance for chaining
     */
    public ChatMessage setId(String id) {
        this.id = id;
        return this;
    }

    /**
     * Gets the role.
     * 
     * @return Role
     */
    public Role getRole() {
        return role;
    }

    /**
     * Sets the role.
     * 
     * @param role Role
     * @return This message instance for chaining
     */
    public ChatMessage setRole(Role role) {
        this.role = role;
        return this;
    }

    /**
     * Gets the content.
     * 
     * @return Content
     */
    public String getContent() {
        return content;
    }

    /**
     * Sets the content.
     * 
     * @param content Content
     * @return This message instance for chaining
     */
    public ChatMessage setContent(String content) {
        this.content = content;
        return this;
    }

    /**
     * Gets the metadata.
     * 
     * @return Metadata
     */
    public Map<String, Object> getMetadata() {
        return metadata;
    }

    /**
     * Sets the metadata.
     * 
     * @param metadata Metadata
     * @return This message instance for chaining
     */
    public ChatMessage setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
        return this;
    }

    /**
     * Adds a metadata entry.
     * 
     * @param key Key
     * @param value Value
     * @return This message instance for chaining
     */
    public ChatMessage addMetadata(String key, Object value) {
        this.metadata.put(key, value);
        return this;
    }

    /**
     * Gets the timestamp.
     * 
     * @return Timestamp
     */
    public ZonedDateTime getTimestamp() {
        return timestamp;
    }

    /**
     * Sets the timestamp.
     * 
     * @param timestamp Timestamp
     * @return This message instance for chaining
     */
    public ChatMessage setTimestamp(ZonedDateTime timestamp) {
        this.timestamp = timestamp;
        return this;
    }

    /**
     * Gets the tool name.
     * 
     * @return Tool name
     */
    public String getToolName() {
        return toolName;
    }

    /**
     * Sets the tool name.
     * 
     * @param toolName Tool name
     * @return This message instance for chaining
     */
    public ChatMessage setToolName(String toolName) {
        this.toolName = toolName;
        return this;
    }

    /**
     * Gets the tool response.
     * 
     * @return Tool response
     */
    public String getToolResponse() {
        return toolResponse;
    }

    /**
     * Sets the tool response.
     * 
     * @param toolResponse Tool response
     * @return This message instance for chaining
     */
    public ChatMessage setToolResponse(String toolResponse) {
        this.toolResponse = toolResponse;
        return this;
    }

    /**
     * Gets the list of tool calls associated with this message (usually for assistant messages).
     * 
     * @return A list of ToolCall objects, or an empty list if none.
     */
    public List<ToolCall> getToolCalls() {
        return toolCalls;
    }

    /**
     * Sets the list of tool calls for this message.
     * 
     * @param toolCalls A list of ToolCall objects.
     * @return This message instance for chaining.
     */
    public ChatMessage setToolCalls(List<ToolCall> toolCalls) {
        this.toolCalls = toolCalls;
        return this;
    }
}
