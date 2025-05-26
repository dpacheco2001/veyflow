package com.veyon.veyflow.core;

import com.veyon.veyflow.state.AgentState;
import com.veyon.veyflow.config.WorkflowConfig;

/**
 * Base interface for all agent nodes in the custom agent framework.
 * Each node processes state and returns updated state.
 */
public interface AgentNode {
    /**
     * Process the current state and return updated state.
     * 
     * @param state Current agent state
     * @param workflowConfig The workflow configuration.
     * @return Updated agent state after node processing
     */
    AgentState process(AgentState state, WorkflowConfig workflowConfig);
    
    /**
     * Get the name of this node.
     * 
     * @return The node name
     */
    String getName();
}
