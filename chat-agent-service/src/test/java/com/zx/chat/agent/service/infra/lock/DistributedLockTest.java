package com.zx.chat.agent.service.infra.lock;

import com.zx.chat.agent.api.common.ErrorCode;
import com.zx.chat.agent.api.exception.LockAcquireFailedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 用 mock 的 RedissonClient 验证加锁语义。沙箱禁止监听端口，无法起真实/嵌入式 Redis。
 */
@ExtendWith(MockitoExtension.class)
class DistributedLockTest {

    @Mock
    private RedissonClient redissonClient;

    @Mock
    private RLock rLock;

    private DistributedLock distributedLock;

    @BeforeEach
    void setUp() {
        distributedLock = new DistributedLock(redissonClient);
    }

    @Test
    void shouldPrefixLockKey() throws InterruptedException {
        when(redissonClient.getLock("lock:draw:1")).thenReturn(rLock);
        when(rLock.tryLock(anyLong(), anyLong(), eq(TimeUnit.SECONDS))).thenReturn(true);

        distributedLock.execute("draw:1", 3, 10, () -> "ok");

        verify(redissonClient).getLock("lock:draw:1");
    }

    @Test
    void shouldReturnActionResultWhenLockAcquired() throws InterruptedException {
        when(redissonClient.getLock("lock:draw:1")).thenReturn(rLock);
        when(rLock.tryLock(anyLong(), anyLong(), eq(TimeUnit.SECONDS))).thenReturn(true);

        String result = distributedLock.execute("draw:1", 3, 10, () -> "ok");

        assertThat(result).isEqualTo("ok");
    }

    @Test
    void shouldPassWaitAndLeaseSecondsToRedisson() throws InterruptedException {
        when(redissonClient.getLock("lock:draw:1")).thenReturn(rLock);
        when(rLock.tryLock(5L, 30L, TimeUnit.SECONDS)).thenReturn(true);

        distributedLock.execute("draw:1", 5, 30, () -> "ok");

        verify(rLock).tryLock(5L, 30L, TimeUnit.SECONDS);
    }

    @Test
    void shouldUnlockWhenHeldByCurrentThread() throws InterruptedException {
        when(redissonClient.getLock("lock:draw:1")).thenReturn(rLock);
        when(rLock.tryLock(anyLong(), anyLong(), eq(TimeUnit.SECONDS))).thenReturn(true);
        when(rLock.isHeldByCurrentThread()).thenReturn(true);

        distributedLock.execute("draw:1", 3, 10, () -> "ok");

        verify(rLock).unlock();
    }

    @Test
    void shouldNotUnlockWhenLeaseAlreadyExpired() throws InterruptedException {
        when(redissonClient.getLock("lock:draw:1")).thenReturn(rLock);
        when(rLock.tryLock(anyLong(), anyLong(), eq(TimeUnit.SECONDS))).thenReturn(true);
        when(rLock.isHeldByCurrentThread()).thenReturn(false);

        distributedLock.execute("draw:1", 3, 10, () -> "ok");

        verify(rLock, never()).unlock();
    }

    @Test
    void shouldUnlockWhenActionThrows() throws InterruptedException {
        when(redissonClient.getLock("lock:draw:1")).thenReturn(rLock);
        when(rLock.tryLock(anyLong(), anyLong(), eq(TimeUnit.SECONDS))).thenReturn(true);
        when(rLock.isHeldByCurrentThread()).thenReturn(true);

        assertThatThrownBy(() -> distributedLock.execute("draw:1", 3, 10, () -> {
            throw new IllegalStateException("boom");
        })).isInstanceOf(IllegalStateException.class);

        verify(rLock).unlock();
    }

    @Test
    void shouldThrowLockAcquireFailedWhenTryLockReturnsFalse() throws InterruptedException {
        when(redissonClient.getLock("lock:draw:1")).thenReturn(rLock);
        when(rLock.tryLock(anyLong(), anyLong(), eq(TimeUnit.SECONDS))).thenReturn(false);

        assertThatThrownBy(() -> distributedLock.execute("draw:1", 3, 10, () -> "ok"))
                .isInstanceOf(LockAcquireFailedException.class)
                .extracting(e -> ((LockAcquireFailedException) e).getErrorCode())
                .isEqualTo(ErrorCode.LOCK_ACQUIRE_FAILED);
    }

    @Test
    void shouldNotRunActionWhenLockNotAcquired() throws InterruptedException {
        when(redissonClient.getLock("lock:draw:1")).thenReturn(rLock);
        when(rLock.tryLock(anyLong(), anyLong(), eq(TimeUnit.SECONDS))).thenReturn(false);
        AtomicBoolean executed = new AtomicBoolean(false);

        assertThatThrownBy(() -> distributedLock.execute("draw:1", 3, 10, () -> {
            executed.set(true);
            return "ok";
        })).isInstanceOf(LockAcquireFailedException.class);

        assertThat(executed).isFalse();
        verify(rLock, never()).unlock();
    }

    @Test
    void shouldCarryLockKeyInExceptionArgs() throws InterruptedException {
        when(redissonClient.getLock("lock:draw:1")).thenReturn(rLock);
        when(rLock.tryLock(anyLong(), anyLong(), eq(TimeUnit.SECONDS))).thenReturn(false);

        assertThatThrownBy(() -> distributedLock.execute("draw:1", 3, 10, () -> "ok"))
                .isInstanceOf(LockAcquireFailedException.class)
                .satisfies(e -> {
                    LockAcquireFailedException ex = (LockAcquireFailedException) e;
                    assertThat(ex.getLockKey()).isEqualTo("draw:1");
                    assertThat(ex.getArgs()).containsExactly("draw:1");
                });
    }

    @Test
    void shouldRestoreInterruptFlagWhenTryLockInterrupted() throws InterruptedException {
        when(redissonClient.getLock("lock:draw:1")).thenReturn(rLock);
        when(rLock.tryLock(anyLong(), anyLong(), eq(TimeUnit.SECONDS)))
                .thenThrow(new InterruptedException("interrupted"));

        try {
            assertThatThrownBy(() -> distributedLock.execute("draw:1", 3, 10, () -> "ok"))
                    .isInstanceOf(LockAcquireFailedException.class);

            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        } finally {
            // 清掉中断标志，避免污染同一线程上的后续测试
            Thread.interrupted();
        }
    }

    @Test
    void shouldRunRunnableUnderLock() throws InterruptedException {
        when(redissonClient.getLock("lock:draw:1")).thenReturn(rLock);
        when(rLock.tryLock(anyLong(), anyLong(), eq(TimeUnit.SECONDS))).thenReturn(true);
        when(rLock.isHeldByCurrentThread()).thenReturn(true);
        AtomicBoolean executed = new AtomicBoolean(false);

        distributedLock.run("draw:1", 3, 10, () -> executed.set(true));

        assertThat(executed).isTrue();
        verify(rLock).unlock();
    }
}
