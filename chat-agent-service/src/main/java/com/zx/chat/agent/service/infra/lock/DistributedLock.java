package com.zx.chat.agent.service.infra.lock;

import com.zx.chat.agent.api.exception.LockAcquireFailedException;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * 分布式锁门面。业务只关心「锁住这个 key 做这件事」，不直接碰 Redisson API。
 *
 * <p>租约（leaseSeconds）到期锁自动释放，避免持有者宕机导致死锁。
 * 业务耗时必须小于租约，否则锁会在执行中途失效。
 */
@Component
public class DistributedLock {

    /** 统一前缀，便于在 Redis 里按 key 空间排查 */
    private static final String KEY_PREFIX = "lock:";

    private final RedissonClient redissonClient;

    public DistributedLock(RedissonClient redissonClient) {
        this.redissonClient = redissonClient;
    }

    /**
     * 加锁执行并返回结果。
     *
     * @param key          业务锁标识，内部自动加 {@code lock:} 前缀
     * @param waitSeconds  获取锁的最长等待时间
     * @param leaseSeconds 锁的租约时间，到期自动释放
     * @throws LockAcquireFailedException 等待超时或线程被中断
     */
    public <T> T execute(String key, long waitSeconds, long leaseSeconds, Supplier<T> action) {
        RLock lock = redissonClient.getLock(KEY_PREFIX + key);

        boolean locked;
        try {
            locked = lock.tryLock(waitSeconds, leaseSeconds, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            // 恢复中断标志，交给上层决定是否终止
            Thread.currentThread().interrupt();
            throw new LockAcquireFailedException(key);
        }

        if (!locked) {
            throw new LockAcquireFailedException(key);
        }

        try {
            return action.get();
        } finally {
            // 租约到期后锁可能已不属于本线程，此时 unlock 会抛 IllegalMonitorStateException
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    /**
     * 加锁执行无返回值的操作。
     */
    public void run(String key, long waitSeconds, long leaseSeconds, Runnable action) {
        execute(key, waitSeconds, leaseSeconds, () -> {
            action.run();
            return null;
        });
    }
}
