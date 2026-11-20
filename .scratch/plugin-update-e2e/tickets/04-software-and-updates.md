# 软件上传识别与多应用更新源

**Status:** claimed

**Assignee:** ZCode coding agent (adolbook)

**Result:** partial — 手工ZIP及BAIZE更新源子范围通过；2026-10-20清单补验更新草稿、原始资产、校验发布、逐应用匿名开关、下载SHA256和1.0.0→1.1.0显式渠道切换。Inspector、GitLab/Electron、Range/条件请求、不可替换治理及生产部署等仍未全部覆盖。

**Blocked by:** 01

**Layer:** L1/L2

## 前置和上下文

[软件部署](../../../docs/26-software-center-deployment.md)、[更新指南](../../../docs/skillhub/guide/software-updates.md)、[Inspector](../../../inspector/README.md)。入口 `/admin/software`、`/software`。至少两个应用、两个机器版本、独立渠道/架构。手工ZIP可先跑；自动识别缺Inspector/合法样本时对应case blocked。

## 场景

- E04-1：UI创建应用/展示版本→分片上传（乱序/重传/冲突/中断续传）→确认/发布→浏览器受控下载；真实MSI/EXE/DEB/RPM识别任务及失败分支；ZIP/DMG人工元数据不标为自动识别或验签。
- E04-2：UI创建更新草稿、关联同应用/展示版本/OS/架构的已确认已发布包，上传原始YAML/JSON/blockmap/校验/说明；保留相对目录。bytes/摘要/版本/引用不符、活动上传未结束、缺Baize说明资产必须阻止发布。
- E04-3：Windows Electron显式NSIS确认，未选不能成功；Mac必须实际引用ZIP，DMG-only拒绝；Linux Electron拒绝。若验历史缺声明快照，需提供合法旧库/升级样本，不能直接改已发布资产来制造场景。
- E04-4：匿名默认关闭→管理员逐应用开启；显式选定目标才发现版本，不自动选最高版。多应用/channel/arch/stable-preview不串；关闭/清空/下架后发现与固定资产均按规则拒绝；指针回退不代表客户端必须降级。
- E04-5：实际GitLab tags/raw（编码project/file/ref）、generic和白泽清单；GET/HEAD/单区间/多区间及完整下载摘要一致，历史精确ref与固定snapshot按治理读取；缺失文件不能返回SPA HTML200。
- E04-6：发布后资产/同机器版本/tag不可替换，原始元数据字节不改写，投影URL含正确部署前缀；可变feed ETag/no-store，目标回退不能被旧日期条件错误304；固定文件条件与If-Range另验。

## 证据与完成条件

截图/trace、上传/检测/快照/channel ID、原始metadata与实际响应、完整下载SHA256/SHA512、治理前后状态。协议通过不等于真实安装；无样本/Inspector子项明确blocked，不能勾全票passed。

## Execution log

2026-10-20补验：见[本轮报告](../manual-acceptance-results.md)。E/F编号步骤及F版本切换扩展通过。协议读取经过本轮Vite代理，不转记为Nginx部署、真实客户端安装或GitLab/Electron更新通过。保留以下2026-10-19历史记录；本轮已覆盖的BAIZE子项不再沿用当时“全部更新源缺环境”的表述。

尚未执行。领取时记录完整SHA、环境/runId、开始时间；结束追加实际命令、结果、证据、缺陷和清理，不覆盖首轮失败。

- 2026-10-19 执行（runId `e2e-20261019-100450`）。新增 `web/e2e/software-manual-zip.spec.ts`，真实 UI + API，trace 归档。
- **缺陷 DEF-03（已修复复验）**：`SoftwareAdminReleaseController.toRelease()` 硬编码 `packages=List.of()` —— 上传确认成功后 release 的包列表 API/UI 恒为空，Confirm/Publish 无法从管理页完成。修复为 `listReleases` 填充真实包列表（证据 `evidence/DEF-03-releases-packages-empty.md`）。修复需停后端重建（Windows 运行中 jar 被锁）。
- **环境阻塞 DEF-02**：BuiltinSkillInitializer 报 `error.security.scanner.required`（扫描器未提供），内置技能种子失败；应用其余功能正常 —— 登记 blocked，不算用例失败。
- 用例1（E04-1 手工链路）：SOFTWARE_ADMIN UI 建应用/release → 对话框分片上传 ZIP（真实多分片）→ Confirm（raw fields JSON 手工元数据，不标自动识别/验签）→ Publish 包 → Publish 应用（行内 Publish，出现 Take down 即成功）→ 匿名访问目录 401（内部目录契约）→ 登录普通用户可见并受控下载，字节与上传档案一致。PASS。
- 用例2（权限边界）：非管理员不能进入 admin 软件页面/调用其 API（403）。PASS。
- E04-1 自动识别分支、E04-2 更新草稿/资产、E04-3 Electron 安装确认、E04-4 更新源发现、E04-5 GitLab/generic 源与 Range、E04-6 不可替换与投影前缀：**blocked** —— Inspector worker、Nginx 同源 /updates//releases 反代、S3、真实 MSI/EXE/DEB/RPM 样本均缺失（见票01 阻塞清单）。手工确认不标为识别通过。
