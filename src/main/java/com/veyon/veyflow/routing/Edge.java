package com.veyon.veyflow.routing;

/**
 * Represents an edge between nodes in the agent framework.
 * An edge connects a source node to a target node.
 */
public class Edge {
    private final String source;
    private final String target;
    private final String name;
    
    /**
     * Create a new edge.
     * 
     * @param source The source node name
     * @param target The target node name
     */
    public Edge(String source, String target) {
        this.source = source;
        this.target = target;
        this.name = source + "->" + target;
    }
    
    /**
     * Create a new edge with a custom name.
     * 
     * @param source The source node name
     * @param target The target node name
     * @param name The name of the edge
     */
    public Edge(String source, String target, String name) {
        this.source = source;
        this.target = target;
        this.name = name;
    }
    
    /**
     * Get the source node name.
     * 
     * @return Source node name
     */
    public String getSource() {
        return source;
    }
    
    /**
     * Get the target node name.
     * 
     * @return Target node name
     */
    public String getTarget() {
        return target;
    }
    
    /**
     * Get the edge name.
     * 
     * @return Edge name
     */
    public String getName() {
        return name;
    }
    
    @Override
    public String toString() {
        return name;
    }
}
