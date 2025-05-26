package com.veyon.veyflow.core;

import com.veyon.veyflow.routing.NodeRouter;
import com.veyon.veyflow.routing.LinearRouter;
import com.veyon.veyflow.state.AgentState;
import com.veyon.veyflow.config.CompileConfig; 
import com.veyon.veyflow.config.WorkflowConfig; 
import com.veyon.veyflow.state.AgentStateRepository;
import com.veyon.veyflow.state.InMemoryAgentStateRepository;

import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.HashSet;
import java.util.List;
import java.util.ArrayList;
import java.util.Queue;
import java.util.LinkedList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.Collections;

/**
 * Represents a workflow of agent nodes.
 * This class is used to define and execute a flow of nodes.
 */
public class AgentWorkflow {
    private static final Logger log = LoggerFactory.getLogger(AgentWorkflow.class);
    
    private final Map<String, AgentNode> nodes;
    private final Map<String, List<NodeRouter>> routers; 
    private final String entryNode;
    private final AgentExecutor executor;
    private final AgentStateRepository agentStateRepository;
    
    /**
     * Create a new agent workflow with a specific state repository.
     * 
     * @param entryNode The name of the entry node
     * @param agentStateRepository The repository for agent state
     */
    public AgentWorkflow(String entryNode, AgentStateRepository agentStateRepository) {
        this.nodes = new HashMap<>();
        this.routers = new HashMap<>(); 
        this.entryNode = entryNode;
        this.agentStateRepository = agentStateRepository;
        this.executor = new AgentExecutor(entryNode, this.agentStateRepository);
    }

    /**
     * Create a new agent workflow with a default InMemoryAgentStateRepository.
     * 
     * @param entryNode The name of the entry node
     */
    public AgentWorkflow(String entryNode) {
        this(entryNode, new InMemoryAgentStateRepository());
    }
    
    /**
     * Add a node to the workflow.
     * 
     * @param node The node to add
     * @return This workflow instance for chaining
     */
    public AgentWorkflow addNode(AgentNode node) {
        nodes.put(node.getName(), node);
        executor.registerNode(node);
        return this;
    }
    
    /**
     * Add a router for a node.
     * 
     * @param nodeName The name of the node
     * @param router The router for the node
     * @return This workflow instance for chaining
     */
    public AgentWorkflow addRouter(String nodeName, NodeRouter router) {
        this.routers.computeIfAbsent(nodeName, k -> new ArrayList<>()).add(router);
        executor.registerRouter(nodeName, router);
        return this;
    }
    
    /**
     * Add a linear edge between two nodes.
     * 
     * @param sourceNode The name of the source node
     * @param targetNode The name of the target node
     * @return This workflow instance for chaining
     */
    public AgentWorkflow addEdge(String sourceNode, String targetNode) {
        LinearRouter router = new LinearRouter(sourceNode, targetNode);
        return addRouter(sourceNode, router);
    }
    
    /**
     * Execute the workflow with the given state.
     * 
     * @param state The initial state
     * @param workflowConfig The workflow configuration
     * @return The final state after execution
     */
    public AgentState execute(AgentState state, WorkflowConfig workflowConfig) {
        log.debug("Executing workflow starting from node: {}", entryNode);
        return executor.execute(state, workflowConfig);
    }
    
    /**
     * Compila el workflow para optimizar su ejecución.
     * 
     * @return El workflow compilado
     */
    public CompiledWorkflow compile() {
        return compile(CompileConfig.defaults());
    }
    
    /**
     * Compila el workflow con la configuración especificada.
     * 
     * @param config La configuración de compilación
     * @return El workflow compilado
     */
    public CompiledWorkflow compile(CompileConfig config) {
        log.info("Compiling workflow with entry node: {}", entryNode);
        
        // Verificar que el nodo de entrada existe
        if (!nodes.containsKey(entryNode)) {
            log.error("Entry node '{}' not found in workflow.", entryNode);
            throw new IllegalStateException("Entry node '" + entryNode + "' not found.");
        }

        if (config.shouldValidateGraph()) {
            log.debug("Validating workflow graph...");
            // Detectar dependencias circulares
            if (detectCircularDependencies()) {
                log.error("Circular dependency detected in the workflow.");
                throw new IllegalStateException("Circular dependency detected.");
            }
            log.debug("No circular dependencies detected.");

            // Detectar nodos desconectados
            Set<String> disconnectedNodes = detectDisconnectedNodes();
            if (!disconnectedNodes.isEmpty()) {
                log.warn("Disconnected nodes detected: {}", disconnectedNodes);
                // Dependiendo de la política, esto podría ser un error o una advertencia
                // throw new IllegalStateException("Disconnected nodes detected: " + disconnectedNodes);
            }
            log.debug("No disconnected nodes detected (or handled as warnings).");

            // New N-furcation Join Validation Logic
            log.debug("Validating N-furcation join convergence...");
            for (String potentialForkNodeName : this.routers.keySet()) {
                List<NodeRouter> outgoingRoutersFromFork = this.routers.get(potentialForkNodeName);

                if (outgoingRoutersFromFork == null || outgoingRoutersFromFork.size() <= 1) {
                    continue; // Not an N-furcation from this node
                }

                List<String> parallelBranchNodes = new ArrayList<>();
                boolean forkIsAllLinear = true;
                for (NodeRouter router : outgoingRoutersFromFork) {
                    if (router instanceof LinearRouter) {
                        String targetNode = ((LinearRouter) router).getTargetNode();
                        if (!parallelBranchNodes.contains(targetNode)) { // Add only distinct branch nodes
                            parallelBranchNodes.add(targetNode);
                        }
                    } else {
                        forkIsAllLinear = false;
                        break;
                    }
                }

                if (!forkIsAllLinear) {
                    log.warn("N-furcation from '{}' involves non-LinearRouters. Strict join validation skipped for this fork. Runtime behavior will determine flow.", potentialForkNodeName);
                    continue;
                }

                if (parallelBranchNodes.size() <= 1) {
                    // This means all linear routers from the fork point to the same immediate node, or it's not a true multi-branch fork.
                    // This is valid by default under the new interpretation, as there aren't multiple distinct branches to check for convergence.
                    log.debug("N-furcation from '{}' either leads to a single immediate node or is not a multi-branch fork. Skipping convergence check.", potentialForkNodeName);
                    continue;
                }

                // Now we have distinct parallelBranchNodes (e.g., A, B) from a purely linear fork.
                // Check if they all converge to a single common join node via a single LinearRouter each.
                log.debug("Checking convergence for parallel branches {} from fork node '{}'", parallelBranchNodes, potentialForkNodeName);
                String commonJoinNodeTarget = null;
                boolean firstBranchTargetSet = false;

                for (String branchNodeName : parallelBranchNodes) {
                    List<NodeRouter> routersFromBranch = this.routers.get(branchNodeName);
                    if (routersFromBranch == null || routersFromBranch.isEmpty()) {
                        String errorMsg = String.format("Compilation Error: Parallel branch node '%s' (from fork '%s') has no outgoing routers and cannot converge.", branchNodeName, potentialForkNodeName);
                        log.error(errorMsg);
                        throw new IllegalStateException(errorMsg);
                    }
                    if (routersFromBranch.size() > 1) {
                        String errorMsg = String.format("Compilation Error: Parallel branch node '%s' (from fork '%s') has multiple outgoing routers, making unambiguous join target unclear for this validation.", branchNodeName, potentialForkNodeName);
                        log.error(errorMsg);
                        throw new IllegalStateException(errorMsg);
                    }

                    NodeRouter singleRouterFromBranch = routersFromBranch.get(0);
                    if (!(singleRouterFromBranch instanceof LinearRouter)) {
                        log.warn("Parallel branch node '{}' (from fork '{}') uses a non-LinearRouter. Strict join validation to a single common node is skipped for this path. Runtime behavior will determine flow.", branchNodeName, potentialForkNodeName);
                        // If one branch is conditional, we can't enforce a common linear join for all others based on this strict rule.
                        // We could decide to bail out for the whole fork, or allow this specific branch to be conditional.
                        // For now, let's be strict: if we are in this validation path (fork was all linear), then branches must also be linear to a common join.
                        String errorMsg = String.format("Compilation Error: Parallel branch node '%s' (from fork '%s') must use a LinearRouter to converge, but found %s.", branchNodeName, potentialForkNodeName, singleRouterFromBranch.getClass().getSimpleName());
                        log.error(errorMsg);
                        throw new IllegalStateException(errorMsg);
                    }

                    String currentBranchJoinTarget = ((LinearRouter) singleRouterFromBranch).getTargetNode();
                    if (!firstBranchTargetSet) {
                        commonJoinNodeTarget = currentBranchJoinTarget;
                        firstBranchTargetSet = true;
                    } else {
                        if (commonJoinNodeTarget == null || !commonJoinNodeTarget.equals(currentBranchJoinTarget)) {
                            String errorMsg = String.format("Compilation Error: Parallel branches from fork '%s' do not converge to the same LinearRouter target. Expected join at '%s', but branch '%s' targets '%s'.", potentialForkNodeName, commonJoinNodeTarget, branchNodeName, currentBranchJoinTarget);
                            log.error(errorMsg);
                            throw new IllegalStateException(errorMsg);
                        }
                    }
                }

                if (firstBranchTargetSet) { // Implies all branches checked and converged
                    log.debug("N-furcation from '{}' with branches {} correctly converges to common join node '{}' via LinearRouters.", potentialForkNodeName, parallelBranchNodes, commonJoinNodeTarget);
                } else {
                    // This case should ideally not be reached if parallelBranchNodes.size() > 1, as an error would have been thrown.
                    log.warn("N-furcation from '{}': Could not determine a common join node for branches {}. This might indicate an issue or an unhandled validation case.", potentialForkNodeName, parallelBranchNodes);
                }
            }
            log.debug("N-furcation join validation complete.");
        }

        // Create CompiledWorkflow with the map of lists of routers
        return new CompiledWorkflow(entryNode, nodes, routers, agentStateRepository, executor);
    }
    
    /**
     * Detecta dependencias circulares en el workflow.
     * 
     * @return true si hay dependencias circulares, false en caso contrario
     */
    private boolean detectCircularDependencies() {
        Set<String> visited = new HashSet<>();
        Set<String> recursionStack = new HashSet<>();
        
        for (String nodeName : nodes.keySet()) {
            if (detectCircularDependenciesUtil(nodeName, visited, recursionStack)) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Función auxiliar para detectar dependencias circulares.
     * 
     * @param nodeName El nombre del nodo actual
     * @param visited Conjunto de nodos visitados
     * @param recursionStack Pila de recursión actual
     * @return true si hay un ciclo, false en caso contrario
     */
    private boolean detectCircularDependenciesUtil(String nodeName, Set<String> visited, Set<String> recursionStack) {
        visited.add(nodeName);
        recursionStack.add(nodeName);
        
        // Get all routers for the current node
        List<NodeRouter> currentRouters = routers.getOrDefault(nodeName, Collections.emptyList());
        
        for (NodeRouter router : currentRouters) {
            // Get possible next nodes for this specific router
            List<String> possibleNextNodes = getPossibleNextNodesForRouter(router); 
            
            for (String nextNode : possibleNextNodes) {
                if (nodes.containsKey(nextNode)) { // Ensure nextNode is a defined node
                    if (!visited.contains(nextNode)) {
                        if (detectCircularDependenciesUtil(nextNode, visited, recursionStack)) {
                            return true;
                        }
                    } else if (recursionStack.contains(nextNode)) {
                        log.warn("Circular dependency detected: {} -> {}", nodeName, nextNode);
                        return true;
                    }
                }
            }
        }
        
        // Quitar el nodo de la pila de recursión
        recursionStack.remove(nodeName);
        return false;
    }
    
    /**
     * Detecta nodos desconectados (no alcanzables desde el nodo de entrada).
     * 
     * @return Conjunto de nombres de nodos desconectados
     */
    private Set<String> detectDisconnectedNodes() {
        Set<String> visited = new HashSet<>();
        Queue<String> queue = new LinkedList<>();
        
        // Comenzar BFS desde el nodo de entrada
        queue.add(entryNode);
        visited.add(entryNode);
        
        while (!queue.isEmpty()) {
            String nodeName = queue.poll();
            
            // Obtener los enrutadores para este nodo
            List<NodeRouter> currentRouters = routers.getOrDefault(nodeName, Collections.emptyList());
            
            for (NodeRouter router : currentRouters) {
                // Obtener posibles nodos siguientes para este enrutador específico
                List<String> possibleNextNodes = getPossibleNextNodesForRouter(router);
                
                for (String nextNode : possibleNextNodes) {
                    if (!visited.contains(nextNode) && nodes.containsKey(nextNode)) {
                        visited.add(nextNode);
                        queue.add(nextNode);
                    }
                }
            }
        }
        
        // Los nodos desconectados son aquellos que no fueron visitados
        Set<String> disconnected = new HashSet<>(nodes.keySet());
        disconnected.removeAll(visited);
        return disconnected;
    }
    
    /**
     * Obtiene los posibles nodos siguientes para un enrutador específico.
     * Este es un método simplificado, ya que algunos enrutadores pueden tener
     * comportamiento dinámico basado en el estado.
     * 
     * @param router El enrutador
     * @return Lista de posibles nodos siguientes
     */
    // Renamed for clarity, was getPossibleNextNodes
    private List<String> getPossibleNextNodesForRouter(NodeRouter router) { 
        // Esta es una implementación simplificada
        // Para enrutadores complejos, necesitaríamos una interfaz adicional
        if (router instanceof LinearRouter) {
            LinearRouter linearRouter = (LinearRouter) router;
            // Ensure target node is not null or empty before adding
            if (linearRouter.getTargetNode() != null && !linearRouter.getTargetNode().isEmpty()) {
                 return List.of(linearRouter.getTargetNode());
            }
        } else if (router instanceof com.veyon.veyflow.routing.ConditionalRouter) {
            // For ConditionalRouter, we cannot know the next nodes without state.
            // For graph validation purposes (cycles, disconnected), we might need
            // to know all *potential* target names if the router can provide them.
            // If ConditionalRouter had a method like `getAllPotentialTargetNodes()` it would be useful here.
            // For now, we return an empty list, meaning validation might miss some paths for conditional routers.
            // This is a limitation of static analysis for dynamic routing.
            log.trace("Cannot statically determine next nodes for ConditionalRouter during validation/optimization.");
        }
        
        // Para otros tipos de enrutadores o si no se pueden determinar estáticamente
        return Collections.emptyList();
    }
    
    /**
     * Get a node by name.
     * 
     * @param nodeName The name of the node
     * @return The node, or null if not found
     */
    public AgentNode getNode(String nodeName) {
        return nodes.get(nodeName);
    }
    
    /**
     * Get a router by node name.
     * 
     * @param nodeName The name of the node
     * @return The router, or null if not found
     */
    // Changed to return List<NodeRouter> and renamed
    public List<NodeRouter> getRouters(String nodeName) { 
        return routers.get(nodeName);
    }
    
    /**
     * Get the entry node name.
     * 
     * @return The entry node name
     */
    public String getEntryNode() {
        return entryNode;
    }
    
    /**
     * Shutdown the workflow executor.
     */
    public void shutdown() {
        executor.shutdown();
    }
}
