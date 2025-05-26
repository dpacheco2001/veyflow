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

1.  **Clonar el repositorio (si aplica):**
    ```bash
    git clone <URL_DEL_REPOSITORIO>
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
        agentState.addMessage(ChatMessage.builder().role(Role.USER).content("Hola, ¿cómo estás?").build());
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

// Activar herramientas específicas (opcional, si el workflow usa ToolAgent)
{{ ... }}
