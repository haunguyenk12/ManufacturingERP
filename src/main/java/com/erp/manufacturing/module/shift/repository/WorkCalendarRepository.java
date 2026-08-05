package com.erp.manufacturing.module.shift.repository;

import com.erp.manufacturing.module.organization.domain.OrganizationStatus;
import com.erp.manufacturing.module.shift.domain.WorkCalendar;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface WorkCalendarRepository extends JpaRepository<WorkCalendar, UUID> {

    boolean existsByPlantPlantIdAndCode(UUID plantId, String code);

    @Query("""
            select c
            from WorkCalendar c
            where c.plant.plantId = :plantId
              and (:status is null or c.status = :status)
            """)
    Page<WorkCalendar> search(@Param("plantId") UUID plantId,
                              @Param("status") OrganizationStatus status,
                              Pageable pageable);

    /**
     * Fetches {@code weeklyShifts} (and each assigned shift's breaks) eagerly, deliberately leaving
     * {@code exceptions} out of this graph: both are {@code List} associations on {@code
     * WorkCalendar}, and Hibernate cannot join-fetch two bag collections in one query
     * (MultipleBagFetchException) — the exact trap C2-4 already hit combining {@code operations} and
     * {@code componentLines} (CLAUDE.md §0.27 hệ quả #1). {@code exceptions} is left to lazy-load
     * within the same transaction instead; it is a single aggregate's own collection, not a report
     * looped per row, so rule C14 does not apply.
     */
    @EntityGraph(attributePaths = {"weeklyShifts", "weeklyShifts.shift", "weeklyShifts.shift.breaks"})
    Optional<WorkCalendar> findWithWeeklyShiftsByWorkCalendarId(UUID workCalendarId);
}
