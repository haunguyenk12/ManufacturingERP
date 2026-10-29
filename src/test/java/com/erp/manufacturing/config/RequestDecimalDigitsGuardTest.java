package com.erp.manufacturing.config;

import jakarta.validation.constraints.Digits;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.RegexPatternTypeFilter;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Parameter;
import java.lang.reflect.RecordComponent;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CF-10: every decimal a client can send must be bounded to the database column, {@code NUMERIC(19,6)}.
 *
 * <p>A {@code BigDecimal} request field without {@code @Digits(integer = 13, fraction = 6)} accepts a
 * 7th decimal: the response echoes the unrounded number while Postgres stores a rounded one, and a
 * 14th integer digit reaches the database and comes back as a 422 with no field. This guard fails the
 * moment a new request record adds such a field without the annotation, instead of waiting for a
 * front end to notice the mismatch.
 *
 * <p>Scope: every {@code record} under {@code com.erp.manufacturing} whose simple name ends in
 * {@code Request}. The annotation is looked for wherever Java may put a record component annotation
 * ({@code @Digits} is not a RECORD_COMPONENT target, so it propagates to the field, the accessor and
 * the canonical constructor parameter).
 */
@DisplayName("Request records - every BigDecimal is bounded by @Digits(13, 6)")
class RequestDecimalDigitsGuardTest {

    private static final int INTEGER_DIGITS = 13;
    private static final int FRACTION_DIGITS = 6;

    /**
     * Request record → component names allowed to carry an unbounded {@code BigDecimal}, each with a
     * reason. Empty on purpose: adding to it is a contract decision, not a way to make the build green.
     */
    private static final Map<String, List<String>> EXCEPTIONS = Map.of();

    /**
     * Fields whose integer part may exceed 13 digits because their column is not {@code NUMERIC(19,6)}.
     * Narrower bounds (scrap rate {@code (1, 6)}, G10 prices and quantities {@code (9, 6)}) need no
     * entry: they fit the column by construction.
     */
    private static final Map<String, String> WIDER_INTEGER_PART = Map.of(
            "com.erp.manufacturing.module.billing.dto.CustomerPaymentAllocationRequest.amount",
            "customer_payment_allocations.amount is NUMERIC(19,0), whole VND (G10d)",
            "com.erp.manufacturing.module.billing.dto.CustomerPaymentCreateRequest.amount",
            "customer_payments.amount is NUMERIC(19,0), whole VND (G10d)",
            "com.erp.manufacturing.module.billing.dto.CustomerPaymentUpdateRequest.amount",
            "customer_payments.amount is NUMERIC(19,0), whole VND (G10d)");

    @Test
    @DisplayName("no request record has a BigDecimal component without @Digits, or bounded wider than (13, 6)")
    void everyBigDecimalRequestComponent_carriesDigits() throws Exception {
        List<Class<?>> requests = requestRecords();
        // Sanity: the scan really found the records it is meant to guard (a broken scan passes vacuously).
        assertThat(requests).extracting(Class::getSimpleName)
                .contains("ItemStandardCostRequest", "PurchaseOrderLineRequest", "WipTransactionRequest");

        TreeSet<String> missing = new TreeSet<>();
        TreeSet<String> wrongBounds = new TreeSet<>();
        int checked = 0;
        for (Class<?> type : requests) {
            List<String> allowed = EXCEPTIONS.getOrDefault(type.getName(), List.of());
            for (RecordComponent component : type.getRecordComponents()) {
                if (component.getType() != BigDecimal.class || allowed.contains(component.getName())) {
                    continue;
                }
                checked++;
                Digits digits = digitsOn(type, component);
                String name = type.getName() + "." + component.getName();
                if (digits == null) {
                    missing.add(name);
                } else if (digits.fraction() > FRACTION_DIGITS
                        || (digits.integer() > INTEGER_DIGITS && !WIDER_INTEGER_PART.containsKey(name))) {
                    wrongBounds.add(name + " @Digits(" + digits.integer() + ", " + digits.fraction() + ")");
                }
            }
        }

        assertThat(checked).as("BigDecimal request components found by the scan").isGreaterThanOrEqualTo(20);
        assertThat(missing).as("BigDecimal request components without @Digits(integer = 13, fraction = 6)").isEmpty();
        assertThat(wrongBounds).as("BigDecimal request components bounded wider than NUMERIC(19,6)").isEmpty();
    }

    private static Digits digitsOn(Class<?> type, RecordComponent component) throws NoSuchFieldException {
        Digits onComponent = component.getAnnotation(Digits.class);
        if (onComponent != null) {
            return onComponent;
        }
        Field field = type.getDeclaredField(component.getName());
        if (field.getAnnotation(Digits.class) != null) {
            return field.getAnnotation(Digits.class);
        }
        RecordComponent[] components = type.getRecordComponents();
        Class<?>[] types = Arrays.stream(components).map(RecordComponent::getType).toArray(Class<?>[]::new);
        try {
            Constructor<?> canonical = type.getDeclaredConstructor(types);
            int index = 0;
            while (!components[index].getName().equals(component.getName())) {
                index++; // getRecordComponents() returns fresh objects, so match by name
            }
            Parameter parameter = canonical.getParameters()[index];
            return parameter.getAnnotation(Digits.class);
        } catch (NoSuchMethodException ex) {
            throw new IllegalStateException("record without a canonical constructor: " + type.getName(), ex);
        }
    }

    private static List<Class<?>> requestRecords() throws ClassNotFoundException {
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false) {
            @Override
            protected boolean isCandidateComponent(
                    org.springframework.beans.factory.annotation.AnnotatedBeanDefinition definition) {
                return true; // records are final, independent classes; the name filter does the rest
            }
        };
        scanner.addIncludeFilter(new RegexPatternTypeFilter(Pattern.compile(".*Request")));
        List<Class<?>> records = new ArrayList<>();
        for (BeanDefinition definition : scanner.findCandidateComponents("com.erp.manufacturing")) {
            Class<?> type = Class.forName(definition.getBeanClassName());
            if (type.isRecord()) {
                records.add(type);
            }
        }
        return records;
    }
}
