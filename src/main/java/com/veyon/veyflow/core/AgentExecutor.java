package com.veyon.veyflow.core;

import com.veyon.veyflow.state.AgentState;
import com.veyon.veyflow.state.AgentStateRepository;
import com.veyon.veyflow.state.InMemoryAgentStateRepository;
import com.veyon.veyflow.state.PersistenceMode;
import com.veyon.veyflow.state.ChatMessage;
import com.veyon.veyflow.routing.NodeRouter;
import com.veyon.veyflow.config.WorkflowConfig;

import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Main executor for the agent framework.
 * This class manages the flow between nodes and handles execution.
 */
public class AgentExecutor {
    private static final Logger log = LoggerFactory.getLogger(AgentExecutor.class);
    
    private final Map<String, AgentNode> nodes;
    private final Map<String, List<NodeRouter>> routers;
    private final String entryNode;
    private final ExecutorService executorService;
    private final AgentStateRepository agentStateRepository;
    
    /**
     * Create a new agent executor with a specific state repository.
     * 
     * @param entryNode The name of the entry node
     * @param agentStateRepository The repository for saving/loading agent state
     */
    public AgentExecutor(String entryNode, AgentStateRepository agentStateRepository) {
        this.nodes = new HashMap<>();
        this.routers = new HashMap<>();
        this.entryNode = entryNode;
        this.executorService = Executors.newCachedThreadPool();
        this.agentStateRepository = agentStateRepository;
    }

    /**
     * Create a new agent executor with a default InMemoryAgentStateRepository.
     * 
     * @param entryNode The name of the entry node
     */
    public AgentExecutor(String entryNode) {
        this(entryNode, new InMemoryAgentStateRepository());
    }
    
    /**
     * Register a node with the executor.
     * 
     * @param node The node to register
     * @return This executor instance for chaining
     */
    public AgentExecutor registerNode(AgentNode node) {
        nodes.put(node.getName(), node);
        return this;
    }
    
    /**
     * Register a router for a node.
     * 
     * @param nodeName The name of the node
     * @param router The router for the node
     * @return This executor instance for chaining
     */
    public AgentExecutor registerRouter(String nodeName, NodeRouter router) {
        this.routers.computeIfAbsent(nodeName, k -> new ArrayList<>()).add(router);
        return this;
    }
    
    /**
     * Execute the agent with the given state.
     * 
     * @param state The initial state
     * @param workflowConfig The workflow configuration
     * @return The final state after execution
     */
    public AgentState execute(AgentState state, WorkflowConfig workflowConfig) {
        // Set the initial node
        state.setCurrentNode(entryNode);
        
        // Execute nodes until we reach a node with no outgoing edges
        while (state.getCurrentNode() != null && !state.getCurrentNode().isEmpty()) {
            String currentNodeName = state.getCurrentNode();
            AgentNode currentNode = nodes.get(currentNodeName);
            
            if (currentNode == null) {
                log.error("Node not found: {}", currentNodeName);
                break;
            }
            
            log.debug("Executing node: {}", currentNodeName);
            
            // Process the current node
            state = currentNode.process(state, workflowConfig);
            
            // Get all routers for the current node
            List<NodeRouter> currentRouters = routers.get(currentNodeName);
            
            if (currentRouters == null || currentRouters.isEmpty()) {
                // No routers means this is a terminal node for this path
                log.debug("Terminal node reached or no routers defined for: {}", currentNodeName);
                break;
            }
            
            List<String> nextNodeNames = new ArrayList<>();
            for (NodeRouter router : currentRouters) {
                String nextNodeName = router.route(state, workflowConfig);
                if (nextNodeName != null && !nextNodeName.isEmpty()) {
                    nextNodeNames.add(nextNodeName);
                }
            }
            
            if (nextNodeNames.isEmpty()) {
                // No next node from any router, so we're done with this path
                log.debug("No valid next node from any router for node: {}", currentNodeName);
                break;
            } else if (nextNodeNames.size() == 1) {
                // Single next node, continue linearly
                String nextNodeName = nextNodeNames.get(0);
                log.debug("Routing from {} to {} (linear)", currentNodeName, nextNodeName);
                state.setCurrentNode(nextNodeName);
            } else {
                // Multiple next nodes, execute in parallel
                log.info("Forking parallel execution from {} to nodes: {}", currentNodeName, nextNodeNames);
                executeParallel(state, workflowConfig, nextNodeNames.toArray(new String[0]));
                break; // Stop current linear execution as it has forked
            }
        }
        
        // After the loop, workflow execution is complete or has been interrupted.
        // Save the state if it's configured for REDIS persistence and a repository is available.
        if (this.agentStateRepository != null && state.getPersistenceMode() == PersistenceMode.REDIS) {
            log.info("Workflow finished for tenant '{}', thread '{}'. Saving state to repository.", state.getTenantId(), state.getThreadId());
            try {
                this.agentStateRepository.save(state);
            } catch (Exception e) {
                log.error("Failed to save state for tenant '{}', thread '{}' to repository after workflow completion.", 
                          state.getTenantId(), state.getThreadId(), e);
            }
        } else if (this.agentStateRepository == null && state.getPersistenceMode() == PersistenceMode.REDIS) {
            log.warn("AgentState persistenceMode is REDIS, but no AgentStateRepository is configured in AgentExecutor for tenant '{}', thread '{}'. State not saved.",
                     state.getTenantId(), state.getThreadId());
        }

        return state;
    }
    
    /**
     * Execute nodes in parallel.
     * 
     * @param state The current state
     * @param workflowConfig The workflow configuration
     * @param targetNodes Array of target node names
     */
    private void executeParallel(AgentState state, WorkflowConfig workflowConfig, String... targetNodes) {
        log.debug("Executing {} nodes in parallel", targetNodes.length);
        
        List<CompletableFuture<AgentState>> futures = new ArrayList<>();
        
        for (String targetNode : targetNodes) {
            CompletableFuture<AgentState> future = CompletableFuture.supplyAsync(() -> {
                // Create a copy of the state for this branch
                AgentState branchState = AgentState.fromJson(state.toJson());
                branchState.setCurrentNode(targetNode);
                
                // Execute this branch using a new AgentExecutor instance or ensure execute is re-entrant and thread-safe
                // For simplicity, let's assume execute can be called, but be mindful of shared state if not designed for it.
                // If AgentExecutor itself has shared mutable state beyond the nodes/routers maps (which are read-only during execute),
                // you might need a new executor instance per branch or a different approach.
                // For now, we'll call execute on the same instance. This implies that the 'nodes' and 'routers' maps are
                // populated before any parallel execution and are not modified during it.
                // The agentStateRepository will be shared, which is generally fine for Redis/DB backed ones.
                return execute(branchState, workflowConfig); 
            }, executorService);
            
            futures.add(future);
        }
        
        // Wait for all branches to complete
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        
        // Merge the results
        for (CompletableFuture<AgentState> future : futures) {
            try {
                AgentState branchState = future.get();
                // Merge branch state with the main state
                if (branchState != null) {
                    // Merge values
                    if (branchState.getKeys() != null && !branchState.getKeys().isEmpty()) {
                        for (String key : branchState.getKeys()) {
                            Object value = branchState.get(key);
                            if (value != null) {
                                state.set(key, value);
                            }
                        }
                    }
                    // Merge chat messages (simple append)
                    if (branchState.getChatMessages() != null) {
                        for (ChatMessage msg : branchState.getChatMessages()) {
                            state.addChatMessage(msg);
                        }
                    }
                    // Note: Merging currentNode, previousNode, threadId, tenantId might not always make sense
                    // or require specific logic if branches can diverge significantly and then try to merge back
                    // into a single conceptual state for these properties. For now, we only merge 'values' and 'chatMessages'.
                }
            } catch (Exception e) {
                log.error("Error merging parallel branch execution result", e);
            }
        }
    }
    
    /**
     * Shutdown the executor.
     */
    public void shutdown() {
        executorService.shutdown();
    }
}
