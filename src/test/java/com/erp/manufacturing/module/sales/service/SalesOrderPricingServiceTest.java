package com.erp.manufacturing.module.sales.service;

import com.erp.manufacturing.common.exception.AppException;
import com.erp.manufacturing.common.exception.BusinessErrorCode;
import com.erp.manufacturing.common.exception.ErrorCode;
import com.erp.manufacturing.common.exception.MultiErrorException;
import com.erp.manufacturing.common.exception.ValidationErrorCode;
import com.erp.manufacturing.common.idempotency.IdempotencySupport;
import com.erp.manufacturing.module.inventory.domain.Item;
import com.erp.manufacturing.module.organization.domain.Company;
import com.erp.manufacturing.module.organization.domain.Plant;
import com.erp.manufacturing.module.sales.domain.CommercialCommand;
import com.erp.manufacturing.module.sales.domain.CommercialIdempotencyRecord;
import com.erp.manufacturing.module.sales.domain.SalesOrder;
import com.erp.manufacturing.module.sales.domain.SalesOrderLine;
import com.erp.manufacturing.module.sales.domain.SalesOrderPriceEvent;
import com.erp.manufacturing.module.sales.domain.SalesOrderStatus;
import com.erp.manufacturing.module.sales.dto.CommercialCommandResult;
import com.erp.manufacturing.module.sales.dto.SalesOrderCommercialPricesRequest;
import com.erp.manufacturing.module.sales.dto.SalesOrderLinePriceRequest;
import com.erp.manufacturing.module.sales.repository.CommercialIdempotencyRecordRepository;
import com.erp.manufacturing.module.sales.repository.SalesOrderPriceEventRepository;
import com.erp.manufacturing.module.sales.repository.SalesOrderRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** G10a (FE R02, Q6): filling missing prices of a confirmed order; known prices locked, zero included. */
@ExtendWith(MockitoExtension.class)
class SalesOrderPricingServiceTest {

    private static final String KEY = "price-key-1";

    @Mock SalesOrderRepository salesOrderRepository;
    @Mock SalesOrderPriceEventRepository priceEventRepository;
    @Mock CommercialIdempotencyRecordRepository idempotencyRepository;

    SalesOrderPricingService service;
    IdempotencySupport idempotencySupport;
    SalesOrder order;
    SalesOrderLine first;
    SalesOrderLine second;

    @BeforeEach
    void setUp() {
        idempotencySupport = new IdempotencySupport(new ObjectMapper().findAndRegisterModules());
        service = new SalesOrderPricingService(salesOrderRepository, priceEventRepository,
                new CommercialIdempotency(idempotencyRepository, idempotencySupport));
        Company company = Company.builder().companyId(UUID.randomUUID()).code("C").build();
        Plant plant = Plant.builder().plantId(UUID.randomUUID()).company(company).code("P").build();
        order = SalesOrder.builder().salesOrderId(UUID.randomUUID()).company(company).plant(plant)
                .orderNo("SO-1").customerName("A").orderDate(LocalDate.of(2026, 10, 1))
                .status(SalesOrderStatus.CONFIRMED).build();
        order.setVersion(3L);
        first = line(1, null);
        second = line(2, null);
        lenient().when(salesOrderRepository.findWithDetailsBySalesOrderId(order.getSalesOrderId()))
                .thenReturn(Optional.of(order));
        lenient().when(salesOrderRepository.saveAndFlush(any(SalesOrder.class))).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    void setPrices_fillsMissingPricesIncludingZero_recordsOneEventPerLineAndMovesThePricingVersion() {
        SalesOrderCommercialPricesRequest request = request(3L,
                price(first, "100000"), price(second, "0"));

        CommercialCommandResult result = service.setPrices(order.getSalesOrderId(), request, KEY);

        assertThat(first.getUnitPrice()).isEqualByComparingTo("100000");
        assertThat(second.getUnitPrice()).isEqualByComparingTo("0");
        assertThat(order.getPricingRevision()).isEqualTo(1);
        assertThat(result.type()).isEqualTo("SALES_PRICE");
        assertThat(result.documentId()).isEqualTo(order.getSalesOrderId());
        assertThat(result.pricingVersion()).isEqualTo("PRICE-1");
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<SalesOrderPriceEvent>> events = ArgumentCaptor.forClass(List.class);
        verify(priceEventRepository).saveAll(events.capture());
        assertThat(events.getValue()).extracting(SalesOrderPriceEvent::getSalesOrderLineId)
                .containsExactly(first.getSalesOrderLineId(), second.getSalesOrderLineId());
        assertThat(events.getValue()).allSatisfy(event -> {
            assertThat(event.getPreviousUnitPrice()).isNull();
            assertThat(event.getPricingRevision()).isEqualTo(1);
        });
        // The header is dirtied so the inverse-collection change still bumps the version exactly once (B112).
        assertThat(order.getUpdatedAt()).isNotNull();
        verify(idempotencyRepository).saveAndFlush(argThatRecord(CommercialCommand.SALES_PRICE_SET));
    }

    /** FE Q6: a known price is locked once confirmed — zero is a known price, not a missing one. */
    @Test
    void setPrices_onALineWhosePriceIsAlreadyKnownZero_isPriceLockedAndChangesNothing() {
        second.setUnitPrice(BigDecimal.ZERO);

        assertThatThrownBy(() -> service.setPrices(order.getSalesOrderId(),
                request(3L, price(first, "5"), price(second, "7")), KEY))
                .isInstanceOf(MultiErrorException.class)
                .satisfies(ex -> {
                    assertThat(((MultiErrorException) ex).getErrorCode()).isEqualTo(BusinessErrorCode.PRICE_LOCKED);
                    assertThat(((MultiErrorException) ex).getFieldErrors()).containsOnlyKeys("prices[1].unitPrice");
                });
        assertThat(first.getUnitPrice()).isNull();
        assertThat(order.getPricingRevision()).isZero();
        verifyNoInteractions(priceEventRepository);
        verify(salesOrderRepository, never()).saveAndFlush(any());
    }

    @Test
    void setPrices_duplicateOrForeignLine_isAFieldErrorBeforeAnyChange() {
        SalesOrderLinePriceRequest foreign = new SalesOrderLinePriceRequest(UUID.randomUUID(), new BigDecimal("1"));

        assertThatThrownBy(() -> service.setPrices(order.getSalesOrderId(),
                request(3L, price(first, "1"), price(first, "2"), foreign), KEY))
                .isInstanceOf(MultiErrorException.class)
                .satisfies(ex -> {
                    assertThat(((MultiErrorException) ex).getErrorCode()).isEqualTo(ValidationErrorCode.INVALID_INPUT);
                    assertThat(((MultiErrorException) ex).getFieldErrors())
                            .containsOnlyKeys("prices[1].salesOrderLineId", "prices[2].salesOrderLineId");
                });
        assertThat(first.getUnitPrice()).isNull();
        verifyNoInteractions(priceEventRepository);
    }

    @Test
    void setPrices_staleVersion_isConcurrentModification() {
        assertCode(() -> service.setPrices(order.getSalesOrderId(), request(2L, price(first, "1")), KEY),
                BusinessErrorCode.CONCURRENT_MODIFICATION);
        verifyNoInteractions(priceEventRepository);
    }

    @Test
    void setPrices_onADraftOrACancelledOrder_isStateConflict() {
        order.setStatus(SalesOrderStatus.DRAFT);
        assertCode(() -> service.setPrices(order.getSalesOrderId(), request(3L, price(first, "1")), KEY),
                BusinessErrorCode.STATE_CONFLICT);
        order.setStatus(SalesOrderStatus.CANCELLED);
        assertCode(() -> service.setPrices(order.getSalesOrderId(), request(3L, price(first, "1")), KEY),
                BusinessErrorCode.STATE_CONFLICT);
        verifyNoInteractions(priceEventRepository);
    }

    @Test
    void setPrices_withoutAnIdempotencyKey_isRefusedBeforeLoadingTheOrder() {
        assertCode(() -> service.setPrices(order.getSalesOrderId(), request(3L, price(first, "1")), " "),
                ValidationErrorCode.MISSING_REQUIRED_FIELD);
        verifyNoInteractions(salesOrderRepository, priceEventRepository);
    }

    /** R9: the retried body is one version behind; replay answers before the version check and writes nothing. */
    @Test
    void setPrices_replayOfTheSameKeyAndPayload_returnsTheCurrentStateWithoutWriting() {
        SalesOrderCommercialPricesRequest request = request(3L, price(first, "100"));
        CommercialIdempotency.Fingerprint fingerprint = new CommercialIdempotency.Fingerprint(order.getSalesOrderId(), request);
        when(idempotencyRepository.findByIdempotencyKeyAndCommand(KEY, CommercialCommand.SALES_PRICE_SET))
                .thenReturn(Optional.of(CommercialIdempotencyRecord.builder().idempotencyKey(KEY)
                        .command(CommercialCommand.SALES_PRICE_SET).resourceId(order.getSalesOrderId())
                        .payloadHash(idempotencySupport.payloadHash(fingerprint)).build()));
        order.setVersion(4L);
        first.setUnitPrice(new BigDecimal("100"));
        order.bumpPricingRevision();

        CommercialCommandResult result = service.setPrices(order.getSalesOrderId(), request, KEY);

        assertThat(result.version()).isEqualTo(4L);
        assertThat(result.pricingVersion()).isEqualTo("PRICE-1");
        verifyNoInteractions(priceEventRepository);
        verify(salesOrderRepository, never()).saveAndFlush(any());
    }

    @Test
    void setPrices_sameKeyWithAnotherPayload_isIdempotencyConflict() {
        CommercialIdempotency.Fingerprint other = new CommercialIdempotency.Fingerprint(order.getSalesOrderId(),
                request(3L, price(first, "999")));
        when(idempotencyRepository.findByIdempotencyKeyAndCommand(KEY, CommercialCommand.SALES_PRICE_SET))
                .thenReturn(Optional.of(CommercialIdempotencyRecord.builder().idempotencyKey(KEY)
                        .command(CommercialCommand.SALES_PRICE_SET).resourceId(order.getSalesOrderId())
                        .payloadHash(idempotencySupport.payloadHash(other)).build()));

        assertCode(() -> service.setPrices(order.getSalesOrderId(), request(3L, price(first, "1")), KEY),
                BusinessErrorCode.IDEMPOTENCY_CONFLICT);
        verifyNoInteractions(priceEventRepository);
    }

    private SalesOrderLine line(int lineNo, BigDecimal unitPrice) {
        SalesOrderLine line = SalesOrderLine.builder().salesOrderLineId(UUID.randomUUID()).salesOrder(order)
                .item(Item.builder().itemId(UUID.randomUUID()).build()).lineNo(lineNo)
                .orderedQuantity(BigDecimal.TEN).dueDate(LocalDate.of(2026, 10, 9)).unitPrice(unitPrice).build();
        order.getLines().add(line);
        return line;
    }

    private static SalesOrderLinePriceRequest price(SalesOrderLine line, String value) {
        return new SalesOrderLinePriceRequest(line.getSalesOrderLineId(), new BigDecimal(value));
    }

    private static SalesOrderCommercialPricesRequest request(Long version, SalesOrderLinePriceRequest... prices) {
        return new SalesOrderCommercialPricesRequest(version, List.of(prices));
    }

    private static CommercialIdempotencyRecord argThatRecord(CommercialCommand command) {
        return argThat(record -> record.getCommand() == command && KEY.equals(record.getIdempotencyKey()));
    }

    private static void assertCode(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, ErrorCode code) {
        assertThatThrownBy(call)
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode()).isEqualTo(code));
    }
}
