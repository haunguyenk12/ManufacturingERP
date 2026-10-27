package com.erp.manufacturing.module.workorder.service.cost;

import java.util.List;

/**
 * G11 (D6, FE §1.3): who may see production cost. "Cost view" is {@code PERM_WORK_ORDER_VARIANCE_READ} or
 * {@code PERM_PRODUCTION_COST_CALCULATE} on the work order's plant. Closing needs {@code PERM_WORK_ORDER_CLOSE} <b>and</b>
 * cost view (FE Q28 — no blind close). The checklist is also readable by the shop floor, without money (FE R27).
 */
public final class CostAccess {

    public static final String COST_VIEW_CODES = "{'PERM_WORK_ORDER_VARIANCE_READ', 'PERM_PRODUCTION_COST_CALCULATE'}";
    public static final List<String> COST_VIEW = List.of("PERM_WORK_ORDER_VARIANCE_READ", "PERM_PRODUCTION_COST_CALCULATE");
    public static final List<String> CHECKLIST_READ = List.of("PERM_WORK_ORDER_VARIANCE_READ", "PERM_PRODUCTION_COST_CALCULATE",
            "PERM_WORK_ORDER_CLOSE", "PERM_WORK_ORDER_READ", "PERM_WIP_READ");

    public static final String COST_VIEW_ON_WORK_ORDER =
            "@workOrderPermissionGuard.hasAnyWorkOrderAccess(authentication, " + COST_VIEW_CODES + ", #workOrderId)";
    public static final String CHECKLIST_ON_WORK_ORDER = "@workOrderPermissionGuard.hasAnyWorkOrderAccess(authentication, "
            + "{'PERM_WORK_ORDER_VARIANCE_READ', 'PERM_PRODUCTION_COST_CALCULATE', 'PERM_WORK_ORDER_CLOSE', "
            + "'PERM_WORK_ORDER_READ', 'PERM_WIP_READ'}, #workOrderId)";
    public static final String CALCULATE_ON_WORK_ORDER =
            "@workOrderPermissionGuard.hasWorkOrderAccess(authentication, 'PERM_PRODUCTION_COST_CALCULATE', #workOrderId)";
    public static final String CLOSE_ON_WORK_ORDER =
            "@workOrderPermissionGuard.hasWorkOrderAccess(authentication, 'PERM_WORK_ORDER_CLOSE', #workOrderId) and "
                    + COST_VIEW_ON_WORK_ORDER;

    private CostAccess() {
    }
}
