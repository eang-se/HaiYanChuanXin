-- Preserve existing official/editor member IDs and all their works and collections.
UPDATE members SET account_type='PUBLIC',role='MEMBER',password_hash='!PUBLIC_IDENTITY_DISABLED!',profile_completed=TRUE,profile_prompt_seen=TRUE
 WHERE username IN ('haiyan_official','haiyan_editor') AND qq_number='';
INSERT INTO members(username,password_hash,pen_name,bio,role,account_type,profile_completed,profile_prompt_seen)
 SELECT 'haiyan_official','!PUBLIC_IDENTITY_DISABLED!','海燕文学社官方','','MEMBER','PUBLIC',TRUE,TRUE
 WHERE NOT EXISTS (SELECT 1 FROM members WHERE username='haiyan_official');
INSERT INTO members(username,password_hash,pen_name,bio,role,account_type,profile_completed,profile_prompt_seen)
 SELECT 'haiyan_editor','!PUBLIC_IDENTITY_DISABLED!','海燕编辑部','','MEMBER','PUBLIC',TRUE,TRUE
 WHERE NOT EXISTS (SELECT 1 FROM members WHERE username='haiyan_editor');
INSERT INTO members(username,password_hash,pen_name,bio,role,account_type,profile_completed,profile_prompt_seen)
 VALUES ('haiyan_tech','!PUBLIC_IDENTITY_DISABLED!','海燕技术部','','MEMBER','PUBLIC',TRUE,TRUE),
        ('haiyan_activity','!PUBLIC_IDENTITY_DISABLED!','海燕活动综合','','MEMBER','PUBLIC',TRUE,TRUE);
INSERT INTO public_account_members(public_member_id,member_id)
 SELECT p.id,u.id FROM members p JOIN members u ON u.username='YuI' AND u.account_type='PERSONAL'
 WHERE p.username IN ('haiyan_official','haiyan_tech') AND p.account_type='PUBLIC';
