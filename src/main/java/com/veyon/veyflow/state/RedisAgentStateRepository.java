package com.veyon.veyflow.state;

import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import java.util.Optional;

public class RedisAgentStateRepository implements AgentStateRepository {

    private static final long TTL_SECONDS = 600; // 10 minutes
    private final RedisClient redisClient;
    private final String redisUri; // e.g., "redis://localhost:6379"

    // Constructor allowing Redis URI to be passed
    public RedisAgentStateRepository(String redisUri) {
        this.redisUri = redisUri;
        this.redisClient = RedisClient.create(this.redisUri);
    }

    // Default constructor using localhost
    public RedisAgentStateRepository() {
        this("redis://localhost:6379");
    }

    private String getKey(String tenantId, String threadId) {
        if (tenantId == null || tenantId.trim().isEmpty() || threadId == null || threadId.trim().isEmpty()) {
            throw new IllegalArgumentException("TenantId and ThreadId must not be null or empty");
        }
        return "agentstate:" + tenantId + "::" + threadId;
    }

    @Override
    public void save(AgentState state) {
        if (state == null) {
            throw new IllegalArgumentException("AgentState must not be null");
        }
        // Ensure persistence mode is REDIS, or handle appropriately
        // For now, we assume if this repository is used, it's for REDIS mode.
        // state.setPersistenceMode(PersistenceMode.REDIS); 

        try (StatefulRedisConnection<String, String> connection = redisClient.connect()) {
            RedisCommands<String, String> syncCommands = connection.sync();
            String key = getKey(state.getTenantId(), state.getThreadId());
            String jsonState = state.toJson();
            syncCommands.setex(key, TTL_SECONDS, jsonState);
        }
    }

    @Override
    public Optional<AgentState> findById(String tenantId, String threadId) {
        try (StatefulRedisConnection<String, String> connection = redisClient.connect()) {
            RedisCommands<String, String> syncCommands = connection.sync();
            String key = getKey(tenantId, threadId);
            String jsonState = syncCommands.get(key);
            if (jsonState == null || jsonState.isEmpty()) {
                return Optional.empty();
            }
            // Optionally, refresh TTL on read if desired (touch behavior)
            // syncCommands.expire(key, TTL_SECONDS); 
            return Optional.of(AgentState.fromJson(jsonState));
        } catch (IllegalArgumentException e) { // Catch issues from getKey
            return Optional.empty();
        }
    }

    @Override
    public boolean delete(String tenantId, String threadId) {
        try (StatefulRedisConnection<String, String> connection = redisClient.connect()) {
            RedisCommands<String, String> syncCommands = connection.sync();
            String key = getKey(tenantId, threadId);
            return syncCommands.del(key) > 0;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    @Override
    public boolean exists(String tenantId, String threadId) {
         try (StatefulRedisConnection<String, String> connection = redisClient.connect()) {
            RedisCommands<String, String> syncCommands = connection.sync();
            String key = getKey(tenantId, threadId);
            return syncCommands.exists(key) > 0;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    // Call this when your application shuts down to release resources
    public void shutdown() {
        if (redisClient != null) {
            redisClient.shutdown();
        }
    }
}
