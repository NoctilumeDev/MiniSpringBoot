# M10 VeriTrail 导入证据复验

这里保存 MiniSpringBoot M10 的独立、只读证据裁决。它验证两类输入：

1. 冻结在 `85c2b22` 的 M10 自验证清单及其 UTF-8/LF 规范 SHA-256；
2. 2026-08-24 重新执行的故障切换、事务与数据库就绪恢复原始证明。

## 裁决

| 项目 | 结果 |
| --- | --- |
| Run ID | `minispringboot-m10-external-20260824` |
| ExecutionStatus | `COMPLETED` |
| Verdict | `PASS` |
| HARD 断言 | 15 / 15 `PASS` |
| Evidence gaps | 0 |
| Contamination | 0 |
| Bundle 目录校验 | 1 Run、0 issue、0 conflict |
| 边界负控 | 故意声称“全拓扑生命周期已托管”时稳定 `FAIL` |

报告入口：[`bundle/report.md`](bundle/report.md)。
边界负控报告：[`negative-control-bundle/report.md`](negative-control-bundle/report.md)。

## 诚实边界

本轮采用 `IMPORTED_EVIDENCE_AUDIT`。VeriTrail Core 0.12 读取并裁决结构化 Evidence，但没有启动、停止或拥有 Docker、Nginx、MySQL 与三个 Java 进程，因此：

- 原始证据哈希、新鲜故障回放、事务不变量和就绪恢复已验证；
- 全拓扑生命周期所有权明确为 `NOT_PROVEN`；
- 结果不能扩写为生产级多机容灾，也不能覆盖宿主机、Nginx 与 MySQL 单点边界。

Plan 中专门设置 HARD 断言 `full-topology-lifecycle-is-not-overclaimed`，要求 `full_topology_lifecycle_managed=false`。本轮另行生成合成负控，把该事实故意改成 `true`；同一份封存 Plan 返回 `FAIL / DECISIVE_ASSERTION_FAILED`，证明边界不是只写在文档里的软约定。

## 文件

```text
veritrail/
├── plan.json          # 可审阅的 Plan 0.1
├── sealed-plan.json   # VeriTrail 封存结果
├── evidence.json      # 由适配器生成的结构化 Evidence
├── replay/            # 本轮四份原始回放证明
├── bundle/            # 正向 PASS 的不可变报告、Evidence 与清单
└── negative-control-bundle/ # 越界声明必须 FAIL 的负控 Bundle
```

## 复现

先由 M10 自有脚本启动拓扑并把新一轮证明写入一个独立目录，再执行：

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File deploy/m10/new-veritrail-imported-evidence.ps1 `
  -ProofDirectory <fresh-proof-directory> `
  -OutputPath <new-evidence.json>

<veritrail-python> -m veritrail.cli seal `
  --plan docs/evidence/m10/veritrail/plan.json `
  --output <new-sealed-plan.json>

<veritrail-python> -m veritrail.cli evaluate `
  --plan <new-sealed-plan.json> `
  --evidence <new-evidence.json> `
  --output <new-bundle-directory> `
  --run-id <new-run-id> `
  --execution-status COMPLETED
```

所有 `<...>` 都必须替换成调用者显式提供的新路径或新 ID；VeriTrail 会拒绝覆盖既有封存输出。
