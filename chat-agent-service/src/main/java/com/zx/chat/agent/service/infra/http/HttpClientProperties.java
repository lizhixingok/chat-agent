package com.zx.chat.agent.service.infra.http;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 第三方 HTTP 调用参数。默认值偏保守：宁可快速失败，也不要把线程挂在别人家的接口上。
 */
@ConfigurationProperties(prefix = "chat-agent.http-client")
public class HttpClientProperties {

    /** 建立 TCP 连接的超时。 */
    private int connectTimeoutMillis = 3000;

    /** 读响应的超时。第三方接口再慢也不该让我们的线程等超过这个数。 */
    private int readTimeoutMillis = 10000;

    /** 从连接池借连接的等待超时。池子被打满时靠这个快速失败，而不是无限排队。 */
    private int connectionRequestTimeoutMillis = 2000;

    private int maxTotalConnections = 200;

    /** 单个目标主机的连接上限。默认 50 防止一个慢第三方吃掉整个池子。 */
    private int maxConnectionsPerRoute = 50;

    /** 空闲连接回收阈值。对端悄悄关连接是线上最常见的偶发 5xx 来源。 */
    private int idleConnectionTimeoutSeconds = 30;

    public int getConnectTimeoutMillis() {
        return connectTimeoutMillis;
    }

    public void setConnectTimeoutMillis(int connectTimeoutMillis) {
        this.connectTimeoutMillis = connectTimeoutMillis;
    }

    public int getReadTimeoutMillis() {
        return readTimeoutMillis;
    }

    public void setReadTimeoutMillis(int readTimeoutMillis) {
        this.readTimeoutMillis = readTimeoutMillis;
    }

    public int getConnectionRequestTimeoutMillis() {
        return connectionRequestTimeoutMillis;
    }

    public void setConnectionRequestTimeoutMillis(int connectionRequestTimeoutMillis) {
        this.connectionRequestTimeoutMillis = connectionRequestTimeoutMillis;
    }

    public int getMaxTotalConnections() {
        return maxTotalConnections;
    }

    public void setMaxTotalConnections(int maxTotalConnections) {
        this.maxTotalConnections = maxTotalConnections;
    }

    public int getMaxConnectionsPerRoute() {
        return maxConnectionsPerRoute;
    }

    public void setMaxConnectionsPerRoute(int maxConnectionsPerRoute) {
        this.maxConnectionsPerRoute = maxConnectionsPerRoute;
    }

    public int getIdleConnectionTimeoutSeconds() {
        return idleConnectionTimeoutSeconds;
    }

    public void setIdleConnectionTimeoutSeconds(int idleConnectionTimeoutSeconds) {
        this.idleConnectionTimeoutSeconds = idleConnectionTimeoutSeconds;
    }
}
