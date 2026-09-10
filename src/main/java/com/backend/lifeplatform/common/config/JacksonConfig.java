package com.backend.lifeplatform.common.config;

import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateDeserializer;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateSerializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalTimeSerializer;
import java.time.format.DateTimeFormatter;
import java.util.TimeZone;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 统一 Java 时间类型的 JSON 格式。 */
@Configuration
public class JacksonConfig {

    /**
     * 统一 JSON 序列化/反序列化的时间格式：
     * LocalDateTime → "yyyy-MM-dd HH:mm:ss"，LocalDate → "yyyy-MM-dd"，LocalTime → "HH:mm:ss"；
     * 并关闭把时间写成时间戳的开关、统一使用东八区。
     */
    @Bean
    public Jackson2ObjectMapperBuilderCustomizer jsonCustomizer() {
        return builder -> {
            // 定义三种时间类型的格式化器
            DateTimeFormatter dateTimeFormatter =
                    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
            DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss");

            // 序列化（Java → JSON）与反序列化（JSON → Java）均使用上述格式
            builder.serializers(
                    new LocalDateTimeSerializer(dateTimeFormatter),
                    new LocalDateSerializer(dateFormatter),
                    new LocalTimeSerializer(timeFormatter));
            builder.deserializers(
                    new LocalDateTimeDeserializer(dateTimeFormatter),
                    new LocalDateDeserializer(dateFormatter),
                    new LocalTimeDeserializer(timeFormatter));
            // 不以时间戳形式输出日期
            builder.featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
            // 统一使用东八区（Asia/Shanghai）
            builder.timeZone(TimeZone.getTimeZone("Asia/Shanghai"));
        };
    }
}
