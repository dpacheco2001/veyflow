package com.veyon.veyflow;

import com.veyon.veyflow.state.AgentState;
import com.veyon.veyflow.foundationmodels.GeminiModelService;
import com.veyon.veyflow.foundationmodels.FoundationModelService;
import com.veyon.veyflow.foundationmodels.ModelParameters;
import com.veyon.veyflow.foundationmodels.OpenAIModelService;
import com.veyon.veyflow.core.AgentWorkflow;
import com.veyon.veyflow.core.CompiledWorkflow;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.SpringBootTest;

import com.veyon.veyflow.tools.ToolService;
import com.veyon.veyflow.core.ToolAgent;
import com.veyon.veyflow.core.AgentTurnResult;
import com.veyon.veyflow.core.LLM;
import com.veyon.veyflow.core.AgentNode;
import com.veyon.veyflow.state.RedisAgentStateRepository;
import com.veyon.veyflow.state.PersistenceMode;
import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import java.util.HashMap;
import java.util.Optional;
import java.util.UUID;

import com.veyon.veyflow.state.ChatMessage;

@SpringBootTest
public class AgentConversationTest {
    private static final Logger log = LoggerFactory.getLogger(AgentConversationTest.class);

    // ANSI escape codes for logging colors
    private static final String ANSI_RESET = "\u001B[0m";
    private static final String ANSI_RED = "\u001B[31m";
    private static final String ANSI_GREEN = "\u001B[32m";
    private static final String ANSI_YELLOW = "\u001B[33m";
    private static final String ANSI_BLUE = "\u001B[34m";
    private static final String ANSI_CYAN = "\u001B[36m";

    private static final String GEMINI_MODEL_FOR_NODE = "gemini-2.5-flash-preview-05-20";
    private static final String OPENAI_MODEL_FOR_NODE = "gpt-4.1-mini";
    private static final String TENANT_ID = "user-tenant";

    private FoundationModelService foundationModelService;
    private OpenAIModelService openAIModelService;
    private GeminiModelService geminiModelService;
    private Map<String, ToolService> registeredToolServices;
    private ToolAgent toolAgent;
    private AgentState initialState;

    @BeforeEach
    void setUp() {
        geminiModelService = new GeminiModelService(); 
        openAIModelService = new OpenAIModelService();
        foundationModelService = new OpenAIModelService();
        registeredToolServices = new HashMap<>();
        registeredToolServices.put(WeatherToolService.class.getName(), new WeatherToolService());

        toolAgent = new ToolAgent(openAIModelService, registeredToolServices, OPENAI_MODEL_FOR_NODE);

        initialState = new AgentState(TENANT_ID);
    }

    // @Test
    // void testToolAgentWithSpecificToolMethodActivation() {
    //     WorkflowConfig workflowConfig = new WorkflowConfig(); 
    //     List<String> methodsToActivate = Arrays.asList("getWeather");
    //     workflowConfig.activateToolMethods(WeatherToolService.class.getName(), methodsToActivate);

    //     String userMessage = "Hi, my name is Diego, What's the weather like in London?";
        
        
    //     ModelParameters modelParamsOverride = new ModelParameters(0.5f, 300, 150);
    //     AgentTurnResult result = toolAgent.execute(
    //         initialState,
    //         userMessage,
    //         workflowConfig,
    //         "You are an assistant that can get weather information.",
    //         modelParamsOverride
    //     );

    //     System.out.println(ANSI_GREEN+"Final message:"+ANSI_RESET);
    //     System.out.println(result.getFinalMessage());
    //     System.out.println(ANSI_GREEN+"Tool execution records:"+ANSI_RESET);
    //     for (AgentTurnResult.ToolExecutionRecord record : result.getToolExecutionMetadata()) {
    //         System.out.println(record.getToolName());
    //     }
    //     System.out.println(ANSI_GREEN+"Chat history:"+ANSI_RESET);
    //     for (ChatMessage message : result.getChatHistory()) {
    //         if(message.getContent() != null && !message.getContent( ).isEmpty()) {
    //             System.out.println(message.getRole()+": "+message.getContent());
    //         }
    //         if(message.getToolCalls() != null && !message.getToolCalls().isEmpty()) {
    //             System.out.println(message.getRole()+": "+message.getToolCalls());
    //         }

    //     }
    //     System.out.println(ANSI_CYAN+"New messages:"+ANSI_RESET);
    //     for (ChatMessage message : result.getNewMessages()) {
    //         if(message.getContent() != null && !message.getContent().isEmpty()) {   
    //             System.out.println(message.getRole()+": "+message.getContent());
    //         }
    //         if(message.getToolCalls() != null && !message.getToolCalls().isEmpty()) {
    //             System.out.println(message.getRole()+": "+message.getToolCalls());
    //         }
    //     }


    //     String userMessage2 = "What is my name? and which was the city i asked you for?";
    //     AgentTurnResult secondTurnResult = toolAgent.execute(
    //         initialState,
    //         userMessage2,
    //         workflowConfig,
    //         "You are an assistant that can get weather information.",
    //         modelParamsOverride
    //     );

    //     System.out.println(ANSI_GREEN+"Final message:"+ANSI_RESET);
    //     System.out.println(secondTurnResult.getFinalMessage()); 
    //     System.out.println(ANSI_GREEN+"Tool execution records:"+ANSI_RESET);
    //     for (AgentTurnResult.ToolExecutionRecord record : secondTurnResult.getToolExecutionMetadata()) {
    //         System.out.println(record.getToolName());
    //     }
    //     System.out.println(ANSI_GREEN+"Chat history:"+ANSI_RESET);
    //     for (ChatMessage message : secondTurnResult.getChatHistory()) {
    //         if(message.getContent() != null && !message.getContent().isEmpty()) {
    //             System.out.println(message.getRole()+": "+message.getContent());
    //         }
    //         if(message.getToolCalls() != null && !message.getToolCalls().isEmpty()) {
    //             System.out.println(message.getRole()+": "+message.getToolCalls());
    //         }

    //     }
    //     System.out.println(ANSI_CYAN+"New messages:"+ANSI_RESET);
    //     for (ChatMessage message : secondTurnResult.getNewMessages()) {
    //         if(message.getContent() != null && !message.getContent().isEmpty()) {   
    //             System.out.println(message.getRole()+": "+message.getContent());
    //         }
    //         if(message.getToolCalls() != null && !message.getToolCalls().isEmpty()) {
    //             System.out.println(message.getRole()+": "+message.getToolCalls());
    //         }
    //     }
        

    //     assertNotNull(result, "ToolAgentResult should not be null.");
    //     assertNotNull(result.getFinalMessage(), "Final message should not be null.");

    // }

    // @Test
    // public void testLLMConversationMemory() {
    //     AgentState agentState = new AgentState("test-tenant", "test-session");
    //     LLM llm = new LLM(foundationModelService, "gpt-4.1-mini-2025-04-14"); 

    //     String userMessage1 = "Hi, my name is Diego.";
    //     System.out.println(ANSI_BLUE + "--- LLM Test - Turn 1 ---" + ANSI_RESET);
    //     AgentTurnResult result1 = llm.execute(
    //         agentState, 
    //         userMessage1, 
    //         "You are a helpful assistant.", 
    //         null 
    //     );

    //     System.out.println(ANSI_GREEN + "Final message (Turn 1):" + ANSI_RESET);
    //     System.out.println(result1.getFinalMessage());
    //     System.out.println(ANSI_GREEN + "Chat history (Turn 1):" + ANSI_RESET);
    //     for (ChatMessage message : result1.getChatHistory()) {
    //         if (message.getContent() != null && !message.getContent().isEmpty()) {
    //             System.out.println(message.getRole() + ": " + message.getContent());
    //         }
    //     }
    //     System.out.println(ANSI_CYAN + "New messages (Turn 1):" + ANSI_RESET);
    //     for (ChatMessage message : result1.getNewMessages()) {
    //         if (message.getContent() != null && !message.getContent().isEmpty()) {
    //             System.out.println(message.getRole() + ": " + message.getContent());
    //         }
    //     }

    //     assertNotNull(result1, "LLM Turn 1 Result should not be null.");
    //     assertNotNull(result1.getFinalMessage(), "LLM Turn 1 Final message should not be null.");

    //     // Second turn
    //     String userMessage2 = "What is my name?";
    //     System.out.println(ANSI_BLUE + "--- LLM Test - Turn 2 ---" + ANSI_RESET);
    //     AgentTurnResult result2 = llm.execute(
    //         agentState, 
    //         userMessage2, 
    //         "You are a helpful assistant.", 
    //         null
    //     );

    //     System.out.println(ANSI_GREEN + "Final message (Turn 2):" + ANSI_RESET);
    //     System.out.println(result2.getFinalMessage());
    //     System.out.println(ANSI_GREEN + "Chat history (Turn 2):" + ANSI_RESET);
    //     for (ChatMessage message : result2.getChatHistory()) {
    //         if (message.getContent() != null && !message.getContent().isEmpty()) {
    //             System.out.println(message.getRole() + ": " + message.getContent());
    //         }
    //     }
    //     System.out.println(ANSI_CYAN + "New messages (Turn 2):" + ANSI_RESET);
    //     for (ChatMessage message : result2.getNewMessages()) {
    //         if (message.getContent() != null && !message.getContent().isEmpty()) {
    //             System.out.println(message.getRole() + ": " + message.getContent());
    //         }
    //     }

    //     assertNotNull(result2, "LLM Turn 2 Result should not be null.");
    //     assertNotNull(result2.getFinalMessage(), "LLM Turn 2 Final message should not be null.");
    //     assertTrue(result2.getFinalMessage().toLowerCase().contains("diego"), "LLM should remember the name 'Diego'.");

    //     AgentState agentState2 = new AgentState("test-tenant", "test-session2");
        
    //     AgentTurnResult result_thread2 = llm.execute(
    //         agentState2, 
    //         "Hi, what is my name?", 
    //         "You are a helpful assistant.", 
    //         null 
    //     );

    //     System.out.println(ANSI_GREEN + "Final message (Thread 2):" + ANSI_RESET);
    //     System.out.println(result_thread2.getFinalMessage());
    //     System.out.println(ANSI_GREEN + "Chat history (Thread 2):" + ANSI_RESET);
    //     for (ChatMessage message : result_thread2.getChatHistory()) {
    //         if (message.getContent() != null && !message.getContent().isEmpty()) {
    //             System.out.println(message.getRole() + ": " + message.getContent());
    //         }
    //     }
    //     System.out.println(ANSI_CYAN + "New messages (Thread 2):" + ANSI_RESET);
    //     for (ChatMessage message : result_thread2.getNewMessages()) {
    //         if (message.getContent() != null && !message.getContent().isEmpty()) {
    //             System.out.println(message.getRole() + ": " + message.getContent());
    //         }
    //     }

    // }

    @Test
    void testSimpleWorkflowWithRedisPersistence() {
        log.info(ANSI_BLUE + "--- Starting Simple Workflow with Redis Persistence Test --- " + ANSI_RESET);
        String googleApiKey = System.getenv("GOOGLE_API_KEY");
        if (googleApiKey == null || googleApiKey.trim().isEmpty()) {
            log.warn(ANSI_YELLOW + "GOOGLE_API_KEY not set. Test might not fully function." + ANSI_RESET);
            // Consider failing or skipping if API key is crucial: fail("GOOGLE_API_KEY not set");
        }

        String uniqueTestTenantId = "test-tenant-" + UUID.randomUUID().toString();
        String uniqueTestThreadId = "test-thread-" + UUID.randomUUID().toString();
        String redisUrl = "redis://localhost:6379"; 

        RedisClient testRedisClient = null;
        StatefulRedisConnection<String, String> redisConnection = null;
        RedisAgentStateRepository redisAgentStateRepository = null;

        try {
            // 1. Configuración del Repositorio Redis
            testRedisClient = RedisClient.create(redisUrl);
            redisConnection = testRedisClient.connect();
            redisAgentStateRepository = new RedisAgentStateRepository(redisUrl); 
            log.info(ANSI_CYAN + "RedisAgentStateRepository initialized for tenant: {}, thread: {}" + ANSI_RESET, uniqueTestTenantId, uniqueTestThreadId);

            // 2. Definición del Nodo LLM Simple
            String llmNodeName = "simpleLLMNode_Gemini";
            FoundationModelService geminiService = new GeminiModelService(); 
            ModelParameters modelParams = new ModelParameters(0.7f, 50);
            SimpleLLMNode llmNode = new SimpleLLMNode(llmNodeName, geminiService, "gemini-1.5-flash-latest", modelParams, "You are a helpful assistant.");

            // 3. Configuración del Workflow
            AgentWorkflow agentWorkflow = new AgentWorkflow(llmNodeName, redisAgentStateRepository);
            agentWorkflow.addNode(llmNode);
            // No router needed for a single terminal node workflow

            // 4. Compilación del Workflow
            CompiledWorkflow compiledWorkflow = agentWorkflow.compile();
            assertNotNull(compiledWorkflow, "Compiled workflow should not be null");
            log.info(ANSI_CYAN + "Workflow compiled successfully." + ANSI_RESET);

            // 5. Creación del Estado Inicial del Agente
            AgentState initialState = new AgentState(uniqueTestTenantId, uniqueTestThreadId, PersistenceMode.REDIS);
            initialState.addChatMessage(new ChatMessage(ChatMessage.Role.USER, "Hello, what is the capital of France?"));
            log.info(ANSI_CYAN + "Initial agent state created for workflow execution." + ANSI_RESET);

            // 6. Ejecución del Workflow Compilado
            log.info(ANSI_BLUE + "--- Executing Compiled Workflow --- " + ANSI_RESET);
            AgentState finalState = compiledWorkflow.execute(initialState);
            assertNotNull(finalState, "Final state should not be null after workflow execution.");
            log.info(ANSI_GREEN + "Workflow execution completed. Final state tenant: {}, thread: {}" + ANSI_RESET, finalState.getTenantId(), finalState.getThreadId());
            //7.1 Segunda entrada
            initialState.addChatMessage(new ChatMessage(ChatMessage.Role.USER, "What is the capital of Spain?"));
            AgentState finalState2 = compiledWorkflow.execute(initialState);
            assertNotNull(finalState2, "Final state should not be null after workflow execution.");
            log.info(ANSI_GREEN + "Workflow execution completed. Final state tenant: {}, thread: {}" + ANSI_RESET, finalState2.getTenantId(), finalState2.getThreadId());
            // 7. Verificación del Estado en Redis
            log.info(ANSI_BLUE + "--- Verifying State in Redis --- " + ANSI_RESET);
            Optional<AgentState> retrievedStateOptional = redisAgentStateRepository.findById(uniqueTestTenantId, uniqueTestThreadId);
            assertTrue(retrievedStateOptional.isPresent(), "AgentState should be found in Redis.");
            AgentState retrievedStateFromRedis = retrievedStateOptional.get();
            assertNotNull(retrievedStateFromRedis, "Retrieved state from Redis should not be null.");
            assertEquals(uniqueTestTenantId, retrievedStateFromRedis.getTenantId(), "Tenant ID should match.");
            assertEquals(uniqueTestThreadId, retrievedStateFromRedis.getThreadId(), "Thread ID should match.");
            assertEquals(PersistenceMode.REDIS, retrievedStateFromRedis.getPersistenceMode(), "PersistenceMode should be REDIS.");

            logChatHistory(retrievedStateFromRedis, ANSI_CYAN + "State from Redis" + ANSI_RESET);

            // Verificar que el historial de chat contiene la respuesta del LLM
            assertTrue(retrievedStateFromRedis.getChatMessages().size() > 1, "Chat history should have more than one message.");
            boolean assistantMessageFound = retrievedStateFromRedis.getChatMessages().stream()
                .anyMatch(msg -> msg.getRole() == ChatMessage.Role.ASSISTANT && msg.getContent() != null && !msg.getContent().isEmpty());
            assertTrue(assistantMessageFound, "Assistant message should be present in chat history from Redis.");
            assertNotNull(retrievedStateFromRedis.get("llm_response"), "LLM response should be in Redis state values.");

            log.info(ANSI_GREEN + "Redis persistence test successful!" + ANSI_RESET);

        } finally {
            // 8. Limpieza
            if (redisConnection != null) {
                try {
                    String redisKey = "agent_state:" + uniqueTestTenantId + ":" + uniqueTestThreadId;
                    System.out.println(ANSI_BLUE + "--- Cleaning up Redis key: " + redisKey + " --- " + ANSI_RESET);
                    // redisConnection.sync().del(redisKey);
                    redisConnection.close();
                } catch (Exception e) {
                    log.warn("Could not clean up Redis key for tenant {} and thread {}: {}", uniqueTestTenantId, uniqueTestThreadId, e.getMessage());
                }
            }
            if (testRedisClient != null) {
                testRedisClient.shutdown();
            }
        }
    }

    private void logChatHistory(AgentState state, String context) {
        log.info(ANSI_BLUE + "--- Chat History ({}) ---" + ANSI_RESET, context);
        if (state.getChatMessages().isEmpty()) {
            log.info(ANSI_YELLOW + "(No messages in chat history for this state)" + ANSI_RESET);
        }
        state.getChatMessages().forEach(msg -> {
            String roleColor = ANSI_RED; 
            String roleString = msg.getRole() != null ? msg.getRole().toString() : "UNKNOWN_ROLE";
            switch (roleString) {
                case "USER": roleColor = ANSI_GREEN; break;
                case "ASSISTANT": roleColor = ANSI_CYAN; break;
                case "TOOL": roleColor = ANSI_YELLOW; break;
                case "SYSTEM": roleColor = ANSI_BLUE; break;
            }
            String content = msg.getContent() != null ? msg.getContent() : "[null content]";
            // Assuming ChatMessage does not have a getMetadata() method that returns a Map for now.
            // If it does, and you want to log metadata, you can uncomment and adjust the following:
            /*
            String metadataString = (msg.getMetadata() != null && !msg.getMetadata().entrySet().isEmpty()) 
                                    ? ANSI_YELLOW + " | Metadata: " + msg.getMetadata() + ANSI_RESET : "";
            log.info(roleColor + "[{}] {}{}" + ANSI_RESET, 
                roleString, 
                content.replace("\n", "\\n"), 
                metadataString
            );
            */
            log.info(roleColor + "[{}] {}" + ANSI_RESET, 
                roleString, 
                content.replace("\n", "\\n")
            );
        });
        log.info(ANSI_BLUE + "--- End Chat History ({}) ---" + ANSI_RESET, context);
    }

    // Inner class for a simple LLM node
    class SimpleLLMNode implements AgentNode {
        private final String nodeName;
        private final FoundationModelService modelService;
        private final String modelName;
        private final ModelParameters modelParams;
        private final String persona;

        public SimpleLLMNode(String nodeName, FoundationModelService modelService, String modelName, ModelParameters modelParams, String persona) {
            this.nodeName = nodeName;
            this.modelService = modelService;
            this.modelName = modelName;
            this.modelParams = modelParams;
            this.persona = persona;
        }

        @Override
        public String getName() {
            return nodeName;
        }

        @Override
        public AgentState process(AgentState state) {
            log.info(ANSI_BLUE + "--- SimpleLLMNode processing --- " + ANSI_RESET);
            // String userInput = state.getChatMessages().get(state.getChatMessages().size() - 1).getContent(); // This line is no longer needed here as LLM will use the last message from state

            LLM llm = new LLM(modelService, modelName);
            AgentTurnResult llmResult = llm.execute(
                state, 
                persona, 
                modelParams
            );
            // LLM.execute ya actualiza el state con los mensajes, así que no necesitamos hacer state.addChatMessage manualmente aquí
            // para los mensajes de la interacción con el LLM.
            state.set("llm_response", llmResult.getFinalMessage());
            log.info(ANSI_GREEN + "LLM Node processed. Response: " + llmResult.getFinalMessage() + ANSI_RESET);
            return state;
        }
    }
}