# 合集属性与笔名

合集作为文章的独立属性，存储在 works.collection_id 与 collection_position；单个字段保证一篇文章最多属于一部合集。普通标签只保留 TOPIC 类型。V11 把原 COLLECTION 标签关联迁入文章属性，保留原顺序，再清理旧合集标签。旧 collection_works 表仅保留历史数据，不再作为当前目录数据源。

编辑文章时使用 collectionId 单选已有的自有合集；0 表示移出合集，省略表示保留。直接切换到另一部自有合集会替换原归属并追加到新目录末尾。同合集内重新保存文章保留手动位置。编辑合集时不能直接收走已经归属其他合集的文章，须先在文章中修改或移出；跨账号绑定始终禁止。私密合集的名称、编号和位置不对访客暴露。

笔名由 pen_names 保存，以名称归集；member_pen_names 记录账号可用笔名。一篇文章的 pen_name_id 与上传账号 author_id 分开，只有上传账号可以修改稿件。绑定同名笔名本身不改变任何稿件的管理权限，也不表示身份认证。联合署名作为一个完整笔名保留，不自动拆分。

个人中心可以绑定、解绑笔名。使用中的笔名须先从稿件中移除才能解绑。投稿时可选绑定笔名，也可保留未绑定的署名文字；penNameId=0 表示不用绑定笔名，省略时保留旧值。已绑定的笔名按全站公开作品计数，超过一篇时署名链接到 /pen-name/:id，页面只汇总公开作品，并使用全站横条/卡片偏好。

本批年刊保留 25 篇、原合集与目录顺序；已确认署名对应的 21 篇迁移到 10 个笔名，“署名待核”的 4 篇保留原文字，不建立混合主页。账号昵称继续使用原 members.pen_name 字段，与可绑定的作品笔名分开。

API：GET/POST /api/member/pen-names、DELETE /api/member/pen-names/:id；GET /api/public/pen-names/:id；GET /api/public/works?penName=:id。作品返回 penNameId、penNameWorkCount、collectionId、collectionPosition 和 collection 对象。旧 collectionTags 非空输入返回明确错误，要求刷新后使用合集属性。
