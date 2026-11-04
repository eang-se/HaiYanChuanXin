ALTER TABLE members MODIFY bio VARCHAR(500) NOT NULL DEFAULT '';

-- Preserve both existing fields in the unified introduction, without duplicating identical text.
UPDATE members SET bio=CASE
 WHEN TRIM(signature)='' THEN bio
 WHEN TRIM(bio)='' THEN signature
 WHEN TRIM(bio)=TRIM(signature) OR LOCATE(TRIM(signature),bio)>0 THEN bio
 ELSE CONCAT(signature,'\n',bio) END;

UPDATE platform_metadata SET setting_value='海燕传信 - 海燕文学社综合活动平台' WHERE setting_key='site_name';
