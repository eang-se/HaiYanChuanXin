# 海燕传信

面向校内文学社的社员投稿、作品分享与浏览平台。

## Linux 正式部署

已按阿里云首尔、Alibaba Cloud Linux 4、2 核 2GB、40GB 配置准备[上云方案](docs/CLOUD_DEPLOYMENT_PLAN.md)、[部署与数据迁移手册](docs/LINUX_DEPLOYMENT.md)及 `deploy/linux/` 配置模板。云端程序以 GitHub 明确提交为准，目前通过域名 HTTPS 提供服务。账号、文章、图片、QQ 名单和生产凭据独立保存在服务器，代码更新不使用本地数据库覆盖线上数据；模板不包含真实凭据。

本地生产 JAR 与云端静态缓存、压缩、品牌缩略图和 Nginx HTTP/2 的配置及发布方法见[性能说明](docs/PERFORMANCE.md)。

当前已完成可运行的本地业务原型：社员通过账号、密码、QQ 号与验证码注册，使用笔名写作，保存草稿、导入文档、插入图片并发布；访客浏览公开作品，社员可以点赞、收藏与评论。首页采用纸白、松绿和宋体排版，适配电脑与手机。

## 已实现的功能

- 首页、作品书架、标题/作者/正文搜索、体裁和标签筛选、排序与分页、作者主页。
- 邀请码为 QQ 号，名单中的号码可用同名 QQ 邮箱验证码注册；测试账号启用时可使用 `12345` / `0000`。支持登录退出、个人中心和首次登录资料提醒。
- 富文本编辑、阅读预览、草稿自动保存、发布、更新和撤回；后台关联真实社员账号，不提供匿名投稿。首页可切换横条和卡片信息流，浏览器记住显示选择。
- DOCX、文字型 PDF、UTF-8 TXT 提取正文；PNG/JPEG 配图上传，自动压缩并移除原图元数据。
- 投稿时从开放活动列表选择参与活动，内容标签由作者自行填写；活动与内容标签独立展示，同名也不会混用。支持活动结束 / 重新开放、点赞、收藏、评论和链接分享，详见[活动与标签说明](docs/TAGS_AND_ACTIVITIES.md)。
- 合集支持连载、诗集和选集，以文章的独立属性展示；每篇最多绑定一部合集。支持公开/私密、手动排序和连续阅读。书桌可直接管理多个绑定笔名；编辑器支持绑定笔名、佚名和临时署名，输入新名字后即时询问是否绑定。他人已绑定的名字直接作为临时署名使用。发布账号与稿件管理权限保持独立，详见[署名与活动管理](docs/PEN_NAMES_AND_ACTIVITIES.md)。
- 社务台使用独立页面管理账户、文章审核、活动标签和站点设置；支持账户角色与权限、公用身份授权、非草稿文章分页检索、正文预览、精选推荐、下架与审核通过、公告文章、分级删除与 QQ 注册名单管理。
- Session 登录、BCrypt 密码、CSRF 校验、作者权限、HTML 清理和未发布图片访问控制。

首版包含 3 篇明确标记的原创示例作品，用来展示版式；未添加虚构互动数据。

## 技术环境

| 组件 | 项目版本 |
| --- | --- |
| JDK | Eclipse Temurin 21.0.12.1 LTS |
| Node.js | 24.22.1 LTS |
| Maven | 3.9.16 |
| Spring Boot | 4.1.2 |
| MyBatis Starter | 4.1.0 |
| MySQL | 复用本机 MySQL 8.0.46 服务 |
| Vue | 3.5.44 |
| Vite | 8.3.5 |
| TypeScript | 5.9.3 |

项目工具链安装在 `.tools/`，启动及构建脚本会自动使用该工具链。未替换全局 Java、Node、Maven，也未升级 MySQL 服务或修改 RealmMusic 项目。前端依赖由 `frontend/package-lock.json` 锁定。

## 已部署环境的使用

双击 `Start.cmd` 启动，访问 <http://127.0.0.1:8091/>。双击 `Stop.cmd` 停止本项目记录的服务。代码改动后，先停止服务，双击 `Build.cmd`，再启动。

桌面“海燕传信后端启动”已提供图形管理窗口，与 Realm 后端入口采用同类布局：可保存监听地址与端口，启动、停止、重启服务，打开网页、查看日志及手动放行局域网。双击后按上次保存的设置启动；关闭管理窗口不停止 Java 后端。默认仅本机 `127.0.0.1:8091`，局域网放行按钮需要用户确认管理员权限，仅允许专用网络中的本地子网访问。

桌面窗口中的“服务器与 QQ 名单管理”支持 SSH 管理云端 systemd 服务、自定义远程命令，以及向本地或云端更新 QQ 名单并保留备份。连接准备、权限和名单格式见[桌面服务器管理说明](docs/DESKTOP_SERVER_MANAGEMENT.md)。

图形入口源码在 `tools/backend-launcher/`，与命令行脚本共用 `.runtime/server.json`，设置单独保存在 `.runtime/backend-launcher/settings.json`。重建桌面入口可用 Windows PowerShell 执行 `tools/backend-launcher/Install-DesktopShortcut.ps1`。

初始管理员用户名为 `admin`，随机密码保存在本机 `.local/admin-account.txt`。登录后进入“社务台 → 账户管理”管理角色、权限与公用身份授权，进入“站点设置”管理注册模式、QQ 名单和站点资料；首次空库启动才会创建管理员与示例作品，重复启动不会重置账号或数据。社务台页面与权限说明见 [社务台重构说明](docs/ADMIN_REDESIGN.md)。

也可以在项目根目录的 PowerShell 7 中运行：

```powershell
./tools/Build.ps1
./tools/Start-Local.ps1
./tools/Test-Local.ps1
./tools/Stop-Local.ps1
```

`Start-Local.ps1 -Port 8092` 可以更换端口；`Test-Local.ps1 -Port 8092` 对应检查。默认仅监听本机。若需要手机联调，使用 `Start-Local.ps1 -Lan`，让设备处于同一局域网，并为选定端口设置仅限本地子网的防火墙入站规则；脚本不会自动改防火墙。手机使用电脑的局域网 IP，不能使用 `localhost`。

## 首次安装或恢复

需要 Windows x64、PowerShell 7、可访问官方软件下载站及 npm/Maven 仓库的网络，以及正在运行的 MySQL 服务。

```powershell
./tools/Install-Toolchain.ps1
./tools/Initialize-Database.ps1
./tools/Prepare-Community.ps1
./tools/Build.ps1
./tools/Start-Local.ps1
```

本机数据库初始化默认读取相邻 RealmMusic 的本地 MySQL 管理员凭据，用于创建 `haiyan_chuanxin` 数据库及限定该数据库权限的 `haiyan_app` 用户。原账号与原数据库保持原样。迁移到其他电脑时，可以通过 `-CredentialSource` 指定包含 `spring.datasource.username`、`spring.datasource.password` 的管理员配置文件；默认连接 `127.0.0.1:3306`。

应用凭据保存在 `.local/application-local.properties`，目录限制为当前 Windows 用户访问；该目录被 Git 忽略，也不会打包进 JAR。文件存在时，初始化脚本不会重新生成密码。请同时保管数据库备份和这份配置，不要提交或分享凭据。

## 开发与运行

```powershell
# 仅修改当前 PowerShell 会话的工具链，不改变全局设置
. ./tools/Use-Environment.ps1

# 后端开发（在项目根目录）
mvn.cmd -f backend/pom.xml spring-boot:run '-Dspring-boot.run.arguments=--spring.config.additional-location=file:../.local/application-local.properties'

# 另开一个 PowerShell，运行前端开发服务器
. ./tools/Use-Environment.ps1
Set-Location frontend
npm.cmd ci
npm.cmd run dev
```

前端开发地址为 `http://127.0.0.1:5173/`，将 `/api` 和健康检查代理到 `8091`。正式本地预览使用已构建 JAR，前后端统一由 `8091` 提供。

运行日志与进程记录在 `.runtime/`。Java 启动参数为 `-Xms128m -Xmx768m`，MySQL 应用连接池最多 5 个连接。业务数据保存在 MySQL，已上传图片保存在项目 `data/images/`。新业务使用 Spring JDBC 参数化查询和事务，站点配置继续通过 MyBatis 读取。

`Build.ps1` 包含前端类型检查、生产构建和后端测试。服务启动后可运行 `tools/Test-Local.ps1` 验证部署，运行 `tools/Test-Community.ps1` 验证真实数据库上的注册、权限、投稿与互动流程；后者会创建独立临时账号并清理自己的测试数据。

## 数据库变更与备份

Flyway 在启动时执行 `backend/src/main/resources/db/migration/` 下的版本迁移，禁止自动清库。已经执行的迁移文件不要直接修改，结构调整新增后续版本迁移。

V1 为平台配置，V2 为基础业务，V3 为合集，V4 为个人资料、注册校验、权限和合集特殊标签。V4 将旧合集关系复制到 `work_tags` 并保留旧表，不清理历史记录。备份需同时覆盖数据库、`.local/` 和 `data/`，至少一份保存到另一设备或独立存储。文档原件不留存，仅保存导入后编辑的正文。备份恢复时停止写入，保持数据库与图片的一致性。

## 当前原型边界

- 文章与评论使用本地开源词库检查：文章和评论使用 sensitive-word 0.29.5（Apache-2.0）的完整词库（包含部分宗教相关词条及辱骂），因此文章也可能因辱骂词命中。另使用 sensitive-stop-words（Apache-2.0）较长的广告词条。文章至少有 3 处独立命中且覆盖非空白字符达到 1% 时提示修改或申请人工复核；重叠词条按一处计算。评论命中直接提示修改。词库并不专门标注宗教，覆盖不完整；词组匹配可能误判或漏判，不理解语义，也不识别图片。
- 文档最大 20 MB，PDF 最多 100 页，提取文字最多 10 万字符；不支持扫描件 OCR、旧版 `.doc`，不保留 Word 复杂版式及内嵌图片。上传后需作者检查正文。
- 单张 PNG/JPEG 最大 5 MB、2,000 万像素、边长不超过 8,000；存储时压缩到最长边 1,600 的 JPEG。未发布图片仅上传者可访问，只有关联公开作品的图片对访客可见。
- 文章支持发布历史正文与私密草稿，详见[文章存储与版本说明](docs/ARTICLE_STORAGE_AND_VERSIONS.md)。账号通过 QQ 注册校验关联身份，尚未接入学校统一身份认证。
- 当前按本机原型部署。正式开放前需补充 HTTPS、登录限流、备份自动化与恢复演练，并按实际投稿量验证容量。资源估算见 `docs/LOCAL_DEPLOYMENT.md`。

## 需求约定

- 面向校内文学社，500 人以内；社员投稿，游客可浏览已公开作品。
- 支持在线编辑、粘贴、上传文件并解析；作者预览确认后提交。
- 不限体裁，支持笔名与配图，后台始终关联社员身份。
- 必要自动审核通过后发布；活动采用标签。
- 包含搜索、点赞、收藏、评论、作者主页与链接分享。
- 原型已改为 QQ 号与验证码注册，测试值为 `12345` / `0000`；外部审核服务、扫描件 OCR 和旧版 Word 可在后续阶段确定。

权限规则、删除语义和公用身份边界见 [当前权限体系](docs/PERMISSIONS.md)，社务台工作概览同时展示权限表。
