package com.erp.manufacturing.common.audit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceUnitUtil;
import jakarta.persistence.metamodel.Attribute;
import jakarta.persistence.metamodel.EntityType;
import jakarta.persistence.metamodel.SingularAttribute;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Field;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Captures a committed entity snapshot before an audited command and compares it with the command
 * response afterwards. Keeping this work here makes field-level auditing available to every
 * {@link Auditable} service without coupling business services to audit storage.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuditChangeCaptureService {

    private static final Set<String> TECHNICAL_FIELDS = Set.of(
            "createdAt", "updatedAt", "createdBy", "updatedBy", "version");
    private static final List<String> SENSITIVE_FRAGMENTS = List.of(
            "password", "token", "secret", "credential", "authorization", "hash");

    private final EntityManager entityManager;
    private final ObjectMapper objectMapper;

    /**
     * Uses an independent read transaction so the snapshot always represents the last committed
     * value, even when the audit aspect is invoked outside the business transaction interceptor.
     */
    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    public Map<String, JsonNode> captureBefore(String entityType, Object[] arguments,
                                               AuditAction action) {
        if (entityType == null || entityType.isBlank() || isCreate(action)) {
            return Map.of();
        }

        EntityType<?> jpaType = findEntityType(entityType);
        if (jpaType == null) {
            log.debug("[Audit] No JPA entity found for entityType={}", entityType);
            return Map.of();
        }

        for (Object candidateId : arguments) {
            if (!isPossibleId(candidateId, jpaType.getIdType().getJavaType())) {
                continue;
            }
            Object entity = entityManager.find(jpaType.getJavaType(), candidateId);
            if (entity != null) {
                return snapshotEntity(jpaType, entity);
            }
        }

        log.debug("[Audit] Could not resolve existing {} from method arguments", entityType);
        return Map.of();
    }

    public List<AuditFieldChange> calculateChanges(AuditAction action,
                                                   Map<String, JsonNode> before,
                                                   Object result) {
        Map<String, JsonNode> after = snapshotResult(result);
        AuditLogChangeType changeType = changeType(action);

        if (changeType == AuditLogChangeType.CREATE) {
            return after.entrySet().stream()
                    .filter(entry -> meaningful(entry.getValue()))
                    .map(entry -> new AuditFieldChange(
                            entry.getKey(), null, json(entry.getValue()), changeType))
                    .toList();
        }

        if (before == null || before.isEmpty()) {
            return List.of();
        }

        if (changeType == AuditLogChangeType.DELETE) {
            return before.entrySet().stream()
                    .filter(entry -> meaningful(entry.getValue()))
                    .map(entry -> new AuditFieldChange(
                            entry.getKey(), json(entry.getValue()), null, changeType))
                    .toList();
        }

        List<AuditFieldChange> changes = new ArrayList<>();
        before.keySet().stream()
                .filter(after::containsKey)
                .sorted()
                .forEach(fieldName -> {
                    JsonNode oldValue = before.get(fieldName);
                    JsonNode newValue = after.get(fieldName);
                    if (!Objects.equals(oldValue, newValue)) {
                        changes.add(new AuditFieldChange(
                                fieldName, json(oldValue), json(newValue), changeType));
                    }
                });
        return List.copyOf(changes);
    }

    /**
     * Resolves a stable display label from the response, falling back to the pre-command snapshot.
     * Entity-specific fields (for example {@code workOrderNo}) win over generic {@code name/code}.
     */
    public String resolveEntityName(String entityType, Map<String, JsonNode> before, Object result) {
        Map<String, JsonNode> after = snapshotResult(result);
        List<String> candidates = entityNameCandidates(entityType);

        for (String candidate : candidates) {
            String value = displayValue(after.get(candidate));
            if (value != null) {
                return value;
            }
        }
        if (before != null) {
            for (String candidate : candidates) {
                String value = displayValue(before.get(candidate));
                if (value != null) {
                    return value;
                }
            }
        }

        return after.entrySet().stream()
                .filter(entry -> isDisplayField(entry.getKey()))
                .map(Map.Entry::getValue)
                .map(this::displayValue)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    private List<String> entityNameCandidates(String entityType) {
        if (entityType == null || entityType.isBlank()) {
            return List.of("name", "code", "number", "title", "username", "label");
        }
        String stem = Character.toLowerCase(entityType.charAt(0)) + entityType.substring(1);
        String shortStem = stem
                .replaceFirst("(Header|Line|Transaction|Assignment|Setting)$", "");
        return List.of(
                stem + "Name", stem + "No", stem + "Code", stem + "Number",
                shortStem + "Name", shortStem + "No", shortStem + "Code", shortStem + "Number",
                "name", "code", "number", "title", "username", "label");
    }

    private boolean isDisplayField(String fieldName) {
        return fieldName.endsWith("Name") || fieldName.endsWith("No")
                || fieldName.endsWith("Code") || fieldName.endsWith("Number");
    }

    private String displayValue(JsonNode value) {
        if (value == null || value.isNull() || value.isContainerNode()) {
            return null;
        }
        String text = value.asText().trim();
        return text.isEmpty() ? null : text;
    }

    private EntityType<?> findEntityType(String requestedType) {
        return entityManager.getMetamodel().getEntities().stream()
                .filter(type -> type.getName().equalsIgnoreCase(requestedType)
                        || type.getJavaType().getSimpleName().equalsIgnoreCase(requestedType))
                .findFirst()
                .orElse(null);
    }

    private Map<String, JsonNode> snapshotEntity(EntityType<?> type, Object entity) {
        Map<String, JsonNode> values = new LinkedHashMap<>();
        PersistenceUnitUtil persistence = entityManager.getEntityManagerFactory().getPersistenceUnitUtil();

        type.getAttributes().stream()
                .filter(attribute -> attribute instanceof SingularAttribute<?, ?>)
                .sorted(Comparator.comparing(Attribute::getName))
                .forEach(attribute -> {
                    String fieldName = attribute.getName();
                    if (excluded(fieldName)) {
                        return;
                    }
                    try {
                        Object value = read(attribute.getJavaMember(), entity);
                        if (attribute.isAssociation()) {
                            if (value != null) {
                                values.put(fieldName + "Id",
                                        objectMapper.valueToTree(persistence.getIdentifier(value)));
                            }
                        } else {
                            values.put(fieldName, objectMapper.valueToTree(value));
                        }
                    } catch (Exception ex) {
                        log.debug("[Audit] Cannot snapshot {}.{}: {}",
                                type.getName(), fieldName, ex.getMessage());
                    }
                });
        return Map.copyOf(values);
    }

    private Map<String, JsonNode> snapshotResult(Object result) {
        if (result == null) {
            return Map.of();
        }
        JsonNode root = objectMapper.valueToTree(result);
        if (!root.isObject()) {
            return Map.of();
        }

        Map<String, JsonNode> values = new LinkedHashMap<>();
        root.fields().forEachRemaining(entry -> {
            if (!excluded(entry.getKey())) {
                values.put(entry.getKey(), entry.getValue());
            }
        });
        return Map.copyOf(values);
    }

    private Object read(Member member, Object target) throws ReflectiveOperationException {
        if (member instanceof Field field) {
            field.trySetAccessible();
            return field.get(target);
        }
        if (member instanceof Method method) {
            method.trySetAccessible();
            return method.invoke(target);
        }
        throw new IllegalArgumentException("Unsupported JPA member: " + member);
    }

    private boolean isPossibleId(Object value, Class<?> idType) {
        return value != null && idType.isAssignableFrom(value.getClass());
    }

    private boolean excluded(String fieldName) {
        if (TECHNICAL_FIELDS.contains(fieldName)) {
            return true;
        }
        String normalized = fieldName.toLowerCase(Locale.ROOT);
        return SENSITIVE_FRAGMENTS.stream().anyMatch(normalized::contains);
    }

    private boolean meaningful(JsonNode value) {
        return value != null && !value.isNull();
    }

    private String json(JsonNode value) {
        return value == null || value.isNull() ? null : value.toString();
    }

    private AuditLogChangeType changeType(AuditAction action) {
        if (isCreate(action)) {
            return AuditLogChangeType.CREATE;
        }
        if (action.name().endsWith("_DELETED")) {
            return AuditLogChangeType.DELETE;
        }
        return AuditLogChangeType.UPDATE;
    }

    private boolean isCreate(AuditAction action) {
        return action.name().endsWith("_CREATED");
    }
}
