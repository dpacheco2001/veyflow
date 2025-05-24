package com.veyon.veyflow.foundationmodels;

import com.google.gson.JsonArray;

public record ModelRequest(
    String systemMessage,
    JsonArray messages,
    JsonArray toolSpecifications,
    ModelParameters modelParameters
) {
}
