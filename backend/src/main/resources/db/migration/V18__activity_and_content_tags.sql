ALTER TABLE tags ADD activity_active BOOLEAN NOT NULL DEFAULT TRUE;
-- Activities and author-written content tags may have the same visible name.
ALTER TABLE tags MODIFY topic_name VARCHAR(132) GENERATED ALWAYS AS
 (CASE WHEN kind='TOPIC' THEN CONCAT(CASE WHEN is_activity THEN 'A:' ELSE 'T:' END,name) ELSE NULL END) STORED;
