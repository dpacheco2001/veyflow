package com.veyon.veyflow.nodes;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.veyon.veyflow.foundationmodels.FoundationModelService;
import com.veyon.veyflow.foundationmodels.ModelParameters;
import com.veyon.veyflow.state.AgentState;
import com.veyon.veyflow.state.ChatMessage;
import com.veyon.veyflow.tools.ToolCallProcessor;
import com.veyon.veyflow.tools.ToolService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.ArrayList;
import java.util.Arrays;

/**
 * A specialized node that implements the "Plan and Execute" pattern.
 * This node first creates a plan with steps, then executes each step.
 */
public class PlanNode extends ModelNode {
    private static final Logger log = LoggerFactory.getLogger(PlanNode.class);
    
    private static final String PLAN_PROMPT_TEMPLATE = 
        "You are an AI assistant that follows the Plan-and-Execute framework." +
        "For each task, first create a clear plan with numbered steps." +
        "Then execute each step in order, using tools when necessary." +
        "\n\n%s"; // Placeholder for additional system prompt
    
    /**
     * Create a new Plan node.
     * 
     * @param name The name of this node
     * @param modelService The foundation model service to use
     * @param modelName The name of the model to use
     * @param modelParameters The model parameters
     * @param systemPrompt Additional system prompt to append to the planning framework
     * @param toolServices The tool services available to the model
     * @param detectionMode The mode for detecting tool calls
     * @param maxToolCalls The maximum number of tool calls to allow in a chain
     */
    public PlanNode(
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
            String.format(PLAN_PROMPT_TEMPLATE, systemPrompt),
            toolServices,
            detectionMode,
            maxToolCalls
        );
        log.info("Created PlanNode '{}' with {} tools", name, toolServices != null ? toolServices.size() : 0);
    }
    
    /**
     * Create a new Plan node with default settings.
     * 
     * @param name The name of this node
     * @param modelService The foundation model service to use
     * @param modelName The name of the model to use
     * @param modelParameters The model parameters
     * @param systemPrompt Additional system prompt to append to the planning framework
     * @param toolServices The tool services available to the model
     */
    public PlanNode(
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
            15 // Mayor límite de llamadas a herramientas por defecto
        );
    }
    
    /**
     * Override the process method to implement the Plan-and-Execute pattern.
     */
    @Override
    public AgentState process(AgentState state) {
        try {
            // Phase 1: Create a plan
            log.debug("PlanNode '{}' - Phase 1: Creating plan", getName());
            
            AgentState planningState = createPlan(state);
            List<String> plan = extractPlanSteps(planningState);
            
            if (plan.isEmpty()) {
                log.warn("PlanNode '{}' - No plan steps extracted", getName());
                return super.process(state); // Fallback to standard processing
            }
            
            // Add the plan to the state
            ChatMessage planMessage = new ChatMessage(
                UUID.randomUUID().toString(),
                ChatMessage.Role.SYSTEM,
                "Plan:\n" + String.join("\n", plan)
            );
            state.addChatMessage(planMessage);
            
            // Phase 2: Execute each step
            log.debug("PlanNode '{}' - Phase 2: Executing {} plan steps", getName(), plan.size());
            
            for (int i = 0; i < plan.size(); i++) {
                String step = plan.get(i);
                log.debug("PlanNode '{}' - Executing step {}/{}: {}", getName(), i+1, plan.size(), step);
                
                // Create a step message
                ChatMessage stepMessage = new ChatMessage(
                    UUID.randomUUID().toString(),
                    ChatMessage.Role.SYSTEM,
                    "Executing step " + (i+1) + ": " + step
                );
                state.addChatMessage(stepMessage);
                
                // Execute the step (using the parent class implementation for tool handling)
                state = executeStep(state, step, i+1, plan.size());
            }
            
            // Final summary
            ChatMessage summaryMessage = new ChatMessage(
                UUID.randomUUID().toString(),
                ChatMessage.Role.SYSTEM,
                "Plan execution completed"
            );
            state.addChatMessage(summaryMessage);
            
            return state;
        } catch (Exception e) {
            log.error("Error in PlanNode processing: {}", e.getMessage(), e);
            ChatMessage errorMessage = new ChatMessage(
                UUID.randomUUID().toString(),
                ChatMessage.Role.SYSTEM,
                "Error in plan execution: " + e.getMessage()
            );
            state.addChatMessage(errorMessage);
            return state;
        }
    }
    
    /**
     * Create a plan based on the initial state.
     * 
     * @param state The initial state
     * @return The state with planning messages added
     */
    private AgentState createPlan(AgentState state) {
        // Add a planning instruction
        ChatMessage planningMessage = new ChatMessage(
            UUID.randomUUID().toString(),
            ChatMessage.Role.SYSTEM,
            "First, create a plan with numbered steps for completing this task. " +
            "Format your plan as: \"1. First step\\n2. Second step\\n...\""
        );
        state.addChatMessage(planningMessage);
        
        // Use the parent class to process the planning request
        return super.process(state);
    }
    
    /**
     * Extract plan steps from the state messages.
     * 
     * @param state The state with planning messages
     * @return List of plan steps
     */
    private List<String> extractPlanSteps(AgentState state) {
        List<String> steps = new ArrayList<>();
        
        // Get the last assistant message
        List<ChatMessage> messages = state.getChatMessages();
        String planText = "";
        
        for (int i = messages.size() - 1; i >= 0; i--) {
            ChatMessage message = messages.get(i);
            if (message.getRole() == ChatMessage.Role.ASSISTANT) {
                planText = message.getContent();
                break;
            }
        }
        
        // Extract numbered steps using regex
        Pattern stepPattern = Pattern.compile("(\\d+)[.)]\\s*([^\\d\\n].+?)(?=\\s*(?:\\d+[.)]|$))", Pattern.DOTALL);
        Matcher matcher = stepPattern.matcher(planText);
        
        while (matcher.find()) {
            steps.add(matcher.group(2).trim());
        }
        
        // If regex didn't work, try simple line-by-line approach
        if (steps.isEmpty() && planText.contains("\n")) {
            String[] lines = planText.split("\n");
            for (String line : lines) {
                line = line.trim();
                if (line.matches("\\d+[.)]\\s+.+")) {
                    steps.add(line.replaceFirst("\\d+[.)]\\s+", ""));
                }
            }
        }
        
        return steps;
    }
    
    /**
     * Execute a single plan step.
     * 
     * @param state The current state
     * @param step The step description
     * @param stepNumber The current step number
     * @param totalSteps The total number of steps
     * @return The updated state
     */
    private AgentState executeStep(AgentState state, String step, int stepNumber, int totalSteps) {
        // Add a step execution instruction
        ChatMessage stepInstruction = new ChatMessage(
            UUID.randomUUID().toString(),
            ChatMessage.Role.USER,
            "Execute step " + stepNumber + "/" + totalSteps + ": " + step + "\n" +
            "Use available tools if needed to complete this specific step."
        );
        state.addChatMessage(stepInstruction);
        
        // Use the parent class to process this step (with tool handling)
        return super.process(state);
    }
}
