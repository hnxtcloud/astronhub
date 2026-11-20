# 身份、CSRF、隔离与机器发版

**Status:** claimed

**Assignee:** ZCode coding agent (adolbook)

**Result:** partial — 已有真实身份/CSRF/隔离子范围证据；2026-10-20清单补验真实UI登录注销、私有详情与下载隔离、普通用户软件管理拒绝及匿名更新源开关。机器凭据/发版工具、完整跨对象矩阵等仍未执行，不能将整票标为passed；未执行本身不意味着必须真实多机才能测。

**Blocked by:** 02,04

**Layer:** L1/L2

## 前置和上下文

[机器发版](../../../docs/software-machine-publishing.md)。依赖作者/审核者/软件管理员/无关用户及两个应用/namespace；真实session与CSRF，匿名上下文无旧cookie/token。为本轮应用由管理员实际签发机器token。

## 场景

- E05-1：作者不能审批自己无权审批的内容；PLUGIN_ADMIN不能借审核角色编辑他人私有包/组件或管理软件；SOFTWARE_ADMIN不能管理无关插件；普通用户不能管理应用、伪造静态证据或指定存储key。
- E05-2：跨namespace/app/release/upload/package/snapshot/component的nested ID逐项拒绝，不产生部分写入；匿名/受限文件不泄漏元信息；冻结namespace的阻断与仅限制性治理边界按规格。
- E05-3：管理写请求缺/错CSRF被拒绝；合法session写入成功；普通API token或自报software:publish scope、inspector token均不能替代应用绑定机器凭据。机器token不能访问session-only后台。
- E05-4：实际Python发版工具：草稿默认、不带publish不发布；完整publish后按显式activate-channel切目标，匿名开关仍独立。重试/断点续发复核远端和本地摘要，不同源/应用/清单/文件拒绝，state无秘密且不破坏原资产。
- E05-5：token到期/撤销、绑定撤销、owner停用或移除角色后下一请求即失败；跨应用token失败；最后使用时间刷新不能复活撤销状态。操作由测试管理员完成并恢复本轮账号。
- E05-6：NSIS声明由UI/清单明确传递，服务端有确认人/时间；resume不能忽略声明。审计含操作对象/原因/身份但不含原token、OAuth/S3等秘密。

## 证据与清理

保留脱敏请求/响应、401/403/404等实际结果与请求前后状态、审计/授权记录和工具退出码。按实际路由契约判断状态，不把任何非200都当安全成功。只撤销本轮token并恢复本轮改动，原token不入报告/trace共享件。

## Execution log

2026-10-20补验：见[本轮报告](../manual-acceptance-results.md)。A/D和匿名更新源边界通过；本轮未复跑2026-10-19的全部CSRF负向与机器发版矩阵，以下历史证据独立保留。

尚未执行。领取时记录完整SHA、环境/runId、开始时间；结束追加实际命令、结果、证据、缺陷和清理，不覆盖首轮失败。

- 2026-10-19 执行（runId `e2e-20261019-100450`，依赖票02/04 已完成的真实账号与业务数据）。新增 `web/e2e/permission-isolation-csrf.spec.ts`，4 用例全过，trace 归档。
- 用例1（匿名边界）：`/api/v1/auth/me` 401；受保护管理 API 401/403；公开插件目录可读（产品契约：软件目录为登录用户内部目录，匿名 401 为预期边界而非缺陷）。UI 受保护路由跳转登录。PASS。
- 用例2（CSRF，E05-3 前半）：`POST /api/v1/plugin-management/projects` 无 `X-XSRF-TOKEN` 403；带真实 cookie+token 成功创建。PASS。
- 用例3（跨用户隔离，E05-2 部分）：outsider 对他人私有插件项目/指定 release/archive 均 403/404，无元信息泄漏。PASS。
- 用例4（注销失效，E05-5 部分）：logout（maxRedirects:0，302）后原 session cookie 调 `/me` 401 —— 服务端真实失效。PASS。
- E05-3 机器 token 部分、E05-4 Python 发版工具、E05-5 token 到期/撤销矩阵、E05-6 NSIS 声明与审计脱敏：**blocked/未覆盖** —— 本轮未运行机器发版工具与真实多机环境（见票09 阻塞清单）；未签发机器 token，故无撤销类用例可执行。
- 工程记录：`BrowserContext.request` 为属性而非方法（Playwright API 差异，修 spec 时踩坑一次）。
