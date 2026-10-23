# MiniSpringBoot 预编译演示包

用浏览器体验用户 CRUD 与转账提交 / 回滚。包内已包含 Java 编译产物、运行依赖和
React 静态页面，使用者无需安装 Maven、Node.js 或 Java。

这是单实例教学 demo。M10 三实例、容量和故障切换演练仍以源码仓库文档为准。

## 启动

需要已正常运行的 Docker（Linux containers）和 Docker Compose v2，支持 `up --wait`。
首次启动需要联网拉取 MySQL、Java 17 和 Nginx 镜像；ZIP 不包含镜像，不是离线安装包。

1. 完整解压 ZIP，进入包含 `compose.yaml` 的目录。
2. 在该目录的终端执行：

   ```sh
   docker compose up -d --wait --wait-timeout 180
   ```

3. 打开 <http://127.0.0.1:19090>。首次初始化的两个账户余额各为 1000。

页面可新建、编辑和删除演示用户；转账页可对照正常提交和“中途失败”后的余额。
数据写入本包独立的 MySQL 卷，关闭页面或重启容器不会清空。

若端口被占用，复制 `.env.example` 为 `.env`，修改 `DEMO_PORT` 后重试。
默认 Compose 项目名为 `minispring-demo`；同时运行多份时，也须分别修改
`COMPOSE_PROJECT_NAME`。解压到新目录本身不会创建一套独立数据库。

## 停止与排障

```sh
docker compose ps
docker compose logs --tail 100
docker compose down
```

`down` 保留数据库。只有明确要删除本演示数据、重新初始化时，才运行
`docker compose down -v`；下次启动将恢复两账户各 1000 的初始数据。

启动超时后先查看日志，修复原因再运行启动命令；失败现场不会被脚本自动清除。
若 Docker daemon 自身无法连接，先恢复 Docker，再尝试本包。

本包使用公开的演示数据库口令，仅将网页端口绑定到本机回环地址；
MySQL 和 Java 服务不发布宿主端口。只放演示数据，当前 demo 没有用户登录鉴权。

## 版本与内容

`BUILD-INFO.json` 记录源码提交、精确标签（存在时）及是否含未提交改动。
`SHA256SUMS` 覆盖包内文件，ZIP 旁的 `.sha256` 用于下载校验。
体验前应核对构建产物记录与包内源码提交；标有 `dirty` 的包仅用于本地开发验证。
本包是教学 demo 的构建产物，项目不提供软件发行版。

- `app/`：demo 编译后的类与资源。保留目录形式以符合当前组件扫描方式。
- `lib/`：框架和第三方运行依赖，保持原 JAR 与其中的许可声明。
- `frontend/`：预构建的浏览器页面。
- `mysql/init.sql`：从对应源码提交复制的初始化 SQL。
- `licenses/frontend/`：前端运行依赖的版本和许可文件；项目许可见 `LICENSE`。

构建与验收说明：
<https://github.com/NoctilumeDev/MiniSpringBoot/blob/main/docs/demo-package.md>。
