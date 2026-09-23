package com.zx.chat.agent.service.infra.http;

import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.core5.util.TimeValue;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.util.concurrent.TimeUnit;

/**
 * 出站 HTTP 调用的连接池与客户端装配。
 */
@Configuration
@EnableConfigurationProperties(HttpClientProperties.class)
public class HttpClientConfig {

    /** 出站调用专用的 RestTemplate bean 名，避免和业务自建的 RestTemplate 混淆。 */
    public static final String OUTBOUND_REST_TEMPLATE = "outboundRestTemplate";

    /** 连接池。connect/socket 超时属于 ConnectionConfig，不在 RequestConfig 里。 */
    @Bean
    public PoolingHttpClientConnectionManager outboundConnectionManager(HttpClientProperties props) {
        ConnectionConfig connectionConfig = ConnectionConfig.custom()
            .setConnectTimeout(props.getConnectTimeoutMillis(), TimeUnit.MILLISECONDS)
            .setSocketTimeout(props.getReadTimeoutMillis(), TimeUnit.MILLISECONDS)
            .build();

        return PoolingHttpClientConnectionManagerBuilder.create()
            .setMaxConnTotal(props.getMaxTotalConnections())
            .setMaxConnPerRoute(props.getMaxConnectionsPerRoute())
            .setDefaultConnectionConfig(connectionConfig)
            .build();
    }

    /** HTTP 客户端。两种空闲连接回收都要开，对端静默关连接是偶发 5xx 的常见来源。 */
    @Bean(destroyMethod = "close")
    public CloseableHttpClient outboundHttpClient(HttpClientProperties props,
                                                 PoolingHttpClientConnectionManager connectionManager) {
        RequestConfig requestConfig = RequestConfig.custom()
            .setConnectionRequestTimeout(props.getConnectionRequestTimeoutMillis(), TimeUnit.MILLISECONDS)
            .build();

        return HttpClients.custom()
            .setConnectionManager(connectionManager)
            .setDefaultRequestConfig(requestConfig)
            .evictIdleConnections(TimeValue.ofSeconds(props.getIdleConnectionTimeoutSeconds()))
            .evictExpiredConnections()
            .build();
    }

    /** 把 HttpClient5 接到 Spring 的请求工厂抽象上。 */
    @Bean
    public ClientHttpRequestFactory outboundRequestFactory(CloseableHttpClient httpClient) {
        return new HttpComponentsClientHttpRequestFactory(httpClient);
    }

    /** 出站专用 RestTemplate。 */
    @Bean(name = OUTBOUND_REST_TEMPLATE)
    public RestTemplate outboundRestTemplate(ClientHttpRequestFactory outboundRequestFactory) {
        return new RestTemplate(outboundRequestFactory);
    }

    /** 业务实际注入的门面。显式 new，方便测试塞入 mock 的 RestTemplate。 */
    @Bean
    public HttpClientTemplate httpClientTemplate(RestTemplate outboundRestTemplate) {
        return new HttpClientTemplate(outboundRestTemplate);
    }
}
