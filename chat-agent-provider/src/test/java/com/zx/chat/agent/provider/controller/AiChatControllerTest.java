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
            .andExpect(content().encoding("UTF-8"))
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
