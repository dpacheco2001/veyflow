package com.veyon.veyflow.tools;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Processor for detecting and handling tool calls from model responses.
 */
public class ToolCallProcessor {
    private static final Logger log = LoggerFactory.getLogger(ToolCallProcessor.class);
    
    /**
     * Modes for detecting tool calls from model responses.
     */
    public enum DetectionMode {
        API_RESPONSE,    // Detect from structured API response
        TEXT_EMBEDDED,   // Detect from JSON embedded in text
        TEXT_MARKDOWN    // Detect from markdown code blocks
    }
    
    private final DetectionMode detectionMode;
    
    /**
     * Create a new tool call processor.
     * 
     * @param mode The detection mode to use
     */
    public ToolCallProcessor(DetectionMode mode) {
        this.detectionMode = mode;
    }
    
    /**
     * Get the current detection mode.
     * 
     * @return The detection mode
     */
    public DetectionMode getDetectionMode() {
        return detectionMode;
    }
    
    /**
     * Detect tool calls from a model response.
     * 
     * @param responseJson The structured JSON response from the model
     * @param responseText The text content from the response
     * @return A list of detected tool calls, or empty list if none found
     */
    public List<ToolCall> detectToolCalls(JsonObject responseJson, String responseText) {
        switch (detectionMode) {
            case API_RESPONSE:
                return detectFromApiResponse(responseJson);
            case TEXT_EMBEDDED:
                return detectFromEmbeddedJson(responseText);
            case TEXT_MARKDOWN:
                return detectFromMarkdown(responseText);
            default:
                return new ArrayList<>();
        }
    }
    
    /**
     * Detect tool calls from a structured API response.
     * 
     * @param responseJson The JSON response from the model
     * @return A list of detected tool calls
     */
    private List<ToolCall> detectFromApiResponse(JsonObject responseJson) {
        List<ToolCall> toolCalls = new ArrayList<>();
        
        try {
            // Check for OpenAI format
            if (responseJson.has("choices")) {
                JsonArray choices = responseJson.getAsJsonArray("choices");
                if (choices.size() > 0) {
                    JsonObject message = choices.get(0).getAsJsonObject().getAsJsonObject("message");
                    if (message.has("tool_calls")) {
                        JsonArray toolCallsArray = message.getAsJsonArray("tool_calls");
                        for (JsonElement toolCallElem : toolCallsArray) {
                            JsonObject toolCallObj = toolCallElem.getAsJsonObject();
                            String id = null;
                            if (toolCallObj.has("id")) {
                                String potentialId = toolCallObj.get("id").getAsString();
                                if (potentialId != null && !potentialId.trim().isEmpty()) {
                                    id = potentialId;
                                }
                            }
                            if (id == null) {
                                id = UUID.randomUUID().toString();
                                log.warn("Tool call from API was missing an ID or had a blank ID. Generated new UUID: {}", id);
                            }
                            JsonObject function = toolCallObj.getAsJsonObject("function");
                            String name = function.get("name").getAsString();
                            JsonObject args = JsonParser.parseString(function.get("arguments").getAsString()).getAsJsonObject();
                            toolCalls.add(new ToolCall(id, name, args));
                        }
                    }
                }
            }
            
            // Check for Gemini format
            else if (responseJson.has("candidates")) {
                JsonArray candidates = responseJson.getAsJsonArray("candidates");
                if (candidates.size() > 0) {
                    JsonObject content = candidates.get(0).getAsJsonObject().getAsJsonObject("content");
                    if (content.has("parts")) {
                        JsonArray parts = content.getAsJsonArray("parts");
                        for (JsonElement partElem : parts) {
                            JsonObject part = partElem.getAsJsonObject();
                            if (part.has("functionCall")) {
                                JsonObject functionCall = part.getAsJsonObject("functionCall");
                                String name = functionCall.get("name").getAsString();
                                JsonObject args = functionCall.getAsJsonObject("args");
                                toolCalls.add(new ToolCall(UUID.randomUUID().toString(), name, args));
                            }
                        }
                    }
                }
            }
            
            // Check for Claude format
            else if (responseJson.has("content")) {
                JsonArray contentArray = responseJson.getAsJsonArray("content");
                for (JsonElement contentElem : contentArray) {
                    JsonObject contentObj = contentElem.getAsJsonObject();
                    if (contentObj.has("type") && contentObj.get("type").getAsString().equals("tool_use")) {
                        String id = contentObj.has("id") ? contentObj.get("id").getAsString() : UUID.randomUUID().toString();
                        String name = contentObj.getAsJsonObject("tool_use").get("name").getAsString();
                        JsonObject args = contentObj.getAsJsonObject("tool_use").getAsJsonObject("parameters");
                        toolCalls.add(new ToolCall(id, name, args));
                    }
                }
            }
        } catch (Exception e) {
            log.error("Error detecting tool calls from API response: {}", e.getMessage());
        }
        
        return toolCalls;
    }
    
    /**
     * Detect tool calls embedded in the text response.
     * 
     * @param responseText The text response from the model
     * @return A list of detected tool calls
     */
    private List<ToolCall> detectFromEmbeddedJson(String responseText) {
        List<ToolCall> toolCalls = new ArrayList<>();
        
        // Look for JSON patterns in the text
        String[] patterns = {
            "\\{\\s*\"functionCall\"\\s*:\\s*\\{.+?\\}\\s*\\}",     // {"functionCall": {...}}
            "\\{\\s*\"name\"\\s*:\\s*\".+?\".+?\\}",                // {"name": "...", ...}
            "functionCall\\(\\{.+?\\}\\)"                          // functionCall({...})
        };
        
        for (String patternStr : patterns) {
            Pattern pattern = Pattern.compile(patternStr, Pattern.DOTALL);
            Matcher matcher = pattern.matcher(responseText);
            
            while (matcher.find()) {
                String potentialJsonStr = matcher.group();
                try {
                    JsonObject json = JsonParser.parseString(potentialJsonStr).getAsJsonObject();
                    
                    // Check for functionCall structure
                    if (json.has("functionCall")) {
                        JsonObject functionCall = json.getAsJsonObject("functionCall");
                        if (functionCall.has("name") && functionCall.has("args")) {
                            String name = functionCall.get("name").getAsString();
                            JsonObject args = functionCall.getAsJsonObject("args");
                            toolCalls.add(new ToolCall(UUID.randomUUID().toString(), name, args));
                        }
                    }
                    // Check for direct name/args structure
                    else if (json.has("name") && json.has("args")) {
                        String name = json.get("name").getAsString();
                        JsonObject args = json.getAsJsonObject("args");
                        toolCalls.add(new ToolCall(UUID.randomUUID().toString(), name, args));
                    }
                } catch (JsonSyntaxException e) {
                    // Ignore malformed JSON
                }
            }
        }
        
        return toolCalls;
    }
    
    /**
     * Detect tool calls from markdown code blocks.
     * 
     * @param responseText The text response from the model
     * @return A list of detected tool calls
     */
    private List<ToolCall> detectFromMarkdown(String responseText) {
        List<ToolCall> toolCalls = new ArrayList<>();
        
        // Look for code blocks
        Pattern codeBlockPattern = Pattern.compile("```(?:json)?\\s*([\\s\\S]*?)```", Pattern.MULTILINE);
        Matcher codeBlockMatcher = codeBlockPattern.matcher(responseText);
        
        while (codeBlockMatcher.find()) {
            String codeBlock = codeBlockMatcher.group(1).trim();
            try {
                // Try to parse as JSON
                JsonElement element = JsonParser.parseString(codeBlock);
                
                // Check for different formats
                if (element.isJsonObject()) {
                    JsonObject json = element.getAsJsonObject();
                    
                    // Format: {"functionCall": {...}}
                    if (json.has("functionCall")) {
                        JsonObject functionCall = json.getAsJsonObject("functionCall");
                        if (functionCall.has("name") && functionCall.has("args")) {
                            String name = functionCall.get("name").getAsString();
                            JsonObject args = functionCall.getAsJsonObject("args");
                            toolCalls.add(new ToolCall(UUID.randomUUID().toString(), name, args));
                        }
                    }
                    // Format: {"name": "...", "args": {...}}
                    else if (json.has("name") && json.has("args")) {
                        String name = json.get("name").getAsString();
                        JsonObject args = json.getAsJsonObject("args");
                        toolCalls.add(new ToolCall(UUID.randomUUID().toString(), name, args));
                    }
                }
            } catch (JsonSyntaxException e) {
                // Ignore malformed JSON
            }
        }
        
        return toolCalls;
    }
}
