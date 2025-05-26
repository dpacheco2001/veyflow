package com.veyon.veyflow;

import com.veyon.veyflow.core.AgentNode;
import com.veyon.veyflow.core.AgentWorkflow;
import com.veyon.veyflow.core.CompiledWorkflow;
import com.veyon.veyflow.core.ToolAgent;
import com.veyon.veyflow.config.WorkflowConfig;
import com.veyon.veyflow.config.CompileConfig;
import com.veyon.veyflow.state.AgentState;
import com.veyon.veyflow.state.AgentStateRepository;
import com.veyon.veyflow.state.ChatMessage;
import com.veyon.veyflow.state.ChatMessage.Role; 
import com.veyon.veyflow.state.PersistenceMode;
import com.veyon.veyflow.state.RedisAgentStateRepository;
import com.veyon.veyflow.tools.ToolService; 
import com.veyon.veyflow.tools.ToolCall; 
import com.veyon.veyflow.foundationmodels.OpenAIModelService;
import com.veyon.veyflow.foundationmodels.ModelParameters;
import com.veyon.veyflow.core.AgentTurnResult;

import io.lettuce.core.RedisClient; 
import io.lettuce.core.api.StatefulRedisConnection; 

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;
import java.util.HashMap;
import java.util.Arrays;
import java.util.Optional; 

import org.slf4j.Logger; 
import org.slf4j.LoggerFactory; 

class EchoNode implements AgentNode {
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
        System.out.println("Executing EchoNode: " + name);
        String inputKey = name + "_input";
        String outputKey = name + "_output";
        
        String inputValue = (String) agentState.get(inputKey);
        if (inputValue == null) {
            inputValue = "Default echo input for " + name;
        }
        agentState.set(outputKey, "Echo from " + name + ": " + inputValue);
        
        @SuppressWarnings("unchecked") 
        List<String> path = (List<String>) agentState.get("execution_path");
        if (path == null) {
            path = new ArrayList<>();
        }
        path.add(outputKey);
        agentState.set("execution_path", path);
        return agentState;
    }
}

class ToolAgentNode implements AgentNode {
    private final ToolAgent toolAgent;
    private final String name;
    private final String systemPrompt; 
    private final ModelParameters modelParameters; 

    public ToolAgentNode(String name, ToolAgent toolAgent, String systemPrompt, ModelParameters modelParameters) {
        this.name = name;
        this.toolAgent = toolAgent;
        this.systemPrompt = systemPrompt;
        this.modelParameters = modelParameters; 
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public AgentState process(AgentState agentState, WorkflowConfig workflowConfig) {
        System.out.println("Executing ToolAgentNode: " + name);
        System.out.println("AgentState: " + agentState); // User added debug
        System.out.println("WorkflowConfig: " + workflowConfig); // User added debug
        
        AgentTurnResult turnResult = toolAgent.execute(agentState, workflowConfig, this.systemPrompt, this.modelParameters);

        agentState.set(name + "_output", turnResult.getFinalMessage() != null ? turnResult.getFinalMessage() : "ToolAgentNode executed");
        return agentState;
    }
}

public class ImplicitParallelismAndToolAgentTest {
    private static final Logger log = LoggerFactory.getLogger(ImplicitParallelismAndToolAgentTest.class); 
    public static final String ANSI_RESET = "\u001B[0m";
    public static final String ANSI_BLUE = "\u001B[34m";
    public static final String ANSI_GREEN = "\u001B[32m";
    public static final String ANSI_YELLOW = "\u001B[33m";
    public static final String ANSI_CYAN = "\u001B[36m";

    private static final String TENANT_ID = "test-tenant"; 
    private static final String OPENAI_MODEL_FOR_NODE = "gpt-4.1-mini";
    private RedisClient testRedisClient = null;
    private StatefulRedisConnection<String, String> redisConnection = null;
    private String uniqueTestTenantId;
    private String uniqueTestThreadId;

    @BeforeEach
    void setUp() {
        log.info(ANSI_BLUE + "--- Setting Up Test --- " + ANSI_RESET);
        uniqueTestTenantId = "test-tenant-" + UUID.randomUUID().toString();
        uniqueTestThreadId = "test-thread-" + UUID.randomUUID().toString();
        // Ensure redisConnection is nullified before each test in case a previous test failed before cleanup
        if (redisConnection != null && redisConnection.isOpen()) {
            redisConnection.close();
        }
        if (testRedisClient != null) {
            testRedisClient.shutdown();
        }
        redisConnection = null;
        testRedisClient = null;
    }

    @AfterEach
    void tearDown() {
        log.info(ANSI_BLUE + "--- Tearing Down Test --- " + ANSI_RESET);
        if (redisConnection != null && redisConnection.isOpen()) {
            try {
                redisConnection.close();
            } catch (Exception e) {
                log.warn("Could not close Redis connection for tenant {} and thread {}: {}", uniqueTestTenantId, uniqueTestThreadId, e.getMessage());
            }
        }
        if (testRedisClient != null) {
            testRedisClient.shutdown();
        }
    }

    @Test
    void testImplicitParallelExecution() {
        AgentWorkflow workflow = new AgentWorkflow("entry"); 
        AgentNode entryNode = new EchoNode("entry");
        AgentNode nodeA = new EchoNode("nodeA");
        AgentNode nodeB = new EchoNode("nodeB");
        AgentNode exitNode = new EchoNode("exit");

        workflow.addNode(entryNode);
        workflow.addNode(nodeA);
        workflow.addNode(nodeB);
        workflow.addNode(exitNode);

        workflow.addEdge("entry", "nodeA"); 
        workflow.addEdge("entry", "nodeB"); 
        workflow.addEdge("nodeA", "exit");
        workflow.addEdge("nodeB", "exit");

        CompileConfig compileConfig = CompileConfig.builder().build(); 
        CompiledWorkflow compiledWorkflow = workflow.compile(compileConfig);

        AgentState initialState = new AgentState(TENANT_ID);
        initialState.set("input", "Start");
        initialState.set("entry_input", "Initial message for entry node");

        AgentState finalState = compiledWorkflow.execute(initialState, new WorkflowConfig());

        assertTrue(initialState.get("entry_output") != null, "Entry node should have run");
        assertTrue(initialState.get("nodeA_output") != null, "NodeA should have run");
        assertTrue(initialState.get("nodeB_output") != null, "NodeB should have run");
        assertTrue(initialState.get("exit_output") != null, "Exit node should have run");

        @SuppressWarnings("unchecked")
        List<String> executionPath = (List<String>) finalState.get("execution_path");
        if (executionPath == null) executionPath = new ArrayList<>();
        
        System.out.println("Execution path: " + executionPath);
        assertEquals("entry_output", executionPath.get(0));
        assertTrue(executionPath.contains("nodeA_output"));
        assertTrue(executionPath.contains("nodeB_output"));
        assertEquals("exit_output", executionPath.get(executionPath.size() - 1));
    }

    // @Test
    // void testToolAgentWithRedisPersistence() {
    //     log.info(ANSI_BLUE + "--- Starting Tool Agent with Redis Persistence Test --- " + ANSI_RESET);
    //     String googleApiKey = System.getenv("GOOGLE_API_KEY"); 
    //     if (googleApiKey == null || googleApiKey.trim().isEmpty()) {
    //         log.warn(ANSI_YELLOW + "API_KEY (e.g., GOOGLE_API_KEY/OPENAI_API_KEY) not set. Test might not fully function." + ANSI_RESET);
    //     }

    //     String uniqueTestTenantId = "tool-test-tenant-" + UUID.randomUUID().toString();
    //     String uniqueTestThreadId = "tool-test-thread-" + UUID.randomUUID().toString();
    //     String redisUrl = "redis://localhost:6379";

    //     try {
    //         testRedisClient = RedisClient.create(redisUrl);
    //         redisConnection = testRedisClient.connect(); 
    //         RedisAgentStateRepository redisAgentStateRepository = new RedisAgentStateRepository(redisUrl);
    //         log.info(ANSI_CYAN + "RedisAgentStateRepository initialized for tenant: {}, thread: {}" + ANSI_RESET, uniqueTestTenantId, uniqueTestThreadId);

    //         OpenAIModelService modelService = new OpenAIModelService(); 
    //         Map<String, ToolService> registeredToolServices = new HashMap<>();
    //         WeatherToolService weatherService = new WeatherToolService();
    //         registeredToolServices.put(WeatherToolService.class.getName(), weatherService);

    //         ToolAgent underlyingToolAgent = new ToolAgent(modelService, registeredToolServices, OPENAI_MODEL_FOR_NODE);
    //         String toolAgentNodeSystemPrompt = "You are an assistant that can get weather information. Prioritize using tools.";
    //         ModelParameters toolAgentNodeModelParams = new ModelParameters(0.5f, 300, 150);
    //         ToolAgentNode toolAgentNode = new ToolAgentNode("toolAgentNode", underlyingToolAgent, toolAgentNodeSystemPrompt, toolAgentNodeModelParams);

    //         AgentNode entryEcho = new EchoNode("entryMessageNode");
    //         AgentNode exitEcho = new EchoNode("exitMessageNode");

    //         AgentWorkflow workflow = new AgentWorkflow("entryMessageNode", redisAgentStateRepository);
    //         workflow.addNode(entryEcho);
    //         workflow.addNode(toolAgentNode);
    //         workflow.addNode(exitEcho);

    //         workflow.addEdge("entryMessageNode", "toolAgentNode");
    //         workflow.addEdge("toolAgentNode", "exitMessageNode");

    //         CompileConfig compileConfig = CompileConfig.builder().build();
    //         CompiledWorkflow compiledWorkflow = workflow.compile(compileConfig);
    //         assertNotNull(compiledWorkflow, "Compiled workflow should not be null");
    //         log.info(ANSI_CYAN + "Tool Agent Workflow compiled successfully." + ANSI_RESET);

    //         AgentState initialState = new AgentState(uniqueTestTenantId, uniqueTestThreadId, PersistenceMode.REDIS);
    //         initialState.set("initial_message", "What is the weather like in London?");
    //         initialState.addChatMessage(new ChatMessage(ChatMessage.Role.USER, "What is the weather like in London?"));
    //         log.info(ANSI_CYAN + "Initial agent state created for Tool Agent workflow execution." + ANSI_RESET);

    //         WorkflowConfig workflowConfig = new WorkflowConfig();
    //         workflowConfig.activateToolMethods(WeatherToolService.class.getName(), Arrays.asList("getWeather")); 
    //         AgentState finalState = compiledWorkflow.execute(initialState, workflowConfig);
    //         assertNotNull(finalState, "Final state should not be null after workflow execution.");
    //         log.info(ANSI_GREEN + "Tool Agent Workflow execution completed. Final state tenant: {}, thread: {}" + ANSI_RESET, finalState.getTenantId(), finalState.getThreadId());

    //         Optional<AgentState> retrievedStateOptional = redisAgentStateRepository.findById(uniqueTestTenantId, uniqueTestThreadId);
    //         assertTrue(retrievedStateOptional.isPresent(), "AgentState should be found in Redis.");
    //         AgentState retrievedStateFromRedis = retrievedStateOptional.get();
    //         assertNotNull(retrievedStateFromRedis, "Retrieved state from Redis should not be null.");
    //         assertEquals(uniqueTestTenantId, retrievedStateFromRedis.getTenantId(), "Tenant ID should match.");
    //         assertEquals(uniqueTestThreadId, retrievedStateFromRedis.getThreadId(), "Thread ID should match.");
    //         assertEquals(PersistenceMode.REDIS, retrievedStateFromRedis.getPersistenceMode(), "PersistenceMode should be REDIS.");

    //         logChatHistory(retrievedStateFromRedis, ANSI_CYAN + "State from Redis (Tool Agent)" + ANSI_RESET);

    //         List<ChatMessage> messagesFromRedis = retrievedStateFromRedis.getChatMessages();
    //         assertTrue(messagesFromRedis.size() >= 3, "Chat history should have at least 3 messages (USER, ASSISTANT_TOOL_CALL, TOOL_RESPONSE).");

    //         boolean toolCallFound = messagesFromRedis.stream()
    //             .anyMatch(msg -> msg.getRole() == Role.ASSISTANT && msg.getToolCalls() != null && !msg.getToolCalls().isEmpty() &&
    //                              msg.getToolCalls().get(0).getName().equals("WeatherToolService.getWeather")); 
    //         assertTrue(toolCallFound, "Assistant message with a tool call to 'WeatherToolService.getWeather' should be present.");

    //         boolean toolResponseFound = messagesFromRedis.stream()
    //             .anyMatch(msg -> msg.getRole() == Role.TOOL &&
    //                              "WeatherToolService.getWeather".equals(msg.getToolName()) && 
    //                              msg.getContent() != null && !msg.getContent().isEmpty()); 
    //         assertTrue(toolResponseFound, "Tool response message for 'WeatherToolService.getWeather' should be present.");

    //         log.info(ANSI_GREEN + "Tool Agent Redis persistence test successful!" + ANSI_RESET);

    //     } finally {
    //         if (redisConnection != null) {
    //             try {
    //                 String redisKey = "agentstate:" + uniqueTestTenantId + "::" + uniqueTestThreadId; 
    //                 log.info(ANSI_BLUE + "--- Cleaning up Redis key: " + redisKey + " --- " + ANSI_RESET);
    //                 redisConnection.sync().del(redisKey);
    //                 redisConnection.close();
    //             } catch (Exception e) {
    //                 log.warn("Could not clean up Redis key for tenant {} and thread {}: {}", uniqueTestTenantId, uniqueTestThreadId, e.getMessage());
    //             }
    //         }
    //         if (testRedisClient != null) {
    //             testRedisClient.shutdown();
    //         }
    //     }
    // }

    private void logChatHistory(AgentState state, String contextLabel) {
        log.info("{} - Chat History (Tenant: {}, Thread: {}):", 
            contextLabel, state.getTenantId(), state.getThreadId());
        if (state.getChatMessages() == null || state.getChatMessages().isEmpty()) {
            log.info("  (No messages in history)");
            return;
        }
        for (ChatMessage message : state.getChatMessages()) {
            String contentPreview = message.getContent() != null ? 
                (message.getContent().length() > 70 ? message.getContent().substring(0, 70) + "..." : message.getContent()) :
                "[No textual content]";
            log.info("  Role: {}, Content: '{}'", message.getRole(), contentPreview);
            if (message.getToolCalls() != null && !message.getToolCalls().isEmpty()) {
                for (ToolCall tc : message.getToolCalls()) { 
                    log.info("    ToolCall: ID={}, Name={}, Args={}", tc.getId(), tc.getName(), tc.getParametersAsMap()); 
                }
            }
            if (message.getRole() == Role.TOOL) {
                String toolName = message.getToolName();
                String toolResponseContent = message.getContent(); 
                String responseContentPreview = toolResponseContent != null ?
                    (toolResponseContent.length() > 70 ? toolResponseContent.substring(0, 70) + "..." : toolResponseContent) :
                    "[No tool response content]";
                log.info("    ToolMessage: Name={}, Content='{}'", toolName, responseContentPreview);
            }
        }
    }
}
