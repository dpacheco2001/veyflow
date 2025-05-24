package com.veyon.veyflow.foundationmodels;

/**
 * Represents a safety setting for the Gemini API.
 *
 * @param category The category of harm to configure (e.g., "HARM_CATEGORY_DANGEROUS_CONTENT").
 * @param threshold The threshold for blocking content for this category (e.g., "BLOCK_NONE").
 */
public record SafetySetting(
    String category,
    String threshold
) {}
