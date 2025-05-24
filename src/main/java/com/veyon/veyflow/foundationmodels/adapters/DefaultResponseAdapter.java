package com.veyon.veyflow.foundationmodels.adapters;

import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import com.veyon.veyflow.state.ChatMessage;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Default adapter for model responses when a specific adapter is not available.
 * Tries to extract content using common patterns in foundation model APIs.
 */
public class DefaultResponseAdapter implements ModelResponseAdapter {
    private static final Logger log = LoggerFactory.getLogger(DefaultResponseAdapter.class);
    
    @Override
    public String extractTextContent(JsonObject response) {
        try {
            // Try common patterns in model responses
            
            // Pattern 1: OpenAI style
            if (response.has("choices") && response.getAsJsonArray("choices").size() > 0) {
                JsonObject choice = response.getAsJsonArray("choices").get(0).getAsJsonObject();
                if (choice.has("message") && choice.getAsJsonObject("message").has("content")) {
                    return choice.getAsJsonObject("message").get("content").getAsString();
                }
                if (choice.has("text")) {
                    return choice.get("text").getAsString();
                }
            }
            
            // Pattern 2: Gemini style
            if (response.has("candidates") && response.getAsJsonArray("candidates").size() > 0) {
                JsonObject candidate = response.getAsJsonArray("candidates").get(0).getAsJsonObject();
                if (candidate.has("content") && candidate.getAsJsonObject("content").has("parts")) {
                    for (int i = 0; i < candidate.getAsJsonObject("content").getAsJsonArray("parts").size(); i++) {
                        JsonObject part = candidate.getAsJsonObject("content").getAsJsonArray("parts").get(i).getAsJsonObject();
                        if (part.has("text")) {
                            return part.get("text").getAsString();
                        }
                    }
                }
            }
            
            // Pattern 3: Claude style
            if (response.has("content")) {
                for (int i = 0; i < response.getAsJsonArray("content").size(); i++) {
                    JsonObject content = response.getAsJsonArray("content").get(i).getAsJsonObject();
                    if (content.has("type") && content.get("type").getAsString().equals("text") && content.has("text")) {
                        return content.get("text").getAsString();
                    }
                }
            }
            
            // Pattern 4: Direct text property
            if (response.has("text")) {
                return response.get("text").getAsString();
            }
            
            // No recognizable pattern found
            log.warn("Could not extract text content using default patterns: {}", response);
            return "";
        } catch (Exception e) {
            log.error("Error extracting text content using default patterns: {}", e.getMessage());
            return "";
        }
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
