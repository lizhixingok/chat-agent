package com.zx.chat.agent.provider.handler;

import com.i61.common.bean.bean.RespResult;
import com.zx.chat.agent.api.common.ErrorCode;
import com.zx.chat.agent.api.exception.BizException;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

/**
 * 全局异常处理。所有异常统一转成 RespResult，文案取自 ErrorCode（中文，不做多语言）。
 *
 * <p>业务异常返回 HTTP 200（业务语义错误不是传输层错误，由 body 里的 code 表达），
 * 未预期异常返回 HTTP 500。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** 业务异常。文案在 BizException 构造时已渲染好，这里直接取。 */
    @ExceptionHandler(BizException.class)
    public ResponseEntity<RespResult<Void>> handleBizException(BizException ex) {
        ErrorCode code = ex.getErrorCode();
        log.warn("business exception: code={} msg={}", code.getCode(), ex.getMessage());
        return respond(HttpStatus.OK, code.getCode(), ex.getMessage());
    }

    /** @RequestBody 上的 @Valid 校验失败。 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<RespResult<Void>> handleValidation(MethodArgumentNotValidException ex) {
        return paramInvalid(describe(ex.getBindingResult()));
    }

    /** 表单/query 参数绑定失败。 */
    @ExceptionHandler(BindException.class)
    public ResponseEntity<RespResult<Void>> handleBindException(BindException ex) {
        return paramInvalid(describe(ex.getBindingResult()));
    }

    /** 方法参数上的约束校验失败（@Validated 配合 @NotNull 等）。 */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<RespResult<Void>> handleConstraintViolation(ConstraintViolationException ex) {
        return paramInvalid(ex.getMessage());
    }

    /** 请求体无法解析，比如 JSON 语法错误。 */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<RespResult<Void>> handleUnreadable(HttpMessageNotReadableException ex) {
        // 请求体格式错误的原始信息含 Jackson 内部细节，不回传给调用方
        log.warn("unreadable request body: {}", ex.getMessage());
        return paramInvalid(null);
    }

    /** 兜底分支。任何未被上面接住的异常都走这里。 */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<RespResult<Void>> handleAny(Exception ex) {
        // 未预期异常必须记完整堆栈，但响应里只给通用文案，避免泄漏连接串、密码等内部信息
        log.error("unhandled exception", ex);
        return respond(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.SYSTEM_ERROR.getCode(),
            ErrorCode.SYSTEM_ERROR.getMessage());
    }

    /** detail 为 null 时用通用文案，否则拼在通用文案之后。 */
    private ResponseEntity<RespResult<Void>> paramInvalid(String detail) {
        String base = ErrorCode.PARAM_INVALID.getMessage();
        String msg = (detail == null || detail.isBlank()) ? base : base + ": " + detail;
        return respond(HttpStatus.OK, ErrorCode.PARAM_INVALID.getCode(), msg);
    }

    /** 把校验结果拼成一行，字段名 + 消息，多个用分号隔开。 */
    private String describe(BindingResult binding) {
        return binding.getAllErrors().stream()
            .map(err -> err instanceof FieldError fe
                ? fe.getField() + " " + fe.getDefaultMessage()
                : err.getDefaultMessage())
            .collect(Collectors.joining("; "));
    }

    /** 统一出口。错误码与文案放 body，HTTP 状态由调用方指定。 */
    private ResponseEntity<RespResult<Void>> respond(HttpStatus status, int code, String msg) {
        return new ResponseEntity<>(RespResult.failed(code, msg), status);
    }
}
