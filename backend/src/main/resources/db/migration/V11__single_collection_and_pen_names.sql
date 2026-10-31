ALTER TABLE works
 ADD collection_id BIGINT NULL,
 ADD collection_position INT NULL,
 ADD CONSTRAINT fk_work_collection FOREIGN KEY(collection_id) REFERENCES collections(id) ON DELETE SET NULL,
 ADD INDEX idx_work_collection(collection_id,collection_position);
UPDATE works w JOIN (
 SELECT wt.work_id,t.collection_id,wt.position,
 ROW_NUMBER() OVER(PARTITION BY wt.work_id ORDER BY t.collection_id,COALESCE(wt.position,2147483647)) AS rn
 FROM work_tags wt JOIN tags t ON t.id=wt.tag_id WHERE t.kind='COLLECTION'
) selected ON selected.work_id=w.id AND selected.rn=1
 SET w.collection_id=selected.collection_id,w.collection_position=selected.position;
DELETE wt FROM work_tags wt JOIN tags t ON t.id=wt.tag_id WHERE t.kind='COLLECTION';
DELETE FROM tags WHERE kind='COLLECTION';
CREATE TABLE pen_names (
 id BIGINT PRIMARY KEY AUTO_INCREMENT,
 name VARCHAR(120) NOT NULL UNIQUE,
 created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE member_pen_names (
 member_id BIGINT NOT NULL,pen_name_id BIGINT NOT NULL,
 PRIMARY KEY(member_id,pen_name_id),
 FOREIGN KEY(member_id) REFERENCES members(id),
 FOREIGN KEY(pen_name_id) REFERENCES pen_names(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
ALTER TABLE works ADD pen_name_id BIGINT NULL,
 ADD CONSTRAINT fk_work_pen_name FOREIGN KEY(pen_name_id) REFERENCES pen_names(id),
 ADD INDEX idx_works_pen_name(pen_name_id,status,published_at);
INSERT INTO pen_names(name)
 SELECT DISTINCT COALESCE(NULLIF(TRIM(w.byline),''),m.pen_name)
 FROM works w JOIN members m ON m.id=w.author_id
 WHERE COALESCE(NULLIF(TRIM(w.byline),''),m.pen_name) NOT IN ('','署名待核');
INSERT INTO member_pen_names(member_id,pen_name_id)
 SELECT DISTINCT w.author_id,p.id FROM works w JOIN members m ON m.id=w.author_id
 JOIN pen_names p ON p.name=COALESCE(NULLIF(TRIM(w.byline),''),m.pen_name);
UPDATE works w JOIN members m ON m.id=w.author_id
 JOIN pen_names p ON p.name=COALESCE(NULLIF(TRIM(w.byline),''),m.pen_name)
 SET w.pen_name_id=p.id;
