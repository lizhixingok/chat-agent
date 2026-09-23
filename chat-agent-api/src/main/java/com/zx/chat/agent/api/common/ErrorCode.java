package com.zx.chat.agent.api.common;

import com.i61.common.bean.exception.BaseResultCode;

/**
 * 全局错误码。**只放错误**，成功不在这里。
 *
 * <p>成功响应走 {@code RespResult.succeed()}，它自带 code 与 "OK"。
 * 这里放一个 SUCCESS 常量会让 {@code new BizException(ErrorCode.SUCCESS)}
 * 这种自相矛盾的写法通过编译，所以刻意不放。
 *
 * <p>code 用于机器判断，message 是直接回给调用方的中文文案。B 端服务只服务内部
 * 与商家，不做多语言，所以文案写死在这里，不再经过 MessageSource 查表。
 *
 * <p>文案里的占位符用 {@code %s}，由 {@link com.zx.chat.agent.api.exception.BizException}
 * 按 String.format 规则填充。
 *
 * <p>通用错误的 code 复用 {@link BaseResultCode} 的数字，保持与公司其他服务一致；
 * 业务专属错误从 10001 起自行编号，新增时往后追加、不复用已删除的号。
 *
 * <p>注意：只复用 BaseResultCode 的 code，不复用它的 msg 常量 —— 文案的措辞由本服务决定。
 */
public enum ErrorCode {

    /** 兜底：未预期的异常。日志里有完整堆栈，响应里只给通用文案 */
    SYSTEM_ERROR(BaseResultCode.BASE_ERROR_CODE, "系统异常，请稍后重试"),
    /** 入参校验不通过，具体字段拼在文案后面 */
    PARAM_INVALID(BaseResultCode.VALIDATE_ERROR_CODE, "参数不合法"),
    /** 未登录或凭证失效 */
    UNAUTHORIZED(BaseResultCode.AUTH_FAILD_CODE, "未登录或登录已失效"),
    /** 已登录但无权限 */
    FORBIDDEN(BaseResultCode.FORBIDDEN_CODE, "无访问权限"),

    /** 目标资源不存在。文案带两个占位符：资源名、id */
    RESOURCE_NOT_FOUND(10001, "%s 不存在：%s"),
    /** 第三方调用失败（超时、连不上、返回非 2xx） */
    THIRD_PARTY_ERROR(10002, "第三方服务调用失败"),
    /** 分布式锁抢不到，通常是并发冲突，可提示重试 */
    LOCK_ACQUIRE_FAILED(10003, "获取锁失败，请重试");

    private final int code;
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public int getCode() {
        return code;
    }

    /** 原始文案，占位符未填充。渲染后的文案取 BizException#getMessage()。 */
    public String getMessage() {
        return message;
    }
}
