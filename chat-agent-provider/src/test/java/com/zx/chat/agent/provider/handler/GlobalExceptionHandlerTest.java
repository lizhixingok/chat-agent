package com.zx.chat.agent.provider.handler;

import com.i61.common.bean.bean.RespResult;
import com.zx.chat.agent.api.common.ErrorCode;
import com.zx.chat.agent.api.exception.BizException;
import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindException;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
    }

    @Test
    void bizExceptionCarriesErrorCodeNumberAndText() {
        ResponseEntity<RespResult<Void>> res =
            handler.handleBizException(new BizException(ErrorCode.LOCK_ACQUIRE_FAILED));

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(res.getBody().getCode()).isEqualTo(ErrorCode.LOCK_ACQUIRE_FAILED.getCode());
        assertThat(res.getBody().getMsg()).isEqualTo("获取锁失败，请重试");
    }

    @Test
    void bizExceptionSubstitutesArgsIntoText() {
        ResponseEntity<RespResult<Void>> res = handler.handleBizException(
            new BizException(ErrorCode.RESOURCE_NOT_FOUND, "奖品", 42L));

        assertThat(res.getBody().getMsg()).isEqualTo("奖品 不存在：42");
    }

    @Test
    void bizExceptionWithoutArgsUsesPlainText() {
        ResponseEntity<RespResult<Void>> res =
            handler.handleBizException(new BizException(ErrorCode.PARAM_INVALID));

        assertThat(res.getBody().getMsg()).isEqualTo("参数不合法");
    }

    @Test
    void methodArgumentNotValidReportsFieldAndMessage() throws Exception {
        BindingResult binding = new BeanPropertyBindingResult(new Object(), "payload");
        binding.rejectValue(null, "NotBlank", "must not be blank");
        // MethodArgumentNotValidException 需要一个 MethodParameter，用本测试类的方法凑
        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(
            new org.springframework.core.MethodParameter(
                GlobalExceptionHandlerTest.class.getDeclaredMethod("dummyTarget", String.class), 0),
            binding);

        ResponseEntity<RespResult<Void>> res = handler.handleValidation(ex);

        assertThat(res.getBody().getCode()).isEqualTo(ErrorCode.PARAM_INVALID.getCode());
        assertThat(res.getBody().getMsg()).contains("must not be blank");
    }

    @Test
    void bindExceptionUsesParamInvalidCode() {
        BindingResult binding = new BeanPropertyBindingResult(new Object(), "query");
        binding.rejectValue(null, "Range", "must be between 1 and 100");

        ResponseEntity<RespResult<Void>> res = handler.handleBindException(new BindException(binding));

        assertThat(res.getBody().getCode()).isEqualTo(ErrorCode.PARAM_INVALID.getCode());
        assertThat(res.getBody().getMsg()).contains("must be between 1 and 100");
    }

    @Test
    void constraintViolationUsesParamInvalidCode() {
        ResponseEntity<RespResult<Void>> res =
            handler.handleConstraintViolation(new ConstraintViolationException("pageSize: must be positive", Set.of()));

        assertThat(res.getBody().getCode()).isEqualTo(ErrorCode.PARAM_INVALID.getCode());
    }

    @Test
    void malformedJsonUsesParamInvalidCodeAndGenericText() {
        ResponseEntity<RespResult<Void>> res = handler.handleUnreadable(
            new HttpMessageNotReadableException("Unexpected end-of-input", (org.springframework.http.HttpInputMessage) null));

        assertThat(res.getBody().getCode()).isEqualTo(ErrorCode.PARAM_INVALID.getCode());
        assertThat(res.getBody().getMsg()).isEqualTo("参数不合法");
    }

    @Test
    void unexpectedExceptionNeverLeaksInternalDetails() {
        ResponseEntity<RespResult<Void>> res = handler.handleAny(
            new IllegalStateException("jdbc:mysql://prod-db:3306 connection refused, password=secret"));

        assertThat(res.getBody().getCode()).isEqualTo(ErrorCode.SYSTEM_ERROR.getCode());
        assertThat(res.getBody().getMsg()).isEqualTo("系统异常，请稍后重试");
        assertThat(res.getBody().getMsg()).doesNotContain("jdbc", "password", "secret");
    }

    @Test
    void unexpectedExceptionReturnsHttp500() {
        ResponseEntity<RespResult<Void>> res = handler.handleAny(new RuntimeException("boom"));

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    /** 仅用于构造 MethodParameter，不会被调用。 */
    @SuppressWarnings("unused")
    private void dummyTarget(String payload) {
    }
}
