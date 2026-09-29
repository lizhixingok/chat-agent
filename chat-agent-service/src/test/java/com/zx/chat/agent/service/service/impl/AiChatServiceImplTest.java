package com.zx.chat.agent.service.service.impl;

import com.i61.common.bean.exception.BaseResultCode;
import com.zx.chat.agent.api.exception.BizException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import reactor.core.publisher.Flux;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AiChatServiceImplTest {

    private ChatClient chatClient;
    private ChatClient.Builder chatClientBuilder;
    private ChatClient.ChatClientRequestSpec requestSpec;
    private ChatClient.CallResponseSpec callResponseSpec;
    private ChatClient.StreamResponseSpec streamResponseSpec;
    private AiChatServiceImpl service;

    @BeforeEach
    void setUp() {
        chatClient = mock(ChatClient.class);
        chatClientBuilder = mock(ChatClient.Builder.class);
        requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
        callResponseSpec = mock(ChatClient.CallResponseSpec.class);
        streamResponseSpec = mock(ChatClient.StreamResponseSpec.class);
        when(chatClientBuilder.build()).thenReturn(chatClient);
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.user("你好")).thenReturn(requestSpec);
        service = new AiChatServiceImpl(chatClientBuilder);
    }

    @Test
    void chatReturnsCompleteModelAnswer() {
        when(requestSpec.call()).thenReturn(callResponseSpec);
        when(callResponseSpec.content()).thenReturn("你好，我是 DeepSeek");

        assertThat(service.chat("你好")).isEqualTo("你好，我是 DeepSeek");
    }

    @Test
    void streamReturnsNonEmptyModelChunks() {
        when(requestSpec.stream()).thenReturn(streamResponseSpec);
        when(streamResponseSpec.content()).thenReturn(Flux.just("你", "", "好"));

        assertThat(service.stream("你好").collectList().block())
            .isEqualTo(List.of("你", "好"));
    }

    @Test
    void chatHidesUpstreamFailureDetails() {
        when(requestSpec.call())
            .thenThrow(new IllegalStateException("upstream body with secret"));

        assertThatThrownBy(() -> service.chat("你好"))
            .isInstanceOf(BizException.class)
            .satisfies(ex -> assertThat(((BizException) ex).getCode())
                .isEqualTo(BaseResultCode.BASE_ERROR_CODE))
            .hasMessage("第三方服务调用失败")
            .hasMessageNotContaining("secret");
    }

    @Test
    void streamMapsAsynchronousFailureToBusinessException() {
        when(requestSpec.stream()).thenReturn(streamResponseSpec);
        when(streamResponseSpec.content())
            .thenReturn(Flux.error(new IllegalStateException("secret stream failure")));

        assertThatThrownBy(() -> service.stream("你好").blockLast())
            .isInstanceOf(BizException.class)
            .satisfies(ex -> assertThat(((BizException) ex).getCode())
                .isEqualTo(BaseResultCode.BASE_ERROR_CODE))
            .hasMessage("第三方服务调用失败")
            .hasMessageNotContaining("secret");
    }
}
