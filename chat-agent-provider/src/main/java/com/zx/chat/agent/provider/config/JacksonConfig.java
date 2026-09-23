package com.zx.chat.agent.provider.config;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateDeserializer;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateSerializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalTimeSerializer;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

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
    public Jackson2ObjectMapperBuilderCustomizer jacksonCustomizer() {
        DateTimeFormatter dateTime = DateTimeFormatter.ofPattern(DATE_TIME_PATTERN);
        DateTimeFormatter date = DateTimeFormatter.ofPattern(DATE_PATTERN);
        DateTimeFormatter time = DateTimeFormatter.ofPattern(TIME_PATTERN);

        return builder -> builder
            // Long 超过 2^53 时 JavaScript 会丢精度，统一转字符串。
            // 只处理包装类型 Long，不动 int/Integer
            .serializerByType(Long.class, ToStringSerializer.instance)
            .serializerByType(Long.TYPE, ToStringSerializer.instance)
            .serializerByType(LocalDateTime.class, new LocalDateTimeSerializer(dateTime))
            .serializerByType(LocalDate.class, new LocalDateSerializer(date))
            .serializerByType(LocalTime.class, new LocalTimeSerializer(time))
            .deserializerByType(LocalDateTime.class, new LocalDateTimeDeserializer(dateTime))
            .deserializerByType(LocalDate.class, new LocalDateDeserializer(date))
            .deserializerByType(LocalTime.class, new LocalTimeDeserializer(time))
            // 不输出 null 字段，减少响应体积
            .serializationInclusion(JsonInclude.Include.NON_NULL)
            // 时间不序列化成 epoch 数字
            .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            // 上游加字段时不至于直接 400
            .featuresToDisable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    }
}
