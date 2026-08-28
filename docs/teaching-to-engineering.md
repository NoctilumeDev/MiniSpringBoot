# 从教学机制到工程约束：一次主动停止的 hardening 实验

## 1. 这份记录是什么

MiniSpringBoot 的教学主线回答的是：IoC、AOP、MVC、JDBC 与事务这些机制，本质上怎样工作。

2026-08-26 的一次归档审查中，工作范围从验证现有教学合同扩展到为事务失败、池化资源、HTTP 容量、异常语义和输入边界增加实现。识别到目标已经从“归档教学项目”漂向“继续建设工业框架”后，这条实验路线被冻结。

这段过程没有被包装成预先设计好的路线，也没有因为已经写出代码就获得进入主线的权利。它作为一个真实中间态被保留，用来说明：

1. 教学实现不是工业版的未完成前身；它在明确的教学边界内选择不覆盖完整工业失败空间；
2. 工业复杂度往往不是来自核心算法，而是来自失败、并发、资源耗尽、恶意或歧义输入、恢复、兼容性与长期演化；
3. 工程化也不是越多越好。当新增复杂度开始遮蔽教学目标时，停止本身就是工程判断。

## 2. 可核对的历史坐标

| 项目 | 坐标 / 状态 |
| --- | --- |
| 教学基线 | `0a8c1d46e256be503ecd948d3d53e123efb08bd7`（2026-08-26 当时的 `main`，`v0.m10.3`） |
| 原始实验本地 ref | `archive-hardening/minispring-system-boundaries` |
| 原始实验分支头 | `870c89080621b8d554e2050dded4d2da76300ebd` |
| 公开归档载体 | annotated tag `archive/raw-hardening-experiment-2026-08-26`；tag object `9d806f677505feb24d947315fac473d121dfcca3`，解引用到上述实验头 |
| 远端保护 | active tag ruleset [`21699535`](https://github.com/NoctilumeDev/MiniSpringBoot/rules/21699535)，精确命中该 tag，只限制 update / deletion，无 bypass |
| 离线载体 | `MiniSpringBoot_raw-hardening-experiment_2026-08-26.bundle`；SHA-256 `9ebaa9bfbbfe5c4d9447e10b7107a60c8005037360a9a5ac71e2652fb812d76a` |
| 与教学基线的关系 | 从上述教学基线分叉；未合入 `main` |
| 归档裁决 | 冻结为工程化候选证据；不是受支持版本，不是 Release，不是生产就绪证明 |

2026-08-26 初次读回时，远端没有 `archive-hardening/minispring-system-boundaries` 或 `docs/raw-hardening-experiment` ref，也没有观察到对应 PR、`main` 合入或 Release。这个结论只描述该时间点可见的远端事实，不能证明历史上从未出现过短暂远端引用。当时实验头 `870c890` 只由本地 ref 保持可达；这不影响教学版的构建、运行或阅读。

2026-08-28 本轮操作记录显示：最终治理阶段先建立了精确 tag ruleset，再创建上述公开归档 tag。经禁用凭据助手的 fresh HTTPS clone 读回，公开 tag object 为 `9d806f6`，并可检出实验提交 `870c890`；远端 `main` 仍为教学基线 `0a8c1d4`。当前公开状态能独立证明两者同时存在，不能单凭最终状态重建创建先后。tag 是 annotated 但未签名，因此本文不把它扩大解释为签名来源证明。ruleset 防止普通 ref 更新与删除，但管理员仍有修改仓库治理配置的能力；半年后的复核仍应同时比对 tag object、解引用提交和 ruleset 事实。

原始实验分支刻意保留当时的施工状态。停止说明作为独立文档变更审议，不反向改写那五个实验提交。

固定 SHA 只解决“对象身份不漂移”，不负责把对象带到新的克隆。因此本次同时保留了三层：本地原始分支用于继续查看施工轨迹；公开且明确标为实验的 tag 供陌生读者取得对象；带 SHA-256 的 Git bundle 用于作者离线恢复。`git bundle verify` 确认 bundle 历史完整，包含 `refs/heads/main` 与公开实验 tag；显式以 `--branch main` fresh clone 后，教学基线和实验对象均可检出。bundle 不携带默认远端 HEAD，故无参数 clone 会停在“未检出工作树”的状态；这是操作条件，不应被写成无条件的一键恢复。

## 3. 同一机制，新增的是失败空间

| 教学版要讲清的机制 | 现实环境追加的问题 | 原始实验尝试表达的约束 | 应继续对照的成熟工业实现 |
| --- | --- | --- | --- |
| `begin → business → commit / rollback` | `commit` 可能失败，`rollback` 也可能失败，最终状态可能未知 | 显式事务终局、异常优先级、不可安全复用连接的丢弃语义 | Spring 的事务基础设施、JDBC 驱动与数据库的真实失败语义 |
| `DataSource#getConnection()` 与释放连接 | 池返回的是可复用资源；污染连接放回池会影响后续请求；可选依赖可能穿透公共类型 | 数据源所有权包装、连接丢弃能力、classpath 隔离 | HikariCP 等连接池的生命周期与驱逐语义、Spring Boot 条件化自动配置 |
| HTTP 请求分派到 Controller | worker、queue、backlog、请求体与输出都不是无限资源 | 有界执行器、`CallerRunsPolicy` 反压、有限 TCP backlog、请求体上限与状态码区分 | Servlet 容器或 Netty 的容量、超时、背压与生命周期模型 |
| JSON 能解析和序列化 | 重复键、非法编码、循环对象、深度与输出膨胀会制造歧义或耗尽资源 | 更严格的解析与序列化边界 | Jackson 等成熟实现的流式解析、约束配置与兼容性处理 |
| 异常映射为 HTTP 响应 | 400、413、500 的语义不同；5xx 细节不应直接泄露；可观测性仍需保留 | 统一错误形态与服务端错误脱敏候选 | 成熟 Web 框架的异常解析、日志关联与可观测体系 |
| CI 执行仓库检查 | workflow 存在不等于安全已审计，绿灯也不能扩大为未观察事实 | 一份 CodeQL / dependency review workflow 候选 | 独立的仓库治理、安全策略、告警审查与发布供应链 |

左右两边的核心机制没有变。变化的是现实世界把“通常成功的主路径”扩展成了必须解释的状态空间，并要求系统为每一种不能忽略的结果付出代码、测试、运维和认知成本。

## 4. 五个提交实际记录了什么

下表只列与教学对照和归档裁决直接相关的代表性内容，不代替完整 diff 清单。例如 `e6eebb6` 还加入了池容量硬边界，`c33f50a` 还把默认 HTTP 绑定地址收窄到 loopback。

| 提交 | 内容 | 归档裁决 |
| --- | --- | --- |
| `852c85a` | 显式事务终局、事务系统异常、连接丢弃接口及故障测试候选 | **DEFERRED ENGINEERING EVIDENCE**：保留为事务失败空间的实现样本，不进入教学主线 |
| `e6eebb6` | 池化连接所有权、可选依赖隔离及真实 MySQL 测试候选 | **DEFERRED ENGINEERING EVIDENCE**：展示资源所有权复杂度，不作为生产完成态 |
| `c33f50a` | HTTP 资源上限、JSON 边界、错误语义及相关测试候选 | **DEFERRED ENGINEERING EVIDENCE**：有教学对照价值，但改动面已经越过归档收尾 |
| `0596a6e` | 纠正教学基线既有的配置优先级、命令行/Profile 能力与生命周期文档；Java 文件只改 JavaDoc 与末尾换行 | **APPROVED AS AN INDEPENDENT CLAIM CORRECTION**：经独立事实核对后，其等价补丁与本文一同审议；这不构成对其余 hardening 的认可 |
| `870c890` | CodeQL 与 dependency review workflow 候选 | **SEALED / NOT ADOPTED**：未合入 `main`，也未产生 GitHub 运行或告警审核证据；不得据此宣称安全通过 |

这些提交能证明“方案曾被实际写出并形成可审查 diff”，不能单独证明：

- 方案已达到生产质量；
- 所有故障模型都已覆盖；
- 测试绿色等于真实运行、恢复与长期兼容性成立；
- 新增安全 workflow 等于仓库已经完成安全审查；
- 复杂度的收益大于它对教学可读性和维护面的成本。

## 5. 为什么在这里停止

继续补齐这条路线并非理论上做不到，但目标会发生性质变化：项目将开始建设一个新的、仍远不及成熟生态完整的工业框架，同时牺牲 MiniSpringBoot 最重要的资产——机制清晰、边界透明、能够逐层读懂。

因此停止条件不是“代码写不下去”，而是：

> 实验已经足以展示工业复杂度如何出现；继续施工不再为教学目标提供成比例的新证据。

后续不以“把 Engineering Edition 做完”为目标，也不因为候选测试通过就把实现合回 `main`。只有未来项目目标被明确改写、每项复杂度都有现实需求和独立证据、并重新承担完整验证成本时，才应另行立项评估。

## 6. 给读者的对照方法

不要只比较代码行数，也不要把“封装更多”自动理解为“更高级”。阅读每一项候选增强时，依次问：

1. 教学版正在表达哪个最小机制？
2. 哪个真实失败条件迫使实现增加状态或边界？
3. 原实现若遇到该条件，会产生什么可观察后果？
4. 候选实现增加了什么约束，又付出了哪些可读性、耦合和验证成本？
5. Spring Framework、HikariCP、成熟 Servlet/Netty 容器或 Jackson 如何处理同一类问题？
6. 工业实现还覆盖了哪些本实验没有资格宣称完成的情形？

这条阅读路径的终点不是“照着候选分支继续造完”，而是理解为什么真正的工业框架会长成今天的样子。

公众可先从公开仓库取得实验对象，并核对 tag 身份：

```bash
git clone --no-checkout https://github.com/NoctilumeDev/MiniSpringBoot.git
cd MiniSpringBoot
git rev-parse refs/remotes/origin/main
git rev-parse refs/tags/archive/raw-hardening-experiment-2026-08-26
git rev-parse 'refs/tags/archive/raw-hardening-experiment-2026-08-26^{}'
git switch --detach archive/raw-hardening-experiment-2026-08-26
```

持有离线载体时，应以绝对路径指定 bundle，先核对 SHA-256，再显式指定教学主分支完成 fresh clone。`git bundle verify` 需要在一个现有 Git 仓库中运行，因此下例先 clone、再通过 `-C` 在新仓库里校验；bundle 没有可供 Git 自动猜测的默认远端 HEAD：

```bash
BUNDLE=/absolute/path/MiniSpringBoot_raw-hardening-experiment_2026-08-26.bundle
sha256sum "$BUNDLE"
git clone --branch main "$BUNDLE" MiniSpringBoot-offline
git -C MiniSpringBoot-offline bundle verify "$BUNDLE"
git -C MiniSpringBoot-offline switch --detach archive/raw-hardening-experiment-2026-08-26
```

只有当 Git 对象库同时包含两个固定提交时，以下只读命令才能核对原始轨迹；先检查可达对象，再读取提交和 diff：

```bash
git cat-file -e 0a8c1d46e256be503ecd948d3d53e123efb08bd7^{commit}
git cat-file -e 870c89080621b8d554e2050dded4d2da76300ebd^{commit}
git log --reverse --oneline 0a8c1d46e256be503ecd948d3d53e123efb08bd7..870c89080621b8d554e2050dded4d2da76300ebd
git diff --stat 0a8c1d46e256be503ecd948d3d53e123efb08bd7..870c89080621b8d554e2050dded4d2da76300ebd
git diff 0a8c1d46e256be503ecd948d3d53e123efb08bd7..870c89080621b8d554e2050dded4d2da76300ebd
```

上述公开 fresh clone、ruleset 读回、bundle 校验与显式分支 clone 已在 2026-08-28 完成。它们证明本次归档时的对象身份与可达性，不承诺 GitHub、作者离线介质或未来工具行为永远不变；复核者仍应重新执行这些读回，而不是只相信本文。

## 7. 最终边界

- `main` 继续代表 Teaching Edition；
- 原始 hardening 分支代表一次被冻结的工程化实验，不是平行维护的产品线；
- 本文解释实验的证据价值与停止理由，不替实验补完实现；
- 安全扫描与 GitHub 仓库治理属于独立验收域，不由这段实验代替；
- 实验的公开可复现性由固定 SHA、受规则约束的公开 tag 与离线 bundle 共同承担；三者各自仍需实际读回，不能互相代替；
- “做过”不等于“应该合入”，“能够继续”也不等于“现在应该继续”。

MiniSpringBoot 负责把机制讲清楚；这段实验负责展示现实为何让机制周围长出复杂度；真正完整的工业答案，应继续到成熟实现中寻找。
