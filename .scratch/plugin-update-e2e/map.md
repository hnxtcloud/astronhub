# 插件管理与多应用更新 E2E Wayfinder

Labels: wayfinder:map
Status: open
Tracker: local-markdown
Planning: ready
Acceptance: partial（L1 基础业务通过；L2/L3 缺环境 blocked，见 09 报告）

## 清单复验（2026-10-20）

- [本轮验收报告](manual-acceptance-results.md)：runId `manual-20261020-110313`，7组真实浏览器场景通过，127/127编号步骤通过，0失败、0跳过；另通过BAIZE渠道从1.0.0显式切换到1.1.0的扩展验证。
- 本轮覆盖中文UI、插件审核/驳回后新版本发布、下载SHA256、固定安装指引、私有访问隔离、隐藏/归档/下架、软件手工ZIP和BAIZE匿名更新源。修复了发布任务成功但版本状态尚未提交时页面停止轮询的问题，保留红绿单测和真实E2E证据。
- 测试数据保留，6个本轮及调试轮应用的匿名更新源均已关闭并核对；服务保持运行。
- 本次清单通过不关闭原全量地图。原生Git/npm、GitLab/Electron真实客户端、签名/安装/差分、扫描器、Inspector、Nginx/S3/子路径及PG16仍需对应验收。

## Run status（2026-10-19，runId `e2e-20261019-100450`）

- 01 passed（盘点完成，L1 最小环境可用）；02/04/05 partial（各自 L1 子范围通过，深度子项 blocked）；09 基础报告已写（结论 partial）。
- 缺陷：DEF-01（实体列映射，已修复复验）、DEF-03（包列表恒空，已修复复验）、DEF-04（既有 smoke 文案漂移，已对齐）、DEF-05（落地页 390px 溢出，已修复复验）；DEF-02（扫描器缺失）为环境阻塞非代码缺陷。
- 最终回归：22 passed / 1 skipped（既有条件跳过）/ 0 failed —— 证据 `.dev/e2e/e2e-20261019-100450/evidence/final-regression-output.txt`。
- 下一执行入口：补齐环境后重领 03（内网 Git/npm）、04 深度（Inspector/Nginx/S3/真实安装包）、06（故障注入）、08（原生终端）；PG16 兼容性另列。

## Destination

由接手的 coding agent 对当前项目执行真实服务 E2E，验证插件管理、软件中心及多应用更新源，交付可复现用例、证据、缺陷和环境阻塞清单。实际已执行范围以本页运行记录及对应报告为准；原实现票01–11保持done，不与本轮测试票混淆。

## Context pointers

- [手动验收步骤（2026-10-20，ASD-STE100风格）](manual-acceptance.md)：本地启动入口、测试账号引用、专用样本及逐步预期结果。
- [清单验收结果（2026-10-20）](manual-acceptance-results.md)：7组/127步、版本切换、轮询缺陷修复及原始证据入口。
- **先读：[当前环境与执行入口](start-here.md)**。已提供根目录 `.env`；PG17.9 和 Redis7.2.11 连接已验证，先验收基础业务，再按资源推进扩展层。
- [执行计划、环境清单和可复制交接指令](../../docs/plugin-updates-e2e-plan.md)
- [环境盘点表](environment.example.yaml)（手工交接格式，尚无读取它的 runner）
- [执行报告模板](report-template.md)
- [插件规格](../../docs/27-plugin-management-implementation-plan.md)、[更新规格](../../docs/28-software-release-update-channels.md)、[已有验收边界](../../docs/plugin-updates-validation.md)
- [既有 Playwright 约定](../../docs/e2e.md)、[软件中心部署](../../docs/26-software-center-deployment.md)、[机器发版](../../docs/software-machine-publishing.md)

## Execution rules

1. 先记录实际 HEAD、版本、部署地址和隔离范围；领取 ready-for-agent 且相关依赖场景已通过的票，改为 claimed、填 Assignee。首次执行先做 01；09 可以同步初始化报告。
2. Status 使用 ready-for-agent / claimed / blocked / done。Result 使用 not-run / passed / failed / blocked / partial。业务票02–08只有该票范围全部实际通过才设 done+passed；失败保留 claimed+failed，缺环境设 blocked，混合结果写 partial 并逐 case 分类。01的passed标准是完成盘点且L1最小环境可用；09的done只表示结果已完整汇总，Result仍可partial/failed。
3. 依赖票 blocked/failed 时，不把下游当通过；可按依赖场景已通过的子范围继续，并保留整票partial，或继续无依赖的其他分支。有新增环境后可重领 blocked 票，保留旧记录。09 可在有阻塞时形成 partial 报告，但地图仍 open；所有要求的验收通过后才 resolved，不能由报告完成代替验收完成。
4. L1/L2/L3 各自结论独立。真实业务/鉴权不可用 page.route/MockBean/mock header 伪造；允许专用代理实施已记录的断流/延迟故障，不允许伪造成功响应。已有 unit/HTTP/本机原生探针不算本轮通过。
5. 先串行、单浏览器 worker，避免共享数据和资源争用。需要并行时由执行者先建立独立账号、命名空间、应用、npm scope、Git 分支和产物目录，资源不足则继续串行。
6. 每个场景保存实际输入摘要、UI/API/外部目标证据和结果；不将秘密放入 git 或公开报告。只清理本 runId 创建并登记的资源，不重置用户现有服务/数据。
7. 发现缺陷做最小修复和回归，保留首个失败；不靠跳过、放宽权限、改写包或迁移历史造绿。原始两个客户端仓库维持只读；原生安装只在指定可重置测试终端执行。
8. 本轮不授权公网同步、生产发布、推送或 PR。执行脚本/样本允许落在本轮专用目录；代码和生成契约遵守 AGENTS.md 及仓库技能。

## Task graph

| ID | 任务 | Blocked by | 层次 |
| --- | --- | --- | --- |
| [01](tickets/01-environment-and-fixtures.md) | 环境、真实身份、样本与前缀助手 | None | L1/L2/L3 盘点 |
| [02](tickets/02-plugin-lifecycle.md) | 插件导入、审核、托管及治理 UI | 01 | L1 |
| [03](tickets/03-native-channels.md) | Git/npm 发布与独立依赖 | 02 | L2 |
| [04](tickets/04-software-and-updates.md) | 软件上传/识别和多应用更新源 | 01 | L1/L2 |
| [05](tickets/05-permissions-and-machine.md) | 多身份、CSRF、隔离及机器发版 | 02,04 | L1/L2 |
| [06](tickets/06-recovery-and-faults.md) | 续租、重启、未知结果、修订及撤回 | 03,05 | L2 |
| [07](tickets/07-proxy-storage-and-load.md) | 前缀、真实代理、S3及大传输 | 04,05 | L2 |
| [08](tickets/08-native-clients.md) | CLI原生安装与应用真实更新 | 03,04,05 | L3 |
| [09](tickets/09-regression-and-report.md) | 既有回归、证据汇总、缺陷复验 | None（最终登记01–08） | 汇总 |

初始 frontier：01；09 只可先建报告，不宣称验收完成。02 与04可在01通过后独立推进；03/06缺渠道环境不阻断已具备环境的04/07。

## Environment to provide

当前已提供 PG17.9（库 `astronhub`）及 Redis7.2.11 的 `.env`，连接验证通过；实际迁移、服务启动、真实账号和浏览器仍由01检查。PG16兼容性另列待验，不能阻断当前PG17环境的基础验收。按 [start-here](start-here.md) 先做01→02/04基础子项→05权限子项→09基础报告；混合票按子项保留partial。其余环境需求见执行计划：内网Git/npm、CA/凭据引用、Inspector、扫描器、Nginx/S3、原生终端和发行物。缺资源只阻塞相应case，不默认从公网补齐。

## Completion

- 每票有实际结果、版本/环境、证据、清理记录；通过/失败/阻塞数量可追溯。
- 新增业务 E2E 测试被实际收集并执行，已有 smoke 不代替新覆盖。
- 缺陷修复后重验受影响流程，代码变更引入的新 HEAD 有对应测试证据。
- 签名、原生安装、首次迁移、差分、全量回退分别判定；无环境不得打勾。
- 地图保持 open，直到必需范围通过；若只完成部分，09明确结论 partial 和下一执行入口。
