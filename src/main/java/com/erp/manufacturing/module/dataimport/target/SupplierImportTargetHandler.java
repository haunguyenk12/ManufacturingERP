package com.erp.manufacturing.module.dataimport.target;

import com.erp.manufacturing.module.dataimport.domain.ImportTargetType;
import com.erp.manufacturing.module.purchasing.dto.CompanySupplierCreateRequest;
import com.erp.manufacturing.module.purchasing.service.SupplierLookupService;
import com.erp.manufacturing.module.purchasing.service.SupplierService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Imports supplier master rows through {@code SupplierService.createForCompany} (G3), so the write
 * carries that method's own {@code PERM_SUPPLIER_MANAGE} check and {@code SUPPLIER_CREATED} audit.
 */
@Component
@RequiredArgsConstructor
public class SupplierImportTargetHandler extends PartnerImportTargetHandler {

    /** {@code suppliers.phone} is {@code VARCHAR(60)} (V20) — narrower than the customer column. */
    public static final int PHONE_MAX_LENGTH = 60;

    private final SupplierService supplierService;
    private final SupplierLookupService supplierLookupService;

    @Override
    public ImportTargetType targetType() {
        return ImportTargetType.SUPPLIER;
    }

    @Override
    protected String label() {
        return "Supplier";
    }

    @Override
    public int phoneMaxLength() {
        return PHONE_MAX_LENGTH;
    }

    @Override
    protected Set<String> existingCodes(UUID companyId, Set<String> codes) {
        return supplierLookupService.findSuppliersByCode(companyId, codes).keySet();
    }

    @Override
    public UUID apply(UUID companyId, Map<String, String> values) {
        return supplierService.createForCompany(companyId, new CompanySupplierCreateRequest(
                normaliseCode(values.get(FIELD_CODE)),
                values.get(FIELD_NAME),
                values.get(FIELD_EMAIL),
                values.get(FIELD_PHONE),
                values.get(FIELD_ADDRESS),
                null,
                values.get(FIELD_CONTACT_NAME),
                values.get(FIELD_NOTES))).supplierId();
    }
}
