# Plugin、Skill、MCP 中文 E2E 验收报告

日期：2026-10-21，Asia/Shanghai。结论：**部分通过，尚不满足完整验收**。

同日后续修复：用户指定的 CAP-02、CAP-03 已完成并复验通过，见文末“指定问题修复复验”。以下 14 组统计保留首次验收结果；CAP-01 坐标搜索未纳入本次修复。

最终选取的 14 组场景：**11 passed / 3 failed / 0 skipped**。插件另有逐步账本，61/61 步通过。此计数合并三次互不重复的最终执行：插件 A–D、新增 M01–M05 / S01–S04、专项 S05；不是声称单次全量套件通过。准备阶段、脚本调整与先前失败记录均保留。

## 环境与方法

- Windows，Chromium 145.0.7632.6，浏览器 `zh-CN`，站点语言 `zh`。
- 浏览器 → 本地 Vite `http://127.0.0.1:3000` → Spring Boot `:8080` → PostgreSQL 17.9 / Redis 7.2.11 / LocalFile。
- Flyway V84，启动时验证 84 条迁移，无新增迁移。HEAD `704328918f5017483f055f8bfbce56ff47b62679` 加工作区原有未提交改动。
- 后端使用现有 JAR，SHA256 `7b277e95f016c2871ef70367c41001ea6f8f33545ab6200699e04f2ade37eca5`。本轮未修改产品代码或生成的 API 类型。
- 使用真实密码登录及独立作者、审核员、外部用户会话。mock、direct、session-bootstrap 均关闭；匿名携带 mock 管理员头仍返回 401。
- 无业务 API mock、无响应拦截。单 worker、retries=0，保留截图、JSON、trace 和下载文件。
- 最初扫描器关闭；随后在独立 Python 3.11 环境安装并启动仓库指定的 Cisco Scanner 2.1.0，后端启用扫描器。真实 `/scan-upload` 返回 200；公开 Skill 审计为 SAFE，2 个 LOW 提示。启用静态及行为分析，不使用 LLM、外部 AI Defense 或 VirusTotal。
- 扫描使用本轮独立 Redis Stream；未清空共享 PostgreSQL 或 Redis。内置技能自动同步在扫描器启用后的实例中关闭。

## 最终场景结果

| 场景 | 结果 | 实际覆盖 |
| --- | --- | --- |
| A 登录与注销 | passed | 中文登录、注销、受限入口跳转 |
| B 插件上传审核 | passed | 中文名称、PRIVATE 项目、ZIP 导入、独立审核、HOSTED 发布、原包 SHA256、安装说明、包文件 |
| C 插件驳回与新版本 | passed | 驳回不可重提原版本；创建并实际发布修正版 |
| D 插件权限及治理 | passed | 外部用户拒绝详情/下载，角色隔离，隐藏/恢复、归档/恢复、版本下架 |
| M01 MCP 所有者中文搜索 | passed | 中文名称、描述、传输方式；公开/私有/命名空间可见条目；不存在查询 |
| M02 MCP 外部用户隔离 | passed | 仅公开条目；私有、命名空间、隐藏、归档、草稿详情均 404 |
| M03 MCP 匿名边界 | passed | 匿名浏览公开条目；mock 身份头不能建立登录态 |
| M04 MCP 完整坐标搜索 | **failed** | 有效完整坐标被搜索为空，见 CAP-01 |
| M05 MCP 连接说明入口 | **failed** | 卡片无详情/复制配置入口，见 CAP-02 |
| S01 私有 Skill | passed | 中文 ZIP 文件名、真实扫描、确认发布、中文说明、包内中文路径、浏览器下载、跨用户拒绝下载 |
| S02 公开 Skill | passed | 上传 → 真实 SAFE 扫描 → 中文审核 → 目录搜索 → 下载，本次无需刷新即可完成 |
| S03 Skill CSRF | passed | 无 CSRF 写请求 403；相同登录会话携带有效 CSRF 后不再被 CSRF 拒绝 |
| S04 非法 Skill ZIP | passed | 缺少 SKILL.md，后端 400，中文页面提示发布失败并恢复按钮 |
| S05 扫描完成后的审核页 | **failed** | 扫描中打开页面，API 已 SAFE，原页审批按钮仍禁用；重新打开恢复，见 CAP-03 |

私有和公开 Skill 的下载 ZIP 均解压逐文件比对：`SKILL.md`、`README.md`、`资料/中文说明.md` 内容与上传文件完全一致。私有包还逐项验证了服务端文件清单 SHA256。不能将服务端重打包后的整个 ZIP 摘要直接等同于上传 ZIP 摘要。

## 缺陷与功能缺口

### CAP-01 · P2 · 完整 MCP 坐标搜索无结果

1. 登录后打开 `/mcp-servers`。
2. 搜索 `中文验收知识库-public`，能找到测试条目。
3. 改搜 `mcp:@personal-3364bf77/capabilities-zh-20261021-130000-public`。
4. 页面显示“当前筛选下还没有已发布条目。”

搜索框明确提示支持“名称、坐标或描述”。当前查询只匹配 displayName、slug、summary，没有完整坐标匹配。

关联：[查询实现](../server/skillhub-app/src/main/java/com/iflytek/skillhub/repository/JpaCapabilityCatalogQueryRepository.java)、[M04 用例](../web/e2e/capabilities-zh.spec.ts)。证据见主验收目录中的 M04 screenshot、error-context、trace。

### CAP-02 · P1 · MCP 只能展示摘要，无法取得连接说明

公开 MCP 卡片只展示坐标、名称、摘要、修订号、传输方式和复制计数。卡片没有链接或按钮，复制图标也只是静态图标；用户无法打开详情或复制 Connection Profile。只读详情 API 返回通用目录项，也未返回连接模板。

这是功能缺口。设计要求 MCP Catalog 解释如何配置服务，当前尚未完成这一用户闭环。MCP 管理页面及提交/审核接口也未实现，未将它们计为通过。

关联：[MCP 卡片](../web/src/entities/mcp-server/mcp-server-card.tsx)、[只读 Controller](../server/skillhub-app/src/main/java/com/iflytek/skillhub/controller/portal/McpCatalogController.java)、[设计文档](25-astronhub-capability-platform-design.md)。证据见主验收目录中的 M05 screenshot、trace。

### CAP-03 · P2 · 扫描结束后审核页不自动恢复

先建立审核员会话，再上传公开 Skill，立即打开扫描中的审核详情。页面显示扫描中提示、审批按钮禁用。实际扫描完成后，审计 API 返回 SAFE 和非空 scannedAt，原页面等待 15 秒后按钮仍禁用。

专项资源：`zh-scanning-1021131203`，reviewId=3，skillId=6。重新打开同一审核页后按钮可用、扫描提示消失；未通过改数据库状态取得结果。

关联：[审核详情查询](../web/src/features/review/use-review-detail.ts)、[审核按钮状态](../web/src/pages/dashboard/review-detail.tsx)、[安全审计查询](../web/src/features/security-audit/use-security-audit.ts)。查询未在扫描期间轮询或接收完成后的失效通知，页面保留原 SCANNING 数据。

证据：专项目录的 `early-open-security-audit.json`、`扫描SAFE后审核页面` 附件及 trace；重新打开后的 `review-reopened.json` 和 `review-reopened.png`。

## 证据与复现

本机证据保存在被 Git 忽略的 `.dev/capability-acceptance/`，原始 trace 可能包含登录请求，不作为公开报告附件。

| 最终执行 | 目录 | 结果 |
| --- | --- | --- |
| 插件 A–D | [capabilities-zh-20261021-125613](../.dev/capability-acceptance/capabilities-zh-20261021-125613/) | 4 passed；61 步 passed |
| MCP / Skill 主验收 | [capabilities-zh-20261021-130830](../.dev/capability-acceptance/capabilities-zh-20261021-130830/) | 7 passed / 2 failed |
| 扫描时序专项 | [capabilities-zh-20261021-131204](../.dev/capability-acceptance/capabilities-zh-20261021-131204/) | 1 failed；重新打开的恢复验证通过 |

每个目录包含 `run.json`、`execution.log`、`playwright.json`、`html/index.html` 和测试附件。主验收另有 `download-verification.json`、`public-skill-security-audit.json`、`public-skill-workflow-complete.json`。

测试源码：[既有插件用例](../web/e2e/manual-acceptance.spec.ts)、[新增中文用例](../web/e2e/capabilities-zh.spec.ts)。非零收集证据：[14 组用例清单](../.dev/capability-acceptance/collected-tests.txt)。新增用例 ESLint、单独严格 TypeScript 检查通过；前端现有 `tsc --noEmit` 通过。未改后端代码，未执行 Maven 回归或 staging；本机无 Docker 命令。

本机执行器自动从既有受限凭据文件注入密码，不在命令行和报告中展开秘密。以下命令在项目根目录执行。Skill ZIP 使用唯一 slug，重跑前须重新生成唯一包；不要直接重用已发布的同名同版本包。

```powershell
python -X utf8 .dev/manual-acceptance/run-capabilities.py e2e/manual-acceptance.spec.ts --grep '插件上传|驳回后|独立身份|登录与注销'
python -X utf8 .dev/manual-acceptance/run-capabilities.py e2e/capabilities-zh.spec.ts
```

新增用例使用 `E2E_AUTHOR_*`、`E2E_OUTSIDER_*`、`E2E_SUPER_ADMIN_*` 凭据及 `E2E_MCP_FIXTURE_PREFIX`、`E2E_MCP_NAMESPACE`、`E2E_SKILL_FIXTURE_DIR` 可选覆盖。当前默认指向本机准备的数据，不是开箱即用的 CI 套件。

## 范围与保留资源

- 插件 `capabilities-zh-20261021-125613-plugin` 位于 `personal-3364bf77`。0.1.0 发布；0.1.1 驳回；0.1.2 发布后经治理下架。
- MCP 测试前无条目。使用独立 SQL fixture 准备六种目录状态，entry/revision ID 为 1–6、前缀 `capabilities-zh-20261021-130000`。**只计为目录读取及访问控制验收，不计为 MCP 创建、审核或发布通过。** Fixture 脚本、ID 账本位于本地证据目录。
- Skill 及专项资源均为 `zh-*` 测试前缀，保留供复查；各次执行目录记录坐标。服务目前保留运行，Web 3000 / API 8080 / Scanner 8000。
- 本轮验证 HOSTED 插件分发与安装说明，没有在 Claude Code/Codex/Pi 中执行实际安装或运行；没有验收 Git/npm 原生渠道、Nginx/子路径、S3、PG16 或移动端。
- MCP 是目录产品，本轮未验收实际 MCP 协议握手、tools/list、tools/call 或网关；当前实现尚不提供这些运行能力。
- 本轮仅新增中文验收用例和本报告；保留用户已有未提交改动，未创建提交或 PR。

## 指定问题修复复验（同日后续）

用户指定修复 CAP-02（MCP 详情和连接配置入口）、CAP-03（扫描结束后审核按钮不恢复）。这两项已修复。

### 根因与实现

- CAP-03：审核详情与安全审计查询没有在扫描过程中持续重查，后台已完成时页面仍持有 SCANNING / 待扫描审计数据。现在审核详情仅在当前审核版本 SCANNING 时每 2 秒重查；审计记录仅在未完成且无失败原因时重查。完成或失败后停止。历史扫描版本不会让当前审核持续轮询。
- CAP-02：目录卡片没有链接，原详情 API 只有目录摘要。现增加中文详情页 `/mcp-servers/{namespace}/{slug}`，展示已发布修订说明、连接模板和变量定义，并支持真实剪贴板复制。STDIO、Streamable HTTP、旧版 SSE 生成通用 `mcpServers` JSON 模板；CUSTOM 和格式不完整的模板提供手工配置提示。
- 新增 `GET /api/v1/mcp-servers/{namespace}/{slug}/connection-profiles`，原摘要接口保持兼容。配置读取与原目录共用可见性、隐藏、归档和已发布修订过滤；不会读取新草稿的模板。敏感变量的默认值不返回，复制时保留 `${VARIABLE}` 占位符。
- 已重新构建并启动后端，通过 `pnpm run generate-api` 重新生成前端类型，未手工修改生成文件。

### 红绿验证

| 检查 | 修复前 | 修复后 |
| --- | --- | --- |
| 原始 M05 / S05 中文 E2E | 2 failed | 强化 M05 为进入详情并校验真实剪贴板 JSON；与 S05 一同通过 |
| 真实 TanStack Query 扫描回归 | 状态一直 SCANNING；审计一直 SUSPICIOUS | 正常转换并停止轮询 |
| 后端目录集成与认证路由 | 新公开配置路径未放行，返回 401 | 39 tests passed（认证路由 33，目录集成 6） |
| 前端相关单元/组件回归 | 保留首轮失败记录 | 33 tests passed |
| 中文真实服务 E2E | 保留原始失败截图和 trace | M01、M02、M03、M05、S05 共 5 passed / 0 failed / 0 skipped |
| 前端完整 TypeScript / ESLint | — | passed |

修复前浏览器证据：[capabilities-zh-20261021-142305](../.dev/capability-acceptance/capabilities-zh-20261021-142305/)。修复后浏览器证据：[capabilities-zh-20261021-145054](../.dev/capability-acceptance/capabilities-zh-20261021-145054/)。后者包含中文详情截图、实际扫描 SAFE 审计、审核按钮恢复截图及 trace。复制内容通过浏览器 `navigator.clipboard.readText()` 实际读回比较，没有伪造剪贴板或业务响应。

后端命令：`mvnw.cmd -pl skillhub-app -am test -Dtest=CapabilityCatalogIntegrationTest,RouteSecurityPolicyRegistryTest -Dsurefire.failIfNoSpecifiedTests=false`。前端命令及输出在本地证据目录的 `mcp-fix-web-tests.log`、`mcp-fix-backend-tests.log`、`mcp-fix-typecheck.log`、`web-lint.log`、`mcp-fix-generate-api.log`。后端测试数据首次因 H2 created_at 无默认值失败，已补正后复跑；该次准备失败未计为产品缺陷已修复的证据。

本次没有修改 CAP-01 完整坐标搜索，也没有增加 MCP 提交/审核/发布管理或网关运行功能。未执行全量后端测试、Docker staging 或真实 MCP 服务连接，结论限于上述修复及验证范围。现有未提交工作已保留，未创建提交或 PR。
