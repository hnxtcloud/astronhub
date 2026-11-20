# AstronHub CLI (preview)

安装企业私服中的 Agent Skills 和 Node 依赖。包名 `astronhub-cli`，命令名 `astronhub`。
运行要求：Node.js 22+；安装 Node 依赖时需已安装 npm 或 pnpm。试用版尚未发布到 registry。

## 本地构建与试用

从仓库根目录执行（构建需要 Bun，产物运行只需要 Node）：

```powershell
cd cli
bun install
bun run build:astronhub
node ./dist/astronhub-cli/bin/astronhub.mjs --help
npm pack ./dist/astronhub-cli --pack-destination ./dist
```

在另一个项目中安装打包产物：

```powershell
npm install -g C:/Project/astronhub/cli/dist/astronhub-cli-0.1.0.tgz
astronhub --version
```

发布到公司私服后，才可使用 `npx astronhub-cli ...` 或 `pnpm dlx astronhub-cli ...`。
首次获取 CLI 时，npm/pnpm 本身也需要配置公司 registry。

制作可拷贝到内网的发布工具包：在 `cli` 目录执行 `bun run prepare:astronhub-intranet`。
产物为 `dist/astronhub-cli-intranet-0.1.0.zip`，包含安装包、校验文件、跨平台发布脚本和内网操作说明。
内网无需源码或 Bun，使用 Node/npm 即可上传；发布应指定可写的 hosted 仓库地址。

## 配置一次

```powershell
astronhub config set npmRegistry http://22.56.110.101:19080/repository/npm-public/
astronhub config set skillRegistry http://localhost:8080
astronhub config --json
```

SkillHub 地址请替换为实际服务地址。两个 registry 独立配置，不会默认连接公共仓库。
配置保存在 `~/.astronhub/config.json`，可用 `ASTRONHUB_HOME` 指定隔离的配置目录。
环境变量 `ASTRONHUB_NPM_REGISTRY`、`ASTRONHUB_SKILL_REGISTRY` 优先于保存的配置；命令行 `--registry` 优先级最高。
仓库要求认证时，在当前终端设置 `ASTRONHUB_NPM_TOKEN` 或 `ASTRONHUB_TOKEN`。
npm 私服的 token 应具备读取元数据和压缩包的权限。原生 npm/pnpm 获取私服压缩包时，仍需有效的 `.npmrc` 认证配置。

## Node 依赖

```powershell
astronhub install @types/node@26.6.5 -D --dry-run
astronhub install @types/node@26.6.5 -D
astronhub install axios
astronhub install
astronhub install axios --pm pnpm
astronhub install axios --pm pnpm --filter my-app
astronhub install axios --pm pnpm -w
```

`astronhub node add` / `astronhub node install` 是别名。
包管理器按 `--pm`、最近的项目/祖先 workspace 中的 `packageManager` 或锁文件、用户配置、npm 默认值选择。
冲突锁文件需显式指定 `--pm`，不自动迁移锁文件。用户默认值可用 `config set packageManager pnpm` 设置。
支持 registry 包名、固定版本、版本范围和标签。使用带空格的版本范围时请加引号。
`--dry-run` 仅展示计划，不下载、不安装；`--ignore-scripts` 跳过依赖脚本；`-E` 保存准确版本。

### scoped 包兼容机制

安装期间临时启动仅监听 `127.0.0.1` 的 registry 网关，将 `/@scope%2fpackage` 元数据路径转换为
`/@scope/package` 再访问配置的私服，因此也能处理间接依赖和版本范围。
元数据中的 `dist.tarball` 保持私服原始地址，避免将临时网关端口写入锁文件。npm 审计请求在兼容模式下关闭。
网关只提供 GET/HEAD，拒绝上游重定向，并在安装结束后关闭。

试用限制：原有 `.npmrc` 中的 `@scope:registry` 会优先于普通 registry，可能绕过网关，需先检查这类配置。
私服应在元数据中返回客户端可访问的稳定压缩包 URL；该 URL 若仍包含不被私服支持的编码，需进一步适配。
后续重新安装仍建议使用 `astronhub install`，它会为解析元数据重新开启网关。
工具遵循 npm/pnpm 原有依赖脚本策略，不自动关闭脚本，也不会自动批准 pnpm 拦截的构建脚本。

## Skills

```powershell
astronhub login
astronhub skill find review
astronhub skill add @global/code-review
astronhub skill add @global/code-review@1.0.0 -a claude-code
astronhub skill add @global/code-review -a codex -g
astronhub skill list
astronhub skill update @global/code-review --check
astronhub skill update @global/code-review
astronhub skill remove @global/code-review
```

使用项目现有 SkillHub CLI 的下载校验、安全解压、安装元数据和登录实现。
默认安装到当前项目 `.agents/skills`；Codex 使用相同共享目录，Claude Code 使用 `.claude/skills`。
其他 Agent 沿用原 CLI 的 profile；`--dir` 可指定目录，与 `--agent` / `-g` 互斥。
登录凭据和 Skill 安装记录与原 CLI 共用 `~/.skillhub`。移除操作只移除本地安装，不删除服务器上的 Skill。
更新需要显式指定坐标，暂不提供隐式全部更新。`--skill-version` 可选择 Skill 版本。

## Agent 使用声明

```powershell
astronhub init --agent codex
astronhub init --agent claude-code
```

在当前项目安装随包提供的 `astronhub` 使用 Skill，并向 `AGENTS.md` / `CLAUDE.md` 追加入口。
保留现有指令，重复运行不会重复追加；已有同名但内容不同的 Skill 或管理块会明确报错。
初始化或安装后，部分 Agent 需要刷新或开启新会话才会发现 Skill。

Agent 调用示例：

```powershell
astronhub install axios --non-interactive --json
astronhub skill add @global/code-review -a codex --non-interactive --json
```

`--json` 的 stdout 用于结构化结果；依赖安装日志写入 stderr。以退出码确认执行是否成功。
退出码：0 成功；1 操作失败；2 SkillHub 认证失败；3 网络错误；4 文件操作失败；5 用法/配置错误；6 校验失败。
