-- A tombstone is outside the registration alphabet, unique per retained member ID.
-- Keep the member row and author/audit foreign keys while freeing the old login name.
UPDATE members SET username=CONCAT('~deleted~',id) WHERE deleted_at IS NOT NULL;
