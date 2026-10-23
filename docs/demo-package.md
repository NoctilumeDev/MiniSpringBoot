# 预编译 demo 包

演示包用于让读者省去源码编译，直接体验已有的用户 CRUD 和转账提交 / 回滚。
采用一个 Java 实例、一个 MySQL 和一个 Nginx；它不替代 M10 三实例演练。

使用者只需 Docker / Compose v2，无需 Maven、Node.js 或宿主 Java。
首次启动仍需联网拉取镜像。完整操作见
[包内使用说明](../deploy/demo-package/README.md)。

## 构建

维护者需要 Git、JDK 17、Maven、Node.js 22.12+ 或 24、Python 3.10+。
在干净的源码提交上执行：

```sh
python scripts/build_demo_package.py
```

脚本从零构建 Java 和前端，收集运行依赖、许可证、初始化 SQL 与 Compose 配置，
输出到 `dist/demo-package/`。构建阶段跳过需要 MySQL 的 Java 测试；
测试仍由现有 CI 执行，包自身还必须通过下面的解压运行验收。

本地开发时可加 `--allow-dirty`，包名和元数据会显式标记未提交状态；
这种包只作本地预览。已提交的包记录完整源码 SHA，若提交有精确标签也一并记录。
ZIP 旁的 SHA-256 校验文件与包内文件清单用于检查下载和解压内容。

demo 类保留目录形式，符合当前文件系统组件扫描方式；框架与第三方依赖使用原 JAR，
自动配置资源保持各自独立。包内不带本机 Maven 仓库路径、源码 checkout 或 node_modules。

## 从实际 ZIP 验收

```sh
python scripts/verify_demo_package.py dist/demo-package/minispringboot-demo-<提交前12位>.zip
```

验证器先核对校验和，再解压到源码目录以外、名称含空格的新目录，并创建独立的
Compose 项目、空数据库卷和本机随机端口。它检查：

- 前端入口与构建资产、后端与真实 MySQL 就绪。
- 用户新增、修改、删除及 SQL 落地。
- 正常转账提交、刻意失败回滚，并对照 SQL 余额。
- 停止后重新启动，用户和余额仍在；随后恢复演示基线。

成功后回收本次测试专属的容器与卷；`--keep-running` 可保留成功实例供浏览器验收。
失败时保留该测试环境、日志和结果路径，定位后再清理该专属项目。
验收结果只证明当前包在所记录环境下的这些行为。

## 获取演示构建产物

在 [Demo package Actions](https://github.com/NoctilumeDev/MiniSpringBoot/actions/workflows/demo-package.yml)
中选择 `main` 分支的成功运行，下载 `minispringboot-demo-<源码 SHA>` 构建产物。
解开 Actions 下载的外层归档后，使用其中的演示 ZIP 和 `.sha256` 校验文件。
产物保留 14 天；[下载需要登录 GitHub](https://docs.github.com/en/actions/how-tos/manage-workflow-runs/download-workflow-artifacts)。

这些产物用于体验教学 demo，项目继续以源码、Git Tag 和里程碑文档维护，不创建 GitHub Release。
体验前核对构建产物名称中的源码 SHA 与包内 `BUILD-INFO.json` 一致；
历史里程碑与调整说明见 [历史记录](../history/releases/README.md)。
