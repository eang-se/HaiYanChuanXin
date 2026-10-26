# 账号、注册与合集接口

更新：2026-10-26。当前本地地址 `http://127.0.0.1:8091/`。

## 页面入口

- 登录 / 注册：`/#/login`。账号为 3～30 位字母、数字、下划线，密码至少 10 位且最多 72 UTF-8 字节。
- 个人中心：`/#/profile`，可设置笔名、头像、签名（120 字以内）和简介（300 字以内）。点击页头头像可进入。默认头像为账号首字，默认笔名为完整账号名。
- 新账号首次登录会弹出资料提醒。成功保存后标记完成；选择“稍后设置”只关闭后续提醒，保留账号名作为默认笔名。失败时不会覆盖已保存资料。
- 后台配置：`/#/admin/settings`，管理员专用独立页面。社务台保留稿件、成员及活动管理。
- 首页 / 作品书架：横条、卡片切换，选择保存在本机浏览器。正文段落外间距由 `1.5em` 调整为 `0.8em`。

## QQ 与验证码

测试模式仅接受 QQ `12345`、验证码 `0000`，可直接注册。测试“获取验证码”只返回测试说明，不发送邮件或 QQ 消息。获取间隔为 60 秒；这两个测试值不能在正式模式使用。

正式模式读取 `qq_allowlist` 中启用的 QQ 号。验证码为随机 6 位数字，有效期 10 分钟，哈希保存，一次使用，最多 5 次错误尝试。当前默认发送器返回 HTTP 503，**没有接通真实邮件或群聊机器人**，不会假装发送成功。

正式接入扩展点：

- `QqEligibility`：注册资格检查，目前由 `DatabaseQqEligibility` 实现，可替换为学校名册服务。
- `VerificationDelivery.send(qq, code, channel)`：`email` 表示发送到同名 QQ 邮箱，`bot` 表示机器人私信。提供该接口的 Spring Bean 即可替换默认未配置发送器。不要把验证码写入日志。
- `RegistrationVerification`：发送频率、有效期、尝试次数和消费控制；`RegistrationPolicy`：注册开关和测试模式。

| 方法 | API | 功能 |
| --- | --- | --- |
| GET | `/api/auth/registration-options` | 是否开放、是否测试模式、接收渠道 |
| POST | `/api/auth/verification-code` | `{qq,channel}` 获取验证码 |
| POST | `/api/auth/register` | `{username,password,qq,code}` 注册 |
| GET | `/api/admin/registration/qq-allowlist` | QQ 号库，仅管理员 |
| POST | `/api/admin/registration/qq-allowlist` | `{qqNumbers:["12345678"]}`，每次最多 1000 个 |
| DELETE | `/api/admin/registration/qq-allowlist/{qq}` | 停用号码，可重新导入启用 |
| GET/PUT | `/api/admin/settings` | 站点名称、介绍、注册开关、`test/live` 模式 |

QQ 号只返回给本人或管理员，公开作者页不返回 QQ 号。头像只允许使用本人上传的 PNG/JPEG，复用原有尺寸、压缩和文件校验；被个人资料引用后可公开读取，移除引用后恢复原有图片访问规则。

## 账号权限

基础角色为 `MEMBER` / `ADMIN`，预留 `WRITE`（投稿）、`COMMENT`（评论）、`UPLOAD`（文件/头像上传）、`COLLECTIONS`（合集管理）能力。普通社员默认拥有这四项，`member_permissions` 保存显式覆盖。管理员拥有全部能力，不能移除最后一位管理员。

`GET /api/admin/accounts/{id}/permissions` 返回角色和有效权限；`PUT` 接受 `{role:"MEMBER",permissions:{WRITE:false,COMMENT:true,UPLOAD:true,COLLECTIONS:true}}`。未知能力代码被拒绝。权限在业务入口校验；每次请求重新从数据库读取角色，避免旧 Session 保留管理员权限。所有修改接口继续要求 CSRF Token。

## 合集特殊标签

V4 将旧目录关系迁移到 `work_tags`，保留原来的合集元信息与顺序。`tags.kind='COLLECTION'`，标签名称固定为 `合集：名称`，关联 `owner_id` 与 `collection_id`；`work_tags.position` 记录篇目顺序。旧 `collection_works` 只保留历史数据，当前读写不再使用。

投稿接口新增 `collectionTags:["#合集：校园诗集"]`，也接受不带前缀的名称。每篇最多 5 个普通标签、10 个合集标签。只能使用自己的合集；同一作者的合集名称不能重复。删除文章中的合集标签会同步移出目录，不删除文章或合集。

标签创建的新合集：草稿阶段私密，首次发布篇目时公开；通过合集管理页手动保存隐私设置后，投稿不再覆盖该设置。已公开目录仅展示公开篇目。编辑文章保留既有顺序，新增篇目追加到末尾。合集编辑页改名或排序同样写回特殊标签。

## 验证与部署

`tools/Test-Accounts.ps1` 在真实 MySQL 上使用随机临时账号验证注册、默认资料、头像归属、提醒、合集标签及权限，完成后清理其自建数据。`Test-Community.ps1` 已转到此新版流程。后端测试额外覆盖正式模式不接受测试验证码、错误尝试计数、验证码消费和最后管理员保护。

本次包含数据库备份的命令被自动审批拦截，未返回具体原因，未生成该备份。V4 为新增字段和关系复制，保留旧业务表记录；后续仍应按本地部署文档另行保存完整备份。
