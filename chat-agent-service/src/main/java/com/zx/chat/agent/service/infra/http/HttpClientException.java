package com.zx.chat.agent.service.infra.http;

import com.zx.chat.agent.api.common.ErrorCode;
import com.zx.chat.agent.api.exception.BizException;

/**
 * 第三方 HTTP 调用失败。统一挂在 THIRD_PARTY_ERROR 错误码下，
 * 对外只暴露错误码文案，状态码与响应体仅用于日志排查。
 */
public class HttpClientException extends BizException {

    private static final long serialVersionUID = 1L;

    private final String detail;
    private final int statusCode;
    private final String responseBody;

    public HttpClientException(String detail, int statusCode, String responseBody, Throwable cause) {
        super(ErrorCode.THIRD_PARTY_ERROR);
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
