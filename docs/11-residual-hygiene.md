# 遗留物收口门禁

本门禁在实现、测试、验证和文档读回之后运行。它不证明 MiniSpring 的容器、AOP、Web、
JDBC 或高可用行为正确，只阻止已失去职责的生成物和施工残留继续进入下一阶段。

## 本仓库的所有权判断

- `demo-frontend/src/assets/` 与 `docs/screenshots/` 由当前前端和 README 消费，属于产品与
  展示资产；
- `docs/evidence/m10/` 由 M10 清单、SHA-256 校验和 VeriTrail Bundle 持有，属于冻结
  Evidence，不按 `.log`、`.json` 等扩展名粗暴删除；
- `target/`、`dist/`、`coverage/`、`node_modules/`、测试输出和临时备份均可重建，不能
  进入当前 Git 树。

两份 M10 负载日志虽然使用 `.log` 扩展名，但已经进入冻结证据清单并受哈希校验。因此
`.gitignore` 只对这两个精确路径声明例外；以后出现的新日志仍默认被忽略，不能借这个例外
取得保留资格。

## 自动门禁

`python scripts/verify_repository_contracts.py --hygiene` 会只读检查：

- Git 跟踪但仍匹配忽略规则的文件；
- `target`、`dist`、`coverage`、`node_modules` 与 `test-results` 中的生成物；
- `.tmp`、`.bak`、`.orig`、`.rej` 与编辑器备份文件。

默认的 `python scripts/verify_repository_contracts.py` 和 GitHub CI 都包含这项检查。M10
Evidence 的内容身份继续由现有 manifest 哈希门禁负责；本门禁不重复发明第二套清单。

当前盘点没有需要删除的受跟踪残留物。Docker 容器、MySQL 数据、运行中 Java 进程、
`node_modules` 和 Maven 输出属于本机工作区状态；清理前须先确认不存在唯一事实，CI 不声明
用户机器已经进入休眠状态。
