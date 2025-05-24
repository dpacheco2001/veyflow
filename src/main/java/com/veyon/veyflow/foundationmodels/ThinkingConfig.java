package com.veyon.veyflow.foundationmodels;

/**
 * Represents the thinking configuration for specific Gemini models (e.g., gemini-2.5 series).
 *
 * @param thinkingBudget The budget for thinking operations.
 */
public record ThinkingConfig(
    Integer thinkingBudget
) {}
