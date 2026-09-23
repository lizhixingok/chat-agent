package com.zx.chat.agent.service.infra.http;

import com.zx.chat.agent.api.exception.BizException;

/**
 * 第三方 HTTP 调用失败。对外只暴露通用文案，
 * 状态码与响应体仅用于日志排查，避免把上游细节透给调用方。
 */
public class HttpClientException extends BizException {

    private static final long serialVersionUID = 1L;

    /** 对外文案。上游的状态码、响应体都不进这里。 */
    public static final String MESSAGE = "第三方服务调用失败";

    private final String detail;
    private final int statusCode;
    private final String responseBody;

    public HttpClientException(String detail, int statusCode, String responseBody, Throwable cause) {
        super(MESSAGE);
        initCause(cause);
        this.detail = detail;
        this.statusCode = statusCode;
        this.responseBody = responseBody;
    }

    /** 排查用描述，不面向终端用户。 */
    public String getDetail() {
        return detail;
    }

    /** HTTP 状态码；连接失败等没有响应的场景为 0。 */
    public int getStatusCode() {
        return statusCode;
    }

    /** 原始响应体，可能为 null。仅用于日志。 */
    public String getResponseBody() {
        return responseBody;
    }
}
