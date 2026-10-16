# 既有回归、缺陷复验与报告

**Status:** ready-for-agent

**Assignee:** unassigned

**Result:** not-run

**Blocked by:** None

**Layer:** 全层汇总

## 开始方式

可先用[报告模板](../report-template.md)建立本轮记录，不必等其他票全部完成。最终汇总需01–08逐项结果；若有failed/blocked/not-run，则结论不能写全量passed，地图保持open并给出下一入口。

## 场景

- E09-1：运行实际收集到的既有Playwright smoke和相关完整回归：登录/注销/角色、namespace、Skill发布/审核/下载、插件/MCP目录、软件中心；新增插件/更新spec非零收集且真正执行。列出实际spec/case清单，不使用旧文档的历史数量。
- E09-2：核心管理页、错误/空状态、进度/重试/多文件操作、安装指引、zh/en/ru新增状态文案和窄屏关键操作可用；这些UI观察不代替权限或外部渠道证据。
- E09-3：汇总发现缺陷、首个失败、最小修复提交、相应类型/lint/后端/生成契约和E2E重验。被测SHA改变后明确对应哪些结果，不能把不同SHA/部署混成单次全绿。
- E09-4：逐case提供结果、环境层、输入摘要与证据；原生安装/签名/差分/全量回退/首次迁移分别判定。缺环境列出最小补充项，不写skip即通过或用模拟替代。
- E09-5：清理/保留账本与进程核对；恢复本轮故障配置、角色/策略，保留npm/Git不可变历史说明和审计；确认用户原有数据、客户端原路径没有改动。

## 交付物

可复用spec/helpers/样本生成与复现命令、最终报告、脱敏证据索引、未解决缺陷和环境需求表；不提交secret/rawsession。报告票可在完整分类后done，Result可为partial/failed；这不使其他blocked票done，也不自动使地图resolved。明确下一位agent从哪张票继续。

## Execution log

尚未执行。领取时记录完整SHA、环境/runId、开始时间；结束追加实际命令、结果、证据、缺陷和清理，不覆盖首轮失败。
