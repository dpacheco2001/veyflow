package com.veyon.veyflow.foundationmodels.adapters;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.veyon.veyflow.foundationmodels.FoundationModelService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OpenAiRequestAdapter implements ModelRequestAdapter {

    private static final String API_BASE_URL = "https://api.openai.com/v1/chat/completions";
    private static final Logger log = LoggerFactory.getLogger(OpenAiRequestAdapter.class);

    @Override
    public JsonObject adaptRequest(FoundationModelService.ModelRequest modelRequest) {
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("model", modelRequest.modelName());

        JsonArray messagesForApi = new JsonArray();

        if (modelRequest.systemInstruction() != null && !modelRequest.systemInstruction().isEmpty()) {
            JsonObject systemMessage = new JsonObject();
            systemMessage.addProperty("role", "system");
            systemMessage.addProperty("content", modelRequest.systemInstruction());
            messagesForApi.add(systemMessage);
        }

        if (modelRequest.contents() != null) {
            for (JsonElement messageElement : modelRequest.contents()) {
                if (!messageElement.isJsonObject()) {
                    log.warn("Skipping non-JsonObject element in modelRequest.contents(): {}", messageElement);
                    continue;
                }
                JsonObject originalMessageJson = messageElement.getAsJsonObject();
                JsonObject messageForApi = new JsonObject();

                String role = originalMessageJson.has("role") ? originalMessageJson.get("role").getAsString() : null;
                if (role == null) {
                    log.warn("Skipping message with missing role in modelRequest.contents(): {}", originalMessageJson);
                    continue;
                }
                messageForApi.addProperty("role", role);

                if (originalMessageJson.has("content") && !originalMessageJson.get("content").isJsonNull()) {
                    if ("assistant".equals(role) && originalMessageJson.has("tool_calls")) {
                        messageForApi.add("content", null);
                    } else {
                        if ("tool".equals(role) && originalMessageJson.get("content").isJsonPrimitive() && originalMessageJson.get("content").getAsJsonPrimitive().isString()) {
                            messageForApi.addProperty("content", originalMessageJson.get("content").getAsString());
                        } else if (!"tool".equals(role)){
                            messageForApi.add("content", originalMessageJson.get("content"));
                        } else {
                            log.warn("Tool message content is not a string or is missing: {}. Setting to empty string.", originalMessageJson.get("content"));
                            messageForApi.addProperty("content", "");
                        }
                    }
                } else if (("assistant".equals(role) && originalMessageJson.has("tool_calls")) || "user".equals(role) || "system".equals(role)) {
                    messageForApi.add("content", null);
                } else if ("tool".equals(role)) {
                    log.warn("Tool message has null or missing content field: {}. Setting to empty string.", originalMessageJson);
                    messageForApi.addProperty("content", "");
                }

                if ("assistant".equals(role)) {
                    if (originalMessageJson.has("tool_calls")) {
                        messageForApi.add("tool_calls", originalMessageJson.get("tool_calls"));
                    }
                } else if ("tool".equals(role)) {
                    if (originalMessageJson.has("name") && originalMessageJson.get("name").isJsonPrimitive() && originalMessageJson.get("name").getAsJsonPrimitive().isString()) {
                        String name = originalMessageJson.get("name").getAsString();
                        if (!name.trim().isEmpty()) {
                            messageForApi.addProperty("name", name);
                        } else {
                            log.error("CRITICAL: 'name' for tool message in JsonObject is empty. Message: {}. API request will likely fail.", originalMessageJson);
                        }
                    } else {
                        log.error("CRITICAL: 'name' for tool message is missing or not a string in JsonObject. Message: {}. API request will likely fail.", originalMessageJson);
                    }

                    if (originalMessageJson.has("tool_call_id") && originalMessageJson.get("tool_call_id").isJsonPrimitive() && originalMessageJson.get("tool_call_id").getAsJsonPrimitive().isString()) {
                        String toolCallId = originalMessageJson.get("tool_call_id").getAsString();
                        if (!toolCallId.trim().isEmpty()) {
                            messageForApi.addProperty("tool_call_id", toolCallId);
                        } else {
                            log.error("CRITICAL: 'tool_call_id' for tool message in JsonObject is empty. Message: {}. API request will likely fail.", originalMessageJson);
                        }
                    } else {
                        log.error("CRITICAL: 'tool_call_id' for tool message is missing or not a string in JsonObject. Message: {}. API request will likely fail.", originalMessageJson);
                    }
                }
                messagesForApi.add(messageForApi);
            }
        }
        requestBody.add("messages", messagesForApi);

        if (modelRequest.functionDeclarations() != null && !modelRequest.functionDeclarations().isEmpty()) {
            JsonArray toolsArray = new JsonArray();
            for (JsonElement funcDeclElement : modelRequest.functionDeclarations()) {
                if (funcDeclElement.isJsonObject()) {
                    JsonObject funcDeclJson = funcDeclElement.getAsJsonObject();
                    JsonObject toolWrapper = new JsonObject();
                    toolWrapper.addProperty("type", "function");
                    toolWrapper.add("function", funcDeclJson);
                    toolsArray.add(toolWrapper);
                } else {
                    log.warn("Skipping non-JsonObject function declaration: {}", funcDeclElement);
                }
            }
            if (!toolsArray.isEmpty()) {
                requestBody.add("tools", toolsArray);
            }
        }

        if (modelRequest.parameters() != null) {
            Object temp = null;
            Object maxTokens = null;
            try {
                java.lang.reflect.Method tempMethod = modelRequest.parameters().getClass().getMethod("temperature");
                temp = tempMethod.invoke(modelRequest.parameters());
            } catch (NoSuchMethodException nsme) {
                log.warn("ModelParameters does not have a 'temperature()' method.");
            } catch (Exception e) {
                log.warn("Could not invoke 'temperature()' on ModelParameters", e);
            }
            try {
                java.lang.reflect.Method maxTokensMethod = modelRequest.parameters().getClass().getMethod("maxOutputTokens");
                maxTokens = maxTokensMethod.invoke(modelRequest.parameters());
            } catch (NoSuchMethodException nsme) {
                log.warn("ModelParameters does not have a 'maxOutputTokens()' method.");
            } catch (Exception e) {
                log.warn("Could not invoke 'maxOutputTokens()' on ModelParameters", e);
            }

            if (temp instanceof Number) {
                requestBody.addProperty("temperature", (Number) temp);
            } else if (temp != null) {
                log.warn("Temperature parameter is not a Number: {}. Type: {}", temp, temp.getClass().getName());
            }

            if (maxTokens instanceof Number) {
                requestBody.addProperty("max_tokens", (Number) maxTokens);
            } else if (maxTokens != null) {
                log.warn("MaxOutputTokens parameter is not a Number: {}. Type: {}", maxTokens, maxTokens.getClass().getName());
            }
        }

        if (modelRequest.additionalConfig() != null && modelRequest.additionalConfig().containsKey("tool_choice")) {
            Object toolChoiceObj = modelRequest.additionalConfig().get("tool_choice");
            if (toolChoiceObj instanceof String) {
                requestBody.addProperty("tool_choice", (String) toolChoiceObj);
            } else {
                log.warn("tool_choice in additionalConfig is not a String: {}", toolChoiceObj);
            }
        }

        log.debug("Adapted OpenAI Request (using FoundationModelService.ModelRequest): {}", requestBody.toString());
        return requestBody;
    }

    @Override
    public String buildEndpointUrl(String modelName) {
        // OpenAI uses a single endpoint for chat completions regardless of the specific model variant
        return API_BASE_URL;
    }

    @Override
    public JsonObject getRequestHeaders(String apiKey) {
        JsonObject headers = new JsonObject();
        headers.addProperty("Authorization", "Bearer " + apiKey);
        headers.addProperty("Content-Type", "application/json");
        // OpenAI specific headers if needed, e.g., Organization ID
        // String organizationId = System.getenv("OPENAI_ORGANIZATION_ID");
        // if (organizationId != null && !organizationId.isEmpty()) {
        //     headers.addProperty("OpenAI-Organization", organizationId);
        // }
        return headers;
    }
}
