package com.veyon.veyflow.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

/**
 * Redis-based implementation of {@link WorkflowConfigRepository}.
 */
public class RedisWorkflowConfigRepository implements WorkflowConfigRepository {

    private static final Logger log = LoggerFactory.getLogger(RedisWorkflowConfigRepository.class);
    private static final String KEY_PREFIX = "veyflow:workflow_config:";
    private final StatefulRedisConnection<String, String> connection;
    private final Gson gson;

    public RedisWorkflowConfigRepository(StatefulRedisConnection<String, String> connection) {
        this.connection = connection;
        this.gson = new GsonBuilder()
                // Potentially register adapters if WorkflowConfig has complex types like ZonedDateTime
                // .registerTypeAdapter(ZonedDateTime.class, new ZonedDateTimeAdapter()) 
                .create();
    }

    private String getKey(String tenantId) {
        return KEY_PREFIX + tenantId;
    }

    @Override
    public void save(WorkflowConfig config) {
        if (config == null || config.getTenantId() == null) {
            throw new IllegalArgumentException("WorkflowConfig and its tenantId must not be null");
        }
        RedisCommands<String, String> commands = connection.sync();
        String key = getKey(config.getTenantId());
        String json = gson.toJson(config);
        try {
            commands.set(key, json);
            log.debug("Saved WorkflowConfig for key: {}", key);
        } catch (Exception e) {
            log.error("Error saving WorkflowConfig to Redis for key {}: {}", key, e.getMessage(), e);
            throw e; // Re-throw to indicate failure
        }
    }

    @Override
    public Optional<WorkflowConfig> findById(String tenantId) {
        if (tenantId == null) {
            return Optional.empty();
        }
        RedisCommands<String, String> commands = connection.sync();
        String key = getKey(tenantId);
        try {
            String json = commands.get(key);
            if (json == null || json.isEmpty()) {
                log.debug("WorkflowConfig not found in Redis for key: {}", key);
                return Optional.empty();
            }
            WorkflowConfig config = gson.fromJson(json, WorkflowConfig.class);
            log.debug("Found WorkflowConfig in Redis for key: {}", key);
            return Optional.of(config);
        } catch (Exception e) {
            log.error("Error finding WorkflowConfig in Redis for key {}: {}", key, e.getMessage(), e);
            return Optional.empty(); // Or re-throw depending on error handling strategy
        }
    }

    @Override
    public boolean delete(String tenantId) {
        if (tenantId == null) {
            return false;
        }
        RedisCommands<String, String> commands = connection.sync();
        String key = getKey(tenantId);
        try {
            Long result = commands.del(key);
            boolean deleted = result != null && result > 0;
            if (deleted) {
                log.debug("Deleted WorkflowConfig from Redis for key: {}", key);
            } else {
                log.debug("WorkflowConfig not found or not deleted from Redis for key: {}", key);
            }
            return deleted;
        } catch (Exception e) {
            log.error("Error deleting WorkflowConfig from Redis for key {}: {}", key, e.getMessage(), e);
            return false; // Or re-throw
        }
    }

    @Override
    public boolean exists(String tenantId) {
        if (tenantId == null) {
            return false;
        }
        RedisCommands<String, String> commands = connection.sync();
        String key = getKey(tenantId);
        try {
            Long result = commands.exists(key);
            boolean exists = result != null && result > 0;
            log.debug("WorkflowConfig {} in Redis for key: {}", (exists ? "exists" : "does not exist"), key);
            return exists;
        } catch (Exception e) {
            log.error("Error checking existence of WorkflowConfig in Redis for key {}: {}", key, e.getMessage(), e);
            return false; // Or re-throw
        }
    }
}
