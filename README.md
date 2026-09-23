# chat-agent

chat-agent - Spring Boot 多模块项目

## 项目结构

```
chat-agent/
├── chat-agent-api/          # API层 - Feign接口定义、DTO
├── chat-agent-service/      # 业务层 - Service、Mapper、Domain
├── chat-agent-provider/     # 提供层 - Controller、启动入口
└── pom.xml                       # 父POM
```

## 技术栈

- Java 21
- Spring Boot 3.2.12
- Spring Cloud 2023.0.3
- MyBatis + PageHelper
- Knife4j (OpenAPI 3)
- Log4j2

## 可选组件

- Apollo 配置中心
- Eureka 服务发现
- RabbitMQ 消息队列
- Swagger/Knife4j API文档
- Log4j2 日志框架

## 快速开始

### 环境要求

- JDK 21+
- Maven 3.6+
- MySQL 8.0+（如使用数据库）

### 构建项目

```bash
# Windows
mvnw.cmd clean install

# Linux/Mac
./mvnw clean install
```

### 配置修改

修改 `chat-agent-provider/src/main/resources/application.yml`：

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/happy_draw_demo
    username: root
    password: root
```

### 运行项目

```bash
# Windows
mvnw.cmd spring-boot:run -pl chat-agent-provider

# Linux/Mac
./mvnw spring-boot:run -pl chat-agent-provider
```

### 打包部署

```bash
# Windows
mvnw.cmd clean package -DskipTests

# Linux/Mac
./mvnw clean package -DskipTests
```

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

## API文档

启动后访问：http://localhost:8081/doc.html

## 配置说明

### 端口配置

默认端口：8081，可在 application.yml 中修改。

### Apollo 配置中心

修改 `bootstrap.yml` 中的 Apollo 地址：

```yaml
apollo:
  meta: http://apollo-dev.i61.cn:9012
```

### Eureka 配置

修改 `application.yml` 中的 Eureka 地址：

```yaml
eureka:
  client:
    service-url:
      defaultZone: http://localhost:8761/eureka/
```

### RabbitMQ 配置

修改 `application.yml` 中的 RabbitMQ 连接信息：

```yaml
spring:
  rabbitmq:
    host: localhost
    port: 5672
    username: guest
    password: guest
    virtual-host: /
```

## 目录说明

| 目录 | 说明 |
|------|------|
| com.zx.chat.agent.api | API层 - Feign接口、DTO |
| com.zx.chat.agent.service | 业务层 - Service、Mapper、Model |
| com.zx.chat.agent.provider | 提供层 - Controller、Application |

## 构建命令

| 命令 | 说明 |
|------|------|
| mvnw clean install | 构建所有模块 |
| mvnw test | 运行测试 |
| mvnw spring-boot:run -pl chat-agent-provider | 启动应用 |
| mvnw clean package -DskipTests | 打包（跳过测试） |

## 日志说明

日志文件位置：`logs/`

- `chat-agent.log` - 业务日志
- `chat-agent-error.log` - 错误日志
- `chat-agent-sql.log` - SQL日志

日志按天滚动，保留30天。

## 包路径规范

- 基础包名：`com.zx.chat.agent`
- API层：`com.zx.chat.agent.api`
- Service层：`com.zx.chat.agent.service`
- Provider层：`com.zx.chat.agent.provider`

## 内部API路径规范

- 内部API（Feign）：`/i/v1/`
- 外部API（REST）：业务路径（如 `/draw`）
