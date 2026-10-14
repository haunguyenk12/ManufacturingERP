package com.erp.manufacturing.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.redis.connection.RedisConnectionFactory;

import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/**
 * Guards the JSON wire format the frontend integrates against (debt #27, fixed 2026-08-12).
 *
 * <p>The defect this pins down was invisible to every other test in the suite: {@code RedisConfig}
 * declared a bare {@code @Bean ObjectMapper}, Boot's {@code JacksonAutoConfiguration} backed off
 * because it is {@code @ConditionalOnMissingBean}, and every {@code spring.jackson.*} property went
 * dead — so dates left the server as {@code [2026,8,15]} / epoch numbers. Controller slice tests
 * (`@WebMvcTest`) never saw it because the slice does not load {@code RedisConfig} at all, and they
 * would have kept passing with the bug in place.
 *
 * <p>So the context here is assembled out of the same three ingredients the running application has —
 * the real {@code application.yml}, Boot's Jackson auto-configuration, and {@code RedisConfig} itself.
 * Re-introduce a mapper bean anywhere in that config and the date assertions below go red.
 */
class JsonWireFormatTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withConfiguration(AutoConfigurations.of(JacksonAutoConfiguration.class))
            .withUserConfiguration(RedisConfig.class)
            .withBean(RedisConnectionFactory.class, () -> mock(RedisConnectionFactory.class));

    /** Shaped like the response DTOs the frontend reads: a date, a timestamp, an optional field. */
    record Payload(LocalDate dueDate, Instant createdAt, String cancelReason) {}

    @Test
    void localDate_isWrittenAsAnIsoStringNotAnArray() {
        contextRunner.run(context -> assertThat(serialize(context.getBean(ObjectMapper.class)))
                .contains("\"dueDate\":\"2026-08-15\"")
                .doesNotContain("[2026,8,15]"));
    }

    @Test
    void instant_isWrittenAsAnIsoUtcStringNotAnEpochNumber() {
        contextRunner.run(context -> assertThat(serialize(context.getBean(ObjectMapper.class)))
                .contains("\"createdAt\":\"2026-08-03T08:00:00Z\"")
                .doesNotContain("1785657600"));
    }

    /**
     * Null fields stay on the wire. {@code application.yml} pins {@code default-property-inclusion:
     * always} on purpose — dropping nulls would change the payload of every endpoint, which is a
     * separate decision from the date-format fix. See the comment in that file.
     */
    @Test
    void nullFields_areStillPresentOnTheWire() {
        contextRunner.run(context -> assertThat(serialize(context.getBean(ObjectMapper.class)))
                .contains("\"cancelReason\":null"));
    }

    record IntegerPayload(Integer leadTimeDays) {}

    /**
     * Read side of the same contract (FE acceptance A10, 2026-10-14). Jackson's default truncates
     * {@code 1.5} to {@code 1} for an integer field and the request succeeds; {@code application.yml}
     * pins {@code accept-float-as-int: false} so every endpoint rejects it instead. {@code 5.0} is
     * rejected too — Jackson treats any decimal point as a float token — while a plain {@code 5} binds.
     */
    @Test
    void fractionalNumber_isRejectedForAnIntegerFieldInsteadOfTruncated() {
        contextRunner.run(context -> {
            ObjectMapper mapper = context.getBean(ObjectMapper.class);
            assertThatThrownBy(() -> mapper.readValue("{\"leadTimeDays\":1.5}", IntegerPayload.class))
                    .isInstanceOf(MismatchedInputException.class);
            assertThatThrownBy(() -> mapper.readValue("{\"leadTimeDays\":5.0}", IntegerPayload.class))
                    .isInstanceOf(MismatchedInputException.class);
            assertThat(mapper.readValue("{\"leadTimeDays\":5}", IntegerPayload.class).leadTimeDays())
                    .isEqualTo(5);
        });
    }

    private String serialize(ObjectMapper mapper) throws Exception {
        return mapper.writeValueAsString(new Payload(
                LocalDate.of(2026, 8, 15), Instant.parse("2026-08-03T08:00:00Z"), null));
    }
}
