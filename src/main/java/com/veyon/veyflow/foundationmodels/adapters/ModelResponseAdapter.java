package com.veyon.veyflow.foundationmodels.adapters;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.List;
import com.veyon.veyflow.state.ChatMessage;

/**
 * Interface for adapting responses from different foundation models.
 * Provides a common interface for extracting information from model responses.
 */
public interface ModelResponseAdapter {
    
    /**
     * Extract the text content from a model response.
     * 
     * @param response The JSON response from the model
     * @return The extracted text content
     */
    String extractTextContent(JsonObject response);
    
    /**
     * Get the raw JSON response for further processing.
     * 
     * @param response The JSON response from the model
     * @return The raw JSON response
     */
    JsonObject getRawResponse(JsonObject response);

    /**
     * Converts a list of chat messages and a system prompt into a JsonArray 
     * formatted as the 'contents' or 'messages' field for the specific model's request payload.
     * 
     * @param chatMessages The list of chat messages to convert.
     * @param systemPrompt The system prompt string. The adapter will decide if/how to include this.
     * @return A JsonArray representing the model-specific formatted chat history.
     */
    JsonArray convertChatHistoryToModelContents(List<ChatMessage> chatMessages, String systemPrompt);
}
