package com.veyon.veyflow.nodes;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import java.util.ArrayList;
import java.util.List;
import com.veyon.veyflow.core.AgentNode;
import com.veyon.veyflow.state.AgentState;
import com.veyon.veyflow.state.ChatMessage;
import com.veyon.veyflow.tools.ToolCall;
import com.veyon.veyflow.tools.ToolCallProcessor;
import com.veyon.veyflow.tools.ToolService;
import com.veyon.veyflow.foundationmodels.FoundationModelService;
import com.veyon.veyflow.foundationmodels.ModelParameters;
import com.veyon.veyflow.foundationmodels.adapters.DefaultResponseAdapter;
import com.veyon.veyflow.foundationmodels.adapters.GeminiResponseAdapter;
import com.veyon.veyflow.foundationmodels.adapters.ModelResponseAdapter;
import com.veyon.veyflow.foundationmodels.adapters.OpenAiResponseAdapter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Node that uses a foundation model to process agent state.
 * This node is responsible for generating responses using LLMs.
 */
public class ModelNode implements AgentNode {
    private static final Logger log = LoggerFactory.getLogger(ModelNode.class);
    
    private final String name;
    private final FoundationModelService modelService;
    private final ModelParameters modelParameters;
    private final String systemPrompt;
    private final List<ToolService> toolServices;
    private final String modelName;
    private final ToolCallProcessor toolCallProcessor;
    private final ModelResponseAdapter responseAdapter;
    private final Map<String, ToolService> toolServicesByName;
    private final int maxToolCalls;
    
    /**
     * Create a new model node.
     * 
     * @param name The name of this node
     * @param modelService The foundation model service to use
     * @param modelName The name of the model to use
     * @param modelParameters The model parameters
     * @param systemPrompt The system prompt for the LLM
     * @param toolServices The tool services available to the model
     * @param detectionMode The mode for detecting tool calls
     * @param maxToolCalls The maximum number of tool calls to allow in a chain
     */
    public ModelNode(
            String name,
            FoundationModelService modelService,
            String modelName,
            ModelParameters modelParameters,
            String systemPrompt,
            List<ToolService> toolServices,
            ToolCallProcessor.DetectionMode detectionMode,
            int maxToolCalls) {
        this.name = name;
        this.modelService = modelService;
        this.modelName = modelName;
        this.modelParameters = modelParameters;
        this.systemPrompt = systemPrompt;
        this.toolServices = toolServices;
        this.toolCallProcessor = new ToolCallProcessor(detectionMode);
        this.responseAdapter = getAdapterForModel(modelName);
        this.maxToolCalls = maxToolCalls;
        
        // Create a map of tool services by name for quick lookup
        this.toolServicesByName = new HashMap<>();
        if (toolServices != null) {
            for (ToolService service : toolServices) {
                // Registrar el servicio por su nombre de clase (para compatibilidad)
                String serviceBaseName = service.getClass().getSimpleName().replace("ToolService", "").toLowerCase();
                toolServicesByName.put(serviceBaseName, service);
                
                // También registrar cada método anotado con @ToolAnnotation
                java.lang.reflect.Method[] methods = service.getClass().getMethods();
                for (java.lang.reflect.Method method : methods) {
                    if (method.isAnnotationPresent(com.veyon.veyflow.tools.ToolAnnotation.class)) {
                        String methodName = method.getName();
                        toolServicesByName.put(methodName, service);
                        log.debug("Registered tool method: {} for service: {}", methodName, serviceBaseName);
                    }
                }
            }
        }
    }
    
    /**
     * Create a new model node with default settings.
     * Uses API_RESPONSE detection mode and a maximum of 10 tool calls.
     * 
     * @param name The name of this node
     * @param modelService The foundation model service to use
     * @param modelName The name of the model to use
     * @param modelParameters The model parameters
     * @param systemPrompt The system prompt for the LLM
     * @param toolServices The tool services available to the model
     */
    public ModelNode(
            String name,
            FoundationModelService modelService,
            String modelName,
            ModelParameters modelParameters,
            String systemPrompt,
            List<ToolService> toolServices) {
        this(name, modelService, modelName, modelParameters, systemPrompt, toolServices, 
             ToolCallProcessor.DetectionMode.API_RESPONSE, 10);
    }
    
    @Override
    public AgentState process(AgentState state) {
        try {
            // Convert tool services to function declarations if needed
            JsonArray functionDeclarations = convertToolServicesToFunctionDeclarations(toolServices);
            
            // Bucle de procesamiento de herramientas
            boolean toolCallsInProgress = true;
            int toolCallCount = 0;
            String assistantResponse = "";
            JsonObject responseJson = null;
            
            while (toolCallsInProgress && toolCallCount < maxToolCalls) {
                // Convert chat messages using the adapter for EACH request from the current state
                JsonArray currentSerializedChatHistory = this.responseAdapter.convertChatHistoryToModelContents(state.getChatMessages(), this.systemPrompt);

                // --- START: Logic to modify last message role for the request --- 
                JsonArray requestContents = new JsonArray();
                if (currentSerializedChatHistory != null && !currentSerializedChatHistory.isEmpty()) {
                    for (int i = 0; i < currentSerializedChatHistory.size(); i++) {
                        JsonObject message = currentSerializedChatHistory.get(i).getAsJsonObject().deepCopy(); // Work with a copy
                        if (i == currentSerializedChatHistory.size() - 1) { // If it's the last message
                            if (message.has("role") && message.get("role").getAsString().equals("model")) {
                                message.addProperty("role", "user"); // Change role from "model" to "user"
                            }
                        }
                        requestContents.add(message);
                    }
                } else if (currentSerializedChatHistory != null) { // Handles case where currentSerializedChatHistory is an empty JsonArray
                    requestContents = currentSerializedChatHistory;
                }
                // --- END: Logic to modify last message role --- 

                // Create the model request
                FoundationModelService.ModelRequest request;
                if (this.responseAdapter instanceof GeminiResponseAdapter) {
                    // Gemini adapter's output (now requestContents) is the "contents" array 
                    // and does not include the system_prompt. Pass systemPrompt separately to ModelRequest.
                    request = new FoundationModelService.ModelRequest(
                        this.modelName, 
                        this.systemPrompt, // For Gemini, systemPrompt is passed directly to ModelRequest
                        requestContents,    // USE MODIFIED requestContents
                        functionDeclarations, 
                        this.modelParameters, 
                        null
                    );
                } else {
                    // For other adapters (e.g., OpenAI), assume the adapter includes the system prompt
                    // within the requestContents if necessary (e.g., as the first message).
                    // So, pass null for systemInstruction to ModelRequest constructor.
                    request = new FoundationModelService.ModelRequest(
                        this.modelName,
                        null, // System prompt is expected to be in requestContents for these adapters
                        requestContents,    // USE MODIFIED requestContents
                        functionDeclarations,
                        this.modelParameters,
                        null
                    );
                }

                log.info("Calling model service with request: {}", request); // Log the actual request being sent
                
                String rawResponse = modelService.generate(request);
                log.debug("Raw response string from model service: {}", rawResponse);

                responseJson = null; // Initialize JsonObject
                String extractedTextPart = ""; // Initialize textual part of the response

                // Attempt to parse the rawResponse as a JsonObject
                // This is crucial because adapters expect a JsonObject
                try {
                    responseJson = JsonParser.parseString(rawResponse).getAsJsonObject();
                    log.debug("Parsed raw response to JsonObject: {}", responseJson);
                } catch (JsonSyntaxException | IllegalStateException e) {
                    // If parsing fails, it might be plain text or a malformed JSON string.
                    // Log the error and treat the rawResponse as plain text for extraction.
                    log.warn("Could not parse rawResponse as JsonObject. Raw response: '{}'. Error: {}. Treating as plain text.", rawResponse, e.getMessage());
                    // In this case, the adapter's extractTextContent might not be suitable if it strictly expects JSON.
                    // However, some adapters might handle this or we might need a different approach for plain text.
                    // For now, we'll let extractTextContent try, or it will use the raw string if responseJson is null.
                    // A robust solution might involve the adapter indicating if it expects JSON or can handle plain text.
                }

                // Extract text content using the adapter. If responseJson is null (due to parse failure),
                // the adapter might still be able to work if it's designed to handle raw strings or if extractTextContent is robust.
                // If responseJson is not null, adapter processes it.
                if (responseJson != null) {
                    extractedTextPart = this.responseAdapter.extractTextContent(responseJson);
                } else {
                    // Fallback if JSON parsing failed: use the raw string directly.
                    // This assumes the response was plain text if not valid JSON.
                    extractedTextPart = rawResponse.trim(); 
                }
                
                // The rest of the tool processing logic relies on 'responseJson' for tool calls
                // and 'extractedTextPart' for the assistant's textual response.

                assistantResponse = (extractedTextPart == null) ? "" : extractedTextPart;
                log.info("Extracted text part from response: {}", assistantResponse);

                // Detect tool calls
                List<ToolCall> toolCalls = new ArrayList<>();
                if (responseJson != null) {
                    toolCalls = toolCallProcessor.detectToolCalls(responseJson, assistantResponse);
                }
                
                if (!toolCalls.isEmpty()) {
                    toolCallCount++;
                    log.debug("Tool call detected in node {}: {}", name, toolCalls.get(0).getName());
                    
                    // 1. Add ASSISTANT message with tool call information to AgentState
                    // The raw 'responseJson' contains the model's decision to call tools.
                    // The 'assistantResponse' is the textual part that might accompany the tool call.
                    ChatMessage assistantToolCallMessage = new ChatMessage(
                        UUID.randomUUID().toString(),
                        ChatMessage.Role.ASSISTANT,
                        assistantResponse // This can be null or empty if the model only outputs a tool call
                    );

                    // Store the raw tool call structure (e.g., function_call or tool_calls object/array)
                    // This assumes 'responseJson' contains the part of the model's output that details the tool call.
                    // For OpenAI, this would be responseJson.get("function_call") or responseJson.get("tool_calls")
                    // For Gemini, this would be responseJson.get("tool_calls") or the relevant part of 'candidates[0].content.parts[0].functionCall'
                    // We'll use a generic key "tool_calls_raw" for now, and the adapter should know how to format this.
                    // The ToolCall objects from toolCallProcessor.detectToolCalls might be more structured.
                    // Let's try to serialize the list of ToolCall objects.
                    // NOTE (LINT d58712b0-5945-4a8e-82a4-84a23fb1a7fe): This block constructs a JSON representation of detected tool calls.
                    // This metadata is added to the ChatMessage and can be used by ModelResponseAdapters 
                    // to format the tool calls for subsequent model requests, or for logging/debugging purposes.
                    // If adapters primarily use raw model responses or other mechanisms, this specific metadata might be unused by them.
                    // For now, it's preserved for potential utility.
                    JsonArray toolCallsJson = new JsonArray();
                    for (ToolCall tc : toolCalls) {
                        JsonObject toolCallJson = new JsonObject();
                        toolCallJson.addProperty("id", tc.getId()); // Assuming ToolCall has an ID, if not, generate one or adapt
                        toolCallJson.addProperty("type", "function"); // Common for OpenAI
                        JsonObject functionJson = new JsonObject();
                        functionJson.addProperty("name", tc.getName());
                        functionJson.addProperty("arguments", tc.getParameters().toString()); // Parameters are already a JsonObject
                        toolCallJson.add("function", functionJson);
                        toolCallsJson.add(toolCallJson);
                    }
                    // If assistantResponse is null and toolCalls exist, some models expect content to be null.
                    // Others might expect an empty string. Let's stick with assistantResponse as is for now.
                    // The actual raw JSON from the model (responseJson) is likely what needs to be partially re-added.
                    // For now, we'll add the detected toolCalls to metadata. The convertChatMessagesToJsonArray will need to handle this.
                    assistantToolCallMessage.addMetadata("tool_calls", toolCallsJson); // Storing the structured tool calls


                    state.addChatMessage(assistantToolCallMessage);
                    // contents will be rebuilt later with this new message

                    // Iterate over all detected tool calls and process them
                    for (ToolCall toolCall : toolCalls) { 
                        String toolName = toolCall.getName();
                        JsonObject parameters = toolCall.getParameters();

                        ToolService toolService = toolServicesByName.get(toolName);
                        if (toolService == null) {
                            log.warn("Tool not found: {} in node {}", toolName, name);
                            ChatMessage toolErrorMessage = new ChatMessage(
                                UUID.randomUUID().toString(),
                                ChatMessage.Role.SYSTEM, // Or TOOL with error content?
                                "Tool not found: " + toolName
                            );
                            state.addChatMessage(toolErrorMessage);
                            // If one tool is not found, we might want to stop further tool processing in this chain for this turn.
                            // However, the API expects a response for EACH tool_call_id declared by the assistant.
                            // So, we should probably send a tool message indicating the error for this specific tool_call_id.
                            ChatMessage errorToolResponse = ChatMessage.toolMessage(toolName, "{\"error\": \"Tool not found: " + toolName + "\"}");
                            if (toolCall.getId() != null) {
                                errorToolResponse.addMetadata("tool_call_id", toolCall.getId());
                                log.info("MN: Added tool_call_id '{}' for ERROR tool message (name: {})", toolCall.getId(), toolName);
                            } else {
                                log.warn("MN: toolCall.getId() was NULL for toolName: {} during error reporting. tool_call_id NOT added.", toolName);
                            }
                            state.addChatMessage(errorToolResponse); // Add error response for this specific tool call
                            // Continue to the next tool call if any, or the loop will end.
                        } else {
                            log.debug("Executing tool: {} with parameters: {}", toolName, parameters);
                            try {
                                JsonObject result = toolService.executeToolMethod(toolName, parameters, state);

                                // 2. Add TOOL message with execution result to AgentState
                                ChatMessage toolResultMessage = ChatMessage.toolMessage(toolName, result.toString());
                                // Ensure the tool_call_id from the original ToolCall object is added to metadata
                                if (toolCall.getId() != null) {
                                    toolResultMessage.addMetadata("tool_call_id", toolCall.getId());
                                    log.info("MN: Added tool_call_id '{}' to metadata for tool message (name: {}, content: {}...)", 
                                             toolCall.getId(), toolResultMessage.getToolName(), 
                                             (toolResultMessage.getContent() != null && toolResultMessage.getContent().length() > 20 ? toolResultMessage.getContent().substring(0, 20) : toolResultMessage.getContent()));
                                } else {
                                    log.warn("MN: toolCall.getId() was NULL for toolName: {}. tool_call_id NOT added to metadata.", toolResultMessage.getToolName());
                                }
                                state.addChatMessage(toolResultMessage);

                            } catch (Exception e) {
                                log.error("Error executing tool {}: {}", toolName, e.getMessage(), e);
                                ChatMessage toolErrorMessage = ChatMessage.toolMessage(
                                    toolName,
                                    "{\"error\": \"Error executing tool: " + e.getMessage() + "\"}" // Send error as JSON content
                                );
                                // If we have a toolCall.getId(), we can add it here too for consistency in error reporting
                                if (toolCall.getId() != null) {
                                    toolErrorMessage.addMetadata("tool_call_id", toolCall.getId());
                                    log.info("MN: Added tool_call_id '{}' for EXCEPTION tool message (name: {})", toolCall.getId(), toolName);
                                }
                                state.addChatMessage(toolErrorMessage);
                            }
                        }
                    } // End of loop for processing multiple tool calls
                    
                    // Update contents for the next iteration, including the assistant's tool call and ALL tool results
                    currentSerializedChatHistory = this.responseAdapter.convertChatHistoryToModelContents(state.getChatMessages(), this.systemPrompt);
                } else {
                    // No tool calls, this is the final assistant response for this turn.
                    toolCallsInProgress = false;
                    ChatMessage finalAssistantMessage = new ChatMessage(
                        UUID.randomUUID().toString(),
                        ChatMessage.Role.ASSISTANT,
                        assistantResponse
                    );
                    state.addChatMessage(finalAssistantMessage);
                    // 'contents' for the *next* user turn will be built from state if this node is re-entered.
                    // For the current execution, this is the end of the loop.
                }
            } // End of while(toolCallsInProgress)

            // If loop finished due to maxToolCalls, add the last assistantResponse if any.
            // Otherwise, the final assistant message was already added if no tool calls were made.
            if (toolCallCount >= maxToolCalls && assistantResponse != null && !assistantResponse.isEmpty()) {
                // Check if the last message in state is already this assistantResponse
                List<ChatMessage> currentMessages = state.getChatMessages();
                boolean alreadyAdded = false;
                if (!currentMessages.isEmpty()) {
                    ChatMessage lastMessage = currentMessages.get(currentMessages.size() - 1);
                    if (lastMessage.getRole() == ChatMessage.Role.ASSISTANT && 
                        assistantResponse.equals(lastMessage.getContent()) && 
                        lastMessage.getMetadata().get("tool_calls") == null) { // Ensure it's not a tool call message
                        alreadyAdded = true;
                    }
                }
                if (!alreadyAdded) {
                    ChatMessage finalAssistantMessage = new ChatMessage(
                        UUID.randomUUID().toString(),
                        ChatMessage.Role.ASSISTANT,
                        assistantResponse
                    );
                    state.addChatMessage(finalAssistantMessage);
                }
            }

        } catch (Exception e) {
            log.error("Error in model node processing: {}", e.getMessage(), e);
            // Add error message to chat history
            ChatMessage errorMessage = new ChatMessage(
                UUID.randomUUID().toString(),
                ChatMessage.Role.SYSTEM,
                "Error generating response: " + e.getMessage()
            );
            state.addChatMessage(errorMessage);
            return state;
        }
        
        return state;
    }
    
    @Override
    public String getName() {
        return name;
    }

    /**
     * Get the appropriate model response adapter based on the model name.
     * 
     * @param modelName The name of the model
     * @return A ModelResponseAdapter for the specified model
     */
    private ModelResponseAdapter getAdapterForModel(String modelName) {
        if (modelName == null) {
            log.warn("Model name is null, using DefaultResponseAdapter.");
            return new DefaultResponseAdapter();
        }
        String lowerModelName = modelName.toLowerCase();
        if (lowerModelName.startsWith("gpt")) {
            return new OpenAiResponseAdapter();
        } else if (lowerModelName.contains("gemini")) {
            return new GeminiResponseAdapter();
        } else {
            log.warn("No specific adapter for model {}, using DefaultResponseAdapter.", modelName);
            return new DefaultResponseAdapter();
        }
    }

    /**
     * Converts tool services to function declarations in JsonArray format required by the model.
     * 
     * @param toolServices The list of tool services to convert
     * @return A JsonArray containing the formatted function declarations
     */
    private JsonArray convertToolServicesToFunctionDeclarations(List<ToolService> toolServices) {
        JsonArray functionDeclarations = new JsonArray();

        if (toolServices != null) {
            for (ToolService toolService : toolServices) {
                Method[] methods = toolService.getClass().getMethods();
                for (Method method : methods) {
                    if (method.isAnnotationPresent(com.veyon.veyflow.tools.ToolAnnotation.class)) {
                        com.veyon.veyflow.tools.ToolAnnotation toolAnnotation = method.getAnnotation(com.veyon.veyflow.tools.ToolAnnotation.class);
                        JsonObject functionDeclaration = new JsonObject();
                        functionDeclaration.addProperty("name", method.getName());
                        functionDeclaration.addProperty("description", toolAnnotation.value());

                        JsonObject parametersSchema = new JsonObject();
                        parametersSchema.addProperty("type", "object");
                        JsonObject properties = new JsonObject();
                        JsonArray requiredParameters = new JsonArray();

                        Parameter[] methodParameters = method.getParameters();
                        for (Parameter param : methodParameters) {
                            if (param.isAnnotationPresent(com.veyon.veyflow.tools.ToolParameter.class)) {
                                com.veyon.veyflow.tools.ToolParameter toolParamAnnotation = param.getAnnotation(com.veyon.veyflow.tools.ToolParameter.class);
                                JsonObject paramDetails = new JsonObject();
                                paramDetails.addProperty("type", toolParamAnnotation.type());
                                paramDetails.addProperty("description", toolParamAnnotation.value());
                                properties.add(param.getName(), paramDetails);

                                if (toolParamAnnotation.required()) {
                                    requiredParameters.add(param.getName());
                                }
                            }
                        }
                        parametersSchema.add("properties", properties);
                        if (requiredParameters.size() > 0) {
                            parametersSchema.add("required", requiredParameters);
                        }
                        functionDeclaration.add("parameters", parametersSchema);
                        functionDeclarations.add(functionDeclaration);
                    }
                }
            }
        }
        return functionDeclarations;
    }
}
