package com.zx.chat.agent.api.exception;

/**
 * 分布式锁获取失败。等待超时或线程被中断时抛出。
 *
 * <p>调用方要区分时按异常类型 catch，不靠错误码，所以走通用码 + 文案。
 */
public class LockAcquireFailedException extends BizException {

    private static final long serialVersionUID = 1L;

    /** 对外文案，lockKey 属于内部信息不外泄，只留在字段里供日志用。 */
    private static final String MESSAGE = "获取锁失败，请重试";

    private final String lockKey;

    public LockAcquireFailedException(String lockKey) {
        super(MESSAGE);
        this.lockKey = lockKey;
    }

    /** 抢锁失败的业务 key，日志里直接可见。 */
    public String getLockKey() {
        return lockKey;
    }
}
