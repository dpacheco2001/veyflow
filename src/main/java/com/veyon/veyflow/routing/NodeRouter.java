package com.veyon.veyflow.routing;

import com.veyon.veyflow.state.AgentState;

/**
 * Interfaz para enrutadores de nodos en el framework de agentes.
 * Un enrutador determina a qué nodo siguiente dirigirse basado en el estado actual.
 */
public interface NodeRouter {
    /**
     * Enruta desde el nodo actual basado en el estado.
     * 
     * @param state El estado actual
     * @return El nombre del nodo siguiente al que dirigirse
     */
    String route(AgentState state);
}
