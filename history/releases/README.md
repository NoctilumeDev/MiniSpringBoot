# 历史里程碑与维护记录

2026-10-23，维护者决定按源码教学项目维护 MiniSpringBoot，不提供软件发行版，停止使用 GitHub Releases 展示里程碑。原五条 Release 的说明和坐标在这里留档；公开 Release 条目在归档进入主线后移除，所有 Git Tag 及其指向保持不变。

阅读当前项目请从 [README](../../README.md) 和 [路线图](../../docs/06-roadmap.md) 开始。Tag 用于复现当时的源码；后续主线修订以 Git 提交为准，不把旧 Tag 当成当前主线。

当前主线的相关链接改为 Tag 或提交地址。历史 Tag 内冻结的 Markdown 仍可能引用旧 Release 页面，移除后这些链接可能失效；保留本次调整说明，不为修复历史链接而改写 Tag。

## 原始记录

[releases.json](releases.json) 保存从 GitHub API 取得的原标题、正文、公开发布时间、Release ID、原 URL、作者、附件列表，以及核对过的 Tag 提交。正文逐字保留并附 UTF-8 SHA-256；五条记录的自定义附件均为空。原 URL 是出处记录，移除 Release 后可能不可访问；请使用下表的 Tag 和提交链接。

以下内容是当时的发布记录，测试数量、依赖版本、审查结论和“当前”等措辞均属于当时的语境，本次归档不重新认证这些历史结论。JSON 时间为 UTC，下表为北京时间。

| 历史记录 | 原公开日期（北京时间） | 源码 Tag | 固定提交 |
| --- | --- | --- | --- |
| MiniSpringBoot M9 · React 全链路里程碑 | 2026-08-21 21:30:42 | [v0.m9](https://github.com/NoctilumeDev/MiniSpringBoot/tree/v0.m9) | [acac7bc](https://github.com/NoctilumeDev/MiniSpringBoot/commit/acac7bc54a84cfef91958422498bba7eaea1741f) |
| MiniSpringBoot M10 · 有界高可用与证据复验里程碑 | 2026-08-25 03:59:39 | [v0.m10](https://github.com/NoctilumeDev/MiniSpringBoot/tree/v0.m10) | [4881414](https://github.com/NoctilumeDev/MiniSpringBoot/commit/4881414f7d0ff448645374f2ce115dbb70acc82d) |
| MiniSpringBoot M10.1 · 事务连接清理维护修订 | 2026-08-25 14:50:43 | [v0.m10.1](https://github.com/NoctilumeDev/MiniSpringBoot/tree/v0.m10.1) | [9c5ef7b](https://github.com/NoctilumeDev/MiniSpringBoot/commit/9c5ef7b7868b8a5507707c2f9cbd98f73163b010) |
| MiniSpringBoot M10.2 · 独立审计正确性维护收口 | 2026-08-25 20:44:04 | [v0.m10.2](https://github.com/NoctilumeDev/MiniSpringBoot/tree/v0.m10.2) | [79430e4](https://github.com/NoctilumeDev/MiniSpringBoot/commit/79430e4fb49bd974613f5b6993ae419f281967d8) |
| MiniSpringBoot M10.3 · Windows 演练退出码可信性收口 | 2026-08-26 06:43:22 | [v0.m10.3](https://github.com/NoctilumeDev/MiniSpringBoot/tree/v0.m10.3) | [0a8c1d4](https://github.com/NoctilumeDev/MiniSpringBoot/commit/0a8c1d46e256be503ecd948d3d53e123efb08bd7) |

## 当时的说明正文

部分 API 正文含字面量 `\n`；下方仅将其展开为换行以便阅读，原始字符串保存在 JSON 中。

<details>
<summary>v0.m9 · 原说明</summary>

# MiniSpringBoot M9：React 全链路里程碑

`v0.m9` 封存了 MiniSpringBoot 的 M9 已验收里程碑：在教学型 MiniSpring 内核之上接入真实 React 前端，通过同源代理连接后端与 MySQL，把“框架机制可读”推进到“真实应用可用”。

M10 的 3 实例、Nginx 高可用和全链路终验 **不在本 Release 范围内**。

## 本里程碑包含

- React 18 + Vite 5 的独立 `demo-frontend`；
- `/api` 到 9090 的 Vite 同源代理与路径改写，后端无需为联调添加 CORS 分支；
- 用户列表、新建、编辑、删除的真实 MySQL CRUD；
- 转账提交与“扣款后失败”回滚的可视化演示；
- 统一 fetch 封装与后端可读错误横幅；
- 浏览器页面、F12 Network 与 MySQL 直查的 V1–V8 三方对照验收记录。

设计与验收记录：

- [M9 React 前端与联调](https://github.com/NoctilumeDev/MiniSpringBoot/blob/v0.m9/docs/09-frontend.md)
- [M0–M10 路线图与验收标准](https://github.com/NoctilumeDev/MiniSpringBoot/blob/v0.m9/docs/06-roadmap.md)
- [项目架构](https://github.com/NoctilumeDev/MiniSpringBoot/blob/v0.m9/docs/architecture.md)

## 发布前标签复验（2026-08-21）

在标签指向的提交 `acac7bc54a84cfef91958422498bba7eaea1741f` 上重新执行：

- Maven 11 模块构建：通过；
- Java 测试：`44/44 PASS`；
- React/Vite production build：通过；
- npm 官方审计端点（production dependencies）：`0 vulnerabilities`。

第一次复验曾因此前本地 M9 演示进程占用 9090 而停止；精确核验并只关闭该进程后，从头复跑通过。这个环境冲突不被改写成代码失败，也不从发布记录中隐去。

本轮复验确认标签源码仍可构建、测试和审计；V1–V8 的浏览器、Network 与数据库落地事实以标签内文档的原始验收记录为准。

</details>

<details>
<summary>v0.m10 · 原说明</summary>

## 里程碑范围

M10 在教学 demo 轨道完成以下有界能力与复验：

- Nginx `least_conn` + 3 个无状态应用实例；
- 本机有界容量与单实例故障切换演练；
- 事务提交/回滚证明与基线恢复；
- MySQL 故障期间 liveness/readiness 的 fail-closed 与恢复；
- VeriTrail Core 0.12 对冻结原始证据和新鲜回放事实执行 15/15 HARD 断言，裁决为 PASS。

## 可复核坐标

- 本 Release 指向最终 M10 复验提交：`4881414f7d0ff448645374f2ce115dbb70acc82d`。
- 冻结合同引用的精确源码对象：`85c2b22dfdcb17cd2527f068f85542aca25d694c`。
- 该对象通过专用标签 `m10-evidence-source-v1` 永久公开；其 tree 为 `137a0cebc49dca22508abd540c162c62f7edc5cd`。
- 主线等价提交 `582b0f53a57844c359724424b869c75088b61b50` 具有同一 tree，逐文件差异为 0；两者提交身份不同，因此冻结证据没有被改写。
- 原始清单与 VeriTrail Bundle 位于 `docs/evidence/m10/`。

## 明确边界

- 这是单机、固定预算、固定拓扑下的有界工程复验，不是生产级高可用承诺。
- VeriTrail 复验范围为 `IMPORTED_EVIDENCE_AUDIT`；Docker、Nginx 与三个 Java 进程的全拓扑生命周期所有权仍为 `NOT_PROVEN`。
- 后续公共协作、依赖维护和文档提交不属于 M10 功能里程碑本体。

</details>

<details>
<summary>v0.m10.1 · 原说明</summary>

# MiniSpringBoot M10.1 · 事务连接清理维护修订

`v0.m10.1` 是 `v0.m10` 之后的维护发布，不改写原始 M10 标签和冻结证据。

## 修订内容

- 当事务连接恢复 `autoCommit` 失败时，仍独立尝试 `Connection.close()`；
- 补充 `closeIsStillAttemptedWhenRestoringAutoCommitFails` 回归测试，锁定该失败路径；
- 保留 M10 的有界高可用、VeriTrail 导入证据与 `NOT_PROVEN` 生命周期边界；
- 同步当前仓库合同、公开措辞和维护坐标。

## 验证要求

- `python scripts/verify_repository_contracts.py`；
- Java 17 + MySQL 下 11 模块 Maven reactor、70 项测试；
- 前端生产构建与依赖审计；
- 当前提交的 GitHub Actions CI。

本发布是教学项目的正确性维护坐标，不扩大为生产级框架承诺。

</details>

<details>
<summary>v0.m10.2 · 原说明</summary>

# MiniSpringBoot M10.2 · 独立审计正确性维护收口

`v0.m10.2` 是 `v0.m10.1` 之后的维护发布，不改写原始 M10 标签、冻结源码对象或 VeriTrail 证据。它把 fresh-checkout 独立审计确认的正确性问题逐项修复并以回归测试封口。

## 修订内容

- 嵌套 `REQUIRED` 事务的内层失败会把外层事务标记为 rollback-only，禁止外层误提交；
- JDK 动态代理的 `equals`、`hashCode` 与 `toString` 保持对象身份语义，不再被业务通知拦截；
- 内嵌 HTTP 服务器停止时同步关闭自有执行器，消除残留线程；
- 同一类型出现多个 `@Primary` 候选时统一 fail-fast，不再因注入路径不同得到不同答案；
- AOP 代理销毁阶段回到原始目标对象执行生命周期回调；
- 重载 `@Bean` 方法保留精确 `Method` 身份，不再按方法名误选工厂方法；
- `@annotation(...)` 切点启动时校验目标类型必须是注解；
- JDK 代理递归收集继承接口，避免子接口场景漏失可代理契约。

## 验证证据

- `python scripts/verify_repository_contracts.py`：仓库合同通过，八个内核模块为 156 个 Java 文件、7,846 个物理行，强制传递的第三方运行时依赖为 0；
- Java 17 + MySQL 下 11 模块 Maven reactor 执行 `mvn clean install -B`，88 项测试全部通过；
- 前端执行锁文件安装、Vite 生产构建与 `npm audit --audit-level=moderate`，结果为 0 漏洞；
- 本机 M10 生产拓扑完成 Nginx + 3 实例轮询验证，三个实例均被实际命中；
- 真实浏览器完成用户新建、编辑、删除、事务提交、刻意失败回滚与界面收起/展开，控制台无 warning/error；
- MySQL 直查与页面逐项一致，临时用户已精确删除，账户余额恢复到 700 / 1300 基线。

## 边界

本发布是教学项目的正确性维护坐标，不扩大为生产级框架承诺。原始 M10 VeriTrail 复验范围仍为 `IMPORTED_EVIDENCE_AUDIT`，Docker、Nginx、MySQL 与三个 Java 进程的全拓扑生命周期所有权仍明确为 `NOT_PROVEN`。

</details>

<details>
<summary>v0.m10.3 · 原说明</summary>

# MiniSpringBoot M10.3 · Windows 演练退出码可信性收口

`v0.m10.3` 是 `v0.m10.2` 之后的维护发布，不改写原始 M10 标签、冻结源码对象或 VeriTrail 证据。它关闭最终 fresh-checkout 审计发现的唯一剩余问题：Windows PowerShell 5.1 在 `Start-Process -PassThru` 与标准输出重定向组合下，子进程结束后可能无法提供可信 `ExitCode`。

## 修订内容

- 启动有重定向的故障窗口压测进程后立即保留原生进程句柄，避免 Windows PowerShell 5.1 丢失退出码；
- 把退出码判定集中为公共 fail-closed 合同：退出码未知或非零时均拒绝生成成功摘要；
- 故障切换摘要显式记录 `loadExitCode`，使压测器成功成为可核对事实；
- 新增 Windows PowerShell 5.1 回归脚本，分别验证退出码 0 可读、退出码 7 必须失败；
- CI 在 `windows-latest` 上使用 Windows PowerShell 解析全部 M10 脚本并执行上述回归。

## 验证证据

- `python scripts/verify_repository_contracts.py`：仓库合同通过，八个内核模块为 156 个 Java 文件、7,846 个物理行，强制传递的第三方运行时依赖为 0；
- Java 17 + MySQL 下 11 模块 Maven reactor 执行 `mvn clean install -B`，88 项测试全部通过；
- 前端执行锁文件安装、Vite 生产构建与 `npm audit --audit-level=moderate`，结果为 0 漏洞；
- Windows PowerShell 5.1 回归确认重定向子进程的退出码 0 可读，退出码 7 以 fail-closed 方式拒绝；
- 按文档参数重新执行 24 并发、10 秒预热、60 秒稳态、20 秒故障时点的完整 M10 演练：19 次故障窗口代理检查 0 失败，`msb-2` 以新 PID 恢复并重新加入，账户余额保持 700 / 1300，摘要记录 `loadExitCode = 0`。

## 边界

本发布只修复演练脚本对外部压测进程结果的可信读取，不修改 MiniSpringBoot Core 语义，也不扩大为生产级框架承诺。原始 M10 VeriTrail 复验范围仍为 `IMPORTED_EVIDENCE_AUDIT`，Docker、Nginx、MySQL 与三个 Java 进程的全拓扑生命周期所有权仍明确为 `NOT_PROVEN`。

</details>
