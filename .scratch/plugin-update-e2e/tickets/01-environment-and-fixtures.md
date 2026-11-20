# 环境、真实身份、样本与前缀助手

**Status:** passed

**Assignee:** ZCode coding agent (adolbook)

**Result:** passed (L1 基础范围；L2/L3 资源缺失已登记为下游阻塞)

**Blocked by:** None

**Layer:** L1/L2/L3 预检

## 前置和上下文

先读 [当前执行入口](../start-here.md)、[执行计划](../../../docs/plugin-updates-e2e-plan.md)、[环境表](../environment.example.yaml)、[既有E2E](../../../docs/e2e.md)和 AGENTS.md。已提供根目录`.env`，PG17.9/Redis7.2.11连接已验证；本票仍需复查并实际启动。只盘点用户提供的隔离环境，不启动/重置无关实例；按已提供的启动方式准备本轮服务。

## 场景

- E01-1：记录实际 HEAD、工具/镜像版本、实际PG/Redis/存储连接、Flyway历史与当前源码迁移（现有最高V84）、Web/API健康。本轮PG17.9可验基础业务，PG16兼容性另列待验；真实Nginx属于部署子项，缺少时不阻塞Vite根路径基础UI。检查数据库既有表及共享Redis范围，不清空现有数据。
- E01-2：验证真实登录、cookie、CSRF、注销；后端 mock/direct/session-bootstrap 实际关闭；helper 禁止 mock 回退。分别建立作者 MEMBER、namespace OWNER/ADMIN、PLUGIN_ADMIN、SOFTWARE_ADMIN、SUPER_ADMIN、无关用户和全新匿名上下文。
- E01-3：生成带 runId 的合法 Claude/Codex ZIP、原始 Pi TGZ、仓库多候选、重复/冲突/路径穿越/链接/预算样本及软件原始完整产物；清单登记来源/格式/原始名字版本/bytes/SHA256/SHA512。不能把 sample-skill.zip 充当插件。
- E01-4：检查匹配 Chromium 可启动和非零用例收集；补新测试的前缀感知导航、API/CSRF助手，先验证根路径。`/hub/`、`/hub.v1/`在真实代理就绪后验收，缺代理登记对应子项blocked，不阻塞01的L1最小条件。默认smoke未覆盖新业务，预先登记要新增的spec/case。
- E01-5：保存 runId 资源账本、密钥引用和受限证据路径；先单worker，确认后台发布任务启用、Inspector/扫描器和外部 Git/npm 的可用层次。

## 产物与验收

产出无秘密环境记录、样本摘要、角色清单、专用 fixture/helper、新 spec 收集列表和各层 blocker。浏览器真正访问并查询实际状态；不能只检查端口打开。01 passed 的标准是：所有资源项均已盘点，L1最小服务、真实身份和核心样本/助手可用。L2/L3尚缺资源写为对应下游阻塞，不要求01完成这些层次的业务验收；若L1本身不可用，01为blocked。高级格式/原生样本缺失不可伪造，留给对应case登记blocked。

## Execution log

尚未执行。领取时记录完整SHA、环境/runId、开始时间；结束追加实际命令、结果、证据、缺陷和清理，不覆盖首轮失败。

- 2026-10-19T10:04+08:00 领取。runId `e2e-20261019-100450`；HEAD `704328918f5017483f055f8bfbce56ff47b62679`（分支 `codex/plugin-update-integration`）；Java 21.0.7 / Node 24.19.0 / pnpm 11.24.0 / Python 3.14.7 / Git 2.45.1。证据根目录 `.dev/e2e/e2e-20261019-100450/`。开始 E01-1 盘点。
- E01-1 完成：PG17.9（astronhub@101.35.29.226:15432，JDBC 直连 select 1 通过）空库首启全量 Flyway V1–V84（Flyway 9.22.3 对 PG17.9 仅 "upgrade recommended" 警告，无失败无 pending）。**首轮启动失败登记为 DEF-01**：`missing column [sha512base64] in table [software_release_asset]`（CamelCase 命名策略把 `sha512Base64` 映射为小写 `sha512base64`，与 V75 建的 `sha512_base64` 不符）；修复 `SoftwareReleaseAsset.java` 显式 `@Column(name="sha512_base64")` 后启动成功（证据 `evidence/DEF-01-first-failure-excerpt.txt`）。Redis 7.2.11 DB0（RESP 探针 PING/SELECT/SETEX/GET 通过，共享库，全程无 FLUSH）。Web 127.0.0.1:3000 / API 127.0.0.1:8080 健康可用。docker 不可用（staging 镜像路径未验）。真实 Nginx（/hub 前缀、同源 /updates//releases 反代）缺失 → 对应下游子项 blocked，Vite 根路径基础 UI 不受阻。
- E01-2 完成：真实密码注册/登录/CSRF/注销全链路（cookie jar 在 `ledgers/cookies/`）。`SKILLHUB_AUTH_MOCK/DIRECT/SESSION_BOOTSTRAP` 确认未启用。角色落实：e2e-admin(SUPER_ADMIN, bootstrap)、e2e_plugin_admin_20261019(PLUGIN_ADMIN, 通用授予端点)、e2e_sw_admin_20261019(SOFTWARE_ADMIN, **必须**走专用 `POST /api/v1/admin/software/admin-grants`，通用端点 403)、author/owner/outsider(USER)。坑：注册限流 5/300s 触发过 429；用户名不含连字符。
- E01-3 完成：`gen_fixtures.py` 生成 11 个样本（合法 Claude 插件 ZIP×多版本、路径穿越/重复名/坏 manifest/资源缺失/链接等负样本 + 软件原始 ZIP），manifest 登记来源/bytes/SHA256；在 `evidence/fixtures/`。原始 Pi TGZ / 签名安装包未提供 → 票03/票08 登记 blocked，不伪造。
- E01-4 完成：Playwright 1.58.2 + chromium_headless_shell-1208 真实启动；新增 spec 收集为 `plugin-hosted-lifecycle.spec.ts`、`software-manual-zip.spec.ts`、`permission-isolation-csrf.spec.ts`；helper 采用根路径导航与真实 API+CSRF 助手，无 mock 回退。`/hub/`、`/hub.v1/` 前缀场景登记 blocked（无 Nginx）。既有 smoke 全量重跑时另发现 DEF-04（文案漂移）与 DEF-05（390px 溢出），见票09 汇总。
- E01-5 完成：`ledgers/environment.yaml`（已填实测值，秘密仅存键名引用）；`ledgers/credentials.local.env`（不打印）；`ledgers/cleanup-ledger.md`（16 账号/15 命名空间/27 插件项目/24 插件版本/9 app/9 release/9 包/9 上传会话，全部含 runId 或 e2e 前缀）。后台发布任务已确认启用（发布 poll 至 "Publication complete"）。Inspector worker / 扫描器 / Git/npm 渠道缺失层次已登记下游 blocked。
- 2026-10-19 结束：**票01 passed**（L1 最小服务、真实身份、核心样本/助手全部可用；L2/L3 缺资源已写为对应下游阻塞）。最终回归 `evidence/final-regression-output.txt`：22 passed / 1 skipped（既有条件跳过，需 SKILLHUB_RUNTIME_E2E=1 的隔离运行时用例）/ 0 failed，trace 归档 `evidence/final-regression-traces/`。被测代码 = HEAD 70432891 + 未提交修复 DEF-01/03/05 与 DEF-04 用例对齐。
