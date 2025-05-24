package com.veyon.veyflow.core;
import com.veyon.veyflow.state.AgentState;
import com.veyon.veyflow.routing.NodeRouter;

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
    private final Map<String, NodeRouter> routers;
    private final String entryNode;
    private final ExecutorService executorService;
    
    /**
     * Create a new agent executor.
     * 
     * @param entryNode The name of the entry node
     */
    public AgentExecutor(String entryNode) {
        this.nodes = new HashMap<>();
        this.routers = new HashMap<>();
        this.entryNode = entryNode;
        this.executorService = Executors.newCachedThreadPool();
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
        routers.put(nodeName, router);
        return this;
    }
    
    /**
     * Execute the agent with the given state.
     * 
     * @param state The initial state
     * @return The final state after execution
     */
    public AgentState execute(AgentState state) {
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
            state = currentNode.process(state);
            
            // Route to the next node
            NodeRouter router = routers.get(currentNodeName);
            if (router == null) {
                // No router means this is a terminal node
                log.debug("Terminal node reached: {}", currentNodeName);
                break;
            }
            
            // Determine the next node
            String nextNodeName = router.route(state);
            
            if (nextNodeName == null || nextNodeName.isEmpty()) {
                // No next node, so we're done
                log.debug("No next node from node: {}", currentNodeName);
                break;
            } else {
                // Update the current node
                log.debug("Routing from {} to {}", currentNodeName, nextNodeName);
                state.setCurrentNode(nextNodeName);
            }
        }
        
        return state;
    }
    
    /**
     * Execute nodes in parallel.
     * 
     * @param state The current state
     * @param targetNodes Array of target node names
     */
    private void executeParallel(AgentState state, String... targetNodes) {
        log.debug("Executing {} nodes in parallel", targetNodes.length);
        
        List<CompletableFuture<AgentState>> futures = new ArrayList<>();
        
        for (String targetNode : targetNodes) {
            CompletableFuture<AgentState> future = CompletableFuture.supplyAsync(() -> {
                // Create a copy of the state for this branch
                AgentState branchState = AgentState.fromJson(state.toJson());
                branchState.setCurrentNode(targetNode);
                
                // Execute this branch
                return execute(branchState);
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
                // This is a simple implementation; you might want to customize this
                for (String key : branchState.getKeys()) {
                    state.set(key, branchState.get(key));
                }
            } catch (Exception e) {
                log.error("Error executing parallel branch", e);
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
