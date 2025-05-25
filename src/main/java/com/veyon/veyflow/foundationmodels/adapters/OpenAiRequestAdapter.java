package com.veyon.veyflow.foundationmodels.adapters;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.veyon.veyflow.foundationmodels.FoundationModelService.ModelRequest;
import com.veyon.veyflow.foundationmodels.ModelParameters;
import com.veyon.veyflow.state.ChatMessage;
import com.veyon.veyflow.tools.Parameter;
import com.veyon.veyflow.tools.Tool;
import com.veyon.veyflow.tools.ToolCall;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * Adapter to convert ModelRequest to OpenAI-specific JSON request body.
 */
public class OpenAiRequestAdapter implements ModelRequestAdapter {

    private static final String API_BASE_URL = "https://api.openai.com/v1/chat/completions";
    private static final Logger log = LoggerFactory.getLogger(OpenAiRequestAdapter.class);
    private final Gson gson = new GsonBuilder().disableHtmlEscaping().create();

    private String transformNameForOpenAI(String canonicalName) {
        if (canonicalName == null) {
            return null;
        }
        return canonicalName.replace('.', '_').replaceAll("[^a-zA-Z0-9_]", ""); // Replace . with _ and remove other invalid chars as a fallback
    }

    @Override
    public JsonObject adaptRequest(ModelRequest modelRequest) {
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("model", modelRequest.modelName());

        JsonArray messages = new JsonArray();

        // Handle system instruction first if present
        if (modelRequest.systemInstruction() != null && !modelRequest.systemInstruction().isEmpty()) {
            JsonObject systemMessage = new JsonObject();
            systemMessage.addProperty("role", "system");
            systemMessage.addProperty("content", modelRequest.systemInstruction());
            messages.add(systemMessage);
        }

        // Process contents (user, assistant, tool messages)
        if (modelRequest.contents() != null) {
            for (ChatMessage chatMessage : modelRequest.contents()) { 
                // Convert ChatMessage to JsonObject (originalMessageJson)
                JsonObject originalMessageJson = new JsonObject();
                String roleName = chatMessage.getRole().name().toLowerCase();
                originalMessageJson.addProperty("role", roleName);

                if (chatMessage.getContent() != null) {
                    originalMessageJson.addProperty("content", chatMessage.getContent());
                }

                if ("tool".equals(roleName)) {
                    // For OpenAI, a 'tool' role message needs 'tool_call_id' and 'content'.
                    // 'name' is not directly at the top level of a 'tool' role message in OpenAI,
                    // it's part of the function call in the assistant's prior message.
                    // However, ChatMessage.toolName might be relevant for constructing the response if needed.
                    // For now, we primarily need tool_call_id from ChatMessage.getId()
                    if (chatMessage.getId() != null) { 
                         originalMessageJson.addProperty("tool_call_id", chatMessage.getId());
                    } else if (chatMessage.getToolName() != null) {
                        // Fallback or alternative: if ID is not set but toolName is, it might be used as tool_call_id
                        // This depends on how tool_call_ids are generated and tracked.
                        // For now, let's log a warning if ID is missing for a tool message.
                        log.warn("OpenAiRequestAdapter: Tool message is missing an ID (tool_call_id). Tool name: " + chatMessage.getToolName());
                        // originalMessageJson.addProperty("tool_call_id", chatMessage.getToolName()); 
                    }
                }

                if ("assistant".equals(roleName) && chatMessage.getToolCalls() != null && !chatMessage.getToolCalls().isEmpty()) {
                    JsonArray toolCallsJsonArray = new JsonArray();
                    for (ToolCall tc : chatMessage.getToolCalls()) {
                        JsonObject toolCallJson = new JsonObject();
                        toolCallJson.addProperty("id", tc.getId());
                        toolCallJson.addProperty("type", "function"); 
                        JsonObject functionJson = new JsonObject();
                        functionJson.addProperty("name", transformNameForOpenAI(tc.getName()));
                        // OpenAI expects arguments as a stringified JSON
                        functionJson.addProperty("arguments", tc.getParameters() != null ? tc.getParameters().toString() : "{}");
                        toolCallJson.add("function", functionJson);
                        toolCallsJsonArray.add(toolCallJson);
                    }
                    originalMessageJson.add("tool_calls", toolCallsJsonArray);
                    // For assistant messages with tool_calls, OpenAI doesn't want a 'content' field if it's null.
                    // If there's actual assistant text content along with tool_calls, it should be kept.
                    if (chatMessage.getContent() == null || chatMessage.getContent().isEmpty()) {
                        originalMessageJson.remove("content"); 
                    }
                }
                // End of ChatMessage to JsonObject conversion

                // Add the fully constructed message to the array
                messages.add(originalMessageJson);
            }
        }
        requestBody.add("messages", messages);

        // Add tools (function declarations)
        if (modelRequest.functionDeclarations() != null && !modelRequest.functionDeclarations().isEmpty()) {
            JsonArray toolsArray = new JsonArray();
            for (Tool tool : modelRequest.functionDeclarations()) {
                JsonObject toolJson = new JsonObject();
                toolJson.addProperty("type", "function");

                JsonObject functionJson = new JsonObject();
                functionJson.addProperty("name", transformNameForOpenAI(tool.getName()));
                if (tool.getDescription() != null && !tool.getDescription().isEmpty()) {
                    functionJson.addProperty("description", tool.getDescription());
                }

                if (tool.getParametersSchema() != null && !tool.getParametersSchema().isEmpty()) {
                    JsonObject parametersJson = new JsonObject();
                    parametersJson.addProperty("type", "object");
                    JsonObject propertiesJson = new JsonObject();
                    JsonArray requiredArray = new JsonArray();

                    for (Parameter param : tool.getParametersSchema()) {
                        JsonObject paramDetails = new JsonObject();
                        // OpenAI expects parameter types in lowercase (e.g., "string", "number", "boolean")
                        paramDetails.addProperty("type", param.getType().toLowerCase()); 
                        if (param.getDescription() != null && !param.getDescription().isEmpty()) {
                            paramDetails.addProperty("description", param.getDescription());
                        }
                        // TODO: Handle enum for parameters if param.getEnumValues() is available and not null/empty
                        propertiesJson.add(param.getName(), paramDetails);
                        if (param.isRequired()) {
                            requiredArray.add(param.getName());
                        }
                    }

                    if (propertiesJson.size() > 0) {
                        parametersJson.add("properties", propertiesJson);
                    }
                    if (requiredArray.size() > 0) {
                        parametersJson.add("required", requiredArray);
                    }
                    functionJson.add("parameters", parametersJson);
                }

                toolJson.add("function", functionJson);
                toolsArray.add(toolJson);
            }
            if (toolsArray.size() > 0) {
                requestBody.add("tools", toolsArray);
                // TODO: Consider adding tool_choice if needed, e.g., "auto" or {"type": "function", "function": {"name": "my_function"}}
                // requestBody.addProperty("tool_choice", "auto"); 
            }
        }

        // Add other parameters from ModelParameters
        ModelParameters params = modelRequest.parameters();
        if (params != null) {
            if (params.temperature() != null) {
                requestBody.addProperty("temperature", params.temperature());
            }
            if (params.maxOutputTokens() != null) {
                requestBody.addProperty("max_tokens", params.maxOutputTokens());
            }
            if (params.topP() != null) {
                requestBody.addProperty("top_p", params.topP());
            }
            // TODO: Add other OpenAI specific parameters like presence_penalty, frequency_penalty, logit_bias, user, seed if available in ModelParameters
            if (params.stopSequences() != null && !params.stopSequences().isEmpty()) {
                JsonElement stopElement = gson.toJsonTree(params.stopSequences());
                if (stopElement.isJsonArray() && stopElement.getAsJsonArray().size() == 1) {
                    requestBody.add("stop", stopElement.getAsJsonArray().get(0)); 
                } else if (stopElement.isJsonArray() && stopElement.getAsJsonArray().size() > 1) {
                     requestBody.add("stop", stopElement); 
                } else if (stopElement.isJsonPrimitive()) {
                    requestBody.add("stop", stopElement); 
                }
            }
        }

        // Add additionalConfig if present (e.g., stream, logprobs, etc.)
        if (modelRequest.additionalConfig() != null && !modelRequest.additionalConfig().isEmpty()) {
            for (Map.Entry<String, Object> entry : modelRequest.additionalConfig().entrySet()) {
                requestBody.add(entry.getKey(), gson.toJsonTree(entry.getValue()));
            }
        }

        // Ensure that "max_tokens" is the parameter sent for max output tokens, 
        // and "maxOutputTokens" is not present, as OpenAI expects "max_tokens".
        // Note: "max_tokens" is already added from params.maxOutputTokens() at line 165.
        log.info("[OpenAiRequestAdapter] Before checking/removing maxOutputTokens. Current requestBody: {}", requestBody.toString()); 
        if (requestBody.has("maxOutputTokens")) {
            log.info("[OpenAiRequestAdapter] 'maxOutputTokens' FOUND. Removing it."); 
            requestBody.remove("maxOutputTokens");
            log.info("[OpenAiRequestAdapter] 'maxOutputTokens' REMOVED. Current requestBody: {}", requestBody.toString()); 
        } else {
            log.info("[OpenAiRequestAdapter] 'maxOutputTokens' NOT FOUND. No removal needed."); 
        }

        return requestBody;
    }

    @Override
    public String buildEndpointUrl(String modelName) {
        // modelName is already part of the requestBody for OpenAI, so the endpoint is static.
        return API_BASE_URL;
    }

    @Override
    public JsonObject getRequestHeaders(String apiKey) {
        JsonObject headers = new JsonObject();
        headers.addProperty("Authorization", "Bearer " + apiKey);
        headers.addProperty("Content-Type", "application/json");
        // OpenAI-Organization header can be added if needed
        // String organizationId = System.getenv("OPENAI_ORGANIZATION_ID");
        // if (organizationId != null && !organizationId.isEmpty()) {
        //     headers.addProperty("OpenAI-Organization", organizationId);
        // }
        return headers;
    }
}
