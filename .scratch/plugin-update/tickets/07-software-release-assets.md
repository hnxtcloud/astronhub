# 软件发行资产上传与完整发布

**What to build:** 软件管理员维护多应用更新版本/渠道/虚拟项目，上传一组发行文件，校验引用后形成不可变完整快照。

**Blocked by:** None

**Status:** done

**Assignee:** software_ticket07_resume (codex/software-release-assets)

**Spec coverage:** U1、U2、U6（页面部分）

- [x] 安装包复用原确认和容量；辅助资产/临时对象入账；路径/SHA256/SHA512/bytes 核对
- [x] 更新机器版本与展示版本分离；macOS 目标；匿名默认关闭；管理页面可操作

## Evidence

2026-10-15：完整发布切片已在 `codex/software-release-assets` 实现并验证，由合并代理以 `95172ec4` 合入集成分支。基础提交 `33af475f`、管理/持久化清理提交 `4f524920`、硬化提交 `3bc4cdda`，完整工作分支 tip 为 `86d22c89`。

- 软件管理页可在空展示版本上传包、创建机器版本草稿、关联已确认并发布的安装包、批量上传同一快照的辅助文件、校验/发布/撤出；应用面板提供默认关闭的匿名开关和独立渠道目标/虚拟项目配置。原管理路由仍为会话、CSRF 和 SOFTWARE_ADMIN/SUPER_ADMIN。
- 快照使用独立生命周期、固定文件身份与 SHA256/SHA512-base64，关联包不重复计费。macOS ZIP/DMG 复用手动确认；不宣称原生签名或自动检查完成。机器版本和 tag 均在完整目标元组上唯一，v/V 别名不能替换同版本内容；过长 tag 在边界返回 400。
- 辅助对象的已用/预留容量在删除或严格 abort 失败时保留；持久化状态供后续清理重试。每个有界存储操作和完成阶段与清理互斥，租约刷新后再次核对，防止过期写入越过已清理容量边界。已支持安装格式后缀不能伪装为 OTHER 跳过包确认，HTTP 拒绝时不预留容量。
- 原始 Electron YAML 验证机器版本、目标安装包、大小、SHA512、旧 path 一致性；支持 macOS ZIP+DMG 和包尾嵌入 blockMapSize，不将其解释为外部 blockmap 长度。未支持 web-installer packages 会明确拒绝。Baize JSON 验证发布时间、piVersion、目标、相对同源链接、SHA256/bytes；CHECKSUM 与实际资产交叉核对。相对引用按元数据父目录解析，允许一次解码的 UTF-8 文件名，拒绝远程地址、路径穿越和编码别名。
- 最终 reactor：S3 adapter 8、domain 2、原生元数据 8、管理/本地存储 HTTP 6、真实 Springdoc 导出 1，共 25 项通过，0 failures/errors；不是仅运行 controller mock。关键 red 证据包括缺元数据、摘要不符、无权/CSRF、安装后缀伪装、同版本 tag 别名和超长 tag。
- 管理页面交互和既有页面 8 项通过；真实 Springdoc 3.0.1 导出 394 paths / 420 schemas 后运行 openapi-typescript，生成类型已更新，完整 tsc 和修改范围 ESLint 通过。未手工编写生成契约。
- 操作手册：[软件更新快照发布](../../../docs/skillhub/guide/software-updates.md)。日志为工作树忽略文件 `.scratch-software-verified.log`、`.scratch-software-ui-verified.log`、`.scratch-software-final-generated-{typecheck,lint}.log`；外部上下文见 `C:/Users/adolbook/.codex/worktrees/plugin-update-notes/ticket07.md`。
- 合并代理核查实际最终日志：25 项后端/契约导出均通过，页面 8 项通过，生成后类型检查和定向 lint 无错误，合并最新前端变更后类型检查无错误。合并无冲突、`git diff --check` 通过；用户 `CONTEXT.md` 与无关未跟踪文件共 90 个 SHA256 保持一致。未额外重跑构建或声称真实生产环境通过。

限制：本票的数据库验收使用 H2 PostgreSQL mode 的实际 Spring/JPA 与本地存储；Flyway V75 的真实 PostgreSQL、真实 S3、容器 staging、GitLab/generic/manifest 读取、机器发布和 Windows/macOS 原生更新安装由后续票/部署验收覆盖。没有修改或安装任一真实客户端，不以本票服务端通过冒充 U7 完成。
