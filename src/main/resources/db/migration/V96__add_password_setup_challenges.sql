-- First-login password setup and email-code password flows (FE_REQUEST_ACCOUNT_PASSWORD_FLOWS_2026-11-06).
--
-- users.status is a plain VARCHAR(20) with no CHECK constraint (V1), so the new PENDING_SETUP value
-- needs no constraint change; existing rows stay valid and no backfill is required.
COMMENT ON COLUMN users.status IS 'ACTIVE | INACTIVE | LOCKED | PENDING_SETUP';

-- One row per emailed code. The code itself is never stored: code_hash is an HMAC bound to the
-- challenge id, so a leaked row cannot be brute-forced offline without the server secret.
-- user_id is NULL for decoy rows, which exist only so an unknown RESET identifier behaves exactly
-- like a real one (account-enumeration prevention).
CREATE TABLE password_challenges (
    challenge_id UUID         PRIMARY KEY,
    user_id      UUID         REFERENCES users (user_id),
    purpose      VARCHAR(10)  NOT NULL,
    code_hash    VARCHAR(64)  NOT NULL,
    status       VARCHAR(12)  NOT NULL DEFAULT 'PENDING',
    attempts     INTEGER      NOT NULL DEFAULT 0,
    expires_at   TIMESTAMPTZ  NOT NULL,
    resend_at    TIMESTAMPTZ  NOT NULL,
    verified_at  TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT chk_password_challenges_purpose  CHECK (purpose IN ('SETUP', 'RESET')),
    CONSTRAINT chk_password_challenges_status   CHECK (status IN ('PENDING', 'VERIFIED', 'COMPLETED', 'SUPERSEDED')),
    CONSTRAINT chk_password_challenges_attempts CHECK (attempts >= 0)
);

CREATE INDEX idx_password_challenges_user_purpose ON password_challenges (user_id, purpose, status);
CREATE INDEX idx_password_challenges_expires_at   ON password_challenges (expires_at);

COMMENT ON TABLE password_challenges IS 'Emailed 6-digit codes for first-login setup and forgot-password; never stores the code';
