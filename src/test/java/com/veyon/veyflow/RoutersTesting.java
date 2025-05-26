package com.veyon.veyflow;

import com.veyon.veyflow.core.*;
import com.veyon.veyflow.state.AgentState;
import com.veyon.veyflow.routing.ConditionalRouter;
import com.veyon.veyflow.config.WorkflowConfig;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class RoutersTesting {

    private static final Logger log = LoggerFactory.getLogger(RoutersTesting.class);
    public static final String ANSI_RESET = "\u001B[0m";
    public static final String ANSI_BLUE = "\u001B[34m";

    private AgentWorkflow workflow;
    private CompiledWorkflow compiledWorkflow;

    // Helper Node for testing
    static class EchoNode implements AgentNode {
        private final String name;

        public EchoNode(String name) {
            this.name = name;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public AgentState process(AgentState agentState, WorkflowConfig workflowConfig) {
            log.info("Executing EchoNode: {}", name);
            String outputKey = name + "_output";
            agentState.set(outputKey, "Processed by " + name);

            @SuppressWarnings("unchecked")
            List<String> path = (List<String>) agentState.get("execution_path");
            if (path == null) {
                path = new ArrayList<>();
            }
            // Add only if not already present to handle potential re-processing in complex graphs (though not expected here)
            if (!path.contains(outputKey)) {
                 path.add(outputKey);
            }
            agentState.set("execution_path", path);
            return agentState;
        }
    }

    @BeforeEach
    void setUp() {
        log.info(ANSI_BLUE + "--- Setting Up Test --- " + ANSI_RESET);
        // workflow initialization moved to test methods as entryNode is specific to each test
    }

    @AfterEach
    void tearDown() {
        log.info(ANSI_BLUE + "--- Tearing Down Test --- " + ANSI_RESET);
        // Cleanup if necessary
    }

    @Test
    void testParallelExecutionWithConditionalRouters() {
        log.info(ANSI_BLUE + "--- Test: Parallel Execution with Conditional Routers --- " + ANSI_RESET);

        EchoNode entryNode = new EchoNode("entry");
        EchoNode nodeA = new EchoNode("nodeA");
        EchoNode nodeB = new EchoNode("nodeB");
        EchoNode nodeC = new EchoNode("nodeC");
        EchoNode joinNode = new EchoNode("joinNode");

        // Initialize workflow here with the specific entry node for this test
        workflow = new AgentWorkflow(entryNode.getName());

        workflow.addNode(entryNode);
        workflow.addNode(nodeA);
        workflow.addNode(nodeB);
        workflow.addNode(joinNode);

        // Routers for entry node (leading to parallel branches)
        workflow.addRouter(entryNode.getName(), new ConditionalRouter((state, config) -> {
            if ("goA".equals(state.get("condition1"))) {
                return nodeA.getName();
            }
            return null;
        }));
        workflow.addEdge(entryNode.getName(), nodeB.getName());

        // Routers for parallel branches (leading to join node)
        workflow.addEdge(nodeA.getName(), joinNode.getName());
        workflow.addEdge(nodeB.getName(), joinNode.getName());


        compiledWorkflow = workflow.compile(); // Use compile() without arguments

        AgentState initialState = new AgentState("test-tenant", "test-thread");
        initialState.set("condition1", "goA");
        initialState.set("condition2", "goB");

        AgentState finalState = compiledWorkflow.execute(initialState, new WorkflowConfig());

        @SuppressWarnings("unchecked")
        List<String> executionPath = (List<String>) finalState.get("execution_path");
        if (executionPath == null) executionPath = new ArrayList<>();
        
        log.info("Execution path: {}", executionPath);

        assertTrue(executionPath.contains("entry_output"), "Entry node should have run");
        assertTrue(executionPath.contains("nodeA_output"), "NodeA should have run");
        assertTrue(executionPath.contains("nodeB_output"), "NodeB should have run");
        assertTrue(executionPath.contains("joinNode_output"), "JoinNode should have run");
        
        assertEquals("joinNode_output", executionPath.get(executionPath.size() - 1), "JoinNode_output should be the last element");
        // Check order: entry first, joinNode last. A and B can be in any order in between.
        assertEquals("entry_output", executionPath.get(0), "Entry_output should be the first element");
        assertTrue(executionPath.indexOf("nodeA_output") < executionPath.indexOf("joinNode_output"), "nodeA should run before joinNode");
        assertTrue(executionPath.indexOf("nodeB_output") < executionPath.indexOf("joinNode_output"), "nodeB should run before joinNode");
    }

    @Test
    void testMixedRoutingFromSingleNode() {
        log.info(ANSI_BLUE + "--- Test: Mixed Routing (Linear + Conditional) from Single Node --- " + ANSI_RESET);

        EchoNode entryNode = new EchoNode("entry");
        EchoNode nodeA = new EchoNode("nodeA"); // From LinearRouter
        EchoNode nodeB = new EchoNode("nodeB"); // From ConditionalRouter option 1
        EchoNode nodeC = new EchoNode("nodeC"); // From ConditionalRouter option 2
        EchoNode joinNode = new EchoNode("joinNode");

        // Scenario 1: Conditional router goes to nodeB
        log.info(ANSI_BLUE + "--- Scenario 1: Conditional to nodeB --- " + ANSI_RESET);
        workflow = new AgentWorkflow(entryNode.getName());
        workflow.addNode(entryNode).addNode(nodeA).addNode(nodeB).addNode(nodeC).addNode(joinNode);

        // Routers from entry node
        workflow.addEdge(entryNode.getName(), nodeA.getName()); // Linear to nodeA
        workflow.addRouter(entryNode.getName(), new ConditionalRouter((state, config) -> {
            String conditionValue = (String) state.get("condition");
            if ("goToB".equals(conditionValue)) {
                return nodeB.getName();
            } else if ("goToC".equals(conditionValue)) {
                return nodeC.getName();
            }
            return null;
        }));

        // Routers for parallel branches to joinNode
        workflow.addEdge(nodeA.getName(), joinNode.getName());
        workflow.addEdge(nodeB.getName(), joinNode.getName());
        workflow.addEdge(nodeC.getName(), joinNode.getName()); // nodeC also needs to go to joinNode

        compiledWorkflow = workflow.compile();

        AgentState initialStateB = new AgentState("test-tenant", "test-thread-b");
        initialStateB.set("condition", "goToB");

        AgentState finalStateB = compiledWorkflow.execute(initialStateB, new WorkflowConfig());

        @SuppressWarnings("unchecked")
        List<String> executionPathB = (List<String>) finalStateB.get("execution_path");
        if (executionPathB == null) executionPathB = new ArrayList<>();
        log.info("Execution path (Scenario B - to nodeB): {}", executionPathB);

        assertTrue(executionPathB.contains("entry_output"), "[B] Entry node should have run");
        assertTrue(executionPathB.contains("nodeA_output"), "[B] NodeA should have run");
        assertTrue(executionPathB.contains("nodeB_output"), "[B] NodeB should have run");
        assertFalse(executionPathB.contains("nodeC_output"), "[B] NodeC should NOT have run");
        assertTrue(executionPathB.contains("joinNode_output"), "[B] JoinNode should have run");
        assertEquals("joinNode_output", executionPathB.get(executionPathB.size() - 1), "[B] JoinNode_output should be the last element");
        assertEquals("entry_output", executionPathB.get(0), "[B] Entry_output should be the first element");
        assertTrue(executionPathB.indexOf("nodeA_output") < executionPathB.indexOf("joinNode_output"), "[B] nodeA should run before joinNode");
        assertTrue(executionPathB.indexOf("nodeB_output") < executionPathB.indexOf("joinNode_output"), "[B] nodeB should run before joinNode");

        // Scenario 2: Conditional router goes to nodeC
        log.info(ANSI_BLUE + "--- Scenario 2: Conditional to nodeC --- " + ANSI_RESET);
        // Re-initialize workflow for a clean state for routers if necessary, or ensure routers are stateless.
        // For this test, AgentWorkflow is re-created to ensure clean router registration for the new scenario's logic if entryNode's routers were stateful (they are not here, but good practice for complex tests)
        // However, since we are just changing the AgentState, we can reuse the same compiledWorkflow if nodes/routers are the same.
        // Let's re-compile to be safe and clear, as if it were a different workflow setup for C.

        workflow = new AgentWorkflow(entryNode.getName()); // Re-init for clarity, though not strictly needed if routers are stateless
        workflow.addNode(entryNode).addNode(nodeA).addNode(nodeB).addNode(nodeC).addNode(joinNode);
        workflow.addEdge(entryNode.getName(), nodeA.getName()); 
        workflow.addRouter(entryNode.getName(), new ConditionalRouter((state, config) -> {
            String conditionValue = (String) state.get("condition");
            if ("goToB".equals(conditionValue)) {
                return nodeB.getName();
            } else if ("goToC".equals(conditionValue)) {
                return nodeC.getName();
            }
            return null;
        }));
        workflow.addEdge(nodeA.getName(), joinNode.getName());
        workflow.addEdge(nodeB.getName(), joinNode.getName()); 
        workflow.addEdge(nodeC.getName(), joinNode.getName());
        compiledWorkflow = workflow.compile(); // Re-compile

        AgentState initialStateC = new AgentState("test-tenant", "test-thread-c");
        initialStateC.set("condition", "goToC");

        AgentState finalStateC = compiledWorkflow.execute(initialStateC, new WorkflowConfig());

        @SuppressWarnings("unchecked")
        List<String> executionPathC = (List<String>) finalStateC.get("execution_path");
        if (executionPathC == null) executionPathC = new ArrayList<>();
        log.info("Execution path (Scenario C - to nodeC): {}", executionPathC);

        assertTrue(executionPathC.contains("entry_output"), "[C] Entry node should have run");
        assertTrue(executionPathC.contains("nodeA_output"), "[C] NodeA should have run");
        assertFalse(executionPathC.contains("nodeB_output"), "[C] NodeB should NOT have run");
        assertTrue(executionPathC.contains("nodeC_output"), "[C] NodeC should have run");
        assertTrue(executionPathC.contains("joinNode_output"), "[C] JoinNode should have run");
        assertEquals("joinNode_output", executionPathC.get(executionPathC.size() - 1), "[C] JoinNode_output should be the last element");
        assertEquals("entry_output", executionPathC.get(0), "[C] Entry_output should be the first element");
        assertTrue(executionPathC.indexOf("nodeA_output") < executionPathC.indexOf("joinNode_output"), "[C] nodeA should run before joinNode");
        assertTrue(executionPathC.indexOf("nodeC_output") < executionPathC.indexOf("joinNode_output"), "[C] nodeC should run before joinNode");
    }
}
