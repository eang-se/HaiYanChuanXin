# 海燕传信上云方案

更新：2026-10-31。已购买阿里云首尔地域服务器：Alibaba Cloud Linux 4、2 vCPU、2GB RAM、40GB 磁盘、标称 200Mbps；尚无域名。迁移现有账号、文章及媒体，程序以 GitHub 同步后的明确提交为准。

## 确定的架构

浏览器 → Nginx HTTPS :443 → Java 21 / Spring Boot :8091 → MySQL 8.x :3306。

Java 和数据库仅监听回环地址。前端构建后随 JAR 发布，服务器无需常驻 Node、Vite、Maven、Redis 或运维面板；systemd 管理自启、重启和日志。图片放在 /var/lib/haiyan/data/images，QQ 名单放在 /var/lib/haiyan/QQ_Whitelist，均独立于发布目录。

2GB 可以作为当前社团规模的起点，不承诺 500 人同时在线。首期 Java 堆上限 512MB、MySQL buffer pool 128MB、应用连接池 5。Java 总内存还包括堆外、线程栈和元空间；systemd 模板设置 850MB 软目标、1GB 硬上限，超过硬上限可能被终止。按实际常驻内存、图片/文档导入峰值和延迟调整，不能把堆上限当作进程总占用。

40GB 起步可用，发布目录保留当前及上一版；日志限额，备份保留少量本地副本并转存异地。监控磁盘达到 80%、内存频繁 swap/OOM 和数据库延迟。标称 200Mbps 不等于中国大陆校园网实测速度或无限流量，需核对订单流量包、出网费用及高峰时段访问。

## 版本与数据

- GitHub：前后端源码、迁移 SQL、依赖锁文件、部署模板及说明。
- 发布：从 GitHub 指定提交的干净快照构建 JAR，记录提交号和 JAR SHA-256，上传服务端；生产更新始终沿用此流程。
- 私有迁移：MySQL 业务库及 Flyway 历史、data/images/、QQ_Whitelist/；不进入 GitHub。
- 生产配置：重新生成服务器数据库密码，在服务器本地设置 QQ 邮箱 SMTP 授权码；不复制整个 RealmMusic 项目或其数据库。
- 恢复现有库时先导入再首次启动应用，保留成员 ID、密码哈希、文章和媒体关联。用户无需重新注册，但已有浏览器 Session 不会随进程迁移。

## 上线顺序

1. 确认系统具体版本、软件源、SSH 通道、磁盘、出网/SMTP 465 连接和安全组。
2. 上传与 GitHub 提交一致的构建产物；安装 Java 21、MySQL 8.x 和 Nginx。
3. 本地临时停写，导出业务库并打包媒体与名单；恢复到云端独立数据库与持久目录。
4. 通过 SSH 隧道先做私有验收，不把数据库和 Java 端口开放到公网。
5. 注册域名、设置 DNS A 记录、签发证书、启用 HTTPS；正式模式关闭测试注册，检查真实邮箱验证码。
6. 验证原有文章、账号、合集、图片及新增投稿；完成备份恢复演练后切换公网入口。

详细安装、配置、迁移、回滚与验收步骤见 [Linux 部署手册](LINUX_DEPLOYMENT.md)，配置模板位于 deploy/linux/。截至本文编写时，尚未连接或修改远程服务器。

## 地域和域名

阿里云说明，域名指向中国大陆以外地域服务器时不要求大陆 ICP 备案；首尔按该境外部署路径准备。域名仍建议尽早注册，作为固定 HTTPS 入口。此项目采用 QQ 名单与邮箱验证码，已不依赖 QQ OAuth 网站应用审批。[阿里云备案 FAQ](https://help.aliyun.com/en/icp-filing/basic-icp-service/support/for-the-record-process-faq)

没有域名时先使用 SSH 隧道验收，后续绑定域名；本方案不依赖纯 IP 的公网证书，也不把公网明文 HTTP 当成长期登录入口。

## 系统依据

Alibaba Cloud Linux 4 标准 RPM 版采用 dnf，不能照抄 CentOS 8 的 module 命令；官方同时提供 Deb Edition，必须先检查 /etc/os-release 和包管理器。官方文档列出 Java 21、MySQL 8.0 与 Nginx 组件，但实际包名、补丁版本与可用仓库以实例查询为准。

- [Alibaba Cloud Linux 4 与 3 的差异](https://help.aliyun.com/en/alinux/getting-started/differences-between-alinux-4-and-3)
- [Alibaba Cloud Linux 4 发布记录](https://help.aliyun.com/en/alinux/product-overview/alibaba-cloud-linux-4-image-release-notes)
- [Spring Boot systemd 部署](https://docs.spring.io/spring-boot/how-to/deployment/installing.html)
- [Nginx 代理配置](https://nginx.org/en/docs/http/ngx_http_proxy_module.html)
- [MySQL 备份](https://dev.mysql.com/doc/refman/8.4/en/mysqldump.html)
