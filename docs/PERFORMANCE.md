# 静态资源与公网性能

更新：2026-11-01。本次优化同时覆盖本地生产 JAR 和云端，不调整页面布局、会员权限、文章或数据库结构。

## 本地与应用层

- Vite 构建生成的 `/assets/**` 使用 `public, max-age=31536000, immutable`。这些 URL 含内容指纹，重新构建后 URL 随内容变化。
- 固定名称的 `/brand/**` 和 favicon 缓存一天并支持条件请求；入口 HTML 仍保持不可存储，防止持续加载旧版本入口。
- 只为公开静态资源补充 HEAD 权限；账号接口、成员接口和媒体访问控制保留原规则。
- Spring Boot 压缩大于 1KB 的 CSS、JavaScript、SVG。认证 JSON、验证码响应和未发布媒体不加入静态缓存或压缩规则。
- `npm run build` 在 prebuild 中通过开发依赖 sharp 自动生成三张 WebP 品牌缩略图。原文件保留作为来源和旧链接兼容；构建脚本固定尺寸、质量与依赖版本，不需要在线图片服务。
- 页脚图片采用懒加载和异步解码，继续沿用原有样式与裁切。

本地开发用的 Vite 热更新服务器保持开发缓存行为。正式本地预览以 `Start.cmd` 启动的生产 JAR 为验收对象。

## Linux / Nginx

1. 将 `deploy/linux/haiyan-performance.conf` 放到 `/etc/nginx/`，在 Nginx 的 http 上下文 include；仅压缩静态文本，level 4。
2. 将 `deploy/linux/haiyan-static.inc` 放到 `/etc/nginx/`，在每个 HTTPS server 中 include。该文件从 `/var/www/haiyan` 读取 assets、brand 和 favicon，不代理给 Java，不缓存 API 或用户上传文件。
3. 发布时只把经校验的前端静态资源复制到 `/var/www/haiyan/{assets,brand}` 和 favicon；目录归 root 管理、Nginx 可读，禁止在这个目录存放数据库、配置或用户图片。保留上一版本的指纹资源，避免已打开页面的懒加载 chunk 失效；后续按发布清单清理旧版本，不无限累积。
4. Nginx 1.25.1 及以后，在每个 TLS server 中设置 `http2 on;`；保留现有证书、HTTPS 跳转和自动续期配置。模板要求带 HTTP/2 模块的 Nginx，部署前用 `nginx -V` 检查。
5. 所有修改先执行 `nginx -t`，成功后 reload；JAR 更新期间短暂重启应用，更新前备份数据库并保留上一版程序和配置，可在失败时回退。

静态响应不设置 Cookie。哈希 JS/CSS 和品牌图可公开缓存；登录、会话、验证码、作者私有内容及 `/api/media/**` 始终经应用权限校验，不配置共享缓存。

## 验收

- 本地和公网 GET/HEAD 静态文件返回正确的 Cache-Control，条件请求返回 304，缺失文件不返回一年缓存；入口与身份 API 仍包含 no-store。
- 携带 Accept-Encoding: gzip 的脚本和样式返回 gzip，内容解压后与构建文件完全一致。
- TLS ALPN 可协商 h2；浏览器支持时使用 HTTP/2。普通本地明文 HTTP 不强制 HTTP/2。
- 抽查已有合集、正文、注册模式、登录/退出、记住设备和快捷登录，确认数据库及图片目录不被代码部署覆盖。
- 传输字节和缓存命中是优化结果指标；跨境线路和用户代理仍影响连接延迟，不把单次总耗时当作所有用户的速度保证。

## 官方参考

- [Spring MVC 静态资源](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-config/static-resources.html)
- [Spring Boot 压缩配置](https://docs.spring.io/spring-boot/how-to/webserver.html)
- [Nginx gzip](https://nginx.org/en/docs/http/ngx_http_gzip_module.html)
- [Nginx HTTP/2](https://nginx.org/en/docs/http/ngx_http_v2_module.html)
