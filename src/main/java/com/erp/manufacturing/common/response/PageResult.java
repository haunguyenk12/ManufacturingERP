package com.erp.manufacturing.common.response;

import org.springframework.data.domain.Page;

import java.util.List;

/**
 * Paginated result wrapper – placed inside {@link ApiResponse#result()}.
 * <p>
 * Usage: {@code ApiResponse.ok(PageResult.from(page))}
 */
public record PageResult<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last
) {
    public static <T> PageResult<T> from(Page<T> page) {
        return new PageResult<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isFirst(),
                page.isLast()
        );
    }
}
