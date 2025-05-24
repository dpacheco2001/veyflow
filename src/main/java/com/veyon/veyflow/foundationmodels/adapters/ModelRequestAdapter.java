package com.veyon.veyflow.foundationmodels.adapters;

import com.google.gson.JsonObject;
import com.veyon.veyflow.foundationmodels.FoundationModelService.ModelRequest;

/**
 * Interfaz para adaptadores de peticiones a modelos de fundación.
 * Cada proveedor de LLM (OpenAI, Google, etc.) tiene su propio formato de petición.
 */
public interface ModelRequestAdapter {
    
    /**
     * Convierte una petición genérica de modelo a un formato específico del proveedor.
     * 
     * @param modelRequest La petición genérica del modelo
     * @return Un objeto JsonObject con el formato específico del proveedor
     */
    JsonObject adaptRequest(ModelRequest modelRequest);
    
    /**
     * Construye la URL del endpoint para la petición.
     * 
     * @param modelName El nombre del modelo a utilizar
     * @return La URL completa del endpoint
     */
    String buildEndpointUrl(String modelName);
    
    /**
     * Proporciona los headers HTTP necesarios para la petición.
     * 
     * @param apiKey La clave API para autenticación
     * @return Un objeto con los headers HTTP
     */
    JsonObject getRequestHeaders(String apiKey);
}
