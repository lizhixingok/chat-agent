# DeepSeek Spring AI 接入设计

## 目标

在保持 Spring Boot 3.2.12 和现有多模块结构不变的前提下，通过兼容 OpenAI 协议的 Spring AI 客户端调用 DeepSeek 官方 API，对外提供同步对话和 SSE 流式对话接口。

## 范围

本次包含：

- DeepSeek 官方 API 的 Spring AI 配置。
- 无状态单轮对话服务。
- 同步对话 REST 接口。
- SSE 流式对话接口。
- Service、Controller 和配置装配测试。

本次不包含：

- 多轮会话记忆。
- Agent、RAG、工具调用、向量库或会话持久化。
- Spring Boot、Spring Cloud 或内部 i61 组件的版本升级。
- 前端聊天页面。

## 依赖选择

使用与 Spring Boot 3.2.x 兼容的 Spring AI 0.8.1 OpenAI starter，通过 OpenAI 兼容协议连接 DeepSeek。不使用需要较新 Spring Boot 版本的原生 DeepSeek starter，也不引入本次不需要的 Spring AI Alibaba Agent Framework。

Spring AI 的版本由根 `pom.xml` 统一管理，OpenAI starter 依赖放在 `chat-agent-service` 模块。

## 模块设计

### chat-agent-api

新增两个 DTO：

- `ChatReqDTO`：包含必填字段 `message`，使用标准校验注解限制为非空且最长 4000 个字符。
- `ChatRespDTO`：包含模型返回的完整文本 `content`。

请求不允许调用方传入模型名称、temperature 或 API 地址，避免将服务端模型配置暴露给外部调用方。

### chat-agent-service

新增 `AiChatService` 及其实现：

- `chat(String message)` 返回完整回答文本。
- `stream(String message)` 返回 `Flux<String>` 文本片段。

实现仅负责构造用户提示词并调用 Spring AI `ChatClient`，不维护会话状态。Controller 不直接依赖 Spring AI，以便隔离 Web 协议与模型客户端。

### chat-agent-provider

新增外部 Controller：

- `POST /o/v1/chat/completions`
  - 请求体：`ChatReqDTO`。
  - 响应：`RespResult<ChatRespDTO>`。
- `POST /o/v1/chat/completions/stream`
  - 请求体：`ChatReqDTO`。
  - 响应类型：`text/event-stream`。
  - 每个模型文本片段作为一条 SSE `message` 事件发送，正常完成后关闭连接。

Controller 只做参数校验、Service 调用和响应包装。

## 配置

在 `chat-agent-provider/src/main/resources/application.yml` 中新增：

```yaml
spring:
  ai:
    openai:
      api-key: sk-replace-with-your-deepseek-api-key
      base-url: https://api.deepseek.com
      chat:
        options:
          model: deepseek-chat
```

按用户要求将 API Key 配置项放在 `application.yml`，但仓库只保存占位值，不提交真实密钥。

## 错误处理

- 空消息或超长消息由 Bean Validation 拦截，沿用现有 `GlobalExceptionHandler` 返回参数错误。
- 同步调用的上游异常转换为项目统一异常，外部响应不包含 API Key、上游原始响应或堆栈。
- SSE 连接建立前的异常交给统一异常处理。
- SSE 已开始输出后发生异常时，发送不包含内部细节的 `error` 事件并结束连接。
- 不记录 API Key，也不新增用户提示词日志。

## 测试策略

按 TDD 顺序实现：

1. 先编写 Service 单元测试，覆盖同步回答、流式片段和上游异常，确认测试在实现前失败。
2. 实现最小 Service 代码使测试通过。
3. 编写 Controller 测试，覆盖正常同步响应、请求校验、SSE 媒体类型和片段输出。
4. 增加最小 Controller 实现并使测试通过。
5. 增加配置装配测试，使用测试密钥，不调用真实 DeepSeek API。
6. 运行 Maven 测试验证全部现有功能无回归。

## 验收标准

- 项目保持 Spring Boot 3.2.12 并可成功编译。
- 填入有效 DeepSeek API Key 后，同步接口能返回 `deepseek-chat` 的完整回答。
- 流式接口以 `text/event-stream` 返回增量文本片段。
- 无效请求被统一校验逻辑拒绝。
- 自动化测试不访问真实 DeepSeek API，不消耗 Token。
- 仓库中不包含真实 API Key。
