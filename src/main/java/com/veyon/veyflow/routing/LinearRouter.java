package com.veyon.veyflow.routing;

import com.veyon.veyflow.state.AgentState;
import com.veyon.veyflow.config.WorkflowConfig;

/**
 * Enrutador lineal que siempre redirige al nodo de destino especificado.
 * Esta es la implementación más simple de un enrutador.
 */
public class LinearRouter implements NodeRouter {
    private final String sourceNode;
    private final String targetNode;
    
    /**
     * Crea un nuevo enrutador lineal.
     * 
     * @param sourceNode El nombre del nodo de origen
     * @param targetNode El nombre del nodo de destino
     */
    public LinearRouter(String sourceNode, String targetNode) {
        this.sourceNode = sourceNode;
        this.targetNode = targetNode;
    }
    
    @Override
    public String route(AgentState state, WorkflowConfig workflowConfig) {
        // workflowConfig is not used in this simple router, which is fine.
        return targetNode;
    }
    
    /**
     * Obtiene el nombre del nodo de origen.
     * 
     * @return El nombre del nodo de origen
     */
    public String getSourceNode() {
        return sourceNode;
    }
    
    /**
     * Obtiene el nombre del nodo de destino.
     * 
     * @return El nombre del nodo de destino
     */
    public String getTargetNode() {
        return targetNode;
    }
}
