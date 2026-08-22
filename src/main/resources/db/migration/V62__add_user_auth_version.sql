ALTER TABLE users
    ADD COLUMN auth_version BIGINT NOT NULL DEFAULT 0;

COMMENT ON COLUMN users.auth_version IS
    'Incremented whenever all existing access and refresh sessions must be revoked';
