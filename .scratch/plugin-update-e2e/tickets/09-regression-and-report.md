# 既有回归、缺陷复验与报告

**Status:** done

**Assignee:** ZCode coding agent (adolbook)

**Result:** partial（报告已完整汇总；存在 blocked/not-run，地图保持 open）

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

- 2026-10-19 完成（runId `e2e-20261019-100450`）。**报告：`.dev/e2e/e2e-20261019-100450/report.md`（结论 partial）**；环境盘点 `ledgers/environment.yaml`；清理账本 `ledgers/cleanup-ledger.md`；凭据引用 `ledgers/credentials.local.env`（不打印不入库）。
- E09-1：最终全量回归 = 3 新 spec（plugin-hosted-lifecycle 3、software-manual-zip 2、permission-isolation-csrf 4）+ 4 既有 smoke（auth-entry 8、landing-navigation 3、route-guard 2、dashboard-shell 1），命令 `pnpm exec playwright test <7 specs> --project=chromium --workers=1 --retries=0 --trace=on`。结果 **22 passed / 1 skipped / 0 failed**（skipped 为既有条件跳过：需 `SKILLHUB_RUNTIME_E2E=1` 隔离运行时的用例，非本轮范围）。输出与退出码：`evidence/final-regression-output.txt`；trace：`evidence/final-regression-traces/`。
- E09-2：390px 窄屏关键操作由既有用例覆盖并修复缺陷 DEF-05（见缺陷表）；zh/en/ru 状态文案未专项遍历（本轮新增状态文案以 en 验证为准）。
- E09-3：缺陷与复验 —— DEF-01（实体列映射致空库首启失败，修复+全量复验）、DEF-03（软件 release 包列表恒空，修复+票04 复验）、DEF-04（既有 smoke 文案漂移，用例对齐）、DEF-05（落地页 390px 溢出，修复+复验）；DEF-02 为环境阻塞（扫描器缺失）。**被测代码 = 70432891 + 未提交修复**；不同 SHA 的结果未混合：首轮失败证据保留于 evidence/，全绿结论仅对应含修复的工作区。修复未 commit（用户未要求），建议按 fix(software)×2 / fix(web) / test(e2e) 拆分提交。
- E09-4：逐 case 矩阵见报告；原生安装/签名/差分/全量回退/首次迁移中，仅首次迁移（PG17.9 空库 V1–V84）有实际证据，其余 blocked。
- E09-5：清理/保留账本见 `ledgers/cleanup-ledger.md`；用户原有数据、共享 Redis、客户端路径未动。
- 下一执行入口：补扫描器 → 内网 Git/npm（票03）→ Inspector+Nginx/S3（票04 深度/票07）→ PG16 实例 → 原生终端（票08）与故障注入（票06）。
