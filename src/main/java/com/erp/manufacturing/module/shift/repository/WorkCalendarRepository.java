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
     * Fetches {@code weeklyShifts} and the {@code Shift} each row points at, deliberately leaving
     * BOTH {@code exceptions} and {@code shift.breaks} out of this graph. Hibernate cannot
     * join-fetch two bag ({@code List}) collections in one query — MultipleBagFetchException — and
     * this graph reaches three of them: {@code WorkCalendar.weeklyShifts},
     * {@code WorkCalendar.exceptions} and {@code Shift.breaks}. Only one may be join-fetched; the
     * other two lazy-load inside the same transaction, in
     * {@link com.erp.manufacturing.module.shift.service.WorkCalendarLookupService}.
     *
     * <p>🔴 {@code shift.breaks} used to be listed here and made every
     * {@code POST /work-orders/{id}/release} answer <b>500</b> as soon as the work centre's calendar
     * used a shift that had any break — that is, any realistic factory calendar. Nothing caught it
     * because the failure lives in the entity graph: unit tests mock this repository away, and no
     * existing {@code *IT} released a work order through a calendar whose shifts had breaks. The
     * guard is {@code WorkCalendarLookupRepositoryIT}. Depth is what makes it easy to miss — the
     * second bag is not on {@code WorkCalendar} at all, it is one association further out.
     *
     * <p>This is the same trap C2-4 hit combining {@code operations} with {@code componentLines}
     * (CLAUDE.md §0.27 hệ quả #1) and C2-7 hit combining {@code weeklyShifts} with
     * {@code exceptions} (§0.29 hệ quả #1). Before adding any path to this graph, check whether it
     * resolves to a {@code List} — following the association one more level counts.
     */
    @EntityGraph(attributePaths = {"weeklyShifts", "weeklyShifts.shift"})
    Optional<WorkCalendar> findWithWeeklyShiftsByWorkCalendarId(UUID workCalendarId);
}
