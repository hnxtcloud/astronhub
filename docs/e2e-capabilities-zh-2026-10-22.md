# Plugin、Skill、MCP 中文 E2E 验收报告（2026-10-22）

日期：2026-10-22，Asia/Shanghai。结论：**中文基础业务验收通过（15 组：14 passed / 1 failed / 0 skipped）**；唯一失败为既有未修复缺陷 CAP-01，无新增缺陷。

本轮是 [2026-10-21 报告](e2e-capabilities-zh-2026-10-21.md) 的复验：在相同被测版本上重跑插件 A–D、MCP/Skill 全部中文用例，并补跑软件手工 ZIP 场景 E。CAP-02（MCP 详情与连接配置）、CAP-03（扫描后审核页自动恢复）在本轮真实浏览器中再次确认已修复。

## 环境与方法

- Windows，Chromium（Playwright 1.58.2），浏览器与站点语言 `zh`（`i18nextLng=zh`，locale `zh-CN`）。
- 浏览器 → Vite `http://127.0.0.1:3000` → Spring Boot `:8080` → PostgreSQL 17.9 / Redis 7.2.11 / LocalFile（存储路径沿用 runId `e2e-20261019-100450`）。
- 被测 HEAD `704328918f5017483f055f8bfbce56ff47b62679`（分支 `codex/plugin-update-integration`）+ 工作区未提交改动；后端 JAR SHA256 `08f490169f5d3176eb2ca4c97eab907932d886c748c7281532d52df2157d66f1`（2026-10-21 14:46 构建，含 CAP-02/03 修复；构建时间晚于全部后端源码修改，无陈旧二进制）。
- `SKILLHUB_AUTH_MOCK_ENABLED`、`DIRECT_ENABLED`、`SESSION_BOOTSTRAP_ENABLED` 均为 false；执行前以 `X-Mock-User-Id: local-admin` 匿名请求实测返回 401。
- Cisco Scanner 2.1.0 运行于 `:8000`，后端启用真实扫描（独立 Redis Stream `astronhub:capabilities-zh-20261021:scan:requests`）；S02 公开技能审计实测 `SAFE`。
- 真实密码登录，作者/外部用户/超级管理员三会话隔离；单 worker、retries=0、trace=on；无业务 mock、无响应拦截。
- 技能样本 ZIP 按 10-21 报告要求重新生成了唯一 slug（`zh-private-1022180445`、`zh-public-1022180445`、`zh-scan-fixed-1022180445`），文件内容与原样本一致；MCP 沿用既有 fixtures（前缀 `capabilities-zh-20261021-130000`，6 条目录状态）。

## 场景结果

| 执行 | 场景 | 结果 |
| --- | --- | --- |
| [capabilities-zh-20261022-plugins](../.dev/capability-acceptance/capabilities-zh-20261022-plugins/) | A 登录与注销；B 插件上传审核/下载 SHA256/安装指引；C 驳回后新版本发布；D 独立身份隔离与托管治理 | 4 passed（61 步，3.1 分钟） |
| [capabilities-zh-20261022-mcp-skill](../.dev/capability-acceptance/capabilities-zh-20261022-mcp-skill/) | M01–M03、M05 MCP 中文搜索/隔离/匿名边界/详情与剪贴板复制；S01–S05 私有/公开 Skill、CSRF、非法包、扫描时序 | 9 passed / 1 failed（M04，见 CAP-01） |
| [capabilities-zh-20261022-software](../.dev/capability-acceptance/capabilities-zh-20261022-software/) | E 软件手工 ZIP 上传、识别确认、发布、真实下载 SHA256 | 1 passed（27 步，53 秒） |

要点复验：

- **CAP-02 修复有效**（M05）：MCP 中文详情页、连接配置模板、真实剪贴板复制（`navigator.clipboard.readText()` 读回校验 JSON）全部通过。
- **CAP-03 修复有效**（S05）：本轮真实命中"扫描中打开审核页"时序（`early-open-security-audit.json`，reviewId=7）；扫描完成后原页面审批按钮自动解除禁用，无需刷新。
- 私有/公开 Skill 下载包逐文件内容断言通过（含 `资料/中文说明.md` 中文内容与中文路径）；外部用户下载隔离 403/404。
- 无 CSRF 写请求 403，携带有效 CSRF 后不再因 CSRF 拒绝（S03）。

## 唯一失败：CAP-01（既有，P2）· 完整 MCP 坐标搜索无结果

搜索框提示"搜索能力名称、坐标或描述"，但查询 `mcp:@personal-3364bf77/capabilities-zh-20261021-130000-public` 返回空列表，页面显示"当前筛选下还没有已发布条目。"。当前查询谓词仍只匹配 `displayName`、`slug`、`summary`（见 [JpaCapabilityCatalogQueryRepository](../server/skillhub-app/src/main/java/com/iflytek/skillhub/repository/JpaCapabilityCatalogQueryRepository.java) 的 keyword 子句），未实现完整坐标匹配。本轮按用户此前决策范围（10-21 仅指定修复 CAP-02/03）未修改该缺陷，仅复核留证：M04 截图、error-context、trace 见 `capabilities-zh-20261022-mcp-skill/test-results/`。

## 范围与未覆盖项

- 本轮未覆盖（与 10-21 报告一致）：Git/npm 原生渠道、真实客户端安装、MCP 提交/审核/发布管理与协议网关、Nginx/子路径/S3、PG16、BAIZE 更新源（该场景 2026-10-20 由 [manual-acceptance-results](../.scratch/plugin-update-e2e/manual-acceptance-results.md) 覆盖，本轮未重跑 F）。
- Wayfinder（`.scratch/plugin-update-e2e/map.md`）结论不变：L1 基础业务持续通过，L2/L3 环境受限场景仍为 blocked/partial。
- 未修改产品代码与生成 API 类型；未创建提交或 PR。测试数据以本轮 runId/slug 前缀保留待查；三个服务（Web 3000 / API 8080 / Scanner 8000）本轮结束后保持运行。

## 复现方式

服务未启动时：`powershell -File .dev/capability-acceptance/start-scanner.ps1`、`start-backend.ps1`（capablity-acceptance 目录，加载根目录 `.env`）、`.dev/manual-acceptance/start-web.ps1`。测试执行（项目根目录）：

```powershell
python -X utf8 .dev/manual-acceptance/run-capabilities.py e2e/manual-acceptance.spec.ts --grep '插件上传|驳回后|独立身份|登录与注销'
python -X utf8 .dev/manual-acceptance/run-capabilities.py e2e/capabilities-zh.spec.ts
python -X utf8 .dev/manual-acceptance/run-capabilities.py e2e/manual-acceptance.spec.ts --grep '软件手工ZIP'
```

重跑前须重新生成唯一 slug 技能包（样本内 `name: zh-*-<MMDDHHMMSS>`），不得复用已发布同名同版本包；插件/软件用例按 runId 自动命名无冲突。
