# 🧪 common-zeebe-test

**Common Zeebe Test** provides shared test configuration for testing BPMN processes with Camunda Platform 8.

This module provides reusable test configuration that can be used across multiple services.

## 📌 What's Included

- **TestProcessEngineConfiguration**: Spring test configuration that ensures `ProcessEngineApi` uses the test `CamundaClient` provided by `@CamundaSpringProcessTest`

## 🔧 How to Use

Include **common-zeebe-test** as a test dependency and import the test configuration:

```xml
<!-- In your service's pom.xml (version managed by the root pom's dependencyManagement) -->
<dependency>
    <groupId>io.miragon.blueprint</groupId>
    <artifactId>common-zeebe-test</artifactId>
    <scope>test</scope>
</dependency>
```

```java
// In your test class
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@CamundaSpringProcessTest
@Import(TestProcessEngineConfiguration.class)
class YourProcessTest {
    // Your tests here
}
```

```java
// Assert the walked path against the generated process API
PathWalk.Trail path = PathWalk.from(FlowNodes.StartEventLeasingRequestReceived.INSTANCE)
    .then(n -> n.serviceTaskValidateApplication())
    // …
    .end(n -> n.endEventLeasingActive());
assertThatProcessInstance(instance).hasCompletedElementsInOrder(path.getIds());
```
