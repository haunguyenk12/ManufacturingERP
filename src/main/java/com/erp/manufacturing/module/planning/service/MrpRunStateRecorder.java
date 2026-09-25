package com.erp.manufacturing.module.planning.service;

import com.erp.manufacturing.common.exception.ExceptionFactory;
import com.erp.manufacturing.common.exception.ValidationErrorCode;
import com.erp.manufacturing.module.planning.domain.MrpRun;
import com.erp.manufacturing.module.planning.dto.MrpRunResponse;
import com.erp.manufacturing.module.planning.mapper.MrpPlanningMapper;
import com.erp.manufacturing.module.planning.repository.MrpRunRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Owns the two writes to {@code mrp_runs} that have to outlive a failing MRP calculation (EH-4).
 *
 * <p>Both methods run in a transaction of their own, for the same reason
 * {@link com.erp.manufacturing.module.workorder.service.WorkOrderBlockRecorder} does: a record whose
 * whole point is to say "this attempt failed" cannot live in the transaction that is failing. Before
 * EH-4 the run row was inserted and marked {@code FAILED} inside {@code MrpRunService.run}'s own
 * transaction, so a calculation that died on a database error left the session unusable — the
 * {@code FAILED} row went down with the rollback and the operator was left with a 500 and no trace of
 * the run at all.
 *
 * <p>🔴 The two methods are a pair and the order matters. {@link #recordFailure} loads the run in a
 * <em>new</em> transaction, so it can only see a row that is already committed — which is exactly what
 * {@link #start} guarantees. Flushing the insert inside the caller's transaction (what
 * {@code saveAndFlush} used to do here) is not enough: an uncommitted row is invisible to any other
 * transaction, this one included.
 *
 * <p>Committing the row up front also makes true a promise {@code MrpRunService.run} already
 * documented: a concurrent submission of the same {@code Idempotency-Key} now collides with
 * {@code uk_mrp_runs_idempotency_key} immediately, instead of blocking on the uncommitted index entry
 * until the first request finishes its whole calculation.
 *
 * <p>Accepted consequence: if the caller's transaction dies <em>after</em> a successful calculation,
 * the run stays {@code RUNNING} rather than disappearing. That is a visible, diagnosable state, which
 * is what this class is trading for.
 */
@Component
@RequiredArgsConstructor
public class MrpRunStateRecorder {

    private final MrpRunRepository mrpRunRepository;
    private final MrpPlanningMapper mapper;

    /**
     * Commits the run row in {@code RUNNING} before any calculation starts.
     *
     * @return the id of the committed run; the caller re-loads it into its own transaction
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public UUID start(MrpRun run) {
        run.start(Instant.now());
        return mrpRunRepository.save(run).getMrpRunId();
    }

    /**
     * Marks a committed run {@code FAILED}.
     *
     * <p>Returns the mapped response rather than the entity: the caller's persistence context may be
     * unusable by the time this is called, so the row is read back and mapped here, inside a healthy
     * transaction.
     *
     * @param message a message already vetted for a client to read — see
     *                {@code MrpRunService.clientSafeFailureMessage}
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public MrpRunResponse recordFailure(UUID mrpRunId, String message) {
        MrpRun run = mrpRunRepository.findById(mrpRunId)
                .orElseThrow(() -> ExceptionFactory.notFound(
                        ValidationErrorCode.RESOURCE_NOT_FOUND, "MRP run", mrpRunId));
        run.fail(Instant.now(), message);
        return mapper.toResponse(mrpRunRepository.save(run));
    }
}
