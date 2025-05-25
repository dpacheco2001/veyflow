package com.veyon.veyflow.tools;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.util.Collections;
import java.util.Map;

/**
 * Represents a tool/function call from a model response.
 * Contains the name of the tool and its parameters.
 */
public class ToolCall {
    private final String id;
    private final String name;
    private final JsonObject parameters;
    
    /**
     * Create a new tool call.
     * 
     * @param id Unique identifier for this tool call
     * @param name Name of the tool to call
     * @param parameters Parameters for the tool call
     */
    public ToolCall(String id, String name, JsonObject parameters) {
        this.id = id;
        this.name = name;
        this.parameters = parameters;
    }
    
    /**
     * Get the ID of this tool call.
     * 
     * @return The tool call ID
     */
    public String getId() {
        return id;
    }
    
    /**
     * Get the name of the tool to call.
     * 
     * @return The tool name
     */
    public String getName() {
        return name;
    }
    
    /**
     * Get the parameters for this tool call.
     * 
     * @return A JsonObject containing the parameters
     */
    public JsonObject getParameters() {
        return parameters;
    }
    
    /**
     * Converts the JsonObject parameters to a Map<String, Object>.
     * @return A map representation of the parameters, or an empty map if parameters are null.
     */
    public Map<String, Object> getParametersAsMap() {
        if (this.parameters == null) {
            return Collections.emptyMap();
        }
        Type type = new TypeToken<Map<String, Object>>() {}.getType();
        return new Gson().fromJson(this.parameters, type);
    }
}
