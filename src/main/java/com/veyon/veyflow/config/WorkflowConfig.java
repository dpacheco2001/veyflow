package com.veyon.veyflow.config;

import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.Collections;

/**
 * Configuration for a workflow execution, defining which tool services and specific methods
 * within those services are active. This configuration is expected to be specific to a given
 * context (e.g., a tenant) and prepared before being passed to the ToolAgent.
 */
public class WorkflowConfig {

    // Map<ServiceClassName, List<MethodName>>
    // List<MethodName> can contain "*" to indicate all methods of the service are active.
    private Map<String, List<String>> configuredToolServices;

    /**
     * Default constructor initializing an empty configuration.
     */
    public WorkflowConfig() {
        this.configuredToolServices = new HashMap<>();
    }

    /**
     * Activates specific methods for a given tool service.
     * If the service was already configured, this will overwrite its previous method list.
     *
     * @param serviceClassName The fully qualified class name of the ToolService.
     * @param methodNames A list of method names within the service to activate.
     */
    public void activateToolMethods(String serviceClassName, List<String> methodNames) {
        if (serviceClassName == null || serviceClassName.isBlank()) {
            throw new IllegalArgumentException("Service class name cannot be null or blank.");
        }
        if (methodNames == null || methodNames.isEmpty()) {
            // To deactivate a service or clear its methods, one might call a different method
            // or pass an empty list explicitly if that's the desired semantic.
            // For now, let's assume activating with no methods means no methods are active.
            this.configuredToolServices.put(serviceClassName, new ArrayList<>());
            return;
        }
        this.configuredToolServices.put(serviceClassName, new ArrayList<>(methodNames));
    }

    /**
     * Activates all methods for a given tool service.
     * This is a convenience method that uses "*" to represent all methods.
     *
     * @param serviceClassName The fully qualified class name of the ToolService.
     */
    public void activateAllToolMethodsForService(String serviceClassName) {
        if (serviceClassName == null || serviceClassName.isBlank()) {
            throw new IllegalArgumentException("Service class name cannot be null or blank.");
        }
        this.configuredToolServices.put(serviceClassName, Collections.singletonList("*"));
    }

    /**
     * Checks if a specific tool method is active within a given tool service according to this configuration.
     *
     * @param serviceClassName The fully qualified class name of the ToolService.
     * @param methodName The name of the method to check.
     * @return {@code true} if the method is active, {@code false} otherwise.
     */
    public boolean isToolMethodActive(String serviceClassName, String methodName) {
        if (serviceClassName == null || methodName == null) {
            return false;
        }
        List<String> activeMethods = this.configuredToolServices.get(serviceClassName);
        if (activeMethods == null) {
            return false; // Service not configured, thus method is not active.
        }
        return activeMethods.contains("*") || activeMethods.contains(methodName);
    }

    /**
     * Gets the set of fully qualified class names of tool services that have any methods activated
     * in this configuration.
     *
     * @return A set of active tool service class names. Returns an empty set if none are configured.
     */
    public Set<String> getActiveServiceClassNames() {
        return Collections.unmodifiableSet(this.configuredToolServices.keySet());
    }

    // In the future, this class can be expanded to include other
    // workflow-level or tenant-specific configurations.
    // For example:
    // private String tenantId;
    // private Map<String, Object> otherTenantConfigs;
}
