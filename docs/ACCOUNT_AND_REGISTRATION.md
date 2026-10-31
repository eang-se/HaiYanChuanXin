# 账号、注册与合集接口

更新：2026-10-26。当前本地地址 `http://127.0.0.1:8091/`。

## 页面入口

- 登录 / 注册 / 找回密码：`/#/login`。找回密码向账号绑定的 QQ 邮箱发送单次验证码，有效期 10 分钟、最多错误 5 次；未绑定 QQ 的账号需联系管理员。测试 QQ `12345` 在测试模式下使用 `0000`，找回密码需先获取验证码。密码重设后已有登录会话失效。账号为 3～30 位字母、数字、下划线，密码至少 10 位且最多 72 UTF-8 字节。
- 个人中心：`/#/profile`，可设置笔名、签名（120 字以内）和简介（300 字以内）。点击页头头像可进入。头像自动使用注册 QQ 号；旧账号未关联 QQ 或头像获取失败时显示账号首字，正式账号注册时默认昵称取自本地 QQ 名单，测试账号默认使用账号名。
- 新账号首次登录会弹出资料提醒。成功保存后标记完成；选择“稍后设置”只关闭后续提醒，保留注册时的默认昵称。失败时不会覆盖已保存资料。
- 后台配置：`/#/admin/settings`，管理员专用独立页面。社务台保留稿件、成员及活动管理。
- 首页 / 作品书架：横条、卡片切换，选择保存在本机浏览器。正文段落外间距由 `1.5em` 调整为 `0.8em`。

## QQ 与验证码

邀请码是 QQ 号：系统实时读取项目 `QQ_Whitelist` 目录中所有 `.md` 文件，文件名为群号，每行填写“QQ号 空格 默认昵称”；文件变更后无需重启。后台按群查看，同一 QQ 的启用、停用和绑定状态跨群同步。若多个群对同一 QQ 设置了不同昵称，注册时默认使用按文件名排序的第一个群昵称。名单内未停用、未绑定的号码可申请注册，管理员可在后台停用或重新启用名单内号码。每个正式 QQ 号注册成功后与账号绑定，不能再次注册；删除账号也不会自动释放号码。验证码发送到 `QQ号@qq.com`。每个号码获取验证码的间隔为 60 秒；发送失败也会记录请求时间。

开启测试账号时，QQ `12345` 可使用验证码 `0000` 多次注册，不受一次绑定限制，并与真实名单号码同时生效。测试账号不发送邮件；关闭测试账号后，`0000` 不再作为通用验证码。已有账号不会被重置。

真实验证码为随机 6 位数字，有效期 10 分钟，哈希保存，一次使用，最多 5 次错误尝试。通过 QQ 邮箱的 `smtp.qq.com:465`（TLS）发送，SMTP 服务器接受邮件后返回发送成功；邮件是否进入收件箱还取决于后续投递。邮件只发往提交的同名 QQ 邮箱。

发件账号优先读取应用配置 `haiyan.mail.username` / `haiyan.mail.password`（QQ 邮箱 SMTP 授权码），或环境变量 `HAIYAN_SMTP_USERNAME` / `HAIYAN_SMTP_PASSWORD`。两项均未设置时，默认只读取相邻 RealmMusic 的 `../realm/realm/application-local.properties` 中的 `email.smtp.email` / `email.smtp.code`；可以用 `haiyan.mail.realm-config` 指定文件位置。凭据不会复制进本项目、写入日志或接口响应。修改凭据后需重启后端；账号未配置或发信失败时，接口返回错误而不会伪装成功。

正式接入扩展点：

- `QqEligibility`：注册资格检查；`MarkdownQqDirectory` 读取本地名册，`DatabaseQqEligibility` 同时检查后台停用状态。未来 QQ 机器人可更新名册文件。
- `VerificationDelivery.send(qq, code, channel)`：当前实现为 QQ 邮箱 SMTP 发信，`channel` 为 `email`。不要把验证码写入日志。
- `RegistrationVerification`：发送频率、有效期、尝试次数和消费控制；`RegistrationPolicy`：注册开关和测试账号开关。
- QQ 机器人后续可由受信任的管理端调用 QQ 号库导入接口，当前没有连接 QQ 机器人或开放匿名导入。

| 方法 | API | 功能 |
| --- | --- | --- |
| GET | `/api/auth/registration-options` | 是否开放、是否测试模式、接收渠道 |
| POST | `/api/auth/verification-code` | `{qq,channel}` 获取验证码 |
| POST | `/api/auth/register` | `{username,password,qq,code}` 注册 |
| GET | `/api/admin/registration/qq-allowlist` | QQ 号库，仅管理员 |
| POST | `/api/admin/registration/qq-allowlist` | `{qqNumbers:["12345678"]}`，启用已在本地名单中的号码，每次最多 1000 个 |
| DELETE | `/api/admin/registration/qq-allowlist/{qq}` | 停用号码，可重新导入启用 |
| GET/PUT | `/api/admin/settings` | 站点名称、介绍、注册开关、`test/live` 模式 |

QQ 号只返回给本人或管理员，公开作者页不返回 QQ 号。头像由服务端根据注册 QQ 号从公开 QQ 头像接口获取并转发，不在本地保存；公开接口只暴露用户 ID。旧头像文件若被作品引用则保留，未引用的旧头像文件会清理。

## 账号权限

基础角色为 `MEMBER` / `ADMIN`，预留 `WRITE`（投稿）、`COMMENT`（评论）、`UPLOAD`（作品文件上传）、`COLLECTIONS`（合集管理）能力。普通社员默认拥有这四项，`member_permissions` 保存显式覆盖。管理员拥有全部能力，不能移除最后一位管理员。

`GET /api/admin/accounts/{id}/permissions` 返回角色和有效权限；`PUT` 接受 `{role:"MEMBER",permissions:{WRITE:false,COMMENT:true,UPLOAD:true,COLLECTIONS:true}}`。未知能力代码被拒绝。权限在业务入口校验；每次请求重新从数据库读取角色，避免旧 Session 保留管理员权限。所有修改接口继续要求 CSRF Token。

## 合集特殊标签

V4 将旧目录关系迁移到 `work_tags`，保留原来的合集元信息与顺序。`tags.kind='COLLECTION'`，标签名称固定为 `合集：名称`，关联 `owner_id` 与 `collection_id`；`work_tags.position` 记录篇目顺序。旧 `collection_works` 只保留历史数据，当前读写不再使用。

投稿接口新增 `collectionTags:["#合集：校园诗集"]`，也接受不带前缀的名称。每篇最多 5 个普通标签、10 个合集标签。只能使用自己的合集；同一作者的合集名称不能重复。删除文章中的合集标签会同步移出目录，不删除文章或合集。

标签创建的新合集：草稿阶段私密，首次发布篇目时公开；通过合集管理页手动保存隐私设置后，投稿不再覆盖该设置。已公开目录仅展示公开篇目。编辑文章保留既有顺序，新增篇目追加到末尾。合集编辑页改名或排序同样写回特殊标签。

## 验证与部署

`tools/Test-Accounts.ps1` 在真实 MySQL 上使用随机临时账号验证注册、默认资料、QQ 头像映射、提醒、合集标签及权限，完成后清理其自建数据。`Test-Community.ps1` 已转到此新版流程。`tools/Test-QqRegistration.ps1` 用本地生成的临时代码验证真实 QQ 路径的错误次数限制和并发一次性消费，不发送邮件。后端测试覆盖邮箱地址生成、发送失败和测试账号隔离。

本次包含数据库备份的命令被自动审批拦截，未返回具体原因，未生成该备份。V4 为新增字段和关系复制，保留旧业务表记录；后续仍应按本地部署文档另行保存完整备份。

## 快捷登录

登录时可选择记住此设备；服务端只保存随机令牌的哈希，浏览器保存 HttpOnly Cookie，有效期 30 天。登录页面可一键进入并选择身份，也可移除本设备记录。退出登录后设备记录保留；修改密码后记录失效。此功能仅在本机地址或 HTTPS 下可用。
