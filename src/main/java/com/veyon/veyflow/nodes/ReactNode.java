package com.veyon.veyflow.nodes;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.veyon.veyflow.foundationmodels.FoundationModelService;
import com.veyon.veyflow.foundationmodels.ModelParameters;
import com.veyon.veyflow.state.AgentState;
import com.veyon.veyflow.tools.ToolCallProcessor;
import com.veyon.veyflow.tools.ToolService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * A specialized node that implements the ReAct (Reasoning and Acting) pattern.
 * This node extends ModelNode with specific optimizations and prompting for ReAct.
 */
public class ReactNode extends ModelNode {
    private static final Logger log = LoggerFactory.getLogger(ReactNode.class);
    
    private static final String REACT_PROMPT_TEMPLATE = 
        "You are an AI assistant that follows the ReAct (Reasoning and Acting) framework." +
        "For each task, think step-by-step and reason about what to do next." +
        "When you need information, use the available tools." +
        "Follow this format for your reasoning:\n\n" +
        "Thought: Reason about what needs to be done and how to accomplish it.\n" +
        "Action: Use a tool if needed (e.g., search, calculate, etc.)\n" +
        "Observation: Examine the result from the tool.\n" +
        "... (repeat Thought/Action/Observation as needed)\n" +
        "Answer: Provide your final answer based on your reasoning and observations.\n\n" +
        "%s"; // Placeholder for additional system prompt
    
    /**
     * Create a new ReAct node with enhanced prompting.
     * 
     * @param name The name of this node
     * @param modelService The foundation model service to use
     * @param modelName The name of the model to use
     * @param modelParameters The model parameters
     * @param systemPrompt Additional system prompt to append to the ReAct framework
     * @param toolServices The tool services available to the model
     * @param detectionMode The mode for detecting tool calls
     * @param maxToolCalls The maximum number of tool calls to allow in a chain
     */
    public ReactNode(
            String name,
            FoundationModelService modelService,
            String modelName,
            ModelParameters modelParameters,
            String systemPrompt,
            List<ToolService> toolServices,
            ToolCallProcessor.DetectionMode detectionMode,
            int maxToolCalls) {
        super(
            name,
            modelService,
            modelName,
            modelParameters,
            String.format(REACT_PROMPT_TEMPLATE, systemPrompt),
            toolServices,
            detectionMode,
            maxToolCalls
        );
        log.info("Created ReactNode '{}' with {} tools", name, toolServices != null ? toolServices.size() : 0);
    }
    
    /**
     * Create a new ReAct node with default settings.
     * Uses API_RESPONSE detection mode and a maximum of 10 tool calls.
     * 
     * @param name The name of this node
     * @param modelService The foundation model service to use
     * @param modelName The name of the model to use
     * @param modelParameters The model parameters
     * @param systemPrompt Additional system prompt to append to the ReAct framework
     * @param toolServices The tool services available to the model
     */
    public ReactNode(
            String name,
            FoundationModelService modelService,
            String modelName,
            ModelParameters modelParameters,
            String systemPrompt,
            List<ToolService> toolServices) {
        this(
            name,
            modelService,
            modelName,
            modelParameters,
            systemPrompt,
            toolServices,
            ToolCallProcessor.DetectionMode.API_RESPONSE,
            10
        );
    }
    
    /**
     * Specialized method to parse ReAct format responses.
     * This could be extended to parse the Thought/Action/Observation format.
     * 
     * @param text The response text
     * @return A JsonObject with parsed ReAct components
     */
    protected JsonObject parseReactResponse(String text) {
        // Implementación básica por ahora, podría mejorarse para parsear el formato ReAct
        JsonObject result = new JsonObject();
        
        // Buscar patrones de "Thought:", "Action:", "Observation:", "Answer:"
        String thought = extractSection(text, "Thought:", "Action:");
        String action = extractSection(text, "Action:", "Observation:");
        String observation = extractSection(text, "Observation:", "Thought:");
        if (observation.isEmpty()) {
            observation = extractSection(text, "Observation:", "Answer:");
        }
        String answer = extractSection(text, "Answer:", null);
        
        if (!thought.isEmpty()) result.addProperty("thought", thought.trim());
        if (!action.isEmpty()) result.addProperty("action", action.trim());
        if (!observation.isEmpty()) result.addProperty("observation", observation.trim());
        if (!answer.isEmpty()) result.addProperty("answer", answer.trim());
        
        return result;
    }
    
    /**
     * Helper method to extract a section from text between start and end markers.
     * 
     * @param text The text to parse
     * @param startMarker The starting marker
     * @param endMarker The ending marker, can be null for end of text
     * @return The extracted section
     */
    private String extractSection(String text, String startMarker, String endMarker) {
        int start = text.indexOf(startMarker);
        if (start == -1) return "";
        
        start += startMarker.length();
        int end;
        if (endMarker != null) {
            end = text.indexOf(endMarker, start);
            if (end == -1) end = text.length();
        } else {
            end = text.length();
        }
        
        return text.substring(start, end).trim();
    }
}
