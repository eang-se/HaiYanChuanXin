ALTER TABLE members
 ADD avatar_url VARCHAR(100) NOT NULL DEFAULT '',
 ADD signature VARCHAR(120) NOT NULL DEFAULT '',
 ADD qq_number VARCHAR(12) NOT NULL DEFAULT '',
 ADD profile_completed BOOLEAN NOT NULL DEFAULT TRUE,
 ADD profile_prompt_seen BOOLEAN NOT NULL DEFAULT TRUE;
CREATE TABLE member_permissions (
 member_id BIGINT NOT NULL, permission_code VARCHAR(30) NOT NULL, allowed BOOLEAN NOT NULL,
 PRIMARY KEY(member_id,permission_code), FOREIGN KEY(member_id) REFERENCES members(id)
);
CREATE TABLE qq_allowlist (
 qq_number VARCHAR(12) PRIMARY KEY, active BOOLEAN NOT NULL DEFAULT TRUE,
 created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE registration_codes (
 qq_number VARCHAR(12) PRIMARY KEY, code_hash CHAR(64) NOT NULL,
 expires_at TIMESTAMP NOT NULL, sent_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 attempts INT NOT NULL DEFAULT 0, consumed BOOLEAN NOT NULL DEFAULT FALSE
);
INSERT INTO platform_metadata(setting_key,setting_value) VALUES
 ('registration_enabled','true'),('registration_mode','test');
ALTER TABLE collections ADD auto_created BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE tags DROP INDEX name, MODIFY name VARCHAR(130) NOT NULL,
 ADD kind VARCHAR(16) NOT NULL DEFAULT 'TOPIC',
 ADD owner_id BIGINT NULL, ADD collection_id BIGINT NULL,
 ADD topic_name VARCHAR(130) GENERATED ALWAYS AS (CASE WHEN kind='TOPIC' THEN name ELSE NULL END) STORED,
 ADD UNIQUE KEY unique_topic_name(topic_name), ADD UNIQUE KEY unique_collection_tag(collection_id),
 ADD FOREIGN KEY(owner_id) REFERENCES members(id), ADD FOREIGN KEY(collection_id) REFERENCES collections(id);
ALTER TABLE work_tags ADD position INT NULL;
INSERT INTO tags(name,description,kind,owner_id,collection_id)
 SELECT CONCAT('合集：',title),'','COLLECTION',author_id,id FROM collections;
INSERT INTO work_tags(work_id,tag_id,position)
 SELECT cw.work_id,t.id,cw.position FROM collection_works cw JOIN tags t ON t.collection_id=cw.collection_id;
