package com.veyon.veyflow.foundationmodels;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import com.veyon.veyflow.foundationmodels.adapters.GeminiRequestAdapter;
import com.veyon.veyflow.foundationmodels.adapters.ModelRequestAdapter;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

public class GeminiModelService implements FoundationModelService {

    private static final Logger log = LoggerFactory.getLogger(GeminiModelService.class);
    private static final boolean verbose = true; // To match original logging style if needed via log.debug or log.info

    public static final String RESET  = "\033[0m"; // For verbose logging if directly printing
    public static final String RED    = "\033[0;31m";
    public static final String YELLOW = "\033[0;33m";
    public static final String BLUE   = "\033[0;34m";
    public static final String PURPLE = "\033[0;35m";

    private static final String GOOGLE_API_KEY_ENV_VAR = "GOOGLE_API_KEY";
    private static final String DEFAULT_GEMINI_MODEL_NAME = "gemini-2.5-flash-preview-04-17"; // Your preferred default
    private static final int MAX_RETRIES = 3;
    private static final long INITIAL_RETRY_DELAY_MS = 5000; // Matched from original
    private static final String API_BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models/";
    private static final String DEFAULT_SYSTEM_INSTRUCTION = "Eres un asistente útil y amigable.";

    private final OkHttpClient httpClient;
    private final Gson gson;
    private final String apiKey;
    private final ModelRequestAdapter requestAdapter;

    public GeminiModelService() {
        this.apiKey = System.getenv(GOOGLE_API_KEY_ENV_VAR);
        if (this.apiKey == null || this.apiKey.trim().isEmpty()) {
            log.error("Google API Key not found in environment variable: {}", GOOGLE_API_KEY_ENV_VAR);
            throw new IllegalStateException("Google API Key (GOOGLE_API_KEY) is not configured.");
        }

        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();
        this.gson = new GsonBuilder().create();
        this.requestAdapter = new GeminiRequestAdapter();
    }

    public GeminiModelService(String apiKey, OkHttpClient httpClient, Gson gson) {
        this(apiKey, httpClient, gson, new GeminiRequestAdapter());
    }
    
    public GeminiModelService(String apiKey, OkHttpClient httpClient, Gson gson, ModelRequestAdapter requestAdapter) {
        Objects.requireNonNull(apiKey, "apiKey cannot be null");
        Objects.requireNonNull(httpClient, "httpClient cannot be null");
        Objects.requireNonNull(gson, "gson cannot be null");
        Objects.requireNonNull(requestAdapter, "requestAdapter cannot be null");
        this.apiKey = apiKey;
        this.httpClient = httpClient;
        this.gson = gson;
        this.requestAdapter = requestAdapter;
    }
    

    private ModelRequest enrichModelRequest(ModelRequest modelRequest) {
        String systemInstruction = modelRequest.systemInstruction();
        if (systemInstruction == null || systemInstruction.trim().isEmpty()) {
            systemInstruction = DEFAULT_SYSTEM_INSTRUCTION;
            log.info("[GeminiModelService.enrichModelRequest] Using default system instruction.");
        }
        
        ZonedDateTime now = ZonedDateTime.now(ZoneId.of("America/Lima"));
        String dia = now.getDayOfWeek().getDisplayName(TextStyle.FULL, new Locale("es", "PE"));
        DateTimeFormatter isoFormatWithZ = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'");
        
        String enhancedSystemInstruction = systemInstruction +
            "\n\nContexto Adicional:\n- Día: " + dia +
            "\n- Fecha y Hora: " + now.format(isoFormatWithZ);
        
        // Crear una nueva instancia de ModelRequest con la instrucción de sistema enriquecida
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
            log.error("[CRITICAL] contents is null or empty. Cannot make Gemini API call.");
            log.error("[DEBUG-DIAGNOSTIC] Details: contents={}, functionDeclarations={}, temperature={}",
                    modelRequest.contents() != null ? "size 0" : "null",
                    modelRequest.functionDeclarations() != null ? modelRequest.functionDeclarations().size() + " declarations" : "null",
                    modelRequest.parameters() != null ? modelRequest.parameters().temperature() : "N/A");
            throw new IllegalArgumentException("contents cannot be null or empty for the Gemini API");
        }

        // Preparar el modelRequest con instrucciones adicionales si es necesario
        ModelRequest enrichedRequest = enrichModelRequest(modelRequest);
        
        // Usar el adaptador para construir el cuerpo de la solicitud
        String modelToUse = (enrichedRequest.modelName() != null && !enrichedRequest.modelName().isEmpty()) 
                            ? enrichedRequest.modelName() 
                            : DEFAULT_GEMINI_MODEL_NAME;
        
        String endpointUrl = requestAdapter.buildEndpointUrl(modelToUse);
        JsonObject payload = requestAdapter.adaptRequest(enrichedRequest);
        JsonObject headersJson = requestAdapter.getRequestHeaders(this.apiKey);

        // --- BEGIN DEBUG LOGS ---
        log.info("[GeminiModelService.generate] Attempting to call Gemini API.");
        log.info("[GeminiModelService.generate] Endpoint URL: {}", endpointUrl);
        if (this.apiKey != null && !this.apiKey.isEmpty()) {
            String apiKeyStart = this.apiKey.substring(0, Math.min(5, this.apiKey.length()));
            String apiKeyEnd = this.apiKey.substring(Math.max(0, this.apiKey.length() - 5));
            log.info("[GeminiModelService.generate] API Key (partial): Starts with '{}', Ends with '{}'", apiKeyStart, apiKeyEnd);
        } else {
            log.error("[GeminiModelService.generate] API Key is NULL or EMPTY when preparing request!");
        }
        // --- END DEBUG LOGS ---

        MediaType JSON = MediaType.get("application/json; charset=utf-8");
        RequestBody body = RequestBody.create(payload.toString(), JSON);
        
        // Obtener los headers del adaptador
        okhttp3.Request.Builder requestBuilder = new okhttp3.Request.Builder()
            .url(endpointUrl)
            .post(body);
        
        // Agregar los headers
        if (headersJson != null) {
            for (String headerName : headersJson.keySet()) {
                if (headersJson.get(headerName).isJsonPrimitive()) {
                    requestBuilder.addHeader(headerName, headersJson.get(headerName).getAsString());
                }
            }
        }
        
        okhttp3.Request request = requestBuilder.build();

        long currentDelayMs = INITIAL_RETRY_DELAY_MS;
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try (Response response = httpClient.newCall(request).execute()) {
                String responseBodyString = response.body() != null ? response.body().string() : null;
                if (verbose) log.info(PURPLE + "[HTTP] Response code: {} (Attempt {})" + RESET, response.code(), attempt);

                if (response.isSuccessful()) {
                    if (verbose) log.info(BLUE + "[HTTP] Response:\n" + responseBodyString + RESET);
                    if (responseBodyString == null || responseBodyString.trim().isEmpty()) {
                        log.warn("Gemini API returned successful but empty body.");
                        return "{\"error\": {\"message\": \"Empty response from model\"}}"; 
                    }
                    // Check for API-level errors or blocked prompts within the JSON response
                    try {
                        JsonObject jsonResponse = gson.fromJson(responseBodyString, JsonObject.class);
                        if (jsonResponse.has("error")) {
                            log.error("Gemini API returned an error in response body: {}", jsonResponse.get("error").toString());
                        }
                        if (jsonResponse.has("promptFeedback")) {
                            JsonObject promptFeedback = jsonResponse.getAsJsonObject("promptFeedback");
                            if (promptFeedback.has("blockReason")) {
                                String blockReason = promptFeedback.get("blockReason").getAsString();
                                log.warn("Gemini API blocked prompt. Reason: {}", blockReason);
                                // Return a structured error as per original intent, but let's ensure it's valid JSON string
                                JsonObject errorPayload = new JsonObject();
                                JsonObject errorDetail = new JsonObject();
                                errorDetail.addProperty("message", "Prompt blocked by API due to: " + blockReason);
                                errorDetail.addProperty("blockReason", blockReason);
                                errorPayload.add("error", errorDetail);
                                return gson.toJson(errorPayload);
                            }
                        }
                    } catch (JsonSyntaxException e) {
                        log.error("Failed to parse Gemini API JSON response: {}", e.getMessage());
                    }
                    return responseBodyString;
                }
                
                log.error(RED + "[ERROR] HTTP {} (Attempt {}): {}" + RESET, response.code(), attempt, responseBodyString);
                // Retry logic based on original: 500 errors or specific client errors like 429
                // OkHttp doesn't throw IOException for HTTP error codes directly like HttpURLConnection does for getErrorStream()
                if ((response.code() == 500 || response.code() == 429) && attempt < MAX_RETRIES) {
                    log.warn(YELLOW + "[WARN] HTTP {} on attempt {}. Retrying in {} seconds... Response: {}" + RESET, 
                             response.code(), attempt, (currentDelayMs / 1000), responseBodyString);
                    Thread.sleep(currentDelayMs);
                    // currentDelayMs *= 2; // Exponential backoff, original used fixed delay
                } else {
                    throw new IOException("Gemini API request failed with HTTP code: " + response.code() + ". Body: " + responseBodyString);
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
                        throw new RuntimeException("Thread interrupted during Gemini API retry delay", ie);
                    }
                } else {
                    log.error(RED + "[ERROR] IOException after {} attempts: {} " + RESET, attempt, e.getMessage());
                    throw new RuntimeException("Gemini API request failed after " + attempt + " attempts due to IOException: " + e.getMessage(), e);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error(RED + "[ERROR] Thread interrupted during retry delay for Gemini API call." + RESET);
                throw new RuntimeException("Gemini API call retry interrupted.", e);
            }
        } // End of retry loop

        log.error(RED + "[ERROR] Gemini call failed definitively after {} attempts." + RESET, MAX_RETRIES);
        throw new RuntimeException("Gemini API call failed after " + MAX_RETRIES + " attempts.");
    }
}
