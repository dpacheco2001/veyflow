package com.veyon.veyflow.config;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory implementation of {@link WorkflowConfigRepository}.
 * This implementation is primarily for testing or single-instance deployments where persistence is not required across restarts.
 */
public class InMemoryWorkflowConfigRepository implements WorkflowConfigRepository {

    private final Map<String, WorkflowConfig> store = new ConcurrentHashMap<>();

    private String getKey(String tenantId, String threadId) {
        return tenantId + ":" + threadId;
    }

    @Override
    public void save(WorkflowConfig config) {
        if (config == null || config.getTenantId() == null || config.getThreadId() == null) {
            throw new IllegalArgumentException("WorkflowConfig and its tenantId and threadId must not be null");
        }
        store.put(getKey(config.getTenantId(), config.getThreadId()), config);
    }

    @Override
    public Optional<WorkflowConfig> findById(String tenantId, String threadId) {
        if (tenantId == null || threadId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(store.get(getKey(tenantId, threadId)));
    }

    @Override
    public boolean delete(String tenantId, String threadId) {
        if (tenantId == null || threadId == null) {
            return false;
        }
        return store.remove(getKey(tenantId, threadId)) != null;
    }

    @Override
    public boolean exists(String tenantId, String threadId) {
        if (tenantId == null || threadId == null) {
            return false;
        }
        return store.containsKey(getKey(tenantId, threadId));
    }
    
    /**
     * Clears all entries from the store. Useful for testing.
     */
    public void clear() {
        store.clear();
    }
}
