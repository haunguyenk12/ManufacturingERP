package com.erp.manufacturing.module.dataimport.target;

import com.erp.manufacturing.common.exception.ExceptionFactory;
import com.erp.manufacturing.common.exception.ValidationErrorCode;
import com.erp.manufacturing.module.dataimport.domain.ImportTargetType;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Finds the handler for a target type.
 *
 * <p>Built from whichever {@link ImportTargetHandler} beans exist, so a new slice is one new
 * {@code @Component} and nothing else. The constructor fails fast on a duplicate registration rather
 * than letting one handler quietly shadow another.
 */
@Component
public class ImportTargetRegistry {

    private final Map<ImportTargetType, ImportTargetHandler> handlers =
            new EnumMap<>(ImportTargetType.class);

    public ImportTargetRegistry(List<ImportTargetHandler> discovered) {
        for (ImportTargetHandler handler : discovered) {
            ImportTargetHandler previous = handlers.put(handler.targetType(), handler);
            if (previous != null) {
                throw new IllegalStateException("Two handlers registered for import target "
                        + handler.targetType() + ": " + previous.getClass().getName()
                        + " and " + handler.getClass().getName());
            }
        }
    }

    public ImportTargetHandler handlerFor(ImportTargetType targetType) {
        ImportTargetHandler handler = handlers.get(targetType);
        if (handler == null) {
            throw ExceptionFactory.notFound(ValidationErrorCode.RESOURCE_NOT_FOUND,
                    "Import target", targetType);
        }
        return handler;
    }

    public ImportTargetDescriptor descriptorFor(ImportTargetType targetType) {
        return handlerFor(targetType).descriptor();
    }

    /** Every target that can actually be imported, for the target-picker screen. */
    public List<ImportTargetDescriptor> descriptors() {
        return handlers.values().stream().map(ImportTargetHandler::descriptor).toList();
    }
}
