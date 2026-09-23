package com.zx.chat.agent.provider.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;
import org.apache.logging.log4j.core.layout.PatternLayout;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RequestLogFilterTest {

    private final RequestLogFilter filter = new RequestLogFilter();

    private CapturingAppender appender;

    private Logger coreLogger;

    @BeforeEach
    void attachAppender() {
        appender = new CapturingAppender();
        appender.start();
        LoggerContext context = (LoggerContext) LogManager.getContext(false);
        coreLogger = context.getLogger(RequestLogFilter.class.getName());
        coreLogger.addAppender(appender);
    }

    @AfterEach
    void detachAppender() {
        coreLogger.removeAppender(appender);
        appender.stop();
    }

    @Test
    void logsMethodUriStatusAndCost() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/prizes");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(appender.only())
            .contains("HTTP GET /api/prizes")
            .contains("status=200")
            .contains("cost=");
    }

    @Test
    void logsQueryStringAlongWithTheUri() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/prizes");
        request.setQueryString("page=1&size=20");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(appender.only()).contains("/api/prizes?page=1&size=20");
    }

    @Test
    void logsRequestAndResponseBody() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/prizes");
        request.setContentType("application/json");
        request.setContent("{\"name\":\"grand\"}".getBytes(StandardCharsets.UTF_8));
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, readBodyThenRespond("{\"code\":0}"));

        assertThat(appender.only())
            .contains("reqBody={\"name\":\"grand\"}")
            .contains("respBody={\"code\":0}");
    }

    @Test
    void downstreamStillReadsTheRequestBody() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/prizes");
        request.setContentType("application/json");
        request.setContent("{\"name\":\"grand\"}".getBytes(StandardCharsets.UTF_8));
        MockHttpServletResponse response = new MockHttpServletResponse();
        StringBuilder seenByDownstream = new StringBuilder();

        filter.doFilter(request, response, (req, res) ->
            seenByDownstream.append(new String(req.getInputStream().readAllBytes(), StandardCharsets.UTF_8)));

        assertThat(seenByDownstream).hasToString("{\"name\":\"grand\"}");
    }

    @Test
    void responseBodyStillReachesTheClient() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/prizes");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> res.getWriter().write("{\"code\":0}"));

        assertThat(response.getContentAsString()).isEqualTo("{\"code\":0}");
    }

    @Test
    void emptyBodiesAreLoggedAsDash() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/prizes");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(appender.only()).contains("reqBody=-").contains("respBody=-");
    }

    @Test
    void oversizedBodyIsTruncated() throws Exception {
        String huge = "x".repeat(RequestLogFilter.MAX_BODY_CHARS + 500);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/prizes");
        request.setContentType("application/json");
        request.setContent(huge.getBytes(StandardCharsets.UTF_8));
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, readBodyThenRespond("ok"));

        String logged = appender.only();
        assertThat(logged).contains("...(truncated)");
        assertThat(logged).doesNotContain("x".repeat(RequestLogFilter.MAX_BODY_CHARS + 1));
    }

    @Test
    void binaryContentTypeIsNotDumped() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/upload");
        request.setContentType("image/png");
        request.setContent(new byte[] {(byte) 0x89, 0x50, 0x4E, 0x47});
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, readBodyThenRespond("ok"));

        assertThat(appender.only()).contains("reqBody=<binary>");
    }

    @Test
    void actuatorAndDocRequestsAreSkipped() throws Exception {
        for (String uri : List.of("/actuator/health", "/doc.html", "/v3/api-docs")) {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", uri);
            filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());
        }

        assertThat(appender.messages()).isEmpty();
    }

    @Test
    void chainFailureStillLogsAndPropagates() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/prizes");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain boom = (req, res) -> {
            throw new IllegalStateException("controller blew up");
        };

        assertThatThrownBy(() -> filter.doFilter(request, response, boom))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("controller blew up");

        assertThat(appender.only()).contains("HTTP GET /api/prizes");
    }

    private FilterChain readBodyThenRespond(String responseBody) {
        return (ServletRequest req, ServletResponse res) -> {
            req.getInputStream().readAllBytes();
            res.getWriter().write(responseBody);
        };
    }

    private static final class CapturingAppender extends AbstractAppender {

        private final List<String> messages = new CopyOnWriteArrayList<>();

        private CapturingAppender() {
            super("requestLogCapture", null, PatternLayout.createDefaultLayout(), true,
                Property.EMPTY_ARRAY);
        }

        @Override
        public void append(LogEvent event) {
            if (event.getLevel().isMoreSpecificThan(Level.INFO)) {
                messages.add(event.getMessage().getFormattedMessage());
            }
        }

        List<String> messages() {
            return messages;
        }

        String only() {
            assertThat(messages).hasSize(1);
            return messages.get(0);
        }
    }
}
