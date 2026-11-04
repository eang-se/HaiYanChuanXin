-- Apply the account-removal policy to accounts deleted before this release.
DELETE b FROM qq_invitation_bindings b JOIN members m ON m.id=b.member_id WHERE m.deleted_at IS NOT NULL;
-- Remove never-published works and all private revisions owned by deleted accounts.
DELETE d FROM work_draft_media d JOIN works w ON w.id=d.work_id JOIN members m ON m.id=w.author_id WHERE m.deleted_at IS NOT NULL;
DELETE d FROM work_drafts d JOIN works w ON w.id=d.work_id JOIN members m ON m.id=w.author_id WHERE m.deleted_at IS NOT NULL;
DELETE vm FROM work_version_media vm JOIN work_versions v ON v.id=vm.version_id JOIN works w ON w.id=v.work_id JOIN members m ON m.id=w.author_id WHERE m.deleted_at IS NOT NULL AND w.status='DRAFT';
DELETE r FROM work_tags r JOIN works w ON w.id=r.work_id JOIN members m ON m.id=w.author_id WHERE m.deleted_at IS NOT NULL AND w.status='DRAFT';
DELETE r FROM work_media r JOIN works w ON w.id=r.work_id JOIN members m ON m.id=w.author_id WHERE m.deleted_at IS NOT NULL AND w.status='DRAFT';
DELETE r FROM collection_works r JOIN works w ON w.id=r.work_id JOIN members m ON m.id=w.author_id WHERE m.deleted_at IS NOT NULL AND w.status='DRAFT';
DELETE r FROM reactions r JOIN works w ON w.id=r.work_id JOIN members m ON m.id=w.author_id WHERE m.deleted_at IS NOT NULL AND w.status='DRAFT';
DELETE r FROM comments r JOIN works w ON w.id=r.work_id JOIN members m ON m.id=w.author_id WHERE m.deleted_at IS NOT NULL AND w.status='DRAFT';
DELETE w FROM works w JOIN members m ON m.id=w.author_id WHERE m.deleted_at IS NOT NULL AND w.status='DRAFT';
DELETE r FROM article_reviews r JOIN works w ON w.id=r.work_id JOIN members m ON m.id=w.author_id WHERE m.deleted_at IS NOT NULL AND r.status='PENDING';
UPDATE works w JOIN members m ON m.id=w.author_id
 SET w.status='WITHDRAWN',w.edit_revision=w.edit_revision+1 WHERE m.deleted_at IS NOT NULL AND w.status IN ('PUBLISHED','UNPUBLISHED');
UPDATE members SET qq_number='' WHERE deleted_at IS NOT NULL AND qq_number<>'';
