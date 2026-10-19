-- G8b: material issues — the supplemental flag and the requester's own cancellation.
--
--   * supplemental: the request was made as a supplemental issue (free stock, a reason per line, FE §1.4
--     step 5). FALSE for every existing document: they were all made through the normal path.
--   * cancellation_reason: why the requester withdrew a PENDING_APPROVAL request. Kept apart from
--     rejection_reason (a manager's decision) so neither has to be inferred from who decided.

ALTER TABLE material_issues
    ADD COLUMN supplemental        BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN cancellation_reason TEXT;
