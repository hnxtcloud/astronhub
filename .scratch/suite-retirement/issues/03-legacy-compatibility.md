# 确定 Suite 停写与历史兼容范围

Labels: wayfinder:grilling
Type: grilling
Status: resolved
Assignee: unassigned
Blocked by: 02

## Question

Suite 如何分阶段退出？已有用户的详情链接、安装、检查、升级、卸载和历史记录是否继续可用？

## Answer

2026-10-20：回填 Q4、Q6；用户在这两轮均答“按推荐”。停止新内容、移除主动入口，保留旧内容兼容与原有治理，不直接清库或自动转换。

行为边界见 [第一阶段行为](../../../docs/suite-retirement-plan.md#第一阶段行为)。本地代码核查表明 install/check/upgrade 依赖服务端 Suite 能力，remove 使用本地 inventory；安装来源兼容必须保留，不能以删除服务端元数据标记的方式阻断所有历史使用。
