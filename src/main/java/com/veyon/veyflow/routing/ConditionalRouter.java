package com.veyon.veyflow.routing;

import com.veyon.veyflow.state.AgentState;
import com.veyon.veyflow.config.WorkflowConfig;
import java.util.function.BiFunction;

/**
 * A router that determines the next node by executing a provided function 
 * with the current agent state and workflow config.
 */
public class ConditionalRouter implements NodeRouter {
    private final BiFunction<AgentState, WorkflowConfig, String> conditionFunction;
    
    /**
     * Create a new conditional router that uses a function to determine the next node.
     * 
     * @param conditionFunction A function that takes the AgentState and WorkflowConfig, 
     *                          and returns the name of the next node, or null if routing should stop.
     * @throws IllegalArgumentException if conditionFunction is null.
     */
    public ConditionalRouter(BiFunction<AgentState, WorkflowConfig, String> conditionFunction) {
        if (conditionFunction == null) {
            throw new IllegalArgumentException("Condition function cannot be null.");
        }
        this.conditionFunction = conditionFunction;
    }
    
    /**
     * Routes from the current node based on the provided condition function.
     * 
     * @param state The current agent state.
     * @param workflowConfig The current workflow configuration.
     * @return The name of the next node as determined by the condition function, 
     *         or null if the function returns null or an empty string.
     */
    @Override
    public String route(AgentState state, WorkflowConfig workflowConfig) {
        String nextNode = this.conditionFunction.apply(state, workflowConfig);
        // AgentExecutor already handles null or empty string as a terminal signal for a path
        return nextNode; 
    }
}
