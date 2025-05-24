package com.veyon.veyflow.examples;

import com.veyon.veyflow.config.AgentConfig;
import com.veyon.veyflow.core.AgentFrameworkService;
import com.veyon.veyflow.core.AgentWorkflow;
import com.veyon.veyflow.foundationmodels.ModelParameters;
import com.veyon.veyflow.state.AgentState;
import com.veyon.veyflow.state.StateParameter;

import java.util.Map;
import java.util.HashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Ejemplo que muestra cómo crear un workflow personalizado
 * utilizando el framework de agentes.
 */
public class CustomWorkflowBuilder {
    private static final Logger log = LoggerFactory.getLogger(CustomWorkflowBuilder.class);
    
    private final AgentFrameworkService agentFrameworkService;
    
    public CustomWorkflowBuilder(AgentFrameworkService agentFrameworkService) {
        this.agentFrameworkService = agentFrameworkService;
    }
    
    /**
     * Define el estado mínimo requerido para este workflow.
     * Utiliza la anotación StateParameter para indicar campos obligatorios.
     */
    public static class CustomerSupportState {
        @StateParameter(required = true, description = "ID del cliente")
        private String customerId;
        
        @StateParameter(required = true, description = "Tipo de consulta del cliente")
        private String queryType;
        
        @StateParameter(description = "Contexto adicional para la consulta")
        private String additionalContext;
        
        // Getters y setters
        public String getCustomerId() { return customerId; }
        public void setCustomerId(String customerId) { this.customerId = customerId; }
        public String getQueryType() { return queryType; }
        public void setQueryType(String queryType) { this.queryType = queryType; }
        public String getAdditionalContext() { return additionalContext; }
        public void setAdditionalContext(String additionalContext) { this.additionalContext = additionalContext; }
    }
    
    /**
     * Crea un workflow de soporte al cliente.
     * 
     * @param tenantId ID del tenant
     * @return El workflow creado
     */
    public AgentWorkflow createCustomerSupportWorkflow(String tenantId) {
        String systemPrompt = "Eres un asistente de soporte al cliente. " +
                "Tu objetivo es ayudar a resolver problemas y responder preguntas. " +
                "Utiliza el ID del cliente y el tipo de consulta para personalizar tus respuestas. " +
                "Si hay contexto adicional disponible, úsalo para proporcionar una respuesta más detallada.";
        
        // Crear el workflow usando el servicio del framework
        AgentWorkflow workflow = agentFrameworkService.createChatAgentWorkflow(
            tenantId,
            "customerSupport",
            systemPrompt,
            ModelParameters.defaults()
        );
        
        log.info("Workflow de soporte al cliente creado para tenant: {}", tenantId);
        return workflow;
    }
    
    /**
     * Procesa un mensaje de usuario utilizando el workflow de soporte al cliente.
     * Demuestra cómo utilizar AgentConfig para pasar parámetros específicos del estado.
     * 
     * @param tenantId ID del tenant
     * @param threadId ID del thread
     * @param userMessage Mensaje del usuario
     * @param customerState Estado específico del cliente
     * @return El estado actualizado
     */
    public AgentState processCustomerQuery(
            String tenantId,
            String threadId,
            String userMessage,
            CustomerSupportState customerState) {
        
        // Validar parámetros obligatorios
        if (customerState.getCustomerId() == null || customerState.getCustomerId().isEmpty()) {
            throw new IllegalArgumentException("customerId es obligatorio");
        }
        if (customerState.getQueryType() == null || customerState.getQueryType().isEmpty()) {
            throw new IllegalArgumentException("queryType es obligatorio");
        }
        
        // Crear configuración con parámetros específicos del estado
        AgentConfig config = AgentConfig.builder()
                .tenantId(tenantId)
                .threadId(threadId)
                .parameter("customerId", customerState.getCustomerId())
                .parameter("queryType", customerState.getQueryType())
                .parameter("additionalContext", 
                        customerState.getAdditionalContext() != null ? 
                        customerState.getAdditionalContext() : "")
                .build();
        
        log.info("Procesando consulta para cliente: {}, tipo: {}", 
                customerState.getCustomerId(), customerState.getQueryType());
        
        // Procesar el mensaje utilizando el servicio del framework
        return agentFrameworkService.processUserMessage(
                "customerSupport",
                userMessage,
                config
        );
    }
    
    /**
     * Ejemplo de uso del workflow de soporte al cliente.
     */
    public static void main(String[] args) {
        // Este es solo un ejemplo, normalmente se inyectaría el servicio
        AgentFrameworkService service = null; // Debe inyectarse o crearse adecuadamente
        
        CustomWorkflowBuilder builder = new CustomWorkflowBuilder(service);
        
        // Crear el workflow
        builder.createCustomerSupportWorkflow("tenant123");
        
        // Preparar estado del cliente
        CustomerSupportState state = new CustomerSupportState();
        state.setCustomerId("C12345");
        state.setQueryType("technical");
        state.setAdditionalContext("Usuario premium con historial de problemas similares");
        
        // Procesar una consulta
        AgentState result = builder.processCustomerQuery(
                "tenant123",
                "thread456",
                "Mi aplicación se cierra constantemente cuando intento abrir archivos PDF",
                state
        );
        
        // Obtener la respuesta
        String lastResponse = result.getChatMessages()
                .get(result.getChatMessages().size() - 1)
                .getContent();
        
        System.out.println("Respuesta: " + lastResponse);
    }
}
