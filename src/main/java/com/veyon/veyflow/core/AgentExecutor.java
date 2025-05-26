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
import java.util.Set;
import java.util.HashSet;
import java.util.Collections;

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
        if (state.getCurrentNode() == null || state.getCurrentNode().isEmpty()) {
             log.debug("execute: Initializing currentNode to entryNode: {}", entryNode);
             state.setCurrentNode(entryNode);
        }
        
        int loopCount = 0; // Para detectar bucles excesivos
        while (state.getCurrentNode() != null && !state.getCurrentNode().isEmpty()) {
            loopCount++;
            if (loopCount > 100) { // Un umbral arbitrario, ajustar si es necesario
                log.error("execute: Excessive loop count ({}) for state Tenant: {}, Thread: {}, currentNode: {}. Aborting.", loopCount, state.getTenantId(), state.getThreadId(), state.getCurrentNode());
                throw new RuntimeException("Excessive loop count detected in AgentExecutor.execute for node " + state.getCurrentNode());
            }

            log.debug("execute: Loop iteration {}. CurrentNode BEFORE processing: {}", loopCount, state.getCurrentNode());
            String currentNodeName = state.getCurrentNode();
            AgentNode currentNode = nodes.get(currentNodeName);
            
            if (currentNode == null) {
                log.error("Node not found: {}. Terminating loop.", currentNodeName);
                state.setCurrentNode(null); // Ensure termination
                break;
            }
            
            log.debug("Executing node: {}", currentNodeName);
            state = currentNode.process(state, workflowConfig); 
            log.debug("execute: CurrentNode AFTER process, BEFORE routing: {} (Node {} processed state for Tenant: {}, Thread: {})", state.getCurrentNode(), currentNodeName, state.getTenantId(), state.getThreadId());

            List<NodeRouter> currentRouters = routers.get(currentNodeName);
            
            if (currentRouters == null || currentRouters.isEmpty()) {
                log.debug("Terminal node reached or no routers defined for: {}. Setting currentNode to null.", currentNodeName);
                state.setCurrentNode(null); 
            } else {
                List<String> nextNodeNames = new ArrayList<>();
                for (NodeRouter router : currentRouters) {
                    // Corregido: Usar router.route() que devuelve un solo String o null
                    String nextNodeName = router.route(state, workflowConfig);
                    if (nextNodeName != null && !nextNodeName.isEmpty()) {
                        nextNodeNames.add(nextNodeName);
                    }
                }
                
                if (nextNodeNames.isEmpty()) {
                    log.debug("No next nodes determined by routers for: {}. Setting currentNode to null.", currentNodeName);
                    state.setCurrentNode(null); 
                } else if (nextNodeNames.size() == 1) {
                    log.debug("Linear transition from {} to {}.", currentNodeName, nextNodeNames.get(0));
                    state.setCurrentNode(nextNodeNames.get(0));
                } else { 
                    // Fork: Execute multiple nodes in parallel
                    log.info("Node {} is forking to: {}. Current state tenant: {}, thread: {}", currentNode.getName(), nextNodeNames, state.getTenantId(), state.getThreadId());
                    String[] parallelExecutionTargets = nextNodeNames.toArray(new String[0]);
                    executeParallel(state, workflowConfig, parallelExecutionTargets);
                    log.info("Parallel execution of nodes {} completed and states merged. Determining join node. State tenant: {}, thread: {}", String.join(", ", parallelExecutionTargets), state.getTenantId(), state.getThreadId());

                    // Determine the common successor (join node) of the parallel branches
                    Set<String> commonSuccessors = null;
                    boolean allBranchesHaveRoutableSuccessors = true;

                    for (String parallelNodeName : parallelExecutionTargets) { 
                        List<NodeRouter> branchRoutersList = routers.get(parallelNodeName);
                        if (branchRoutersList != null && !branchRoutersList.isEmpty()) {
                            Set<String> allSuccessorsForThisBranchNode = new HashSet<>();
                            for (NodeRouter routerInstance : branchRoutersList) {
                                Object rawRouteResult = routerInstance.route(state, workflowConfig); // 'state' is merged
                                List<String> successorsFromOneRouter = null;
                                if (rawRouteResult instanceof String) {
                                    successorsFromOneRouter = Collections.singletonList((String) rawRouteResult);
                                } else if (rawRouteResult instanceof List) {
                                    // We need to ensure it's List<String>, not List of something else.
                                    // This cast might still be risky if the list contains non-Strings, but aligns with expectation.
                                    @SuppressWarnings("unchecked")
                                    List<String> tempList = (List<String>) rawRouteResult;
                                    successorsFromOneRouter = tempList;
                                } else if (rawRouteResult == null) {
                                    successorsFromOneRouter = Collections.emptyList();
                                }

                                if (successorsFromOneRouter != null && !successorsFromOneRouter.isEmpty()) { 
                                    allSuccessorsForThisBranchNode.addAll(successorsFromOneRouter);
                                }
                            }

                            if (!allSuccessorsForThisBranchNode.isEmpty()) {
                                if (commonSuccessors == null) {
                                    commonSuccessors = new HashSet<>(allSuccessorsForThisBranchNode);
                                } else {
                                    commonSuccessors.retainAll(allSuccessorsForThisBranchNode);
                                }
                            } else {
                                // This branch has no successors according to all its routers
                                log.debug("Branch {} has no successors from any of its routers after parallel execution.", parallelNodeName);
                                allBranchesHaveRoutableSuccessors = false;
                                break; 
                            }
                        } else {
                             // No routers defined for this parallel node, means it's an end node for its branch.
                             log.debug("Branch {} has no routers defined after parallel execution.", parallelNodeName);
                             allBranchesHaveRoutableSuccessors = false;
                             break;
                        }
                    }

                    if (!allBranchesHaveRoutableSuccessors || commonSuccessors == null || commonSuccessors.isEmpty()) {
                        log.info("Parallel branches completed, but no single common successor found or a branch ended. Ending workflow. State tenant: {}, thread: {}", state.getTenantId(), state.getThreadId());
                        state.setCurrentNode(null); // End of workflow
                    } else if (commonSuccessors.size() == 1) {
                        String joinNode = commonSuccessors.iterator().next();
                        log.info("Parallel branches joined. Next node is: {}. State tenant: {}, thread: {}", joinNode, state.getTenantId(), state.getThreadId());
                        state.setCurrentNode(joinNode);
                        // The while loop will continue with joinNode as currentNode
                    } else { // commonSuccessors.size() > 1
                        log.warn("Parallel branches joined, but multiple common successors found: {}. This scenario is not handled yet. Ending workflow. State tenant: {}, thread: {}", commonSuccessors, state.getTenantId(), state.getThreadId());
                        state.setCurrentNode(null); // End of workflow
                    }
                }
            }
            log.debug("execute: End of loop iteration {}. CurrentNode for next iteration check: {} (State Tenant: {}, Thread: {})", loopCount, state.getCurrentNode(), state.getTenantId(), state.getThreadId());
        } 
        log.debug("execute: Exited while loop. Final currentNode: {} (State Tenant: {}, Thread: {})", state.getCurrentNode(), state.getTenantId(), state.getThreadId());
        
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
        log.debug("Executing {} nodes in parallel: {}", targetNodes.length, String.join(", ", targetNodes));
        
        List<CompletableFuture<AgentState>> futures = new ArrayList<>();
        
        for (String targetNodeName : targetNodes) {
            CompletableFuture<AgentState> future = CompletableFuture.supplyAsync(() -> {
                AgentState branchState = AgentState.fromJson(state.toJson()); // Copia del estado *antes* de la ejecución de esta rama
                // No establecemos currentNode aquí, ya que solo vamos a procesar el targetNodeName específico

                AgentNode targetNode = nodes.get(targetNodeName);
                if (targetNode == null) {
                    log.error("executeParallel: Node not found for parallel execution: {}. Returning original branch state.", targetNodeName);
                    return branchState; // o lanzar excepción
                }
                
                log.debug("executeParallel: Processing node {} in a new branch. Initial branch state tenant: {}, thread: {}", targetNodeName, branchState.getTenantId(), branchState.getThreadId());
                // Solo procesa este nodo específico, no un sub-workflow.
                AgentState processedBranchState = targetNode.process(branchState, workflowConfig);
                log.debug("executeParallel: Node {} processed in branch. Final branch state tenant: {}, thread: {}", targetNodeName, processedBranchState.getTenantId(), processedBranchState.getThreadId());
                return processedBranchState; 
            }, executorService);
            
            futures.add(future);
        }
        
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        
        log.debug("executeParallel: All parallel branches completed. Merging results into state for tenant: {}, thread: {}", state.getTenantId(), state.getThreadId());
        for (CompletableFuture<AgentState> future : futures) {
            try {
                AgentState branchState = future.get(); 
                if (branchState != null) {
                    // Merge values
                    if (branchState.getKeys() != null && !branchState.getKeys().isEmpty()) {
                        for (String key : branchState.getKeys()) {
                            Object value = branchState.get(key);
                            if (value != null) {
                                if (key.equals("execution_path") && value instanceof List) {
                                    @SuppressWarnings("unchecked")
                                    List<String> mainPath = (List<String>) state.get(key);
                                    if (mainPath == null) { 
                                        mainPath = new ArrayList<>();
                                        state.set(key, mainPath);
                                    }
                                    @SuppressWarnings("unchecked")
                                    List<String> branchPath = (List<String>) value;
                                    for (String pathItem : branchPath) {
                                        if (!mainPath.contains(pathItem)) { 
                                            mainPath.add(pathItem);
                                        }
                                    }
                                } else {
                                    state.set(key, value);
                                }
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
