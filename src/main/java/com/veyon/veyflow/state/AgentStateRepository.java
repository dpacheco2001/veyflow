package com.veyon.veyflow.state;

import java.util.Optional;

public interface AgentStateRepository {

    /**
     * Saves the given agent state.
     * If a state with the same tenantId and threadId already exists, it will be overwritten.
     *
     * @param state The AgentState to save.
     */
    void save(AgentState state);

    /**
     * Finds an AgentState by its tenantId and threadId.
     *
     * @param tenantId The ID of the tenant.
     * @param threadId The ID of the conversation thread.
     * @return An Optional containing the AgentState if found, or an empty Optional otherwise.
     */
    Optional<AgentState> findById(String tenantId, String threadId);

    /**
     * Deletes an AgentState by its tenantId and threadId.
     *
     * @param tenantId The ID of the tenant.
     * @param threadId The ID of the conversation thread.
     * @return true if the state was deleted, false otherwise (e.g., if not found).
     */
    boolean delete(String tenantId, String threadId);

    /**
     * Checks if an AgentState exists for the given tenantId and threadId.
     *
     * @param tenantId The ID of the tenant.
     * @param threadId The ID of the conversation thread.
     * @return true if the state exists, false otherwise.
     */
    boolean exists(String tenantId, String threadId);
}
