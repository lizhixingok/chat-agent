package com.zx.chat.agent.api.exception;

import com.zx.chat.agent.api.common.ErrorCode;

/**
 * 业务异常。文案在构造时就渲染好，GlobalExceptionHandler 直接取 getMessage() 回给调用方。
 *
 * <p>args 填充 {@link ErrorCode} 文案里的 {@code %s} 占位符，
 * 个数对不上时退回未渲染的原始文案，不让格式化异常盖掉真正的业务错误。
 */
public class BizException extends RuntimeException {

    private static final long serialVersionUID = 1L;
    private static final Object[] NO_ARGS = new Object[0];

    private final ErrorCode errorCode;
    private final Object[] args;

    public BizException(ErrorCode errorCode) {
        this(errorCode, NO_ARGS);
    }

    public BizException(ErrorCode errorCode, Object... args) {
        super(render(errorCode, args));
        this.errorCode = errorCode;
        this.args = (args == null || args.length == 0) ? NO_ARGS : args.clone();
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }

    public Object[] getArgs() {
        return args.clone();
    }

    /** 用 args 填充文案占位符。无参或格式化失败时返回原始文案。 */
    private static String render(ErrorCode errorCode, Object[] args) {
        String template = errorCode.getMessage();
        if (args == null || args.length == 0) {
            return template;
        }
        try {
            return String.format(template, args);
        } catch (RuntimeException ignored) {
            // 占位符与 args 个数/类型不匹配。文案不完美好过抛异常掩盖原始业务错误
            return template;
        }
    }
}
