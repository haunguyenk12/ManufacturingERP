package com.erp.manufacturing.common.audit;

import com.erp.manufacturing.common.audit.model.AuditActorSnapshot;
import com.erp.manufacturing.common.audit.model.AuditEntityRef;
import com.erp.manufacturing.common.audit.model.AuditOutcome;
import com.erp.manufacturing.common.audit.model.AuditRecordDraft;
import com.erp.manufacturing.common.audit.model.AuditRequestSnapshot;
import com.erp.manufacturing.common.audit.model.AuditScope;
import com.erp.manufacturing.common.audit.model.AuditSource;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Serialises an {@link AuditRecordDraft} to the {@code audit_outbox.payload} document and back.
 *
 * <p>The mapping is written by hand rather than left to Jackson's record support, for two reasons
 * that both matter here:
 *
 * <ol>
 *   <li><strong>Canonical form.</strong> The tamper-evidence hash (AR-7) has to be reproducible years
 *       later. Reflection-driven output changes shape when a field is added, renamed or reordered,
 *       and every such change would invalidate every stored hash. An explicit writer freezes the
 *       shape, and {@link ObjectMapper#writeValueAsBytes} over a node built key-by-key is stable.</li>
 *   <li><strong>Decoding a payload written by an older build.</strong> The dispatcher may read a row
 *       an earlier version produced. Field-by-field reads with defaults degrade; a strict
 *       record binding throws, and a payload that cannot be parsed is an audit event that can never
 *       be delivered.</li>
 * </ol>
 */
@Component
@RequiredArgsConstructor
public class AuditPayloadCodec {

    /** Bumped only when the payload shape changes in a way a reader must know about. */
    public static final int PAYLOAD_VERSION = 1;

    private final ObjectMapper objectMapper;

    public String encode(AuditRecordDraft draft) {
        return toNode(draft).toString();
    }

    public ObjectNode toNode(AuditRecordDraft draft) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("v", PAYLOAD_VERSION);
        root.put("eventId", draft.eventId().toString());
        root.put("occurredAt", draft.occurredAt().toString());
        root.put("action", draft.action().name());
        root.put("outcome", draft.outcome().name());
        root.put("source", draft.source().name());
        putNullable(root, "reasonCode", draft.reasonCode());
        putNullable(root, "description", draft.description());

        ObjectNode actor = root.putObject("actor");
        putNullable(actor, "userId", draft.actor().userId() == null ? null : draft.actor().userId().toString());
        putNullable(actor, "username", draft.actor().username());
        actor.put("actorType", draft.actor().actorType().name());

        ObjectNode scope = root.putObject("scope");
        putNullable(scope, "companyId", asText(draft.scope().companyId()));
        putNullable(scope, "plantId", asText(draft.scope().plantId()));
        putNullable(scope, "warehouseId", asText(draft.scope().warehouseId()));

        ObjectNode request = root.putObject("request");
        putNullable(request, "traceId", draft.request().traceId());
        putNullable(request, "clientIp", draft.request().clientIp());
        putNullable(request, "userAgent", draft.request().userAgent());
        putNullable(request, "httpMethod", draft.request().httpMethod());
        putNullable(request, "requestPath", draft.request().requestPath());

        ArrayNode entities = root.putArray("entities");
        for (AuditEntityRef ref : draft.entities()) {
            ObjectNode node = entities.addObject();
            node.put("relation", ref.relation().name());
            putNullable(node, "entityType", ref.entityType());
            putNullable(node, "entityId", ref.entityId());
            putNullable(node, "entityName", ref.entityName());
        }

        ArrayNode changes = root.putArray("changes");
        for (AuditFieldChange change : draft.changes()) {
            ObjectNode node = changes.addObject();
            putNullable(node, "fieldName", change.fieldName());
            putRawJson(node, "oldValue", change.oldValue());
            putRawJson(node, "newValue", change.newValue());
            node.put("changeType", change.changeType().name());
        }

        putRawJson(root, "metadata", draft.metadataJson());
        return root;
    }

    public AuditRecordDraft decode(String payload) {
        try {
            JsonNode root = objectMapper.readTree(payload);
            JsonNode actorNode = root.path("actor");
            JsonNode scopeNode = root.path("scope");
            JsonNode requestNode = root.path("request");

            List<AuditEntityRef> entities = new ArrayList<>();
            for (JsonNode node : root.path("entities")) {
                entities.add(new AuditEntityRef(
                        AuditEntityRef.AuditEntityRelation.valueOf(
                                node.path("relation").asText(AuditEntityRef.AuditEntityRelation.PRIMARY.name())),
                        text(node, "entityType"), text(node, "entityId"), text(node, "entityName")));
            }

            List<AuditFieldChange> changes = new ArrayList<>();
            for (JsonNode node : root.path("changes")) {
                changes.add(new AuditFieldChange(
                        text(node, "fieldName"), rawJson(node, "oldValue"), rawJson(node, "newValue"),
                        AuditLogChangeType.valueOf(
                                node.path("changeType").asText(AuditLogChangeType.UPDATE.name()))));
            }

            return new AuditRecordDraft(
                    UUID.fromString(root.path("eventId").asText()),
                    Instant.parse(root.path("occurredAt").asText()),
                    AuditAction.valueOf(root.path("action").asText()),
                    AuditOutcome.valueOf(root.path("outcome").asText(AuditOutcome.SUCCESS.name())),
                    new AuditActorSnapshot(
                            uuid(actorNode, "userId"), text(actorNode, "username"),
                            AuditActorSnapshot.AuditActorType.valueOf(actorNode.path("actorType")
                                    .asText(AuditActorSnapshot.AuditActorType.USER.name()))),
                    entities,
                    new AuditScope(uuid(scopeNode, "companyId"), uuid(scopeNode, "plantId"),
                            uuid(scopeNode, "warehouseId")),
                    AuditSource.valueOf(root.path("source").asText(AuditSource.SYSTEM.name())),
                    new AuditRequestSnapshot(text(requestNode, "traceId"), text(requestNode, "clientIp"),
                            text(requestNode, "userAgent"), text(requestNode, "httpMethod"),
                            text(requestNode, "requestPath")),
                    text(root, "reasonCode"), text(root, "description"), changes,
                    rawJson(root, "metadata"));
        } catch (Exception e) {
            throw new IllegalStateException("Unreadable audit outbox payload", e);
        }
    }

    /** SHA-256 over the canonical payload; the anchor for AR-7 tamper verification. */
    public String hash(ObjectNode payload) {
        return AuditInputSanitizer.sha256Hex(payload.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static String asText(UUID value) {
        return value == null ? null : value.toString();
    }

    private static void putNullable(ObjectNode node, String field, String value) {
        if (value == null) {
            node.putNull(field);
        } else {
            node.put(field, value);
        }
    }

    /** Field-change values are already JSON text; embedding them as strings would double-encode. */
    private void putRawJson(ObjectNode node, String field, String json) {
        if (json == null) {
            node.putNull(field);
            return;
        }
        try {
            node.set(field, objectMapper.readTree(json));
        } catch (Exception e) {
            node.put(field, json);
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? null : value.asText();
    }

    private static String rawJson(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull()) {
            return null;
        }
        // toString(), not asText(): AuditFieldChange holds JSON *documents*, so a text snapshot must
        // keep its quoting ("DRAFT") to stay a valid jsonb value on the way back out.
        return value.toString();
    }

    private static UUID uuid(JsonNode node, String field) {
        String value = text(node, field);
        return value == null ? null : UUID.fromString(value);
    }
}
