# 内网 Git 原生市场发布

**What to build:** 配置 namespace 内网 Git 仓库并推送 Claude/Codex 原生文件及各自市场索引，显示安装指引。

**Blocked by:** 01,02

**Status:** done

**Assignee:** npm_publication / codex/plugin-git-marketplaces

**Migration reservation:** V80

**Spec coverage:** P6

- [x] 仅 PUBLIC 内容进入首版 public Git；市场名称和原生插件名称冲突检查
- [x] 不变 commit 记录；两种索引样例验证；实际 Git 与原生宿主验收单独记录

## Implementation context

- 接入票 04 的不可变渠道计划及单队列适配器，不另建调度器；工作树外 `plugin-update-notes/ticket04.md` 和 `native-channels.md` 提供具体接口和真实 CLI 探针位置。
- 票 03 使用持久审批任务 ID 排序发布请求，release ID 兜底，旧无任务发布版本优先级较低；Git 当前索引采用同一规则，迟到旧请求只保留历史内容，不回拨当前索引。顺序不能由网络完成时间推断。
- Git 对象写入不得执行归档中的 hooks、过滤器、子模块或配置；保留原生文件字节，不能把原始包内的 Git 元数据变成仓库控制文件。未支持内容明确拒绝并保留错误证据。

## Evidence

核心实现 `efb9222b`；与票 03 的访问/治理接线 `a5eb1739`；已并入集成文档 `ec1f8727`。V80 记录不可变目标修订、namespace 当前选择、持久目的地认领及推送前提交意图。Git 通过票 04 的单队列渠道执行，未新增调度器。

- 真实 bare Git 测试验证两种独立原生索引、精确文件字节、普通推送冲突重建、迟到旧任务保留历史但不回拨、丢失回执后的重启核对、名称/内容冲突及 `.git` 控制路径拒绝。
- 无外层测试事务的 HTTP 链路实际导入两种原生包，验证 PUBLIC 审批固定旧目标、成功回执及持久意图；配置变化不重定向既有任务；受限内容只有 HOSTED。错误响应仅保留稳定错误码，不泄漏发布凭据。
- 安装沿用票 03 的登录审计入口。成功 Git 回执须匹配 runtime/原生名称/版本/SHA256，且该 commit 索引确实选择该版本（`indexed=true`）；迟到旧版本或缺少选择证明时使用固定归档本地指引，避免安装成较新索引版本。提供固定 commit 的 POSIX/PowerShell 命令；仍 `runtimeVerified=false`。
- 仅部署专用显式 allowlist/credentials 引用；首版相同规范 HTTPS 发布和读取 URL，保留 TLS 校验，不支持 SSH/重定向/URL 内凭据。file 仅显式本地夹具。临时裸库 fast-import 不检出或执行归档代码，保留 push 前意图及成功历史；不 force push。
- 非事务 HTTP 回归暴露公开详情原有 stream 越过 EntityManager 生命周期的 500；plugin/MCP 只将两个有界查询改为 `getResultList().stream().findFirst()`，并验证 200/404，无其他查询重构。
- 票 03/04 合并回归：领域生命周期 2、npm HTTP 4、已发布访问/治理 HTTP 7 项通过。新安装用例初次漏登录产生 403，已修正用例，最终重跑的 5 项 Git HTTP 全通过；不将含该失败的早轮整体记为成功。
- 前端最终使用单线程、复用 worker 验证 Git/npm 表单及访问 UI，3 文件 4 测试通过；修改范围 ESLint 通过。首次 fork/独立 thread 启动超时发生于测试执行前，保留原日志。

最终后端 `ticket05-index-selection-and-export2.log`：14 项全通过（真实 Springdoc HTTP 导出 1，Git 非事务 HTTP 5，安装回执 3，真实 Git 5），无跳过。真实 Git 额外验证未管理目标文件、被外部修改索引不覆盖、不创建意图。生成 SDK 来自合并后实际 `target/openapi/openapi.json`，未手工合并生成文件。全量前端 `pnpm exec tsc --noEmit` 通过（`ticket05-final-web-tsc.log`）；修改范围 ESLint 及 `git diff --check` 通过。最终验收日志位于工作树外 `plugin-update-notes`，交由集成代理合并并更新总图。

原生客户端证据与实际内网验收分开：真实 publisher 输出由 Claude Code 2.1.273 和 Codex CLI 0.162.1 在隔离目录、loopback HTTPS Git 夹具注册和安装成功。Codex file:// URL 的拒绝保留为负向证据，产品生成本地克隆目录指引。未启动插件会话、执行插件代码或修改用户正常设置。真实企业 Git/认证/宿主运行和 Docker staging 仍由部署环境验收；原生更新补测由集成代理单独记录。

集成关闭：最终工作分支 tip `d055a760`（包含集成 `ec1f8727`）以 `40babeec` 无冲突合入。合并代理复核实际最终 14 项零失败/零跳过、早轮独立通过的 03/04 回归 13 项、最终 UI 4 项、完整类型检查、定向 lint 和实际 SDK 生成日志；初始缺登录失败、导出跳过及 worker 启动超时未当作最终成功。`git diff --check` 通过，90 个用户原有文件 SHA256 同合并前及票 02 清单一致。未额外构建、推送、建 PR 或清理工作树。票 06 解除依赖，可接续恢复及渠道治理。
