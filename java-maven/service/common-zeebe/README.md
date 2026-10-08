# ⚙️ Common Zeebe

**Common Zeebe** is the backbone module for integrating the **Zeebe process engine** into your services.
It streamlines the connection to Zeebe, making it easy to interact with BPMN workflows
and manage job workers effortlessly.

This module provides everything you need to seamlessly connect, configure,
and interact with the Zeebe engine running in your stack.

## 🔧 Key Features

- **Zeebe Client Connection**: `zeebe-application.yaml` contributes the self-managed connection defaults
  (gRPC `localhost:26500`, REST `localhost:8080`, no auth) through `ZeebeEnvironmentConfiguration`.
- **Auto-deployment**: `EngineAutoConfiguration` (registered via `META-INF/spring/…AutoConfiguration.imports`)
  deploys every `bpmn/*.bpmn`, `dmn/*.dmn` and `forms/*.form` on the classpath at start-up.
- **ProcessEngineApi**: a small facade over the `CamundaClient` to start processes, publish messages and
  search process instances, speaking bpmn-to-code's typed `ProcessId` / `MessageName` / `VariableName`.

## 🔍 Further Details

Job workers are plain Spring beans using Camunda's native **`@JobWorker`** annotation (see
`service/app/src/main/java/io/miragon/blueprint/adapter/inbound/zeebe`). Process tests run them for real
against an in-container engine with **`@CamundaSpringProcessTest`** from
[`camunda-process-test-spring`](https://github.com/camunda/camunda/tree/main/testing/camunda-process-test-spring);
the workers register automatically and the use cases behind them are mocked. `common-zeebe-test`
supplies the matching test configuration.

## 📌 How to Use

Include **common-zeebe** as a dependency in your service modules
to integrate and interact with the Zeebe engine efficiently.

### Example (Maven Setup):

```xml
<!-- version managed by the root pom's dependencyManagement -->
<dependency>
    <groupId>io.miragon.blueprint</groupId>
    <artifactId>common-zeebe</artifactId>
</dependency>
```
