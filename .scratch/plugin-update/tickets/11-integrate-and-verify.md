# 完整集成与原生渠道验收

**What to build:** 集成全部票，生成 OpenAPI，运行仓库检查并更新部署和迁移文档；代码审查修复。

**Blocked by:** 06,10

**Status:** ready-for-agent

**Spec coverage:** P8、U7

- [ ] 后端/前端/typecheck/lint/staging 按环境记录；不得手改 schema.d.ts
- [ ] 真实内网 Git/npm、NSIS/macOS 签名、白泽下载和差分/全量回退分别记录；缺条件不得宣称通过

## Evidence

完整集成验收尚未完成；已有分票证据与下列独立探针不能提前关闭本票。未经验证不得勾选验收项。

### Isolated native Pi install — 2026-10-15

- 根代理通过实际 `NpmRegistryPublisher` 组件发布合成的仅含 Skill 的原始 TGZ，到本地无 uplinks 的 Verdaccio 6.1.6；随后调用已安装的 Pi 1.2.0 `pi install npm:@astronhub/native-pi-…@1.0.0`。固定版本写入独立 `PI_CODING_AGENT_DIR`，安装后的 Skill 字节、包名和版本、registry 返回的 TGZ 字节均一致。
- 使用空 user/global npmrc、独立缓存、明确 loopback registry，并关闭 lifecycle scripts、audit 和更新提示；`PI_OFFLINE=1` 禁止启动网络操作。未启动代理会话，未修改正常 Pi/npm 配置。原生安装完成后测试 registry 已停止。
- 外部证据：`C:/Users/adolbook/.codex/worktrees/plugin-update-notes/native-pi-probe/latest-result.json`、同目录 Java/Python 探针及 `run-a348cca8c269/` 日志。首次使用 PowerShell 启动包装器的探针因进程句柄等待失败，不计为通过；进程停止后改用直接受控子进程重跑成功。
- 这是实际组件发布与本地原生固定版本安装证据，未包含 Spring 审批整链，也未执行复杂扩展或验证用户实际内网 registry。Claude/Codex 的生成 Git 市场验收仍由票 05 补齐；原生软件签名、安装和完整差分仍未验收。
