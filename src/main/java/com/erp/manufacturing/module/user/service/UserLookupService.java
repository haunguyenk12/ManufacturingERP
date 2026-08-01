package com.erp.manufacturing.module.user.service;

import com.erp.manufacturing.module.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Entry point for other modules that need to render a user id as a name (rule C7) — documents store
 * {@code created_by}/{@code approved_by} as UUIDs, but the frontend shows usernames (spec §6.4).
 * <p>
 * Batch-oriented on purpose: a page of documents must resolve its authors in one query, not one per
 * row (rule C14). No {@code @PreAuthorize} — a username attached to a document the caller is already
 * authorized to read carries no additional privilege, the same rationale as
 * {@code RoutingLookupService}.
 */
@Service
@RequiredArgsConstructor
public class UserLookupService {

    private final UserRepository userRepository;

    /** Ids with no matching user are simply absent from the map — callers render null. */
    @Transactional(readOnly = true)
    public Map<UUID, String> findUsernames(Collection<UUID> userIds) {
        Set<UUID> ids = userIds.stream()
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return Map.of();
        }
        return userRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(user -> user.getUserId(), user -> user.getUsername()));
    }
}
