package com.erp.manufacturing.common.audit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class AuditChangeCaptureServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AuditChangeCaptureService service = new AuditChangeCaptureService(
            mock(jakarta.persistence.EntityManager.class), objectMapper);

    @Test
    void calculateChanges_update_returnsOnlyChangedBusinessFieldsWithBeforeAndAfterValues() {
        Map<String, JsonNode> before = Map.of(
                "uomId", objectMapper.valueToTree("5c06bd98-b2ca-4fdc-9f6d-1dc260602e73"),
                "name", objectMapper.valueToTree("Piece"),
                "description", objectMapper.valueToTree("Old description"),
                "status", objectMapper.valueToTree("ACTIVE"));
        TestResponse after = new TestResponse(
                "5c06bd98-b2ca-4fdc-9f6d-1dc260602e73", "Piece updated",
                "Old description", "ACTIVE", "must-not-be-audited");

        var changes = service.calculateChanges(AuditAction.UOM_UPDATED, before, after);

        assertThat(changes).singleElement().satisfies(change -> {
            assertThat(change.fieldName()).isEqualTo("name");
            assertThat(change.oldValue()).isEqualTo("\"Piece\"");
            assertThat(change.newValue()).isEqualTo("\"Piece updated\"");
            assertThat(change.changeType()).isEqualTo(AuditLogChangeType.UPDATE);
        });
    }

    @Test
    void calculateChanges_create_excludesTechnicalAndSensitiveFields() {
        var changes = service.calculateChanges(
                AuditAction.USER_CREATED, Map.of(),
                new TestResponse("user-1", "Admin", null, "ACTIVE", "secret-value"));

        assertThat(changes).extracting(AuditFieldChange::fieldName)
                .contains("uomId", "name", "status")
                .doesNotContain("passwordHash");
        assertThat(changes).allMatch(change -> change.oldValue() == null
                && change.changeType() == AuditLogChangeType.CREATE);
    }

    @Test
    void resolveEntityName_prefersEntitySpecificNumberOverRelatedNames() {
        WorkOrderTestResponse response = new WorkOrderTestResponse(
                "WO-2026-001", "Finished product", "PLANT-01");

        String entityName = service.resolveEntityName("WorkOrder", Map.of(), response);

        assertThat(entityName).isEqualTo("WO-2026-001");
    }

    @Test
    void resolveEntityName_fallsBackToPreviousNameWhenResponseHasNoDisplayField() {
        Map<String, JsonNode> before = Map.of("name", objectMapper.valueToTree("Piece"));

        String entityName = service.resolveEntityName(
                "Uom", before, new IdOnlyResponse("5c06bd98-b2ca-4fdc-9f6d-1dc260602e73"));

        assertThat(entityName).isEqualTo("Piece");
    }

    private record TestResponse(
            String uomId,
            String name,
            String description,
            String status,
            String passwordHash
    ) {
    }

    private record WorkOrderTestResponse(
            String workOrderNo,
            String productItemName,
            String plantCode
    ) {
    }

    private record IdOnlyResponse(String uomId) {
    }
}
