package com.zx.chat.agent.provider.controller;

import com.i61.common.bean.exception.BaseResultCode;
import com.zx.chat.agent.api.exception.BizException;
import com.zx.chat.agent.provider.controller.outer.AiChatController;
import com.zx.chat.agent.provider.filter.RequestLogFilter;
import com.zx.chat.agent.provider.handler.GlobalExceptionHandler;
import com.zx.chat.agent.service.service.AiChatService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.filter.CharacterEncodingFilter;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
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
            .addFilters(new CharacterEncodingFilter("UTF-8", true), new RequestLogFilter())
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
            .andExpect(jsonPath("$.code").value(BaseResultCode.VALIDATE_ERROR_CODE));
    }

    @ParameterizedTest
    @MethodSource("invalidStreamRequests")
    void invalidStreamMessageReturnsUnifiedJsonEvenWhenSseIsAccepted(String body) throws Exception {
        mockMvc.perform(post("/o/v1/chat/completions/stream")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .content(body))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.code").value(BaseResultCode.VALIDATE_ERROR_CODE))
            .andExpect(jsonPath("$.msg").value(org.hamcrest.Matchers.containsString("message")));

        verifyNoInteractions(aiChatService);
    }

    private static Stream<String> invalidStreamRequests() {
        return Stream.of("{\"message\":\" \"}", "{}", "{\"message\":\"" + "x".repeat(4001) + "\"}");
    }

    @Test
    void streamDeliversChunksEmittedAfterInitialRequestReturns() throws Exception {
        Sinks.Many<String> chunks = Sinks.many().unicast().onBackpressureBuffer();
        when(aiChatService.stream("你好")).thenReturn(chunks.asFlux());

        MvcResult pending = mockMvc.perform(post("/o/v1/chat/completions/stream")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .content("{\"message\":\"你好\"}"))
            .andExpect(request().asyncStarted())
            .andReturn();

        assertThat(pending.getResponse().getContentAsString()).isEmpty();
        assertThat(chunks.tryEmitNext("你")).isEqualTo(Sinks.EmitResult.OK);
        assertThat(chunks.tryEmitNext("好")).isEqualTo(Sinks.EmitResult.OK);
        assertThat(chunks.tryEmitComplete()).isEqualTo(Sinks.EmitResult.OK);

        mockMvc.perform(asyncDispatch(pending))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM))
            .andExpect(content().encoding("UTF-8"))
            .andExpect(content().string("event:message\ndata:你\n\nevent:message\ndata:好\n\n"));
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
            .andExpect(content().encoding("UTF-8"))
            .andExpect(content().string("event:message\ndata:你\n\nevent:message\ndata:好\n\n"));
    }

    @Test
    void streamConvertsUpstreamFailureToSafeErrorEvent() throws Exception {
        when(aiChatService.stream("你好"))
            .thenReturn(Flux.error(new BizException("第三方服务调用失败")));

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

    @Test
    void streamConvertsSynchronousServiceFailureToSafeErrorEvent() throws Exception {
        when(aiChatService.stream("你好"))
            .thenThrow(new BizException("第三方服务调用失败"));

        MvcResult pending = mockMvc.perform(post("/o/v1/chat/completions/stream")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .content("{\"message\":\"你好\"}"))
            .andExpect(request().asyncStarted())
            .andReturn();

        mockMvc.perform(asyncDispatch(pending))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM))
            .andExpect(content().string("event:error\ndata:AI 服务暂时不可用\n\n"));
    }
}
