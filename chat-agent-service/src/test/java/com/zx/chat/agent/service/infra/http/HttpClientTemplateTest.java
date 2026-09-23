package com.zx.chat.agent.service.infra.http;

import com.zx.chat.agent.api.common.ErrorCode;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class HttpClientTemplateTest {

    private final HttpClientConfig config = new HttpClientConfig();

    private MockRestServiceServer server;
    private HttpClientTemplate template;

    @BeforeEach
    void setUp() {
        RestTemplate restTemplate = new RestTemplate();
        server = MockRestServiceServer.bindTo(restTemplate).build();
        template = config.httpClientTemplate(restTemplate);
    }

    record Prize(Long id, String name) {}

    @Test
    void defaultsMatchTheDocumentedSizing() {
        HttpClientProperties defaults = new HttpClientProperties();

        assertThat(defaults.getConnectTimeoutMillis()).isEqualTo(3000);
        assertThat(defaults.getReadTimeoutMillis()).isEqualTo(10000);
        assertThat(defaults.getConnectionRequestTimeoutMillis()).isEqualTo(2000);
        assertThat(defaults.getMaxTotalConnections()).isEqualTo(200);
        assertThat(defaults.getMaxConnectionsPerRoute()).isEqualTo(50);
        assertThat(defaults.getIdleConnectionTimeoutSeconds()).isEqualTo(30);
    }

    @Test
    void poolSizingIsAppliedToTheConnectionManager() {
        HttpClientProperties custom = new HttpClientProperties();
        custom.setMaxTotalConnections(11);
        custom.setMaxConnectionsPerRoute(7);

        PoolingHttpClientConnectionManager cm = config.outboundConnectionManager(custom);

        assertThat(cm.getMaxTotal()).isEqualTo(11);
        assertThat(cm.getDefaultMaxPerRoute()).isEqualTo(7);
    }

    @Test
    void getDeserializesJsonIntoTheTargetType() {
        server.expect(requestTo("http://upstream/prize"))
            .andRespond(withSuccess("{\"id\":9007199254740993,\"name\":\"Trip\"}",
                MediaType.APPLICATION_JSON));

        Prize prize = template.get("http://upstream/prize", Prize.class);

        assertThat(prize.id()).isEqualTo(9007199254740993L);
        assertThat(prize.name()).isEqualTo("Trip");
        server.verify();
    }

    @Test
    void getSupportsGenericCollectionsViaTypeReference() {
        server.expect(requestTo("http://upstream/prizes"))
            .andRespond(withSuccess("[{\"id\":1,\"name\":\"A\"},{\"id\":2,\"name\":\"B\"}]",
                MediaType.APPLICATION_JSON));

        List<Prize> prizes = template.get("http://upstream/prizes", null,
            new ParameterizedTypeReference<List<Prize>>() {});

        assertThat(prizes).extracting(Prize::name).containsExactly("A", "B");
        server.verify();
    }

    @Test
    void postSendsJsonBodyAndReadsTheResponse() {
        server.expect(requestTo("http://upstream/draw"))
            .andExpect(header("Content-Type", MediaType.APPLICATION_JSON_VALUE))
            .andExpect(content().json("{\"id\":1,\"name\":\"Trip\"}"))
            .andRespond(withSuccess("{\"id\":5,\"name\":\"Won\"}", MediaType.APPLICATION_JSON));

        Prize prize = template.post("http://upstream/draw", new Prize(1L, "Trip"), Prize.class);

        assertThat(prize.name()).isEqualTo("Won");
        server.verify();
    }

    @Test
    void customHeadersReachTheUpstream() {
        server.expect(requestTo("http://upstream/secured"))
            .andExpect(header("Authorization", "Bearer abc"))
            .andRespond(withSuccess("ok", MediaType.TEXT_PLAIN));

        String body = template.get("http://upstream/secured",
            Map.of("Authorization", "Bearer abc"), String.class);

        assertThat(body).isEqualTo("ok");
        server.verify();
    }

    @Test
    void errorStatusBecomesThirdPartyErrorCodeCarryingStatusAndBody() {
        server.expect(requestTo("http://upstream/broken"))
            .andRespond(withStatus(HttpStatus.BAD_GATEWAY)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"error\":\"upstream down\"}"));

        assertThatThrownBy(() -> template.get("http://upstream/broken", Prize.class))
            .isInstanceOf(HttpClientException.class)
            .satisfies(e -> {
                HttpClientException ex = (HttpClientException) e;
                assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.THIRD_PARTY_ERROR);
                assertThat(ex.getStatusCode()).isEqualTo(502);
                assertThat(ex.getResponseBody()).contains("upstream down");
            });
    }

    @Test
    void unreachableHostBecomesThirdPartyErrorWithNoStatusCode() {
        RestTemplate unreachable = new RestTemplate((uri, method) -> {
            throw new IOException("Connection refused");
        });
        HttpClientTemplate failing = config.httpClientTemplate(unreachable);

        assertThatThrownBy(() -> failing.get("http://127.0.0.1:1/nope", Prize.class))
            .isInstanceOf(HttpClientException.class)
            .satisfies(e -> {
                HttpClientException ex = (HttpClientException) e;
                assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.THIRD_PARTY_ERROR);
                assertThat(ex.getStatusCode()).isZero();
                assertThat(ex.getCause()).isNotNull();
            });
    }

    @Test
    void failureMessageNeverExposesUpstreamBodyToCallers() {
        server.expect(requestTo("http://upstream/leaky"))
            .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"stack\":\"jdbc:mysql://prod-db password=secret\"}"));

        assertThatThrownBy(() -> template.get("http://upstream/leaky", Prize.class))
            .isInstanceOf(HttpClientException.class)
            .satisfies(e -> {
                HttpClientException ex = (HttpClientException) e;
                assertThat(ex.getMessage()).isEqualTo(ErrorCode.THIRD_PARTY_ERROR.getMessage());
                assertThat(ex.getMessage()).doesNotContain("password", "jdbc");
            });
    }
}
