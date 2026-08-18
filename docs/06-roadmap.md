# 06 · 路线图与验收标准（M0 → M10）

> 推进方式：**严格串行**。前一里程碑未「落地验收」通过，后一个绝对不启动——不跳、不并行、不提前埋后续的坑。

---

## 1. 验收总则（规矩，先锁死）

1. **落地为准**：里程碑通过 = 真实可观测结果落地（真实 `main` 启动 / 浏览器真实访问 / 数据库真实落库）。**单测与日志只作最低基线，不作为验收依据。**
2. **分阶段落地口径**：无 Web/数据库的里程碑（M1~M4、M6、M7）以「真实 `main` 启动 + 运行期真实行为」验收；自 M5 起用浏览器 F12 实证；自 M8 起用数据库真落库实证。
3. **严格串行**：M0→M10 顺序推进，未经前述落地验收，绝不触碰后续代码。
4. **双轨制**：框架内核（M1~M7）零第三方依赖；demo 层（M8~M10）可引真实依赖（MySQL 驱动 + HikariCP）。
5. **接口契约**：跨模块只依赖下层公开接口，禁止触碰实现类；内部重构不改接口签名。

---

## 2. 里程碑总览

```
 M0  环境就绪 + 规矩冻结 + 方案审批
 M1  IoC 容器（core）
 M2  注解与类扫描（context）
 M3  AOP（aop）
 M4  外部化配置（config）
 M5  Web/MVC + 内嵌服务器（web）
 M6  自动配置 + Starter（autoconfigure）
 M7  启动器 + 事件 + 后端 demo 收口（boot）
 M8  数据库接入（MySQL + HikariCP）
 M9  React 前端 + 前后端联调
 M10 3 实例 + Nginx 高可用 + 全链路终验
```

顺序：仅线性，M_{n+1} 依赖 M_n 已落地验收。

---

## 3. 各里程碑产出与落地证据

### M0 · 环境就绪 + 规矩冻结 + 方案审批

- **产出**：工具链确认（JDK17 / Maven / Node / MySQL / Docker / 内置浏览器均已就绪）；「落地验收 / React / 串行 / 接口契约」写入文档；本里程碑表获批。
- **落地证据**：文档更新为 React + 落地口径，git 提交，方案获用户批准。

### M1 · IoC 容器

- **产出**：`BeanDefinition` / `BeanDefinitionRegistry` / `DefaultListableBeanFactory` / 三级缓存 / 生命周期 / `BeanPostProcessor`。
- **落地证据**：demo `main` 真启动，依赖注入成功、循环依赖 `A↔B` 正确解开、`@PostConstruct/@PreDestroy` 真实回调。

### M2 · 注解与类扫描

- **产出**：`@Component/@Service/@Repository/@Configuration/@Bean`、`@Autowired/@Qualifier/@Primary`、`@Scope`、`ClassPathScanner`。
- **落地证据**：demo 用注解真实扫描出 Bean 并完成注入，main 运行可观察。

### M3 · AOP

- **产出**：`Pointcut/Advice/Aspect/Advisor`、`@Before/@After/@Around`、JDK 动态代理。
- **落地证据**：demo 里 `@Around` 计时切面真实拦截目标方法，耗时真实打印。

### M4 · 外部化配置

- **产出**：`Environment/PropertySource`、properties/yaml 解析、`@Value` 占位符与默认值、Profile。
- **落地证据**：demo 从 `application.yml` 真实读值注入字段；切换 profile 后值真实变化。

### M5 · Web 与 MVC

- **产出**：`WebServer` SPI + `SunHttpServer`、前端控制器、`HandlerMapping/HandlerAdapter`、参数绑定、自写 JSON、静态资源托管。
- **落地证据**：**浏览器真实打开** `GET /hello`、`GET /users/{id}`、`POST /users`，F12 Network 真实看到请求/响应。

### M6 · 自动配置 + Starter

- **产出**：`@Conditional` 派生、`AutoConfigurationImportSelector`、SPI 读取、演示 Starter。
- **落地证据**：引入 starter 后容器真实启用对应 Bean；缺依赖时真实跳过（运行期可观察）。

### M7 · 启动器 + 事件 + 后端 demo 收口

- **产出**：`MiniSpringApplication.run`、复合注解、事件总线、Banner。
- **落地证据**：一条 `run()` 真实启动 demo，事件真实按序触发。

### M8 · 数据库接入

- **产出**：轻量 JDBC 封装（JdbcTemplate 等价物）、MySQL 8（Docker）+ HikariCP、建表脚本。
- **落地证据**：**MySQL 表里真实查到数据**（SQL 客户端验证）、CRUD 真实落库；并发下连接池无泄漏、无脏读。

### M9 · React 前端 + 联调

- **产出**：React + Vite 前端（`demo-frontend/`），调后端 JSON API。
- **落地证据**：**浏览器真实打开 React 页**，F12 Network 看到前端调后端；页面数据 = MySQL 里真实记录。

### M10 · 3 实例 + Nginx 高可用 + 全链路终验

- **产出**：Nginx `upstream` + 健康检查、3 个无状态实例、健康检查接口、压测脚本、部署手册。
- **落地证据**：**浏览器经 Nginx 访问**，3 实例同时服务；kill 1 实例后浏览器访问不中断、数据仍在库；产出压测报告。

---

## 4. 通用基线（贯穿所有里程碑，但不作为验收）

| 维度 | 要求 |
| --- | --- |
| 可读性 | 直白写法优先，中文注释，命名稳定 |
| 接口契约 | 跨模块只依赖公开接口，不碰实现类 |
| 测试 | 核心类有单测、链路有集成测试（**仅基线，不作验收**） |
| 诚实标注 | 明确标注「未实现/简化」边界 |

---

## 5. 明确不在范围

- 生产级极致性能（NIO / Reactor / 异步 / 连接池调优）
- 安全（认证 / 授权 / CSRF / CORS 完整实现）
- 框架内核内置分布式协调（高可用由 demo 层 Nginx 承担）
- 与 Spring 的 API 二进制/源码级兼容
- 完整 SpEL / AspectJ 表达式 / YAML 规范 / Servlet 规范

---

## 6. 版本约定

- 每个里程碑**落地验收通过后**打 Git tag（如 `v0.m1`、`v0.m2`…），方便回溯每个阶段的完整可运行状态。
- 里程碑未经落地验收，绝不进入下一个。