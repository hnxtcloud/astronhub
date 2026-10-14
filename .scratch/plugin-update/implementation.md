# 插件管理与应用更新实施索引

Tracker: local-markdown
Status: ready-for-implementation
Integration branch: codex/plugin-update-integration
Base branch: codex/software-center-phase1

## Specifications

- [插件规格](../../docs/27-plugin-management-implementation-plan.md)
- [更新规格](../../docs/28-software-release-update-channels.md)
- [决策地图](map.md)

## Tracker operations

- 每票独立文件，Status 使用 ready-for-agent、claimed、done；Assignee 表示认领。
- 读取每票 Blocked by；只有所有阻塞票 done 才能认领。失败/缺环境保留未验收，不自动关闭。
- 实施子代理在由 integration tip 创建的独立工作树和分支工作；先读仓库技能，按 TDD 做一条行为再扩展；合并前更新 integration。
- 合并代理顺序合并，记录提交；统一代码审查和修复后才关闭最后集成票。
- 本次只使用本地跟踪器，不要求推送或创建外部 PR。全仓库跟踪器可另用 /setup-matt-pocock-skills 配置。
- 已有用户改动与 .scratch 其他内容不归本工作提交；规范和研究作为本工作上下文。

## Task graph

| 任务 | 阻塞 | 规格验收分组 |
| --- | --- | --- |
| [插件草稿、版本与审核管理闭环](tickets/01-plugin-drafts-review.md) | None | P1、P4 |
| [离线单包和仓库批量导入](tickets/02-offline-plugin-import.md) | 01 | P2、P3 |
| [插件下载、历史版本与组件治理](tickets/03-plugin-download-governance.md) | 01,02 | P4 |
| [内网 npm 插件发布及依赖上传](tickets/04-npm-publication.md) | 01,02 | P5 |
| [内网 Git 原生市场发布](tickets/05-git-marketplaces.md) | 01,02 | P6 |
| [发布恢复、重试与渠道撤回](tickets/06-publication-recovery.md) | 03,04,05 | P7 |
| [软件发行资产上传与完整发布](tickets/07-software-release-assets.md) | None | U1、U2、U6（页面部分） |
| [按应用匿名的 GitLab 更新读取](tickets/08-gitlab-update-reads.md) | 07 | U3、U4 |
| [Generic 与白泽灵台更新源](tickets/09-native-update-feeds.md) | 08 | U5 |
| [受控机器发版入口与运维说明](tickets/10-software-machine-publishing.md) | 07,08,09 | U6（机器入口部分） |
| [完整集成与原生渠道验收](tickets/11-integrate-and-verify.md) | 06,10 | P8、U7 |

当前 frontier：插件草稿、版本与审核管理闭环；软件发行资产上传与完整发布。其余由明确阻塞关系推进。

## Verification seams

领域服务（规则与不可变性）、HTTP（权限与完整流程）、原生解析器/外部渠道适配器（协议与冲突恢复）、页面操作（管理流程）。TDD 技能要求用户确认，已提问，等待答复后再编写测试。

## Environment acceptance

真实内网 registry、Git 目标和发布凭据；安装样本与签名；NSIS/macOS 原生安装和差分请求分别验收。模拟通过只记服务端证据。
