package com.veyon.veyflow.config;

import com.veyon.veyflow.state.PersistenceMode;

import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.Collections;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Configuration for a workflow execution, defining which tool services and specific methods
 * within those services are active. This configuration is expected to be specific to a given
 * context (e.g., a tenant) and prepared before being passed to the ToolAgent.
 */
public class WorkflowConfig {

    private static final Logger log = LoggerFactory.getLogger(WorkflowConfig.class);

    private String tenantId;
    private PersistenceMode persistenceMode;

    // Map<ServiceClassName, List<MethodName>>
    // List<MethodName> can contain "*" to indicate all methods of the service are active.
    private Map<String, List<String>> configuredToolServices;

    private transient WorkflowConfigRepository repository;
    private transient boolean isDirty = false;

    /**
     * Default constructor initializing an empty configuration.
     */
    public WorkflowConfig() {
        this.configuredToolServices = new HashMap<>();
        this.persistenceMode = PersistenceMode.IN_MEMORY;
    }

    /**
     * Constructor for creating a WorkflowConfig with specific identifiers.
     *
     * @param tenantId The tenant ID.
     */
    public WorkflowConfig(String tenantId) {
        this();
        this.tenantId = tenantId;
    }

    /**
     * Constructor for creating a WorkflowConfig with specific identifiers and persistence mode.
     *
     * @param tenantId The tenant ID.
     * @param persistenceMode The persistence mode.
     */
    public WorkflowConfig(String tenantId, PersistenceMode persistenceMode) {
        this(tenantId);
        this.persistenceMode = (persistenceMode != null) ? persistenceMode : PersistenceMode.IN_MEMORY;
        // Initial state is not dirty from constructor
    }

    // Repository and Dirty Flag Management
    public void setRepository(WorkflowConfigRepository repository) {
        this.repository = repository;
    }

    public void clearDirtyFlag() {
        this.isDirty = false;
    }

    public boolean isDirty() {
        return isDirty;
    }

    public void save() {
        if (repository == null) {
            log.warn("Repository not set, cannot save WorkflowConfig for tenant {}.", tenantId);
            // Optionally, throw new IllegalStateException("Repository not set.");
            return;
        }

        if (persistenceMode == PersistenceMode.IN_MEMORY) {
            log.debug("PersistenceMode is IN_MEMORY, explicit save operation to external store skipped for WorkflowConfig tenant {}.", tenantId);
            // If you want InMemoryRepository to also clear dirty flag upon any 'save' call:
            // if (isDirty) { repository.save(this); this.isDirty = false; }
            return;
        }

        if (persistenceMode == PersistenceMode.REDIS && (tenantId == null || tenantId.isBlank())) {
            throw new IllegalStateException("Cannot save to REDIS without a valid tenantId.");
        }

        if (isDirty) {
            try {
                log.info("Saving dirty WorkflowConfig for tenant {} with mode {}.", tenantId, persistenceMode);
                repository.save(this);
                this.isDirty = false; // Reset dirty flag after successful save
            } catch (Exception e) {
                log.error("Failed to save WorkflowConfig for tenant {}: {}", tenantId, e.getMessage(), e);
                // Depending on policy, we might want to keep isDirty=true if save fails, or rethrow
            }
        } else {
            log.debug("WorkflowConfig for tenant {} is not dirty, save operation skipped.", tenantId);
        }
    }

    // Getters and Setters
    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        if (this.tenantId == null || !this.tenantId.equals(tenantId)) {
            this.tenantId = tenantId;
            this.isDirty = true;
        }
    }

    public PersistenceMode getPersistenceMode() {
        return persistenceMode;
    }

    public void setPersistenceMode(PersistenceMode persistenceMode) {
        PersistenceMode newMode = (persistenceMode != null) ? persistenceMode : PersistenceMode.IN_MEMORY;
        if (this.persistenceMode != newMode) {
            this.persistenceMode = newMode;
            this.isDirty = true;
        }
    }

    public Map<String, List<String>> getConfiguredToolServices() {
        return configuredToolServices;
    }

    public void setConfiguredToolServices(Map<String, List<String>> configuredToolServices) {
        // Typically for deserialization, consider if this should always mark dirty
        // For now, let's assume if it's different, it's dirty.
        if (this.configuredToolServices == null || !this.configuredToolServices.equals(configuredToolServices)) {
            this.configuredToolServices = configuredToolServices;
            this.isDirty = true; 
        }
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
            this.isDirty = true;
            return;
        }
        List<String> newMethods = new ArrayList<>(methodNames);
        List<String> currentMethods = this.configuredToolServices.getOrDefault(serviceClassName, new ArrayList<>());

        if (!currentMethods.equals(newMethods)) {
            this.configuredToolServices.put(serviceClassName, newMethods);
            this.isDirty = true;
        }
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
        List<String> newMethods = Collections.singletonList("*");
        List<String> currentMethods = this.configuredToolServices.getOrDefault(serviceClassName, new ArrayList<>());
        
        if (!currentMethods.equals(newMethods) || currentMethods.size() != 1) { // check if it's already '*' or needs update
            this.configuredToolServices.put(serviceClassName, newMethods);
            this.isDirty = true;
        }
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
     * Checks if a specific tool service is configured (i.e., has any methods activated).
     *
     * @param serviceClassName The fully qualified class name of the ToolService.
     * @return {@code true} if the service is configured, {@code false} otherwise.
     */
    public boolean isToolServiceConfigured(String serviceClassName) {
        return this.configuredToolServices.containsKey(serviceClassName);
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
}
