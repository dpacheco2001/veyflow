package com.veyon.veyflow.state;

import com.google.gson.Gson;
import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.Set;

/**
 * Represents the state of an agent during execution.
 * This state is passed between nodes and is fully serializable.
 */
public class AgentState {
    private Map<String, Object> values;
    private List<ChatMessage> chatMessages;
    private String currentNode;
    private String previousNode;
    private String threadId;
    private String tenantId;
    private PersistenceMode persistenceMode;

    /**
     * Creates a new empty agent state.
     */
    public AgentState() {
        this.values = new HashMap<>();
        this.chatMessages = new ArrayList<>();
        this.currentNode = "";
        this.previousNode = "";
        this.tenantId = "";
        this.threadId = "";
        this.persistenceMode = PersistenceMode.IN_MEMORY;
    }

    /**
     * Creates a new agent state with tenant ID.
     * 
     * @param tenantId The tenant ID
     */
    public AgentState(String tenantId) {
        this();
        this.tenantId = tenantId;
    }

    /**
     * Creates a new agent state with tenant ID and thread ID.
     *
     * @param tenantId The tenant ID
     * @param threadId The thread ID for this state instance
     */
    public AgentState(String tenantId, String threadId) {
        this(tenantId);
        this.threadId = (threadId == null) ? "" : threadId;
    }

    /**
     * Creates a new agent state with tenant ID, thread ID, and persistence mode.
     *
     * @param tenantId The tenant ID
     * @param threadId The thread ID for this state instance
     * @param persistenceMode The persistence mode for this state instance
     */
    public AgentState(String tenantId, String threadId, PersistenceMode persistenceMode) {
        this(tenantId, threadId);
        this.persistenceMode = persistenceMode;
    }

    /**
     * Get a value from the state.
     * 
     * @param key The key for the value
     * @return The value, or null if not found
     */
    @SuppressWarnings("unchecked")
    public <T> T get(String key) {
        return (T) values.get(key);
    }

    /**
     * Set a value in the state.
     * 
     * @param key The key for the value
     * @param value The value to store
     * @return This state instance for chaining
     */
    public AgentState set(String key, Object value) {
        values.put(key, value);
        return this;
    }

    /**
     * Get the current chat messages.
     * 
     * @return List of chat messages
     */
    public List<ChatMessage> getChatMessages() {
        return chatMessages;
    }

    /**
     * Set the chat messages.
     * 
     * @param chatMessages List of chat messages
     * @return This state instance for chaining
     */
    public AgentState setChatMessages(List<ChatMessage> chatMessages) {
        this.chatMessages = chatMessages;
        return this;
    }

    /**
     * Add a new chat message.
     * 
     * @param message The chat message to add
     * @return This state instance for chaining
     */
    public AgentState addChatMessage(ChatMessage message) {
        this.chatMessages.add(message);
        return this;
    }

    /**
     * Get the current node.
     * 
     * @return Current node name
     */
    public String getCurrentNode() {
        return currentNode;
    }

    /**
     * Set the current node.
     * 
     * @param currentNode Current node name
     * @return This state instance for chaining
     */
    public AgentState setCurrentNode(String currentNode) {
        this.previousNode = this.currentNode;
        this.currentNode = currentNode;
        return this;
    }

    /**
     * Get the previous node.
     * 
     * @return Previous node name
     */
    public String getPreviousNode() {
        return previousNode;
    }

    /**
     * Get the thread ID for this state instance.
     * 
     * @return Thread ID
     */
    public String getThreadId() {
        return threadId;
    }

    /**
     * Set the thread ID for this state instance.
     * 
     * @param threadId Thread ID
     * @return This state instance for chaining
     */
    public AgentState setThreadId(String threadId) {
        this.threadId = (threadId == null) ? "" : threadId;
        return this;
    }

    /**
     * Get the tenant ID.
     * 
     * @return Tenant ID
     */
    public String getTenantId() {
        return tenantId;
    }

    /**
     * Set the tenant ID.
     * 
     * @param tenantId Tenant ID
     * @return This state instance for chaining
     */
    public AgentState setTenantId(String tenantId) {
        this.tenantId = tenantId;
        return this;
    }

    /**
     * Get the persistence mode.
     * 
     * @return Persistence mode
     */
    public PersistenceMode getPersistenceMode() {
        return persistenceMode;
    }

    /**
     * Set the persistence mode.
     * 
     * @param persistenceMode Persistence mode
     * @return This state instance for chaining
     */
    public AgentState setPersistenceMode(PersistenceMode persistenceMode) {
        this.persistenceMode = persistenceMode;
        return this;
    }

    /**
     * Serialize the state to JSON.
     * 
     * @return JSON representation of the state
     */
    public String toJson() {
        return new Gson().toJson(this);
    }

    /**
     * Deserialize the state from JSON.
     * 
     * @param json JSON representation of the state
     * @return The deserialized state
     */
    public static AgentState fromJson(String json) {
        return new Gson().fromJson(json, AgentState.class);
    }
    
    /**
     * Get all keys in the state.
     * 
     * @return Set of keys
     */
    public Set<String> getKeys() {
        return values.keySet();
    }
}
