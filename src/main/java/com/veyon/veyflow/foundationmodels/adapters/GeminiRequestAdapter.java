package com.veyon.veyflow.foundationmodels.adapters;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import com.veyon.veyflow.foundationmodels.FoundationModelService.ModelRequest;
import com.veyon.veyflow.foundationmodels.ModelParameters;
import com.veyon.veyflow.state.ChatMessage;
import com.veyon.veyflow.tools.Parameter;
import com.veyon.veyflow.tools.Tool;
import com.veyon.veyflow.tools.ToolCall;

/**
 * Adaptador para convertir peticiones genéricas al formato específico de Google Gemini.
 */
public class GeminiRequestAdapter implements ModelRequestAdapter {
    
    private static final String API_BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models/";
    private static final String PROJECT_ID = ""; // Dejar vacío para usar la configuración global de tu proyecto
    
    @Override
    public JsonObject adaptRequest(ModelRequest modelRequest) {
        JsonObject requestBody = new JsonObject();
        Gson gson = new Gson(); // For converting lists/objects to JsonElements if needed

        // Configurar mensajes de contenido
        if (modelRequest.contents() != null) {
            JsonArray newContents = new JsonArray();
            for (ChatMessage chatMessage : modelRequest.contents()) {
                JsonObject originalContentObject = new JsonObject();
                originalContentObject.addProperty("role", chatMessage.getRole().name().toLowerCase());
                if (chatMessage.getContent() != null) {
                    originalContentObject.addProperty("content", chatMessage.getContent());
                }
                if (chatMessage.getToolName() != null) {
                    originalContentObject.addProperty("name", chatMessage.getToolName());
                }

                if (chatMessage.getToolCalls() != null && !chatMessage.getToolCalls().isEmpty()) {
                    JsonArray toolCallsJsonArray = new JsonArray();
                    for (ToolCall tc : chatMessage.getToolCalls()) {
                        JsonObject toolCallJson = new JsonObject();
                        toolCallJson.addProperty("id", tc.getId());
                        toolCallJson.addProperty("type", "function");
                        JsonObject funcDetails = new JsonObject();
                        funcDetails.addProperty("name", tc.getName());
                        funcDetails.add("arguments", tc.getParameters());
                        toolCallJson.add("function", funcDetails);
                        toolCallsJsonArray.add(toolCallJson);
                    }
                    originalContentObject.add("tool_calls", toolCallsJsonArray);
                }

                JsonObject newContentObject = new JsonObject();
                String originalRole = originalContentObject.has("role") ? originalContentObject.get("role").getAsString() : null;

                if ("system".equalsIgnoreCase(originalRole)) {
                    continue;
                }

                JsonArray partsArray = new JsonArray();

                if ("user".equalsIgnoreCase(originalRole)) {
                    newContentObject.addProperty("role", "user");
                    if (originalContentObject.has("content") && originalContentObject.get("content").isJsonPrimitive()) {
                        JsonObject partObject = new JsonObject();
                        partObject.add("text", originalContentObject.get("content"));
                        partsArray.add(partObject);
                    }
                } else if ("assistant".equalsIgnoreCase(originalRole)) {
                    newContentObject.addProperty("role", "model");
                    if (originalContentObject.has("tool_calls") && originalContentObject.get("tool_calls").isJsonArray()) {
                        JsonArray toolCalls = originalContentObject.getAsJsonArray("tool_calls");
                        for (JsonElement toolCallElement : toolCalls) {
                            if (toolCallElement.isJsonObject()) {
                                JsonObject receivedToolCall = toolCallElement.getAsJsonObject();
                                JsonObject functionCallPart = new JsonObject();
                                if (receivedToolCall.has("function") && 
                                    receivedToolCall.getAsJsonObject("function").has("name") && 
                                    receivedToolCall.getAsJsonObject("function").has("arguments")) {
                                    JsonObject functionDetails = receivedToolCall.getAsJsonObject("function");
                                    JsonObject geminiFunctionCall = new JsonObject();
                                    geminiFunctionCall.add("name", functionDetails.get("name"));
                                    geminiFunctionCall.add("args", functionDetails.get("arguments"));
                                    functionCallPart.add("functionCall", geminiFunctionCall);
                                    partsArray.add(functionCallPart);
                                }
                            }
                        }
                    } else if (originalContentObject.has("content") && originalContentObject.get("content").isJsonPrimitive()) {
                        JsonObject partObject = new JsonObject();
                        partObject.add("text", originalContentObject.get("content"));
                        partsArray.add(partObject);
                    }
                } else if ("tool".equalsIgnoreCase(originalRole)) {
                    newContentObject.addProperty("role", "function");

                    JsonObject functionResponsePart = new JsonObject();
                    JsonObject functionResponsePayload = new JsonObject();

                    String functionName = "unknown_function";
                    if (originalContentObject.has("name") && originalContentObject.get("name").isJsonPrimitive()) {
                        functionName = originalContentObject.get("name").getAsString();
                    }
                    functionResponsePayload.addProperty("name", functionName);

                    JsonObject responseContainer = new JsonObject();
                    responseContainer.addProperty("name", functionName);

                    if (originalContentObject.has("content")) {
                        JsonElement toolContentElement = originalContentObject.get("content");
                        if (toolContentElement.isJsonPrimitive() && toolContentElement.getAsJsonPrimitive().isString()) {
                            try {
                                String contentString = toolContentElement.getAsString();
                                JsonElement parsedContent = JsonParser.parseString(contentString);
                                responseContainer.add("content", parsedContent);
                            } catch (JsonSyntaxException e) {
                                System.err.println("GeminiRequestAdapter: Failed to parse tool content string for function " + functionName + ". Content: '" + toolContentElement.getAsString() + "'. Error: " + e.getMessage());
                                JsonObject errorContent = new JsonObject();
                                errorContent.addProperty("error", "Failed to parse content string.");
                                errorContent.addProperty("originalContent", toolContentElement.getAsString());
                                responseContainer.add("content", errorContent);
                            }
                        } else if (toolContentElement.isJsonObject() || toolContentElement.isJsonArray()) {
                            responseContainer.add("content", toolContentElement);
                        } else if (toolContentElement.isJsonNull()){
                            responseContainer.add("content", new JsonObject());
                        } else {
                            System.err.println("GeminiRequestAdapter: Unexpected tool content type for function " + functionName + ". Content: " + toolContentElement.toString());
                            JsonObject errorContent = new JsonObject();
                            errorContent.addProperty("error", "Unexpected content type.");
                            errorContent.addProperty("originalContentToString", toolContentElement.toString());
                            responseContainer.add("content", errorContent);
                        }
                    } else {
                        responseContainer.add("content", new JsonObject());
                    }

                    functionResponsePayload.add("response", responseContainer);
                    functionResponsePart.add("functionResponse", functionResponsePayload);
                    partsArray.add(functionResponsePart);
                }

                if (!partsArray.isEmpty()) {
                    newContentObject.add("parts", partsArray);
                }
                
                if (newContentObject.has("role")) {
                    newContents.add(newContentObject);
                }
            }
            if (!newContents.isEmpty()) {
                requestBody.add("contents", newContents);
            }
        } // Closes 'if (modelRequest.contents() != null)'

        // Add system instruction if present
        if (modelRequest.systemInstruction() != null && !modelRequest.systemInstruction().isEmpty()) {
            JsonObject systemInstructionObject = new JsonObject();
            JsonArray siPartsArray = new JsonArray();
            JsonObject siPartObject = new JsonObject();
            siPartObject.addProperty("text", modelRequest.systemInstruction());
            siPartsArray.add(siPartObject);
            systemInstructionObject.add("parts", siPartsArray);
            requestBody.add("system_instruction", systemInstructionObject);
        }
        
        // Configurar parámetros de generación (si existen)
        if (modelRequest.parameters() != null) {
            ModelParameters params = modelRequest.parameters();
            JsonObject generationConfig = new JsonObject();
            boolean hasGenerationConfig = false;

            if (params.temperature() != null) {
                generationConfig.addProperty("temperature", params.temperature());
                hasGenerationConfig = true;
            }
            if (params.topP() != null) {
                generationConfig.addProperty("topP", params.topP());
                hasGenerationConfig = true;
            }
            // TODO: Add topK to ModelParameters if needed for Gemini
            // if (params.topK() != null) { 
            //     generationConfig.addProperty("topK", params.topK());
            //     hasGenerationConfig = true;
            // }
            if (params.maxOutputTokens() != null) {
                generationConfig.addProperty("maxOutputTokens", params.maxOutputTokens());
                hasGenerationConfig = true;
            }
            // TODO: Add candidateCount to ModelParameters if needed for Gemini
            // if (params.candidateCount() != null) { 
            //     generationConfig.addProperty("candidateCount", params.candidateCount());
            //     hasGenerationConfig = true;
            // }
            if (params.stopSequences() != null && !params.stopSequences().isEmpty()) {
                generationConfig.add("stopSequences", gson.toJsonTree(params.stopSequences()));
                hasGenerationConfig = true;
            }

            // Conditional ThinkingConfig for specific models (e.g., gemini-1.5 and later)
            String modelName = modelRequest.modelName();
            if (params.thinkingConfig() != null && params.thinkingConfig().thinkingBudget() != null &&
                modelName != null && (modelName.startsWith("gemini-1.5") || modelName.startsWith("gemini-2.5"))) {
                JsonObject thinkingConfigJson = new JsonObject();
                thinkingConfigJson.addProperty("thinkingBudget", params.thinkingConfig().thinkingBudget());
                generationConfig.add("thinkingConfig", thinkingConfigJson);
                hasGenerationConfig = true;
            }

            if (hasGenerationConfig) {
                requestBody.add("generation_config", generationConfig);
            }

            // Safety Settings (at the root of the request body)
            if (params.safetySettings() != null && !params.safetySettings().isEmpty()) {
                requestBody.add("safetySettings", gson.toJsonTree(params.safetySettings()));
            }
        }
    
        // Configurar herramientas (funciones)
        if (modelRequest.functionDeclarations() != null && !modelRequest.functionDeclarations().isEmpty()) {
            JsonArray geminiToolsArray = new JsonArray();
            JsonObject geminiToolContainer = new JsonObject();
            JsonArray geminiFunctionDeclarationsArray = new JsonArray();

            for (Tool toolObj : modelRequest.functionDeclarations()) {
                JsonObject funcDeclJson = new JsonObject();
                funcDeclJson.addProperty("name", toolObj.getName());
                funcDeclJson.addProperty("description", toolObj.getDescription());

                JsonObject parametersJson = new JsonObject();
                parametersJson.addProperty("type", "OBJECT");

                JsonObject propertiesJson = new JsonObject();
                JsonArray requiredJsonArray = new JsonArray();

                if (toolObj.getParametersSchema() != null) {
                    for (Parameter paramObj : toolObj.getParametersSchema()) {
                        JsonObject paramDetailsJson = new JsonObject();
                        paramDetailsJson.addProperty("type", paramObj.getType().toUpperCase());
                        if (paramObj.getDescription() != null && !paramObj.getDescription().isEmpty()) {
                            paramDetailsJson.addProperty("description", paramObj.getDescription());
                        }
                        propertiesJson.add(paramObj.getName(), paramDetailsJson);
                        if (paramObj.isRequired()) {
                            requiredJsonArray.add(paramObj.getName());
                        }
                    }
                }

                if (propertiesJson.size() > 0) {
                    parametersJson.add("properties", propertiesJson);
                }
                if (requiredJsonArray.size() > 0) {
                    parametersJson.add("required", requiredJsonArray);
                }
                funcDeclJson.add("parameters", parametersJson);
                geminiFunctionDeclarationsArray.add(funcDeclJson);
            }
            geminiToolContainer.add("functionDeclarations", geminiFunctionDeclarationsArray);
            geminiToolsArray.add(geminiToolContainer);
            requestBody.add("tools", geminiToolsArray);
        }
    
        return requestBody;
    } // End of adaptRequest method

    @Override
    public String buildEndpointUrl(String modelName) {
        String baseUrl = API_BASE_URL + modelName + ":generateContent";
    
        if (PROJECT_ID != null && !PROJECT_ID.isEmpty()) {
            return "https://us-central1-aiplatform.googleapis.com/v1/projects/" + 
                   PROJECT_ID + "/locations/us-central1/publishers/google/models/" + 
                   modelName + ":generateContent";
        }
    
        return baseUrl;
    }
    
    @Override
    public JsonObject getRequestHeaders(String apiKey) {
        JsonObject headers = new JsonObject();
        headers.addProperty("x-goog-api-key", apiKey);
        headers.addProperty("Content-Type", "application/json");
        return headers;
    }
}
