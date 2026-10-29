package com.erp.manufacturing.config;

import com.erp.manufacturing.module.costing.dto.ItemStandardCostRequest;
import com.erp.manufacturing.module.inventory.dto.ItemWarehouseSettingRequest;
import com.erp.manufacturing.module.planning.dto.PlanningDemandCreateRequest;
import com.erp.manufacturing.module.planning.dto.ProductionEstimateRequest;
import com.erp.manufacturing.module.purchasing.dto.ItemSupplierRequest;
import com.erp.manufacturing.module.purchasing.dto.PurchaseOrderCreateRequest;
import com.erp.manufacturing.module.purchasing.dto.PurchaseOrderLineRequest;
import com.erp.manufacturing.module.purchasing.dto.PurchaseRequisitionLineApprovalRequest;
import com.erp.manufacturing.module.purchasing.dto.PurchaseRequisitionLineRequest;
import com.erp.manufacturing.module.routing.dto.RoutingOperationRequest;
import com.erp.manufacturing.module.sales.dto.SalesOrderLineRequest;
import com.erp.manufacturing.module.workorder.dto.execution.MaterialIssueFlatRequest;
import com.erp.manufacturing.module.workorder.dto.execution.WipTransactionRequest;
import com.erp.manufacturing.module.workorder.dto.execution.WorkOrderComponentIssueRequest;
import com.erp.manufacturing.module.workorder.dto.execution.WorkOrderOutputCompletionRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import jakarta.validation.constraints.Digits;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.lang.reflect.Constructor;
import java.lang.reflect.RecordComponent;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CF-10: the 20 request decimals bounded to {@code NUMERIC(19,6)} in this phase, each checked against
 * the real Bean Validation engine — the guard test proves the annotation is there, this proves it
 * rejects what it must and accepts the column's maximum.
 *
 * <p>Every other component of the record under test is left {@code null}, and only {@code @Digits}
 * violations on the tested property are counted, so a {@code @NotNull} elsewhere cannot make a case
 * pass or fail for the wrong reason.
 */
@DisplayName("Request decimals - @Digits(13, 6) on the CF-10 fields")
class RequestDecimalDigitsValidationTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        factory.close();
    }

    static Stream<Arguments> fields() {
        return Stream.of(
                Arguments.of(ItemStandardCostRequest.class, "materialCost"),
                Arguments.of(ItemStandardCostRequest.class, "laborCost"),
                Arguments.of(ItemStandardCostRequest.class, "overheadCost"),
                Arguments.of(ItemWarehouseSettingRequest.class, "safetyStock"),
                Arguments.of(ItemWarehouseSettingRequest.class, "reorderPoint"),
                Arguments.of(PlanningDemandCreateRequest.class, "requiredQuantity"),
                Arguments.of(ProductionEstimateRequest.class, "targetQuantity"),
                Arguments.of(ItemSupplierRequest.class, "minimumOrderQuantity"),
                Arguments.of(ItemSupplierRequest.class, "unitPrice"),
                Arguments.of(PurchaseOrderLineRequest.class, "orderedQuantity"),
                Arguments.of(PurchaseOrderLineRequest.class, "unitPrice"),
                Arguments.of(PurchaseRequisitionLineApprovalRequest.class, "approvedQuantity"),
                Arguments.of(PurchaseRequisitionLineRequest.class, "requestedQuantity"),
                Arguments.of(RoutingOperationRequest.class, "setupMinutes"),
                Arguments.of(RoutingOperationRequest.class, "runMinutesPerUnit"),
                Arguments.of(SalesOrderLineRequest.class, "orderedQuantity"),
                Arguments.of(MaterialIssueFlatRequest.class, "quantity"),
                Arguments.of(WipTransactionRequest.class, "quantity"),
                Arguments.of(WorkOrderComponentIssueRequest.class, "quantity"),
                Arguments.of(WorkOrderOutputCompletionRequest.class, "quantity"));
    }

    @Test
    void theTableCoversTwentyFields() {
        assertThat(fields().count()).isEqualTo(20);
    }

    @ParameterizedTest(name = "{0}.{1}: a 7th decimal is rejected")
    @MethodSource("fields")
    void seventhDecimal_isRejected(Class<?> type, String field) throws Exception {
        assertThat(digitsViolations(type, field, "1.2345678")).singleElement()
                .satisfies(v -> assertThat(v.getPropertyPath().toString()).isEqualTo(field));
    }

    /** Hibernate Validator counts a trailing zero as a digit (it does not strip them) — intended, FE sends ≤ 6. */
    @ParameterizedTest(name = "{0}.{1}: a 7th decimal that is a zero is still rejected")
    @MethodSource("fields")
    void seventhDecimalZero_isRejected(Class<?> type, String field) throws Exception {
        assertThat(digitsViolations(type, field, "1.2345670")).hasSize(1);
    }

    @ParameterizedTest(name = "{0}.{1}: a 14th integer digit is rejected")
    @MethodSource("fields")
    void fourteenthIntegerDigit_isRejected(Class<?> type, String field) throws Exception {
        assertThat(digitsViolations(type, field, "12345678901234")).hasSize(1);
    }

    @ParameterizedTest(name = "{0}.{1}: the column maximum is accepted")
    @MethodSource("fields")
    void columnMaximum_isAccepted(Class<?> type, String field) throws Exception {
        assertThat(digitsViolations(type, field, "9999999999999.999999")).isEmpty();
        assertThat(digitsViolations(type, field, "1.123456")).isEmpty();
    }

    @Test
    @DisplayName("a nested line names its index in the violation path")
    void nestedLine_namesItsIndexInThePath() {
        PurchaseOrderLineRequest valid = new PurchaseOrderLineRequest(null, UUID.randomUUID(),
                new BigDecimal("2"), new BigDecimal("10"), "VND", null);
        PurchaseOrderLineRequest tooPrecise = new PurchaseOrderLineRequest(null, UUID.randomUUID(),
                new BigDecimal("1.1234567"), new BigDecimal("10"), "VND", null);
        PurchaseOrderCreateRequest request = new PurchaseOrderCreateRequest(UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(), "PO-1", LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 5),
                null, null, List.of(valid, tooPrecise));

        assertThat(validator.validate(request)).singleElement().satisfies(v -> {
            assertThat(v.getPropertyPath().toString()).isEqualTo("lines[1].orderedQuantity");
            assertThat(v.getConstraintDescriptor().getAnnotation()).isInstanceOf(Digits.class);
        });
    }

    private static List<ConstraintViolation<Object>> digitsViolations(Class<?> type, String field, String value)
            throws Exception {
        Object instance = instanceWith(type, field, new BigDecimal(value));
        return validator.validate(instance).stream()
                .filter(v -> v.getConstraintDescriptor().getAnnotation() instanceof Digits)
                .filter(v -> v.getPropertyPath().toString().equals(field))
                .toList();
    }

    /** Canonical constructor with {@code value} at {@code field} and null / false everywhere else. */
    private static Object instanceWith(Class<?> type, String field, BigDecimal value) throws Exception {
        RecordComponent[] components = type.getRecordComponents();
        Class<?>[] types = Arrays.stream(components).map(RecordComponent::getType).toArray(Class<?>[]::new);
        Object[] args = new Object[components.length];
        boolean found = false;
        for (int i = 0; i < components.length; i++) {
            if (components[i].getName().equals(field)) {
                assertThat(components[i].getType()).isEqualTo(BigDecimal.class);
                args[i] = value;
                found = true;
            } else if (types[i] == boolean.class) {
                args[i] = false;
            } else if (types[i] == int.class) {
                args[i] = 0;
            }
        }
        assertThat(found).as("%s has no component %s", type.getSimpleName(), field).isTrue();
        Constructor<?> canonical = type.getDeclaredConstructor(types);
        return canonical.newInstance(args);
    }
}
