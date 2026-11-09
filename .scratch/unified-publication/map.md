# 统一发现与发布流程决策地图

Labels: wayfinder:map
Status: resolved
Tracker: local-markdown

## Destination

明确统一发现、统一发布、我的发布及可恢复发布任务的完整范围，形成用户整体核对后可交接实现的规格。地图完成表示决策完成，不表示代码实现或验收通过。

## Notes

- 本图为本地 Wayfinder 入口，子票位于 `issues/`，产品行为及验收条件以 [规格入口](spec.md) 指向的文档为准。沿用 local-markdown，不创建外部 Issue。
- 本轮仅建图。四轮 Q1–Q14 的真实答复回填为 resolved 决策票；这不是本次建图中新解决四张票。用户“帮我先建立wayfinder”不回填为完整方案的最终确认，也不授权开始业务实现。
- 每次继续先读本图，再扫描 `issues/` 的 Status 为 open、Assignee 为 unassigned、Blocked by 全部 resolved 的票，按编号选择第一张。领取时先设 claimed 并填写 Assignee，保存后再处理。
- 子票仅使用 `wayfinder:<type>` 标签；Type 为 research、prototype、grilling 或 task；Status 为 open、claimed 或 resolved；无依赖写 None。Question 保存问题，Comments 追加过程，Answer 保存真实结论。
- 每次会话最多解决一张非 research 票。grilling 票使用 grilling 与 domain-modeling，需要用户真实答复；事实调查和界面试验按需使用对应技能，不能代理用户做产品决定。
- 修改术语时同步 GLOSSARY.md；改变领域边界时核对 ADR-0001、ADR-0005 和 ADR-0006。涉及实现时再按 AGENTS.md 读取对应编码、前端、API、生命周期、开发和测试技能。
- 所有子票 resolved 且范围内无未明事项时，才将本图设 resolved；后续实现另建任务和验证记录。保留其他工作及地图的已有未提交改动。

## Decisions so far

- [统一用户流程，保留领域边界](issues/01-experience-and-domain-boundaries.md)：Q1–Q3 已确认统一操作、材料识别及包含软件的统一发现。
- [统一新建更新、提交与失败恢复](issues/02-submission-and-recovery.md)：Q4–Q6 已确认共用流程、一次提交及持久化恢复。
- [确定首期内容、来源与管理入口](issues/03-scope-sources-and-management.md)：Q7–Q10 已确认首期领域、候选清单、既有来源及我的发布。
- [确定草稿、取消、兼容与使用动作](issues/04-drafts-compatibility-and-use.md)：Q11–Q14 已确认自动保存、权限复核、取消边界及旧客户端兼容。
- [核对完整统一发布范围并交接实施](issues/05-final-scope-review.md)：2026-11-09 用户明确调用 implement，完成范围交接，功能实现与验证独立记录。

## Not yet specified

当前无额外范围内雾区，全部决策票已 resolved。后续实现进度见 [实施记录](implementation.md)，不以地图状态代替实现和验收。

## Out of scope

- 本次建图不修改业务代码、不运行迁移、不启动或部署服务、不推送或创建 PR。
- 首期不新增独立 MCP 发布、通用远程导入器、协作编辑或通用 Capability 聚合，不恢复 Suite 新发布。
- 不以建图、规格完成或决策票 resolved 代替功能交付与验证。
