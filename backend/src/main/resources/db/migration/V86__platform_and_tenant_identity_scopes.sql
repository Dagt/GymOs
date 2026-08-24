ALTER TABLE person ADD COLUMN account_scope VARCHAR(16);
ALTER TABLE user_account ADD COLUMN account_scope VARCHAR(16);
ALTER TABLE role ADD COLUMN account_scope VARCHAR(16);
ALTER TABLE organization ADD COLUMN data_origin VARCHAR(16) NOT NULL DEFAULT 'PRODUCTIVE';

UPDATE organization
SET data_origin = 'DEMO'
WHERE id = '11111111-1111-1111-1111-111111111111'
  AND tax_id = 'GYMOS-DEMO';

ALTER TABLE organization
    ADD CONSTRAINT ck_organization_data_origin CHECK (data_origin IN ('PRODUCTIVE', 'DEMO'));

UPDATE role r
SET account_scope = CASE
    WHEN EXISTS (
        SELECT 1
        FROM role_permission rp
        JOIN permission p ON p.id = rp.permission_id
        WHERE rp.role_id = r.id
          AND p.code = 'platform.admin'
    ) THEN 'PLATFORM'
    ELSE 'TENANT'
END;

UPDATE user_account ua
SET account_scope = CASE
    WHEN EXISTS (
        SELECT 1
        FROM user_role ur
        JOIN role r ON r.id = ur.role_id
        WHERE ur.user_id = ua.id
          AND r.account_scope = 'PLATFORM'
    ) THEN 'PLATFORM'
    ELSE 'TENANT'
END;

-- The historical local seed assigned the known demo account both the platform
-- role and a tenant-owner role. It is safe to detach only this exact fixture.
DELETE FROM user_role ur
USING user_account ua, role r
WHERE ur.user_id = ua.id
  AND ur.role_id = r.id
  AND ua.id = '44444444-4444-4444-4444-444444444444'
  AND ua.organization_id = '11111111-1111-1111-1111-111111111111'
  AND LOWER(ua.username) = 'admin@gymos.local'
  AND ua.account_scope = 'PLATFORM'
  AND r.account_scope = 'TENANT';

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM user_account ua
        JOIN user_role ur ON ur.user_id = ua.id
        JOIN role r ON r.id = ur.role_id
        GROUP BY ua.id
        HAVING COUNT(DISTINCT r.account_scope) > 1
    ) THEN
        RAISE EXCEPTION USING
            MESSAGE = 'V86 cannot classify users assigned to both PLATFORM and TENANT roles',
            HINT = 'Remove the incompatible role assignment after reviewing the affected accounts, then retry the migration.';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM user_account platform_user
        JOIN user_account tenant_user
          ON tenant_user.person_id = platform_user.person_id
         AND tenant_user.id <> platform_user.id
        WHERE platform_user.account_scope = 'PLATFORM'
          AND tenant_user.account_scope = 'TENANT'
    ) OR EXISTS (
        SELECT 1
        FROM user_account platform_user
        JOIN client c ON c.person_id = platform_user.person_id
        WHERE platform_user.account_scope = 'PLATFORM'
          AND c.deleted_at IS NULL
    ) THEN
        RAISE EXCEPTION USING
            MESSAGE = 'V86 cannot detach a platform identity whose person is also used by a tenant',
            HINT = 'Create a dedicated platform person and relink the platform account before retrying the migration.';
    END IF;

    IF EXISTS (
        SELECT LOWER(username)
        FROM user_account
        WHERE deleted_at IS NULL
        GROUP BY LOWER(username)
        HAVING COUNT(*) > 1
    ) THEN
        RAISE EXCEPTION USING
            MESSAGE = 'V86 found duplicate login usernames across tenants',
            HINT = 'Rename duplicate user_account.username values before retrying; login identifiers must be globally unique.';
    END IF;
END $$;

UPDATE person p
SET account_scope = CASE
    WHEN EXISTS (
        SELECT 1
        FROM user_account ua
        WHERE ua.person_id = p.id
          AND ua.account_scope = 'PLATFORM'
    ) THEN 'PLATFORM'
    ELSE 'TENANT'
END;

DELETE FROM user_branch_access uba
USING user_account ua
WHERE uba.user_id = ua.id
  AND ua.account_scope = 'PLATFORM';

UPDATE user_account
SET organization_id = NULL
WHERE account_scope = 'PLATFORM';

UPDATE person
SET organization_id = NULL
WHERE account_scope = 'PLATFORM';

DELETE FROM role_permission rp
USING role r, permission p
WHERE rp.role_id = r.id
  AND rp.permission_id = p.id
  AND r.account_scope = 'TENANT'
  AND p.code LIKE 'platform.%';

ALTER TABLE person ALTER COLUMN organization_id DROP NOT NULL;
ALTER TABLE user_account ALTER COLUMN organization_id DROP NOT NULL;
ALTER TABLE person ALTER COLUMN account_scope SET DEFAULT 'TENANT';
ALTER TABLE user_account ALTER COLUMN account_scope SET DEFAULT 'TENANT';
ALTER TABLE role ALTER COLUMN account_scope SET DEFAULT 'TENANT';
ALTER TABLE person ALTER COLUMN account_scope SET NOT NULL;
ALTER TABLE user_account ALTER COLUMN account_scope SET NOT NULL;
ALTER TABLE role ALTER COLUMN account_scope SET NOT NULL;

ALTER TABLE person
    ADD CONSTRAINT ck_person_account_scope CHECK (account_scope IN ('PLATFORM', 'TENANT')),
    ADD CONSTRAINT ck_person_account_scope_organization CHECK (
        (account_scope = 'PLATFORM' AND organization_id IS NULL)
        OR (account_scope = 'TENANT' AND organization_id IS NOT NULL)
    );

ALTER TABLE user_account
    ADD CONSTRAINT ck_user_account_scope CHECK (account_scope IN ('PLATFORM', 'TENANT')),
    ADD CONSTRAINT ck_user_account_scope_organization CHECK (
        (account_scope = 'PLATFORM' AND organization_id IS NULL)
        OR (account_scope = 'TENANT' AND organization_id IS NOT NULL)
    );

ALTER TABLE role
    ADD CONSTRAINT ck_role_account_scope CHECK (account_scope IN ('PLATFORM', 'TENANT')),
    ADD CONSTRAINT ck_role_platform_organization CHECK (
        account_scope <> 'PLATFORM' OR organization_id IS NULL
    );

DROP INDEX ux_user_org_username;
CREATE UNIQUE INDEX ux_user_username_active
    ON user_account (LOWER(username))
    WHERE deleted_at IS NULL;
CREATE INDEX ix_user_account_scope_status
    ON user_account (account_scope, status)
    WHERE deleted_at IS NULL;

CREATE OR REPLACE FUNCTION enforce_user_account_identity_scope()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
    person_scope VARCHAR(16);
    person_organization UUID;
BEGIN
    SELECT account_scope, organization_id
      INTO person_scope, person_organization
      FROM person
     WHERE id = NEW.person_id;

    IF person_scope IS DISTINCT FROM NEW.account_scope THEN
        RAISE EXCEPTION 'User account scope % does not match person scope %', NEW.account_scope, person_scope;
    END IF;
    IF NEW.account_scope = 'TENANT' AND person_organization IS DISTINCT FROM NEW.organization_id THEN
        RAISE EXCEPTION 'Tenant user and person must belong to the same organization';
    END IF;
    IF NEW.account_scope = 'PLATFORM' AND person_organization IS NOT NULL THEN
        RAISE EXCEPTION 'Platform person cannot belong to an organization';
    END IF;
    IF TG_OP = 'UPDATE' AND NEW.account_scope IS DISTINCT FROM OLD.account_scope
       AND EXISTS (
           SELECT 1
           FROM user_role ur
           JOIN role r ON r.id = ur.role_id
           WHERE ur.user_id = NEW.id
             AND r.account_scope <> NEW.account_scope
       ) THEN
        RAISE EXCEPTION 'User scope change conflicts with assigned roles';
    END IF;
    RETURN NEW;
END $$;

CREATE TRIGGER trg_user_account_identity_scope
BEFORE INSERT OR UPDATE OF account_scope, organization_id, person_id
ON user_account
FOR EACH ROW EXECUTE FUNCTION enforce_user_account_identity_scope();

CREATE OR REPLACE FUNCTION enforce_user_role_identity_scope()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
    user_scope VARCHAR(16);
    assigned_role_scope VARCHAR(16);
BEGIN
    SELECT account_scope INTO user_scope FROM user_account WHERE id = NEW.user_id;
    SELECT account_scope INTO assigned_role_scope FROM role WHERE id = NEW.role_id;
    IF user_scope IS DISTINCT FROM assigned_role_scope THEN
        RAISE EXCEPTION 'Cannot assign a % role to a % account', assigned_role_scope, user_scope;
    END IF;
    RETURN NEW;
END $$;

CREATE TRIGGER trg_user_role_identity_scope
BEFORE INSERT OR UPDATE OF user_id, role_id
ON user_role
FOR EACH ROW EXECUTE FUNCTION enforce_user_role_identity_scope();

CREATE OR REPLACE FUNCTION enforce_role_identity_scope_change()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    IF NEW.account_scope IS DISTINCT FROM OLD.account_scope
       AND EXISTS (
           SELECT 1
           FROM user_role ur
           JOIN user_account ua ON ua.id = ur.user_id
           WHERE ur.role_id = NEW.id
             AND ua.account_scope <> NEW.account_scope
       ) THEN
        RAISE EXCEPTION 'Role scope change conflicts with assigned users';
    END IF;
    RETURN NEW;
END $$;

CREATE TRIGGER trg_role_identity_scope_change
BEFORE UPDATE OF account_scope
ON role
FOR EACH ROW EXECUTE FUNCTION enforce_role_identity_scope_change();

CREATE OR REPLACE FUNCTION enforce_role_permission_identity_scope()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
    assigned_role_scope VARCHAR(16);
    permission_code VARCHAR(120);
BEGIN
    SELECT account_scope INTO assigned_role_scope FROM role WHERE id = NEW.role_id;
    SELECT code INTO permission_code FROM permission WHERE id = NEW.permission_id;
    IF permission_code LIKE 'platform.%' AND assigned_role_scope <> 'PLATFORM' THEN
        RAISE EXCEPTION 'Platform permissions can only be assigned to PLATFORM roles';
    END IF;
    RETURN NEW;
END $$;

CREATE TRIGGER trg_role_permission_identity_scope
BEFORE INSERT OR UPDATE OF role_id, permission_id
ON role_permission
FOR EACH ROW EXECUTE FUNCTION enforce_role_permission_identity_scope();

CREATE OR REPLACE FUNCTION enforce_user_branch_tenant_scope()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
    user_scope VARCHAR(16);
    user_organization UUID;
    branch_organization UUID;
BEGIN
    SELECT account_scope, organization_id
      INTO user_scope, user_organization
      FROM user_account
     WHERE id = NEW.user_id;
    SELECT organization_id
      INTO branch_organization
      FROM branch
     WHERE id = NEW.branch_id;
    IF user_scope <> 'TENANT' THEN
        RAISE EXCEPTION 'Platform accounts cannot receive persistent tenant branch access';
    END IF;
    IF user_organization IS DISTINCT FROM branch_organization THEN
        RAISE EXCEPTION 'Branch access must stay inside the user tenant';
    END IF;
    RETURN NEW;
END $$;

CREATE TRIGGER trg_user_branch_tenant_scope
BEFORE INSERT OR UPDATE OF user_id, branch_id
ON user_branch_access
FOR EACH ROW EXECUTE FUNCTION enforce_user_branch_tenant_scope();
