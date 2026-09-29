package com.zx.chat.agent.provider;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.model.chat.client.autoconfigure.ChatClientAutoConfiguration;
import org.springframework.ai.model.openai.autoconfigure.OpenAiChatAutoConfiguration;
import org.springframework.ai.model.tool.autoconfigure.ToolCallingAutoConfiguration;
import org.springframework.ai.retry.autoconfigure.SpringAiRetryAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.restclient.autoconfigure.RestClientAutoConfiguration;
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
            ToolCallingAutoConfiguration.class,
            OpenAiChatAutoConfiguration.class,
            ChatClientAutoConfiguration.class))
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
            .isEqualTo("${DEEPSEEK_API_KEY:sk-replace-with-your-deepseek-api-key}");
        assertThat(source.getProperty("spring.ai.openai.embedding.enabled"))
            .isEqualTo(false);
        assertThat(source.getProperty("spring.ai.openai.image.enabled"))
            .isEqualTo(false);
    }

    @Test
    void springAiCreatesChatModelAndClientBuilder() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(ChatModel.class);
            assertThat(context).hasSingleBean(ChatClient.Builder.class);
        });
    }

    @Test
    void applicationYamlBindsJacksonProperties() throws IOException {
        PropertySource<?> source = new YamlPropertySourceLoader()
            .load("application", new ClassPathResource("application.yml"))
            .get(0);

        new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(JacksonAutoConfiguration.class))
            .withInitializer(context -> context.getEnvironment().getPropertySources().addFirst(source))
            .run(context -> assertThat(context).hasNotFailed());
    }
}
