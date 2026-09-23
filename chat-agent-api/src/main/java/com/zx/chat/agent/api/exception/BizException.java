package com.zx.chat.agent.api.exception;

import com.i61.common.bean.exception.BaseResultCode;

/**
 * 业务异常。code 用于机器判断，message 是直接回给调用方的中文文案，
 * GlobalExceptionHandler 取这两个值拼成 RespResult。
 *
 * <p>code 一律取自 {@link BaseResultCode}，不再自建错误码表。判断标准是
 * 「调用方会不会因为这个码走不同的代码分支」：
 * <ul>
 *   <li>会 —— 比如登录态失效要跳登录页，用 {@link BaseResultCode#AUTH_FAILD_CODE} 这类明确的码；</li>
 *   <li>不会 —— 调用方只会把 message 弹出来，走默认的
 *       {@link BaseResultCode#BASE_ERROR_CODE}(101)，文案说清楚就够了。</li>
 * </ul>
 *
 * <p>B 端服务不做多语言，文案在抛出点写死中文，带变量的直接字符串拼接。
 */
public class BizException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final int code;

    /** 通用业务错误：调用方不分支，只弹 message。 */
    public BizException(String message) {
        this(BaseResultCode.BASE_ERROR_CODE, message);
    }

    /** 调用方需要按 code 走不同分支时用，code 取 BaseResultCode 的常量。 */
    public BizException(int code, String message) {
        super(message);
        this.code = code;
    }

    public int getCode() {
        return code;
    }
}
