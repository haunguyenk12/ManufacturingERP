package com.erp.manufacturing.module.bom.service.audit;

import com.erp.manufacturing.common.audit.AuditFieldChange;
import com.erp.manufacturing.common.audit.AuditLogChangeType;
import com.erp.manufacturing.common.audit.model.AuditRecordDraft;
import com.erp.manufacturing.common.audit.spi.AuditChangeProvider;
import com.erp.manufacturing.common.audit.spi.AuditDescriptorProvider;
import com.erp.manufacturing.common.audit.spi.AuditInvocation;
import com.erp.manufacturing.module.bom.domain.BomLine;
import com.erp.manufacturing.module.bom.dto.BomLineResponse;
import com.erp.manufacturing.module.bom.dto.BomResponse;
import com.erp.manufacturing.module.bom.repository.BomLineRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Correct identity and correct diff for the BOM-line commands (AR-4/AR-5).
 *
 * <p><strong>Two defects this replaces.</strong>
 *
 * <ol>
 *   <li>{@code updateLine(UUID lineId, …)} was annotated {@code entityType = "BomLine"} with
 *       {@code entityIdExpression = "bomId.toString()"} evaluated against the returned
 *       {@code BomResponse}. The row therefore claimed to be about a line while carrying the
 *       <em>header's</em> id — an identifier that resolves to a real but different row, which is
 *       worse than a null because it points an investigator confidently at the wrong object.
 *       {@code deleteLine(UUID lineId)} returns {@code void} and recorded no id at all.</li>
 *   <li>The automatic differ compares a {@code BomHeader} snapshot with a {@code BomResponse}. A
 *       header's own scalar fields do not change when a line is added, edited or removed, so every
 *       line command produced <em>zero</em> field changes. "No changes recorded" reads as evidence
 *       that nothing happened, which is the opposite of the truth.</li>
 * </ol>
 *
 * <p>The line collection is snapshotted before the command and diffed by {@code lineId} afterwards,
 * so an added line, a removed line and an edited quantity are each reported as themselves instead of
 * as a wholesale replacement of the list.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class BomLineAuditProvider implements AuditDescriptorProvider, AuditChangeProvider {

    private static final Set<String> LINE_METHODS = Set.of("addLine", "updateLine", "deleteLine");

    private final BomLineRepository bomLineRepository;
    private final ObjectMapper objectMapper;

    /**
     * Required override: both SPIs declare a default {@code order()}, and Java refuses to inherit two
     * unrelated defaults with the same signature. One class implementing both is deliberate here —
     * naming the line and diffing the line are the same piece of knowledge.
     */
    @Override
    public int order() {
        return 50;
    }

    @Override
    public boolean supports(AuditInvocation invocation) {
        return LINE_METHODS.contains(invocation.method().getName())
                && "BomService".equals(invocation.method().getDeclaringClass().getSimpleName());
    }

    // ── Pre-state ────────────────────────────────────────────────────────

    /**
     * Snapshots the sibling lines as plain values, not entities. Holding entity references would be
     * useless: the command is about to mutate those very instances inside the same persistence
     * context, and the "before" image would change under us into the "after" image.
     */
    @Override
    public Object capturePreState(AuditInvocation invocation) {
        try {
            UUID bomId = resolveBomId(invocation);
            if (bomId == null) {
                return null;
            }
            Map<UUID, Map<String, Object>> snapshot = new LinkedHashMap<>();
            for (BomLine line : bomLineRepository.findByBomBomIdOrderByLineNoAsc(bomId)) {
                snapshot.put(line.getLineId(), fieldsOf(
                        line.getLineNo(),
                        line.getComponentItem() == null ? null : line.getComponentItem().getCode(),
                        line.getQuantityPer(),
                        line.getScrapRate()));
            }
            return snapshot;
        } catch (Exception e) {
            log.debug("[Audit] Could not snapshot BOM lines before {}: {}",
                    invocation.method().getName(), e.getMessage());
            return null;
        }
    }

    // ── Identity ─────────────────────────────────────────────────────────

    @Override
    public void describe(AuditInvocation invocation, AuditRecordDraft.Builder builder) {
        UUID lineId = uuidArgument(invocation, "lineId");
        UUID bomId = uuidArgument(invocation, "bomId");

        if (invocation.result() instanceof BomResponse response) {
            bomId = response.bomId();
            if (lineId == null) {
                lineId = addedLineId(invocation, response);
            }
            builder.primaryEntity("BomHeader", bomId, response.revision());
            builder.relatedEntity("Item", response.parentItemId(), response.parentItemCode());
        } else if (bomId == null && lineId != null) {
            bomId = bomLineRepository.findById(lineId)
                    .map(line -> line.getBom().getBomId())
                    .orElse(null);
            if (bomId != null) {
                builder.primaryEntity("BomHeader", bomId, null);
            }
        }

        // The header is the primary target because it is the aggregate a reader looks up; the specific
        // line is a related target. Recording only one of the two is what made these events unusable.
        if (lineId != null) {
            builder.relatedEntity("BomLine", lineId, lineLabel(invocation, lineId));
        }
    }

    // ── Diff ─────────────────────────────────────────────────────────────

    @Override
    @SuppressWarnings("unchecked")
    public List<AuditFieldChange> capture(AuditInvocation invocation) {
        Map<UUID, Map<String, Object>> before = invocation.preState(Map.class)
                .map(map -> (Map<UUID, Map<String, Object>>) map)
                .orElseGet(LinkedHashMap::new);

        Map<UUID, Map<String, Object>> after = new LinkedHashMap<>();
        if (invocation.result() instanceof BomResponse response) {
            for (BomLineResponse line : response.lines()) {
                after.put(line.lineId(), fieldsOf(line.lineNo(), line.componentItemCode(),
                        line.quantityPer(), line.scrapRate()));
            }
        } else {
            // deleteLine returns void, so the surviving lines are whatever the snapshot had minus the
            // one that was removed. Deriving it this way keeps the DELETE explicit rather than
            // emitting an empty diff that would read as "nothing changed".
            UUID removed = uuidArgument(invocation, "lineId");
            before.forEach((lineId, fields) -> {
                if (!lineId.equals(removed)) {
                    after.put(lineId, fields);
                }
            });
        }

        List<AuditFieldChange> changes = new ArrayList<>();
        after.forEach((lineId, fields) -> {
            Map<String, Object> previous = before.get(lineId);
            if (previous == null) {
                changes.add(change(lineId, null, fields, AuditLogChangeType.CREATE));
            } else if (!Objects.equals(previous, fields)) {
                changes.add(change(lineId, previous, fields, AuditLogChangeType.UPDATE));
            }
        });
        before.forEach((lineId, fields) -> {
            if (!after.containsKey(lineId)) {
                changes.add(change(lineId, fields, null, AuditLogChangeType.DELETE));
            }
        });
        return List.copyOf(changes);
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private UUID resolveBomId(AuditInvocation invocation) {
        UUID bomId = uuidArgument(invocation, "bomId");
        if (bomId != null) {
            return bomId;
        }
        UUID lineId = uuidArgument(invocation, "lineId");
        return lineId == null ? null : bomLineRepository.findById(lineId)
                .map(line -> line.getBom().getBomId())
                .orElse(null);
    }

    @SuppressWarnings("unchecked")
    private UUID addedLineId(AuditInvocation invocation, BomResponse response) {
        Optional<Map<UUID, Map<String, Object>>> before = invocation.preState(Map.class)
                .map(map -> (Map<UUID, Map<String, Object>>) map);
        return response.lines().stream()
                .map(BomLineResponse::lineId)
                .filter(lineId -> before.map(map -> !map.containsKey(lineId)).orElse(false))
                .findFirst()
                .orElse(null);
    }

    private String lineLabel(AuditInvocation invocation, UUID lineId) {
        if (invocation.result() instanceof BomResponse response) {
            Optional<String> fromResponse = response.lines().stream()
                    .filter(line -> lineId.equals(line.lineId()))
                    .map(BomLineResponse::componentItemCode)
                    .findFirst();
            if (fromResponse.isPresent()) {
                return fromResponse.get();
            }
        }
        return invocation.preState(Map.class)
                .map(map -> map.get(lineId))
                .filter(Map.class::isInstance)
                .map(fields -> ((Map<?, ?>) fields).get("componentItemCode"))
                .map(Object::toString)
                .orElse(null);
    }

    private Map<String, Object> fieldsOf(Integer lineNo, String componentItemCode,
                                         BigDecimal quantityPer, BigDecimal scrapRate) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("lineNo", lineNo);
        fields.put("componentItemCode", componentItemCode);
        // Plain string, not BigDecimal: 2.0 and 2.00 are unequal as BigDecimal, so comparing the
        // objects would report a change every time the scale moved without the value moving.
        fields.put("quantityPer", quantityPer == null ? null : quantityPer.stripTrailingZeros().toPlainString());
        fields.put("scrapRate", scrapRate == null ? null : scrapRate.stripTrailingZeros().toPlainString());
        return fields;
    }

    private AuditFieldChange change(UUID lineId, Map<String, Object> before,
                                    Map<String, Object> after, AuditLogChangeType type) {
        return new AuditFieldChange("lines[" + lineId + "]", json(before), json(after), type);
    }

    private UUID uuidArgument(AuditInvocation invocation, String name) {
        return invocation.argument(name)
                .filter(UUID.class::isInstance)
                .map(UUID.class::cast)
                .orElse(null);
    }

    private String json(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            log.debug("[Audit] Could not serialise BOM line snapshot: {}", e.getMessage());
            return null;
        }
    }
}
