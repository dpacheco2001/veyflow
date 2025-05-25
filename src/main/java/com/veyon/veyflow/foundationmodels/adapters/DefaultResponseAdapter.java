package com.veyon.veyflow.foundationmodels.adapters;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import com.veyon.veyflow.foundationmodels.ModelTurnResponse;
import com.veyon.veyflow.state.ChatMessage;
import com.veyon.veyflow.tools.ToolCall;
import java.util.Collections;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Default adapter for model responses, used when no specific adapter is available.
 */
public class DefaultResponseAdapter implements ModelResponseAdapter {
    private static final Logger log = LoggerFactory.getLogger(DefaultResponseAdapter.class);

    @Override
    public ModelTurnResponse parseResponse(String rawJsonResponseString) {
        String assistantContent = null;
        List<ToolCall> toolCalls = Collections.emptyList(); // Default to no tool calls

        if (rawJsonResponseString == null || rawJsonResponseString.trim().isEmpty()) {
            log.warn("DefaultResponseAdapter: Response string is null or empty.");
            return new ModelTurnResponse(null, toolCalls);
        }

        try {
            // Try to parse as JSON and look for common text fields
            JsonElement jsonElement = JsonParser.parseString(rawJsonResponseString.trim());
            if (jsonElement.isJsonObject()) {
                JsonObject responseJson = jsonElement.getAsJsonObject();
                assistantContent = extractTextContent(responseJson); // Use existing or a similar logic
                // Default adapter does not attempt to parse tool calls from unknown structures.
            } else if (jsonElement.isJsonPrimitive() && jsonElement.getAsJsonPrimitive().isString()){
                // If it's just a plain string response
                assistantContent = jsonElement.getAsString();
            } else {
                // Not a JSON object or simple string, return raw (or part of it)
                log.warn("DefaultResponseAdapter: Response is not a JSON object or simple string. Returning raw content (truncated if long).");
                assistantContent = rawJsonResponseString.length() > 1000 ? rawJsonResponseString.substring(0, 1000) + "..." : rawJsonResponseString;
            }
        } catch (JsonSyntaxException e) {
            log.warn("DefaultResponseAdapter: Failed to parse response as JSON. Returning raw content (truncated if long). Error: {}", e.getMessage());
            assistantContent = rawJsonResponseString.length() > 1000 ? rawJsonResponseString.substring(0, 1000) + "..." : rawJsonResponseString;
        } catch (Exception e) {
            log.error("DefaultResponseAdapter: Unexpected error parsing response. Raw: [{}...]. Error: {}", rawJsonResponseString.substring(0, Math.min(rawJsonResponseString.length(), 200)), e.getMessage(), e);
            assistantContent = "Error processing model response."; // Fallback error message
        }
        
        return new ModelTurnResponse(assistantContent, toolCalls);
    }

    @Override
    public String extractTextContent(JsonObject response) {
        // Try common fields for text content
        if (response.has("text") && response.get("text").isJsonPrimitive()) {
            return response.get("text").getAsString();
        }
        if (response.has("content") && response.get("content").isJsonPrimitive()) {
            return response.get("content").getAsString();
        }
        if (response.has("message") && response.get("message").isJsonPrimitive()) {
            return response.get("message").getAsString();
        }
        // OpenAI specific, but could be a common pattern
        if (response.has("choices") && response.getAsJsonArray("choices").size() > 0) {
            JsonObject firstChoice = response.getAsJsonArray("choices").get(0).getAsJsonObject();
            if (firstChoice.has("message")) {
                JsonObject messageObj = firstChoice.getAsJsonObject("message");
                if (messageObj.has("content") && !messageObj.get("content").isJsonNull()) {
                    return messageObj.get("content").getAsString();
                }
            }
        }
        // Gemini specific, but could be a common pattern
        if (response.has("candidates") && response.getAsJsonArray("candidates").size() > 0) {
            JsonObject firstCandidate = response.getAsJsonArray("candidates").get(0).getAsJsonObject();
            if (firstCandidate.has("content") && firstCandidate.getAsJsonObject("content").has("parts")) {
                JsonArray parts = firstCandidate.getAsJsonObject("content").getAsJsonArray("parts");
                for (JsonElement part : parts) {
                    if (part.isJsonObject() && part.getAsJsonObject().has("text")) {
                        return part.getAsJsonObject().get("text").getAsString(); // Return first text part
                    }
                }
            }
        }
        log.warn("DefaultResponseAdapter: Could not extract text content from response using common fields.");
        return null; // Or return response.toString() as a fallback if no specific field found
    }

    @Override
    public JsonObject getRawResponse(JsonObject response) {
        return response;
    }

    @Override
    public JsonArray convertChatHistoryToModelContents(List<ChatMessage> chatMessages, String systemPrompt) {
        JsonArray messages = new JsonArray();

        // Add system prompt as the first message if provided
        if (systemPrompt != null && !systemPrompt.isEmpty()) {
            JsonObject systemMessage = new JsonObject();
            systemMessage.addProperty("role", "system");
            systemMessage.addProperty("content", systemPrompt);
            messages.add(systemMessage);
        }

        for (ChatMessage chatMessage : chatMessages) {
            JsonObject jsonMessage = new JsonObject();
            String role = chatMessage.getRole().toString().toLowerCase();
            jsonMessage.addProperty("role", role);

            String content = chatMessage.getContent();
            if (content != null && !content.isEmpty()) {
                jsonMessage.addProperty("content", content);
            }

            switch (chatMessage.getRole()) {
                case ASSISTANT:
                    if (chatMessage.getMetadata() != null && chatMessage.getMetadata().containsKey("tool_calls")) {
                        Object toolCallsData = chatMessage.getMetadata().get("tool_calls");
                        JsonArray toolCallsArray = null;
                        if (toolCallsData instanceof JsonArray) {
                            toolCallsArray = (JsonArray) toolCallsData;
                        } else if (toolCallsData instanceof String) {
                            try {
                                JsonElement parsedElement = JsonParser.parseString((String) toolCallsData);
                                if (parsedElement.isJsonArray()) {
                                    toolCallsArray = parsedElement.getAsJsonArray();
                                }
                            } catch (JsonSyntaxException | IllegalStateException e) {
                                log.warn("Could not parse tool_calls metadata string for DefaultAdapter: {}", toolCallsData, e);
                            }
                        }
                        if (toolCallsArray != null) {
                            jsonMessage.add("tool_calls", toolCallsArray);
                            // OpenAI allows content to be null if tool_calls are present.
                            // For a default adapter, if there was no other content, we might remove it or ensure it's explicitly null.
                            // If content was already added from chatMessage.getContent(), it stays.
                            // If content is null and tool_calls are present, some models might expect content: null.
                            // For simplicity, we'll leave content as is (either set from message or not set if null/empty).
                        } else if (content == null || content.isEmpty()) {
                            // If there are no tool calls and no textual content, some models might require a content field (e.g. empty string)
                            // However, OpenAI allows assistant message with only tool_calls and no content.
                            // For default, if both are missing, it might be an issue. Let's ensure content is at least empty string if no tool_calls.
                            // This case (no content, no tool_calls for assistant) is unusual.
                            // If tool_calls were expected but not parsable, content might be the only thing.
                            // If content was null/empty AND tool_calls were null/empty/unparsable, add empty content.
                            if (!jsonMessage.has("content")) { 
                                jsonMessage.addProperty("content", "");
                            }
                        }
                    }
                    break;
                case TOOL:
                    if (chatMessage.getToolName() != null && !chatMessage.getToolName().isEmpty()) {
                        jsonMessage.addProperty("name", chatMessage.getToolName());
                    }
                    if (chatMessage.getMetadata() != null && chatMessage.getMetadata().containsKey("tool_call_id")) {
                        Object toolCallIdObj = chatMessage.getMetadata().get("tool_call_id");
                        if (toolCallIdObj != null) {
                            jsonMessage.addProperty("tool_call_id", toolCallIdObj.toString());
                        }
                    }
                    // The tool's actual response content is already added by the generic content handling above.
                    // Ensure content is present for tool messages, as it's the result.
                    if (!jsonMessage.has("content")) {
                         log.warn("Tool message for '{}' is missing content (result).", chatMessage.getToolName());
                         jsonMessage.addProperty("content", "Error: Tool result missing."); // Fallback content
                    }
                    break;
                case USER:
                case SYSTEM: // System messages from history (primary one handled above)
                    // Content is already handled. No special fields for default user/system.
                    if (!jsonMessage.has("content")) {
                         // This should not happen for user/system messages if they are valid.
                         log.warn("{} message is missing content.", role);
                         jsonMessage.addProperty("content", ""); // Fallback content
                    }
                    break;
                default:
                    log.warn("Unhandled role in DefaultResponseAdapter: {}", chatMessage.getRole());
                    break;
            }
            messages.add(jsonMessage);
        }
        return messages;
    }
}
