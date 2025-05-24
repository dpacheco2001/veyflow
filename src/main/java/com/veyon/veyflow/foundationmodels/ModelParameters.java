package com.veyon.veyflow.foundationmodels;

import java.util.List;

public record ModelParameters(
    Float temperature,
    Integer maxOutputTokens,
    Double topP,                  // New
    List<String> stopSequences,   // New
    List<SafetySetting> safetySettings, // New
    ThinkingConfig thinkingConfig // New, replaces standalone thinkingBudget
) {
    // Compact constructor for validation (canonical constructor is implicitly called after this)
    public ModelParameters {
        if (temperature != null && (temperature < 0.0f || temperature > 2.0f)) {
            throw new IllegalArgumentException("Temperature must be between 0.0 and 2.0, or null.");
        }
        if (maxOutputTokens != null && maxOutputTokens <= 0) {
            throw new IllegalArgumentException("Max output tokens must be a positive integer, or null.");
        }
        if (topP != null && (topP < 0.0 || topP > 1.0)) {
            throw new IllegalArgumentException("topP must be between 0.0 and 1.0, or null.");
        }
    }

    // Non-canonical constructor for basic parameters
    public ModelParameters(Float temperature, Integer maxOutputTokens) {
        this(temperature, maxOutputTokens, null, null, null, null);
    }

    // Non-canonical constructor with thinkingBudget as Integer
    public ModelParameters(Float temperature, Integer maxOutputTokens, Integer thinkingBudget) {
        this(temperature, maxOutputTokens, null, null, null, 
             (thinkingBudget != null ? new ThinkingConfig(thinkingBudget) : null));
    }

    public static ModelParameters defaults() {
        // Defaults: temp 0.7, maxTokens 1024, topP null (model default), others null/empty.
        return new ModelParameters(0.7f, 1024, null, null, null, null);
    }
}
