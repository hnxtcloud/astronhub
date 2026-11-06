# Suite 退出与 Plugin 组合交付决策地图

Labels: wayfinder:map
Status: resolved
Tracker: local-markdown

## Destination

明确 Suite 分阶段退出、Plugin 组合交付及旧用户兼容的完整范围，形成经过用户整体核对、可交接实现的规格。地图完成表示决策完成，不表示实现或验收完成。

## Notes

- 本地入口为本文件，规格入口为 [实施规格指针](spec.md)，每个决策独立保存在 `issues/`。沿用仓库 local-markdown，不创建外部 Issue。
- 已有官方研究和用户 Q1–Q7 的真实答复回填为 resolved 票；这不是本次建图会话中新解决的决策。建图本身不代替用户对完整范围的最终核对。
- 每次继续先读本图，再查询 `issues/` 中 Status 为 open、Assignee 为 unassigned 且所有 Blocked by 均已 resolved 的票，按编号选择第一张。先设 claimed 并填写 Assignee，再开始处理。
- 决策票只有 `wayfinder:<type>` 标签；Type 使用 research、prototype、grilling 或 task。Status 使用 open、claimed、resolved；无依赖写 None。问题在 Question，解决时追加 Answer，讨论追加 Comments。
- 每次会话最多解决一张非 research 票。HITL 票必须有用户真实答复；grilling 票使用 grilling 与 domain-modeling，新增外部事实问题使用 research，界面试验使用 prototype。
- 本图是索引；决策过程在子票，ADR 保存架构决定，产品行为和验收要求以链接规格为准。阅读行为细节时跳转对应文档，避免在地图复制整份规格。
- 全部决策票 resolved 且没有范围内雾区后，才将本图设为 resolved。实现任务需在后续实施交接中单独建立，不能用决策票状态表示代码完成。
- 保留其他工作的未提交改动，尤其是现有 Plugin/E2E 地图和 CONTEXT.md；本图不认领其他工作的完成状态。

## Decisions so far

- [核实 Claude Code 纯 Skill 插件的官方支持](issues/01-skill-only-plugin-evidence.md)：纯 Skill Plugin 得到官方文档支持，与 Suite 的组合交付场景存在重叠。
- [确定组合交付模型、宿主范围与打包责任](issues/02-plugin-product-boundary.md)：默认采用 Plugin，Suite 可退出，继续三宿主，由发布者提供原生包。
- [确定 Suite 停写与历史兼容范围](issues/03-legacy-compatibility.md)：移除主动入口、停止新内容，历史读取、安装和本地卸载继续兼容。
- [确定存量审核与导入任务的收尾规则](issues/04-inflight-completion.md)：已开始流程允许完成最后一次发布，普通未提交草稿保留查看。

- [核对完整实施范围并完成交接](issues/05-final-scope-review.md)：2026-11-06 用户调用 implement，按既有规格开始实施。

## Not yet specified

当前没有额外雾区。可明确提出的问题均已建立子票；继续核对时如发现新的范围内决策，再增加独立子票并设置依赖。

## Out of scope

- 本次建图不修改功能代码、不运行数据迁移、不部署、不推送或创建 PR。
- 第一阶段不物理删除 Suite 数据或旧接口，不自动转换历史 Suite，不新增平台自动构包。
- 本轮不更改独立 Skill 生命周期、MCP 领域或软件中心。
- 不将某个 Claude Code 原生包视为 Codex、Pi 可直接安装的通用包。
