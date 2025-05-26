package com.veyon.veyflow.core;

import com.veyon.veyflow.config.WorkflowConfig;
import com.veyon.veyflow.routing.NodeRouter;
import com.veyon.veyflow.state.AgentState;
import com.veyon.veyflow.state.AgentStateRepository;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Represents a compiled and optimized workflow for execution.
 * This class is designed to be immutable and thread-safe once created.
 */
public class CompiledWorkflow {
    private static final Logger log = LoggerFactory.getLogger(CompiledWorkflow.class);

    private final Map<String, AgentNode> nodes; 
    private final Map<String, List<NodeRouter>> routers; 
    private final String entryNode; 
    private final AgentStateRepository agentStateRepository; 
    private final AgentExecutor executor; 

    public CompiledWorkflow(
            String entryNode, 
            Map<String, AgentNode> nodes,
            Map<String, List<NodeRouter>> routers, 
            AgentStateRepository agentStateRepository, 
            AgentExecutor executor 
            ) {
        this.entryNode = entryNode;
        this.nodes = new HashMap<>(nodes); 
        this.routers = new HashMap<>(); 
        if (routers != null) {
            routers.forEach((key, value) -> this.routers.put(key, new ArrayList<>(value)));
        }
        this.agentStateRepository = agentStateRepository;
        this.executor = executor; 

        // The AgentExecutor instance is already configured with nodes and routers by AgentWorkflow.
        // No need to re-register them here. The executor is ready to be used.
    }
    
    public AgentState execute(AgentState state, WorkflowConfig workflowConfig) {
        log.debug("Executing compiled workflow via AgentExecutor, starting from node: {}", entryNode);
        if (state == null) {
            log.error("Initial AgentState cannot be null.");
            throw new IllegalArgumentException("Initial AgentState cannot be null.");
        }
        if (workflowConfig == null) {
            log.error("WorkflowConfig cannot be null.");
            throw new IllegalArgumentException("WorkflowConfig cannot be null.");
        }

        // Delegate execution to the AgentExecutor instance
        // The AgentExecutor is already configured with all nodes, routers, entryNode, and state repository.
        // It also handles persistence logic internally now.
        return executor.execute(state, workflowConfig);
    }

    public String getEntryNode() {
        return entryNode;
    }

    public Map<String, AgentNode> getNodes() {
        return nodes;
    }

    public Map<String, List<NodeRouter>> getRouters() { 
        return routers;
    }

    public AgentStateRepository getAgentStateRepository() {
        return agentStateRepository;
    }

    public AgentExecutor getExecutor() {
        return executor;
    }
}
