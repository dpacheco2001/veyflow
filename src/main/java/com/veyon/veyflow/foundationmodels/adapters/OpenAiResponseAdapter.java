package com.veyon.veyflow.foundationmodels.adapters;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import com.veyon.veyflow.foundationmodels.ModelTurnResponse;
import com.veyon.veyflow.state.ChatMessage;
import com.veyon.veyflow.tools.ToolCall;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Adapter for OpenAI model responses.
 */
public class OpenAiResponseAdapter implements ModelResponseAdapter {
    private static final Logger log = LoggerFactory.getLogger(OpenAiResponseAdapter.class);
    
    private String transformNameToCanonical(String openAiName) {
        if (openAiName == null) {
            return null;
        }
        // Assuming the primary transformation was '.' to '_'.
        // If other characters were stripped by transformNameForOpenAI, 
        // reversing that perfectly might be complex and context-dependent.
        // For now, we only reverse the dot-underscore transformation.
        return openAiName.replace('_', '.');
    }

    @Override
    public ModelTurnResponse parseResponse(String rawJsonResponseString) {
        String assistantContent = null;
        List<ToolCall> toolCalls = new ArrayList<>();

        if (rawJsonResponseString == null || rawJsonResponseString.trim().isEmpty()) {
            log.warn("OpenAI response string is null or empty.");
            return new ModelTurnResponse(null, toolCalls);
        }

        try {
            JsonObject responseJson = JsonParser.parseString(rawJsonResponseString.trim()).getAsJsonObject();
            assistantContent = extractTextContent(responseJson); // Use existing method

            if (responseJson.has("choices") && responseJson.getAsJsonArray("choices").size() > 0) {
                JsonObject message = responseJson.getAsJsonArray("choices")
                    .get(0).getAsJsonObject()
                    .getAsJsonObject("message");

                if (message.has("tool_calls") && message.get("tool_calls").isJsonArray()) {
                    JsonArray toolCallsJsonArray = message.getAsJsonArray("tool_calls");
                    for (JsonElement toolCallElement : toolCallsJsonArray) {
                        JsonObject toolCallJson = toolCallElement.getAsJsonObject();
                        String id = toolCallJson.has("id") ? toolCallJson.get("id").getAsString() : null;
                        String type = toolCallJson.has("type") ? toolCallJson.get("type").getAsString() : null;
                        
                        if ("function".equals(type) && toolCallJson.has("function")) {
                            JsonObject functionJson = toolCallJson.getAsJsonObject("function");
                            String openAiName = functionJson.has("name") ? functionJson.get("name").getAsString() : null;
                            String canonicalName = transformNameToCanonical(openAiName); // Apply reverse transformation
                            String arguments = functionJson.has("arguments") ? functionJson.get("arguments").getAsString() : null;

                            if (id != null && canonicalName != null && arguments != null) {
                                // Convert arguments string to JsonObject for ToolCall constructor
                                JsonObject argsObject = JsonParser.parseString(arguments).getAsJsonObject();
                                toolCalls.add(new ToolCall(id, canonicalName, argsObject));
                            } else {
                                log.warn("Skipping tool call due to missing id, name (original: {}), or arguments: {}", openAiName, toolCallJson);
                            }
                        } else {
                            log.warn("Skipping non-function tool call or malformed function call: {}", toolCallJson);
                        }
                    }
                }
            }
        } catch (JsonSyntaxException e) {
            log.error("Failed to parse OpenAI JSON response: {}. Raw response: [{}...]", e.getMessage(), rawJsonResponseString.substring(0, Math.min(rawJsonResponseString.length(), 200)));
            // Return with any content/tool_calls parsed so far, or null/empty if parsing failed early
            return new ModelTurnResponse(assistantContent, toolCalls); 
        } catch (Exception e) {
            log.error("Unexpected error parsing OpenAI response: {}. Raw response: [{}...]", e.getMessage(), rawJsonResponseString.substring(0, Math.min(rawJsonResponseString.length(), 200)), e);
            return new ModelTurnResponse(assistantContent, toolCalls); // Graceful degradation
        }

        return new ModelTurnResponse(assistantContent, toolCalls);
    }

    @Override
    public String extractTextContent(JsonObject response) {
        try {
            if (response.has("choices") && response.getAsJsonArray("choices").size() > 0) {
                JsonObject message = response.getAsJsonArray("choices")
                    .get(0).getAsJsonObject()
                    .getAsJsonObject("message");
                
                if (message.has("content") && !message.get("content").isJsonNull()) {
                    return message.get("content").getAsString();
                }
            }
            return "";
        } catch (Exception e) {
            log.error("Error extracting text content from OpenAI response: {}", e.getMessage());
            return "";
        }
    }
    
    @Override
    public JsonObject getRawResponse(JsonObject response) {
        return response;
    }

    @Override
    public JsonArray convertChatHistoryToModelContents(List<ChatMessage> chatMessages, String systemPrompt) {
        JsonArray contents = new JsonArray();

        // OpenAI expects the system prompt as the first message if provided
        if (systemPrompt != null && !systemPrompt.isEmpty()) {
            JsonObject systemMessageObject = new JsonObject();
            systemMessageObject.addProperty("role", "system");
            systemMessageObject.addProperty("content", systemPrompt);
            contents.add(systemMessageObject);
        }

        for (ChatMessage message : chatMessages) {
            JsonObject messageObject = new JsonObject();
            ChatMessage.Role currentRole = message.getRole();

            switch (currentRole) {
                case USER:
                    messageObject.addProperty("role", "user");
                    messageObject.addProperty("content", message.getContent());
                    break;
                case ASSISTANT:
                    messageObject.addProperty("role", "assistant");
                    if (message.getContent() != null) {
                        messageObject.addProperty("content", message.getContent());
                    }

                    if (message.getMetadata() != null && message.getMetadata().containsKey("tool_calls")) {
                        Object toolCallsData = message.getMetadata().get("tool_calls");
                        if (toolCallsData instanceof JsonElement) {
                            messageObject.add("tool_calls", (JsonElement) toolCallsData);
                        } else {
                            try {
                                messageObject.add("tool_calls", JsonParser.parseString(String.valueOf(toolCallsData)));
                            } catch (JsonSyntaxException e) {
                                log.warn("Could not parse tool_calls metadata for OpenAI: {}", toolCallsData, e);
                            }
                        }
                    }
                    // OpenAI allows content to be null if tool_calls are present.
                    // If content is null and there are no tool_calls, an empty string might be required by some older models, but typically not for newer ones.
                    if (messageObject.get("content") == null && messageObject.get("tool_calls") == null) {
                         // It's often better to omit content entirely if null, rather than sending an empty string, unless the API specifically requires it.
                         // For now, let's assume null content is fine if tool_calls are also null (though this state might indicate an issue).
                         // If there are no tool_calls, content should ideally not be null. Let's default to empty string for safety if both are null.
                        messageObject.addProperty("content", ""); 
                    }
                    break;
                case TOOL:
                    messageObject.addProperty("role", "tool");
                    log.info("OAIRespAdapt: Processing TOOL message. Name: '{}', Content: '{}...'. Metadata keys: {}",
                             message.getToolName(),
                             (message.getContent() != null && message.getContent().length() > 20 ? message.getContent().substring(0, 20) : message.getContent()),
                             message.getMetadata() != null ? message.getMetadata().keySet() : "null_metadata_map");

                    Object rawToolCallId = message.getMetadata() != null ? message.getMetadata().get("tool_call_id") : null;
                    log.info("OAIRespAdapt: Raw tool_call_id from metadata: '{}' (type: {})", 
                             rawToolCallId, (rawToolCallId != null ? rawToolCallId.getClass().getName() : "null"));

                    String toolCallId = rawToolCallId != null ? String.valueOf(rawToolCallId) : null;
                    
                    if (toolCallId == null || toolCallId.equals("null")) { // Check for literal "null" string from String.valueOf()
                        log.warn("OAIRespAdapt: Tool message for tool '{}' is missing a tool_call_id or it's the string 'null'. Using tool name as fallback. Raw value was: '{}'", 
                                 message.getToolName(), rawToolCallId);
                        toolCallId = message.getToolName(); // Fallback, but not ideal
                    }
                    messageObject.addProperty("tool_call_id", toolCallId);
                    // OpenAI's 'name' for tool role is the function name, which we store in getToolName()
                    messageObject.addProperty("name", message.getToolName()); 
                    messageObject.addProperty("content", message.getContent()); // Content is the JSON string result from the tool
                    break;
                case SYSTEM: // System messages are handled by the initial systemPrompt parameter for OpenAI
                    log.warn("System message encountered in chat history for OpenAI; should be handled by systemPrompt parameter. Skipping.");
                    continue; // Skip, as it's handled at the beginning
                default:
                    log.warn("Unhandled chat message role for OpenAI: {}", currentRole);
                    continue;
            }
            contents.add(messageObject);
        }
        return contents;
    }
}
