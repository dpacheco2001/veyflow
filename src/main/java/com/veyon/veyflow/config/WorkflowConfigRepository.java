package com.veyon.veyflow.config;

import java.util.Optional;

/**
 * Repository interface for managing the persistence of {@link WorkflowConfig} objects.
 */
public interface WorkflowConfigRepository {

    /**
     * Saves the given WorkflowConfig.
     * If a config with the same tenantId and threadId already exists, it will be overwritten.
     *
     * @param config The WorkflowConfig to save.
     */
    void save(WorkflowConfig config);

    /**
     * Finds a WorkflowConfig by its tenant ID and thread ID.
     *
     * @param tenantId The tenant ID.
     * @param threadId The thread ID.
     * @return An {@link Optional} containing the found WorkflowConfig, or {@link Optional#empty()} if not found.
     */
    Optional<WorkflowConfig> findById(String tenantId, String threadId);

    /**
     * Deletes a WorkflowConfig by its tenant ID and thread ID.
     * 
     * @param tenantId The tenant ID.
     * @param threadId The thread ID.
     * @return true if the config was deleted, false otherwise (e.g., if not found).
     */
    boolean delete(String tenantId, String threadId);

    /**
     * Checks if a WorkflowConfig exists for the given tenantId and threadId.
     *
     * @param tenantId The ID of the tenant.
     * @param threadId The ID of the conversation thread.
     * @return true if the config exists, false otherwise.
     */
    boolean exists(String tenantId, String threadId);
}
