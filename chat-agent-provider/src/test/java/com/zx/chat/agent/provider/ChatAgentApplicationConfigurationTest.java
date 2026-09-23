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
        assertThat(source.getProperty("spring.ai.openai.embedding.enabled"))
            .isEqualTo(false);
        assertThat(source.getProperty("spring.ai.openai.image.enabled"))
            .isEqualTo(false);
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
