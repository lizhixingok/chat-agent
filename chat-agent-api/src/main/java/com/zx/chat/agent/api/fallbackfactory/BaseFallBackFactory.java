package com.zx.chat.agent.api.fallbackfactory;

import lombok.extern.slf4j.Slf4j;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Proxy;
import com.i61.common.bean.bean.RespResult;

/**
 * Feign Fallback 工厂基类
 * 基于 Spring Cloud Circuit Breaker (Resilience4j)
 */
@Slf4j
public abstract class BaseFallBackFactory<T> {

    private static final String FEIGN_ERROR_MSG = "服务熔断，请联系开发人员检查";

    private final Class<T> tClass;
    private final T fallbackProxy;

    @SuppressWarnings("unchecked")
    public BaseFallBackFactory() {
        tClass = (Class<T>) ((ParameterizedType) getClass().getGenericSuperclass())
            .getActualTypeArguments()[0];
        fallbackProxy = (T) Proxy.newProxyInstance(
            BaseFallBackFactory.class.getClassLoader(),
            new Class[]{tClass},
            (Object proxy, Method method, Object[] args) -> {
                log.error("Feign调用失败，返回默认响应: {}", method.getName());
                return RespResult.failed(FEIGN_ERROR_MSG);
            }
        );
    }

    public T create(Throwable cause) {
        log.error("服务熔断，请联系开发人员检查, 错误信息: {}", cause.getMessage(), cause);
        return fallbackProxy;
    }

    public T getFallback() {
        return fallbackProxy;
    }
}
