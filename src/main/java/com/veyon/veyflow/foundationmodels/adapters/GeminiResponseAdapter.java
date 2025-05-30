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
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Adapter for Google Gemini model responses.
 */
public class GeminiResponseAdapter implements ModelResponseAdapter {
    private static final Logger log = LoggerFactory.getLogger(GeminiResponseAdapter.class);

    @Override
    public ModelTurnResponse parseResponse(String rawJsonResponseString) {
        String assistantContent = null;
        List<ToolCall> toolCalls = new ArrayList<>();

        if (rawJsonResponseString == null || rawJsonResponseString.trim().isEmpty()) {
            log.warn("Gemini response string is null or empty.");
            return new ModelTurnResponse(null, toolCalls);
        }

        try {
            JsonObject responseJson = JsonParser.parseString(rawJsonResponseString.trim()).getAsJsonObject();
            
            if (responseJson.has("candidates") && responseJson.getAsJsonArray("candidates").size() > 0) {
                JsonObject candidate = responseJson.getAsJsonArray("candidates").get(0).getAsJsonObject();
                if (candidate.has("content") && candidate.getAsJsonObject("content").has("parts")) {
                    JsonArray parts = candidate.getAsJsonObject("content").getAsJsonArray("parts");
                    for (JsonElement partElement : parts) {
                        if (partElement.isJsonObject()) {
                            JsonObject partJson = partElement.getAsJsonObject();
                            // Extract text content
                            if (partJson.has("text")) {
                                if (assistantContent == null) { // Take the first text part found
                                    assistantContent = partJson.get("text").getAsString();
                                } else {
                                    // Append if multiple text parts exist, though usually there's one primary one or one per tool_call response.
                                    // For now, ToolAgent expects a single assistant content string if not making tool calls.
                                    log.debug("Multiple text parts found in Gemini response, appending. This might need specific handling.");
                                    assistantContent += "\n" + partJson.get("text").getAsString();
                                }
                            }

                            // Extract function call
                            if (partJson.has("functionCall")) {
                                JsonObject functionCallJson = partJson.getAsJsonObject("functionCall");
                                String name = functionCallJson.has("name") ? functionCallJson.get("name").getAsString() : null;
                                JsonObject args = null;
                                if (functionCallJson.has("args") && functionCallJson.get("args").isJsonObject()) {
                                    args = functionCallJson.getAsJsonObject("args");
                                } else if (functionCallJson.has("args") && functionCallJson.get("args").isJsonPrimitive() && functionCallJson.get("args").getAsJsonPrimitive().isString()) {
                                    // Sometimes args might be a stringified JSON, try to parse it
                                    try {
                                        args = JsonParser.parseString(functionCallJson.get("args").getAsString()).getAsJsonObject();
                                    } catch (JsonSyntaxException e) {
                                        log.warn("Could not parse functionCall arguments string for Gemini: {}", functionCallJson.get("args").getAsString(), e);
                                    }
                                }
                                
                                if (name != null && args != null) {
                                    String toolCallId = UUID.randomUUID().toString(); // Gemini doesn't provide a tool_call_id in the request like OpenAI
                                    toolCalls.add(new ToolCall(toolCallId, name, args));
                                } else {
                                    log.warn("Skipping Gemini functionCall due to missing name or arguments: {}", functionCallJson);
                                }
                            }
                        }
                    }
                }
            }
        } catch (JsonSyntaxException e) {
            log.error("Failed to parse Gemini JSON response: {}. Raw response: [{}...]", e.getMessage(), rawJsonResponseString.substring(0, Math.min(rawJsonResponseString.length(), 200)));
            return new ModelTurnResponse(assistantContent, toolCalls);
        } catch (Exception e) {
            log.error("Unexpected error parsing Gemini response: {}. Raw response: [{}...]", e.getMessage(), rawJsonResponseString.substring(0, Math.min(rawJsonResponseString.length(), 200)), e);
            return new ModelTurnResponse(assistantContent, toolCalls);
        }
        // If only tool calls were made, assistantContent might be null. This is fine.
        return new ModelTurnResponse(assistantContent, toolCalls);
    }
    
    @Override
    public String extractTextContent(JsonObject response) {
        try {
            if (response.has("candidates") && response.getAsJsonArray("candidates").size() > 0) {
                JsonObject candidate = response.getAsJsonArray("candidates").get(0).getAsJsonObject();
                
                if (candidate.has("content") && candidate.getAsJsonObject("content").has("parts")) {
                    JsonArray parts = candidate.getAsJsonObject("content").getAsJsonArray("parts");
                    
                    // Look for text parts
                    for (JsonElement part : parts) {
                        if (part.isJsonObject() && part.getAsJsonObject().has("text")) {
                            return part.getAsJsonObject().get("text").getAsString();
                        }
                    }
                }
            }
            return "";
        } catch (Exception e) {
            log.error("Error extracting text content from Gemini response: {}", e.getMessage());
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
        // Gemini handles systemPrompt via a top-level "system_instruction" field in the request.
        // So, we ignore the systemPrompt parameter here, assuming ModelNode handles it.

        for (ChatMessage message : chatMessages) {
            JsonObject messageObject = new JsonObject();
            JsonArray partsArray = new JsonArray();
            ChatMessage.Role currentRole = message.getRole();

            switch (currentRole) {
                case USER:
                    messageObject.addProperty("role", "user");
                    JsonObject userPart = new JsonObject();
                    userPart.addProperty("text", message.getContent());
                    partsArray.add(userPart);
                    break;
                case ASSISTANT: // Gemini uses "model" role
                    messageObject.addProperty("role", "model");
                    // Check for textual content from assistant
                    if (message.getContent() != null && !message.getContent().isEmpty()) {
                        JsonObject assistantTextPart = new JsonObject();
                        assistantTextPart.addProperty("text", message.getContent());
                        partsArray.add(assistantTextPart);
                    }

                    // Check for tool calls (Gemini's functionCall)
                    if (message.getMetadata() != null && message.getMetadata().containsKey("tool_calls")) {
                        Object toolCallsData = message.getMetadata().get("tool_calls");
                        JsonArray toolCallsArray = null;
                        if (toolCallsData instanceof JsonArray) {
                            toolCallsArray = (JsonArray) toolCallsData;
                        } else if (toolCallsData instanceof String) {
                            try {
                                toolCallsArray = JsonParser.parseString((String) toolCallsData).getAsJsonArray();
                            } catch (JsonSyntaxException | IllegalStateException e) {
                                log.warn("Could not parse tool_calls metadata string for Gemini: {}", toolCallsData, e);
                            }
                        }

                        if (toolCallsArray != null) {
                            for (JsonElement tcElement : toolCallsArray) {
                                if (tcElement.isJsonObject()) {
                                    JsonObject toolCall = tcElement.getAsJsonObject();
                                    JsonObject functionCallPart = new JsonObject();
                                    JsonObject functionCallObject = new JsonObject();
                                    // Gemini expects "name" and "args" (which is an object)
                                    functionCallObject.addProperty("name", toolCall.get("function").getAsJsonObject().get("name").getAsString());
                                    // Ensure arguments are parsed as JsonObject if they are a string
                                    JsonElement argsElement = toolCall.get("function").getAsJsonObject().get("arguments");
                                    if (argsElement.isJsonPrimitive() && argsElement.getAsJsonPrimitive().isString()) {
                                        try {
                                            functionCallObject.add("args", JsonParser.parseString(argsElement.getAsString()));
                                        } catch (JsonSyntaxException e) {
                                            log.warn("Could not parse function call arguments string for Gemini: {}", argsElement.getAsString(), e);
                                            functionCallObject.add("args", new JsonObject()); // Add empty args on error
                                        }
                                    } else {
                                        functionCallObject.add("args", argsElement);
                                    }
                                    functionCallPart.add("functionCall", functionCallObject);
                                    partsArray.add(functionCallPart);
                                }
                            }
                        }
                    }
                    // If no text and no tool_calls, add an empty text part for safety, though this state is unusual.
                    if (partsArray.isEmpty()) {
                        JsonObject emptyTextPart = new JsonObject();
                        emptyTextPart.addProperty("text", "");
                        partsArray.add(emptyTextPart);
                    }
                    break;
                case TOOL: // Gemini uses "function" role for tool responses
                    messageObject.addProperty("role", "function");
                    JsonObject toolResponsePart = new JsonObject();
                    JsonObject functionResponseObject = new JsonObject();
                    functionResponseObject.addProperty("name", message.getToolName());
                    // The content of the ChatMessage (tool response) should be a JSON string.
                    // Gemini expects the 'response' field to be a JsonObject.
                    try {
                        functionResponseObject.add("response", JsonParser.parseString(message.getContent()));
                    } catch (JsonSyntaxException e) {
                        log.error("Tool response content for Gemini is not valid JSON: {}. Tool: {}", message.getContent(), message.getToolName(), e);
                        JsonObject errorResponse = new JsonObject();
                        errorResponse.addProperty("error", "Invalid JSON response from tool");
                        errorResponse.addProperty("originalContent", message.getContent());
                        functionResponseObject.add("response", errorResponse);
                    }
                    toolResponsePart.add("functionResponse", functionResponseObject);
                    partsArray.add(toolResponsePart);
                    break;
                case SYSTEM:
                    // System messages are handled by top-level "system_instruction" in Gemini, ignore here.
                    log.debug("System message encountered in chat history for Gemini; should be handled by system_instruction. Skipping.");
                    continue; // Skip
                default:
                    log.warn("Unhandled chat message role for Gemini: {}", currentRole);
                    continue;
            }
            messageObject.add("parts", partsArray);
            contents.add(messageObject);
        }
        return contents;
    }
}
