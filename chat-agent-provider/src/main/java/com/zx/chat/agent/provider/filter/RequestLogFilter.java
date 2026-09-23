package com.zx.chat.agent.provider.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;
import org.springframework.web.util.UrlPathHelper;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;

/**
 * 统一请求日志：一行记录方法、URI、状态码、耗时、请求体、响应体。
 *
 * <p>用 ContentCaching 包装器读 body —— 原始流只能读一次，直接读会让
 * Controller 拿到空 body。响应侧包装后必须 copyBodyToResponse()，
 * 否则响应体停在缓存里，客户端收到空响应。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class RequestLogFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RequestLogFilter.class);

    /** body 日志上限，超出截断。避免大报文打爆磁盘 */
    static final int MAX_BODY_CHARS = 2048;

    private static final String TRUNCATED = "...(truncated)";

    private static final String EMPTY_BODY = "-";

    private static final String BINARY_BODY = "<binary>";

    /** 探活、文档、静态资源不记日志，否则噪音淹没业务请求 */
    private static final List<String> SKIP_PREFIXES = List.of(
        "/actuator", "/doc.html", "/swagger-ui", "/v3/api-docs", "/webjars", "/favicon.ico");

    /** 只有文本类 body 才有可读价值 */
    private static final List<String> LOGGABLE_TYPES = List.of(
        "application/json", "application/xml", "application/x-www-form-urlencoded", "text/");

    /** 聊天请求不缓存或记录正文，避免泄漏对话内容及阻塞 SSE 输出。 */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String path = UrlPathHelper.defaultInstance.getPathWithinApplication(request);
        return SKIP_PREFIXES.stream().anyMatch(uri::startsWith)
            || "/o/v1/chat/completions".equals(path)
            || "/o/v1/chat/completions/stream".equals(path);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        ContentCachingRequestWrapper cachedRequest = new ContentCachingRequestWrapper(request);
        ContentCachingResponseWrapper cachedResponse = new ContentCachingResponseWrapper(response);
        long startNanos = System.nanoTime();
        try {
            filterChain.doFilter(cachedRequest, cachedResponse);
        } finally {
            long costMillis = (System.nanoTime() - startNanos) / 1_000_000;
            try {
                log.info("HTTP {} {} status={} cost={}ms reqBody={} respBody={}",
                    cachedRequest.getMethod(),
                    fullUri(cachedRequest),
                    cachedResponse.getStatus(),
                    costMillis,
                    body(cachedRequest.getContentAsByteArray(),
                        cachedRequest.getContentType(), cachedRequest.getCharacterEncoding()),
                    body(cachedResponse.getContentAsByteArray(),
                        cachedResponse.getContentType(), cachedResponse.getCharacterEncoding()));
            } catch (RuntimeException e) {
                // 打日志本身不能影响业务响应
                log.warn("request log failed, uri={}", request.getRequestURI(), e);
            }
            cachedResponse.copyBodyToResponse();
        }
    }

    /** URI 带上 query，排障时能直接看到入参。 */
    private String fullUri(HttpServletRequest request) {
        String query = request.getQueryString();
        return query == null ? request.getRequestURI() : request.getRequestURI() + "?" + query;
    }

    /** body 转可读文本：空体记 -，二进制记 <binary>，超长截断。 */
    private String body(byte[] content, String contentType, String characterEncoding) {
        if (content == null || content.length == 0) {
            return EMPTY_BODY;
        }
        if (!isLoggable(contentType)) {
            return BINARY_BODY;
        }
        String text = new String(content, charset(characterEncoding));
        return text.length() <= MAX_BODY_CHARS
            ? text
            : text.substring(0, MAX_BODY_CHARS) + TRUNCATED;
    }

    /** contentType 为 null 时按可记处理，GET 的空 body 会在上一步先返回。 */
    private boolean isLoggable(String contentType) {
        if (contentType == null) {
            return true;
        }
        String lower = contentType.toLowerCase(Locale.ROOT);
        return LOGGABLE_TYPES.stream().anyMatch(lower::startsWith);
    }

    /** 编码名非法时退回 UTF-8，不让日志解析失败影响请求。 */
    private Charset charset(String characterEncoding) {
        if (characterEncoding == null) {
            return StandardCharsets.UTF_8;
        }
        try {
            return Charset.forName(characterEncoding);
        } catch (RuntimeException e) {
            return StandardCharsets.UTF_8;
        }
    }
}
