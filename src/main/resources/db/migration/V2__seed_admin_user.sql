-- V2__seed_admin_user.sql
-- Default admin user – password: Admin@123
-- BCrypt hash for 'Admin@123' with cost factor 12

DO $$
DECLARE
    v_admin_id UUID;
    v_role_id  UUID;
BEGIN
    -- Insert admin user (idempotent)
    INSERT INTO users (user_id, username, email, password, status)
    VALUES (
        gen_random_uuid(),
        'admin',
        'admin@erp.local',
        '$2a$12$vqPrFbZJ4RqbTKR2e8IQ2.Ld6E5J5.oMFDsrlJITXOxVAd/fCYmkS',
        'ACTIVE'
    )
    ON CONFLICT (username) DO NOTHING
    RETURNING user_id INTO v_admin_id;

    IF v_admin_id IS NOT NULL THEN
        SELECT role_id INTO v_role_id FROM roles WHERE name = 'ADMIN';
        INSERT INTO user_roles (user_id, role_id) VALUES (v_admin_id, v_role_id)
        ON CONFLICT DO NOTHING;
    END IF;
END $$;
