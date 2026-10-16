# 软件更新的受控机器发版

机器入口为 `/api/v1/software-publishing/apps/{applicationId}`。部署在子路径时，服务地址必须包含子路径，例如 `https://hub.example/hub`。

软件管理员在应用的「机器发版凭据」中创建凭据，设置名称和到期时间。凭据绑定当前管理员账号、一个应用和一个 API token；只有签发响应返回原始 token。列表与审计只保留非敏感身份。普通 token 自行申请 `software:publish` 不能获得权限。机器 token 不能访问原有 `/api/v1/admin/software/**` 管理接口。

每次请求重新检查 token 的有效期和撤销状态、应用绑定、账号启用状态以及当前 SOFTWARE_ADMIN/SUPER_ADMIN 职责。撤销授权、停用账号或移除职责都会阻止后续请求。建议 CI 使用专门管理员账号、短期凭据和密钥库；不要把凭据写进仓库、命令行参数或更新清单。

## 上传完整产物

使用仓库中的 `scripts/publish-software-release.py`。Python 3.11+ 标准库即可运行。将 token 放入 `ASTRONHUB_PUBLISH_TOKEN`，再执行：

```powershell
python scripts/publish-software-release.py --server https://hub.example/hub --app 12 --manifest release.json --state release-state.json --publish
```

完整清单示例：

```json
{
  "displayVersion": "Intelli Dev 2.4.0",
  "releaseNotes": "Release notes",
  "confirmMetadata": true,
  "update": {
    "machineVersion": "2.4.0",
    "tag": "v2.4.0",
    "targetOs": "WINDOWS",
    "targetArch": "X86_64",
    "channel": "stable",
    "variant": "default",
    "protocol": "ELECTRON"
  },
  "installers": [
    { "file": "dist/app-2.4.0.exe", "path": "dist/app-2.4.0.exe", "format": "EXE", "originalFullVersion": "2.4.0", "confirmedInstallerFamily": "NSIS" }
  ],
  "assets": [
    { "file": "dist/latest.yml", "path": "dist/latest.yml", "role": "METADATA" },
    { "file": "dist/app-2.4.0.exe.blockmap", "path": "dist/app-2.4.0.exe.blockmap", "role": "BLOCKMAP" }
  ],
  "channel": { "slug": "windows-stable", "virtualProjectPath": "apps/intelli-dev" }
}
```

`file` 相对清单所在目录解析；`path` 是发布后保留的原始相对路径。安装包必须走分片上传、检测、明确确认和发布；元数据、blockmap、校验和、签名按原始字节上传。不得重新生成或改写构建产物中的摘要。

Windows Electron 更新中的每个 installer 必须显式填写 `confirmedInstallerFamily: "NSIS"`。它是发布者的独立人工声明，与 `confirmMetadata` 和 `rawVersionFields` 无关；服务端保存确认人和时间。脚本缺少该声明会在请求前失败，续发也会复核服务端声明。此记录不代表二进制检测、签名或安装验收；其他平台/协议不填写此字段。

工具默认只完成草稿校验；`--publish` 才发布不可变快照。增加 `--activate-channel` 才会把清单指定渠道指向本次快照；应用匿名源开关仍由会话管理员配置。不要把渠道自动指向“最新上传时间”。

检测未完成时退出码为 2，保留状态文件，可在检测完成后使用同一命令重试；`--wait-seconds` 可有限等待。基础校验失败阻止确认；元数据提取失败只允许在清单的对应安装包内明确提供 `rawVersionFields` 后确认。检测服务凭据不能作为发版凭据。

状态文件不含 token。它绑定服务地址、应用、清单及所有本地文件摘要；重试会复核服务端分片、包和快照，不能把同一状态文件用于不同字节。已发布快照不可替换资产，同 tag 不同内容需新版本。失败和检测历史可通过包查询读取，发布和确认沿用审计。

## 首次迁移和验收范围

服务端发布并不会自动改变已安装客户端的更新地址。Intelli Dev 首次需通过已有 GitLab 通道发出一次携带 AstronHub 地址的新客户端；兼容参数见应用更新源页面。白泽首次配置 `BAIZELINGTAI_UPDATE_FEED_URL`。迁移后才可由 AstronHub 后续快照持续更新。

第一版 GitLab 虚拟项目无私有仓库认证；内网匿名读取按应用显式开启。原客户端仓库保持只读，不启动客户端或安装器。协议测试、真实签名安装包、差分下载、断线恢复及客户端 fallback 是不同验收项，协议通过不能替代原生安装和部署验收。
