# 按应用匿名的 GitLab 更新读取

**What to build:** 已开启应用通过兼容 tags/raw 和固定资产 URL 提供完整已发布文件，GET/HEAD/Range 流式读取。

**Blocked by:** 07

**Status:** claimed

**Handoff:** ready-for-merge（合并代理复核后关闭）

**Assignee:** software_ticket07_resume / codex/gitlab-update-reads

**Proxy support:** plugin_ticket02 / codex/update-proxy-routing（先合入主实施分支，再整体集成）

**Spec coverage:** U3、U4

- [x] 未开启、未发布、缺资产、下架拒绝；旧 token 不赋予管理权限；应用/渠道隔离
- [x] 编码 project/file 真实容器验证；防火墙不全局放开；权限不扩大原软件接口

## Evidence

- 实际嵌入 Tomcat 的根路径和 `/hub` Servlet context 各 8 项 HTTP 行为通过，加日志流式边界 9 项，共 25 项；日志 `.scratch-software-gitlab-complete.log`。覆盖目标切换/清空、精确历史 ref、跨应用/草稿拒绝、所有治理检查先于 304/HEAD/Range、旧 token 无管理权限及严格编码路径拒绝。
- `encodedSolidusHandling=passthrough` 保留原始 project/file 编码；仅精确匹配 GET/HEAD GitLab tags/raw 的请求使用允许编码斜杠的独立严格防火墙，普通路径仍保留默认检查。没有全局放开编码百分号、点、反斜杠或双斜杠。
- 原始字节保持流式传输；SHA256 ETag、Last-Modified、no-store、单区间及有界 multipart。If-Range 不匹配先退回完整 200；最多 16 区间、多区间 32 MiB，超限忽略 Range。请求日志避免缓存整个更新体，保留普通 API 行为。
- 实际 HTTP multipart fixture 经原客户端已安装 electron-updater 6.8.9 的 DataSplitter 接受，得到精确 `0189`，日志 `.scratch-software-native-multipart.log`；仅证明解析器契约，不证明完整差分或回退。
- 代理子任务已合入本分支（`d342721f` / `dc2184d7`）；实际 Nginx 1.28 在根、`/hub/`、`/hub.v1/` 通过 48 项，日志 `.scratch-software-proxy-complete.log`。另有 Vite 前缀 22 项和 shell 路由通过。大上传仅检查请求头准入，未传大实体；Helm 渲染及 Docker staging 未运行。
- 最终正常 reactor `.scratch-software-gitlab-final.log` 通过 59 项（auth 33 + app 26，包括真实 Springdoc 导出）；补充响应体 OpenAPI 元数据后的同组 `.scratch-software-gitlab-contract.log` 也通过 59 项，均 0 failures/errors。先前正常 reactor 的路由策略覆盖不变量将 `/api/v4` 误认为 token-filter 表面；已改为与实际 `/api/v1/`、`/api/web/`、`/api/cli/` 一致。未添加运行时 token 权限或错误声明为 session-only。
- PostgreSQL/Flyway、真实 S3、原生 Windows/macOS 安装、签名、完整差分及全量回退继续在票 11 独立验收；未修改客户端仓库。
- 最终路径/响应体注解后的实际 Springdoc 导出 `.scratch-software-gitlab-openapi.log` 单项通过；导出为 401 paths / 433 schemas，已逐项检查 tags 裸数组、binary 响应和所有模板路径必填参数，再以 openapi-typescript 重新生成 SDK，未手改生成文件。
- 生成契约后的全前端 TypeScript、代理前缀配置/测试文件定向 ESLint、`git diff --check` 通过，日志 `.scratch-software-gitlab-typecheck.log`、`.scratch-software-gitlab-lint.log`。
- 功能提交 `0faf8753`；已在 `0ea658fc` 合入最新集成 `a3bff27f`（包含票 04），无冲突；合并后全前端 TypeScript 再次通过，日志 `.scratch-software-gitlab-merged-typecheck.log`。生成契约自动合并保留双方新增接口；最终联合 Springdoc 重导出由集成票统一核对。
