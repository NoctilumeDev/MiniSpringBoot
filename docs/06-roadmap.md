# 06 · 路线图与验收标准（M0 → M10）

> 推进方式：**严格串行**。前一里程碑未「落地验收」通过，后一个绝对不启动——不跳、不并行、不提前埋后续的坑。

---

## 1. 验收总则（规矩，先锁死）

1. **落地为准**：里程碑通过 = 真实可观测结果落地（真实 `main` 启动 / 浏览器真实访问 / 数据库真实落库）。**单测与日志只作最低基线，不作为验收依据。**
2. **分阶段落地口径**：无 Web/数据库的里程碑（M1~M4、M6、M7）以「真实 `main` 启动 + 运行期真实行为」验收；自 M5 起用浏览器 F12 实证；自 M8 起用数据库真落库实证。
3. **严格串行**：M0→M10 顺序推进，未经前述落地验收，绝不触碰后续代码。
4. **双轨制**：框架内核（M1~M7）零第三方依赖；demo 层（M8~M10）可引真实依赖（MySQL 驱动 + HikariCP）。
5. **接口契约**：跨模块只依赖下层公开接口，禁止触碰实现类；内部重构不改接口签名。
6. **三次质量审查门闩**：每个里程碑「打 tag / 判定通过」前，必须完成并记录三次审查——①**回顾审查**（是否回归破坏既有里程碑、是否违反接口契约、依赖方向是否仍单向）；②**当前审查**（happy path 之外，还要覆盖异常路径、边界值、null 输入等真实运行全路径）；③**前瞻审查**（本里程碑埋了哪些假设与简化，会在 Mₙ₊₁ / Mₙ₊₂ 的什么场景被打破）。三次审查未完成，不得进入下一里程碑。

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

- **产出**：工具链确认；「落地验收 / React / 串行 / 接口契约」写入文档；本里程碑表获批；整机环境实测 + 16GB 内存预算锁定。
- **实测环境（2026-08-18）**：
  - 硬件：16 GB RAM（实测 15.8 GB）/ 24 逻辑核
  - 工具链：JDK 17.0.12（Oracle，`JAVA_HOME=D:\IDEA\JDK17`）/ Maven 3.9.11 / Node v24.14.0 + npm 11.9.0 / Docker 29.7.2 + Compose v5.3.1（已装，引擎当前未运行，M8 前启动即可）
- **16GB 内存预算（Docker Compose 编排）**：Docker Desktop VM ~2GB + MySQL 8 容器 512MB（mem_limit）+ 后端×3 各 512MB（JVM -Xmx256m）≈1.5GB + Nginx 64MB；容器内合计 ≈2.1GB，加宿主（Windows+IDE+浏览器）峰值 ≈11GB，总在 16GB 内，留 ~4GB 余量。
- **落地证据**：文档更新为 React + 落地口径 + 环境实测记录，git 提交，方案获用户批准。

### M1 · IoC 容器

- **产出**：`BeanDefinition` / `BeanDefinitionRegistry` / `DefaultListableBeanFactory` / 三级缓存 / 生命周期 / `BeanPostProcessor`。
- **落地证据**：demo `main` 真启动，依赖注入成功、循环依赖 `A↔B` 正确解开、`@PostConstruct/@PreDestroy` 真实回调。

### M2 · 注解与类扫描

- **产出**：`@Component/@Service/@Repository/@Configuration/@Bean`、`@Autowired/@Qualifier/@Primary`、`@Scope`、`ClassPathScanner`。
- **落地证据**：demo 用注解真实扫描出 Bean 并完成注入，main 运行可观察。

### M3 · AOP

- **产出**：`Pointcut/Advice/Aspect/Advisor`、`@Before/@After/@Around`、JDK 动态代理。
- **落地证据**：demo 里 `@Around` 计时切面真实拦截目标方法，耗时真实打印。
- **落地边界（显式技术债）**：`DefaultListableBeanFactory.getEarlyBeanReference` 目前仍直接返回原始对象，尚未委托 AOP 代理器提前生成代理；因此「被代理 Bean 同时参与循环依赖」时，提前暴露的是裸对象而非代理，与容器最终持有的代理不一致。**M3 验收口径不含此联合场景，不阻塞**；归到 **M7 收口**（最迟 M8 数据接入前）接上——core 新增 `SmartInstantiationAwareBeanPostProcessor#getEarlyBeanReference` 扩展点，`AspectJAutoProxyCreator` 实现并用 `earlyProxyReferences` 去重，同时补「AOP × 循环依赖」集成场景验收。

### M4 · 外部化配置

- **产出**：`Environment/PropertySource`、properties/yaml 解析、`@Value` 占位符与默认值、Profile。
- **落地证据**：`ConfigDemo` 真实 `main` 启动，从 `application.yml` + `application.properties` 读值注入字段，并逐一断言——yaml 二级嵌套、列表拍平（`app.features[0]/[1]`）、int 类型转换、默认值（`${app.missing:3000}`）、嵌套占位符（`${app.${app.pointer}}`）、properties 解析（`app.version`）、prod profile 覆盖 `server.port`（8080→8443），全部在运行期真实通过。
- **落地边界（显式技术债）**：YAML 仅为「教学子集」、`@Value` 不支持 SpEL 与复杂类型、Profile 仅手动激活、配置文件仅 classpath 根——详见 §7 的 D10–D13。

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

---

## 7. 已知局限与技术债（代码审查登记）

> 下列各项经 M0–M4 代码审查显式登记，当前 demo 均不触发，仅作诚实标注；按「接上时机」逐项关闭。

| 编号 | 局限 / 技术债 | 触发条件 | 接上时机 / 做法 |
| --- | --- | --- | --- |
| D1 | 类路径扫描仅支持文件目录，不支持 JAR | 打成可执行 JAR 部署时 `jar:` 协议 URL 无法 `toURI()` 遍历 | M10 部署前补 `JarURLConnection` 分支，或改用 exploded classes |
| D2 | `@Bean` 不支持 `initMethod/destroyMethod` | 需要方法级生命周期回调时 | M4/M7 生命周期收口时给 `@Bean` 加属性并在 reader 读取 |
| D3 | `@Autowired` 声明支持构造器/方法/参数注入，实际只实现字段注入 | 需要构造或方法注入时 | M5 参数绑定时一并评估，或收窄 `@Target` |
| D4 | `@After` 注释写「正常返回后」，实现为 finally 语义 | 目标方法抛异常时后置仍会执行 | 改注释，或拆成 `@AfterReturning`/`@AfterThrowing` |
| D5 | 无通知排序（`@Order`） | 多切面命中同一方法时顺序不确定 | M6/M7 多切面场景补 `Ordered` 优先级 |
| D6 | `getBeanNamesForType` 不感知 JDK 代理，按具体类注入会 ClassCastException | 按具体类而非接口注入被代理 Bean | 保持「按接口注入」约定（JDK 代理固有限制，与 Spring 一致） |
| D7 | 单例创建无线程安全保护 | 运行时懒加载/动态注册单例时存在竞态 | 单例 refresh 预实例化 + 服务器其后启动即安全；未来懒加载再加锁 |
| D8 | AOP 仅 JDK 动态代理，无接口的类无法被织入 | 对具体类（如 M5 的 Controller）做 AOP 时 | M5/M6 前评估：引入 CGLIB 代理策略，或将被增强 Bean 接口化 |
| D9 | demo 包名跨模块重复：`com.minispring.demo` 同时出现在 core（IocDemo）与 context（demo 组件），Scanner 的 `getResource` 按 classpath 顺序命中首个目录，扫描结果依赖 classpath 顺序 | 同一包名跨模块、以文件目录扫描时 | M5 组装前把 demo 包模块化（`core.demo` / `context.demo`） |
| D10 | Profile 激活仅支持手动 `setActiveProfiles`，未从 `spring.profiles.active` / `SPRING_PROFILES_ACTIVE` 自动读取 | 部署阶段按环境变量切换 profile | M10 部署前接上 |
| D11 | YAML 解析为「教学子集」：无 anchor、多行字符串（`|`/`>`）、flow style（`{k:v}`/`[a,b]`）、list-of-map 嵌套；值内 `#` 被当作注释截断；引号不剥离（`port: "8080"` 的引号会带进值，转 int 抛 NumberFormatException） | 配置用到这些语法 / 给标量加引号 | 按需扩展 `YamlPropertySourceLoader`（引号剥离建议 M5 写 HTTP/JDBC 配置前先补） |
| D12 | `@Value` 不支持 SpEL（`#{...}`）；类型转换仅 String + 基本/包装类型（无 List/Map/枚举） | 注入复杂类型或 SpEL 表达式 | M5 参数绑定时一并评估，或收窄标注 |
| D13 | 配置文件仅 classpath 根 `application.*`，不支持 `config/` 子目录、命令行参数覆盖 | 外部部署自定义配置位置 | M10 部署规范统一约定 |

> 注：B1（ITE 拆包）、B3（Object 方法过滤）为已发布 M3 代码的真实 bug，已单独修复并回归，不列入本表；B2（AOP×循环依赖）已在 M3「落地边界」登记。