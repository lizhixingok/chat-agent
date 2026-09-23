package com.zx.chat.agent.provider.controller;

import com.zx.chat.agent.api.common.ErrorCode;
import com.zx.chat.agent.provider.controller.outer.DemoController;
import com.zx.chat.agent.provider.handler.GlobalExceptionHandler;
import com.zx.chat.agent.service.service.impl.DemoServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * DemoController 的 HTTP 层测试。
 *
 * <p>用 standaloneSetup 而不是 @SpringBootTest：只装 Controller、Service 与
 * 全局异常处理，不起完整容器，因此不依赖 MySQL/Redis/RabbitMQ/Eureka。
 *
 * <p>注意 standaloneSetup 不会加载 JacksonConfig，所以这里只断言 code/msg
 * 与业务字段，不断言 Long 转字符串、时间格式 —— 那些由 JacksonConfigTest 覆盖。
 */
class DemoControllerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
            .standaloneSetup(new DemoController(new DemoServiceImpl()))
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
    }

    @Test
    void pingReturnsPong() throws Exception {
        mockMvc.perform(get("/demo/ping"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data").value("pong"));
    }

    @Test
    void createReturnsGeneratedIdAndTimestamp() throws Exception {
        mockMvc.perform(post("/demo/create")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"alice\",\"score\":88}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.id").exists())
            .andExpect(jsonPath("$.data.name").value("alice"))
            .andExpect(jsonPath("$.data.score").value(88))
            .andExpect(jsonPath("$.data.createdAt").exists());
    }

    @Test
    void blankNameIsRejectedWithParamInvalidCode() throws Exception {
        mockMvc.perform(post("/demo/create")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"\",\"score\":10}"))
            // 业务语义错误仍是 HTTP 200，错误在 body 的 code 里
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(ErrorCode.PARAM_INVALID.getCode()));
    }

    @Test
    void scoreOutOfRangeIsRejected() throws Exception {
        mockMvc.perform(post("/demo/create")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"bob\",\"score\":999}"))
            .andExpect(jsonPath("$.code").value(ErrorCode.PARAM_INVALID.getCode()));
    }

    @Test
    void malformedJsonIsRejected() throws Exception {
        mockMvc.perform(post("/demo/create")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":"))
            .andExpect(jsonPath("$.code").value(ErrorCode.PARAM_INVALID.getCode()));
    }

    @Test
    void missingIdReturnsResourceNotFound() throws Exception {
        mockMvc.perform(get("/demo/999"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(ErrorCode.RESOURCE_NOT_FOUND.getCode()))
            .andExpect(jsonPath("$.msg").value("Demo 不存在：999"));
    }

    @Test
    void createdRecordIsRetrievableById() throws Exception {
        mockMvc.perform(post("/demo/create")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"name\":\"carol\",\"score\":50}"));

        mockMvc.perform(get("/demo/1"))
            .andExpect(jsonPath("$.data.name").value("carol"));
    }

    @Test
    void listStartsEmpty() throws Exception {
        mockMvc.perform(get("/demo/listAll"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data").isArray())
            .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    void unexpectedExceptionReturns500WithGenericMessage() throws Exception {
        mockMvc.perform(get("/demo/boom"))
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.code").value(ErrorCode.SYSTEM_ERROR.getCode()))
            .andExpect(jsonPath("$.msg").value("系统异常，请稍后重试"))
            // 兜底分支绝不能把原始异常信息带出去
            .andExpect(jsonPath("$.msg").value(
                org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("intentional"))));
    }
}
