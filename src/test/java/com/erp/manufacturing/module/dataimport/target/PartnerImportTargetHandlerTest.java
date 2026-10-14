package com.erp.manufacturing.module.dataimport.target;

import com.erp.manufacturing.module.dataimport.domain.ImportCellError;
import com.erp.manufacturing.module.dataimport.domain.ImportErrorCode;
import com.erp.manufacturing.module.purchasing.domain.Supplier;
import com.erp.manufacturing.module.purchasing.dto.CompanySupplierCreateRequest;
import com.erp.manufacturing.module.purchasing.dto.SupplierResponse;
import com.erp.manufacturing.module.purchasing.service.SupplierLookupService;
import com.erp.manufacturing.module.purchasing.service.SupplierService;
import com.erp.manufacturing.module.sales.dto.CustomerCreateRequest;
import com.erp.manufacturing.module.sales.dto.CustomerResponse;
import com.erp.manufacturing.module.sales.service.CustomerLookupService;
import com.erp.manufacturing.module.sales.service.CustomerService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PartnerImportTargetHandlerTest {

    private static final UUID COMPANY = UUID.randomUUID();

    private final SupplierService supplierService = mock(SupplierService.class);
    private final SupplierLookupService supplierLookup = mock(SupplierLookupService.class);
    private final CustomerService customerService = mock(CustomerService.class);
    private final CustomerLookupService customerLookup = mock(CustomerLookupService.class);
    private final SupplierImportTargetHandler suppliers = new SupplierImportTargetHandler(supplierService, supplierLookup);
    private final CustomerImportTargetHandler customers = new CustomerImportTargetHandler(customerService, customerLookup);

    @Test
    void validateBatch_flagsShapeRepeatTakenCodeAndEmail_inOneLookup() {
        when(supplierLookup.findSuppliersByCode(eq(COMPANY), any()))
                .thenReturn(Map.of("TAKEN", Supplier.builder().code("TAKEN").build()));

        Map<Integer, List<ImportCellError>> problems = suppliers.validateBatch(COMPANY, List.of(
                row("bad code", null), row("taken", null), row("ok", "nope"), row("OK", null)));

        assertThat(problems.get(0)).extracting(ImportCellError::targetField, ImportCellError::code)
                .containsExactly(tuple("code", ImportErrorCode.INVALID_FORMAT));
        assertThat(problems.get(1)).extracting(ImportCellError::code).containsExactly(ImportErrorCode.ALREADY_EXISTS);
        assertThat(problems.get(2)).extracting(ImportCellError::targetField, ImportCellError::code)
                .containsExactly(tuple("email", ImportErrorCode.INVALID_FORMAT));
        assertThat(problems.get(3)).extracting(ImportCellError::code).containsExactly(ImportErrorCode.DUPLICATE_IN_FILE);
        verify(supplierLookup).findSuppliersByCode(COMPANY, Set.of("TAKEN", "OK"));
    }

    /** The write goes through the owning service, normalised the way that service stores it. */
    @Test
    void apply_supplier_goesThroughCreateForCompanyWithNormalisedCode() {
        UUID created = UUID.randomUUID();
        SupplierResponse response = mock(SupplierResponse.class);
        when(response.supplierId()).thenReturn(created);
        when(supplierService.createForCompany(eq(COMPANY), any())).thenReturn(response);

        assertThat(suppliers.apply(COMPANY, values(" ncc-01 ", "a@b.vn"))).isEqualTo(created);

        ArgumentCaptor<CompanySupplierCreateRequest> request = ArgumentCaptor.forClass(CompanySupplierCreateRequest.class);
        verify(supplierService).createForCompany(eq(COMPANY), request.capture());
        assertThat(request.getValue().code()).isEqualTo("NCC-01");
        assertThat(request.getValue().contactName()).isEqualTo("Contact");
        assertThat(request.getValue().email()).isEqualTo("a@b.vn");
        assertThat(request.getValue().taxCode()).isNull();
    }

    @Test
    void apply_customer_goesThroughCustomerServiceCreate() {
        UUID created = UUID.randomUUID();
        CustomerResponse response = mock(CustomerResponse.class);
        when(response.customerId()).thenReturn(created);
        when(customerService.create(eq(COMPANY), any())).thenReturn(response);

        assertThat(customers.apply(COMPANY, values("KH-01", null))).isEqualTo(created);

        ArgumentCaptor<CustomerCreateRequest> request = ArgumentCaptor.forClass(CustomerCreateRequest.class);
        verify(customerService).create(eq(COMPANY), request.capture());
        assertThat(request.getValue().code()).isEqualTo("KH-01");
        assertThat(request.getValue().phone()).isEqualTo("0000123456");
        assertThat(request.getValue().email()).isNull();
    }

    @Test
    void descriptor_phoneWidthFollowsEachTable() {
        assertThat(suppliers.descriptor().fields()).filteredOn(field -> field.name().equals("phone"))
                .singleElement().satisfies(field -> assertThat(field.maxLength()).isEqualTo(60));
        assertThat(customers.descriptor().fields()).filteredOn(field -> field.name().equals("phone"))
                .singleElement().satisfies(field -> assertThat(field.maxLength()).isEqualTo(255));
    }

    private static Map<String, String> row(String code, String email) {
        Map<String, String> row = new HashMap<>();
        row.put("code", code);
        row.put("email", email);
        return row;
    }

    private static Map<String, String> values(String code, String email) {
        Map<String, String> values = new HashMap<>();
        values.put("code", code);
        values.put("name", "Name");
        values.put("contactName", "Contact");
        values.put("email", email);
        values.put("phone", "0000123456");
        values.put("address", null);
        values.put("notes", null);
        return values;
    }
}
