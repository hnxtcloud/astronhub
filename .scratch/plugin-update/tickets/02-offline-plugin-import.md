# 离线单包和仓库批量导入

**What to build:** 在首条单插件导入闭环上扩展 ZIP/TGZ、Codex/Pi 适配器及多插件仓库安全扫描，预览并选择创建草稿；不执行包内程序。复用第一票的安全门禁和真实证据，不创建相互阻塞的空接口。

**Blocked by:** 01

**Status:** ready-for-merger

**Spec coverage:** P2、P3

- [x] 预算、路径/链接/重复条目检查；单候选失败隔离；身份及内容冲突拒绝覆盖
- [x] 三宿主清单、资源与依赖展示；参数可配置；运行验证单独记录

## Evidence

实现于 `codex/plugin-offline-import`，基于 `49cc3fc3`，完成前合并最新集成分支；提交 SHA 由合并者补记。V76 归此票。

- 新增安全、磁盘流式 ZIP/TGZ 扫描及三宿主适配器、24 小时持久预览、逐候选选择创建草稿，版本/宿主内容冲突不覆盖，相同摘要跳过。候选独立事务；顶层安全/预算错误停止扫描。
- 原生单 Pi npm TGZ、根目录原生 Claude/Codex ZIP 保留原字节；仓库候选派生包显式标记，保留普通执行权限，并记录原始归档摘要、目录、资源和依赖声明。运行证据始终与静态检查分开。
- 后台持久配置预算，预览保留限制快照；已有旧 ZIP 入口遵守更低配置并保留 100 MiB 上限。预览包与导入版本对象独立，过期清理失败保留记录重试。
- `/dashboard/plugins` 增加 ZIP/TGZ/仓库预览、候选选择、项目/可见性选择及管理员预算配置，提供中英俄文案。新 API 全部延用会话鉴权、CSRF 和命名空间/平台权限。
- 实际 Nginx 根路径与 `/hub/` 下均允许声明 101 MiB 的导入请求进入后端（测试未发送实体大包），前缀与查询正确传递，其他入口保持全局 100 MiB。证据 `nginx-harness/ticket02-admission-report.json`。

验证：`mvn -pl skillhub-app -am -Dtest=OfflinePluginScannerTest,OfflinePluginImportIntegrationTest,PluginManagementIntegrationTest,ClaudePluginZipValidatorTest -Dsurefire.failIfNoSpecifiedTests=false test` 23 项通过：新导入 HTTP 4、扫描 6、原 Claude ZIP 9、原管理 HTTP 4。涵盖 Pi TGZ 完整字节下载、内容冲突、权限和预览所有权、单项失败隔离、预算修改与原快照、路径/链接/大小写歧义、全局展开预算、原生 ZIP 原字节、TAR 结束后隐藏归档、Codex 原生扩展资源与严格版本。

前端导入交互和原项目创建共 2 项通过；`tsc --noEmit` 与相关文件 ESLint 通过。TDD 证据含首次扫描器缺失接口红态、隐藏 TAR 红态、原生 ZIP/路径别名红态及其后绿态。根 agent 的隔离原生 Claude validate 和 Codex portable 插件加入探针提供静态格式参考，不能代替最终生成市场或真实内网安装验收。

尚未执行 Docker staging、真实内网 Git/npm 发布或宿主运行安装；这些属于后续任务。新前端命名契约由集成者使用实际 Springdoc 导出后替换为生成类型，未手改 schema.d.ts。
