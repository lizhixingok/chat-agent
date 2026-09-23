package com.zx.chat.agent.service.infra.http;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * 第三方 HTTP 调用的统一入口。所有出站调用走这里，
 * 好处是超时、连接池、错误码转换、日志只有一处实现。
 * 内部服务之间的调用请继续使用 OpenFeign，不要走这个类。
 */
public class HttpClientTemplate {

    private static final Logger log = LoggerFactory.getLogger(HttpClientTemplate.class);

    private final RestTemplate restTemplate;

    public HttpClientTemplate(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    /** GET，无自定义请求头。 */
    public <T> T get(String url, Class<T> responseType) {
        return get(url, null, responseType);
    }

    /** GET，带自定义请求头。 */
    public <T> T get(String url, Map<String, String> headers, Class<T> responseType) {
        return execute(HttpMethod.GET, url, headers, null, responseType);
    }

    /** GET，返回泛型集合。用 ParameterizedTypeReference 保住元素类型。 */
    public <T> T get(String url, Map<String, String> headers, ParameterizedTypeReference<T> responseType) {
        return execute(HttpMethod.GET, url, headers, null, responseType);
    }

    /** POST JSON，无自定义请求头。 */
    public <T> T post(String url, Object body, Class<T> responseType) {
        return post(url, null, body, responseType);
    }

    /** POST JSON，带自定义请求头。 */
    public <T> T post(String url, Map<String, String> headers, Object body, Class<T> responseType) {
        return execute(HttpMethod.POST, url, headers, body, responseType);
    }

    /** POST JSON，返回泛型集合。 */
    public <T> T post(String url, Map<String, String> headers, Object body,
                      ParameterizedTypeReference<T> responseType) {
        return execute(HttpMethod.POST, url, headers, body, responseType);
    }

    /** Class 版执行出口。RestTemplate 的两种 responseType 是独立签名，泛型收不到一起。 */
    private <T> T execute(HttpMethod method, String url, Map<String, String> headers,
                          Object body, Class<T> responseType) {
        try {
            return restTemplate.exchange(url, method, entity(headers, body), responseType).getBody();
        } catch (RuntimeException e) {
            throw translate(e, method, url);
        }
    }

    /** ParameterizedTypeReference 版执行出口，逻辑与 Class 版一致。 */
    private <T> T execute(HttpMethod method, String url, Map<String, String> headers,
                          Object body, ParameterizedTypeReference<T> responseType) {
        try {
            return restTemplate.exchange(url, method, entity(headers, body), responseType).getBody();
        } catch (RuntimeException e) {
            throw translate(e, method, url);
        }
    }

    /** 组装请求实体。有 body 才设 JSON Content-Type，GET 不该带这个头。 */
    private HttpEntity<Object> entity(Map<String, String> headers, Object body) {
        HttpHeaders httpHeaders = new HttpHeaders();
        if (headers != null) {
            headers.forEach(httpHeaders::set);
        }
        if (body != null) {
            httpHeaders.setContentType(MediaType.APPLICATION_JSON);
        }
        return new HttpEntity<>(body, httpHeaders);
    }

    /**
     * 所有失败都转成 HttpClientException。
     * 上游响应体只进日志和异常字段，不进 message，避免把连接串、密码回传给调用方。
     */
    private HttpClientException translate(RuntimeException e, HttpMethod method, String url) {
        // 4xx/5xx：RestTemplate 默认的错误处理器已经把状态码和响应体挂在异常上
        if (e instanceof HttpStatusCodeException se) {
            int status = se.getStatusCode().value();
            String responseBody = se.getResponseBodyAsString();
            log.warn("outbound call failed, method={} url={} status={} body={}", method, url, status, responseBody);
            return new HttpClientException(
                "outbound call returned HTTP " + status, status, responseBody, null);
        }
        // 连接超时、DNS 失败、对端拒连等，没有响应可读，状态码记 0
        if (e instanceof ResourceAccessException) {
            log.warn("outbound call unreachable, method={} url={}", method, url, e);
            return new HttpClientException("outbound call unreachable: " + url, 0, null, e);
        }
        // 反序列化失败等其它异常
        log.warn("outbound call error, method={} url={}", method, url, e);
        return new HttpClientException("outbound call error: " + url, 0, null, e);
    }
}
