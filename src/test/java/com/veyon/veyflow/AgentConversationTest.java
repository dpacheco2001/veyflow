package com.veyon.veyflow;

import com.veyon.veyflow.state.AgentState;
import com.veyon.veyflow.foundationmodels.GeminiModelService;
import com.veyon.veyflow.foundationmodels.FoundationModelService;
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
import com.veyon.veyflow.core.LLM;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Arrays;

import com.veyon.veyflow.state.ChatMessage;

@SpringBootTest
public class AgentConversationTest {
    private static final Logger log = LoggerFactory.getLogger(AgentConversationTest.class);

    private static final String GEMINI_MODEL_FOR_NODE = "gemini-2.5-flash-preview-05-20";
    private static final String OPENAI_MODEL_FOR_NODE = "gpt-4.1-mini";
    private static final String ANSI_RESET = "\u001B[0m";
    private static final String ANSI_GREEN = "\u001B[32m";
    private static final String ANSI_BLUE = "\u001B[34m"; 
    private static final String ANSI_CYAN = "\u001B[36m";
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

    @Test
    public void testLLMConversationMemory() {
        AgentState agentState = new AgentState("test-tenant", "test-session");
        LLM llm = new LLM(foundationModelService, "gpt-4.1-mini-2025-04-14"); 

        String userMessage1 = "Hi, my name is Diego.";
        System.out.println(ANSI_BLUE + "--- LLM Test - Turn 1 ---" + ANSI_RESET);
        AgentTurnResult result1 = llm.execute(
            agentState, 
            userMessage1, 
            "You are a helpful assistant.", 
            null 
        );

        System.out.println(ANSI_GREEN + "Final message (Turn 1):" + ANSI_RESET);
        System.out.println(result1.getFinalMessage());
        System.out.println(ANSI_GREEN + "Chat history (Turn 1):" + ANSI_RESET);
        for (ChatMessage message : result1.getChatHistory()) {
            if (message.getContent() != null && !message.getContent().isEmpty()) {
                System.out.println(message.getRole() + ": " + message.getContent());
            }
        }
        System.out.println(ANSI_CYAN + "New messages (Turn 1):" + ANSI_RESET);
        for (ChatMessage message : result1.getNewMessages()) {
            if (message.getContent() != null && !message.getContent().isEmpty()) {
                System.out.println(message.getRole() + ": " + message.getContent());
            }
        }

        assertNotNull(result1, "LLM Turn 1 Result should not be null.");
        assertNotNull(result1.getFinalMessage(), "LLM Turn 1 Final message should not be null.");

        // Second turn
        String userMessage2 = "What is my name?";
        System.out.println(ANSI_BLUE + "--- LLM Test - Turn 2 ---" + ANSI_RESET);
        AgentTurnResult result2 = llm.execute(
            agentState, 
            userMessage2, 
            "You are a helpful assistant.", 
            null
        );

        System.out.println(ANSI_GREEN + "Final message (Turn 2):" + ANSI_RESET);
        System.out.println(result2.getFinalMessage());
        System.out.println(ANSI_GREEN + "Chat history (Turn 2):" + ANSI_RESET);
        for (ChatMessage message : result2.getChatHistory()) {
            if (message.getContent() != null && !message.getContent().isEmpty()) {
                System.out.println(message.getRole() + ": " + message.getContent());
            }
        }
        System.out.println(ANSI_CYAN + "New messages (Turn 2):" + ANSI_RESET);
        for (ChatMessage message : result2.getNewMessages()) {
            if (message.getContent() != null && !message.getContent().isEmpty()) {
                System.out.println(message.getRole() + ": " + message.getContent());
            }
        }

        assertNotNull(result2, "LLM Turn 2 Result should not be null.");
        assertNotNull(result2.getFinalMessage(), "LLM Turn 2 Final message should not be null.");
        assertTrue(result2.getFinalMessage().toLowerCase().contains("diego"), "LLM should remember the name 'Diego'.");

        AgentState agentState2 = new AgentState("test-tenant", "test-session2");
        
        AgentTurnResult result_thread2 = llm.execute(
            agentState2, 
            "Hi, what is my name?", 
            "You are a helpful assistant.", 
            null 
        );

        System.out.println(ANSI_GREEN + "Final message (Thread 2):" + ANSI_RESET);
        System.out.println(result_thread2.getFinalMessage());
        System.out.println(ANSI_GREEN + "Chat history (Thread 2):" + ANSI_RESET);
        for (ChatMessage message : result_thread2.getChatHistory()) {
            if (message.getContent() != null && !message.getContent().isEmpty()) {
                System.out.println(message.getRole() + ": " + message.getContent());
            }
        }
        System.out.println(ANSI_CYAN + "New messages (Thread 2):" + ANSI_RESET);
        for (ChatMessage message : result_thread2.getNewMessages()) {
            if (message.getContent() != null && !message.getContent().isEmpty()) {
                System.out.println(message.getRole() + ": " + message.getContent());
            }
        }

    }
}