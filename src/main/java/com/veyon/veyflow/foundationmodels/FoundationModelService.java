package com.veyon.veyflow.foundationmodels;

import com.google.gson.JsonArray;

import java.util.Map;

/**
 * FoundationModelService is an interface that provides a method to generate a response from a foundation model.
 */
public interface FoundationModelService {

    /**
     * Generates a response from a foundation model based on the provided request.
     *
     * @param request the request containing the model name, system instruction, contents, function declarations, parameters, and additional configuration
     * @return the generated response as a string
     */
    String generate(ModelRequest request);

    /**
     * ModelRequest is a record that represents a request to a foundation model.
     *
     * @param modelName the name of the foundation model
     * @param systemInstruction the system instruction for the model
     * @param contents the contents of the request, represented as a JSON array
     * @param functionDeclarations the function declarations for the model, represented as a JSON array
     * @param parameters the parameters for the model
     * @param additionalConfig additional configuration for the model
     */
    record ModelRequest(
        String modelName,
        String systemInstruction, 
        JsonArray contents, 
        JsonArray functionDeclarations, 
        ModelParameters parameters, 
        Map<String, Object> additionalConfig 
    ) {
        /**
         * Constructs a new ModelRequest instance.
         *
         * @throws IllegalArgumentException if modelName is null or blank, contents is null, or parameters is null
         */
        public ModelRequest {
            if (modelName == null || modelName.isBlank()) {
                throw new IllegalArgumentException("modelName cannot be null or blank.");
            }
            if (contents == null) { 
                throw new IllegalArgumentException("contents cannot be null.");
            }
            if (parameters == null) {
                throw new IllegalArgumentException("parameters cannot be null.");
            }
        }
    }
}
