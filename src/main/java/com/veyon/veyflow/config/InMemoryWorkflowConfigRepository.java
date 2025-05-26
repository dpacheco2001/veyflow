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

    @Override
    public void save(WorkflowConfig config) {
        if (config == null || config.getTenantId() == null) {
            throw new IllegalArgumentException("WorkflowConfig and its tenantId must not be null");
        }
        store.put(config.getTenantId(), config);
    }

    @Override
    public Optional<WorkflowConfig> findById(String tenantId) {
        if (tenantId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(store.get(tenantId));
    }

    @Override
    public boolean delete(String tenantId) {
        if (tenantId == null) {
            return false;
        }
        return store.remove(tenantId) != null;
    }

    @Override
    public boolean exists(String tenantId) {
        if (tenantId == null) {
            return false;
        }
        return store.containsKey(tenantId);
    }
    
    /**
     * Clears all entries from the store. Useful for testing.
     */
    public void clear() {
        store.clear();
    }
}
