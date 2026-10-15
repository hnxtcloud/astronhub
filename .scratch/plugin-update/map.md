# 内网插件市场与多应用更新源决策地图

Labels: wayfinder:map
Status: resolved
Tracker: local-markdown

## Destination

将已确认的离线插件管理及软件更新源要求固化为可实现规格，并移交 implement-spec。地图完成表示决策清晰，不表示功能已完成。

## Notes

- 沿用现有软件中心 local-markdown 跟踪方式，不发布外部 Issue。可使用 /setup-matt-pocock-skills 更改全仓库跟踪器；本次不更改其他工作的跟踪方式。
- 用户 Q1–Q28 和“确认”构成插件决定依据；GitLab 兼容和逐应用匿名开关来自两次真实用户答复，不重新访谈。
- 地图为索引；事实及决定保存在链接的规格/研究。实施使用 [独立实施票](implementation.md)，不得把已解决的决策票当实现完成票。
- 实施进度（2026-10-15）：票 01、02、04、07、08 已合入；03 下载治理、05 Git 市场和 09 Generic／白泽 feed 正在实施。其余按实施图推进，地图的 resolved 状态不随实现票关闭而替代验收证据。
- 依赖配置、凭据和真实安装样本属于运行验收条件，不是允许公网同步或远程安装的授权。

## Decisions so far

- [离线导入与版本治理](decisions/01-plugin-scope.md)：三宿主、不可变版本、手动归档导入和独立检查证据。
- [内网原生市场及 npm 依赖](decisions/02-native-channels.md)：服务器维护 Git/npm，仅 PUBLIC 插件进入公共原生渠道，分渠道恢复。
- [多应用更新协议与客户端现状](decisions/03-update-protocols.md)：优先最小 GitLab tags/raw，另外投影 Electron generic 与白泽 manifest。
- [逐应用匿名读取的边界](decisions/04-update-access.md)：默认关闭，显式启用后只开放完整已发布资产。

## Not yet specified

无范围内待确认产品决策。协议实现、真实渠道和客户端安装结果由实施验收记录，不伪装成未确定产品方向。

## Out of scope

- 公网市场同步、Skill 生命周期改造、MCP 网关、终端远程安装。
- 软件强制更新、灰度、白泽无人值守安装、完整 GitLab 产品。
- 修改客户端仓库或发放已部署应用的新构建；本次只读检查和服务端迁移说明。
