# 确定组合交付模型、宿主范围与打包责任

Labels: wayfinder:grilling
Type: grilling
Status: resolved
Assignee: unassigned
Blocked by: 01

## Question

多技能组合采用什么默认交付模型，是否保留 Suite 的独立产品能力，覆盖哪些宿主，以及谁负责生成原生插件包？

## Answer

2026-10-20：回填本聊天 Q1–Q3、Q5 的真实答复。用户对 Q1 选择推荐，对 Q2 答“可以不保留”，对 Q3 答“是的”；随后对包含 Q5 的轮次答“按推荐”。

默认用 Plugin 交付组合技能，Suite 可以退出；继续 Claude Code、Codex、Pi，首期由发布者提供原生包。已确认行为见 [ADR-0005](../../../docs/adr/0005-consolidate-skill-bundles-under-plugins.md) 和 [实施说明的产品目标](../../../docs/suite-retirement-plan.md#产品目标)。平台自动构包另行评估，不是第一阶段实施任务。
