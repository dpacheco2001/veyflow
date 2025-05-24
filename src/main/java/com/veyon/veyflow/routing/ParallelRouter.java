package com.veyon.veyflow.routing;

import com.veyon.veyflow.state.AgentState;
import java.util.List;
import java.util.ArrayList;

/**
 * A router that routes to multiple target nodes in parallel.
 */
public class ParallelRouter implements NodeRouter {
    private final List<String> targetNodes;
    
    /**
     * Create a new parallel router.
     */
    public ParallelRouter() {
        this.targetNodes = new ArrayList<>();
    }
    
    /**
     * Add a target node to route to in parallel.
     * 
     * @param targetNode The target node name
     * @return This router instance for chaining
     */
    public ParallelRouter addTarget(String targetNode) {
        targetNodes.add(targetNode);
        return this;
    }
    
    @Override
    public String route(AgentState state) {
        // En la implementación actual, no podemos devolver múltiples nodos
        // Dado que la interfaz NodeRouter ha cambiado para simplificar el framework
        // Devolvemos el primer nodo destino si existe, o null si no hay destinos
        if (targetNodes.isEmpty()) {
            return null;
        }
        
        // Nota: Esta implementación solo utiliza el primer target
        // Para una verdadera ejecución en paralelo, se necesitaría un enfoque diferente
        return targetNodes.get(0);
    }
    
    /**
     * Obtiene todos los nodos destino para procesamiento en paralelo.
     * Este método puede ser utilizado por implementaciones que soporten paralelismo.
     * 
     * @return Lista de nombres de nodos destino
     */
    public List<String> getAllTargetNodes() {
        return new ArrayList<>(targetNodes);
    }
}
