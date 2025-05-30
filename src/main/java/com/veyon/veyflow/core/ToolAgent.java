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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

//This object updates your state with the tool calls and responses automatically, that cuz
//this object doesnt CLONE the state object, it just updates it.

public class ToolAgent {

    private static final Logger log = LoggerFactory.getLogger(ToolAgent.class);
    private static final int MAX_TOOL_ITERATIONS_PER_EXECUTE = 10; // Max iterations for tool calls within one execute() call
    private final Gson gson;
    private final FoundationModelService foundationModelService;
    private final Map<String, ToolService> registeredToolServices; // Assuming this is populated at construction
    private final String modelName;

    public ToolAgent(FoundationModelService foundationModelService, Map<String, ToolService> registeredToolServices, String modelName) {
        this.foundationModelService = foundationModelService;
        this.registeredToolServices = (registeredToolServices != null) ? new HashMap<>(registeredToolServices) : new HashMap<>();
        this.modelName = modelName;
        this.gson = new Gson();
    }

    public AgentTurnResult execute(
        AgentState initialState,
        WorkflowConfig workflowConfig,
        String systemPromptOverride,
        ModelParameters modelParamsOverride
    ) {
        AgentState currentState = initialState; 
        List<AgentTurnResult.ToolExecutionRecord> allToolExecutionRecords = new ArrayList<>();
        List<ChatMessage> newMessagesThisTurn = new ArrayList<>(); // Added: List to collect new messages
        String finalAssistantMessage = null;
        int currentToolIteration = 0;

        List<ToolService> activeToolServices = getActiveToolServices(workflowConfig);
        List<Tool> functionDeclarations = buildFunctionDeclarations(currentState.getTenantId(), activeToolServices, workflowConfig);

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
            
            // Construct assistant ChatMessage using ModelTurnResponse.getAssistantContent() and setToolCalls().
            String assistantContent = modelTurnResponse.getAssistantContent();
            List<ToolCall> toolCallsFromModel = modelTurnResponse.getToolCalls();
            
            ChatMessage assistantMessage = new ChatMessage(ChatMessage.Role.ASSISTANT, assistantContent);
            if (toolCallsFromModel != null && !toolCallsFromModel.isEmpty()) {
                assistantMessage.setToolCalls(toolCallsFromModel);
            }
            currentState.addChatMessage(assistantMessage);
            newMessagesThisTurn.add(assistantMessage); // Added: Collect assistant message

            if (toolCallsFromModel != null && !toolCallsFromModel.isEmpty()) {
                finalAssistantMessage = null; // Not final yet, as tools will be called
                log.info("ToolAgent execute: Detected {} tool calls in iteration {} for tenant {}.", toolCallsFromModel.size(), currentToolIteration, currentState.getTenantId());
                // List<ChatMessage> toolResponses = new ArrayList<>(); // Not needed here, add directly to newMessagesThisTurn and currentState

                for (ToolCall toolCall : toolCallsFromModel) {
                    String toolArgsJson = toolCall.getParameters() != null ? gson.toJson(toolCall.getParameters()) : "{}";
                    log.info("Executing tool: {} with ID: {} and arguments: {}", toolCall.getName(), toolCall.getId(), toolArgsJson);

                    String fullToolName = toolCall.getName();
                    String serviceClassNameFromTool = "";
                    String methodNameFromTool = "";

                    // Analizamos el nombre de la herramienta asumiendo formato SimpleClassName_MethodName
                    int separatorIndex = fullToolName.lastIndexOf('_');
                    if (separatorIndex == -1) {
                        separatorIndex = fullToolName.lastIndexOf('.');
                    }

                    if (separatorIndex != -1 && separatorIndex < fullToolName.length() - 1) {
                        serviceClassNameFromTool = fullToolName.substring(0, separatorIndex);
                        methodNameFromTool = fullToolName.substring(separatorIndex + 1);
                    } else {
                        // Si no hay un separador válido, log error
                        log.error("Could not parse service class name and method name from tool name: '{}'. Expected 'ClassName_MethodName'.", fullToolName);
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
                    
                    // Use new ChatMessage(toolCall.getId(), ChatMessage.Role.TOOL, toolContent).setToolName() for tool responses.
                    ChatMessage toolResponseMessage = new ChatMessage(toolCall.getId(), ChatMessage.Role.TOOL, toolExecutionResultContent);
                    toolResponseMessage.setToolName(toolCall.getName());
                    
                    currentState.addChatMessage(toolResponseMessage);
                    newMessagesThisTurn.add(toolResponseMessage); // Added: Collect tool response message
                    allToolExecutionRecords.add(new AgentTurnResult.ToolExecutionRecord(toolCall.getName(), toolArgsJson, toolExecutionResultContent));
                }
            } else {
                finalAssistantMessage = assistantContent; // Use content from model directly
                // No new messages to add here beyond what was already added from modelTurnResponse.getAssistantMessage()
                break; // Exit loop as we have a final text response
            }
        }
        
        // Ensure chat history in AgentState is what we want to return in AgentTurnResult
        List<ChatMessage> finalChatHistory = currentState.getChatMessages();

        return new AgentTurnResult(finalAssistantMessage, finalChatHistory, newMessagesThisTurn, allToolExecutionRecords);
    }

    private List<ToolService> getActiveToolServices(WorkflowConfig workflowConfig) {
        List<ToolService> availableServices = new ArrayList<>();
        
        // Si no hay workflowConfig, no hay servicios activos
        if (workflowConfig == null) {
            log.debug("No WorkflowConfig provided, no tool services are active.");
            return availableServices;
        }
        
        // Obtener los servicios activos desde el WorkflowConfig
        Set<String> activeServiceClassNames = workflowConfig.getActiveServiceClassNames();
        for (String serviceClassName : activeServiceClassNames) {
            ToolService service = registeredToolServices.get(serviceClassName);
            if (service != null) {
                availableServices.add(service);
                log.debug("Tool service '{}' added (active in WorkflowConfig).", serviceClassName);
            } else {
                log.warn("Service '{}' is active in WorkflowConfig but not registered.", serviceClassName);
            }
        }
        return availableServices;
    }

    private List<Tool> buildFunctionDeclarations(String tenantId, List<ToolService> activeToolServices, WorkflowConfig workflowConfig) {
        List<Tool> functionDeclarations = new ArrayList<>();
        for (ToolService service : activeToolServices) {
            Class<?> serviceClass = service.getClass();
            String serviceFQN = serviceClass.getName();

            for (Method method : serviceClass.getDeclaredMethods()) {
                if (method.isAnnotationPresent(ToolAnnotation.class)) {
                    // Verificar si el método está activo en workflowConfig
                    if (workflowConfig != null && !workflowConfig.isToolMethodActive(serviceFQN, method.getName())) {
                        log.debug("Skipping tool declaration for method {} in service {} as it's not active in WorkflowConfig for tenant {}.", method.getName(), serviceFQN, tenantId);
                        continue;
                    }
                    
                    ToolAnnotation toolAnnotation = method.getAnnotation(ToolAnnotation.class);
                    Tool function = new Tool();
                    function.setName(serviceClass.getSimpleName() + "." + method.getName());
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
        
        // Intentar buscar por nombre completo como fallback
        for (ToolService service : services) {
            if (service.getClass().getName().equals(className)) {
                log.debug("Found service by fully qualified name instead of simple name: {}", className);
                return service;
            }
        }
        
        log.warn("Service with class name '{}' not found in active services list", className);
        return null;
    }
}
