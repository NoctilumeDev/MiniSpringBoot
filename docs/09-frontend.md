# 09 · React 前端 + 前后端联调（demo-frontend）

## 1. 目标

给后端接上真实浏览器端：`demo-frontend/`（React + Vite，独立 npm 项目，端口 9010），经 Vite 代理调后端 9090 JSON API。**验收的唯一事实：浏览器真实打开 React 页面；F12 Network 看到前端→后端的真实请求与响应；页面上的每一条数据都能在 MySQL 里直查到（docker exec 对比）；页面上做的每一次写操作都真实落库。**

## 2. 模块边界与依赖方向

- `demo-frontend/`：demo 轨道（双轨制），真实依赖 React + Vite，并以 `lucide-react` 提供统一线性图标；**不入 Maven modules**，与后端唯一耦合点是 HTTP JSON 契约 + Vite proxy 配置。
- 后端**零改动**为前提（联调是前端接入，不是后端改造）；验收中若暴露后端契约缺陷，按 bug 修后端而非前端绕。
- 数据流：`浏览器 → :9010（Vite dev）→ proxy /api → :9090（mini-spring）→ MySQL 容器`。

## 3. 关键设计

### 3.1 跨源方案（决策点 A，推荐 ①）

| 方案 | 理由 |
| --- | --- |
| ① Vite dev proxy（`/api` → 9090，rewrite 去前缀） | 联调标准做法；后端零改动；与 M10 生产架构同构（Nginx 反代同源——CORS 在目标架构里本就不需要），不产生「仅为联调」的内核路径 |
| ② 内核手写 CORS（响应头 + OPTIONS 预检处理） | 有教学价值（亲手理解预检机制），但 M10 Nginx 同源反代后无用武之地，且内核为此加分支违背「最小教学面」 |

### 3.2 语言（决策点 B，推荐 ①）

| 方案 | 理由 |
| --- | --- |
| ① JavaScript（jsx） | 本里程碑焦点是「前后端联调链路」而非类型系统；两三个组件的类型收益撑不起样板成本 |
| ② TypeScript | 类型安全，但 demo 轨道规模下收益有限 |

### 3.3 页面清单（决策点 C，双页操作台 + 无路由库）

- **用户管理**：列表（GET /users）+ 新建（POST）+ 编辑（PUT）+ 删除（DELETE），每次写后刷新列表；
- **转账演示**：from/to/amount 表单 + [正常转账]（验提交）/ [中途失败转账]（验回滚）双按钮 + 两账户余额卡（GET /accounts/{id}）；
- 顶部 tab 切换两页；**不引** react-router / 状态管理库 / UI 组件库（原生 fetch + 手写 CSS，图标仅用 `lucide-react`）。
- 两页共享一套低饱和星际控制舱背景和玻璃材质，但仍以数据可读性为第一约束；桌面为左右分区，窄屏按 DOM 顺序自然堆叠。
- “收起界面”是纯观景态：退出时清空当次操作反馈，隐藏 tab、工作区与运行链路，只留下可点击/可键盘展开的居中品牌，不把一套业务状态带进另一套展示逻辑。

### 3.4 错误链路（前端可诊断性与服务端信息边界）

fetch 统一封装：非 2xx → 读稳定响应正文 → 页面顶部显示 HTTP 状态与可公开文案。参数、资源不存在等
4xx 可保留面向调用方的业务说明；未处理异常和数据库/连接池故障等 5xx 固定返回通用文案，不能把
SQL、异常消息或基础设施状态一路回显到浏览器。成功提示与错误提示互斥；新操作开始、手工关闭或
进入观景态都会结束旧反馈，避免多条消息互相覆盖或跨状态残留。

## 4. demo 数据流

浏览器打开 `http://localhost:9010` → React 挂载 → GET `/api/users`（Vite 改写为 9090 `/users`）→ 表格渲染；新建/编辑/删除 → 对应 REST 调用 → docker exec 直查 MySQL 取证；转账页同理（accounts 表，V5/V6 的事实在页面上可视化复现）。

## 5. 验收清单（唯一事实约束，§1.7/§1.8 纪律）

| # | 用例 | 事实 |
| --- | --- | --- |
| V1 | React 真实渲染 | 浏览器打开 :9010，页面非空白（DOM 含表格骨架与 tab） |
| V2 | 列表=库 | 页面用户行数与内容 = `docker exec` 直查 users 表结果 |
| V3 | 新建落库 | 页面表单新建（唯一 email 锚点）→ MySQL 直查出现该行 |
| V4 | 编辑/删除落库 | PUT 后 name 变化、DELETE 后行消失（docker exec 前后对比） |
| V5 | 转账提交（前端视角） | [正常转账] → 页面两余额卡 = MySQL balance 精确变动 |
| V6 | 转账回滚（前端视角） | [中途失败转账] → 显示稳定、无内部细节的错误提示 + 余额卡不变（docker exec 复核） |
| V7 | 断库自愈（前端视角） | `docker stop minispring-mysql` → 页面显示不泄露连接池/SQL/异常消息的通用 500 → `docker start minispring-mysql` 后刷新即恢复 |
| V8 | F12 Network 全链路 | F12 看到 `/api/users` 等请求：200 + JSON 响应体（含 payload 记录为证） |

## 6. 任务清单（严格串行）

1. 脚手架：Vite + React（端口 9010、proxy `/api`→9090 + rewrite、`node_modules` 入 .gitignore）
2. fetch 封装 + 互斥操作消息 + tab 骨架
3. 用户管理页（列表/新建/编辑/删除，写后刷新）
4. 转账页（双按钮 + 余额卡，操作后刷新余额）
5. V1~V8 全跑（浏览器 F12 + docker exec 逐一对比）+ M0~M8 回归（后端 44 单测 + demo 接口冒烟）
6. roadmap / README 更新 + 三次审查记录 → tag `v0.m9`

## 7. 边界与债务

- 不做：react-router、状态管理库、UI 组件库、CSS 框架、前端单测（demo 轨道，联调为唯一目的）；`lucide-react` 仅承担图标，不承载布局或业务状态；
- CORS 显式不做（决策点 A ① 的推论）：dev 用 Vite proxy、生产用 M10 Nginx 同源反代；若未来真要跨源部署再登记新债务；
- M9 本身不做构建产物部署，只验 dev 联调链路；其 `vite build` 产物已在 M10 由 Nginx `:9080` 同源托管，见 [`10-high-availability.md`](10-high-availability.md)；
- D1（JAR 扫描）维持 M10；D47（Hikari 参数面）维持 M10 评估。
