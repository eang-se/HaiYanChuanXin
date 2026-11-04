-- Retained articles can exist without a member until a public account republishes them.
ALTER TABLE works MODIFY author_id BIGINT NULL;
ALTER TABLE media_files MODIFY owner_id BIGINT NULL;
DELETE c FROM collection_works c JOIN works w ON w.id=c.work_id JOIN members m ON m.id=w.author_id WHERE m.deleted_at IS NOT NULL;
UPDATE media_files f JOIN members m ON m.id=f.owner_id SET f.owner_id=NULL
 WHERE m.deleted_at IS NOT NULL AND (EXISTS (SELECT 1 FROM work_media wm JOIN works w ON w.id=wm.work_id WHERE wm.media_id=f.id AND w.author_id=m.id) OR EXISTS (SELECT 1 FROM work_version_media vm JOIN work_versions v ON v.id=vm.version_id JOIN works w ON w.id=v.work_id WHERE vm.media_id=f.id AND w.author_id=m.id));
UPDATE works w JOIN members m ON m.id=w.author_id LEFT JOIN pen_names p ON p.id=w.pen_name_id
 SET w.byline=COALESCE(NULLIF(p.name,''),NULLIF(TRIM(w.byline),''),NULLIF(TRIM(m.pen_name),''),'佚名'),
 w.byline_mode=CASE WHEN COALESCE(NULLIF(p.name,''),NULLIF(TRIM(w.byline),''),NULLIF(TRIM(m.pen_name),''),'佚名')='佚名' THEN 'ANONYMOUS' ELSE 'TEMPORARY' END,
 w.pen_name_id=NULL,w.collection_id=NULL,w.collection_position=NULL,w.author_id=NULL,w.status='WITHDRAWN',w.edit_revision=w.edit_revision+1
 WHERE m.deleted_at IS NOT NULL;
DELETE b FROM member_pen_names b JOIN members m ON m.id=b.member_id WHERE m.deleted_at IS NOT NULL;
