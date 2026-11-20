# AstronHub CLI 内网发布包

将整个 ZIP 拷贝到内网并解压。内网电脑需要 Node.js 22+ 和 npm；无需源码、Bun 或重新构建。
CLI 自身的 JavaScript 依赖已经打包，发布包没有需要额外下载的 dependencies。
包内包含 npm 安装包、跨平台发布脚本、release.json 和 SHA256SUMS；不包含账号、token 或个人 npm 配置。

## 1. 确认上传仓库地址

截图中的 `http://22.56.110.101:19080/repository/npm-public/` 是已知的下载入口。
如果它在 Nexus 中是 npm **group** 仓库，发布时应改用 npm **hosted** 仓库地址。
请在 Nexus 的 Repositories 中确认类型和 URL，或者向管理员索取可写的 npm hosted 地址。
下文的 `npm-hosted` 仅为示例名称，并未确认该服务器实际存在此仓库。
若希望继续从 npm-public 下载，请让管理员将上传的 hosted 仓库加入 npm-public 分组。

## 2. 在内网登录

打开解压目录中的终端。先确认 Node 和 npm 可用：

```text
node --version
npm --version
```

将下面地址替换为实际的 hosted 仓库地址，然后执行：

```text
npm login --registry="http://22.56.110.101:19080/repository/npm-hosted/" --auth-type=legacy
```

在 npm 提示中输入内网账号和密码。若企业使用 token，使用企业规定的用户级 `.npmrc` 配置即可。
发布脚本使用 npm 现有认证，不会收集账号或将凭据写入发布包。

## 3. 先预览，再上传（Windows / Linux / macOS 通用）

```text
node publish.mjs --registry="http://22.56.110.101:19080/repository/npm-hosted/" --dry-run
node publish.mjs --registry="http://22.56.110.101:19080/repository/npm-hosted/"
```

脚本先核对安装包的 SHA-256，再调用 npm publish。`--dry-run` 不上传。
上传目标必须显式传入；脚本不会采用 npm 默认 registry，也不支持发布到公共 npm。
脚本跳过发布生命周期脚本并关闭 provenance，无需构建工具或外部签名服务。
发布使用 latest 标签；若仓库已有同名同版本，按私服版本策略处理，通常应增加版本后重新制作发布包。

也可以在核对 SHA256SUMS 后直接使用 npm 上传安装包（文件名以 release.json 为准）：

```text
npm publish ./astronhub-cli-0.1.0.tgz --registry="http://22.56.110.101:19080/repository/npm-hosted/" --ignore-scripts --provenance=false
```

本机的预览验证不代表已上传到真实内网仓库；以实际发布成功和下述查询结果为准。

## 4. 验证下载入口并使用

确认 npm-public 分组已包含上述 hosted 仓库后，查询：

```text
npm view astronhub-cli@0.1.0 version --registry="http://22.56.110.101:19080/repository/npm-public/"
```

返回 `0.1.0` 后，可以通过以下任一方式使用。

全局安装（无需修改默认 registry）：

```text
npm install -g astronhub-cli@0.1.0 --registry="http://22.56.110.101:19080/repository/npm-public/"
astronhub --version
astronhub config set npmRegistry http://22.56.110.101:19080/repository/npm-public/
```

进入需要添加依赖的项目目录：

```text
astronhub install @types/node@26.6.5 -D
astronhub install @types/node@26.6.5 -D --pm pnpm
```

临时运行，无需全局安装：

```text
npx --registry="http://22.56.110.101:19080/repository/npm-public/" astronhub-cli@0.1.0 --help
pnpm --registry="http://22.56.110.101:19080/repository/npm-public/" dlx astronhub-cli@0.1.0 --help
```

上面入口处的 registry 用于下载 CLI 自身。CLI 操作 Node 依赖时，另通过 `astronhub config set npmRegistry ...`
或 `astronhub install ... --registry=...` 指定依赖私服。
Skill 安装使用独立的 SkillHub 服务：`astronhub config set skillRegistry <实际服务地址>`。

## 没有上传权限时本地试用

```text
npm install -g ./astronhub-cli-0.1.0.tgz --ignore-scripts --no-audit --no-fund
astronhub --version
```

该步骤只安装 CLI 本身。安装业务依赖、搜索/下载 Skill 时，仍需要相应的内网服务和权限。
