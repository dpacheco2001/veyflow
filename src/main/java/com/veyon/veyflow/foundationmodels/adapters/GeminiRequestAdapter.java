package com.veyon.veyflow.foundationmodels.adapters;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.veyon.veyflow.foundationmodels.FoundationModelService.ModelRequest;
import com.veyon.veyflow.foundationmodels.ModelParameters;

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
            for (JsonElement contentElement : modelRequest.contents()) {
                if (contentElement.isJsonObject()) {
                    JsonObject originalContentObject = contentElement.getAsJsonObject();
                    JsonObject newContentObject = new JsonObject();
                    if (originalContentObject.has("role")) {
                        newContentObject.add("role", originalContentObject.get("role"));
                    }
                    if (originalContentObject.has("content") && originalContentObject.get("content").isJsonPrimitive()) {
                        JsonArray partsArray = new JsonArray();
                        JsonObject partObject = new JsonObject();
                        partObject.add("text", originalContentObject.get("content"));
                        partsArray.add(partObject);
                        newContentObject.add("parts", partsArray);
                    } else if (originalContentObject.has("parts")) {
                        newContentObject.add("parts", originalContentObject.get("parts"));
                    }
                    newContents.add(newContentObject);
                }
            }
            requestBody.add("contents", newContents);
        }
        
        // Configurar system instruction
        if (modelRequest.systemInstruction() != null && !modelRequest.systemInstruction().isEmpty()) {
            JsonObject systemInstructionObject = new JsonObject();
            JsonArray partsArray = new JsonArray();
            JsonObject partObject = new JsonObject();
            partObject.addProperty("text", modelRequest.systemInstruction());
            partsArray.add(partObject);
            systemInstructionObject.add("parts", partsArray);
            requestBody.add("system_instruction", systemInstructionObject);
        }
        
        // Configurar parámetros del modelo (generation_config and safety_settings)
        if (modelRequest.parameters() != null) {
            ModelParameters params = modelRequest.parameters();
            JsonObject generationConfig = new JsonObject();
            boolean hasGenerationConfig = false;

            if (params.temperature() != null) {
                generationConfig.addProperty("temperature", params.temperature());
                hasGenerationConfig = true;
            }
            if (params.maxOutputTokens() != null) {
                generationConfig.addProperty("maxOutputTokens", params.maxOutputTokens());
                hasGenerationConfig = true;
            }
            if (params.topP() != null) {
                generationConfig.addProperty("topP", params.topP());
                hasGenerationConfig = true;
            }
            if (params.stopSequences() != null && !params.stopSequences().isEmpty()) {
                generationConfig.add("stopSequences", gson.toJsonTree(params.stopSequences()));
                hasGenerationConfig = true;
            }

            // Conditional ThinkingConfig for specific models (e.g., gemini-1.5 and later)
            String modelName = modelRequest.modelName();
            if (params.thinkingConfig() != null && params.thinkingConfig().thinkingBudget() != null &&
                modelName != null && (modelName.startsWith("gemini-1.5") || modelName.startsWith("gemini-2.5"))) { // Adjusted model check
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
        if (modelRequest.functionDeclarations() != null && modelRequest.functionDeclarations().size() > 0) {
            JsonArray tools = new JsonArray();
            JsonObject tool = new JsonObject();
            
            // En Gemini, las declaraciones de funciones se agrupan bajo functionDeclarations
            tool.add("function_declarations", modelRequest.functionDeclarations());
            tools.add(tool);
            requestBody.add("tools", tools);
        }
        
        return requestBody;
    }
    
    @Override
    public String buildEndpointUrl(String modelName) {
        String baseUrl = API_BASE_URL + modelName + ":generateContent";
        
        // Si se especifica un PROJECT_ID, usar el endpoint con el proyecto
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
        // headers.addProperty("Authorization", "Bearer " + apiKey); // Previous method
        headers.addProperty("x-goog-api-key", apiKey); // Using x-goog-api-key header
        headers.addProperty("Content-Type", "application/json");
        return headers;
    }
}
