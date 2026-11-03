ALTER TABLE works ADD COLUMN edit_revision BIGINT NOT NULL DEFAULT 0;
CREATE TABLE work_drafts (
 work_id BIGINT PRIMARY KEY,
 payload MEDIUMTEXT NOT NULL,
 revision BIGINT NOT NULL DEFAULT 1,
 updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
 FOREIGN KEY(work_id) REFERENCES works(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE work_draft_media (
 work_id BIGINT NOT NULL,
 media_id CHAR(36) NOT NULL,
 PRIMARY KEY(work_id,media_id),
 FOREIGN KEY(work_id) REFERENCES works(id) ON DELETE CASCADE,
 FOREIGN KEY(media_id) REFERENCES media_files(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE work_versions (
 id BIGINT NOT NULL PRIMARY KEY AUTO_INCREMENT,
 work_id BIGINT NOT NULL,
 title VARCHAR(100) NOT NULL,
 body MEDIUMTEXT NOT NULL,
 body_json MEDIUMTEXT NULL,
 created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 INDEX idx_work_versions(work_id,id),
 FOREIGN KEY(work_id) REFERENCES works(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE work_version_media (
 version_id BIGINT NOT NULL,
 media_id CHAR(36) NOT NULL,
 PRIMARY KEY(version_id,media_id),
 FOREIGN KEY(version_id) REFERENCES work_versions(id) ON DELETE CASCADE,
 FOREIGN KEY(media_id) REFERENCES media_files(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
-- Preserve the published baseline and its image references without changing existing works.
INSERT INTO work_versions(work_id,title,body,body_json,created_at)
 SELECT id,title,body,NULL,COALESCE(published_at,created_at) FROM works WHERE status='PUBLISHED';
INSERT INTO work_version_media(version_id,media_id)
 SELECT v.id,wm.media_id FROM work_versions v JOIN work_media wm ON wm.work_id=v.work_id;
