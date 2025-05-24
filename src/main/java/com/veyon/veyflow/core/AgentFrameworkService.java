package com.veyon.veyflow.core;

import com.veyon.veyflow.config.AgentConfig;
import com.veyon.veyflow.state.AgentState;
import com.veyon.veyflow.state.ChatMessage;
import com.veyon.veyflow.tools.ToolService;
import com.veyon.veyflow.nodes.ModelNode;
import com.veyon.veyflow.foundationmodels.FoundationModelService;
import com.veyon.veyflow.foundationmodels.ModelParameters;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.UUID;
import java.util.ArrayList;

/**
 * Service class for the custom agent framework.
 * This class provides a high-level API for creating and executing agent workflows.
 */
@Service
public class AgentFrameworkService {
    private static final Logger log = LoggerFactory.getLogger(AgentFrameworkService.class);
    
    private final FoundationModelService modelService;
    private final List<ToolService> toolServices;
    
    // Cache of workflows by tenant
    private final Map<String, Map<String, AgentWorkflow>> workflowsByTenant = new HashMap<>();
    
    // Cache of state by thread ID
    private final Map<String, AgentState> stateByThreadId = new HashMap<>();
    
    /**
     * Create a new agent framework service.
     * 
     * @param modelService The foundation model service
     * @param toolServices The tool services
     */
    @Autowired
    public AgentFrameworkService(
            FoundationModelService modelService,
            List<ToolService> toolServices) {
        this.modelService = modelService;
        this.toolServices = toolServices;
    }
    
    /**
     * Create a standard chat agent workflow.
     * 
     * @param tenantId The tenant ID
     * @param workflowName The name of the workflow
     * @param systemPrompt The system prompt for the LLM
     * @param modelParameters The model parameters
     * @return The created workflow
     */
    public AgentWorkflow createChatAgentWorkflow(
            String tenantId,
            String workflowName,
            String systemPrompt,
            ModelParameters modelParameters) {
        
        // Create the model node
        ModelNode modelNode = new ModelNode(
                "model",
                modelService,
                ModelParameters.defaults().toString(), // Using default model name from parameters
                modelParameters,
                systemPrompt,
                toolServices
        );
        
        // Create the workflow
        AgentWorkflow workflow = new AgentWorkflow("model");
        workflow.addNode(modelNode);
        
        // Store the workflow
        workflowsByTenant
            .computeIfAbsent(tenantId, k -> new HashMap<>())
            .put(workflowName, workflow);
        
        return workflow;
    }
    
    /**
     * Process a user message using a workflow.
     * 
     * @param tenantId The tenant ID
     * @param threadId The thread ID
     * @param workflowName The name of the workflow
     * @param userMessage The user message
     * @return The updated state
     */
    public AgentState processUserMessage(
            String tenantId,
            String threadId,
            String workflowName,
            String userMessage) {
        
        AgentConfig config = AgentConfig.builder()
                .tenantId(tenantId)
                .threadId(threadId)
                .build();
                
        return processUserMessage(workflowName, userMessage, config);
    }
    
    /**
     * Process a user message using a workflow with custom configuration.
     * 
     * @param workflowName The name of the workflow
     * @param userMessage The user message
     * @param config The agent configuration
     * @return The updated state
     */
    public AgentState processUserMessage(
            String workflowName,
            String userMessage,
            AgentConfig config) {
        
        String tenantId = config.getTenantId();
        String threadId = config.getThreadId();
        
        log.debug("Processing user message for tenant: {}, thread: {}, workflow: {}", 
                tenantId, threadId, workflowName);
        
        // Get or create the workflow
        AgentWorkflow workflow = getWorkflow(tenantId, workflowName);
        if (workflow == null) {
            throw new IllegalArgumentException("Workflow not found: " + workflowName);
        }
        
        // Get or create the state
        AgentState state = getOrCreateState(tenantId, threadId);
        
        // Add custom parameters from config to state
        Map<String, Object> parameters = config.getAllParameters();
        for (Map.Entry<String, Object> entry : parameters.entrySet()) {
            state.set(entry.getKey(), entry.getValue());
        }
        
        // Add the user message
        ChatMessage message = new ChatMessage(UUID.randomUUID().toString(), ChatMessage.Role.USER, userMessage);
        state.addChatMessage(message);
        
        // Execute the workflow
        state = workflow.execute(state);
        
        // Save the state
        persistState(tenantId, threadId, state);
        
        return state;
    }
    
    /**
     * Get a workflow by tenant and name.
     * 
     * @param tenantId The tenant ID
     * @param workflowName The name of the workflow
     * @return The workflow, or null if not found
     */
    public AgentWorkflow getWorkflow(String tenantId, String workflowName) {
        Map<String, AgentWorkflow> workflows = workflowsByTenant.get(tenantId);
        if (workflows == null) {
            return null;
        }
        return workflows.get(workflowName);
    }
    
    /**
     * Get or create a state for a thread.
     * 
     * @param tenantId The tenant ID
     * @param threadId The thread ID
     * @return The state
     */
    public AgentState getOrCreateState(String tenantId, String threadId) {
        AgentConfig config = AgentConfig.builder()
                .tenantId(tenantId)
                .threadId(threadId)
                .build();
                
        return getOrCreateState(config);
    }
    
    /**
     * Get or create a state for a thread using AgentConfig.
     * 
     * @param config The agent configuration
     * @return The state
     */
    public AgentState getOrCreateState(AgentConfig config) {
        String tenantId = config.getTenantId();
        String threadId = config.getThreadId();
        
        // Try to get from cache first
        AgentState state = stateByThreadId.get(threadId);
        if (state != null) {
            return state;
        }
        
        // Try to load from database
        state = loadState(tenantId, threadId);
        if (state != null) {
            stateByThreadId.put(threadId, state);
            return state;
        }
        
        // Create new state
        state = new AgentState(tenantId);
        
        // Add custom parameters from config to state
        Map<String, Object> parameters = config.getAllParameters();
        for (Map.Entry<String, Object> entry : parameters.entrySet()) {
            state.set(entry.getKey(), entry.getValue());
        }
        
        stateByThreadId.put(threadId, state);
        return state;
    }
    
    /**
     * Load state from database.
     * This is a placeholder - you would implement this to load from your database.
     * 
     * @param tenantId The tenant ID
     * @param threadId The thread ID
     * @return The loaded state, or null if not found
     */
    private AgentState loadState(String tenantId, String threadId) {
        AgentConfig config = AgentConfig.builder()
                .tenantId(tenantId)
                .threadId(threadId)
                .build();
                
        return loadState(config);
    }
    
    /**
     * Load state from database using AgentConfig.
     * This is a placeholder - you would implement this to load from your database.
     * 
     * @param config The agent configuration
     * @return The loaded state, or null if not found
     */
    private AgentState loadState(AgentConfig config) {
        String tenantId = config.getTenantId();
        String threadId = config.getThreadId();
        
        // Implement this to load from your database
        // For example, you might use a repository to load the state
        
        // StateEntity stateEntity = stateRepository.findByThreadId(threadId);
        // if (stateEntity != null) {
        //     String stateJson = stateEntity.getStateJson();
        //     return AgentState.fromJson(stateJson);
        // }
        
        return null;
    }
    
    /**
     * Persist state to database.
     * This is a placeholder - you would implement this to save to your database.
     * 
     * @param tenantId The tenant ID
     * @param threadId The thread ID
     * @param state The state to persist
     */
    private void persistState(String tenantId, String threadId, AgentState state) {
        AgentConfig config = AgentConfig.builder()
                .tenantId(tenantId)
                .threadId(threadId)
                .build();
                
        persistState(config, state);
    }
    
    /**
     * Persist state to database using AgentConfig.
     * This is a placeholder - you would implement this to save to your database.
     * 
     * @param config The agent configuration
     * @param state The state to persist
     */
    private void persistState(AgentConfig config, AgentState state) {
        String tenantId = config.getTenantId();
        String threadId = config.getThreadId();
        
        // Implement this to save to your database
        // For example, you might use a repository to save the state
        
        // Here's how you might serialize the state
        String stateJson = state.toJson();
        
        // Then save to database
        // stateRepository.save(new StateEntity(threadId, tenantId, stateJson));
        
        log.debug("Persisted state for thread: {}, state size: {} bytes", 
                threadId, stateJson.length());
    }
    
    /**
     * Convert a JSON string of messages to chat messages.
     * 
     * @param messagesJson The JSON string of messages
     * @return List of chat messages
     */
    public List<ChatMessage> convertJsonToChatMessages(String messagesJson) {
        List<ChatMessage> messages = new ArrayList<>();
        
        try {
            JsonObject json = JsonParser.parseString(messagesJson).getAsJsonObject();
            // Implement conversion logic based on your message format
            // This is just an example
            
            return messages;
        } catch (Exception e) {
            log.error("Error converting JSON to chat messages: {}", e.getMessage(), e);
            return messages;
        }
    }
    
    /**
     * Reset a thread's state.
     * 
     * @param tenantId The tenant ID
     * @param threadId The thread ID
     */
    public void resetThread(String tenantId, String threadId) {
        AgentConfig config = AgentConfig.builder()
                .tenantId(tenantId)
                .threadId(threadId)
                .build();
                
        resetThread(config);
    }
    
    /**
     * Reset a thread's state using AgentConfig.
     * 
     * @param config The agent configuration
     */
    public void resetThread(AgentConfig config) {
        String threadId = config.getThreadId();
        
        // Remove from cache
        stateByThreadId.remove(threadId);
        
        // Remove from database
        // stateRepository.deleteByThreadId(threadId);
        
        log.debug("Reset thread state for thread: {}", threadId);
    }
    
}
