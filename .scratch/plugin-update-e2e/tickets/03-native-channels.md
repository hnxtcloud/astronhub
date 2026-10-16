# 内网 Git/npm 发布和独立依赖

**Status:** ready-for-agent

**Assignee:** unassigned

**Result:** not-run

**Blocked by:** 02

**Layer:** L2

## 前置和上下文

[渠道配置与约束](../../../docs/skillhub/guide/plugin-management.md)。运维预配 `skillhub.plugin.publication.allowed-git-remotes`、`allowed-registries` 与 `credentials` 显式映射；凭据仅提供引用。两个专用HTTPS Git目标与npm来源、CA、读写权限及无公网uplink须实际可用；未提供相应渠道则子项blocked，不默认用公网。

## 场景

- E03-1：后台配置 namespace Git 目标和npm修订，页面选择已允许地址/凭据引用；拒绝未允许远端、URL内凭据/重定向/不支持来源。配置更改不改已批准任务目标。
- E03-2：PUBLIC Claude/Codex 批准→实际Git索引及文件；两种marketplace分别解析，读回commit/原字节/可执行权限。发布1.0与1.1后旧commit仍可读，固定版本指引与索引选择一致；迟到旧任务不得回拨新选择。
- E03-3：PUBLIC Pi只用原始TGZ→内网npm，读回原TGZ逐字节和摘要匹配；派生仓库TGZ不得自动成为原始npm发行物。PRIVATE/NAMESPACE_ONLY 不进入Git/npm。
- E03-4：同名同版同字节复用、不同字节拒绝；新版本不破坏旧包；独立依赖TGZ多选、逐项检查/确认/结果，不创建插件项目。精确依赖、范围、URL/Git依赖的可用性文案保持真实。
- E03-5：服务器与安装用户认证分开；页面、安装命令、回执、审计和日志不暴露原发布秘密；无公网请求，无执行导入包脚本。Git不force-push、不采用导入包钩子/子模块。

## 证据与清理

Git目标、namespace/branch、commit/tree、两索引、存储/远端文件摘要、npm包元数据及原TGZ、渠道回执/任务ID与浏览器证据齐全。使用专用scope/仓库；不删除/覆盖已发布npm版本或重写Git历史。原生CLI的实际安装另由08验收。

## Execution log

尚未执行。领取时记录完整SHA、环境/runId、开始时间；结束追加实际命令、结果、证据、缺陷和清理，不覆盖首轮失败。
