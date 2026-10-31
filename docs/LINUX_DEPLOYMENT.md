# 首尔服务器正式部署与迁移手册

适用 2026-10-31 已购配置：阿里云首尔、Alibaba Cloud Linux 4、2 核 2GB、40GB、标称 200Mbps。本文为待执行手册，模板尚未在该远程实例验收。所有 CHANGE_ME、SERVER_IP、USER、COMMIT、example.com 必须替换；含密码的实际配置、SQL 和备份禁止上传 GitHub。

## 1. 系统与网络确认

通过阿里云 SSH/Workbench 或已有 SSH 登录，先运行只读命令：

```bash
cat /etc/os-release
uname -m
command -v dnf || command -v apt
free -h
df -h /
ss -lnt
```

标准 RPM 版查询后安装，不使用 `dnf module enable mysql:8.0`：

```bash
sudo dnf makecache
dnf list --available '*java*21*' '*dragonwell*21*' 'mysql*server*' nginx
```

按查询结果选择 Java **21** runtime/headless 或 Dragonwell 21、MySQL **8.x** server/client、Nginx，以及 curl、tar、gzip、openssl。不要误装默认 Java 8/17 或把 MariaDB 当作已验证的替代品。安装后检查 `java -version`、`mysql --version`、`nginx -v` 以及 `/usr/bin/java`；服务模板使用该路径。若目标数据库低于本机源库 8.0.46，应先确认 MySQL 支持的逻辑迁移兼容性并演练，不直接生产导入；优先使用同版本或已验证的更高补丁版本。

若实例为 Deb Edition，按该系统官方 apt 软件源安装；不要运行 RPM 命令。软件仓库版本不满足时，使用适配该发行版的官方仓库或受支持的二进制包，不套用未知的 CentOS RPM 包。

阿里云安全组/轻量防火墙：SSH 限维护者地址，正式开放 80/443；不开放 3306、33060、8091、5173。先建立可用 SSH 会话再调整 SSH 规则。主机启用 firewalld 时配置相同规则；不关闭 SELinux 来解决权限问题。Nginx 连接本机上游若遇 SELinux 拒绝，按审计信息设置所需连接策略。

## 2. 以 GitHub 提交为发布版本

程序来自 `https://github.com/eang-se/HaiYanChuanXin` 的明确提交，不把服务器目录和本机临时文件双向覆盖。

本机另建干净目录，检出已推送提交。使用已有工具链测试和构建：

```text
frontend: npm ci → npm test → npm run build
copy frontend/dist/* → backend/src/main/resources/static/
backend: mvn --batch-mode --no-transfer-progress verify
artifact: backend/target/haiyan-chuanxin.jar
```

若使用项目 Build.ps1，新目录要配置其所需 .tools/toolchain.json，不可误认为 GitHub 包含本机工具链。生产配置和数据均不打包进 JAR。Windows 构建的 JAR 可交给 Linux Java 21 运行，须包含这次前端构建结果。记录完整 Git 提交号和 `Get-FileHash -Algorithm SHA256` 或 `sha256sum` 结果；通过 SCP 上传 `/opt/haiyan/releases/COMMIT/app.jar`，远端再次校验。

2GB 主机不同时运行线上服务和完整 npm/Maven 构建。后续可由 GitHub Actions 构建该提交的产物，首次无需购买构建服务器。

## 3. 服务账号与目录

首次部署、确认无同名账号及目录后执行：

```bash
sudo useradd --system --home-dir /var/lib/haiyan --shell /sbin/nologin haiyan
sudo install -d -o root -g haiyan -m 0750 /opt/haiyan /opt/haiyan/releases /etc/haiyan
sudo install -d -o haiyan -g haiyan -m 0750 /var/lib/haiyan /var/lib/haiyan/data /var/lib/haiyan/data/images /var/lib/haiyan/QQ_Whitelist
```

JAR/发布目录由 root 管理并允许 haiyan 读取，应用不能修改自己的程序。current 链接指向当前提交的发布目录，例如首次配置使用 `sudo ln -s /opt/haiyan/releases/COMMIT /opt/haiyan/current`。`WorkingDirectory=/var/lib/haiyan` 不可遗漏：当前图片目录 data/images 是相对路径。

实际配置 `/etc/haiyan/application-production.properties` 设置 root:haiyan、0640。从 `deploy/linux/application-production.properties.example` 复制后替换所有占位符；不要复用本机数据库密码。QQ 邮箱与 SMTP 授权码明确配置，云端不依赖本机相邻 Realm 项目；名单用绝对路径。

properties 中反斜杠等须正确转义；数据库密码可生成无转义歧义的随机十六进制字符串。实际文件不得包含 CHANGE_ME。模板为 HTTPS 模式，私有 HTTP 验收遵循第 6 节临时覆盖。

## 4. 数据库配置和迁移

MySQL 8.x 仅监听本机回环。`deploy/linux/mysql-small.cnf` 是共享 2GB 内存起步配置，不是总内存限制。安装到发行版实际读取的 include 目录前检查冲突，再重启 MySQL；确认 3306/33060 不监听公网。不删除已有 MySQL 数据目录，不覆盖其他业务库。

在 MySQL 管理会话中创建专用数据库和应用用户，密码用独立随机值，不将真实密码写入仓库或命令行参数：

```sql
CREATE DATABASE haiyan_chuanxin CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE USER 'haiyan_app'@'127.0.0.1' IDENTIFIED BY 'CHANGE_ME_DATABASE_PASSWORD';
GRANT ALL PRIVILEGES ON haiyan_chuanxin.* TO 'haiyan_app'@'127.0.0.1';
```

业务库内权限用于现有 Flyway DDL，不授予全局管理权限。使用 `mysql --host=127.0.0.1 --user=haiyan_app --password` 验证 TCP 登录，不能仅验证 socket localhost 用户。

### 本机停写和导出

约定维护窗口，停止本机网站和 FRP 访问通道，不停止整个本机 MySQL 服务。数据库、图片和名单来自同一次停写窗口。先记录账号、作品、合集、笔名、媒体数量和 Flyway 最新版本，不输出密码哈希或完整名单。

使用 MySQL 8 客户端，仅导出 haiyan_chuanxin 及库内 Flyway 历史，不导出 mysql 系统库、root 用户或 RealmMusic 库：

```powershell
& 'C:\Program Files\MySQL\MySQL Server 8.0\bin\mysqldump.exe' --host=127.0.0.1 --user=haiyan_app --password --single-transaction --no-tablespaces --set-gtid-purged=OFF --default-character-set=utf8mb4 --result-file=C:\PRIVATE_BACKUP\haiyan.sql haiyan_chuanxin
```

`--password` 不附值，使用交互输入或受限 MySQL option file。先创建 PRIVATE_BACKUP 私有目录，检查退出码和文件大小。使用 result-file 避免 PowerShell 重定向改变 SQL 编码。若库另有自建 routines/events，需另行核实权限并带上。

同时打包 data/images/ 和 QQ_Whitelist/，保留文件名及结构。SMTP 授权码私有配置；备份经 SCP 加密传输，不进入公开仓库。

### 云端恢复

在空目标业务库、应用尚未首次启动时，用有恢复权限的数据库管理员导入：

```bash
mysql --host=127.0.0.1 --user=RESTORE_USER --password --default-character-set=utf8mb4 haiyan_chuanxin < /PRIVATE_BACKUP/haiyan.sql
```

不向已有新数据的库盲目重放 dump。解包媒体和名单到第 3 节目录，haiyan 可读、媒体目录可写。保留 flyway_schema_history；启动仅执行后续迁移，不运行 Flyway clean、不删历史表、不改已执行迁移。

恢复个人成员后 BootstrapData 不重建 admin 或示例文章，原管理员和密码继续有效；bootstrap 密码不能重置现有账号。对比迁移前数量，随机检查旧图片、合集排序、作者与笔名。

公网开放前先在 platform_metadata 把 registration_enabled 改为 false、registration_mode 改为 live，通过后台完成真实邮件与名单测试后再开放注册。导库会把本地 test 模式一并带过来，不能仅改 properties。

## 5. systemd 启动

恢复配置、数据库和媒体后，将 haiyan.service 安装到 /etc/systemd/system/，确认 Java 21、current/JAR、目录权限正确：

```bash
sudo systemctl daemon-reload
sudo systemctl enable --now haiyan
sudo systemctl status haiyan --no-pager
sudo journalctl -u haiyan -n 80 --no-pager
curl --fail http://127.0.0.1:8091/actuator/health
```

模板包含崩溃重启、512MB Java 堆和进程内存保护。1GB 硬上限并非性能保证，须测试图片、20MB 文档与 PDF 处理峰值。日志进入 journal，按服务器所有业务情况配置总日志限额，例如 200MB。实例有 swap 时监控实际交换量；若没有，可在确认磁盘和文件系统支持后配置约 1GB swap 缓冲，swap 不能代替内存，也不在未检查实例前自动创建。

## 6. 暂无域名时私有验收

保持 8091 不对公网开放，本机建立隧道：

```powershell
ssh -N -L 18091:127.0.0.1:8091 USER@SERVER_IP
```

仅在此阶段，用 systemd 临时 drop-in 的 [Service] 配置 `Environment=SERVER_SERVLET_SESSION_COOKIE_SECURE=false`，重载并重启；通过 http://127.0.0.1:18091/ 做浏览和普通登录验收。隧道仍加密，cookie 仅在本机 HTTP 入口使用。快捷登录的安全来源判断应在最终 HTTPS 环境单独验收。完成后只删除此临时覆盖，重载重启恢复 Secure cookie，不能带着覆盖开放公网。

无需域名即可完成迁移和私有验收；本方案不依赖纯 IP 公网证书。

## 7. 域名、TLS 与反向代理

域名 A 记录指向公网 IPv4；未准备 IPv6 不添加 AAAA。按阿里云说明，首尔境外部署无需大陆 ICP 备案；网络质量仍需实测。先搭建仅用于证书验证的临时 80 站点：

```nginx
server {
    listen 80;
    server_name example.com;
    location ^~ /.well-known/acme-challenge/ { root /var/www/haiyan-acme; }
    location / { return 404; }
}
```

创建 challenge 目录，使用官方支持当前发行版的 Certbot/ACME 客户端签发域名证书，webroot 为 /var/www/haiyan-acme，也可用 DNS 验证。具体安装命令在服务器确认后决定，不执行未知的 curl|bash 安装脚本。

证书就绪后，将 haiyan-proxy.inc 放到 /etc/nginx/，替换 nginx-https.conf.example 的域名/证书路径，放入 Nginx 实际加载的 http include 目录；`nginx -t` 成功再 reload。移走临时同名 80 站点，避免重复 server，不误删其他站点。

HTTP 自动跳 HTTPS。Nginx 覆盖转发来源头，应用仅信任回环代理，才能识别 HTTPS、设置安全 cookie 并避免伪造来源。若以后增加 CDN，要重新设计可信代理链。模板不缓存身份相关 API/受限媒体，上传上限 25MB，上游超时 120 秒；登录及发信返回 429 的限流需考虑校园网共享出口，按实际使用调整。

配置证书自动续期和续期后 reload，执行客户端 dry-run 验证。验收域名 HTTPS、普通/快捷登录、CSRF、邮箱验证码、旧图片及新增投稿；不以关闭 CSRF 解决问题。

## 8. 备份、更新与回退

- 数据库、图片、名单和配置共同备份，首次采用短暂停写的配套备份并异地保存；数据库 single-transaction 不会自动让图片目录获得相同快照。
- 40GB 不永久堆积发布包及备份；保留当前/上一版程序和少量本地备份，其他转独立设备或对象存储。
- 更新先记录线上 commit，备份，上传新提交 JAR、验哈希、切换 current、重启、检查健康及业务。
- 新版若执行不兼容迁移，仅回退 JAR 不一定安全；采用已验证的兼容策略或恢复整套备份，不能盲目降级。
- 本机保留迁移前备份，验收期间停止公开写入，确认云端承接后才恢复投稿，避免两端数据分叉。

验收记录提交号、JAR SHA-256、MySQL/Flyway 版本、数量对比、媒体抽样及功能测试、恢复演练结果，不记录敏感字段。本文不代表已连接或修改远程服务器。

## 官方参考

- [Alibaba Cloud Linux 4 与 3 差异](https://help.aliyun.com/en/alinux/getting-started/differences-between-alinux-4-and-3)
- [Alibaba Cloud Linux 4 镜像发布记录](https://help.aliyun.com/en/alinux/product-overview/alibaba-cloud-linux-4-image-release-notes)
- [Spring Boot systemd](https://docs.spring.io/spring-boot/how-to/deployment/installing.html)
- [Spring Boot 反向代理](https://docs.spring.io/spring-boot/how-to/webserver.html)
- [Nginx proxy module](https://nginx.org/en/docs/http/ngx_http_proxy_module.html)
- [MySQL mysqldump](https://dev.mysql.com/doc/refman/8.4/en/mysqldump.html)
- [阿里云备案条件](https://help.aliyun.com/en/icp-filing/basic-icp-service/support/for-the-record-process-faq)
