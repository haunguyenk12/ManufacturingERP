package com.erp.manufacturing.common.audit;

import com.erp.manufacturing.common.audit.model.AuditActorSnapshot;
import com.erp.manufacturing.common.audit.model.AuditEntityRef;
import com.erp.manufacturing.common.audit.model.AuditOutcome;
import com.erp.manufacturing.common.audit.model.AuditRecordDraft;
import com.erp.manufacturing.common.audit.model.AuditRequestSnapshot;
import com.erp.manufacturing.common.audit.model.AuditScope;
import com.erp.manufacturing.common.audit.model.AuditSource;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("AuditPayloadCodec")
class AuditPayloadCodecTest {

    private final AuditPayloadCodec codec = new AuditPayloadCodec(new ObjectMapper());

    @Test
    @DisplayName("round-trips every field the dispatcher needs to materialise a row")
    void encodeThenDecode_preservesTheWholeEvent() {
        UUID eventId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        UUID plantId = UUID.randomUUID();
        AuditRecordDraft original = new AuditRecordDraft(
                eventId, Instant.parse("2026-09-01T08:15:30Z"), AuditAction.PERMISSION_GRANTED,
                AuditOutcome.SUCCESS, AuditActorSnapshot.user(actorId, "admin"),
                List.of(AuditEntityRef.primary("Role", "role-1", "ADMIN"),
                        AuditEntityRef.related("Permission", "perm-1", "PERM_AUDIT_READ")),
                AuditScope.of(null, plantId, null), AuditSource.HTTP,
                new AuditRequestSnapshot("trace-1", "10.0.0.1", "curl/8.0", "POST", "/v1/x"),
                "PERMISSION_GRANTED", "granted", List.of(), "{\"noOp\":true}");

        AuditRecordDraft decoded = codec.decode(codec.encode(original));

        assertThat(decoded.eventId()).isEqualTo(eventId);
        assertThat(decoded.occurredAt()).isEqualTo(original.occurredAt());
        assertThat(decoded.action()).isEqualTo(AuditAction.PERMISSION_GRANTED);
        assertThat(decoded.outcome()).isEqualTo(AuditOutcome.SUCCESS);
        assertThat(decoded.actor().userId()).isEqualTo(actorId);
        assertThat(decoded.scope().plantId()).isEqualTo(plantId);
        assertThat(decoded.request().userAgent()).isEqualTo("curl/8.0");
        assertThat(decoded.reasonCode()).isEqualTo("PERMISSION_GRANTED");
        assertThat(decoded.metadataJson()).isEqualTo("{\"noOp\":true}");
        assertThat(decoded.entities()).hasSize(2);
        assertThat(decoded.primaryEntity().entityName()).isEqualTo("ADMIN");
    }

    @Test
    @DisplayName("keeps a field-change snapshot as a JSON document, not as a double-encoded string")
    void changeValues_surviveAsJsonDocuments() {
        // AuditFieldChange holds JSON, and the storage column is jsonb. Encoding the value as a
        // *string* would nest it: the stored snapshot would come back as "\"{\\\"a\\\":1}\"".
        AuditRecordDraft draft = AuditRecordDraft.builder()
                .action(AuditAction.ITEM_UPDATED)
                .changes(List.of(
                        new AuditFieldChange("name", "\"old\"", "\"new\"", AuditLogChangeType.UPDATE),
                        new AuditFieldChange("lines", null, "[{\"lineNo\":1}]", AuditLogChangeType.CREATE)))
                .build();

        AuditRecordDraft decoded = codec.decode(codec.encode(draft));

        assertThat(decoded.changes()).hasSize(2);
        assertThat(decoded.changes().get(0).oldValue()).isEqualTo("\"old\"");
        assertThat(decoded.changes().get(1).newValue()).isEqualTo("[{\"lineNo\":1}]");
        assertThat(decoded.changes().get(1).oldValue()).isNull();
    }

    @Test
    @DisplayName("hashes identical events identically and different events differently")
    void hash_isStableForTheSameEventAndSensitiveToChange() {
        // The tamper-evidence hash has to be reproducible years later, which is why the payload is
        // written key-by-key rather than by reflection over a record whose shape can drift.
        AuditRecordDraft draft = AuditRecordDraft.builder()
                .eventId(UUID.fromString("00000000-0000-0000-0000-000000000001"))
                .occurredAt(Instant.parse("2026-09-01T08:15:30Z"))
                .action(AuditAction.LOGIN)
                .primaryEntity("User", "user-1", "admin")
                .build();
        AuditRecordDraft tampered = AuditRecordDraft.builder()
                .eventId(draft.eventId())
                .occurredAt(draft.occurredAt())
                .action(AuditAction.LOGIN)
                .primaryEntity("User", "user-1", "someone-else")
                .build();

        assertThat(codec.hash(codec.toNode(draft)))
                .isEqualTo(codec.hash(codec.toNode(draft)))
                .isNotEqualTo(codec.hash(codec.toNode(tampered)))
                .hasSize(64);
    }
}
