-- Imported authors remain credited on their articles without binding their names
-- to the editorial publishing account. Invalidate open editors before changing
-- either the published credit or its private draft; author_id stays unchanged.
UPDATE works w JOIN members m ON m.id=w.author_id
 LEFT JOIN pen_names p ON p.id=w.pen_name_id
 LEFT JOIN work_drafts d ON d.work_id=w.id
 LEFT JOIN pen_names dp ON dp.id=CAST(NULLIF(JSON_UNQUOTE(JSON_EXTRACT(d.payload,'$.penNameId')),'null') AS UNSIGNED)
 SET w.edit_revision=w.edit_revision+1,w.updated_at=w.updated_at
 WHERE (m.username='haiyan_editor' AND (w.pen_name_id IS NOT NULL OR TRIM(w.byline)<>''
   OR COALESCE(NULLIF(TRIM(JSON_UNQUOTE(JSON_EXTRACT(d.payload,'$.byline'))),'null'),'')<>'' OR dp.id IS NOT NULL))
 OR TRIM(w.byline)='署名待核' OR p.name='署名待核'
 OR TRIM(JSON_UNQUOTE(JSON_EXTRACT(d.payload,'$.byline')))='署名待核' OR dp.name='署名待核';

UPDATE work_drafts d JOIN works w ON w.id=d.work_id
 JOIN members m ON m.id=w.author_id
 LEFT JOIN pen_names p ON p.id=w.pen_name_id
 LEFT JOIN pen_names dp ON dp.id=CAST(NULLIF(JSON_UNQUOTE(JSON_EXTRACT(d.payload,'$.penNameId')),'null') AS UNSIGNED)
 SET d.payload=CASE
   WHEN COALESCE(NULLIF(NULLIF(TRIM(JSON_UNQUOTE(JSON_EXTRACT(d.payload,'$.byline'))),'null'),''),dp.name,'')=''
     THEN d.payload
   ELSE JSON_SET(d.payload,'$.penNameId',0,
     '$.byline',CASE WHEN TRIM(JSON_UNQUOTE(JSON_EXTRACT(d.payload,'$.byline'))) IN ('署名待核','佚名') OR dp.name='署名待核'
       THEN '佚名' ELSE COALESCE(NULLIF(NULLIF(TRIM(JSON_UNQUOTE(JSON_EXTRACT(d.payload,'$.byline'))),'null'),''),dp.name) END,
     '$.bylineMode',CASE WHEN TRIM(JSON_UNQUOTE(JSON_EXTRACT(d.payload,'$.byline'))) IN ('署名待核','佚名') OR dp.name='署名待核'
       THEN 'ANONYMOUS' ELSE 'TEMPORARY' END)
   END,
   d.revision=w.edit_revision,d.updated_at=d.updated_at
 WHERE m.username='haiyan_editor' OR TRIM(w.byline)='署名待核' OR p.name='署名待核'
 OR TRIM(JSON_UNQUOTE(JSON_EXTRACT(d.payload,'$.byline')))='署名待核' OR dp.name='署名待核';

UPDATE works w JOIN members m ON m.id=w.author_id
 LEFT JOIN pen_names p ON p.id=w.pen_name_id
 SET w.byline=CASE WHEN TRIM(w.byline) IN ('署名待核','佚名') OR p.name='署名待核'
     THEN '佚名' ELSE COALESCE(NULLIF(TRIM(w.byline),''),p.name,'') END,
   w.byline_mode=CASE WHEN COALESCE(NULLIF(TRIM(w.byline),''),p.name,'') IN ('署名待核','佚名') OR p.name='署名待核'
     THEN 'ANONYMOUS' ELSE 'TEMPORARY' END,
   w.pen_name_id=NULL,w.updated_at=w.updated_at
 WHERE (m.username='haiyan_editor' AND (w.pen_name_id IS NOT NULL OR TRIM(w.byline)<>''))
 OR TRIM(w.byline)='署名待核' OR p.name='署名待核';

DELETE b FROM member_pen_names b JOIN members m ON m.id=b.member_id
 WHERE m.username='haiyan_editor';
