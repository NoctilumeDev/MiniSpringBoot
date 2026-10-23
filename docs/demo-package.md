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

## 分发

`Demo package` 工作流构建并验收 ZIP，成功后保存为 Actions artifact。
它不自动创建 Release。正式发布时应从已审核的发布标签构建、验收，
再把对应 ZIP 与 `.sha256` 附到该版本，保持包内 SHA 和发布源码一致。
历史 Release 的源码与证据不因新包而改写。
