# Framework Agentico VEYFLOW

*Created by:* 
[@VeyonDev:https://github.com/dpacheco2001]
Bienvenido a la documentación del Framework de VEYFLOW. Este framework está diseñado para facilitar la creación, orquestación y ejecución de flujos de trabajo complejos utilizando múltiples agentes inteligentes.

## Tabla de Contenidos
1. [Conceptos Fundamentales](#conceptos-fundamentales)
2. [Cómo Utilizarlo](#cómo-utilizarlo)
   - [Definición de Nodos](#definición-de-nodos)
   - [Definición de Flujos de Trabajo (Workflows)](#definición-de-flujos-de-trabajo-workflows)
   - [Gestión del Estado (AgentState)](#gestión-del-estado-agentstate)
   - [Uso de Herramientas (ToolService)](#uso-de-herramientas-toolservice)
3. [Cookbook (Inicio Rápido)](#cookbook-inicio-rápido)
   - [Ejemplo 1: Agente Conversacional Simple](#ejemplo-1-agente-conversacional-simple)
   - [Ejemplo 2: Arquitectura con Router](#ejemplo-2-arquitectura-con-router)
   - [Ejemplo 3: Arquitectura Lineal](#ejemplo-3-arquitectura-lineal)

## Conceptos Fundamentales

El framework se basa en varios componentes clave que interactúan para ejecutar flujos de trabajo de agentes:

- **`AgentNode` (Nodo de Agente):**
  - La unidad fundamental de procesamiento en un flujo de trabajo.
  - Cada nodo representa una tarea o una etapa específica que un agente (o un sistema) debe realizar.
  - Implementa la interfaz `AgentNode` y su método principal `process(AgentState state)`.
  - Ejemplos: `ModelNode` (para interactuar con LLMs), `ToolNode` (para ejecutar herramientas), `CodeExecutionNode` (para ejecutar código personalizado).

- **`AgentWorkflow` (Flujo de Trabajo del Agente):**
  - Define la secuencia y la lógica de cómo los `AgentNode`s se conectan y operan.
  - Permite agregar nodos y definir el enrutamiento entre ellos.
  - Utiliza un `AgentExecutor` interno para gestionar la ejecución paso a paso del flujo.
  - Puede ser "compilado" a un `CompiledWorkflow` para optimización y validación.

- **`NodeRouter` (Enrutador de Nodos):**
  - Determina el siguiente `AgentNode` a ejecutar basado en el estado actual (`AgentState`) o en una lógica predefinida.
  - Implementa la interfaz `NodeRouter` y su método `route(AgentState state)`.
  - Ejemplos: `LinearRouter` (para secuencias lineales), `ConditionalRouter` (para ramificaciones basadas en condiciones), `StaticRouter` (para un destino fijo).

- **`AgentState` (Estado del Agente):**
  - Un objeto contenedor que transporta datos a través de los diferentes nodos de un `AgentWorkflow`.
  - Mantiene el historial de la conversación (`List<ChatMessage>`), variables personalizadas (`Map<String, Object> customData`), y otros metadatos relevantes para el flujo.
  - Es mutable y se actualiza a medida que cada nodo procesa la información.

- **`ChatMessage` (Mensaje de Chat):**
  - Representa un único turno en una conversación.
  - Contiene el `role` (USER, ASSISTANT, SYSTEM, TOOL), el `content` (texto del mensaje), y opcionalmente `tool_calls` o `tool_call_id`.

- **`FoundationModelService` (Servicio de Modelo Fundacional):**
  - Abstracción para interactuar con diferentes Modelos de Lenguaje Grandes (LLMs) como OpenAI, Gemini, etc.
  - Proporciona una interfaz unificada para enviar solicitudes y recibir respuestas de los modelos.
  - Se utiliza principalmente dentro de `ModelNode`.

- **`ToolService` (Servicio de Herramientas):**
  - Define una interfaz para herramientas que los agentes pueden invocar.
  - Cada herramienta expone métodos que pueden ser anotados con `@ToolMethod` para que el framework los descubra y los haga disponibles para los `ModelNode` configurados para usar herramientas.

- **`AgentFrameworkService` (Servicio del Framework de Agentes):**
  - Un servicio de alto nivel que simplifica la creación y ejecución de flujos de trabajo comunes.
  - Proporciona métodos de conveniencia para construir nodos y flujos de trabajo sin necesidad de configurar cada componente manualmente.

- **`AgentExecutor` (Ejecutor de Agentes):**
  - El motor que realmente ejecuta un `AgentWorkflow` o `CompiledWorkflow`.
  - Gestiona el ciclo de vida de la ejecución, pasando el `AgentState` de un nodo a otro según la lógica del `NodeRouter`.

- **`CompiledWorkflow` (Flujo de Trabajo Compilado):**
  - Una representación optimizada e inmutable de un `AgentWorkflow`.
  - La compilación puede validar la estructura del grafo (ej. detectar ciclos, nodos desconectados) y optimizar las rutas de ejecución.

## Cómo Utilizarlo

A continuación, se describe cómo configurar y utilizar los componentes principales del framework.

### Definición de Nodos

Los nodos son el corazón de cualquier flujo de trabajo. Representan unidades de trabajo discretas.

- **Implementación Básica:**
  Cualquier clase que implemente la interfaz `com.viyon.closer.utils.agentFramework.core.AgentNode` puede actuar como un nodo.
  El método clave es `AgentState process(AgentState state)`, que toma el estado actual, realiza su lógica y devuelve el estado modificado (o uno nuevo).

- **`ModelNode`:**
  Es el nodo más común para interactuar con Modelos de Lenguaje Grandes (LLMs).
  ```java
  // Necesitarás un FoundationModelService (ej. OpenAIModelService, GeminiModelService)
  FoundationModelService modelService = new OpenAIModelService("tu_api_key");
  
  // Parámetros del modelo
  ModelParameters modelParams = new ModelParameters.Builder()
      .temperature(0.7f)
      .maxOutputTokens(150)
      .build();

  // Lista de herramientas (puede estar vacía)
  List<ToolService> tools = new ArrayList<>(); 
  // tools.add(new MyCustomToolService());

  ModelNode myModelNode = new ModelNode(
      "nombreDelNodo",        // Nombre único para el nodo
      modelService,           // Servicio del modelo (OpenAI, Gemini, etc.)
      "gpt-4.1-mini",       // Nombre/ID del modelo específico
      modelParams,            // Parámetros de generación
      "Eres un asistente útil.", // Prompt del sistema o instrucción base
      tools                   // Lista de ToolService disponibles para este nodo
  );
  ```

- **Nodos Personalizados:**
  Puedes crear tus propios nodos para cualquier tarea, como llamar a una API externa, procesar datos, o ejecutar lógica de negocio específica.
  ```java
  public class MyCustomNode implements AgentNode {
      private final String nodeName;

      public MyCustomNode(String nodeName) {
          this.nodeName = nodeName;
      }

      @Override
      public AgentState process(AgentState state) {
          System.out.println("Ejecutando lógica personalizada en: " + nodeName);
          // Modificar el estado, por ejemplo, añadiendo datos personalizados
          state.setCustomData("myCustomNodeOutput", "Resultado de " + nodeName);
          // Añadir un mensaje al historial de chat
          state.addChatMessage(ChatMessage.builder()
                               .role(ChatMessage.Role.SYSTEM)
                               .content("MyCustomNode ha completado su tarea.")
                               .build());
          return state;
      }

      @Override
      public String getName() {
          return nodeName;
      }
  }
  ```

### Definición de Flujos de Trabajo (Workflows)

Un `AgentWorkflow` define cómo se conectan y ejecutan los nodos.

```java
import com.viyon.closer.utils.agentFramework.core.AgentWorkflow;
import com.viyon.closer.utils.agentFramework.routing.LinearRouter;
import com.viyon.closer.utils.agentFramework.routing.ConditionalRouter;
import com.viyon.closer.utils.agentFramework.core.AgentNode;
import com.viyon.closer.utils.agentFramework.state.AgentState;
import com.viyon.closer.utils.agentFramework.state.ChatMessage;

// Definición de nodos simples para el ejemplo de workflow:
AgentNode node1 = new AgentNode() {
    @Override public String getName() { return "node1_initial"; }
    @Override public AgentState process(AgentState state) {
        System.out.println("Ejecutando Node1: Nodo Inicial");
        state.addChatMessage(ChatMessage.system("Node1 ejecutado."));
        return state;
    }
};

AgentNode node2 = new AgentNode() {
    @Override public String getName() { return "node2_processing"; }
    @Override public AgentState process(AgentState state) {
        System.out.println("Ejecutando Node2: Nodo de Procesamiento");
        state.addChatMessage(ChatMessage.system("Node2 ejecutado."));
        // Aquí podrías añadir lógica de procesamiento real
        return state;
    }
};

AgentNode node3 = new AgentNode() {
    @Override public String getName() { return "node3_final"; }
    @Override public AgentState process(AgentState state) {
        System.out.println("Ejecutando Node3: Nodo Final");
        state.addChatMessage(ChatMessage.system("Node3 ejecutado."));
        return state;
    }
};

AgentWorkflow workflow = new AgentWorkflow();

// 1. Añadir nodos al workflow
workflow.addNode(node1);
workflow.addNode(node2);
workflow.addNode(node3);

// 2. Establecer el nodo de entrada
workflow.setEntryNode(node1.getName());

// 3. Definir el enrutamiento
// Ejemplo de enrutamiento lineal: node1 -> node2 -> node3
workflow.addRouter(node1.getName(), new LinearRouter(node2.getName()));
workflow.addRouter(node2.getName(), new LinearRouter(node3.getName()));
// El último nodo no necesita un router si es el final del flujo.

// Ejemplo de enrutamiento condicional desde node1:
ConditionalRouter conditionalRouter = new ConditionalRouter("defaultTargetNodeName"); // Nodo por defecto
conditionalRouter.addCondition(
    (state) -> state.getCustomData("someKey") != null && state.getCustomData("someKey").equals("someValue"),
    node2.getName() // Si la condición es verdadera, ir a node2
);
conditionalRouter.addCondition(
    (state) -> state.getLastMessageContent().toLowerCase().contains("ayuda"),
    node3.getName() // Si la condición es verdadera, ir a node3
);
// workflow.addRouter(node1.getName(), conditionalRouter);

// 4. (Opcional) Compilar el workflow para validación y optimización
// CompiledWorkflow compiledWorkflow = workflow.compile();

// 5. Ejecutar el workflow
AgentState initialState = new AgentState();
initialState.addChatMessage(ChatMessage.user("Hola agente."));

// AgentState finalState = workflow.execute(initialState); // Si no se compila
// AgentState finalState = compiledWorkflow.execute(initialState); // Si se compila
```

### Gestión del Estado (`AgentState`)

El `AgentState` es el portador de datos que fluye a través del workflow.

- **Historial de Chat:**
  - `state.getChatMessages()`: Obtiene la lista de `ChatMessage`.
  - `state.addChatMessage(ChatMessage message)`: Añade un nuevo mensaje al historial.
  - `state.getLastMessage()`: Obtiene el último mensaje.
  - `state.getLastMessageContent()`: Obtiene el contenido del último mensaje.

  ```java
  // Añadir un mensaje de usuario
  state.addChatMessage(ChatMessage.user("¿Cuál es el clima hoy?"));

  // Añadir un mensaje del asistente
  state.addChatMessage(ChatMessage.assistant("El clima es soleado."));
  ```

- **Datos Personalizados:**
  Un `Map<String, Object>` para almacenar cualquier dato relevante para el flujo.
  - `state.setCustomData(String key, Object value)`: Almacena un dato.
  - `state.getCustomData(String key)`: Recupera un dato.
  - `state.getCustomData()`: Obtiene todo el mapa de datos personalizados.

  ```java
  state.setCustomData("userId", "user123");
  String userId = (String) state.getCustomData("userId");
  ```

- **ID de Conversación:**
  - `state.getConversationId()`: Útil para rastrear o agrupar interacciones.

### Uso de Herramientas (`ToolService`)

Los agentes (especialmente los `ModelNode`) pueden invocar herramientas para interactuar con el mundo exterior o realizar tareas específicas.

1.  **Crear un Servicio de Herramientas:**
    Implementa la interfaz `ToolService` y anota los métodos que quieres exponer al LLM con `@ToolMethod`.

    ```java
    import com.viyon.closer.utils.agentFramework.tools.ToolService;
    import com.viyon.closer.utils.agentFramework.tools.ToolMethod;
    import com.google.gson.JsonObject; // Para parámetros complejos si es necesario

    public class WeatherToolService implements ToolService {

        @Override
        public String getServiceName() {
            return "WeatherService"; // Nombre único para el servicio
        }

        @ToolMethod(description = "Obtiene el clima actual para una ciudad específica.")
        public String getCurrentWeather(String city, String unit) {
            // Lógica para obtener el clima...
            if ("Celcius".equalsIgnoreCase(unit)) {
                return "El clima en " + city + " es 25°C.";
            }
            return "El clima en " + city + " es 77°F.";
        }

        // Ejemplo de un método que podría tomar un JSON como parámetro
        @ToolMethod(description = "Establece un recordatorio.")
        public String setReminder(JsonObject reminderDetails) {
            // reminderDetails podría tener campos como "text", "time", "date"
            // String text = reminderDetails.get("text").getAsString();
            // ... lógica para establecer el recordatorio ...
            return "Recordatorio establecido: " + reminderDetails.toString();
        }
    }
    ```

2.  **Configurar `ModelNode` para Usar Herramientas:**
    Pasa una lista de tus `ToolService` al constructor de `ModelNode`.

    ```java
    List<ToolService> agentTools = new ArrayList<>();
    agentTools.add(new WeatherToolService());
    // agentTools.add(new CalendarToolService());

    ModelNode modelNodeWithTools = new ModelNode(
        "agentWithTools",
        modelService,
        "gpt-4-turbo-preview", // Un modelo que soporte llamadas a funciones/herramientas
        modelParams,
        "Eres un asistente que puede usar herramientas para responder preguntas.",
        agentTools,
        ToolCallProcessor.DetectionMode.AUTO, // O JSON_MODE si el modelo siempre devuelve un JSON estructurado para llamadas a herramientas
        3 // maxToolCallIterations: número máximo de rondas de llamadas a herramientas por `process`
    );
    ```

    El `ModelNode` internamente usará un `ToolCallProcessor` para:
    -   Presentar las herramientas disponibles al LLM.
    -   Detectar solicitudes de llamada a herramientas en la respuesta del LLM.
    -   Ejecutar las herramientas solicitadas.
    -   Enviar los resultados de vuelta al LLM para que genere una respuesta final.

## Cookbook (Inicio Rápido)

Esta sección proporciona ejemplos prácticos para comenzar a construir agentes rápidamente.

### Ejemplo 1: Agente Conversacional Simple

Este ejemplo muestra cómo crear un agente básico que recibe un mensaje del usuario y responde utilizando un `ModelNode`.

**Objetivo:** Crear un chatbot simple que responda a un saludo.

**Pasos:**

1.  **Dependencias (Maven POM conceptual):**
    Asegúrate de tener las dependencias necesarias. Si estás usando Maven, tu `pom.xml` podría incluir algo como:
    ```xml
    <!-- Reemplaza con las versiones actuales del framework -->
    <dependency>
        <groupId>com.viyon.closer</groupId>
        <artifactId>agent-framework</artifactId>
        <version>0.0.1-SNAPSHOT</version>
    </dependency>
    <!-- Y las dependencias para los servicios de modelos que uses (ej. OpenAI) -->
    <dependency>
        <groupId>com.viyon.closer</groupId>
        <artifactId>openai-model-service</artifactId> <!-- o el módulo específico -->
        <version>0.0.1-SNAPSHOT</version>
    </dependency>
    ```

2.  **Configurar el `ModelNode`:**
    ```java
    import com.viyon.closer.utils.agentFramework.core.AgentNode;
    import com.viyon.closer.utils.agentFramework.nodes.ModelNode;
    import com.viyon.closer.utils.agentFramework.foundationmodels.FoundationModelService;
    import com.viyon.closer.utils.agentFramework.foundationmodels.OpenAIModelService; // Ejemplo
    import com.viyon.closer.utils.agentFramework.foundationmodels.ModelParameters;
    import com.viyon.closer.utils.agentFramework.state.AgentState;
    import com.viyon.closer.utils.agentFramework.state.ChatMessage;
    import com.viyon.closer.utils.agentFramework.core.AgentWorkflow;
    import java.util.Collections;

    public class SimpleChatbotExample {

        public static void main(String[] args) {
            // 1. Configurar el servicio del modelo (ej. OpenAI)
            // ¡Asegúrate de gestionar tu API key de forma segura!
            FoundationModelService openAIService = new OpenAIModelService(System.getenv("OPENAI_API_KEY"));

            // 2. Definir los parámetros del modelo
            ModelParameters chatParams = new ModelParameters.Builder()
                .temperature(0.7f)
                .maxOutputTokens(100)
                .build();

            // 3. Crear el ModelNode
            ModelNode chatAgentNode = new ModelNode(
                "chatbotNode",
                openAIService,
                "gpt-3.5-turbo", // Modelo específico
                chatParams,
                "Eres un amigable chatbot asistente.",
                Collections.emptyList() // Sin herramientas para este ejemplo simple
            );

            // 4. Crear el Workflow
            AgentWorkflow workflow = new AgentWorkflow();
            workflow.addNode(chatAgentNode);
            workflow.setEntryNode(chatAgentNode.getName());
            // No se necesita router para un solo nodo.

            // 5. Preparar el estado inicial
            AgentState initialState = new AgentState();
            String userInput = "Hola, ¿cómo estás?";
            initialState.addChatMessage(ChatMessage.user(userInput));
            System.out.println("Usuario: " + userInput);

            // 6. Ejecutar el workflow
            AgentState finalState = workflow.execute(initialState);

            // 7. Mostrar la respuesta del agente
            ChatMessage agentResponse = finalState.getLastMessage();
            if (agentResponse != null && agentResponse.getRole() == ChatMessage.Role.ASSISTANT) {
                System.out.println("Agente: " + agentResponse.getContent());
            }
        }
    }
    ```

3.  **Ejecutar:**
    Al ejecutar `SimpleChatbotExample`, el `ModelNode` procesará el mensaje del usuario y generará una respuesta basada en su prompt de sistema y la entrada del usuario.

**Puntos Clave:**

-   **`AgentState`**: Se utiliza para pasar el mensaje inicial del usuario al `ModelNode` y para recuperar la respuesta del agente.
-   **`ModelNode`**: Maneja toda la interacción con el LLM.
-   **`AgentWorkflow`**: Aunque simple (un solo nodo), sigue siendo el mecanismo para ejecutar el agente.
-   **`ChatMessage.user(...)` y `ChatMessage.assistant(...)`**: Para estructurar la conversación.

Este ejemplo sienta las bases para interacciones más complejas. En los siguientes ejemplos, veremos cómo conectar múltiples nodos y usar enrutadores.

### Ejemplo 2: Arquitectura con Router

Este ejemplo demuestra cómo utilizar un `ConditionalRouter` para dirigir el flujo de trabajo a diferentes agentes (nodos) según una condición en el `AgentState`. Por ejemplo, si el usuario pregunta sobre el clima, se dirige a un agente especializado en clima; si pregunta sobre tareas, a un agente de productividad.

**Objetivo:** Crear un agente principal (router) que delegue a un `WeatherAgentNode` o a un `TaskAgentNode`.

**Pasos:**

1.  **Definir los Nodos Especializados y Servicios Comunes:**
    Primero, configuramos los servicios y parámetros que usarán nuestros nodos, similar al Ejemplo 1. Luego, definimos los `ModelNode`s especializados.

    ```java
    import com.viyon.closer.utils.agentFramework.core.AgentNode;
    import com.viyon.closer.utils.agentFramework.nodes.ModelNode;
    import com.viyon.closer.utils.agentFramework.foundationmodels.FoundationModelService;
    import com.viyon.closer.utils.agentFramework.foundationmodels.OpenAIModelService;
    import com.viyon.closer.utils.agentFramework.foundationmodels.ModelParameters;
    import com.viyon.closer.utils.agentFramework.state.AgentState;
    import com.viyon.closer.utils.agentFramework.state.ChatMessage;
    import com.viyon.closer.utils.agentFramework.core.AgentWorkflow;
    import com.viyon.closer.utils.agentFramework.routing.ConditionalRouter;
    import java.util.Collections;
    import java.util.List; // Necesario para Collections.emptyList()

    // Asumimos estas inicializaciones como en el Ejemplo 1 o adaptadas:
    // FoundationModelService openAIService = new OpenAIModelService(System.getenv("OPENAI_API_KEY"));
    // ModelParameters chatParams = new ModelParameters.Builder().temperature(0.7f).maxOutputTokens(100).build();

    public class RouterAgentExample {
        // Definimos openAIService y chatParams aquí para claridad en este ejemplo
        static FoundationModelService openAIService = new OpenAIModelService(System.getenv("OPENAI_API_KEY_IF_YOU_HAVE_ONE_ELSE_PLAIN_TEXT_KEY"));
        static ModelParameters chatParams = new ModelParameters.Builder().temperature(0.7f).maxOutputTokens(150).build();

        // Nodo para consultas de clima
        static ModelNode weatherAgentNode = new ModelNode(
            "weatherAgentNode",
            openAIService,
            "gpt-3.5-turbo",
            chatParams,
            "Eres un experto en clima. Responde solo a preguntas sobre el clima concisamente.",
            Collections.emptyList()
        );

        // Nodo para consultas de tareas
        static ModelNode taskAgentNode = new ModelNode(
            "taskAgentNode",
            openAIService,
            "gpt-3.5-turbo",
            chatParams,
            "Eres un experto en gestión de tareas. Responde solo a preguntas sobre tareas o productividad de forma breve.",
            Collections.emptyList()
        );

        // Nodo por defecto si ninguna condición se cumple
        static ModelNode fallbackAgentNode = new ModelNode(
            "fallbackAgentNode",
            openAIService,
            "gpt-3.5-turbo",
            chatParams,
            "Soy un asistente general. No estoy seguro de cómo manejar tu solicitud específica. Intenta reformular.",
            Collections.emptyList()
        );
    // ... el resto de la clase RouterAgentExample continúa abajo
    ```

2.  **Configurar el `ConditionalRouter` y el `AgentWorkflow`:**

    ```java
    // ... (continuación de la clase RouterAgentExample)
    public static void main(String[] args) {
        // Los nodos weatherAgentNode, taskAgentNode, fallbackAgentNode ya están definidos arriba como static.

        // Crear el router condicional
        ConditionalRouter router = new ConditionalRouter(fallbackAgentNode.getName()); // Nodo por defecto
{{ ... }}
        public static void testWorkflow(AgentWorkflow workflow, String userInput) {
            System.out.println("\n--- Probando con Input: '" + userInput + "' ---");
            AgentState initialState = new AgentState();
            // Es importante añadir el mensaje de usuario al AgentState ANTES de ejecutar el workflow
            // para que el ConditionalRouter pueda acceder a él mediante state.getLastMessageContent().
            initialState.addChatMessage(ChatMessage.user(userInput)); 
            // System.out.println("Usuario: " + userInput); // Esto ya se imprime en el loop de abajo

            AgentState finalState = workflow.execute(initialState);

            // Imprimir todos los mensajes para ver el flujo
            finalState.getChatMessages().forEach(msg -> 
                System.out.println("[" + msg.getRole() + "] " + (msg.getContent() == null ? "(Contenido nulo)" : msg.getContent())) 
            );
        }
    }
    ```

3.  **Ejecutar:**
{{ ... }}
```

### Ejemplo 3: Arquitectura Lineal

Este ejemplo muestra cómo crear un flujo de trabajo lineal donde cada nodo procesa la información y la pasa al siguiente nodo en la secuencia.

**Objetivo:** Crear un agente que reciba un mensaje del usuario, procese la información en un nodo, y luego pase el resultado a otro nodo para generar una respuesta final.

**Pasos:**

1.  **Definir los Nodos:**
    Supondremos que tenemos dos `ModelNode`s: uno para procesar la entrada del usuario y otro para generar la respuesta final.

    ```java
    // Asumimos que openAIService y chatParams están definidos como en el Ejemplo 1

    // Nodo para procesar la entrada del usuario
    ModelNode processNode = new ModelNode(
        "processNode",
        openAIService,
        "gpt-3.5-turbo",
        chatParams,
        "Eres un procesador de entrada. Analiza la solicitud del usuario.",
        Collections.emptyList()
    );

    // Nodo para generar la respuesta final
    ModelNode responseNode = new ModelNode(
        "responseNode",
        openAIService,
        "gpt-3.5-turbo",
        chatParams,
        "Eres un generador de respuestas. Crea una respuesta basada en el resultado del procesamiento.",
        Collections.emptyList()
    );
    ```

2.  **Configurar el `AgentWorkflow`:**

    ```java
    import com.viyon.closer.utils.agentFramework.core.AgentWorkflow;
    import com.viyon.closer.utils.agentFramework.routing.LinearRouter;

    public class LinearAgentExample {
        public static void main(String[] args) {
            // ... (inicialización de openAIService, chatParams, processNode, responseNode) ...

            // Crear el Workflow
            AgentWorkflow workflow = new AgentWorkflow();

            // Añadir nodos al workflow
            workflow.addNode(processNode);
            workflow.addNode(responseNode);

            // Establecer el nodo de entrada
            workflow.setEntryNode(processNode.getName());

            // Definir el enrutamiento lineal
            workflow.addRouter(processNode.getName(), new LinearRouter(responseNode.getName()));

            // Probar el workflow
            testWorkflow(workflow, "¿Cómo estará el clima en Madrid mañana por la tarde?");
        }

        public static void testWorkflow(AgentWorkflow workflow, String userInput) {
            System.out.println("\n--- Probando con Input: '" + userInput + "' ---");
            AgentState initialState = new AgentState();
            initialState.addChatMessage(ChatMessage.user(userInput));
            System.out.println("Usuario: " + userInput);

            AgentState finalState = workflow.execute(initialState);

            // Imprimir todos los mensajes para ver el flujo
            finalState.getChatMessages().forEach(msg -> 
                System.out.println("[" + msg.getRole() + "] " + msg.getContent()) 
            );
        }
    }
    ```

3.  **Ejecutar:**
    Al ejecutar `LinearAgentExample`, el `processNode` primero analizará la entrada del usuario. Su salida se convierte en la entrada para el `responseNode`, que generará la respuesta final basada en el resultado del procesamiento.

**Puntos Clave:**

-   **`LinearRouter`**: Permite definir una secuencia de ejecución fija de un nodo al siguiente.
-   **Paso de Información a Través de `AgentState`**: La salida de un nodo se convierte en la entrada o contexto para el siguiente nodo en la cadena.
-   **Nodos Especializados para Etapas**: Cada `ModelNode` está configurado con un prompt de sistema para realizar una tarea específica en la secuencia de procesamiento (análisis, generación de respuesta).
-   **Flujo de Control Secuencial**: Ideal para tareas que requieren múltiples pasos de procesamiento en un orden definido.

Este patrón de flujo de trabajo lineal es útil para crear agentes que necesitan procesar la información de manera secuencial, como en pipelines de extracción-transformación-respuesta o análisis multi-etapa.

```

Este patrón de router es fundamental para construir agentes más complejos que pueden manejar una variedad de tareas o consultas delegando a sub-agentes o módulos especializados.

### Ejemplo 3: Arquitectura Lineal

Este ejemplo muestra cómo crear un flujo de trabajo lineal donde cada nodo procesa la información y la pasa al siguiente nodo en la secuencia usando un `LinearRouter`.

**Objetivo:** Crear un agente que reciba un mensaje del usuario, lo analice con un `processNode`, y luego pase el análisis a un `responseNode` para generar una respuesta final.

**Pasos:**

1.  **Definir los Nodos y Servicios Comunes:**
    Configuramos los servicios y parámetros, y luego definimos los `ModelNode`s para cada paso del proceso lineal.

    ```java
    import com.viyon.closer.utils.agentFramework.nodes.ModelNode;
    import com.viyon.closer.utils.agentFramework.foundationmodels.FoundationModelService;
    import com.viyon.closer.utils.agentFramework.foundationmodels.OpenAIModelService;
    import com.viyon.closer.utils.agentFramework.foundationmodels.ModelParameters;
    import com.viyon.closer.utils.agentFramework.state.AgentState;
    import com.viyon.closer.utils.agentFramework.state.ChatMessage;
    import com.viyon.closer.utils.agentFramework.core.AgentWorkflow;
    import com.viyon.closer.utils.agentFramework.routing.LinearRouter;
    import java.util.Collections;
    import java.util.List; // Necesario para Collections.emptyList()

    public class LinearAgentExample {
        // Definimos openAIService y chatParams aquí para claridad
        static FoundationModelService openAIService = new OpenAIModelService(System.getenv("OPENAI_API_KEY_IF_YOU_HAVE_ONE_ELSE_PLAIN_TEXT_KEY"));
        static ModelParameters chatParams = new ModelParameters.Builder().temperature(0.5f).maxOutputTokens(200).build();

        // Nodo para procesar la entrada del usuario y extraer la intención o datos clave
        static ModelNode processNode = new ModelNode(
            "processNode",
            openAIService,
            "gpt-3.5-turbo",
            chatParams,
            "Tu tarea es analizar la siguiente pregunta del usuario y resumirla en una sola frase concisa, identificando la intención principal. Por ejemplo, si el usuario pregunta '¿Cuál es el pronóstico del tiempo para París mañana?', tu salida debería ser algo como 'Intención: Consultar pronóstico del tiempo para París mañana.'. Responde ÚNICAMENTE con esta frase resumen.",
            Collections.emptyList()
        );

        // Nodo para generar la respuesta final basada en el análisis del processNode
        static ModelNode responseNode = new ModelNode(
            "responseNode",
            openAIService,
            "gpt-3.5-turbo",
            chatParams,
            "Has recibido un análisis de una pregunta de un usuario. Tu tarea es tomar este análisis y generar una respuesta amigable y útil. Si el análisis indica una consulta de clima, proporciona un pronóstico inventado. Si indica una tarea, confirma que se ha anotado. Para otras consultas, ofrece una respuesta general. El análisis es: ", // El contenido del mensaje anterior (del processNode) se añadirá aquí por el historial.
            Collections.emptyList()
        );
    // ... el resto de la clase LinearAgentExample continúa abajo
    ```

2.  **Configurar el `AgentWorkflow` con `LinearRouter`:**

    ```java
    // ... (continuación de la clase LinearAgentExample)
    public static void main(String[] args) {
        // Los nodos processNode y responseNode ya están definidos arriba como static.

        // Crear el Workflow
        AgentWorkflow workflow = new AgentWorkflow();

        // Añadir nodos al workflow
        workflow.addNode(processNode);
        workflow.addNode(responseNode);

        // Establecer el nodo de entrada
        workflow.setEntryNode(processNode.getName());

        // Definir el enrutamiento lineal: processNode -> responseNode
        workflow.addRouter(processNode.getName(), new LinearRouter(responseNode.getName()));
        // responseNode es el último, no necesita un router de salida en este flujo.

        // Probar el workflow
        testWorkflow(workflow, "¿Cómo estará el clima en Madrid mañana por la tarde?");
        testWorkflow(workflow, "Recuérdame llamar al dentista la próxima semana.");
    }

    public static void testWorkflow(AgentWorkflow workflow, String userInput) {
        System.out.println("\n--- Probando con Input: '" + userInput + "' ---");
        AgentState initialState = new AgentState();
        initialState.addChatMessage(ChatMessage.user(userInput));

        AgentState finalState = workflow.execute(initialState);

        // Imprimir todos los mensajes para ver el flujo y la respuesta final
        finalState.getChatMessages().forEach(msg -> 
            System.out.println("[" + msg.getRole() + "] " + (msg.getContent() == null ? "(Contenido nulo)" : msg.getContent()))
        );
    }
}
    ```

3.  **Ejecutar:**
    Al ejecutar `LinearAgentExample`, el `processNode` primero analizará la entrada del usuario. Su salida (el análisis) se añadirá al historial de chat en `AgentState`. Luego, el `LinearRouter` pasará el control al `responseNode`. El `responseNode`, utilizando su prompt de sistema y el historial de chat (que ahora incluye el análisis del `processNode`), generará la respuesta final.

**Puntos Clave:**

-   **`LinearRouter`**: Asegura una secuencia de ejecución fija de un nodo al siguiente.
-   **Paso de Información a Través de `AgentState`**: La salida de un nodo (añadida como un `ChatMessage` al `AgentState`) se convierte en la entrada o contexto para el siguiente nodo en la cadena.
-   **Nodos Especializados para Etapas**: Cada `ModelNode` está configurado con un prompt de sistema para realizar una tarea específica en la secuencia de procesamiento (análisis, generación de respuesta).
-   **Flujo de Control Secuencial**: Ideal para tareas que requieren múltiples pasos de procesamiento en un orden definido.

Este patrón de flujo de trabajo lineal es útil para crear agentes que necesitan procesar la información de manera secuencial, como en pipelines de extracción-transformación-respuesta o análisis multi-etapa.
{{ ... }}

```

## Personalización y Enrutamiento Avanzado

Esta sección cubre cómo extender la funcionalidad básica del framework creando tus propios nodos y definiendo lógicas de enrutamiento más complejas.

### Creación de Nodos Personalizados

Si bien `ModelNode` es versátil para interactuar con LLMs, a menudo necesitarás nodos que realicen tareas específicas, como llamar a APIs externas, procesar datos de formas particulares, o añadir lógica de pre/post-procesamiento alrededor de una llamada a un LLM. Puedes lograr esto implementando la interfaz `AgentNode` directamente o creando un nodo que utilice un `ModelNode` internamente (composición).

**1. Implementando `AgentNode` Directamente**

Cualquier clase que implemente `AgentNode` debe proporcionar un nombre y un método `process`.

```java
import com.viyon.closer.utils.agentFramework.core.AgentNode;
import com.viyon.closer.utils.agentFramework.state.AgentState;
import com.viyon.closer.utils.agentFramework.state.ChatMessage;
// Asume que tienes una clase de servicio, por ejemplo, para obtener datos de usuario
// import com.example.services.UserProfileService;

public class UserProfileNode implements AgentNode {
    private final String nodeName;
    // private final UserProfileService userProfileService; // Ejemplo de servicio inyectado

    public UserProfileNode(String nodeName /*, UserProfileService userProfileService */) {
        this.nodeName = nodeName;
        // this.userProfileService = userProfileService;
    }

    @Override
    public String getName() {
        return nodeName;
    }

    @Override
    public AgentState process(AgentState state) {
        System.out.println("Ejecutando " + nodeName);
        // Lógica personalizada: por ejemplo, obtener un ID de usuario del estado
        String userId = (String) state.get("userId"); // Asume que "userId" fue puesto en el estado previamente

        if (userId != null) {
            // String userProfileData = userProfileService.fetchProfile(userId); // Llama a un servicio externo
            String userProfileData = "Datos de perfil simulados para el usuario: " + userId; // Simulación
            state.put("userProfile", userProfileData); // Añade nuevos datos al estado
            state.addChatMessage(ChatMessage.system("Perfil de usuario recuperado para " + userId));
        } else {
            state.addChatMessage(ChatMessage.system("No se encontró userId en el estado para UserProfileNode."));
        }
        return state;
    }
}

// Uso en un workflow:
// UserProfileNode customNode = new UserProfileNode("fetchUserProfile");
// workflow.addNode(customNode);
// ... configurar enrutamiento hacia/desde este nodo
```

**2. Componiendo (Envolviendo) un `ModelNode` para Lógica Adicional**

Si quieres ejecutar lógica antes o después de una interacción con un LLM, puedes crear un nodo personalizado que contenga una instancia de `ModelNode`.

```java
import com.viyon.closer.utils.agentFramework.nodes.ModelNode;
// ... otras importaciones necesarias ...

public class EnhancedModelNode implements AgentNode {
    private final String nodeName;
    private final ModelNode internalModelNode;
    // Otros servicios o configuraciones que necesites

    public EnhancedModelNode(String nodeName, ModelNode internalModelNode) {
        this.nodeName = nodeName;
        this.internalModelNode = internalModelNode;
    }

    @Override
    public String getName() {
        return nodeName;
    }

    @Override
    public AgentState process(AgentState state) {
        System.out.println("Ejecutando pre-procesamiento en " + nodeName);
        // Lógica de pre-procesamiento: modificar el estado, preparar la entrada para el LLM
        String originalUserInput = state.getLastMessageContent();
        state.addChatMessage(ChatMessage.user("Contexto adicional para el LLM: " + originalUserInput));

        // Ejecutar el ModelNode interno
        AgentState stateAfterModel = internalModelNode.process(state);

        System.out.println("Ejecutando post-procesamiento en " + nodeName);
        // Lógica de post-procesamiento: analizar la respuesta del LLM, formatearla, etc.
        ChatMessage llmResponse = stateAfterModel.getLastMessage();
        if (llmResponse != null && llmResponse.getRole() == ChatMessage.Role.ASSISTANT) {
            String processedContent = llmResponse.getContent().toUpperCase(); // Ejemplo: convertir a mayúsculas
            // Reemplazar el último mensaje con el contenido procesado o añadir uno nuevo
            stateAfterModel.addChatMessage(ChatMessage.assistant(processedContent, llmResponse.getToolCalls(), true)); // Asume que quieres sobreescribir
            stateAfterModel.addChatMessage(ChatMessage.system("Respuesta del LLM procesada por " + nodeName));
        }
        return stateAfterModel;
    }
}

// Uso:
// FoundationModelService service = ...;
// ModelParameters params = ...;
// ModelNode coreLLMNode = new ModelNode("coreLLM", service, "gpt-3.5-turbo", params, "Prompt...", Collections.emptyList());
// EnhancedModelNode enhancedNode = new EnhancedModelNode("enhancedLLMInteraction", coreLLMNode);
// workflow.addNode(enhancedNode);
```

Estos nodos personalizados se pueden integrar en `AgentWorkflow` como cualquier otro nodo, permitiendo una gran flexibilidad en el diseño de tus agentes.

```

Follow these instructions to make the following change to my code document.

Instruction: Append a new subsection 'Definición de Lógica en ConditionalRouter' to the 'Personalización y Enrutamiento Avanzado' section. This subsection should explain how to use Predicate<AgentState> to define routing conditions.

Code Edit:
```
{{ ... }}
Estos nodos personalizados se pueden integrar en `AgentWorkflow` como cualquier otro nodo, permitiendo una gran flexibilidad en el diseño de tus agentes.

### Definición de Lógica en `ConditionalRouter`

El `ConditionalRouter` (mencionado en el "Ejemplo 2: Arquitectura con Router") permite dirigir el flujo de un `AgentWorkflow` a diferentes nodos basándose en el estado actual del `AgentState`. La clave para esto es el uso de `java.util.function.Predicate<AgentState>`.

**¿Qué es un `Predicate<AgentState>`?**

Un `Predicate` es una interfaz funcional que representa una condición (una función que devuelve `true` o `false`). En el contexto de `ConditionalRouter`, un `Predicate<AgentState>` es una función que toma el `AgentState` actual como entrada y devuelve `true` si se cumple una condición específica, o `false` en caso contrario.

**Cómo Funciona:**

1.  El `ConditionalRouter` se inicializa con un nombre de nodo de destino por defecto. Este es el nodo al que se enrutará si ninguna de las condiciones personalizadas se cumple.
    ```java
    ConditionalRouter router = new ConditionalRouter("defaultTargetNodeName");
    ```

2.  Se añaden condiciones al router usando el método `addCondition(Predicate<AgentState> condition, String targetNodeName)`.
    -   El primer argumento es el `Predicate` que define la lógica de la condición.
    -   El segundo argumento es el nombre del nodo al que se debe enrutar si el `Predicate` evalúa a `true`.

3.  Cuando el `AgentWorkflow` llega a un nodo que tiene este `ConditionalRouter` asociado, el router evalúa cada `Predicate` en el orden en que fueron añadidos.
    -   Si un `Predicate` devuelve `true`, el flujo se dirige inmediatamente al `targetNodeName` asociado con ese `Predicate`.
    -   Si ningún `Predicate` devuelve `true`, el flujo se dirige al `defaultTargetNodeName`.

**Ejemplos de Definición de `Predicate<AgentState>`:**

Los predicados se pueden definir usando expresiones lambda, lo que hace el código conciso.

```java
import com.viyon.closer.utils.agentFramework.state.AgentState;
import com.viyon.closer.utils.agentFramework.state.ChatMessage;
import com.viyon.closer.utils.agentFramework.routing.ConditionalRouter;
// ... otros nodos (weatherNode, taskNode, generalQueryNode, fallbackNode) definidos como en ejemplos anteriores ...

// Supongamos que tenemos los siguientes nombres de nodos:
// String weatherNodeName = weatherNode.getName();
// String taskNodeName = taskNode.getName();
// String generalQueryNodeName = generalQueryNode.getName();
// String fallbackNodeName = fallbackNode.getName();

ConditionalRouter decisionRouter = new ConditionalRouter("fallbackNodeName"); // Nodo por defecto

// Condición 1: Basada en el contenido del último mensaje del usuario
decisionRouter.addCondition(
    (state) -> {
        ChatMessage lastMessage = state.getLastMessage();
        if (lastMessage != null && lastMessage.getRole() == ChatMessage.Role.USER) {
            String content = lastMessage.getContent().toLowerCase();
            return content.contains("clima") || content.contains("pronóstico");
        }
        return false;
    },
    "weatherNodeName"
);

// Condición 2: Basada en la presencia de un dato específico en el AgentState
decisionRouter.addCondition(
    (state) -> state.containsKey("requiresUrgentTaskHandling"), // Asume que otro nodo pudo haber puesto esta clave
    "taskNodeName"
);

// Condición 3: Basada en la longitud del historial de chat (ejemplo más abstracto)
decisionRouter.addCondition(
    (state) -> state.getChatMessages().size() > 10, // Si la conversación es larga, quizás ir a un nodo de resumen
    "summaryNodeName" // Asume que existe un 'summaryNodeName'
);

// Condición 4: Enrutamiento basado en el tipo del último mensaje del modelo (si usa herramientas, por ejemplo)
decisionRouter.addCondition(
    (state) -> {
        ChatMessage lastMessage = state.getLastMessage();
        // Si el último mensaje es del asistente Y contiene llamadas a herramientas
        return lastMessage != null && 
               lastMessage.getRole() == ChatMessage.Role.ASSISTANT && 
               lastMessage.getToolCalls() != null && 
               !lastMessage.getToolCalls().isEmpty();
    },
    "toolExecutorNodeName" // Un nodo que maneja la ejecución de herramientas
);

// Uso en un workflow:
// AgentNode entryPointNode = ... ;
// workflow.addRouter(entryPointNode.getName(), decisionRouter);
```

**Consideraciones:**

-   **Orden de las Condiciones:** Las condiciones se evalúan en el orden en que se añaden. La primera que resulte `true` determinará la ruta. Diseña el orden cuidadosamente si las condiciones pueden solaparse.
-   **Claridad del `AgentState`:** La efectividad de tus predicados depende de qué tan bien estructurado y predecible sea tu `AgentState`. Asegúrate de que los nodos anteriores coloquen la información necesaria en el estado de manera consistente.
-   **Complejidad del Predicado:** Si bien puedes hacer predicados complejos, busca un equilibrio. Si un predicado se vuelve demasiado complicado, podría ser una señal de que la lógica debería estar dentro de un nodo personalizado previo que simplifique el estado para el router.

El `ConditionalRouter` es una herramienta poderosa para crear flujos de trabajo dinámicos y adaptativos en tus agentes.
