package com.erp.manufacturing.module.dataimport.target;

import com.erp.manufacturing.module.dataimport.domain.ImportTargetType;
import com.erp.manufacturing.module.sales.dto.CustomerCreateRequest;
import com.erp.manufacturing.module.sales.service.CustomerLookupService;
import com.erp.manufacturing.module.sales.service.CustomerService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Imports customer master rows through {@code CustomerService.create} (G3), so the write carries that
 * method's own {@code PERM_CUSTOMER_MANAGE} check and {@code CUSTOMER_CREATED} audit.
 */
@Component
@RequiredArgsConstructor
public class CustomerImportTargetHandler extends PartnerImportTargetHandler {

    public static final int PHONE_MAX_LENGTH = 255;

    private final CustomerService customerService;
    private final CustomerLookupService customerLookupService;

    @Override
    public ImportTargetType targetType() {
        return ImportTargetType.CUSTOMER;
    }

    @Override
    protected String label() {
        return "Customer";
    }

    @Override
    public int phoneMaxLength() {
        return PHONE_MAX_LENGTH;
    }

    @Override
    protected Set<String> existingCodes(UUID companyId, Set<String> codes) {
        return customerLookupService.findCustomersByCode(companyId, codes).keySet();
    }

    @Override
    public UUID apply(UUID companyId, Map<String, String> values) {
        return customerService.create(companyId, new CustomerCreateRequest(
                normaliseCode(values.get(FIELD_CODE)),
                values.get(FIELD_NAME),
                values.get(FIELD_CONTACT_NAME),
                values.get(FIELD_EMAIL),
                values.get(FIELD_PHONE),
                values.get(FIELD_ADDRESS),
                values.get(FIELD_NOTES))).customerId();
    }
}
