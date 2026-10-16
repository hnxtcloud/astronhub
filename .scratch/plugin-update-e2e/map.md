# 插件管理与多应用更新 E2E Wayfinder

Labels: wayfinder:map
Status: open
Tracker: local-markdown
Planning: ready
Acceptance: not-run

## Destination

由接手的 coding agent 对当前项目执行真实服务 E2E，验证插件管理、软件中心及多应用更新源，交付可复现用例、证据、缺陷和环境阻塞清单。本文建立地图，不表示测试已经执行；原实现票 01–11 保持 done，不与本轮测试票混淆。

## Context pointers

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

完整清单见执行计划。优先提供：真实测试实例/PG16/Redis、测试管理员、专用存储、匹配 Chromium；然后提供内网 Git/npm 及 CA/发布凭据引用、Inspector 与样本；最后提供 Windows/macOS 可重置终端、已装旧版和真实签名新旧发行物。Docker、Helm、故障控制、大文件容量按相应票补齐。缺什么写到环境表和09，不默认从公网补齐。

## Completion

- 每票有实际结果、版本/环境、证据、清理记录；通过/失败/阻塞数量可追溯。
- 新增业务 E2E 测试被实际收集并执行，已有 smoke 不代替新覆盖。
- 缺陷修复后重验受影响流程，代码变更引入的新 HEAD 有对应测试证据。
- 签名、原生安装、首次迁移、差分、全量回退分别判定；无环境不得打勾。
- 地图保持 open，直到必需范围通过；若只完成部分，09明确结论 partial 和下一执行入口。
