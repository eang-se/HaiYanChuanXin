CREATE TABLE retired_avatar_media (media_id CHAR(36) PRIMARY KEY);
INSERT IGNORE INTO retired_avatar_media(media_id)
 SELECT f.id FROM media_files f JOIN members m ON m.avatar_url=CONCAT('/api/media/',f.id);
UPDATE members SET avatar_url='';
