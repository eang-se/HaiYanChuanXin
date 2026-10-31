ALTER TABLE works ADD notes VARCHAR(4000) NOT NULL DEFAULT '';
UPDATE works SET notes=source_note,source_note='' WHERE source_note<>'';
