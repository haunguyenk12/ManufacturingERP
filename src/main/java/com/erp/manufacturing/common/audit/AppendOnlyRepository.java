package com.erp.manufacturing.common.audit;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.NoRepositoryBean;
import org.springframework.data.repository.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository base for the immutable audit tables: it can append and it can read, and there is no
 * method on it that updates or deletes (AR-7).
 *
 * <p>The audit repositories used to extend {@code JpaRepository}, which hands every caller
 * {@code delete}, {@code deleteAll}, {@code deleteAllInBatch} and a {@code save} that will happily
 * issue an UPDATE. Those methods were never called, but "nobody calls it" is not a control —
 * it is one autocomplete away from being false, in a package whose entire purpose is to be
 * trustworthy. Narrowing the type removes the possibility instead of relying on discipline.
 *
 * <p>This is the application-side half of a pair. The database-side half is the trigger installed by
 * {@code V68}, which rejects the statement even if it is somehow issued. Either alone is a
 * convention; together they are an invariant.
 *
 * <p>{@code saveAndFlush} is retained deliberately: {@code AuditLogMaterializer} needs the generated
 * id immediately, to attach child rows within the same transaction.
 */
@NoRepositoryBean
public interface AppendOnlyRepository<T, ID> extends Repository<T, ID> {

    T saveAndFlush(T entity);

    <S extends T> List<S> saveAll(Iterable<S> entities);

    Optional<T> findById(ID id);

    boolean existsById(ID id);

    Page<T> findAll(Pageable pageable);

    long count();
}
