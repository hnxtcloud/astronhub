-- No default or historical backfill: a generic EXE does not establish NSIS identity.
ALTER TABLE software_release_asset ADD COLUMN confirmed_installer_family VARCHAR(20);
ALTER TABLE software_release_asset ADD COLUMN installer_family_confirmed_by VARCHAR(128);
ALTER TABLE software_release_asset ADD COLUMN installer_family_confirmed_at TIMESTAMPTZ;
ALTER TABLE software_release_asset ADD CONSTRAINT ck_software_installer_family_confirmation CHECK (
    (confirmed_installer_family IS NULL AND installer_family_confirmed_by IS NULL AND installer_family_confirmed_at IS NULL)
    OR (confirmed_installer_family IS NOT NULL AND confirmed_installer_family = 'NSIS'
        AND role = 'INSTALLER' AND package_revision_id IS NOT NULL
        AND installer_family_confirmed_by IS NOT NULL AND length(trim(installer_family_confirmed_by)) > 0
        AND installer_family_confirmed_at IS NOT NULL)
);
