package com.zx.chat.agent.provider.config;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ext.javatime.deser.LocalDateDeserializer;
import tools.jackson.databind.ext.javatime.deser.LocalDateTimeDeserializer;
import tools.jackson.databind.ext.javatime.deser.LocalTimeDeserializer;
import tools.jackson.databind.ext.javatime.ser.LocalDateSerializer;
import tools.jackson.databind.ext.javatime.ser.LocalDateTimeSerializer;
import tools.jackson.databind.ext.javatime.ser.LocalTimeSerializer;
import tools.jackson.databind.module.SimpleModule;
import tools.jackson.databind.ser.std.ToStringSerializer;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * 全局 JSON 序列化规则。
 *
 * <p>时间约定：DB 列 DATETIME，Java 用 LocalDateTime，**一律视为 UTC**。
 * LocalDateTime 不携带时区，spring.jackson.time-zone 对它无效，
 * 所以 UTC 语义只能靠代码纪律保证 —— 禁止 LocalDateTime.now()，
 * 必须写 LocalDateTime.now(ZoneOffset.UTC)。Task 10 有守卫测试拦截。
 */
@Configuration
public class JacksonConfig {

    public static final String DATE_TIME_PATTERN = "yyyy-MM-dd HH:mm:ss";
    public static final String DATE_PATTERN = "yyyy-MM-dd";
    public static final String TIME_PATTERN = "HH:mm:ss";

    /** 定制全局 ObjectMapper：Long 转字符串、时间用固定格式、忽略 null 与未知字段。 */
    @Bean
    public JsonMapperBuilderCustomizer jacksonCustomizer() {
        DateTimeFormatter dateTime = DateTimeFormatter.ofPattern(DATE_TIME_PATTERN);
        DateTimeFormatter date = DateTimeFormatter.ofPattern(DATE_PATTERN);
        DateTimeFormatter time = DateTimeFormatter.ofPattern(TIME_PATTERN);
        SimpleModule module = new SimpleModule("chat-agent-json");
        module.addSerializer(Long.class, new ToStringSerializer(Long.class));
        module.addSerializer(LocalDateTime.class, new LocalDateTimeSerializer(dateTime));
        module.addSerializer(LocalDate.class, new LocalDateSerializer(date));
        module.addSerializer(LocalTime.class, new LocalTimeSerializer(time));
        module.addDeserializer(LocalDateTime.class, new LocalDateTimeDeserializer(dateTime));
        module.addDeserializer(LocalDate.class, new LocalDateDeserializer(date));
        module.addDeserializer(LocalTime.class, new LocalTimeDeserializer(time));

        return builder -> builder
            // Long 超过 2^53 时 JavaScript 会丢精度，统一转字符串。
            // 只处理包装类型 Long，不动 int/Integer
            .addModule(module)
            // 不输出 null 字段，减少响应体积
            .changeDefaultPropertyInclusion(inclusion ->
                inclusion.withValueInclusion(JsonInclude.Include.NON_NULL))
            // 上游加字段时不至于直接 400
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    }
}
