package com.erp.manufacturing.module.sales.service;

import com.erp.manufacturing.module.inventory.domain.Item;
import com.erp.manufacturing.module.organization.domain.Company;
import com.erp.manufacturing.module.organization.domain.Plant;
import com.erp.manufacturing.module.organization.security.PermissionGuard;
import com.erp.manufacturing.module.sales.domain.SalesOrder;
import com.erp.manufacturing.module.sales.domain.SalesOrderLine;
import com.erp.manufacturing.module.sales.domain.SalesOrderStatus;
import com.erp.manufacturing.module.sales.dto.SalesOrderDraftLineRequest;
import com.erp.manufacturing.module.sales.dto.SalesOrderResponse;
import com.erp.manufacturing.module.sales.mapper.SalesOrderMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * G10a (D6): sales prices are on the order only for callers who may see them, and "may not see" is an
 * <b>absent</b> field, never a {@code null} one ({@code default-property-inclusion: always} would print the
 * null). Asserted on the JSON, not on the DTO, because the absence is a serialisation property.
 */
class SalesOrderCommercialShapeTest {

    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();
    private final SalesOrderMapper mapper = new SalesOrderMapper();

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void detail_forACallerWhoMaySeePrices_carriesThePriceTheMissingOneAsNullAndTheOrderTotals() {
        SalesOrder order = confirmedOrder(new BigDecimal("100000"), null);

        JsonNode body = json.valueToTree(mapper.toDetailResponse(order, Map.of(), Map.of(), List.of(), true, Map.of()));

        assertThat(body.get("lines").get(0).get("commercial").get("unitPrice").decimalValue()).isEqualByComparingTo("100000");
        assertThat(body.get("lines").get(0).get("commercial").get("priceKnown").asBoolean()).isTrue();
        assertThat(body.get("lines").get(1).get("commercial").has("unitPrice")).isTrue();
        assertThat(body.get("lines").get(1).get("commercial").get("unitPrice").isNull()).isTrue();
        assertThat(body.get("lines").get(1).get("commercial").get("priceKnown").asBoolean()).isFalse();
        assertThat(body.get("commercial").get("currency").asText()).isEqualTo("VND");
        assertThat(body.get("commercial").get("pricingVersion").asText()).isEqualTo("PRICE-0");
        assertThat(body.get("commercial").get("missingPriceLineCount").asInt()).isEqualTo(1);
    }

    @Test
    void detail_forACallerWhoMayNotSeePrices_hasNoMoneyFieldAtAll() {
        SalesOrder order = confirmedOrder(new BigDecimal("100000"), new BigDecimal("5"));

        JsonNode body = json.valueToTree(mapper.toDetailResponse(order, Map.of(), Map.of(), List.of(), false, Map.of()));

        assertThat(body.has("commercial")).isFalse();
        assertThat(body.get("lines")).allSatisfy(line -> assertThat(line.has("commercial")).isFalse());
        assertThat(body.toString()).doesNotContain("100000").doesNotContain("unitPrice");
    }

    /** D3: an order already past DRAFT when V86 ran has no delivery baseline, and says why. */
    @Test
    void detail_ofALegacyOrder_reportsTheUnknownBaselineWithItsReason() {
        SalesOrder order = confirmedOrder(null, null);
        order.setDeliveryBaselineKnown(false);

        SalesOrderResponse response = mapper.toDetailResponse(order, Map.of(), Map.of(), List.of(), false, Map.of());

        assertThat(response.deliveryBaselineKnown()).isFalse();
        assertThat(response.deliveryBaselineUnknownReason()).isEqualTo(SalesOrderResponse.LEGACY_ORDER);
        order.setDeliveryBaselineKnown(true);
        assertThat(mapper.toDetailResponse(order, Map.of(), Map.of(), List.of(), false, Map.of()).deliveryBaselineUnknownReason())
                .isNull();
    }

    /** The legacy and list shapes never carry money, whoever calls. */
    @Test
    void legacyAndListShapes_neverCarryMoney() {
        SalesOrder order = confirmedOrder(new BigDecimal("1"), new BigDecimal("2"));

        assertThat(json.valueToTree(mapper.toResponse(order, true)).has("commercial")).isFalse();
        assertThat(json.valueToTree(mapper.toResponse(order, true)).get("lines").get(0).has("commercial")).isFalse();
        assertThat(json.valueToTree(mapper.toListResponse(order, null)).has("commercial")).isFalse();
    }

    @Test
    void priceVisibility_isGrantedByPriceManageOrAnyInvoiceAuthorityOnThePlant() {
        PermissionGuard guard = mock(PermissionGuard.class);
        UUID plantId = UUID.randomUUID();
        Authentication auth = new UsernamePasswordAuthenticationToken("u", null, List.of());
        SecurityContextHolder.getContext().setAuthentication(auth);
        SalesPriceVisibility visibility = new SalesPriceVisibility(guard);

        assertThat(visibility.canSeePrices(plantId)).isFalse();
        when(guard.hasResourceAccess(eq(auth), eq("PERM_SALES_INVOICE_POST"), eq("PLANT"), eq(plantId))).thenReturn(true);
        assertThat(visibility.canSeePrices(plantId)).isTrue();
    }

    @Test
    void priceVisibility_isNeverGrantedByOrderReadOrDeliveryAuthority() {
        PermissionGuard guard = mock(PermissionGuard.class);
        UUID plantId = UUID.randomUUID();
        Authentication auth = new UsernamePasswordAuthenticationToken("u", null, List.of());
        SecurityContextHolder.getContext().setAuthentication(auth);
        when(guard.hasResourceAccess(any(), anyString(), eq("PLANT"), eq(plantId)))
                .thenAnswer(i -> List.of("PERM_SALES_ORDER_READ", "PERM_DELIVERY_POST", "PERM_CUSTOMER_PAYMENT_POST")
                        .contains(i.getArgument(1, String.class)));

        assertThat(new SalesPriceVisibility(guard).canSeePrices(plantId)).isFalse();
        assertThat(SalesPriceVisibility.PRICE_PERMISSIONS).containsExactlyInAnyOrder(
                "PERM_SALES_PRICE_MANAGE", "PERM_SALES_INVOICE_READ", "PERM_SALES_INVOICE_MANAGE", "PERM_SALES_INVOICE_POST");
    }

    @Test
    void pricesChange_detectsSetClearedOrChangedPricesByLinePosition_butNotScaleOnlyDifferences() {
        SalesOrder order = confirmedOrder(new BigDecimal("10.000000"), null);
        LocalDate due = LocalDate.of(2026, 10, 20);
        UUID item = UUID.randomUUID();

        assertThat(SalesOrderService.pricesChange(order.getLines(), List.of(
                new SalesOrderDraftLineRequest(item, BigDecimal.ONE, due, new BigDecimal("10")),
                new SalesOrderDraftLineRequest(item, BigDecimal.ONE, due, null)))).isFalse();
        assertThat(SalesOrderService.pricesChange(order.getLines(), List.of(
                new SalesOrderDraftLineRequest(item, BigDecimal.ONE, due, new BigDecimal("10")),
                new SalesOrderDraftLineRequest(item, BigDecimal.ONE, due, BigDecimal.ZERO)))).isTrue();
        assertThat(SalesOrderService.pricesChange(order.getLines(), List.of(
                new SalesOrderDraftLineRequest(item, BigDecimal.ONE, due, null),
                new SalesOrderDraftLineRequest(item, BigDecimal.ONE, due, null)))).isTrue();
        assertThat(SalesOrderService.pricesChange(order.getLines(), List.of(
                new SalesOrderDraftLineRequest(item, BigDecimal.ONE, due, new BigDecimal("10"))))).isTrue();
    }

    private static SalesOrder confirmedOrder(BigDecimal firstPrice, BigDecimal secondPrice) {
        Company company = Company.builder().companyId(UUID.randomUUID()).code("C").build();
        Plant plant = Plant.builder().plantId(UUID.randomUUID()).company(company).code("P").build();
        SalesOrder order = SalesOrder.builder().salesOrderId(UUID.randomUUID()).company(company).plant(plant)
                .orderNo("SO-1").customerName("A").orderDate(LocalDate.of(2026, 10, 1))
                .status(SalesOrderStatus.CONFIRMED).build();
        order.getLines().add(line(order, 1, firstPrice));
        order.getLines().add(line(order, 2, secondPrice));
        return order;
    }

    private static SalesOrderLine line(SalesOrder order, int lineNo, BigDecimal price) {
        return SalesOrderLine.builder().salesOrderLineId(UUID.randomUUID()).salesOrder(order)
                .item(Item.builder().itemId(UUID.randomUUID()).code("FG").name("FG").unit("PCS").build())
                .lineNo(lineNo).orderedQuantity(BigDecimal.TEN).dueDate(LocalDate.of(2026, 10, 9))
                .unitPrice(price).build();
    }
}
