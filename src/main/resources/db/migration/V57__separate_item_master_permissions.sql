-- V57__separate_item_master_permissions.sql
-- FE-4 5C: publish the Item Master permission contract separately from stock inventory.
--
-- Item is company-owned master data shared by the company's plants. PERM_INVENTORY_* remains for
-- stock, lots, movements, and item-warehouse settings. Copying grants from the old permissions
-- preserves access for system roles and any custom roles configured before this migration.

INSERT INTO permissions (code, resource, action, description)
VALUES
    ('PERM_ITEM_READ', 'ITEM', 'READ', 'Read company item master data'),
    ('PERM_ITEM_MANAGE', 'ITEM', 'MANAGE', 'Create, update, activate, and deactivate company item master data')
ON CONFLICT (code) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT rp.role_id, target.permission_id
FROM role_permissions rp
JOIN permissions source ON source.permission_id = rp.permission_id
JOIN (VALUES
    ('PERM_INVENTORY_READ', 'PERM_ITEM_READ'),
    ('PERM_INVENTORY_MANAGE', 'PERM_ITEM_MANAGE')
) AS mapping(source_code, target_code) ON mapping.source_code = source.code
JOIN permissions target ON target.code = mapping.target_code
ON CONFLICT DO NOTHING;

UPDATE permissions
SET description = 'Read stock balances, lots, movements, and item-warehouse settings'
WHERE code = 'PERM_INVENTORY_READ';

UPDATE permissions
SET description = 'Manage stock configuration and item-warehouse settings'
WHERE code = 'PERM_INVENTORY_MANAGE';
