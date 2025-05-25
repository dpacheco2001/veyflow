package com.veyon.veyflow.core;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.veyon.veyflow.foundationmodels.FoundationModelService;
import com.veyon.veyflow.foundationmodels.FoundationModelService.ModelRequest;
import com.veyon.veyflow.foundationmodels.ModelParameters;
import com.veyon.veyflow.foundationmodels.ModelTurnResponse;
import com.veyon.veyflow.state.AgentState;
import com.veyon.veyflow.state.ChatMessage;
import com.veyon.veyflow.tools.Tool;
import com.veyon.veyflow.tools.ToolCall;
import com.veyon.veyflow.tools.ToolService;
import com.veyon.veyflow.tools.Parameter;
import com.veyon.veyflow.tools.ToolAnnotation;
import com.veyon.veyflow.tools.ToolParameter;
import com.veyon.veyflow.config.WorkflowConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ToolAgent {

    private static final Logger log = LoggerFactory.getLogger(ToolAgent.class);
    private static final int MAX_TOOL_ITERATIONS_PER_EXECUTE = 10; // Max iterations for tool calls within one execute() call
    private final Gson gson;
    private final FoundationModelService foundationModelService;
    private final Map<String, ToolService> registeredToolServices; // Assuming this is populated at construction
    private final String modelName;

    public ToolAgent(FoundationModelService foundationModelService, Map<String, ToolService> registeredToolServices, String modelName, Gson gson) {
        this.foundationModelService = foundationModelService;
        this.registeredToolServices = (registeredToolServices != null) ? new HashMap<>(registeredToolServices) : new HashMap<>();
        this.modelName = modelName;
        this.gson = gson;
    }

    public AgentTurnResult execute(
        AgentState initialState,
        String userQuery,
        WorkflowConfig workflowConfig,
        String systemPromptOverride,
        ModelParameters modelParamsOverride,
        List<String> nodeRequestedToolNames
    ) {
        AgentState currentState = initialState; 
        List<AgentTurnResult.ToolExecutionRecord> allToolExecutionRecords = new ArrayList<>();
        String finalAssistantMessage = null;
        int currentToolIteration = 0;

        // Determine active tool services and declarations
        List<ToolService> activeToolServices = determineActiveToolServices(
            (workflowConfig != null && workflowConfig.getEnabledToolServiceClassNames() != null) ? workflowConfig.getEnabledToolServiceClassNames() : Collections.emptyList(), 
            nodeRequestedToolNames
        );
        List<Tool> functionDeclarations = buildFunctionDeclarations(currentState.getTenantId(), activeToolServices);

        if (userQuery != null && !userQuery.isEmpty()) {
            currentState.addChatMessage(new ChatMessage(ChatMessage.Role.USER, userQuery));
        }

        // Determine System Prompt
        String effectiveSystemPrompt = "You are a helpful assistant."; // Default system prompt
        if (systemPromptOverride != null && !systemPromptOverride.isEmpty()) {
            effectiveSystemPrompt = systemPromptOverride;
        } 

        // Determine Model Parameters
        ModelParameters effectiveModelParams = ModelParameters.defaults();
        if (modelParamsOverride != null) {
            effectiveModelParams = modelParamsOverride;
        }

        // Determine Additional Config - Set to null as WorkflowConfig methods are unavailable
        Map<String, Object> additionalConfig = null;

        ModelTurnResponse modelTurnResponse = null;

        while (currentToolIteration < MAX_TOOL_ITERATIONS_PER_EXECUTE) {
            currentToolIteration++;
            log.info("ToolAgent execute: Iteration {}/{} for tenant {}", currentToolIteration, MAX_TOOL_ITERATIONS_PER_EXECUTE, currentState.getTenantId());

            ModelRequest modelRequest = new ModelRequest(
                this.modelName, 
                effectiveSystemPrompt,
                currentState.getChatMessages(), 
                functionDeclarations, 
                effectiveModelParams,
                additionalConfig 
            );

            modelTurnResponse = foundationModelService.generate(modelRequest);

            String assistantContentThisTurn = modelTurnResponse.getAssistantContent();
            List<ToolCall> toolCallsThisTurn = modelTurnResponse.getToolCalls();
            
            ChatMessage assistantMessageForHistory = new ChatMessage(ChatMessage.Role.ASSISTANT, assistantContentThisTurn);
            if (toolCallsThisTurn != null && !toolCallsThisTurn.isEmpty()) {
                assistantMessageForHistory.setToolCalls(toolCallsThisTurn);
            }
            currentState.addChatMessage(assistantMessageForHistory);

            if (toolCallsThisTurn == null || toolCallsThisTurn.isEmpty()) {
                finalAssistantMessage = assistantContentThisTurn;
                log.info("ToolAgent execute: No tool calls from LLM. Final assistant message received for tenant {}.", currentState.getTenantId());
                break; 
            }

            log.info("ToolAgent execute: Detected {} tool calls in iteration {} for tenant {}.", toolCallsThisTurn.size(), currentToolIteration, currentState.getTenantId());
            List<AgentTurnResult.ToolExecutionRecord> turnExecutionRecords = new ArrayList<>();

            for (ToolCall toolCall : toolCallsThisTurn) {
                String toolArgsJson = toolCall.getParameters() != null ? gson.toJson(toolCall.getParameters()) : "{}";
                log.info("Executing tool: {} with ID: {} and arguments: {}", toolCall.getName(), toolCall.getId(), toolArgsJson);

                String fullToolName = toolCall.getName();
                String serviceClassNameFromTool = "";
                String methodNameFromTool = "";

                int separatorIndex = fullToolName.lastIndexOf('_');
                if (separatorIndex == -1) { // Underscore not found, try dot
                    separatorIndex = fullToolName.lastIndexOf('.');
                }

                if (separatorIndex != -1 && separatorIndex < fullToolName.length() - 1) {
                    serviceClassNameFromTool = fullToolName.substring(0, separatorIndex);
                    methodNameFromTool = fullToolName.substring(separatorIndex + 1);
                } else {
                    // If still no valid separator, or format is unexpected, log an error.
                    // This might happen if the tool name is very short or malformed.
                    log.error("Could not parse service class name and method name from tool name: '{}'. Expected 'ClassName_MethodName' or 'ClassName.MethodName'.", fullToolName);
                    // Skip this tool call or handle error appropriately
                    // For now, let it try to find service/method with empty names, which will likely fail gracefully later.
                }

                ToolService service = findToolServiceByClassName(activeToolServices, serviceClassNameFromTool);
                Object result = null;
                String toolExecutionResultContent = "";

                if (service != null && !methodNameFromTool.isEmpty()) {
                    try {
                        Method methodToExecute = null;
                        for (Method m : service.getClass().getMethods()) {
                            if (m.getName().equals(methodNameFromTool)) {
                                methodToExecute = m;
                                break;
                            }
                        }
                        if (methodToExecute == null) throw new NoSuchMethodException("Method " + methodNameFromTool + " not found in service " + serviceClassNameFromTool);

                        java.lang.reflect.Parameter[] methodParams = methodToExecute.getParameters();
                        Object[] argsToPass = new Object[methodParams.length];
                        JsonObject llmArgs = toolCall.getParameters();

                        for (int i = 0; i < methodParams.length; i++) {
                            java.lang.reflect.Parameter param = methodParams[i];
                            if (param.getType() == AgentState.class) {
                                argsToPass[i] = currentState;
                            } else {
                                String paramName = param.getName();
                                if (llmArgs != null && llmArgs.has(paramName)) {
                                    argsToPass[i] = gson.fromJson(llmArgs.get(paramName), param.getType());
                                } else {
                                    if (param.getType().isPrimitive()) throw new IllegalArgumentException("Missing required primitive parameter: " + paramName);
                                    argsToPass[i] = null;
                                }
                            }
                        }
                        result = methodToExecute.invoke(service, argsToPass);
                        toolExecutionResultContent = (result != null) ? gson.toJson(result) : "";
                    } catch (Exception e) {
                        log.error("Error executing tool {}: {}", toolCall.getName(), e.getMessage(), e);
                        toolExecutionResultContent = "Error: " + e.getMessage();
                    }
                } else {
                    toolExecutionResultContent = "Error: Service for " + toolCall.getName() + " not found or method name invalid.";
                    log.error(toolExecutionResultContent);
                }
                turnExecutionRecords.add(new AgentTurnResult.ToolExecutionRecord(toolCall.getName(), toolArgsJson, toolExecutionResultContent));
                currentState.addChatMessage(new ChatMessage(toolCall.getId(), ChatMessage.Role.TOOL, toolExecutionResultContent));
            }
            allToolExecutionRecords.addAll(turnExecutionRecords);

            if (currentToolIteration >= MAX_TOOL_ITERATIONS_PER_EXECUTE) {
                log.warn("ToolAgent execute: Reached max tool iterations ({}) for tenant {}. Returning current state without further LLM calls.", MAX_TOOL_ITERATIONS_PER_EXECUTE, currentState.getTenantId());
                finalAssistantMessage = assistantContentThisTurn; // The LLM was about to ask for more tools or give a partial response
                break;
            }
        } 

        // Use the existing AgentTurnResult constructor
        // This means tenantId, detailed turnCount, rawModelResponse, token counts are not set here
        // AgentTurnResult may need to be enhanced later if this information is required.
        return new AgentTurnResult(finalAssistantMessage, currentState.getChatMessages(), allToolExecutionRecords);
    }

    private List<ToolService> determineActiveToolServices(List<String> globallyEnabledToolServiceClassNames, List<String> nodeRequestedToolNames) {
        List<ToolService> availableServices = new ArrayList<>();

        if (nodeRequestedToolNames != null && !nodeRequestedToolNames.isEmpty()) {
            for (String serviceClassName : nodeRequestedToolNames) {
                ToolService service = registeredToolServices.get(serviceClassName);
                if (service != null) { 
                    availableServices.add(service);
                    log.debug("Tool service '{}' added (node-requested).", serviceClassName);
                } else {
                    log.warn("Node requested tool service '{}' but it is not registered.", serviceClassName);
                }
            }
        } else {
            for (String serviceClassName : globallyEnabledToolServiceClassNames) {
                ToolService service = registeredToolServices.get(serviceClassName);
                if (service != null) {
                    availableServices.add(service);
                    log.debug("Tool service '{}' added (globally enabled).", serviceClassName);
                } else {
                    log.warn("Globally enabled tool service '{}' but it is not registered.", serviceClassName);
                }
            }
        }
        return availableServices;
    }

    private List<Tool> buildFunctionDeclarations(String tenantId, List<ToolService> activeToolServices) {
        List<Tool> functionDeclarations = new ArrayList<>();
        for (ToolService service : activeToolServices) {
            Class<?> serviceClass = service.getClass();
            String serviceClassName = serviceClass.getSimpleName();

            for (Method method : serviceClass.getDeclaredMethods()) {
                if (method.isAnnotationPresent(ToolAnnotation.class)) {
                    ToolAnnotation toolAnnotation = method.getAnnotation(ToolAnnotation.class);
                    Tool function = new Tool();
                    function.setName(serviceClassName + "_" + method.getName());
                    function.setDescription(toolAnnotation.value()); 

                    List<Parameter> parametersList = new ArrayList<>();
                    for (java.lang.reflect.Parameter reflectParam : method.getParameters()) {
                        if (reflectParam.isAnnotationPresent(ToolParameter.class)) {
                            ToolParameter paramAnnotation = reflectParam.getAnnotation(ToolParameter.class);
                            Parameter schemaParam = new Parameter(); 
                            schemaParam.setName(reflectParam.getName()); 
                            schemaParam.setType(paramAnnotation.type());
                            schemaParam.setDescription(paramAnnotation.value()); 
                            schemaParam.setRequired(paramAnnotation.required());
                            parametersList.add(schemaParam);
                        }
                    }
                    function.setParametersSchema(parametersList); 

                    functionDeclarations.add(function);
                    log.debug("Added tool declaration: {}", function.getName());
                }
            }
        }
        return functionDeclarations;
    }

    private ToolService findToolServiceByClassName(List<ToolService> services, String className) {
        for (ToolService service : services) {
            if (service.getClass().getSimpleName().equals(className)) {
                return service;
            }
        }
        return null;
    }
}
