package com.veyon.veyflow.tools;

/**
 * Service interface for determining if a specific tool method within a ToolService
 * is active for a given tenant.
 */
public interface TenantToolActivationService {

    /**
     * Checks if a specific tool method is active for a given tenant.
     *
     * @param tenantId The unique identifier for the tenant.
     * @param toolServiceClassName The fully qualified class name of the ToolService.
     * @param methodName The name of the method within the ToolService to check.
     * @return {@code true} if the tool method is active for the tenant, {@code false} otherwise.
     */
    boolean isToolMethodActive(String tenantId, String toolServiceClassName, String methodName);

}
