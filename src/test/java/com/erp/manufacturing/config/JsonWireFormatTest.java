package com.erp.manufacturing.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.redis.connection.RedisConnectionFactory;

import java.math.BigDecimal;
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

    record DecimalPayload(BigDecimal v) {}

    /**
     * Quantities are {@code NUMERIC(19,6)}: up to 13 integer and 6 fraction digits. They must leave the
     * server as a JSON number carrying every digit — not a string, not scientific notation, not a
     * double that loses the tail. {@code application.yml} sets no BigDecimal-specific option; this pins
     * what {@code BigDecimal.toString()} produces for these values (scale 6 and up keeps plain form).
     */
    @Test
    void bigDecimal_atTheNumeric19_6Limits_isWrittenAsAPlainJsonNumberWithEveryDigit() {
        contextRunner.run(context -> {
            ObjectMapper mapper = context.getBean(ObjectMapper.class);
            assertThat(writeDecimal(mapper, "9999999999999.999999")).isEqualTo("{\"v\":9999999999999.999999}");
            assertThat(writeDecimal(mapper, "999999999.999999")).isEqualTo("{\"v\":999999999.999999}");
            assertThat(writeDecimal(mapper, "0.000001")).isEqualTo("{\"v\":0.000001}");
            assertThat(writeDecimal(mapper, "0")).isEqualTo("{\"v\":0}");
            // What a NUMERIC(19,6) zero reads back as from the database.
            assertThat(writeDecimal(mapper, "0.000000")).isEqualTo("{\"v\":0.000000}");
        });
    }

    /**
     * Documents current behaviour: trailing zeros are NOT stripped. The scale of the BigDecimal goes on
     * the wire as-is, so a value read back from the database ({@code 100.000000}) and the same value as
     * a client sent it ({@code 100}) produce different JSON text for the same number.
     */
    @Test
    void bigDecimal_withTrailingZeros_keepsItsScaleOnTheWire() {
        contextRunner.run(context -> {
            ObjectMapper mapper = context.getBean(ObjectMapper.class);
            assertThat(writeDecimal(mapper, "100.000000")).isEqualTo("{\"v\":100.000000}");
            assertThat(writeDecimal(mapper, "100")).isEqualTo("{\"v\":100}");
        });
    }

    /**
     * Jackson binds every digit it is given; rejecting a 7th fraction digit is Bean Validation's job
     * ({@code @Digits(fraction = 6)} on the request DTO), not the deserializer's.
     */
    @Test
    void bigDecimal_withSevenFractionDigits_isReadWithEveryDigitKept() {
        contextRunner.run(context -> {
            ObjectMapper mapper = context.getBean(ObjectMapper.class);
            BigDecimal read = mapper.readValue("{\"v\":1.1234567}", DecimalPayload.class).v();
            assertThat(read.toPlainString()).isEqualTo("1.1234567");
            assertThat(read.scale()).isEqualTo(7);
        });
    }

    /**
     * Documents current behaviour, not a decision: a JSON string holding a number is ACCEPTED for a
     * BigDecimal field (Jackson's default scalar coercion; nothing in {@code application.yml} turns it
     * off) and binds to {@code 1.5}.
     */
    @Test
    void numericString_isAcceptedForABigDecimalField_currentBehaviour() {
        contextRunner.run(context -> {
            ObjectMapper mapper = context.getBean(ObjectMapper.class);
            BigDecimal read = mapper.readValue("{\"v\":\"1.5\"}", DecimalPayload.class).v();
            assertThat(read).isEqualByComparingTo("1.5");
            assertThat(read.toPlainString()).isEqualTo("1.5");
        });
    }

    /**
     * Documents current behaviour: the string {@code "1.5"} is REJECTED for an Integer field — not
     * truncated to 1 — with {@code InvalidFormatException} (a {@code MismatchedInputException}, so the
     * global handler reports it as 400 VALIDATION_ERROR with the field name).
     */
    @Test
    void fractionalNumericString_isRejectedForAnIntegerField_currentBehaviour() {
        contextRunner.run(context -> {
            ObjectMapper mapper = context.getBean(ObjectMapper.class);
            assertThatThrownBy(() -> mapper.readValue("{\"leadTimeDays\":\"1.5\"}", IntegerPayload.class))
                    .isInstanceOf(InvalidFormatException.class)
                    .hasMessageStartingWith("Cannot deserialize value of type `java.lang.Integer` from String \"1.5\": "
                            + "not a valid `java.lang.Integer` value");
        });
    }

    private String writeDecimal(ObjectMapper mapper, String value) throws Exception {
        return mapper.writeValueAsString(new DecimalPayload(new BigDecimal(value)));
    }

    private String serialize(ObjectMapper mapper) throws Exception {
        return mapper.writeValueAsString(new Payload(
                LocalDate.of(2026, 8, 15), Instant.parse("2026-08-03T08:00:00Z"), null));
    }
}
