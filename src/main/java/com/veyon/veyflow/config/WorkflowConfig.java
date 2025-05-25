package com.veyon.veyflow.config;

import java.util.List;
import java.util.ArrayList;

/**
 * Configuration for a workflow execution, including tenant-specific settings
 * such as enabled tool services.
 */
public class WorkflowConfig {

    private List<String> enabledToolServiceClassNames;

    /**
     * Default constructor initializing an empty list of enabled tool service class names.
     */
    public WorkflowConfig() {
        this.enabledToolServiceClassNames = new ArrayList<>();
    }

    /**
     * Constructor to initialize with a specific list of enabled tool service class names.
     * @param enabledToolServiceClassNames A list of fully qualified class names of ToolServices that are enabled.
     */
    public WorkflowConfig(List<String> enabledToolServiceClassNames) {
        this.enabledToolServiceClassNames = enabledToolServiceClassNames != null ? new ArrayList<>(enabledToolServiceClassNames) : new ArrayList<>();
    }

    /**
     * Gets the list of enabled tool service class names.
     * These are the fully qualified class names of ToolService implementations
     * that are permitted for use during the workflow execution for a specific tenant.
     *
     * @return A list of enabled tool service class names. Returns an empty list if none are set.
     */
    public List<String> getEnabledToolServiceClassNames() {
        return enabledToolServiceClassNames;
    }

    /**
     * Sets the list of enabled tool service class names.
     *
     * @param enabledToolServiceClassNames A list of fully qualified class names of ToolServices to be enabled.
     */
    public void setEnabledToolServiceClassNames(List<String> enabledToolServiceClassNames) {
        this.enabledToolServiceClassNames = enabledToolServiceClassNames != null ? new ArrayList<>(enabledToolServiceClassNames) : new ArrayList<>();
    }

    // In the future, this class can be expanded to include other
    // workflow-level or tenant-specific configurations.
    // For example:
    // private String tenantId;
    // private Map<String, Object> otherTenantConfigs;
}
