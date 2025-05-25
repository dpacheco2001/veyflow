package com.veyon.veyflow.foundationmodels;

import com.google.gson.JsonObject;
import com.veyon.veyflow.foundationmodels.adapters.GeminiRequestAdapter;
import com.veyon.veyflow.foundationmodels.adapters.GeminiResponseAdapter;
import com.veyon.veyflow.foundationmodels.adapters.ModelRequestAdapter;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Map;
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
    private final String apiKey;
    private final ModelRequestAdapter requestAdapter;
    private final GeminiResponseAdapter responseAdapter;

    public GeminiModelService() {
        this.apiKey = System.getenv(GOOGLE_API_KEY_ENV_VAR); // Corrected Env Var
        if (this.apiKey == null || this.apiKey.isEmpty()) {
            String errorMessage = "[CRITICAL] Gemini API key not found in environment variable " + GOOGLE_API_KEY_ENV_VAR;
            log.error(errorMessage);
        }
        this.httpClient = new OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build();
        this.requestAdapter = new GeminiRequestAdapter();
        this.responseAdapter = new GeminiResponseAdapter();
    }

    public GeminiModelService(String apiKey, OkHttpClient httpClient, ModelRequestAdapter requestAdapter, GeminiResponseAdapter responseAdapter) {
        Objects.requireNonNull(apiKey, "apiKey cannot be null");
        Objects.requireNonNull(httpClient, "httpClient cannot be null");
        Objects.requireNonNull(requestAdapter, "requestAdapter cannot be null");
        Objects.requireNonNull(responseAdapter, "responseAdapter cannot be null");
        this.apiKey = apiKey;
        this.httpClient = httpClient;
        this.requestAdapter = requestAdapter;
        this.responseAdapter = responseAdapter;
    }

    public GeminiModelService(String apiKey, OkHttpClient httpClient, ModelRequestAdapter requestAdapter) {
        this(apiKey, httpClient, requestAdapter, new GeminiResponseAdapter()); // Pass null for gson or remove if not needed
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
    public ModelTurnResponse generate(ModelRequest modelRequest) {
        ModelRequest enrichedRequest = enrichModelRequest(modelRequest);
        String modelToUse = (enrichedRequest.modelName() != null && !enrichedRequest.modelName().isEmpty()) 
                            ? enrichedRequest.modelName() 
                            : DEFAULT_GEMINI_MODEL_NAME;

        String endpoint = API_BASE_URL + modelToUse + ":generateContent?key=" + this.apiKey;
        JsonObject payload = requestAdapter.adaptRequest(enrichedRequest);

        if (verbose) {
            log.info(YELLOW + "[Gemini Payload Sent]:\n" + payload.toString() + RESET);
        }

        RequestBody body = RequestBody.create(payload.toString(), MediaType.get("application/json; charset=utf-8"));
        
        // Prepare request headers from the enriched ModelRequest if available
        Request.Builder requestBuilder = new Request.Builder().url(endpoint).post(body);
        // Access _requestHeaders from additionalConfig map
        Map<String, Object> additionalConfigMap = enrichedRequest.additionalConfig(); 
        if (additionalConfigMap != null && additionalConfigMap.containsKey("_requestHeaders")) {
            Object headersObj = additionalConfigMap.get("_requestHeaders");
            if (headersObj instanceof JsonObject) {
                JsonObject headersJson = (JsonObject) headersObj;
                for (String headerName : headersJson.keySet()) {
                    if (headersJson.get(headerName).isJsonPrimitive()) {
                        requestBuilder.addHeader(headerName, headersJson.get(headerName).getAsString());
                    }
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
                        // Return a ModelTurnResponse with an error message or empty content
                        return new ModelTurnResponse("Error: Empty response from model", new ArrayList<>()); 
                    }
                    // Use the response adapter to parse the response body into ModelTurnResponse
                    return responseAdapter.parseResponse(responseBodyString);
                }
                
                log.error(RED + "[ERROR] HTTP {} (Attempt {}): {}" + RESET, response.code(), attempt, responseBodyString);
                if ((response.code() == 500 || response.code() == 429) && attempt < MAX_RETRIES) {
                    log.warn(YELLOW + "[WARN] HTTP {} on attempt {}. Retrying in {} seconds... Response: {}" + RESET, 
                             response.code(), attempt, (currentDelayMs / 1000), responseBodyString);
                    Thread.sleep(currentDelayMs);
                } else {
                    // For non-retryable errors or last attempt, return ModelTurnResponse with error
                    String errorMessage = "Gemini API request failed with HTTP code: " + response.code() + ". Body: " + responseBodyString;
                    return new ModelTurnResponse(errorMessage, new ArrayList<>());
                }

            } catch (IOException e) { 
                log.error(YELLOW + "[WARN] IOException on attempt {}: {}." + RESET, attempt, e.getMessage());
                if (attempt < MAX_RETRIES) {
                    log.info("Retrying in {} ms...", currentDelayMs);
                    try {
                        Thread.sleep(currentDelayMs);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        log.error(RED + "[ERROR] Thread interrupted during retry delay." + RESET);
                        String errorMessage = "Thread interrupted during Gemini API retry delay: " + ie.getMessage();
                        return new ModelTurnResponse(errorMessage, new ArrayList<>());
                    }
                } else {
                    log.error(RED + "[ERROR] IOException after {} attempts: {} " + RESET, attempt, e.getMessage());
                    String errorMessage = "Gemini API request failed after " + attempt + " attempts due to IOException: " + e.getMessage();
                    return new ModelTurnResponse(errorMessage, new ArrayList<>());
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error(RED + "[ERROR] Thread interrupted during retry delay for Gemini API call." + RESET);
                String errorMessage = "Gemini API call retry interrupted: " + e.getMessage();
                return new ModelTurnResponse(errorMessage, new ArrayList<>());
            }
        } 

        log.error(RED + "[ERROR] Gemini call failed definitively after {} attempts." + RESET, MAX_RETRIES);
        String finalErrorMessage = "Gemini API call failed after " + MAX_RETRIES + " attempts.";
        return new ModelTurnResponse(finalErrorMessage, new ArrayList<>());
    }
}
