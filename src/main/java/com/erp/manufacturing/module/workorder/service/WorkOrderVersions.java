package com.erp.manufacturing.module.workorder.service;

import com.erp.manufacturing.common.exception.BusinessErrorCode;
import com.erp.manufacturing.common.exception.ExceptionFactory;
import com.erp.manufacturing.common.exception.ValidationErrorCode;
import com.erp.manufacturing.module.workorder.domain.WorkOrder;

import java.util.Map;
import java.util.Objects;

/**
 * G8 (D5): every command on a work order carries the version the client last read. Checked before the
 * command touches anything (rule C9); the {@code @Version} check at flush is what still catches two
 * requests that both passed this line at the same moment.
 */
public final class WorkOrderVersions {

    private WorkOrderVersions() {
    }

    /**
     * @param field the request field that carried the version, named in the 400 when it is missing
     */
    public static void ensure(WorkOrder workOrder, Long expected, String field) {
        if (expected == null) {
            throw ExceptionFactory.withErrors(ValidationErrorCode.INVALID_INPUT, Map.of(field, "must not be null"));
        }
        if (!Objects.equals(workOrder.getVersion(), expected)) {
            throw ExceptionFactory.custom(BusinessErrorCode.CONCURRENT_MODIFICATION,
                    "Work order version is " + workOrder.getVersion() + ", not " + expected + "; reload it");
        }
    }
}
