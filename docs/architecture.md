# MiniSpringBoot 总体设计

## 1. 目标与非目标

### 1.1 目标

复刻 Spring Boot 的核心能力，且**只依赖 JDK**。当开发者读完这份代码，能独立回答下面六个问题：

1. Bean 是怎么被扫描、定义、实例化、注入、销毁的？
2. 循环依赖是怎么被「解开」的？
3. AOP 是如何让调用方「无感」地插入横切逻辑的？
4. HTTP 请求是如何命中到 Controller 方法的？
5. 自动配置是如何做到「引入即生效」的？
6. 配置文件里的值是如何流到 `@Value` 字段上的？

### 1.2 非目标（刻意不做）

| 不做的事 | 原因 |
| --- | --- |
| 生产级性能（NIO/Reactor、异步、缓存优化） | 教学脚手架，可读性优先 |
| 安全加固、多线程并发正确性保证 | 同上 |
| 与 Spring 的 API 完全二进制/源码兼容 | 只对齐「机制」，不对齐「签名」 |
| 全套 Spring 生态（Data/JPA/Security/Cloud） | 只做「出框架内」最核心的几块 |

验收标准是「工程级完成度」：测试、文档、可运行示例、清晰分层——即使它不配扛生产流量。

---

## 2. 技术选型与权衡

### 2.1 语言与构建

| 项 | 选择 | 理由 |
| --- | --- | --- |
| 语言 | Java 17（LTS） | 支持 `record`、`switch` 表达式、文本块等现代语法，减少样板代码 |
| 构建 | Maven | 生态最通用，多模块（multi-module）配置直观 |
| 单元测试 | JUnit 5 | 仅测试期依赖，运行时保持零依赖 |

### 2.2 核心取舍：零第三方运行时依赖

这是本项目最「反常识」的一条，也是灵魂所在：

| 需求 | 常规做法（引第三方） | MiniSpringBoot 做法 |
| --- | --- | --- |
| HTTP 服务器 | 内嵌 Tomcat / Netty | 基于 JDK `com.sun.net.httpserver.HttpServer`，并用 `WebServer` 接口抽象 |
| JSON 序列化 | Jackson / Gson | 自写极简 JSON 解析器 + 反射序列化 |
| YAML 解析 | SnakeYAML | 自写 YAML 子集解析器（不支持 anchor/alias 等冷门特性） |
| 类代理 | CGLIB / ByteBuddy | JDK 动态代理优先（接口），类代理留待后续 |

**为什么要这么「自虐」？** 因为「引一个依赖」会瞬间遮蔽掉最值得理解的部分。一个从未写过 HTTP 服务器的人，永远无法真正理解「内嵌 Tomcat」意味着什么。这个项目要做的，正是把那层遮蔽揭开。

> 成本收益评估：自写 JSON/YAML/HTTP 服务器的收益是「教育价值」，成本是「多写几千行代码且容易埋 bug」。对于一个教学项目，这笔账是划算的；对于一个生产项目，这是灾难——这个边界必须清晰。

### 2.3 代理策略

- **首选**：JDK `Proxy` + `InvocationHandler`，要求目标实现接口。
- **折中**：对无接口的类，首版通过「要求 Spring 风格的分层结构 / 面向接口编程」来规避类代理；类级代理（cglib 等价物）作为后续里程碑的可选增强。

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
- `BeanPostProcessor` / `BeanFactoryPostProcessor`：扩展点
- `InitializingBean` / `DisposableBean`：生命周期回调
- 三级缓存：`singletonObjects` / `earlySingletonObjects` / `singletonFactories`（解决循环依赖）

### 4.3 `mini-spring-context` —— 上下文与扫描

- 注解：`@Component` / `@Service` / `@Repository` / `@Controller` / `@Configuration` / `@Bean`
- 注入注解：`@Autowired` / `@Value` / `@Qualifier` / `@Primary`
- 生命周期注解：`@PostConstruct` / `@PreDestroy` / `@Scope`
- `ClassPathScanner`：扫描 classpath 下的字节码，识别候选 Bean

### 4.4 `mini-spring-aop` —— 面向切面

- `Aspect` / `Pointcut` / `Advice` / `Advisor`
- 通知：`@Before` / `@After` / `@AfterReturning` / `@AfterThrowing` / `@Around`
- `AopProxyFactory`：生成 JDK 动态代理

### 4.5 `mini-spring-web` —— Web 与 MVC

- `WebServer`：内嵌服务器接口
- `SunHttpServer`：基于 JDK 的默认实现
- `DispatcherServlet`（等价物，可能不叫这个名字以保持诚实）
- `HandlerMapping` / `HandlerAdapter`：把 URL + 方法 → 处理器 → 调用结果
- 参数解析：`@PathVariable` / `@RequestParam` / `@RequestBody` / `HttpServletRequest`
- 返回值处理：`@ResponseBody` + JSON 序列化、视图名

### 4.6 `mini-spring-autoconfigure` —— 自动配置

- `@Conditional` 及派生：`@ConditionalOnClass` / `@ConditionalOnMissingBean` / `@ConditionalOnProperty`
- `AutoConfigurationImportSelector`：读取 `META-INF/mini.factories`（等价 `spring.factories`）批量导入
- Starter 约定：`xxx-starter` 模块只做声明式装配

### 4.7 `mini-spring-boot` —— 启动器

- `MiniSpringApplication.run(主类, args)`：编排完整启动流程
- `@MiniSpringBootApplication`：复合注解
- `ApplicationEvent` / `ApplicationListener`：事件总线
- `Banner`：启动横幅

---

## 5. Bean 的完整生命周期（贯穿全项目的「主旋律」）

这是理解整个内核的「唯一主线」，所有模块本质上都在为这条生命周期上的某个环节服务：

```
1. 实例化（构造器）
2. 属性填充（依赖注入 @Autowired / @Value）
3. Aware 回调（BeanNameAware / BeanFactoryAware 等）
4. BeanPostProcessor.postProcessBeforeInitialization
5. 初始化（InitializingBean.afterPropertiesSet / @PostConstruct）
6. BeanPostProcessor.postProcessAfterInitialization（AOP 代理在此生成）
7. 就绪，放入一级缓存提供使用
8. 容器关闭：DisposableBean.destroy / @PreDestroy
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

- 所有模块统一顶层包：`io.github.noctilumdev.minispring`（或更短的 `minispring`，编码阶段最终确定）
- 命名对齐 Spring 的概念，让读者能「对照着看」Spring 源码
- 注释与文档统一中文；标识符用英文

---

## 8. 编码阶段约定

| 项 | 约定 |
| --- | --- |
| 可读性 | 能用直白写法讲清楚的，不用设计模式 |
| 测试 | 核心模块 100% 有单测，关键链路有集成测试 |
| 文档 | 每个里程碑完成后，回填本章对应子模块的实现细节 |
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