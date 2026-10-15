# Generic 与白泽灵台更新源

**What to build:** 同一发布快照投影 Electron YAML 与白泽 manifest v1，管理页展示地址与迁移配置。

**Blocked by:** 08

**Status:** done

**Handoff:** merged（合并代理已复核并关闭）

**Assignee:** software_ticket07_resume / codex/native-update-feeds

**Migration reservation:** V81（如需要）

**Spec coverage:** U5

- [x] 原生解析器接受、同源 ZIP、无重定向、引用版本完整；preview/stable 独立
- [x] SHA512 base64 与 SHA256 使用真实字节；缓存目标切换；历史资产可取

## Evidence

- 规范读取路径为 `/updates/{appSlug}/{channelSlug}/{metadataName}`。Root 已确认该形式：slug 绑定完整目标元组，stable/preview 显式目标独立，不推断“最高版本”；清除目标关闭 feed。
- URL 投影逐段编码固定快照资产路径并保留部署前缀；原始 YAML/JSON 字节及安装包摘要保持不变。复用 08 治理与流式响应，投影字节有独立 SHA256 ETag、HEAD/Range 和 no-store。
- 两轮完整 reactor `.scratch-software-native-complete.log` / `.scratch-software-native-final.log` 各 84 项通过（auth 33 + app 51），0 failures/errors/skips；涵盖根与 `/hub` 原生 HTTP 各 13、原生元数据 9、已有发布 6、日志 9 和实际 Springdoc 1。
- 现有安装的 electron-updater 6.8.9 GenericProvider、文件选择、HttpExecutor 完整下载、DigestTransform SHA512，以及白泽原始 parseFeed、同源 ZIP 完整下载及 SHA256 在实际 HTTP 上通过；stable/preview、前缀、源关闭、显式目标切换/清空、历史文件、缓存条件、原始元数据与投影 HEAD/Range 均有覆盖。最终 native 日志在 `server/skillhub-app/target/update-contract/native-feed{,-hub}.log`。合成字节样本不等于真实安装包、签名、安装、完整差分或全量回退验收。
- 原生格式：Windows Electron 仅受管 EXE/NSIS；macOS 元数据必须引用受管 ZIP，可附 DMG；Linux Electron 暂不支持；白泽 Windows x64 ZIP。仅有 DMG 和 DMG 改路径伪装 ZIP 都有真实红绿检查。既有中性 Windows ZIP/Electron 测试样本改为 macOS ZIP，以保持其原有测试目的。
- 实际 semver 7.7.4 对最大安全整数边界的结果已核对；发布时检查 machineVersion 和白泽 piVersion 核心分量，草稿保存规则未改变。数值超限的元数据红绿检查通过。
- 管理页使用实际生成的发布/渠道类型展示地址和迁移配置；白泽环境变量、Generic provider 与预发布提示、现有 Intelli Dev 的 baseUrl/owner/repo/artifactPath 分开显示。UI/admin 9 项通过（`.scratch-software-native-ui-final.log`）；未改客户端仓库或启动应用。
- 最终注解版实际 Springdoc 导出通过（`.scratch-software-native-openapi.log`），410 paths / 449 schemas；已检查三个必填模板参数、YAML media type、白泽裸清单必填字段及 package 字段，再以 openapi-typescript 生成 SDK。未手改生成文件。
- 收尾 MIME 红测已观察 application/json 与预期 application/yaml 的差异，已改为投影协议 MIME。`.scratch-software-native-final-contract.log` 最终 86 项通过，0 failures/errors/skips，实际 native 子进程日志正常捕获，不再污染 Surefire 通道。
- 实际生成契约后的全前端类型检查及定向 ESLint 已通过；新增“没有规范原始清单文件时不生成旧 GitLab 配置”UI 红测正确失败并已修复。双文件 forks/threads 尝试分别遇到 worker 启动超时（0 tests/2 errors，以及 admin 5 通过但 source worker 1 error），未记为通过。随后单文件干净重跑 source UI 5、admin UI 5 均通过（`.scratch-software-native-ui-source-only.log`、`.scratch-software-native-ui-admin-only.log`），无未处理错误。
- 缓存复核真实观察到：渠道回退时日期条件请求错误返回 304。Root 同意可变 feed 只提供投影 ETag/no-store、忽略日期条件并省略 Last-Modified；固定/raw 日期规则保持。修复后 `.scratch-software-native-date-cache-green.log` 37 项通过，包含固定资产 IMS/If-Range 日期正例。
- 原生版本边界进一步实测：semver 将两个超限数字预发布标识比较为相等；白泽原始比较函数使用 BigInt，但有 100 字符上限。新增两项红测后，发布规则扩展到数字预发布标识和白泽版本长度。最终 `.scratch-software-native-exact-clients-final.log` 48 项通过，0 failures/errors/skips，脚本使用白泽原始比较函数的隔离执行与原始 parseFeed；没有加载白泽服务模块。覆盖当前最新代码、两个实际 native HTTP 子进程及更新后的版本校验 11 项。
- V81 未使用；真实 PostgreSQL/S3、Docker staging、原生 Windows/macOS 签名及安装、旧版 blockmap 定位、完整差分/全量回退保留票 11 独立验收。
- 功能提交 `c9e9e8c9`；已在 `bd2302b2` 合入最新集成 `ec1f8727`。唯一冲突是 docs28 进度段落，已合并两边事实；生成 SDK、语言资源和鉴权策略自动合并。合并后全前端 TypeScript 再次通过（`.scratch-software-native-merged-typecheck.log`），工作树干净，最终联合 Springdoc 由集成票统一核对。
- 集成关闭：完整分支 tip `c4cee5a8` 以 `372230df` 合入当前集成（保留额外原生升级证据提交 `465498a3`）。合并代理核查完整 86 项、后续最新代码 48 项均通过，单文件 source/admin 各 5 项 UI 通过，类型检查及定向 lint 无错误。生成 SDK 和语言资源自动合并，无手改；最终联合契约仍由集成统一核对。`git diff --check` 通过，90 个用户原有文件 SHA256 与合并前及票 02 清单一致；未额外构建、推送、建 PR 或清理工作树。票 10 解除阻塞；原生解析/合成字节完整下载不等于真实安装、差分或回退。
