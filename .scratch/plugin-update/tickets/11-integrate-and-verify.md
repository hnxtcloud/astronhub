# 完整集成与原生渠道验收

**What to build:** 集成全部票，生成 OpenAPI，运行仓库检查并更新部署和迁移文档；代码审查修复。

**Blocked by:** 06,10

**Status:** done

**Assignee:** software_ticket07_resume

**Verification base:** `898384ab0019f18479f2ebc388254067de0fee1c`（集成 `ba9739e1`；本次关闭提交仅更新本票，不改变已验证产品源码）。

**Spec coverage:** P8、U7

- [x] 后端/前端/typecheck/lint/staging 按环境记录；不得手改 schema.d.ts
- [x] 真实内网 Git/npm、NSIS/macOS 签名、白泽下载和差分/全量回退分别记录；缺条件不得宣称通过

## Evidence

2026-10-16 经根代理明确批准关闭。`done` 表示本环境集成实现、双轴审查修复及可用检查完成；部署、签名安装和以下外部环境验收仍独立保留，不表示全部生产验收通过。下列原生安装探针保留各自的版本、环境和范围，不拼接为最终完整回归结果。

### Final integrated verification — 2026-10-16

- 固定源码 `898384ab`，验证分支 `codex/plugin-final-verification`。Standards/Spec 两轴审查及后续修复复核均为 0 pending；集成 `ba9739e1` 在本轮测试后再次核对已合入。用户主工作树、客户端仓库与正常客户端配置未修改，无推送、部署或应用/安装器启动。
- 全 Maven reactor `-pl skillhub-app -am test`，不使用 `-Dtest` 筛选；`final-backend/run-2294978adfcd/result.json`：**2408 tests / 381 suites，2406 passed、0 failures、0 errors、2 skipped，Maven exit 0**。独立无 uplinks 的 loopback npm registry，Maven 512 MiB / fork 768 MiB / Spring context cache 2；测试 registry 与构建进程已停止，清理错误为空。
- 两项跳过分别为 `RedisClusterIntegrationTest.clusterSupportsSpringDataSessionsAndRedissonStreams` 缺少 `REDIS_CLUSTER_TEST_NODES`，以及 `GitNativeFixtureIntegrationTest.exportsActualPublishedCommitForNativeClients` 缺少可选 `astronhub.git.native-fixture`。另有 **12 个已知依赖 Docker 的测试类被明确排除**，名单保存在该 run 的 `excluded-docker-tests.txt` 和 result 中；排除不计为通过。既有隔离原生 Pi/Claude/Codex 安装证据见下文，不替代本轮跳过项。
- 本轮实际 Springdoc 导出 **453 paths / 493 schemas**，SHA256 `75c8e63f85ac29fc0c3b935499fde17de3418e4f0592a5fab0499a44f65c5264`。同 HEAD、同工作树且新鲜导出门控通过后，实际调用 openapi-typescript 生成 SDK；仅有 CRLF/LF 工作副本差异，通过精确 `git add` 刷新索引，staged/content diff 为空，未手改生成文件。
- `final-frontend/run-96de93315da2/result.json`：Node heap 1536 MiB，Vitest 使用单 worker 与正常文件隔离，**237 files / 967 tests 全部通过**；完整 `tsc --noEmit`、全仓 ESLint（零 warning）、`tsc -b` 和 Vite production build 均 exit 0。SDK、测试、类型、lint 和构建严格串行，未安装依赖。
- `final-config/run-9245027cd46c`：base-path 脚本、发布 CLI **4 tests**、`git diff --check` 通过；已安装 Nginx 1.28.0 实际代理 **57 checks** 通过，覆盖 `/`、`/hub/`、`/hub.v1/`、编码路径、HEAD/range/cache/错误与限定流式入口。大 body 入场探针没有发送上传体，不构成 5 GiB 传输证明。
- 发布配置整套脚本 **未通过环境门槛**：`validate-release-config-test.sh` 在第 75 行因 `docker: command not found` 停止；此前仅执行首个合法配置样例，不能据此宣称整套通过。Docker staging/镜像构建未执行，Helm 不可用；未伪造 Docker、改写测试或下载 chart 依赖绕过。
- PostgreSQL 18.6 的实际 Flyway 新库全量 **84 migrations**、空 schema 先至 V83（83 条）再至 V84（1 条）均通过 validate，重复 migrate 均为 0；证据 `pg-migration/run-e591b81b0560/result.json` 锚定 `5c31c266`。最终 `898384ab` 的 84 个 SQL 文件 SHA256 逐项完全一致，映射 `pg-migration/final-head-hash-mapping.json`。临时数据库已停止，未使用现有服务器。已有 Flyway 会提示 PG18 超出其官方已测试版本；此证明不等于生产 PG16、Hibernate 启动或数据库并发验收。

### Preserved failures and evidence boundaries

- 旧基线 `322ae993` 的 `final-backend/run-d28d2d0b8e14` 经根代理要求在审查修复前安全停止：1220 项局部通过，**不是完整通过结果**。
- `dcf8a07d` 的完整 `final-backend/run-e3eca879bffd`：2402 tests / 380 suites，**3 failures、0 errors、2 skips**，exit 1。失败为迁移编号连续性、白泽可选 releasesUrl 正例缺资产及超时清理 fixture 恰好等于到期时刻的边界样本；最小修复 `b9cb83b4` 后重跑，不改变历史结果。
- `5c31c266` 的完整 `final-backend/run-84ada98530c8`：2402 tests / 380 suites，**0 failures、1 error、2 skips**，exit 1。Git 并发发布测试暴露 scratch 遍历中的瞬时对象文件消失，修复 `6c2e6936` 并增加 6 个预算边界用例后，才启动最终新完整 run。两轮失败未拼接成全绿。
- 所有外部路径以 `C:/Users/adolbook/.codex/worktrees/plugin-update-notes/` 为根。两轮失败各保存 761 份当轮 report/OpenAPI，最终成功轮保存 763 份；分别附 SHA256 manifest、精确失败/跳过 JSON 和原始 reactor 日志。最终还保存 44 份原生 update-contract 产物、前端原始报告/构建日志/实际生成 SDK、配置与真实代理日志；外部交接为 `ticket11-final-handoff.md`。
- 仍需部署环境独立验收：真实企业 Git/npm 服务及认证、生产 PostgreSQL 16、Docker staging、S3/MinIO、Helm 渲染、签名 NSIS/macOS 安装与更新应用、现有客户端首次更新源迁移、完整差分和全量回退、真实大文件传输。现有白泽/Intelli 源码只读合同验证与合成下载字节检查不替代这些验收。

### Isolated native Pi install — 2026-10-15

- 根代理通过实际 `NpmRegistryPublisher` 组件发布合成的仅含 Skill 的原始 TGZ，到本地无 uplinks 的 Verdaccio 6.1.6；随后调用已安装的 Pi 1.2.0 `pi install npm:@astronhub/native-pi-…@1.0.0`。固定版本写入独立 `PI_CODING_AGENT_DIR`，安装后的 Skill 字节、包名和版本、registry 返回的 TGZ 字节均一致。
- 使用空 user/global npmrc、独立缓存、明确 loopback registry，并关闭 lifecycle scripts、audit 和更新提示；`PI_OFFLINE=1` 禁止启动网络操作。未启动代理会话，未修改正常 Pi/npm 配置。原生安装完成后测试 registry 已停止。
- 外部证据：`C:/Users/adolbook/.codex/worktrees/plugin-update-notes/native-pi-probe/latest-result.json`、同目录 Java/Python 探针及 `run-a348cca8c269/` 日志。首次使用 PowerShell 启动包装器的探针因进程句柄等待失败，不计为通过；进程停止后改用直接受控子进程重跑成功。
- 这是实际组件发布与本地原生固定版本安装证据，未包含 Spring 审批整链，也未执行复杂扩展或验证用户实际内网 registry。Claude/Codex 的生成 Git 市场验收仍由票 05 补齐；原生软件签名、安装和完整差分仍未验收。

### Isolated native Claude/Codex installation and update — 2026-10-15

- 实际 `GitMarketplacePublisher` 生成两种市场索引和原生文件；隔离 Claude Code 2.1.273 与 Codex 0.162.1 经只读 loopback smart HTTPS Git 注册市场，安装 1.0.0。随后再次通过实际发布组件发布 1.1.0，两种客户端刷新市场并更新/安装到 1.1.0，清单与 Skill 字节核对通过；原始 commit 的 1.0.0 仍可读取。
- 使用临时 CA，仅通过子进程 Git 配置受信，TLS 验证保持开启；所有 HOME、Claude/Codex 配置与 Git 配置隔离。仅含文档命令和 Skill，未启动代理会话、Hook、MCP 或插件可执行代码。测试 HTTPS 服务已停止，正常客户端设置未修改。
- 成功证据：`C:/Users/adolbook/.codex/worktrees/plugin-update-notes/native-git05/run-2930989487306959924/native-acceptance-310f982959/{summary.json,results.json}`；原始 commit `8188f48c1c731fe1d22bf8b956cf5331ebe9b1d7`，新 commit `94a71fe9134c59d6e35f773b02688baa69055281`。外部 `native_git05_acceptance.py`、`NativeGitAdvanceProbe.java` 保留探针来源。
- 首次升级探针因测试 classpath 缺 commons-compress 失败；补依赖后的首轮因宿主资源争用在 Claude 注册超时（其输出随后完成，已确认进程退出），均不计通过。放宽受控子进程超时后完整重跑成功。更早 `native-acceptance-4aa518c003` 仅证明初次安装/刷新，不替代本次升级证据。
- 这是本地实际组件和已安装原生 CLI 的安装/更新合同验证；未包含 Spring 审批整链、复杂插件运行、企业 Git 认证或真实内网部署。软件签名、原生安装和完整差分/回退继续保留独立环境验收。

### Original Intelli GitLab transport contract — 2026-10-15

- 新增可选 `SoftwareIntelliClientContractIntegrationTest` 与 `/hub` 子类，使用实际嵌入 Tomcat、H2、本地对象存储、合法惰性 ZIP 和新发布的原始 `dist/latest-mac.yml`。调用只读客户端现有 `release-checker.ts`、`gitlab-feed-proxy.ts`，仅转译这两个模块至测试输出目录，不启动 Electron 或应用。
- 根路径和 `/hub` 各 1 项、共 2 项实际 HTTP 测试通过，无失败/错误/跳过；每轮探针 17 个合同检查通过。实际 TypeScript 5.9.3、electron-updater 6.8.9 的 GenericProvider/HttpExecutor/DigestTransform/DataSplitter 完成 tags/说明、原始元数据、221 字节 ZIP 完整下载 SHA512、HEAD、单区间及 multipart 精确字节验证，代理已停止。
- 日志 `.scratch/plugin-update-native-intelli-initial.log`；完整报告 `server/skillhub-app/target/update-contract/intelli-gitlab-*/probe-output/intelli-native-*/report.json`。复现脚本为 `scripts/tests/intelli-gitlab-contract-probe.cjs`；显式指定 `astronhub.update.intelliProbe=true` 与 `astronhub.update.intelliClientRoot`，无凭据、无公网请求或正常客户端配置改写。
- 这补充既有 GitLab 服务端协议证据，未验证签名、安装、完整差分或全量回退；合成 ZIP 只含 README。后续机器鉴权/恢复合入后的统一回归仍属于本票，不能据此提前关闭。

### Container runtime prerequisites

- 代码核对发现既有生产镜像仅安装 wget，staging 镜像也没有 Git。已在两者补充 Git 与系统 CA 包，并记录企业 CA 与 Java truststore 的区别。该修正保证镜像声明包含新发布器的运行依赖；当前环境没有 Docker，未把 Dockerfile 静态核对描述为镜像构建或 staging 通过。
