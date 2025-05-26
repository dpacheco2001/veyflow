# Veyflow: Intelligent Agent and AI Workflow Framework

**Veyflow** es un framework robusto y flexible diseñado para la creación de agentes inteligentes y flujos de trabajo complejos impulsados por Inteligencia Artificial. Construido sobre Spring Boot, Veyflow facilita la modularidad, la extensibilidad y la integración con diversos modelos de lenguaje (LLMs) y herramientas externas.

## Tabla de Contenidos
1. [Descripción General](#descripción-general)
2. [Características Principales](#características-principales)
3. [Prerrequisitos](#prerrequisitos)
4. [Instalación](#instalación)
5. [Conceptos Fundamentales del Core](#conceptos-fundamentales-del-core)
   - [AgentState](#agentstate)
   - [WorkflowConfig](#workflowconfig)
   - [Nodos (AgentNode)](#nodos-agentnode)
   - [Workflows (AgentWorkflow) y Edges](#workflows-agentworkflow-y-edges)
   - [Procesadores de IA (LLM y ToolAgent)](#procesadores-de-ia-llm-y-toolagent)
   - [Herramientas (Tools)](#herramientas-tools)
6. [Cómo Empezar: Un Ejemplo Básico](#cómo-empezar-un-ejemplo-básico)
7. [Creación de Nodos Personalizados](#creación-de-nodos-personalizados)
8. [Gestión del Estado (AgentState)](#gestión-del-estado-agentstate)
9. [Configuración de Workflows (WorkflowConfig)](#configuración-de-workflows-workflowconfig)
10. [Integración y Uso de Herramientas](#integración-y-uso-de-herramientas)
11. [Flujo de Control: Routers](#flujo-de-control-routers)
12. [Definición, Compilación y Ejecución de Workflows](#definición-compilación-y-ejecución-de-workflows)
13. [Ejecución de Pruebas](#ejecución-de-pruebas)
14. [Contribuciones](#contribuciones)
15. [Licencia](#licencia)

## Descripción General

Veyflow tiene como objetivo simplificar el desarrollo de aplicaciones basadas en LLMs, permitiendo a los desarrolladores construir flujos de trabajo sofisticados donde múltiples agentes y herramientas pueden colaborar para resolver tareas complejas. Proporciona una abstracción sobre la gestión del estado, la configuración de la persistencia y la orquestación de diferentes componentes de IA.

## Características Principales

- **Basado en Spring Boot**: Aprovecha el ecosistema de Spring para la gestión de dependencias, configuración y otras características empresariales.
- **Modular y Extensible**: Diseñado para permitir la fácil adición de nuevos nodos, agentes, herramientas y modelos de IA.
- **Gestión de Estado Flexible**: Soporte para persistencia en memoria y Redis para `AgentState` y `WorkflowConfig`.
- **Integración de Herramientas**: Permite a los agentes invocar herramientas externas (APIs, servicios, etc.) de forma estructurada.
- **Orquestación de Workflows**: Define flujos de trabajo como una secuencia o grafo de nodos, cada uno realizando una tarea específica.
- **Soporte Multi-Modelo**: Facilita la integración con diferentes proveedores de LLMs (ej. OpenAI, Gemini).

## Prerrequisitos

- **Java Development Kit (JDK)**: Versión 17 o superior.
- **Apache Maven**: Para la gestión de dependencias y la compilación del proyecto.
- **Redis (Opcional)**: Necesario si se desea utilizar la persistencia basada en Redis para `AgentState` y `WorkflowConfig`.

## Instalación

1.  **Clonar el repositorio :**
    ```bash
    git clone https://github.com/dpacheco2001/veyflow.git
    cd veyflow
    ```
2.  **Construir el proyecto con Maven:**
    Desde la raíz del directorio del proyecto, ejecuta:
    ```bash
    mvn clean install
    ```
    Esto compilará el código, ejecutará las pruebas y empaquetará el framework en un archivo JAR, instalándolo en tu repositorio Maven local.

## Conceptos Fundamentales del Core

Entender los siguientes componentes es crucial para trabajar con Veyflow:

### AgentState
Representa el estado de un agente en un momento dado durante la ejecución de un workflow. **La correcta gestión de sus identificadores (`tenantId`, `threadId`) por parte del desarrollador es crucial para la seguridad, el aislamiento de datos y el seguimiento de conversaciones.** Incluye:
-   **`tenantId` (String)**: Identificador único asignado a un cliente, usuario o aplicación específica. Es **fundamental para la multi-tenancy** (múltiples inquilinos), ya que permite aislar los datos y estados de diferentes inquilinos que utilizan la misma instancia de Veyflow. Todas las operaciones de persistencia (guardado y carga de `AgentState` y `WorkflowConfig`) se segmentan utilizando este `tenantId` para garantizar la privacidad y la organización de los datos.
-   **`threadId` (String)**: Identificador único para una conversación o sesión específica dentro de un `tenantId` dado. Si un mismo inquilino (`tenantId`) está manejando múltiples interacciones o workflows concurrentemente (por ejemplo, diferentes chats de un usuario o diferentes procesos de negocio), el `threadId` permite rastrear y persistir el estado de cada uno de manera independiente y sin colisiones.
-   `persistenceMode`: Define cómo se persiste el estado (ej. `IN_MEMORY`, `REDIS`).
-   Un mapa de datos (`Map<String, Object>`) para almacenar información arbitraria.
-   Una lista de `ChatMessage` para mantener el historial de la conversación.

`AgentState` se gestiona a través de `AgentStateRepository` (con implementaciones como `InMemoryAgentStateRepository` y `RedisAgentStateRepository`).

### WorkflowConfig
Contiene la configuración específica para la ejecución de un workflow para un `tenantId` particular. **Es vital que el `tenantId` en `WorkflowConfig` sea consistente con el del `AgentState` para asegurar que se aplique la configuración correcta, especialmente en entornos persistentes.** Incluye:
-   **`tenantId` (String)**: Identificador del inquilino al que pertenece esta configuración. Es crucial que este `tenantId` **coincida con el `tenantId` del `AgentState`** con el que se ejecutará el workflow, especialmente cuando se utilizan mecanismos de persistencia (como Redis). Esto asegura que la configuración correcta del workflow se cargue y aplique al estado del agente correspondiente, manteniendo la coherencia dentro del contexto de un inquilino.
-   `persistenceMode`: Modo de persistencia para esta configuración.
-   Configuración de servicios de herramientas: Qué herramientas y métodos están activos para este workflow.

`WorkflowConfig` se gestiona a través de `WorkflowConfigRepository` (con implementaciones como `InMemoryWorkflowConfigRepository` y `RedisWorkflowConfigRepository`).

### Nodos (AgentNode)
Los nodos son los bloques de construcción fundamentales de un workflow. Cada nodo implementa la interfaz `com.veyon.veyflow.core.AgentNode` y realiza una tarea específica. Su método principal, `process(AgentState state, WorkflowConfig workflowConfig)`, recibe el estado actual y la configuración del workflow, y devuelve el `AgentState` actualizado después de su ejecución.

-   **Interfaz Principal**: `com.veyon.veyflow.core.AgentNode`
-   **Implementaciones de Ejemplo**: 
    -   `EchoNode`: Un nodo simple que puede devolver su entrada o una configuración fija.
    -   `ToolAgentNode`: Un nodo más complejo que internamente utiliza la lógica de `ToolAgent` (ver Procesadores de IA) para interactuar con LLMs y herramientas externas.
-   Un nodo puede realizar cualquier tipo de lógica, desde simples transformaciones de datos hasta complejas interacciones con modelos de IA y servicios externos.

### Workflows (AgentWorkflow) y Edges
Un `AgentWorkflow` define la secuencia o el grafo de ejecución de múltiples `AgentNode`s. Los `Edges` (bordes o transiciones) definen cómo fluye el control y el `AgentState` entre los nodos.

-   **Clase Principal**: `com.veyon.veyflow.core.AgentWorkflow`
-   Se construye utilizando un `AgentWorkflow.Builder` donde se añaden nodos (`AgentNode`) y los `edges` que los conectan.
-   Permite orquestar cómo el `AgentState` se transforma a través de diferentes etapas de procesamiento.
-   Requiere una `CompileConfig` que especifica los repositorios para `AgentState` y `WorkflowConfig`.

### Procesadores de IA (LLM y ToolAgent)
Aunque no son `AgentNode`s por sí mismos, Veyflow proporciona clases de utilidad o "procesadores" que encapsulan lógica de IA común y pueden ser utilizados *dentro* de la implementación de un `AgentNode` personalizado:

-   **`com.veyon.veyflow.core.LLM`**: Una utilidad para interactuar directamente con un servicio de modelo de lenguaje (configurado a través de un `FoundationModelService`). Permite enviar un `AgentState` (que contiene el historial de chat y otros datos) y recibir la respuesta del modelo, actualizando el estado con el mensaje del LLM.
    
-   **`com.veyon.veyflow.core.ToolAgent`**: Una utilidad más avanzada que gestiona el ciclo completo de interacción con un LLM que puede usar herramientas. Esto incluye:
    -   Enviar el estado actual al LLM.
    -   Interpretar si el LLM solicita una o más llamadas a herramientas (`ToolCall`).
    -   Invocar esas herramientas (si están activadas en `WorkflowConfig`).
    -   Enviar los resultados de las herramientas de nuevo al LLM para obtener una respuesta final.
    -   Actualizar el `AgentState` con todas las interacciones (mensajes del LLM, llamadas a herramientas, respuestas de herramientas).
    Un `AgentNode` (como `ToolAgentNode`) puede usar `ToolAgent` para implementar fácilmente esta compleja lógica de uso de herramientas.

Estos procesadores ayudan a mantener la lógica de los `AgentNode`s más limpia y centrada en su tarea específica, delegando las interacciones de IA a estas utilidades especializadas.

### Herramientas (Tools)
Las herramientas son funcionalidades externas que los agentes (a través de la lógica en `ToolAgent` y un `AgentNode` que lo utilice) pueden invocar.

-   **Interfaz Principal**: `com.veyon.veyflow.tools.ToolService`
-   **Implementaciones de Ejemplo**: 
    -   `WeatherToolService` para obtener información meteorológica.
-   Las herramientas se activan por workflow a través de `WorkflowConfig`.

## Cómo Empezar: Un Ejemplo Básico

```java
// 1. Configurar Repositorios (ej. en memoria para simplicidad)
AgentStateRepository agentStateRepository = new InMemoryAgentStateRepository();
WorkflowConfigRepository workflowConfigRepository = new InMemoryWorkflowConfigRepository();

// 2. Configurar WorkflowConfig
String tenantId = "my-test-tenant";
WorkflowConfig wfConfig = new WorkflowConfig(tenantId, PersistenceMode.IN_MEMORY);
// wfConfig.activateToolMethods(MyToolService.class.getName(), Arrays.asList("myMethod")); // Si se usan herramientas
wfConfig.setRepository(workflowConfigRepository);
wfConfig.save();

// 3. Crear e inicializar AgentState
String threadId = "my-test-thread";
AgentState initialState = new AgentState(tenantId, threadId, PersistenceMode.IN_MEMORY);
initialState.setRepository(agentStateRepository);
initialState.addMessage(ChatMessage.builder().role(Role.USER).content("Hola Mundo!").build());
initialState.save();

// 4. Definir Nodos y el Workflow
// Suponiendo que tienes una implementación de AgentNode llamada EchoNode
// public class EchoNode implements AgentNode { /* ... implementación ... */ }
AgentNode echoNode = new EchoNode("echo1"); // Suponiendo que EchoNode toma un nombre/ID
CompileConfig compileConfig = new CompileConfig(PersistenceMode.IN_MEMORY, agentStateRepository, workflowConfigRepository);

AgentWorkflow workflow = AgentWorkflow.builder()
    .addNode(echoNode)
    // .setInitialInputMapping(Map.of("echo1_input", "initialState")) // El mapeo de entrada puede ser más granular o no necesario si el nodo toma AgentState directamente
    .setCompileConfig(compileConfig)
    .build();

// ... el resto del ejemplo ...
```

## Creación de Nodos Personalizados

Crear nodos personalizados es fundamental para extender la funcionalidad de Veyflow. Un nodo es cualquier clase que implemente la interfaz `com.veyon.veyflow.core.AgentNode`.

La interfaz `AgentNode` define dos métodos principales:
-   `String getName()`: Devuelve un nombre único para el nodo.
-   `AgentState process(AgentState agentState, WorkflowConfig workflowConfig)`: Contiene la lógica principal del nodo. Recibe el estado actual del agente y la configuración del workflow, y devuelve el `AgentState` modificado (o el mismo si no hay cambios).

Aquí tienes un ejemplo de un nodo personalizado que utiliza un `ToolAgent` internamente, similar al `ToolAgentNode` que podrías usar en tus pruebas:

```java
package com.veyon.veyflow.example;

import com.veyon.veyflow.core.AgentNode;
import com.veyon.veyflow.core.AgentState;
import com.veyon.veyflow.core.AgentTurnResult;
import com.veyon.veyflow.core.ToolAgent;
import com.veyon.veyflow.config.WorkflowConfig;
import com.veyon.veyflow.foundationmodels.ModelParameters;

public class MyCustomToolAgentNode implements AgentNode {
    private final ToolAgent toolAgent; // El ToolAgent que ejecutará las herramientas
    private final String name;         // Nombre único del nodo
    private final String systemPrompt; // Prompt del sistema para el LLM dentro del ToolAgent
    private final ModelParameters modelParameters; // Parámetros del modelo para el LLM

    public MyCustomToolAgentNode(String name, ToolAgent toolAgent, String systemPrompt, ModelParameters modelParameters) {
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
        System.out.println("Executing MyCustomToolAgentNode: " + name);
        // Aquí puedes añadir lógica específica antes o después de llamar al ToolAgent
        // Por ejemplo, preparar datos en el agentState o leer de workflowConfig

        // Ejecutar el ToolAgent con el estado actual, configuración y prompts/parámetros específicos del nodo
        AgentTurnResult turnResult = toolAgent.execute(agentState, workflowConfig, this.systemPrompt, this.modelParameters);

        // El ToolAgent ya modifica el agentState internamente con los resultados de las herramientas y la respuesta del LLM.
        // Aquí, adicionalmente, guardamos la respuesta final del LLM en una clave específica del nodo.
        agentState.set(name + "_output", turnResult.getFinalMessage() != null ? turnResult.getFinalMessage() : "MyCustomToolAgentNode executed, no final message from LLM.");
        
        // Devolver el agentState, que ahora contiene los resultados de la ejecución del ToolAgent
        return agentState;
    }
}
```

**Consideraciones al crear nodos:**

*   **Inmutabilidad (parcial):** Aunque `AgentState` es mutable, intenta que tus nodos sean lo más predecibles posible. Si un nodo modifica el estado, debe ser una parte intencionada de su diseño.
*   **Reusabilidad:** Diseña nodos que puedan ser reutilizados en diferentes workflows si es posible.
*   **Dependencias:** Si tu nodo tiene dependencias (como el `ToolAgent` en el ejemplo), estas generalmente se inyectan a través del constructor.

## Gestión del Estado (AgentState)

`AgentState` es el portador de toda la información relevante durante la ejecución de un workflow. Los nodos pueden:
-   **Leer datos**: Acceder a mensajes previos o datos almacenados en el mapa `data`.
    ```java
    String userInput = agentState.getMessages().stream()
        .filter(m -> m.getRole() == Role.USER)
        .map(ChatMessage::getContent)
        .findFirst().orElse("");
    Object customData = agentState.get("myKey");
    ```
-   **Modificar datos**: Añadir nuevos mensajes o actualizar el mapa `data`.
    ```java
    agentState.addMessage(ChatMessage.builder().role(Role.ASSISTANT).content("Respuesta del agente.").build());
    agentState.set("processedValue", 123);
    ```
-   **Persistencia**: Si `AgentState` tiene un `AgentStateRepository` configurado y un `persistenceMode` diferente de `NONE`, puedes guardar el estado:
    ```java
    agentState.save(); // Guardará usando el repositorio y modo configurados
    ```
    La carga se realiza a través de los métodos `findById` del repositorio.

### Identificadores Clave: `tenantId` y `threadId`

Cada `AgentState` se inicializa y se identifica unívocamente mediante la combinación de dos cadenas de texto cruciales:

*   **`tenantId` (String)**: Este identificador es **esencial para la multi-tenancy**. Permite que Veyflow, y especialmente sus mecanismos de persistencia (como `RedisAgentStateRepository`), aíslen de forma segura los datos pertenecientes a diferentes usuarios, aplicaciones o clientes (inquilinos) que puedan estar utilizando el mismo sistema Veyflow. Cuando se guarda o recupera un `AgentState`, el `tenantId` forma parte de la clave de almacenamiento, asegurando que solo se acceda a los datos del inquilino correcto.
    *   **Importancia Primaria**: Garantiza la privacidad, seguridad y separación de los datos entre diferentes entidades operativas.

*   **`threadId` (String)**: Este identificador se utiliza para distinguir entre diferentes **hilos de conversación, sesiones de usuario o ejecuciones de workflow** que pertenecen a un *mismo* `tenantId`. Por ejemplo, si un usuario (representado por `tenantId="user123"`) está interactuando con dos chatbots diferentes simultáneamente, cada chatbot tendría su propio `threadId` (ej. `threadId="chat_support"` y `threadId="chat_sales_info"`). Esto permite que el estado de cada conversación se mantenga y evolucione de forma independiente sin interferencias.
    *   **Importancia Primaria**: Permite el manejo concurrente y ordenado de múltiples interacciones o procesos para un solo inquilino.

Al instanciar un `AgentState`, se deben proporcionar ambos identificadores:
```java
String tenant = "acme_corp"; // Identificador del cliente/empresa
String conversationThread = "support_ticket_98765"; // Identificador de la conversación específica

AgentState agentState = new AgentState(tenant, conversationThread);
```

Estos identificadores son luego utilizados por las implementaciones de `AgentStateRepository` (como `RedisAgentStateRepository` o `InMemoryAgentStateRepository`) para construir claves únicas al guardar o recuperar el estado (ej. `veyflow:state:acme_corp:support_ticket_98765`), garantizando que siempre se acceda al contexto correcto y evitando colisiones de datos.

### Funcionalidades Principales
`AgentState` ofrece varios métodos para interactuar con el estado del agente:

*   **Almacenamiento de Datos Genéricos (Clave-Valor):**
    *   `void set(String key, Object value)`: Almacena un valor asociado a una clave. Los nodos utilizan esto para guardar resultados intermedios, decisiones, o cualquier información relevante para el flujo.
        ```java
        agentState.set("user_city", "London");
        agentState.set("temperature_celsius", 22);
        ```
    *   `Object get(String key)`: Recupera el valor asociado a una clave. Devuelve `null` si la clave no existe.
        ```java
        String city = (String) agentState.get("user_city");
        ```
    *   `<T> T get(String key, Class<T> type)`: Recupera el valor y lo castea al tipo especificado. Útil para evitar casteos manuales.
        ```java
        Integer temp = agentState.get("temperature_celsius", Integer.class);
        ```
    *   `boolean containsKey(String key)`: Verifica si una clave existe en el estado.
    *   `Object remove(String key)`: Elimina una clave y su valor asociado del estado.

*   **Gestión del Historial de Chat:**
    *   `void addMessage(ChatMessage message)`: Añade un único `ChatMessage` al final del historial de la conversación.
        ```java
        agentState.addMessage(ChatMessage.builder().role(ChatMessage.Role.USER).content("Hola, ¿cómo estás?").build());
        ```
    *   `void addMessages(List<ChatMessage> messages)`: Añade una lista de `ChatMessage` al historial.
    *   `List<ChatMessage> getChatMessages()`: Devuelve una copia de la lista de todos los mensajes de chat almacenados, manteniendo el orden cronológico.
    *   `void setChatMessages(List<ChatMessage> messages)`: Reemplaza completamente el historial de chat existente con la lista proporcionada.

*   **Acceso a Identificadores y Configuración:**
    *   `String getTenantId()`: Devuelve el `tenantId` del estado.
    *   `String getThreadId()`: Devuelve el `threadId` del estado.
    *   `PersistenceMode getPersistenceMode()`: Devuelve el modo de persistencia configurado para este `AgentState`.

Estas funcionalidades permiten a los nodos y servicios de Veyflow leer, escribir y modificar el contexto de un agente de manera flexible a lo largo de la ejecución de un workflow.
## Configuración de Workflows (WorkflowConfig)

`com.veyon.veyflow.config.WorkflowConfig` permite configurar aspectos específicos de la ejecución de un workflow, como:
*   **`tenantId` (String)**: Identificador del inquilino al que pertenece esta configuración. Es crucial que este `tenantId` **coincida con el `tenantId` del `AgentState`** con el que se ejecutará el workflow, especialmente cuando se utilizan mecanismos de persistencia (como Redis). Esto asegura que la configuración correcta del workflow se cargue y aplique al estado del agente correspondiente, manteniendo la coherencia dentro del contexto de un inquilino.
*   **`threadId` (String)**: Aunque `WorkflowConfig` se asocia a un `tenantId`, no gestiona directamente un `threadId` para su propia persistencia de la misma manera que `AgentState`. La configuración del workflow suele ser más general para un inquilino. Sin embargo, la instancia de `WorkflowConfig` se utiliza *junto con* un `AgentState` que sí tiene un `threadId` específico durante la ejecución del workflow.
*   **`persistenceMode`**: Cómo se persistirá el `AgentState` y el `WorkflowConfig` (ej. `PersistenceMode.REDIS`, `PersistenceMode.IN_MEMORY`). La elección aquí afecta tanto al estado como a la configuración.
*   **Activación de Herramientas**: Qué `ToolService` y qué métodos de esos servicios están activos para esta ejecución particular del workflow.

```java
// El tenantId aquí debe ser consistente con el AgentState que se usará.
// Por ejemplo, si AgentState usa "acme_corp", WorkflowConfig también debe usar "acme_corp".
WorkflowConfig workflowConfig = new WorkflowConfig("acme_corp", PersistenceMode.IN_MEMORY);


// Ejemplo de cómo se podrían activar métodos específicos de un servicio:
workflowConfig.activateToolMethod(MyCalculatorService.class.getSimpleName(), "add");
workflowConfig.activateToolMethod(MyCalculatorService.class.getSimpleName(), "subtract");

// Alternativamente, si se quisiera activar todos los métodos de un servicio registrado:
// workflowConfig.activateToolService(MyCalculatorService.class.getSimpleName());
## Integración y Uso de Herramientas

1.  **Crear un `ToolService`**:
    Crea una clase que **extienda** `com.veyon.veyflow.tools.ToolService`. Anota los métodos que deseas exponer como herramientas con `@ToolAnnotation` y sus parámetros con `@ToolParameter`.
    Los métodos de herramientas pueden, opcionalmente, aceptar un parámetro `AgentState` si necesitan acceder al estado actual.
    > **Nota Importante:** Si un método de herramienta recibe la instancia de `AgentState`, tiene la capacidad de **modificar directamente dicho estado** (por ejemplo, usando `state.set("clave", "valor")`). Estas modificaciones se reflejarán en el `AgentState` que `ToolAgent` continúa procesando y que será pasado a nodos subsecuentes en el flujo de trabajo.

    ```java
    import com.veyon.veyflow.tools.ToolService;
    import com.veyon.veyflow.tools.ToolAnnotation;
    import com.veyon.veyflow.tools.ToolParameter;
    import com.veyon.veyflow.state.AgentState; // Opcional, si la herramienta necesita el estado
    import com.google.gson.JsonObject; // Ejemplo de tipo de retorno, puede ser cualquier tipo serializable

    public class MyCalculatorService extends ToolService { // Extiende ToolService

        @ToolAnnotation("Adds two integers and returns the sum.")
        public JsonObject add(
            @ToolParameter(value = "The first integer to add", type = "integer", required = true) int a, 
            @ToolParameter(value = "The second integer to add", type = "integer", required = true) int b,
            AgentState state // Parámetro AgentState opcional
        ) {
            // Se podría usar el 'state' para logging o lógica condicional si fuera necesario
            // log.info("Adding numbers for tenant: {}", state.getTenantId());
            
            int sum = a + b;
            JsonObject result = new JsonObject();
            result.addProperty("sum", sum);
            return result; // Devuelve un JsonObject, pero podría ser String, int, etc.
        }

        @ToolAnnotation("Subtracts the second integer from the first.")
        public int subtract(
            @ToolParameter(value = "The integer to subtract from", type = "integer", required = true) int a, 
            @ToolParameter(value = "The integer to subtract", type = "integer", required = true) int b
        ) {
            return a - b;
        }
    }
    ```
2.  **Registrar y Activar la Herramienta**:
    -   Cuando se crea una instancia de `ToolAgent` (que sería usada por un `AgentNode`), se le puede pasar un mapa de instancias de tus `ToolService` registrados (ej. `Map<String, ToolService> registeredServices`). La clave del mapa suele ser el nombre de la clase del servicio.
3.  **Uso por un `AgentNode` (ej. `ToolAgentNode`)**:
    Un `AgentNode` que utilice `ToolAgent` internamente podrá considerar el uso de las herramientas activadas si el LLM decide que una herramienta es apropiada para responder a la solicitud del usuario.

## Flujo de Control: Routers

Los Routers en Veyflow son componentes cruciales que definen cómo se navega entre los diferentes `AgentNode` dentro de un `AgentWorkflow`. Determinan la secuencia de ejecución y permiten la creación de flujos de trabajo condicionales y paralelos.

### Conceptos Clave de Enrutamiento

1.  **`com.veyon.veyflow.routing.NodeRouter` (Interfaz Principal)**:
    Esta es la interfaz fundamental que todos los routers deben implementar. Define un único método:
    ```java
    package com.veyon.veyflow.routing;

    import com.veyon.veyflow.state.AgentState;
    import com.veyon.veyflow.config.WorkflowConfig;

    public interface NodeRouter {
        String route(AgentState state, WorkflowConfig workflowConfig);
    }
    ```
    El método `route` toma el `AgentState` actual y el `WorkflowConfig`, y devuelve el nombre (`String`) del siguiente `AgentNode` a ejecutar. Si devuelve `null` o un nombre de nodo no válido, esa ruta de ejecución particular puede terminar.

2.  **Conexiones Lineales (Edges) con `workflow.addEdge()`**:
    La forma más sencilla de definir una transición incondicional de un nodo a otro es mediante `AgentWorkflow.addEdge()`:
    ```java
    // Ejemplo de conexión directa de 'entryNode' a 'targetNode'
    workflow.addEdge("entryNodeName", "targetNodeName");
    ```
    Esto crea una ruta lineal donde, después de que `entryNode` se complete, `targetNode` será el siguiente en ejecutarse. Internamente, esto utiliza un mecanismo de enrutamiento lineal (probablemente un `LinearRouter` implícito).

3.  **`com.veyon.veyflow.routing.ConditionalRouter`**: 
    Para flujos de trabajo donde la ruta depende del estado actual, se utiliza `ConditionalRouter`. Este router se construye con una función (lambda) que evalúa el `AgentState` y el `WorkflowConfig` para decidir el siguiente nodo.
    ```java
    // Añadir un ConditionalRouter al 'entryNode'
    workflow.addRouter(entryNode.getName(), new ConditionalRouter((state, config) -> {
        String decision = (String) state.get("user_choice");
        if ("opcion1".equals(decision)) {
            return pathOneNode.getName(); // Ir a pathOneNode
        } else if ("opcion2".equals(decision)) {
            return pathTwoNode.getName(); // Ir a pathTwoNode
        }
        return null; // No tomar ninguna ruta condicional explícita desde aquí
    }));
    ```

4.  **`com.veyon.veyflow.routing.LinearRouter` (Explícito)**:
    Aunque `workflow.addEdge()` es común para conexiones directas, también existe `com.veyon.veyflow.routing.LinearRouter`. Este puede ser usado para definir explícitamente una ruta de un `sourceNode` a un `targetNode` si se prefiere una instanciación directa del router.
    ```java
    // workflow.addRouter("sourceNodeName", new LinearRouter("sourceNodeName", "targetNodeName"));
    ```

### Ejecución Paralela de Nodos y Convergencia (N-furcación)

Veyflow permite que múltiples nodos se ejecuten en paralelo. Esto se logra cuando un nodo de origen tiene múltiples rutas de salida definidas, ya sea a través de varios `addEdge` o una combinación de `addEdge` y `addRouter` (lo que se conoce como N-furcación o bifurcación en N caminos).

```java
// Ejemplo de N-furcación desde 'entryNode'
EchoNode entryNode = new EchoNode("entry");
EchoNode taskA = new EchoNode("taskA");
EchoNode taskB = new EchoNode("taskB");
EchoNode joinNode = new EchoNode("join");

AgentWorkflow workflow = new AgentWorkflow(entryNode.getName());
workflow.addNode(entryNode).addNode(taskA).addNode(taskB).addNode(joinNode);

// Ruta 1: entryNode -> taskA (implícitamente lineal)
workflow.addEdge(entryNode.getName(), taskA.getName());

// Ruta 2: entryNode -> taskB (condicional)
workflow.addRouter(entryNode.getName(), new ConditionalRouter((state, config) -> {
    if (state.get("condition_for_b") != null) {
        return taskB.getName();
    }
    return null; // Si la condición no se cumple, esta ruta no se toma
}));

// Ambas rutas, si se activan, deben converger
workflow.addEdge(taskA.getName(), joinNode.getName());
workflow.addEdge(taskB.getName(), joinNode.getName());

// Compilar y ejecutar...
// AgentState initialState = new AgentState("tenant", "thread");
// initialState.set("condition_for_b", true);
// CompiledWorkflow compiledWorkflow = workflow.compile();
// AgentState finalState = compiledWorkflow.execute(initialState, new WorkflowConfig());
```

En el ejemplo anterior, si la condición para `taskB` se cumple, `taskA` y `taskB` podrían ejecutarse en paralelo después de `entryNode`.

**Limitación Crítica: Convergencia Obligatoria**

Una restricción fundamental en Veyflow es que **todas las rutas de ejecución paralelas deben converger eventualmente en un único nodo común (join node)**. Si las rutas paralelas divergen y no se vuelven a unir en un solo nodo, el `AgentWorkflow` **no podrá compilarse correctamente** y lanzará un error durante la fase de `workflow.compile()`.

Esto se puede observar en pruebas como `testFailedCompilationForDivergingLinearRouters` y `testSuccessfulCompilationForConvergingLinearRouters` dentro de `RoutersTesting.java`. El compilador de workflows necesita un grafo dirigido acíclico (DAG) que tenga un punto final claro para cada conjunto de bifurcaciones.

## Definición, Compilación y Ejecución de Workflows

El ciclo de vida de un workflow en Veyflow implica su definición, compilación y posterior ejecución.

### 1. Definición del Workflow (`AgentWorkflow`)

Un workflow se define creando una instancia de `com.veyon.veyflow.core.AgentWorkflow`.

```java
// 1. Crear instancias de tus nodos personalizados (implementaciones de AgentNode)
MyCustomToolAgentNode entryNode = new MyCustomToolAgentNode("inputNode", toolAgentInstance, "System prompt for input", ModelParameters.defaults());
AnotherCustomNode processingNode = new AnotherCustomNode("processor");
FinalNode outputNode = new FinalNode("outputGenerator");

// 2. Crear el AgentWorkflow, especificando el nombre del nodo de entrada
AgentWorkflow workflow = new AgentWorkflow(entryNode.getName());

// 3. Añadir todos los nodos al workflow
workflow.addNode(entryNode);
workflow.addNode(processingNode);
workflow.addNode(outputNode);

// 4. Definir las rutas (edges y routers) entre los nodos
workflow.addEdge(entryNode.getName(), processingNode.getName()); // entryNode -> processingNode
workflow.addRouter(processingNode.getName(), new ConditionalRouter((state, config) -> {
    if ("data_ok".equals(state.get("status"))) {
        return outputNode.getName();
    }
    return null; // Terminar si el estado no es 'data_ok'
}));

// Compilar y ejecutar...
```

### 2. Configuración del Workflow (`WorkflowConfig`)

`com.veyon.veyflow.config.WorkflowConfig` permite configurar aspectos específicos de la ejecución de un workflow, como:
*   **`tenantId` (String)**: Identificador del inquilino al que pertenece esta configuración. Es crucial que este `tenantId` **coincida con el `tenantId` del `AgentState`** con el que se ejecutará el workflow, especialmente cuando se utilizan mecanismos de persistencia (como Redis). Esto asegura que la configuración correcta del workflow se cargue y aplique al estado del agente correspondiente, manteniendo la coherencia dentro del contexto de un inquilino.
*   **`threadId` (String)**: Aunque `WorkflowConfig` se asocia a un `tenantId`, no gestiona directamente un `threadId` para su propia persistencia de la misma manera que `AgentState`. La configuración del workflow suele ser más general para un inquilino. Sin embargo, la instancia de `WorkflowConfig` se utiliza *junto con* un `AgentState` que sí tiene un `threadId` específico durante la ejecución del workflow.
*   **`persistenceMode`**: Cómo se persistirá el `AgentState` y el `WorkflowConfig` (ej. `PersistenceMode.REDIS`, `PersistenceMode.IN_MEMORY`). La elección aquí afecta tanto al estado como a la configuración.
*   **Activación de Herramientas**: Qué `ToolService` y qué métodos de esos servicios están activos para esta ejecución particular del workflow.

```java
// El tenantId aquí debe ser consistente con el AgentState que se usará.
// Por ejemplo, si AgentState usa "acme_corp", WorkflowConfig también debe usar "acme_corp".
WorkflowConfig workflowConfig = new WorkflowConfig("acme_corp", PersistenceMode.IN_MEMORY);

// Activar herramientas específicas (opcional, si el workflow usa ToolAgent)
workflowConfig.activateToolMethod(MyCalculatorService.class.getSimpleName(), "add");
workflowConfig.activateToolMethod(MyCalculatorService.class.getSimpleName(), "subtract");

### 3. Compilación del Workflow (`workflow.compile()`)

Una vez definido, el `AgentWorkflow` debe ser compilado. Este paso valida la estructura del grafo, resuelve las rutas y prepara un `CompiledWorkflow` ejecutable.

```java
CompiledWorkflow compiledWorkflow = workflow.compile();
```
Si hay problemas en la definición del grafo (ej. rutas paralelas que no convergen), la compilación fallará.

### 4. Preparación del Estado Inicial (`AgentState`)

Antes de ejecutar, necesitas un `AgentState` inicial. Este contendrá cualquier dato de entrada necesario para el primer nodo y **debe ser instanciado con el `tenantId` y `threadId` correctos**.

```java
// El tenantId debe coincidir con el de WorkflowConfig si se usa persistencia
// El threadId identifica esta sesión/conversación particular
AgentState initialState = new AgentState("acme_corp", "support_ticket_98765"); 
initialState.set("userInput", "Quiero saber el clima de mañana en Londres.");
initialState.set("user_choice", "opcion1"); // Para el ConditionalRouter del ejemplo
```

### 5. Ejecución del Workflow (`compiledWorkflow.execute()`)

Finalmente, se ejecuta el workflow compilado con el estado inicial y la configuración del workflow.

```java
AgentState finalState = compiledWorkflow.execute(initialState, workflowConfig);

// El finalState contendrá los resultados y el estado modificado después de la ejecución completa del workflow.
System.out.println("Resultado final del workflow: " + finalState.get("outputGenerator_output"));
System.out.println("Historial de chat: " + finalState.getChatMessages());
```
El método `execute` devuelve el `AgentState` final después de que todos los nodos aplicables en la ruta se hayan procesado.

## Ejecución de Pruebas

Veyflow utiliza Maven Surefire Plugin para ejecutar pruebas JUnit 5.

-   **Ejecutar todas las pruebas:**
    ```bash
    mvn test
    ```
-   **Ejecutar una clase de prueba específica:**
    ```bash
    mvn test -Dtest=com.veyon.veyflow.MySpecificTestClass
    ```
-   **Ejecutar un método de prueba específico:**
    ```bash
    mvn test -Dtest=com.veyon.veyflow.MySpecificTestClass#mySpecificTestMethod
    ```

Los informes de las pruebas se encuentran generalmente en `target/surefire-reports/`.

## Contribuciones

¡Las contribuciones son bienvenidas! Si deseas contribuir a Veyflow, por favor considera lo siguiente:

-   Sigue las convenciones de código existentes.
-   Asegúrate de que todas las pruebas pasen (`mvn clean install`).
-   Añade nuevas pruebas para cualquier nueva funcionalidad o corrección de errores.
-   Documenta tu código y cualquier cambio significativo.
-   (Opcional: Abre un issue para discutir cambios mayores antes de implementarlos).

## Licencia

Este proyecto está licenciado bajo la Licencia MIT. Consulta el archivo [LICENSE](LICENSE) para más detalles.
