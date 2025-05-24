package com.veyon.veyflow.routing;

import com.veyon.veyflow.state.AgentState;
import java.util.Map;
import java.util.HashMap;
import java.util.function.Predicate;

/**
 * A router that routes to different target nodes based on conditions.
 */
public class ConditionalRouter implements NodeRouter {
    private final Map<Predicate<AgentState>, String> conditions;
    private final String defaultTarget;
    
    /**
     * Create a new conditional router.
     * 
     * @param defaultTarget The default target node if no conditions match
     */
    public ConditionalRouter(String defaultTarget) {
        this.conditions = new HashMap<>();
        this.defaultTarget = defaultTarget;
    }
    
    /**
     * Add a condition for routing.
     * 
     * @param condition The condition predicate
     * @param targetNode The target node if the condition is true
     * @return This router instance for chaining
     */
    public ConditionalRouter addCondition(Predicate<AgentState> condition, String targetNode) {
        conditions.put(condition, targetNode);
        return this;
    }
    
    @Override
    public String route(AgentState state) {
        // Check each condition and route accordingly
        for (Map.Entry<Predicate<AgentState>, String> entry : conditions.entrySet()) {
            if (entry.getKey().test(state)) {
                return entry.getValue(); // Return target node on first match
            }
        }
        
        // Use default target if no conditions match
        return defaultTarget;
    }
}
