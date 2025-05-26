package com.veyon.veyflow.state;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryAgentStateRepository implements AgentStateRepository {

    private final Map<String, AgentState> store = new ConcurrentHashMap<>();

    private String getKey(String tenantId, String threadId) {
        return tenantId + "::" + threadId;
    }

    @Override
    public void save(AgentState state) {
        if (state == null || state.getTenantId() == null || state.getThreadId() == null) {
            throw new IllegalArgumentException("AgentState and its tenantId and threadId must not be null");
        }
        store.put(getKey(state.getTenantId(), state.getThreadId()), state);
    }

    @Override
    public Optional<AgentState> findById(String tenantId, String threadId) {
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
}
