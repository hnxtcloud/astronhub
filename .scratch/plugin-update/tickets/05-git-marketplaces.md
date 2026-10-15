# 内网 Git 原生市场发布

**What to build:** 配置 namespace 内网 Git 仓库并推送 Claude/Codex 原生文件及各自市场索引，显示安装指引。

**Blocked by:** 01,02

**Status:** ready-for-agent

**Migration reservation:** V80

**Spec coverage:** P6

- [ ] 仅 PUBLIC 内容进入首版 public Git；市场名称和原生插件名称冲突检查
- [ ] 不变 commit 记录；两种索引样例验证；实际 Git 与原生宿主验收单独记录

## Implementation context

- 接入票 04 的不可变渠道计划及单队列适配器，不另建调度器；工作树外 `plugin-update-notes/ticket04.md` 和 `native-channels.md` 提供具体接口和真实 CLI 探针位置。
- 票 03 使用持久审批任务 ID 排序发布请求，release ID 兜底，旧无任务发布版本优先级较低；Git 当前索引采用同一规则，迟到旧请求只保留历史内容，不回拨当前索引。顺序不能由网络完成时间推断。
- Git 对象写入不得执行归档中的 hooks、过滤器、子模块或配置；保留原生文件字节，不能把原始包内的 Git 元数据变成仓库控制文件。未支持内容明确拒绝并保留错误证据。

## Evidence

尚未实现；完成时追加行为验证、测试、提交及真实环境限制。未经验证不得勾选验收项。
