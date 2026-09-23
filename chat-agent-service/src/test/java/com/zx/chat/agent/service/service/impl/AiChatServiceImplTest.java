package com.zx.chat.agent.service.service.impl;

import com.i61.common.bean.exception.BaseResultCode;
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
            .satisfies(ex -> assertThat(((BizException) ex).getCode())
                .isEqualTo(BaseResultCode.BASE_ERROR_CODE))
            .hasMessage("第三方服务调用失败")
            .hasMessageNotContaining("secret");
    }

    @Test
    void streamMapsAsynchronousFailureToBusinessException() {
        when(streamingChatClient.stream("你好"))
            .thenReturn(Flux.error(new IllegalStateException("secret stream failure")));

        assertThatThrownBy(() -> service.stream("你好").blockLast())
            .isInstanceOf(BizException.class)
            .satisfies(ex -> assertThat(((BizException) ex).getCode())
                .isEqualTo(BaseResultCode.BASE_ERROR_CODE))
            .hasMessage("第三方服务调用失败")
            .hasMessageNotContaining("secret");
    }
}
