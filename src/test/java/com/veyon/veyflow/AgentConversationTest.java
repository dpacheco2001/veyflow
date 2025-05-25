package com.veyon.veyflow;

import com.veyon.veyflow.state.AgentState;
import com.veyon.veyflow.foundationmodels.GeminiModelService;
import com.veyon.veyflow.foundationmodels.ModelParameters;
import com.veyon.veyflow.foundationmodels.OpenAIModelService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.SpringBootTest;

import com.veyon.veyflow.tools.ToolService;
import com.veyon.veyflow.core.ToolAgent;
import com.veyon.veyflow.config.WorkflowConfig;
import com.veyon.veyflow.core.AgentTurnResult;
import com.google.gson.Gson;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.Collections;

@SpringBootTest
public class AgentConversationTest {
    private static final Logger log = LoggerFactory.getLogger(AgentConversationTest.class);

    private static final String GEMINI_MODEL_FOR_NODE = "gemini-2.5-flash-preview-05-20";
    private static final String OPENAI_MODEL_FOR_NODE = "gpt-4.1-mini";
    private static final String ANSI_RESET = "\u001B[0m";
    private static final String ANSI_GREEN = "\u001B[32m";
    // private static final String ANSI_YELLOW = "\u001B[33m"; // Unused
    private static final String ANSI_CYAN = "\u001B[36m";
    private static final String TENANT_ID = "user-tenant";

    private OpenAIModelService openAIModelService;
    private GeminiModelService geminiModelService;
    private Map<String, ToolService> registeredToolServices;
    private Gson gson;
    private ToolAgent toolAgent;
    private AgentState initialState;

    @BeforeEach
    void setUp() {
        geminiModelService = new GeminiModelService(); 
        registeredToolServices = new HashMap<>();
        registeredToolServices.put(WeatherToolService.class.getName(), new WeatherToolService());
        gson = new Gson();

        toolAgent = new ToolAgent(geminiModelService, registeredToolServices, GEMINI_MODEL_FOR_NODE, gson);

        initialState = new AgentState(TENANT_ID);
    }

    @Test
    void testToolAgentWithSpecificToolMethodActivation() {
        // 1. Setup FoundationModelService (e.g., GeminiModelService)
        // OpenAIModelService localOpenAIModelService = new OpenAIModelService();
        // String modelName = OPENAI_MODEL_FOR_NODE; // Or any other suitable model

        // 2. Instantiate WeatherToolService
        // WeatherToolService weatherService = new WeatherToolService(); 
        // Map<String, ToolService> registeredToolServices = new HashMap<>();
        // registeredToolServices.put(WeatherToolService.class.getName(), weatherService);

        // 5. Instantiate ToolAgent
        // int maxTurns = 5;
        // ToolAgent toolAgent = new ToolAgent(
        //     localOpenAIModelService,
        //     registeredToolServices,
        //     maxTurns,
        //     modelName
        // );

        // 6. Create AgentState and WorkflowConfig
        // String tenantId = "test-tenant-specific-tool";
        // AgentState currentState = new AgentState(tenantId); 
        // currentState.setChatMessages(new ArrayList<>()); // Initialize chat history
        
        WorkflowConfig workflowConfig = new WorkflowConfig(); 
        workflowConfig.setEnabledToolServiceClassNames(Collections.singletonList(WeatherToolService.class.getName()));

        // 7. Specify nodeRequestedToolNames
        List<String> nodeRequestedToolNames = Collections.singletonList(WeatherToolService.class.getName());
        System.out.println("Node Requested Tool Names: " + nodeRequestedToolNames);
        // 8. User Query
        String userQuery = "What's the weather like in London?";
        
        // 9. Model Parameters (optional override)
        ModelParameters modelParamsOverride = new ModelParameters(0.5f, 300);

        // 10. Execute ToolAgent
        AgentTurnResult result = toolAgent.execute(
            initialState,
            userQuery,
            workflowConfig,
            "You are an assistant that can get weather information.", // System prompt override
            modelParamsOverride,
            nodeRequestedToolNames
        );

        // 11. Assertions
        assertNotNull(result, "ToolAgentResult should not be null.");
        assertNotNull(result.getFinalMessage(), "Final message should not be null.");
        System.out.println(ANSI_CYAN + "ToolAgent Final Message: " + result.getFinalMessage() + ANSI_RESET);

        List<AgentTurnResult.ToolExecutionRecord> executionRecords = result.getToolExecutionMetadata(); 
        System.out.println(ANSI_CYAN + "ToolAgent Execution Records: " + executionRecords + ANSI_RESET);
        System.out.println(ANSI_GREEN + "ToolAgent Specific Tool Method Activation Test Passed!" + ANSI_RESET);
    }
}