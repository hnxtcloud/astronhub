# 插件导入、审核、托管及治理 UI

**Status:** claimed

**Assignee:** ZCode coding agent (adolbook)

**Result:** partial — HOSTED子范围已通过；2026-10-20清单补验真实驳回后新版本发布、下载SHA256、安装指引、隐藏/归档/下架。三宿主、批量候选、组件关联及原生渠道等未全部覆盖，不能将整票标为passed。

**Blocked by:** 01

**Layer:** L1

## 前置和上下文

[插件指南](../../../docs/skillhub/guide/plugin-management.md)；入口 `/dashboard/plugins`、`/plugins`。使用01独立账号和新样本；至少包含普通作者与审核者两个身份。文件对照 API 和 UI 的生成契约。

## 场景

- E02-1：普通作者选 namespace，导入三宿主单包，查看静态证据和文件；仓库预览选择有效候选形成草稿，不自动审核。单包成功后从页面继续提交，静态检查不伪装成运行/扫描成功。
- E02-2：同内容重复候选可跳过，同身份同版不同字节冲突；无效候选单独反馈；不安全顶层路径/链接/重复条目/全局预算拒绝整次扫描。确认版本、原始名称、字节和权限未被静默修改。
- E02-3：普通提交→审核者驳回及意见→修正草稿/重新提交→批准→持久后台发布。受限 HOSTED-only 完成后可见；PUBLIC 等所需外部渠道齐全才可见，不拿03未就绪导致的BLOCKED当发布成功。
- E02-4：已发布原始包不可替换；管理页、公开目录和固定历史包/文件/安装指引一致；两个版本历史摘要正确。私有内容不能从匿名、其他namespace或安装指引泄漏。
- E02-5：组件显式关联 Skill/MCP 固定版本，独立目标ACL或下架后不可见，不转移权限；解除关联不删除目标。
- E02-6：隐藏/恢复、归档/恢复、版本下架、可见性改变均需正确角色与原因；目录当前指针重算、旧文件治理有效；恢复显示不自动恢复已撤回的原生渠道。

## 证据与完成条件

保留浏览器trace、审核记录、发布任务/渠道及审计ID、页面状态、包/文件摘要。至少一条完整业务链由UI真实操作完成，API只用于准备/佐证；按角色记录失败边界。只治理/清理本run资源，保留必要历史。

## Execution log

2026-10-20补验：见[本轮报告](../manual-acceptance-results.md)。B/C/D/G对应步骤通过；修复任务SUCCEEDED但版本仍PENDING_REVIEW时停止轮询的页面缺陷。保留以下2026-10-19历史记录，不将当时仅比较下载长度的结果转记为SHA256通过。

尚未执行。领取时记录完整SHA、环境/runId、开始时间；结束追加实际命令、结果、证据、缺陷和清理，不覆盖首轮失败。

- 2026-10-19 执行（runId `e2e-20261019-100450`，被测 = 70432891 + 未提交 DEF-01/03/05 修复）。新增 `web/e2e/plugin-hosted-lifecycle.spec.ts`，真实 UI 操作 + 真实 API 佐证，Playwright trace 归档 `.dev/e2e/e2e-20261019-100450/evidence/`。
- 用例1（E02-1/E02-3/E02-4）：作者 UI 导入 ZIP（版本/namespace 校验，manifest name/version 契约）→ 提交 PENDING_REVIEW → PLUGIN_ADMIN 审核页批准 → 后台发布任务 poll 至 "Publication complete"（HOSTED 渠道）→ 作者页 Download original ZIP 可见且字节与导入档案一致 → 公开目录详情页可浏览。PASS。
- 用例2（E02-3 驳回路径）：驳回并填意见 → 版本终态 REJECTED 不可再提交 → 新版本重新提交成立。PASS。
- 用例3（权限门禁）：普通作者 approve 返回 403；outsider 无法看到私有项目。PASS。
- E02-2（Git/npm 仓库候选）、E02-5（组件关联）、E02-6（隐藏/归档/下架治理）、PUBLIC 渠道发布：**未执行，blocked** —— 外部 Git/npm 渠道与扫描器环境缺失（见票03、票01 阻塞清单）；按规则不将 HOSTED-only 通过计为整票全量 passed。
- 缺陷：无本票新缺陷（首轮启动失败 DEF-01 与软件包列表 DEF-03 见票01/票04）。
