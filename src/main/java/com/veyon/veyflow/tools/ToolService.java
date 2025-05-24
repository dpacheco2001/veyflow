package com.veyon.veyflow.tools;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonElement;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Base class for all tool services in the agent framework.
 * This replaces the ToolServiceBase from the LangGraph implementation.
 */
public abstract class ToolService {
    private final String toolsJson;
    private final Map<String, Method> toolMethods = new HashMap<>();
    private final Map<String, Boolean> toolRecallSettings = new HashMap<>();

    /**
     * Creates a new tool service and initializes its tools.
     */
    protected ToolService() {
        this.toolsJson = buildToolsJson();
        initializeToolMethods();
    }

    /**
     * Initialize the tool methods map.
     */
    private void initializeToolMethods() {
        for (Method method : this.getClass().getDeclaredMethods()) {
            if (method.isAnnotationPresent(ToolAnnotation.class)) {
                ToolAnnotation annotation = method.getAnnotation(ToolAnnotation.class);
                String methodName = method.getName();
                toolMethods.put(methodName, method);
                toolRecallSettings.put(methodName, annotation.recall());
            }
        }
    }

    /**
     * Get the JSON representation of the tools.
     * 
     * @return JSON string representing the tools
     */
    public String getToolsJson() {
        return toolsJson;
    }

    /**
     * Check if a tool requires recall.
     * 
     * @param toolName The name of the tool
     * @return True if the tool requires recall
     */
    public boolean shouldRecall(String toolName) {
        return toolRecallSettings.getOrDefault(toolName, true);
    }

    /**
     * Get a tool method by name.
     * 
     * @param toolName The name of the tool
     * @return The method, or null if not found
     */
    public Method getToolMethod(String toolName) {
        return toolMethods.get(toolName);
    }
    
    /**
     * Execute a tool method by its name with the provided parameters.
     * 
     * @param toolName The name of the tool method to execute
     * @param parameters The parameters as a JsonObject
     * @param state The current agent state (if needed by the tool)
     * @return The result of the tool execution as a JsonObject
     */
    public JsonObject executeToolMethod(String toolName, JsonObject parameters, Object state) {
        try {
            // Obtener el método a ejecutar
            Method method = getToolMethod(toolName);
            if (method == null) {
                JsonObject errorResult = new JsonObject();
                errorResult.addProperty("status", "error");
                errorResult.addProperty("message", "Tool method not found: " + toolName);
                return errorResult;
            }
            
            // Preparar los parámetros para la invocación
            Parameter[] methodParams = method.getParameters();
            Object[] args = new Object[methodParams.length];
            
            for (int i = 0; i < methodParams.length; i++) {
                Parameter param = methodParams[i];
                String paramName = null;
                
                // Obtener el nombre del parámetro desde la anotación
                if (param.isAnnotationPresent(ToolParameter.class)) {
                    ToolParameter annotation = param.getAnnotation(ToolParameter.class);
                    paramName = annotation.value();
                }
                
                // Si no tiene anotación, usar el nombre del parámetro
                if (paramName == null || paramName.isEmpty()) {
                    paramName = param.getName();
                }
                
                // Convertir el parámetro JSON al tipo correcto
                Class<?> paramType = param.getType();
                
                if (state != null && paramType.isAssignableFrom(state.getClass())) {
                    args[i] = state;
                } else if (parameters != null && parameters.has(paramName)) {
                    JsonElement paramValue = parameters.get(paramName);
                    
                    if (paramType == String.class) {
                        args[i] = paramValue.getAsString();
                    } else if (paramType == int.class || paramType == Integer.class) {
                        args[i] = paramValue.getAsInt();
                    } else if (paramType == boolean.class || paramType == Boolean.class) {
                        args[i] = paramValue.getAsBoolean();
                    } else if (paramType == double.class || paramType == Double.class) {
                        args[i] = paramValue.getAsDouble();
                    } else if (paramType == JsonObject.class) {
                        args[i] = paramValue.getAsJsonObject();
                    } else {
                        // Para otros tipos, intentamos convertir de String
                        args[i] = paramValue.getAsString();
                    }
                } else if (parameters == null || !parameters.has(paramName)) {
                    if (paramType.isPrimitive()) {
                        // Valores por defecto para tipos primitivos
                        if (paramType == int.class) args[i] = 0;
                        else if (paramType == boolean.class) args[i] = false;
                        else if (paramType == double.class) args[i] = 0.0;
                        else args[i] = null; // Esto puede causar un NPE para tipos primitivos
                    } else {
                        args[i] = null;
                    }
                }
            }
            
            // Invocar el método
            Object result = method.invoke(this, args);
            
            // Manejar el resultado
            if (result instanceof JsonObject) {
                return (JsonObject) result;
            } else if (result != null) {
                // Convertir otros tipos de resultado a JsonObject
                JsonObject jsonResult = new JsonObject();
                jsonResult.addProperty("result", result.toString());
                jsonResult.addProperty("status", "success");
                return jsonResult;
            } else {
                JsonObject jsonResult = new JsonObject();
                jsonResult.addProperty("status", "success");
                jsonResult.addProperty("message", "Tool executed successfully with null result");
                return jsonResult;
            }
            
        } catch (Exception e) {
            JsonObject errorResult = new JsonObject();
            errorResult.addProperty("status", "error");
            errorResult.addProperty("message", "Error executing tool: " + e.getMessage());
            return errorResult;
        }
    }

    /**
     * Build the JSON representation of the tools.
     * 
     * @return JSON string representing the tools
     */
    protected String buildToolsJson() {
        JsonObject toolObj = new JsonObject();
        JsonArray functionDeclarations = new JsonArray();

        for (Method method : this.getClass().getDeclaredMethods()) {
            if (!method.isAnnotationPresent(ToolAnnotation.class)) continue;
            ToolAnnotation toolAnn = method.getAnnotation(ToolAnnotation.class);

            JsonObject fn = new JsonObject();
            fn.addProperty("name", method.getName());
            fn.addProperty("description", toolAnn.value());

            JsonObject paramsSchema = new JsonObject();
            paramsSchema.addProperty("type", "object");
            JsonObject props = new JsonObject();
            JsonArray required = new JsonArray();

            for (Parameter param : method.getParameters()) {
                ToolParameter paramAnn = param.getAnnotation(ToolParameter.class);
                if (paramAnn == null) continue;
                
                String paramName = param.getName();
                JsonObject prop = new JsonObject();
                
                // Determine parameter type
                String paramType = mapJavaTypeToJsonType(param.getType());
                prop.addProperty("type", paramType);
                prop.addProperty("description", paramAnn.value());
                props.add(paramName, prop);
                
                // Add to required parameters if not Optional
                if (!param.getType().equals(Optional.class)) {
                    required.add(paramName);
                }
            }

            paramsSchema.add("properties", props);
            if (required.size() > 0) {
                paramsSchema.add("required", required);
            }
            
            fn.add("parameters", paramsSchema);
            functionDeclarations.add(fn);
        }

        toolObj.add("functionDeclarations", functionDeclarations);
        JsonArray tools = new JsonArray();
        tools.add(toolObj);
        return new Gson().toJson(tools);
    }
    
    /**
     * Map Java types to JSON types.
     * 
     * @param javaType The Java type
     * @return The corresponding JSON type
     */
    private String mapJavaTypeToJsonType(Class<?> javaType) {
        if (javaType.equals(String.class)) {
            return "string";
        } else if (javaType.equals(int.class) || javaType.equals(Integer.class) ||
                  javaType.equals(long.class) || javaType.equals(Long.class) ||
                  javaType.equals(float.class) || javaType.equals(Float.class) ||
                  javaType.equals(double.class) || javaType.equals(Double.class)) {
            return "number";
        } else if (javaType.equals(boolean.class) || javaType.equals(Boolean.class)) {
            return "boolean";
        } else if (javaType.equals(java.util.List.class) || javaType.isArray()) {
            return "array";
        } else if (javaType.equals(java.util.Map.class) || javaType.equals(JsonObject.class)) {
            return "object";
        } else {
            return "object";
        }
    }
}
