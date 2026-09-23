package com.zx.chat.agent.api.exception;

import com.zx.chat.agent.api.common.ErrorCode;

/**
 * 分布式锁获取失败。等待超时或线程被中断时抛出。
 */
public class LockAcquireFailedException extends BizException {

    private static final long serialVersionUID = 1L;

    private final String lockKey;

    public LockAcquireFailedException(String lockKey) {
        super(ErrorCode.LOCK_ACQUIRE_FAILED, lockKey);
        this.lockKey = lockKey;
    }

    /** 抢锁失败的业务 key，日志里直接可见。 */
    public String getLockKey() {
        return lockKey;
    }
}
