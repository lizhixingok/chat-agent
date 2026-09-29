package com.zx.chat.agent.provider.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class JacksonConfigTest {

    private ObjectMapper mapper;

    @BeforeEach
    void setUp() {
        JsonMapper.Builder builder = JsonMapper.builder();
        new JacksonConfig().jacksonCustomizer().customize(builder);
        mapper = builder.build();
    }

    @Test
    void longIsSerializedAsStringToSurviveJavaScriptPrecision() throws Exception {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", 9007199254740993L);

        assertThat(mapper.writeValueAsString(payload)).isEqualTo("{\"id\":\"9007199254740993\"}");
    }

    @Test
    void intIsStillANumberNotAString() throws Exception {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("count", 42);

        assertThat(mapper.writeValueAsString(payload)).isEqualTo("{\"count\":42}");
    }

    @Test
    void localDateTimeUsesIsoFormatNotEpochNumber() throws Exception {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("createdAt", LocalDateTime.of(2026, 8, 27, 12, 0, 0));

        assertThat(mapper.writeValueAsString(payload))
            .isEqualTo("{\"createdAt\":\"2026-08-27 12:00:00\"}");
    }

    @Test
    void localDateUsesIsoDateFormat() throws Exception {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("day", LocalDate.of(2026, 8, 27));

        assertThat(mapper.writeValueAsString(payload)).isEqualTo("{\"day\":\"2026-08-27\"}");
    }

    @Test
    void localDateTimeDeserializesFromSameFormat() throws Exception {
        record Payload(LocalDateTime createdAt) {}

        Payload p = mapper.readValue("{\"createdAt\":\"2026-08-27 12:00:00\"}", Payload.class);

        assertThat(p.createdAt()).isEqualTo(LocalDateTime.of(2026, 8, 27, 12, 0, 0));
    }

    @Test
    void unknownJsonFieldsAreIgnoredForForwardCompatibility() throws Exception {
        record Payload(String name) {}

        Payload p = mapper.readValue("{\"name\":\"draw\",\"futureField\":123}", Payload.class);

        assertThat(p.name()).isEqualTo("draw");
    }

    @Test
    void nullFieldsAreOmittedFromOutput() throws Exception {
        record Payload(String name, String note) {}

        assertThat(mapper.writeValueAsString(new Payload("draw", null)))
            .isEqualTo("{\"name\":\"draw\"}");
    }
}
