package com.veyon.veyflow.foundationmodels;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import com.veyon.veyflow.foundationmodels.adapters.ModelRequestAdapter;
import com.veyon.veyflow.foundationmodels.adapters.OpenAiRequestAdapter;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

public class OpenAIModelService implements FoundationModelService {

    private static final Logger log = LoggerFactory.getLogger(OpenAIModelService.class);
    private static final boolean verbose = true;

    public static final String RESET  = "\033[0m";
    public static final String RED    = "\033[0;31m";
    public static final String GREEN  = "\033[0;32m";
    public static final String YELLOW = "\033[0;33m";
    public static final String BLUE   = "\033[0;34m";
    public static final String PURPLE = "\033[0;35m";

    private static final String OPENAI_API_KEY_ENV_VAR = "OPENAI_API_KEY";
    private static final String DEFAULT_OPENAI_MODEL_NAME = "gpt-4o";
    private static final int MAX_RETRIES = 3;
    private static final long INITIAL_RETRY_DELAY_MS = 5000;
    private static final String API_BASE_URL = "https://api.openai.com/v1/chat/completions";
    private static final String DEFAULT_SYSTEM_INSTRUCTION = "Eres un asistente útil y amigable.";

    private final OkHttpClient httpClient;
    private final Gson gson;
    private final String apiKey;
    private final ModelRequestAdapter requestAdapter;

    public OpenAIModelService() {
        this.apiKey = System.getenv(OPENAI_API_KEY_ENV_VAR);
        if (this.apiKey == null || this.apiKey.trim().isEmpty()) {
            log.error("OpenAI API Key not found in environment variable: {}", OPENAI_API_KEY_ENV_VAR);
            throw new IllegalStateException("OpenAI API Key (OPENAI_API_KEY) is not configured.");
        }

        if (log.isInfoEnabled()) {
            log.info("OpenAI API Key: [REDACTED]");
        }

        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();
        this.gson = new GsonBuilder().create();
        this.requestAdapter = new OpenAiRequestAdapter();
    }

    public OpenAIModelService(String apiKey, OkHttpClient httpClient, Gson gson) {
        this(apiKey, httpClient, gson, new OpenAiRequestAdapter());
    }
    
    public OpenAIModelService(String apiKey, OkHttpClient httpClient, Gson gson, ModelRequestAdapter requestAdapter) {
        Objects.requireNonNull(apiKey, "apiKey cannot be null");
        Objects.requireNonNull(httpClient, "httpClient cannot be null");
        Objects.requireNonNull(gson, "gson cannot be null");
        Objects.requireNonNull(requestAdapter, "requestAdapter cannot be null");
        this.apiKey = apiKey;
        this.httpClient = httpClient;
        this.gson = gson;
        this.requestAdapter = requestAdapter;

        if (log.isInfoEnabled()) {
            log.info("OpenAI API Key: [REDACTED]");
        }
    }
    
    private ModelRequest enrichModelRequest(ModelRequest modelRequest) {
        String systemInstruction = modelRequest.systemInstruction();
        if (systemInstruction == null || systemInstruction.trim().isEmpty()) {
            systemInstruction = DEFAULT_SYSTEM_INSTRUCTION;
            log.info("[OpenAIModelService.enrichModelRequest] Using default system instruction.");
        }
        
        ZonedDateTime now = ZonedDateTime.now(ZoneId.of("America/Lima"));
        String dia = now.getDayOfWeek().getDisplayName(TextStyle.FULL, new Locale("es", "PE"));
        DateTimeFormatter isoFormatWithZ = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'");
        
        String enhancedSystemInstruction = systemInstruction +
            "\n\nContexto Adicional:\n- Día: " + dia +
            "\n- Fecha y Hora: " + now.format(isoFormatWithZ);
        
        return new ModelRequest(
            modelRequest.modelName(),
            enhancedSystemInstruction,
            modelRequest.contents(),
            modelRequest.functionDeclarations(),
            modelRequest.parameters(),
            modelRequest.additionalConfig()
        );
    }

    @Override
    public String generate(ModelRequest modelRequest) {
        if (modelRequest.contents() == null || modelRequest.contents().size() == 0) {
            log.error("[CRITICAL] contents is null or empty. Cannot make OpenAI API call.");
            log.error("[DEBUG-DIAGNOSTIC] Details: contents={}, functionDeclarations={}, temperature={}",
                    modelRequest.contents() != null ? "size 0" : "null",
                    modelRequest.functionDeclarations() != null ? modelRequest.functionDeclarations().size() + " declarations" : "null",
                    modelRequest.parameters() != null ? modelRequest.parameters().temperature() : "N/A");
            throw new IllegalArgumentException("contents cannot be null or empty for the OpenAI API");
        }

        // Preparar el modelRequest con instrucciones adicionales si es necesario
        ModelRequest enhancedRequest = enrichModelRequest(modelRequest);
        
        // Usar el adaptador para construir el cuerpo de la solicitud
        JsonObject payload = requestAdapter.adaptRequest(enhancedRequest);
        
        // Determinar el nombre del modelo a usar
        String effectiveModelName = enhancedRequest.modelName() != null && !enhancedRequest.modelName().isBlank() 
                                   ? enhancedRequest.modelName() : DEFAULT_OPENAI_MODEL_NAME;
        
        // Construir la URL del endpoint
        String url = requestAdapter.buildEndpointUrl(effectiveModelName);
        
        log.info("[OpenAIModelService.generate] Request prepared for model: {}. Content items: {}", 
                effectiveModelName, 
                enhancedRequest.contents() != null ? enhancedRequest.contents().size() : 0);
        
        // Handle additionalConfig if present
        // Check if there's an additionalConfig object and handle it appropriately
        if (modelRequest.additionalConfig() != null) {
            Object additionalConfig = modelRequest.additionalConfig();
            
            // If additionalConfig is a Map<String, Object>, process its entries
            if (additionalConfig instanceof Map) {
                Map<String, Object> configMap = (Map<String, Object>) additionalConfig;
                for (Map.Entry<String, Object> entry : configMap.entrySet()) {
                    if (entry.getValue() instanceof String) {
                        payload.addProperty(entry.getKey(), (String) entry.getValue());
                    } else if (entry.getValue() instanceof Number) {
                        payload.addProperty(entry.getKey(), (Number) entry.getValue());
                    } else if (entry.getValue() instanceof Boolean) {
                        payload.addProperty(entry.getKey(), (Boolean) entry.getValue());
                    }
                }
            }
        }

        String jsonPayload = gson.toJson(payload);
        if (verbose) {
            log.debug(BLUE + "[OpenAIModelService.generate] Request payload: " + RESET + "\n" +
                     new GsonBuilder().setPrettyPrinting().create().toJson(JsonParser.parseString(jsonPayload)) + RESET);
        }

        MediaType JSON = MediaType.get("application/json; charset=utf-8");
        RequestBody body = RequestBody.create(jsonPayload, JSON);
        
        // Obtener los headers del adaptador
        JsonObject headers = requestAdapter.getRequestHeaders(this.apiKey);
        
        // Construir la solicitud HTTP
        okhttp3.Request.Builder requestBuilder = new okhttp3.Request.Builder()
            .url(url)
            .post(body);
        
        // Agregar los headers
        if (headers != null) {
            for (String headerName : headers.keySet()) {
                if (headers.get(headerName).isJsonPrimitive()) {
                    requestBuilder.addHeader(headerName, headers.get(headerName).getAsString());
                }
            }
        }
        
        okhttp3.Request request = requestBuilder.build();

        long currentDelayMs = INITIAL_RETRY_DELAY_MS;
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                // Convertir JsonObject a string para el cuerpo de la solicitud
                String requestBodyJson = gson.toJson(payload);
                
                if (verbose) {
                    log.debug("[OpenAIModelService.generate] Request body: {}", requestBodyJson);
                }

                Response response = httpClient.newCall(request).execute();
                if (!response.isSuccessful() && response.code() != 429 && response.code() < 500) { 
                    // Client errors except 429 are usually not recoverable via retries
                    String responseBodyString = response.body().string();
                    log.error(RED + "[ERROR] HTTP {} - Response: {}" + RESET, response.code(), responseBodyString);
                    throw new IOException("OpenAI API request failed with HTTP code: " + response.code() + ". Body: " + responseBodyString);
                }

                String responseBodyString = response.body().string();
                if (response.isSuccessful()) {
                    if (verbose) {
                        log.debug(GREEN + "[OpenAIModelService.generate] Response: " + RESET + "\n" + 
                                 responseBodyString.substring(0, Math.min(500, responseBodyString.length())) + 
                                 (responseBodyString.length() > 500 ? "..." : ""));
                    }

                    try {
                        JsonObject jsonResponse = JsonParser.parseString(responseBodyString).getAsJsonObject();
                        if (jsonResponse.has("choices") && jsonResponse.getAsJsonArray("choices").size() > 0) {
                            JsonObject choice = jsonResponse.getAsJsonArray("choices").get(0).getAsJsonObject();
                            
                            if (choice.has("message")) {
                                JsonObject message = choice.getAsJsonObject("message");
                                JsonElement contentElement = message.get("content");
                                String textContent = null;

                                if (contentElement != null && !contentElement.isJsonNull()) {
                                    textContent = contentElement.getAsString();
                                }

                                // If there's text content, return it.
                                if (textContent != null) {
                                    return textContent.trim();
                                }

                                // If content is null, BUT there are tool_calls (either in message or by finish_reason),
                                // the ModelNode expects the full JSON string to parse tool_calls.
                                boolean hasToolCallsInMessage = message.has("tool_calls") && message.getAsJsonArray("tool_calls").size() > 0;
                                boolean finishReasonIsToolCalls = choice.has("finish_reason") && "tool_calls".equals(choice.get("finish_reason").getAsString());

                                if (hasToolCallsInMessage || finishReasonIsToolCalls) {
                                    return responseBodyString; // Return the full JSON string which includes tool_calls
                                }
                                
                                // If content is null and no tool calls, model provided no text and no actions.
                                // Return empty string to avoid JsonNull issues downstream.
                                log.warn("[OpenAIModelService.generate] Response message content is null and no tool_calls found. Choice: {}", choice.toString());
                                return ""; 
                            } else {
                                // Fallback if 'message' object is missing, though unlikely with valid API responses.
                                log.warn("[OpenAIModelService.generate] 'message' object missing in choice. Choice: {}", choice.toString());
                                return responseBodyString; // Or return an error/empty string
                            }
                        }
                    } catch (JsonSyntaxException e) {
                        log.error("Failed to parse OpenAI API JSON response: {}", e.getMessage());
                    }
                    return responseBodyString;
                }
                
                log.error(RED + "[ERROR] HTTP {} (Attempt {}): {}" + RESET, response.code(), attempt, responseBodyString);
                // Retry logic based on original: 500 errors or specific client errors like 429
                if ((response.code() == 500 || response.code() == 429) && attempt < MAX_RETRIES) {
                    log.warn(YELLOW + "[WARN] HTTP {} on attempt {}. Retrying in {} seconds... Response: {}" + RESET, 
                             response.code(), attempt, (currentDelayMs / 1000), responseBodyString);
                    Thread.sleep(currentDelayMs);
                    // currentDelayMs *= 2; // Exponential backoff
                } else {
                    throw new IOException("OpenAI API request failed with HTTP code: " + response.code() + ". Body: " + responseBodyString);
                }

            } catch (IOException e) { // Catches network issues, timeouts from OkHttp, or the re-thrown above
                log.error(YELLOW + "[WARN] IOException on attempt {}: {}." + RESET, attempt, e.getMessage());
                if (attempt < MAX_RETRIES) {
                    log.info("Retrying in {} ms...", currentDelayMs);
                    try {
                        Thread.sleep(currentDelayMs);
                        // currentDelayMs *= 2;
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        log.error(RED + "[ERROR] Thread interrupted during retry delay." + RESET);
                        throw new RuntimeException("Thread interrupted during OpenAI API retry delay", ie);
                    }
                } else {
                    log.error(RED + "[ERROR] IOException after {} attempts: {} " + RESET, attempt, e.getMessage());
                    throw new RuntimeException("OpenAI API request failed after " + attempt + " attempts due to IOException: " + e.getMessage(), e);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error(RED + "[ERROR] Thread interrupted during retry delay for OpenAI API call." + RESET);
                throw new RuntimeException("OpenAI API call retry interrupted.", e);
            }
        } // End of retry loop

        log.error(RED + "[ERROR] OpenAI call failed definitively after {} attempts." + RESET, MAX_RETRIES);
        throw new RuntimeException("OpenAI API call failed after " + MAX_RETRIES + " attempts.");
    }
}
