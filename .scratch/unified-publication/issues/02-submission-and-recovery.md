# 统一新建更新、提交与失败恢复

Labels: wayfinder:grilling
Type: grilling
Status: resolved
Assignee: unassigned
Blocked by: 01

## Question

新建与更新是否共用流程？最后一次点击提交什么？中途失败是否保留成功部分并支持重新登录后继续？

## Answer

2026-11-09：回填本聊天第二轮 Q4–Q6，用户真实答复“按推荐”。

确认新建与更新共用流程，已有内容由用户确认归属及版本；一次提交串联中间操作并保留保存草稿；如实显示审核、检查和发布结果。发布任务持久化，成功步骤保留，仅修复重试失败部分，并防止重复执行。

行为与验收见 [设计中的 Q4–Q6](../../../docs/unified-publication-design.md#已确认)。用户确认的是可恢复分步完成，不是跨领域整体回滚。
