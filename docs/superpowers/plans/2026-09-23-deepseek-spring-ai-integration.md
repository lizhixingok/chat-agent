# DeepSeek Spring AI Integration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Keep Spring Boot 3.2.12 and add stateless synchronous and SSE streaming chat endpoints backed by DeepSeek's OpenAI-compatible API.

**Architecture:** The API module owns validated request/response DTOs, the service module isolates the Spring AI 0.8.1 clients behind `AiChatService`, and the provider module exposes the two HTTP endpoints. Spring AI's OpenAI starter is configured with DeepSeek's base URL and model; tests mock the model clients and never call the real API.

**Tech Stack:** Java 21, Spring Boot 3.2.12, Spring MVC, Spring AI 0.8.1 OpenAI starter, Reactor `Flux`, JUnit 5, Mockito, MockMvc, AssertJ.

---

## File map

- Modify `pom.xml`: manage Spring AI 0.8.1 and add the milestone repository that hosts it.
- Modify `chat-agent-service/pom.xml`: add the OpenAI Spring AI starter.
- Create `chat-agent-api/src/main/java/com/zx/chat/agent/api/dto/req/ChatReqDTO.java`: validated chat input.
- Create `chat-agent-api/src/main/java/com/zx/chat/agent/api/dto/resp/ChatRespDTO.java`: synchronous chat output.
- Create `chat-agent-service/src/main/java/com/zx/chat/agent/service/service/AiChatService.java`: model-independent service boundary.
- Create `chat-agent-service/src/main/java/com/zx/chat/agent/service/service/impl/AiChatServiceImpl.java`: Spring AI adapter and upstream error translation.
- Create `chat-agent-service/src/test/java/com/zx/chat/agent/service/service/impl/AiChatServiceImplTest.java`: service behavior tests.
- Create `chat-agent-provider/src/main/java/com/zx/chat/agent/provider/controller/outer/AiChatController.java`: synchronous and SSE HTTP endpoints.
- Create `chat-agent-provider/src/test/java/com/zx/chat/agent/provider/controller/AiChatControllerTest.java`: web behavior tests.
- Modify `chat-agent-provider/src/main/resources/application.yml`: DeepSeek URL, placeholder key, and model.
- Modify `chat-agent-provider/src/main/java/com/zx/chat/agent/provider/ChatAgentApplication.java`: include project packages in component scanning.
- Create `chat-agent-provider/src/test/java/com/zx/chat/agent/provider/ChatAgentApplicationConfigurationTest.java`: component scan and Spring AI configuration tests.

### Task 1: Add Spring AI dependencies and chat DTOs

**Files:**
- Modify: `pom.xml`
- Modify: `chat-agent-service/pom.xml`
- Create: `chat-agent-api/src/main/java/com/zx/chat/agent/api/dto/req/ChatReqDTO.java`
- Create: `chat-agent-api/src/main/java/com/zx/chat/agent/api/dto/resp/ChatRespDTO.java`

- [ ] **Step 1: Add Spring AI version management and repository**

Add this property to the root `pom.xml` properties:

```xml
<spring-ai.version>0.8.1</spring-ai.version>
```

Import the BOM at the start of `dependencyManagement.dependencies`:

```xml
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-bom</artifactId>
    <version>${spring-ai.version}</version>
    <type>pom</type>
    <scope>import</scope>
</dependency>
```

Add the repository next to Maven Central:

```xml
<repository>
    <id>spring-milestones</id>
    <url>https://repo.spring.io/milestone</url>
</repository>
```

- [ ] **Step 2: Add the OpenAI-compatible starter to the service module**

Add to `chat-agent-service/pom.xml` dependencies:

```xml
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-openai-spring-boot-starter</artifactId>
</dependency>
```

- [ ] **Step 3: Add the validated request DTO**

Create `ChatReqDTO.java`:

```java
package com.zx.chat.agent.api.dto.req;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

@Data
public class ChatReqDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotBlank(message = "不能为空")
    @Size(max = 4000, message = "长度不能超过 {max}")
    private String message;
}
```

- [ ] **Step 4: Add the synchronous response DTO**

Create `ChatRespDTO.java`:

```java
package com.zx.chat.agent.api.dto.resp;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatRespDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String content;
}
```

- [ ] **Step 5: Resolve dependencies and compile DTOs**

Run:

```bash
mvn -pl chat-agent-api,chat-agent-service -am -DskipTests compile
```

Expected: `BUILD SUCCESS`; Maven resolves Spring AI 0.8.1 from `spring-milestones` and Spring Boot remains 3.2.12.

- [ ] **Step 6: Commit**

```bash
git add pom.xml chat-agent-service/pom.xml chat-agent-api/src/main/java/com/zx/chat/agent/api/dto
git commit -m "build: add Spring AI DeepSeek dependencies"
```

### Task 2: Implement the model service with TDD

**Files:**
- Create: `chat-agent-service/src/test/java/com/zx/chat/agent/service/service/impl/AiChatServiceImplTest.java`
- Create: `chat-agent-service/src/main/java/com/zx/chat/agent/service/service/AiChatService.java`
- Create: `chat-agent-service/src/main/java/com/zx/chat/agent/service/service/impl/AiChatServiceImpl.java`

- [ ] **Step 1: Write the failing service tests**

Create `AiChatServiceImplTest.java`:

```java
package com.zx.chat.agent.service.service.impl;

import com.zx.chat.agent.api.common.ErrorCode;
import com.zx.chat.agent.api.exception.BizException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.ChatClient;
import org.springframework.ai.chat.StreamingChatClient;
import reactor.core.publisher.Flux;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AiChatServiceImplTest {

    private ChatClient chatClient;
    private StreamingChatClient streamingChatClient;
    private AiChatServiceImpl service;

    @BeforeEach
    void setUp() {
        chatClient = mock(ChatClient.class);
        streamingChatClient = mock(StreamingChatClient.class);
        service = new AiChatServiceImpl(chatClient, streamingChatClient);
    }

    @Test
    void chatReturnsCompleteModelAnswer() {
        when(chatClient.call("你好")).thenReturn("你好，我是 DeepSeek");

        assertThat(service.chat("你好")).isEqualTo("你好，我是 DeepSeek");
    }

    @Test
    void streamReturnsNonEmptyModelChunks() {
        when(streamingChatClient.stream("你好"))
            .thenReturn(Flux.just("你", "", "好"));

        assertThat(service.stream("你好").collectList().block())
            .isEqualTo(List.of("你", "好"));
    }

    @Test
    void chatHidesUpstreamFailureDetails() {
        when(chatClient.call("你好"))
            .thenThrow(new IllegalStateException("upstream body with secret"));

        assertThatThrownBy(() -> service.chat("你好"))
            .isInstanceOf(BizException.class)
            .satisfies(ex -> assertThat(((BizException) ex).getErrorCode())
                .isEqualTo(ErrorCode.THIRD_PARTY_ERROR))
            .hasMessage(ErrorCode.THIRD_PARTY_ERROR.getMessage())
            .hasMessageNotContaining("secret");
    }

    @Test
    void streamMapsAsynchronousFailureToBusinessException() {
        when(streamingChatClient.stream("你好"))
            .thenReturn(Flux.error(new IllegalStateException("secret stream failure")));

        assertThatThrownBy(() -> service.stream("你好").blockLast())
            .isInstanceOf(BizException.class)
            .hasMessage(ErrorCode.THIRD_PARTY_ERROR.getMessage())
            .hasMessageNotContaining("secret");
    }
}
```

- [ ] **Step 2: Run the test to verify RED**

Run:

```bash
mvn -pl chat-agent-service -am -Dtest=AiChatServiceImplTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: compilation fails because `AiChatServiceImpl` does not exist.

- [ ] **Step 3: Add the service interface**

Create `AiChatService.java`:

```java
package com.zx.chat.agent.service.service;

import reactor.core.publisher.Flux;

public interface AiChatService {

    String chat(String message);

    Flux<String> stream(String message);
}
```

- [ ] **Step 4: Add the minimal service implementation**

Create `AiChatServiceImpl.java`:

```java
package com.zx.chat.agent.service.service.impl;

import com.zx.chat.agent.api.common.ErrorCode;
import com.zx.chat.agent.api.exception.BizException;
import com.zx.chat.agent.service.service.AiChatService;
import org.springframework.ai.chat.ChatClient;
import org.springframework.ai.chat.StreamingChatClient;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

@Service
public class AiChatServiceImpl implements AiChatService {

    private final ChatClient chatClient;
    private final StreamingChatClient streamingChatClient;

    public AiChatServiceImpl(ChatClient chatClient, StreamingChatClient streamingChatClient) {
        this.chatClient = chatClient;
        this.streamingChatClient = streamingChatClient;
    }

    @Override
    public String chat(String message) {
        try {
            return chatClient.call(message);
        } catch (RuntimeException ex) {
            throw upstreamFailure();
        }
    }

    @Override
    public Flux<String> stream(String message) {
        try {
            return streamingChatClient.stream(message)
                .filter(chunk -> chunk != null && !chunk.isEmpty())
                .onErrorMap(RuntimeException.class, ex -> upstreamFailure());
        } catch (RuntimeException ex) {
            throw upstreamFailure();
        }
    }

    private BizException upstreamFailure() {
        return new BizException(ErrorCode.THIRD_PARTY_ERROR);
    }
}
```

- [ ] **Step 5: Run service tests to verify GREEN**

Run:

```bash
mvn -pl chat-agent-service -am -Dtest=AiChatServiceImplTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: all four `AiChatServiceImplTest` tests pass.

- [ ] **Step 6: Commit**

```bash
git add chat-agent-service/src/main/java/com/zx/chat/agent/service chat-agent-service/src/test/java/com/zx/chat/agent/service/service/impl/AiChatServiceImplTest.java
git commit -m "feat: add DeepSeek chat service"
```

### Task 3: Expose synchronous and SSE endpoints with TDD

**Files:**
- Create: `chat-agent-provider/src/test/java/com/zx/chat/agent/provider/controller/AiChatControllerTest.java`
- Create: `chat-agent-provider/src/main/java/com/zx/chat/agent/provider/controller/outer/AiChatController.java`

- [ ] **Step 1: Write the failing controller tests**

Create `AiChatControllerTest.java`:

```java
package com.zx.chat.agent.provider.controller;

import com.zx.chat.agent.api.common.ErrorCode;
import com.zx.chat.agent.api.exception.BizException;
import com.zx.chat.agent.provider.controller.outer.AiChatController;
import com.zx.chat.agent.provider.handler.GlobalExceptionHandler;
import com.zx.chat.agent.service.service.AiChatService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import reactor.core.publisher.Flux;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AiChatControllerTest {

    private AiChatService aiChatService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        aiChatService = mock(AiChatService.class);
        mockMvc = MockMvcBuilders
            .standaloneSetup(new AiChatController(aiChatService))
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
    }

    @Test
    void chatReturnsWrappedAnswer() throws Exception {
        when(aiChatService.chat("你好")).thenReturn("你好，我是 DeepSeek");

        mockMvc.perform(post("/o/v1/chat/completions")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"message\":\"你好\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.content").value("你好，我是 DeepSeek"));
    }

    @Test
    void blankMessageIsRejected() throws Exception {
        mockMvc.perform(post("/o/v1/chat/completions")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"message\":\" \"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(ErrorCode.PARAM_INVALID.getCode()));
    }

    @Test
    void streamReturnsSseChunks() throws Exception {
        when(aiChatService.stream("你好")).thenReturn(Flux.just("你", "好"));

        MvcResult pending = mockMvc.perform(post("/o/v1/chat/completions/stream")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .content("{\"message\":\"你好\"}"))
            .andExpect(request().asyncStarted())
            .andReturn();

        mockMvc.perform(asyncDispatch(pending))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM))
            .andExpect(content().string("event:message\ndata:你\n\nevent:message\ndata:好\n\n"));
    }

    @Test
    void streamConvertsUpstreamFailureToSafeErrorEvent() throws Exception {
        when(aiChatService.stream("你好"))
            .thenReturn(Flux.error(new BizException(ErrorCode.THIRD_PARTY_ERROR)));

        MvcResult pending = mockMvc.perform(post("/o/v1/chat/completions/stream")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .content("{\"message\":\"你好\"}"))
            .andExpect(request().asyncStarted())
            .andReturn();

        mockMvc.perform(asyncDispatch(pending))
            .andExpect(status().isOk())
            .andExpect(content().string("event:error\ndata:AI 服务暂时不可用\n\n"));
    }
}
```

- [ ] **Step 2: Run the controller test to verify RED**

Run:

```bash
mvn -pl chat-agent-provider -am -Dtest=AiChatControllerTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: compilation fails because `AiChatController` does not exist.

- [ ] **Step 3: Add the controller**

Create `AiChatController.java`:

```java
package com.zx.chat.agent.provider.controller.outer;

import com.i61.common.bean.bean.RespResult;
import com.zx.chat.agent.api.dto.req.ChatReqDTO;
import com.zx.chat.agent.api.dto.resp.ChatRespDTO;
import com.zx.chat.agent.api.exception.BizException;
import com.zx.chat.agent.service.service.AiChatService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/o/v1/chat")
@Tag(name = "AI Chat", description = "DeepSeek 对话接口")
public class AiChatController {

    private final AiChatService aiChatService;

    public AiChatController(AiChatService aiChatService) {
        this.aiChatService = aiChatService;
    }

    @PostMapping("/completions")
    @Operation(summary = "同步对话")
    public RespResult<ChatRespDTO> chat(@Valid @RequestBody ChatReqDTO req) {
        return RespResult.succeed(new ChatRespDTO(aiChatService.chat(req.getMessage())));
    }

    @PostMapping(value = "/completions/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "流式对话")
    public Flux<ServerSentEvent<String>> stream(@Valid @RequestBody ChatReqDTO req) {
        return aiChatService.stream(req.getMessage())
            .map(chunk -> ServerSentEvent.builder(chunk).event("message").build())
            .onErrorResume(BizException.class, ex -> Flux.just(
                ServerSentEvent.builder("AI 服务暂时不可用").event("error").build()));
    }
}
```

- [ ] **Step 4: Run controller tests to verify GREEN**

Run:

```bash
mvn -pl chat-agent-provider -am -Dtest=AiChatControllerTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: all four `AiChatControllerTest` tests pass. If MockMvc emits platform line endings, assert the two event/data pairs with `containsString` while retaining the media-type assertion.

- [ ] **Step 5: Commit**

```bash
git add chat-agent-provider/src/main/java/com/zx/chat/agent/provider/controller/outer/AiChatController.java chat-agent-provider/src/test/java/com/zx/chat/agent/provider/controller/AiChatControllerTest.java
git commit -m "feat: expose DeepSeek chat endpoints"
```

### Task 4: Configure DeepSeek and fix application scanning

**Files:**
- Modify: `chat-agent-provider/src/main/resources/application.yml`
- Modify: `chat-agent-provider/src/main/java/com/zx/chat/agent/provider/ChatAgentApplication.java`
- Create: `chat-agent-provider/src/test/java/com/zx/chat/agent/provider/ChatAgentApplicationConfigurationTest.java`

- [ ] **Step 1: Write failing configuration tests**

Create `ChatAgentApplicationConfigurationTest.java`:

```java
package com.zx.chat.agent.provider;

import org.junit.jupiter.api.Test;
import org.springframework.ai.autoconfigure.openai.OpenAiAutoConfiguration;
import org.springframework.ai.autoconfigure.retry.SpringAiRetryAutoConfiguration;
import org.springframework.ai.chat.ChatClient;
import org.springframework.ai.chat.StreamingChatClient;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.web.client.RestClientAutoConfiguration;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class ChatAgentApplicationConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(
            RestClientAutoConfiguration.class,
            SpringAiRetryAutoConfiguration.class,
            OpenAiAutoConfiguration.class))
        .withPropertyValues(
            "spring.ai.openai.api-key=test-key",
            "spring.ai.openai.base-url=https://api.deepseek.com",
            "spring.ai.openai.chat.options.model=deepseek-chat",
            "spring.ai.openai.embedding.enabled=false",
            "spring.ai.openai.image.enabled=false");

    @Test
    void componentScanIncludesApplicationPackages() {
        SpringBootApplication annotation = ChatAgentApplication.class
            .getAnnotation(SpringBootApplication.class);

        assertThat(Arrays.asList(annotation.scanBasePackages()))
            .contains("com.i61", "com.zx.chat.agent");
    }

    @Test
    void applicationYamlConfiguresDeepSeek() throws IOException {
        PropertySource<?> source = new YamlPropertySourceLoader()
            .load("application", new ClassPathResource("application.yml"))
            .get(0);

        assertThat(source.getProperty("spring.ai.openai.base-url"))
            .isEqualTo("https://api.deepseek.com");
        assertThat(source.getProperty("spring.ai.openai.chat.options.model"))
            .isEqualTo("deepseek-chat");
        assertThat(source.getProperty("spring.ai.openai.api-key"))
            .isEqualTo("sk-replace-with-your-deepseek-api-key");
    }

    @Test
    void springAiCreatesChatAndStreamingClients() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(ChatClient.class);
            assertThat(context).hasSingleBean(StreamingChatClient.class);
            assertThat(context.getBean(ChatClient.class))
                .isSameAs(context.getBean(StreamingChatClient.class));
        });
    }
}
```

- [ ] **Step 2: Run configuration tests to verify RED**

Run:

```bash
mvn -pl chat-agent-provider -am -Dtest=ChatAgentApplicationConfigurationTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: failures report the missing `com.zx.chat.agent` scan package and missing DeepSeek properties.

- [ ] **Step 3: Fix component scanning**

Change the application annotation to:

```java
@SpringBootApplication(scanBasePackages = {"com.i61", "com.zx.chat.agent"})
```

Leave Feign and MyBatis scan settings unchanged because this feature adds neither Feign clients nor mappers.

- [ ] **Step 4: Add DeepSeek properties**

Under the existing `spring` block in `application.yml`, add:

```yaml
  ai:
    openai:
      api-key: sk-replace-with-your-deepseek-api-key
      base-url: https://api.deepseek.com
      chat:
        options:
          model: deepseek-chat
      embedding:
        enabled: false
      image:
        enabled: false
```

- [ ] **Step 5: Run configuration tests to verify GREEN**

Run:

```bash
mvn -pl chat-agent-provider -am -Dtest=ChatAgentApplicationConfigurationTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: all three configuration tests pass without starting MySQL, Redis, RabbitMQ, Eureka, Apollo, or DeepSeek.

- [ ] **Step 6: Commit**

```bash
git add chat-agent-provider/src/main/java/com/zx/chat/agent/provider/ChatAgentApplication.java chat-agent-provider/src/main/resources/application.yml chat-agent-provider/src/test/java/com/zx/chat/agent/provider/ChatAgentApplicationConfigurationTest.java
git commit -m "config: connect Spring AI to DeepSeek"
```

### Task 5: Full verification and usage documentation

**Files:**
- Modify: `README.md` if it exists; otherwise create `README.md`

- [ ] **Step 1: Document local configuration and calls**

Add a concise section containing:

````markdown
## DeepSeek 对话接口

将 `chat-agent-provider/src/main/resources/application.yml` 中的
`spring.ai.openai.api-key` 替换为有效的 DeepSeek API Key，再启动 provider。

同步调用：

```bash
curl -X POST http://localhost:8081/o/v1/chat/completions \
  -H 'Content-Type: application/json' \
  -d '{"message":"用一句话介绍 Spring AI"}'
```

流式调用：

```bash
curl -N -X POST http://localhost:8081/o/v1/chat/completions/stream \
  -H 'Content-Type: application/json' \
  -H 'Accept: text/event-stream' \
  -d '{"message":"用一句话介绍 Spring AI"}'
```
````

- [ ] **Step 2: Run the complete test suite**

Run:

```bash
mvn test
```

Expected: `BUILD SUCCESS`; no test contacts DeepSeek or other external infrastructure.

- [ ] **Step 3: Verify dependency and configuration safety**

Run:

```bash
mvn -pl chat-agent-provider -am dependency:tree -Dincludes=org.springframework.ai
rg -n "sk-[A-Za-z0-9_-]{16,}" . -g '!target' -g '!docs/superpowers/**'
git diff --check
```

Expected: dependency tree resolves Spring AI artifacts at 0.8.1; secret scan returns no matches; `git diff --check` is silent.

- [ ] **Step 4: Review the final diff for scope**

Run:

```bash
git status --short
git diff --stat
git diff
```

Expected: only the files listed in this plan changed, with no unrelated refactoring or formatting.

- [ ] **Step 5: Commit documentation**

```bash
git add README.md
git commit -m "docs: document DeepSeek chat endpoints"
```
