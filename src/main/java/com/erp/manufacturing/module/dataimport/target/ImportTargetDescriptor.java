package com.erp.manufacturing.module.dataimport.target;

import com.erp.manufacturing.module.dataimport.domain.ImportTargetType;

import java.util.List;
import java.util.Optional;

/**
 * The complete field contract for one import target.
 *
 * <p>Serves three readers at once, which is why it is a first-class object rather than a constant
 * inside a handler: the mapping screen ({@code GET /import-targets/{type}/fields}), the template
 * writer ({@code GET /import-targets/{type}/template}) and profile validation, which refuses a
 * mapping that points at a field this list does not contain.
 */
public record ImportTargetDescriptor(ImportTargetType type,
                                     String label,
                                     List<ImportFieldDescriptor> fields) {

    public ImportTargetDescriptor {
        fields = List.copyOf(fields);
    }

    public Optional<ImportFieldDescriptor> field(String name) {
        return fields.stream().filter(f -> f.name().equals(name)).findFirst();
    }

    public List<ImportFieldDescriptor> requiredFields() {
        return fields.stream().filter(ImportFieldDescriptor::required).toList();
    }
}
