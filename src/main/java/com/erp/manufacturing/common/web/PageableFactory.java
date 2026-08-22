package com.erp.manufacturing.common.web;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/**
 * Builds a {@link Pageable} from raw request parameters with defensive bounds.
 * Centralizes pagination construction that was previously duplicated across
 * every list controller, and enforces the {@code size <= 100} API convention.
 */
public final class PageableFactory {

    public static final int DEFAULT_PAGE_SIZE = 20;
    public static final int MAX_PAGE_SIZE = 100;

    private PageableFactory() {
    }

    public static Pageable of(int page, int size, String sortBy, String sortDir) {
        int safePage = Math.max(page, 0);
        int safeSize = size < 1 ? DEFAULT_PAGE_SIZE : Math.min(size, MAX_PAGE_SIZE);
        Sort sort = sortDir.equalsIgnoreCase("desc")
                ? Sort.by(sortBy).descending()
                : Sort.by(sortBy).ascending();
        return PageRequest.of(safePage, safeSize, sort);
    }

    public static Pageable of(int page, int size, String sortBy, String sortDir, String tieBreaker) {
        int safePage = Math.max(page, 0);
        int safeSize = size < 1 ? DEFAULT_PAGE_SIZE : Math.min(size, MAX_PAGE_SIZE);
        Sort.Direction direction = sortDir.equalsIgnoreCase("desc")
                ? Sort.Direction.DESC : Sort.Direction.ASC;
        Sort sort = Sort.by(direction, sortBy);
        if (!sortBy.equals(tieBreaker)) {
            sort = sort.and(Sort.by(direction, tieBreaker));
        }
        return PageRequest.of(safePage, safeSize, sort);
    }
}
