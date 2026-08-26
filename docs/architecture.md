# MiniSpringBoot 总体设计

## 1. 目标与非目标

### 1.1 目标

复刻 Spring Boot 的核心能力，且框架内核**只依赖 JDK**。当开发者读完代码，能独立回答下面六个问题：

1. Bean 是怎么被扫描、定义、实例化、注入、销毁的？
2. 循环依赖是怎么被「解开」的？
3. AOP 是如何让调用方「无感」地插入横切逻辑的？
4. HTTP 请求是如何命中到 Controller 方法的？
5. 自动配置是如何做到「引入即生效」的？
6. 配置文件里的值是如何流到 `@Value` 字段上的？

同时，内核之上必须有一个**真正能用的 demo 应用**，把「前端 → 后端 → 数据库」全链路跑通，并通过并发测试与 3 实例高可用验收——证明这个从零造的框架「不是纸老虎」。

### 1.2 非目标（刻意不做）

| 不做的事 | 原因 |
| --- | --- |
| 生产级极致性能（NIO/Reactor、异步模型、缓存/锁精调） | 可读性优先，「正确」重于「极致快」 |
| 框架内核内置分布式协调（服务注册/配置中心/会话复制） | 高可用由 demo 层以「无状态 + 外部负载均衡」解决，不污染内核 |
| 与 Spring 的 API 完全二进制/源码兼容 | 只对齐「机制」，不对齐「签名」 |
| 全套 Spring 生态（Data/JPA/Security/Cloud） | 只做「框架内核」最核心的几块 |

### 1.3 双轨制（内核 / demo 的边界）

为同时满足「从零写透内核」与「一定要真能用、全链路高可用」，项目采用**双轨制**：

| 轨道 | 组成 | 第三方依赖 | 使命 |
| --- | --- | --- | --- |
| **框架内核** | `core/context/aop/web/config/autoconfigure/boot` | **零**（仅 JDK） | 把 Spring 的「魔法」摊开讲透 |
| **demo 应用** | 业务代码 + React 前端 + MySQL 接入 + Nginx/压测脚本 | **允许** | 证明内核「真能用」，跑通全链路并验收高可用 |

**边界铁律**：内核绝不依赖 demo；demo 只能通过内核暴露的公开接口使用框架，绝不触碰内核实现类。

---

## 2. 技术选型与权衡

### 2.1 语言与构建

| 项 | 选择 | 理由 |
| --- | --- | --- |
| 语言 | Java 17（LTS） | 支持 `record`、`switch` 表达式、文本块等现代语法，减少样板代码 |
| 构建 | Maven | 生态最通用，多模块（multi-module）配置直观 |
| 单元测试 | JUnit 5 | 仅测试期依赖，不向内核运行时使用方传递 |

### 2.2 核心取舍：框架内核零强制传递的第三方运行时依赖

这是框架内核最「反常识」的一条，也是灵魂所在：

| 需求 | 常规做法（引第三方） | 内核做法 |
| --- | --- | --- |
| HTTP 服务器 | 内嵌 Tomcat / Netty | 基于 JDK `com.sun.net.httpserver.HttpServer`，并用 `WebServer` 接口抽象 |
| JSON 序列化 | Jackson / Gson | 自写极简 JSON 解析器 + 反射序列化 |
| YAML 解析 | SnakeYAML | 自写 YAML 子集解析器（不支持 anchor/alias 等冷门特性） |
| 类代理 | CGLIB / ByteBuddy | JDK 动态代理优先（接口），类代理留待后续 |
| 数据源连接池集成 | 在内核中强制携带连接池 | `autoconfigure` 直接以 `<optional>true>` 编译依赖 HikariCP，并按类路径条件启用；使用方不自动获得该依赖 |

**为什么要这么「自虐」？** 因为「引一个依赖」会瞬间遮蔽掉最值得理解的部分。一个从未写过 HTTP 服务器的人，永远无法真正理解「内嵌 Tomcat」意味着什么。这个项目要做的，正是把那层遮蔽揭开。

> 成本收益评估：自写 JSON/YAML/HTTP 服务器的收益是「教育价值」，成本是「多写几千行代码且容易埋 bug」。框架内核不向使用方强制传递第三方运行时库；`autoconfigure` 的 HikariCP direct optional 集成只在使用方显式提供该库时启用。demo 应用为了「真能用」则显式引入真实依赖——这正是双轨制的意义。

### 2.3 代理策略

- **首选**：JDK `Proxy` + `InvocationHandler`，要求目标实现接口。
- **折中**：对无接口的类，首版通过「要求 Spring 风格的分层结构 / 面向接口编程」来规避类代理；类级代理（cglib 等价物）作为后续里程碑的可选增强。

### 2.4 demo 应用层技术选型

内核保持零强制传递的第三方运行时依赖；demo 层为了「真能用、高可用」显式引入真实基础设施：

| 层 | 选型 | 理由 |
| --- | --- | --- |
| 数据库 | MySQL 8 + HikariCP 连接池 | 真实工程级，验证「能用」与多实例高可用 |
| 前端 | React（Vite 构建） | 现代工程形态，浏览器 F12 可调试全链路 |
| 负载均衡 | Nginx（`least_conn` + 被动失败摘除） | 3 实例无状态水平扩展 + 故障转移；应用另暴露 live/ready 探针 |
| 压测 | ApacheBench / JMeter / wrk | 并发测试与全链路验证 |

---

## 3. 总体分层

```
+--------------------------------------------------------------+
| boot         启动器 + 事件总线                                 |
+--------------------------------------------------------------+
| autoconfigure 自动装配 + Starter                              |
+--------------------------------------------------------------+
| web          Web/MVC + 内嵌服务器                              |
+--------------------------------------------------------------+
| aop          切面 + 动态代理（依赖 core）                      |
+--------------------------------------------------------------+
| core         容器 + Bean 生命周期（最底层，被所有层依赖）        |
+--------------------------------------------------------------+
| config       配置系统（被 core 依赖，提供 Environment）         |
+--------------------------------------------------------------+
```

依赖方向：**上层依赖下层，下层绝不知道上层**。`core` 是供血心脏，`config` 为其提供「外部输入」，二者之上才是 AOP、Web、自动配置与启动器。

### 3.1 接口契约：模块解耦的硬约束

「不能牵一发动全身」是本项目的架构红线，也是「只见树木不见森林」的解药。落地为三条铁律：

1. **依赖方向单向**：上层只能依赖下层，下层绝不反向依赖上层。
2. **编程针对接口**：跨模块访问只能通过下层**导出的接口**，禁止 `import` 下层模块的实现类。实现类统一放 `internal`/`support` 子包，用包名明示「你不该碰我」。
3. **改动局部化**：内部实现重构不得改动接口签名；接口一旦发布，只在「新增可选方法」的意义上扩展，绝不破坏既有调用方。

> 这是「森林视角」的保证：每个模块对外只暴露一个小而稳定的接口面；内部怎么改，森林都不摇晃。

---

## 4. 模块职责与核心类（规划）

### 4.1 `mini-spring-config` —— 配置系统

负责「文件里的字符串」到「内存里的值」的翻译。

- `Environment`：统一配置入口，聚合多个 `PropertySource`
- `PropertySource`：一个来源（如 `application.properties`）
- `PropertiesParser` / `YamlParser`：把文本解析为扁平 `key=value`
- 占位符解析：`${...}` 递归解引用

### 4.2 `mini-spring-core` —— 核心容器

- `BeanDefinition`：Bean 元数据（类名、作用域、依赖、初始化/销毁方法）
- `BeanDefinitionRegistry`：注册与查找
- `BeanFactory` / `DefaultListableBeanFactory`：生产与缓存 Bean
- `ApplicationContext`：在 BeanFactory 之上叠加扫描、事件等能力
- `BeanPostProcessor`（含 `InstantiationAware` / `SmartInstantiationAware` 派生）：扩展点（`BeanFactoryPostProcessor` 未实现，属规划项）
- `InitializingBean` / `DisposableBean`：生命周期回调
- 三级缓存：`singletonObjects` / `earlySingletonObjects` / `singletonFactories`（解决循环依赖）
- `ObjectFactory`：半成品工厂

### 4.3 `mini-spring-context` —— 上下文与扫描

- 注解：`@Component` / `@Service` / `@Repository` / `@Controller` / `@Configuration` / `@Bean`
- 注入注解：`@Autowired` / `@Value` / `@Qualifier` / `@Primary`
- 作用域注解：`@Scope`（注解式 `@PostConstruct`/`@PreDestroy` 未实现——生命周期回调走 `InitializingBean`/`DisposableBean`/`@Bean(initMethod/destroyMethod)`，显式边界）
- `ClassPathScanningCandidateComponentProvider`：扫描 classpath 下的字节码，识别候选 Bean

### 4.4 `mini-spring-aop` —— 面向切面

- `Aspect` / `Pointcut` / `Advice` / `Advisor`
- 通知：`@Before` / `@After`（finally 语义）/ `@Around`（`@AfterReturning`/`@AfterThrowing` 未实现，显式边界）
- `JdkDynamicAopProxy`：生成 JDK 动态代理（无独立 AopProxyFactory，教学子集直连）

### 4.5 `mini-spring-web` —— Web 与 MVC

- `WebServer`：内嵌服务器接口
- `SunHttpServer`：基于 JDK 的默认实现
- `DispatcherServlet`（等价物，可能不叫这个名字以保持诚实）
- `HandlerMapping` / `HandlerAdapter`：把 URL + 方法 → 处理器 → 调用结果
- 参数解析：`@PathVariable` / `@RequestParam` / `@RequestBody` / `HttpServletRequest`
- 返回值处理：`@ResponseBody` + JSON 序列化、视图名
- 静态资源托管（为 demo 前端提供 HTML/JS/CSS）

### 4.6 `mini-spring-autoconfigure` —— 自动配置

- `@Conditional` 及派生：`@ConditionalOnClass` / `@ConditionalOnMissingBean` / `@ConditionalOnProperty`
- `AutoConfigurationImportSelector`：读取 `META-INF/mini.factories`（等价 `spring.factories`）批量导入
- Starter 约定：`xxx-starter` 模块只做声明式装配

### 4.7 `mini-spring-boot` —— 启动器

- `MiniSpringApplication.run(主类, args)`：编排完整启动流程
- `@MiniSpringBootApplication`：复合注解
- `ApplicationEvent` / `ApplicationListener`：事件总线
- `Banner`：启动横幅

### 4.8 `mini-spring-jdbc` —— 数据访问与事务（M8 新增）

- `JdbcTemplate` / `RowMapper<T>`：JDBC 样板收敛（查询/更新/自增主键回填），纯 `java.sql.*`，零第三方
- `DataAccessException` 体系：`SQLException` 统一翻译（约束冲突转 `DuplicateKeyException`），包装消息携带根因
- `TransactionManager`（编程式）+ `@Transactional`（声明式，AOP 切面驱动）：REQUIRED 传播，异常/Error 一律回滚；内层参与者失败会将共享事务标记为 rollback-only
- `TransactionContext`：ThreadLocal 绑定活动事务状态（连接 + rollback-only + 首个失败原因），同一事务内 SQL 复用同一物理连接
- 依赖方向：`jdbc` 仅依赖 `aop`（复用切面），与 `web` 并列，单向无环

---

## 5. Bean 的完整生命周期（贯穿全项目的「主旋律」）

这是理解整个内核的「唯一主线」，所有模块本质上都在为这条生命周期上的某个环节服务：

```
1. 实例化（构造器）
2. 属性填充（依赖注入 @Autowired / @Value）
3. Aware 回调（BeanNameAware / BeanFactoryAware 等）
4. BeanPostProcessor.postProcessBeforeInitialization
5. 初始化（`InitializingBean.afterPropertiesSet` / `@Bean(initMethod)`；未实现 `@PostConstruct`）
6. BeanPostProcessor.postProcessAfterInitialization（AOP 代理在此生成）
7. 就绪，放入一级缓存提供使用
8. 容器关闭（`DisposableBean.destroy` / `@Bean(destroyMethod)`；未实现 `@PreDestroy`）
```

> 记住这条「主旋律」，后面每一章都能在上面找到自己的位置：IoC 负责 1/2/5/7/8，AOP 负责 6，配置系统负责 2 里的 `@Value`，Web 层则是 7 之后「被使用」的消费者。

---

## 6. 数据流：一个请求的完整旅程

```
HTTP 请求
   │
   ▼
WebServer(SunHttpServer) ──► 解析成 MiniHttpServletRequest
   │
   ▼
DispatcherServlet.doDispatch()
   │
   ▼
HandlerMapping ──► 命中 HandlerMethod(Controller + Method)
   │
   ▼
HandlerAdapter ──► 逐个 ArgumentResolver 解析入参
   │
   ▼
Controller.method(...) 执行（可能经过 AOP 代理）
   │
   ▼
ReturnValueHandler ──► @ResponseBody 走 JSON 序列化
   │
   ▼
写回 HTTP Response
```

---

## 7. 包结构与命名约定

- 内核所有模块统一顶层包：`io.github.noctilumdev.minispring`，实现类收拢在各自 `support`/`internal` 子包。
- 命名对齐 Spring 的概念，让读者能「对照着看」Spring 源码。
- 注释与文档统一中文；标识符用英文。

---

## 8. 编码阶段约定

| 项 | 约定 |
| --- | --- |
| 可读性 | 能用直白写法讲清楚的，不用设计模式 |
| 接口契约 | 跨模块只依赖下层公开接口，禁止触碰实现类 |
| 落地验收 | 以「真实运行 / 浏览器 / 数据库」实证为准；单测与日志仅作最低基线，不作为验收 |
| 文档 | 每个里程碑完成后，回填对应模块的实现细节 |
| 诚实 | 不为了「像 Spring」而照搬晦涩命名；API 非兼容处明确标注 |

---

## 9. 与「真 Spring」的差异对照表（速查）

| 维度 | 真 Spring | MiniSpringBoot |
| --- | --- | --- |
| IoC | 三级缓存 + 大量扩展点 | 同构的三级缓存，但扩展点精简 |
| AOP | AspectJ 语法 + 多代理策略 | 只做 JDK 动态代理 + 精简匹配 |
| Web | 完整 Servlet 规范 + 内嵌 Tomcat | 极简 HTTP + JDK HttpServer |
| 配置 | 复杂 Environment/Binder/类型转换 | 扁平 `key=value` + 简单类型转换 |
| 自动配置 | 海量 `*.AutoConfiguration` | 几个演示用的自动配置清单 |

> 这份对照表会在编码过程中持续更新，帮助读者建立「Spring 究竟简化/复杂化了什么」的体感。

---

## 10. demo 应用、全链路与高可用

### 10.1 demo 应用架构

demo 是一套「用户管理 + 账户转账」三层 Web 应用，用于证明内核「真能用」并跑通全链路。下图是 M10 已落地的本机生产形态：

```
浏览器（:9080，React 前端，F12 可调试）
   │ 同源 HTTP
   ▼
Nginx（dist 托管 + least_conn + 被动故障判定）
   ├─► demo 实例 1（MiniSpringBoot 内嵌服务器 :9091）
   ├─► demo 实例 2（:9092）
   └─► demo 实例 3（:9093）
          │ JDBC（HikariCP 连接池）
          ▼
       MySQL 8
```

- 前端（React）通过 JSON API 访问后端，F12 里直接看请求/响应/状态码。
- 后端提供 `GET/POST` 资源接口，内部走 Service → DAO → JDBC → MySQL 完整链路。
- 每个实例**完全无状态**，会话与数据全部落在 MySQL，因此可被任意替换。

### 10.2 高可用设计（无状态 + 外部负载均衡）

高可用不靠内核「内置分布式协调」，而靠两个朴素事实：

1. **实例无状态**：任何请求打到任何一个实例都得到一致结果，实例之间无需同步。
2. **外部被动故障判定**：Nginx 根据真实代理连接失败与超时暂时摘除实例，并把后续流量切到存活实例；应用自身另提供 `/health/live` 与真实穿透 MySQL 的 `/health`，供操作和取证使用。这里没有虚构 Nginx 开源版不存在的主动周期探测。

```
        Nginx upstream
   ┌──────┼──────┐
 :9091  :9092  :9093   （任意一个宕机，其余两个接管）
```

因此本项目「3 实例高可用」的完整语义是：**单台宿主内任一应用实例故障，请求可被其余实例承接，事务数据仍由共享 MySQL 保持。** 它不等于多机容灾；宿主、Nginx、Docker Desktop 与 MySQL 仍是单点。

### 10.3 并发与全链路测试策略

> 不追求「高并发」数字，但「并发下的正确性」必须有测试背书。

| 测试 | 工具 | 验证点 |
| --- | --- | --- |
| 单元测试 | JUnit 5 | 内核各模块（M1~M7） |
| 集成测试 | JUnit 5 + MySQL 8 | Web/Service/DAO/JDBC 链路 |
| 并发正确性 | 压测脚本 | 内嵌服务器线程安全、连接池无泄漏、DAO 无脏读 |
| 全链路测试 | 浏览器 F12 + 自动化 | 前端 → 后端 → 数据库端到端 |
| 高可用演练 | 起 3 实例 + kill 1 个 | 其余实例接管、服务不中断 |

M10 的精确命令、容量曲线、故障/事务/就绪演练与证据哈希见 [`10-high-availability.md`](10-high-availability.md)。

### 10.4 前端工程

- React + Vite，独立目录 `demo-frontend/`；M9 用 `npm run dev` 在 `:9010` 联调，M10 的生产构建由 Nginx 在 `:9080` 同源托管。
- 前端只依赖后端公开的 JSON 接口，与后端解耦——「不能牵一发动全身」在前后端边界的体现。
