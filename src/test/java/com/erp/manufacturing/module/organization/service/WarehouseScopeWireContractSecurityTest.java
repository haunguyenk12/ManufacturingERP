package com.erp.manufacturing.module.organization.service;

import com.erp.manufacturing.module.organization.domain.AccessScope;
import com.erp.manufacturing.module.organization.domain.OrganizationStatus;
import com.erp.manufacturing.module.organization.domain.ScopeType;
import com.erp.manufacturing.module.organization.dto.AccessScopeCreateRequest;
import com.erp.manufacturing.module.organization.mapper.OrganizationMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Pins the public WAREHOUSE wire contract while retaining WAREHOUSE_GROUP in persistence. */
@DisplayName("Warehouse access-scope wire contract")
class WarehouseScopeWireContractSecurityTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final OrganizationMapper mapper = new OrganizationMapper();

    @Test
    void request_acceptsCanonicalWarehouseWireValue() throws Exception {
        AccessScopeCreateRequest request = objectMapper.readValue("""
                {
                  "code": "WAREHOUSES-NORTH",
                  "name": "Northern warehouses",
                  "scopeType": "WAREHOUSE",
                  "description": null
                }
                """, AccessScopeCreateRequest.class);

        assertThat(request.scopeType()).isEqualTo(ScopeType.WAREHOUSE_GROUP);
    }

    @Test
    void request_keepsAcceptingLegacyWarehouseGroupValueDuringMigration() throws Exception {
        AccessScopeCreateRequest request = objectMapper.readValue("""
                {
                  "code": "WAREHOUSES-LEGACY",
                  "name": "Legacy warehouses",
                  "scopeType": "WAREHOUSE_GROUP",
                  "description": null
                }
                """, AccessScopeCreateRequest.class);

        assertThat(request.scopeType()).isEqualTo(ScopeType.WAREHOUSE_GROUP);
    }

    @Test
    void response_emitsCanonicalWarehouseWireValue() {
        AccessScope scope = AccessScope.builder()
                .scopeId(UUID.randomUUID())
                .code("WAREHOUSES-NORTH")
                .name("Northern warehouses")
                .scopeType(ScopeType.WAREHOUSE_GROUP)
                .status(OrganizationStatus.ACTIVE)
                .build();

        assertThat(mapper.toResponse(scope).scopeType()).isEqualTo("WAREHOUSE");
    }
}
