package com.veyon.veyflow.core;

import com.veyon.veyflow.foundationmodels.FoundationModelService;
import com.veyon.veyflow.foundationmodels.ModelParameters;
import com.veyon.veyflow.foundationmodels.ModelTurnResponse;
import com.veyon.veyflow.foundationmodels.FoundationModelService.ModelRequest;
import com.veyon.veyflow.state.AgentState;
import com.veyon.veyflow.state.ChatMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map; // Keep for potential future use with additionalConfig

/**
 * LLM class is responsible for direct interaction with a foundation model 
 * to get a response without any tool processing capabilities.
 * It updates the agent state with the user query and the model's response.
 */
public class LLM {

    private static final Logger log = LoggerFactory.getLogger(LLM.class);
    private final FoundationModelService foundationModelService;
    private final String modelName;

    public LLM(FoundationModelService foundationModelService, String modelName) {
        this.foundationModelService = foundationModelService;
        this.modelName = modelName;
    }

    public AgentTurnResult execute(
        AgentState initialState,
        String systemPromptOverride,
        ModelParameters modelParamsOverride
    ) {
        AgentState currentState = initialState;
        List<ChatMessage> newMessagesThisTurn = new ArrayList<>();

        String effectiveSystemPrompt = "You are a helpful assistant."; // Default
        if (systemPromptOverride != null && !systemPromptOverride.isEmpty()) {
            effectiveSystemPrompt = systemPromptOverride;
        }

        ModelParameters effectiveModelParams = ModelParameters.defaults();
        if (modelParamsOverride != null) {
            effectiveModelParams = modelParamsOverride;
        }
        
        // For LLM class, functionDeclarations will be empty as it does not use tools.
        // AdditionalConfig is also set to null for simplicity, can be parameterized if needed.
        Map<String, Object> additionalConfig = null; 

        ModelRequest modelRequest = new ModelRequest(
            this.modelName,
            effectiveSystemPrompt,
            currentState.getChatMessages(),
            Collections.emptyList(), // No tools/functions for LLM class
            effectiveModelParams,
            additionalConfig
        );

        log.info("LLM execute: Sending request to model {} for tenant {}", this.modelName, currentState.getTenantId());
        ModelTurnResponse modelTurnResponse = foundationModelService.generate(modelRequest);

        // Assuming ModelTurnResponse provides a direct ChatMessage for the assistant's response
        // or provides content that can be wrapped into one.
        // For simplicity, let's assume getAssistantContent() and it doesn't involve tool calls.
        String assistantContent = modelTurnResponse.getAssistantContent();
        ChatMessage assistantMessage = new ChatMessage(ChatMessage.Role.ASSISTANT, assistantContent);
        
        currentState.addChatMessage(assistantMessage);
        newMessagesThisTurn.add(assistantMessage);

        log.info("LLM execute: Received response from model for tenant {}", currentState.getTenantId());

        return new AgentTurnResult(
            assistantMessage.getContent(),
            currentState.getChatMessages(),
            newMessagesThisTurn,
            Collections.emptyList() // No tool execution records
        );
    }
}
