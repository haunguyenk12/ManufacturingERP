-- V60__seed_data_import_permissions.sql
-- Permissions for the spreadsheet master-data importer.
--
-- ADMIN + MANAGER, no OPERATOR — same call as V51 (costing) rather than V49 (capacity). An import
-- writes master data in bulk: a single bad file can create thousands of items. That is a supervisory
-- act, not part of daily shop-floor execution, so it sits with the roles that already own master
-- data (PERM_ITEM_MANAGE is ADMIN+MANAGER too).
--
-- READ is separate from EXECUTE so a manager can be given visibility into what was imported without
-- being able to apply a file, and because listing runs/rows is the endpoint the frontend polls.

INSERT INTO permissions (code, resource, action, description)
VALUES
    ('PERM_DATA_IMPORT_READ', 'DATA_IMPORT', 'READ',
     'Read import runs, staged rows and mapping profiles'),
    ('PERM_DATA_IMPORT_EXECUTE', 'DATA_IMPORT', 'EXECUTE',
     'Upload a spreadsheet, validate it and apply it to master data')
ON CONFLICT (code) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.role_id, p.permission_id
FROM roles r
JOIN permissions p ON p.code IN ('PERM_DATA_IMPORT_READ', 'PERM_DATA_IMPORT_EXECUTE')
WHERE r.code IN ('ADMIN', 'MANAGER')
ON CONFLICT DO NOTHING;
