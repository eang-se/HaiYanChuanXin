-- Retain historical article authorship and audit foreign keys after account removal.
ALTER TABLE members ADD COLUMN deleted_at TIMESTAMP NULL DEFAULT NULL;
