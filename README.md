# MiniSpringBoot

> 假如这个世界上没有 Spring Boot，我们该怎么办？
> —— 那就从零把它造出来，把所有底层和内核拆开、摊平、写透。

MiniSpringBoot 是一个 **从零手写** 的 Spring Boot 内核复刻项目。框架内核不依赖 Spring、不依赖任何第三方运行时库，只用 JDK 重新实现 Spring 家族最核心的那几块「魔法」：IoC 容器、AOP、Web/MVC、自动配置、外部化配置。

---

## ⚠️ 重要声明：内核是脚手架，demo 是真应用

请务必先认清它的身份——**框架内核是一个教学脚手架，不是开箱即用的生产框架**；但在它之上，我们搭了一个**真正能跑、能测、能扛 3 实例高可用的 demo 应用**，用来证明它「不是纸老虎」。

- ❌ 框架内核不是「拿来即用」的生产框架，不内置分布式高可用
- ❌ 不追求 API 与 Spring 完全兼容，只追求「机制」与「思想」的一致
- ❌ 不会为了「能用」而牺牲「能被看懂」
- ✅ 但 demo 应用**一定要可用**：前端 + 后端 + 数据库全链路打通，且通过并发测试与 3 实例高可用验收

它的目标是双重的：

1. 给 **底层爱好者** 一个能把每个环节看清楚、甚至打断点一步步跟进去的脚手架；
2. 用它**亲手造出来**的一套前端 + 后端 + 数据库 + 多实例部署，证明「从零造框架」这件事真的站得住。

> 麻雀虽小，五脏俱全。内核的验收标准是「工程级、商业级」完成度；demo 的验收标准是「真能用 + 全链路 + 高可用」。

---

## 它想回答什么问题

当你写下一个 `@SpringBootApplication`，然后点下运行按钮，背后到底发生了多少次魔法？大多数教程会用一句「Spring 自动帮你搞定了」一笔带过。MiniSpringBoot 拒绝这种含糊，它要亲手回答：

1. `@Component` 标注的类，是怎么被「扫描」出来、变成一个个 Bean 的？
2. 依赖注入（`@Autowired`）到底是怎么把对象塞进字段里的？循环依赖靠什么解开？
3. AOP 的切面，是怎么让你「无感」地加上了事务、日志、权限？
4. 一个 HTTP 请求，从 80 端口进来，是怎么精准命中到你的 Controller 方法的？
5. `spring.factories` / 自动配置，是如何做到「只引入一个 Starter 就生效一堆 Bean」的？
6. `application.yml` 里的一行配置，是怎么跑到你 `@Value` 标注的字段上的？

每一个问号，这里都有一份「自己写出来」的答案，而不是「调用了 Spring 某个类」的答案。

---

## 架构总览

MiniSpringBoot 采用与 Spring 对齐的分层设计。下面是**框架内核**的分层（从上到下，依赖方向严格单向，上层依赖下层）：

```
┌────────────────────────────────────────────────────────────────┐
│  boot          MiniSpringApplication.run() / Lifecycle 驱动 /   │
│                关闭钩子 / Banner / StartedEvent（不依赖 web）   │
├────────────────────────────────────────────────────────────────┤
│  autoconfigure @Conditional / SPI 读取 + 框架自动配置类归位     │
│                （web/aop/config 均 optional：裁掉即消失，       │
│                与 spring-boot-autoconfigure 实证结构一致）      │
├────────────────────────────────────────────────────────────────┤
│  web           DispatcherServlet / HandlerMapping / 参数绑定    │
│                （内嵌 HTTP 服务器，零第三方依赖；经 Lifecycle   │
│                由 boot 启动）                                   │
├────────────────────────────────────────────────────────────────┤
│  aop           Pointcut / Advice / 动态代理 / 自动代理创建器    │
├────────────────────────────────────────────────────────────────┤
│  context       注解扫描 / @Configuration·@Bean / 事件 / Lifecycle│
├────────────────────────────────────────────────────────────────┤
│  config        Environment / PropertySource / @Value            │
├────────────────────────────────────────────────────────────────┤
│  core          BeanFactory / BeanDefinition / Bean 生命周期     │
│                BeanPostProcessor                                │
└────────────────────────────────────────────────────────────────┘
     —— 内核全部基于 JDK 17，零第三方运行时依赖 ——

  demo 轨道：mini-spring-demo（后端收口）/ mini-spring-starter-demo（Starter 验证）
```

| 模块 | 对应 Spring 的概念 | 责任 |
| --- | --- | --- |
| `core` | spring-beans | Bean 定义、实例化、依赖注入、生命周期、循环依赖（三级缓存） |
| `config` | Environment / Binder | 配置文件解析（properties/yaml/Profile）、`@Value`、属性绑定 |
| `context` | spring-context | 注解扫描、配置类解析、`@ComponentScan`、事件广播、`Lifecycle` |
| `aop` | spring-aop | 切点匹配、通知执行、JDK 动态代理、自动代理创建器 |
| `web` | spring-webmvc + 内嵌容器 | HTTP 服务器、路由、参数绑定、响应序列化、静态资源 |
| `autoconfigure` | spring-boot-autoconfigure | `@Conditional` 派生、SPI 装配、框架自动配置类归位（optional + name 探测） |
| `boot` | spring-boot | 启动入口（Lifecycle 驱动内嵌服务器）、事件、Banner |

> **双轨制**：上表是**框架内核**（零第三方依赖，用于教学）。在它之上还有一条「demo 应用」轨道——业务代码 + React 前端 + MySQL + Nginx，用它证明内核「真能用」，并跑通全链路、3 实例高可用。详见 [docs/architecture.md](docs/architecture.md)。

---

## 设计哲学

本项目刻意选择了四条「不讨好」的原则，它们决定了整个代码库的气质：

### 1. 零依赖（内核）——造轮子本身就是目的

框架内核不引入 Spring、不引入 Jackson、不引入 Tomcat、不引入 YAML 库。JSON 解析、YAML 解析、HTTP 服务器，全部自己写。

这不代表「自己写的更好」，而是因为 **一个初学者只有亲手写过一个 HTTP 服务器，才真正理解 Spring Boot 内嵌的 Tomcat 到底替我们做了什么**。轮子不是用来省事的，而是用来理解「为什么需要这个轮子」的。

### 2. 剥洋葱——每一层都能被单独打开

在 Spring 里，很多机制被层层封装、工厂套工厂、代理套代理。MiniSpringBoot 刻意保持「扁平」：能用直白的 `if/else` 讲清楚，就绝不用晦涩的策略模式。**可读性优先于优雅性。**

### 3. 落地验收——不靠测试与日志，靠真实落地

一个里程碑算「过」，标准是**真实观察到的落地结果**：浏览器真打开、F12 真看到请求、数据库真查到数据、真进程真跑起来。**单测与日志只作最低基线，不作为验收依据。**

- 每个模块真实可运行，行为真实可观察（非 mock）
- 并发正确性：内嵌服务器多线程并发处理请求、数据库连接池无泄漏
- 一个能真正跑起来的示例应用 `demo`（React 前端 + 后端 + MySQL 全链路）
- 3 实例无状态 + Nginx 负载均衡的高可用演练
- 每个里程碑都有明确的「落地证据」验收清单

### 4. 不能牵一发动全身——接口契约

架构上强制「高内聚、低耦合」：跨模块只允许依赖下层**公开接口**，禁止触碰实现类；内部重构绝不改动接口签名。**只见树木不见森林，是本项目最不能犯的错误**——模块要有清晰的森林全局视角，局部改动不得波及全身。

---

## 目录结构

```
MiniSpringBoot
├── README.md
├── docs/                      # 设计文档
│   ├── architecture.md        # 总体设计
│   ├── 01-ioc-container.md    # IoC 容器
│   ├── 02-aop.md              # AOP
│   ├── 03-web-mvc.md          # Web 与 MVC
│   ├── 04-auto-configuration.md # 自动配置与 Starter
│   ├── 05-externalized-configuration.md # 外部化配置
│   ├── 07-boot.md             # 启动器
│   └── 06-roadmap.md          # 路线图、技术债与验收标准
├── mini-spring-core/          # 核心容器（三级缓存 / 生命周期 / BPP）
├── mini-spring-config/        # 配置系统（properties/yaml/Profile/@Value）
├── mini-spring-context/       # 注解扫描 / 配置类解析 / 事件
├── mini-spring-aop/           # AOP（JDK 动态代理，零第三方）
├── mini-spring-web/           # Web/MVC + 内嵌服务器 + 自写 JSON
├── mini-spring-autoconfigure/ # 自动配置（框架自动配置类统一归位于此）
├── mini-spring-boot/          # 启动器（run 自动起服务器 + 关闭钩子）
├── mini-spring-starter-demo/  # Starter 验证（引入依赖即自动装配）
├── mini-spring-demo/          # 后端 demo 收口（全链路能力验证）
└── deploy/                    # （规划）Nginx 配置、3 实例启动与压测脚本
```

> demo 前端与 MySQL 接入在 M8/M9 落地后补充。

---

## 构建与运行

```bash
# 全量构建 + 测试（JDK 17）
mvn clean test

# 启动后端 demo（一条 run() 拉起：自动配置 + AOP + 事件 + 内嵌服务器 9090 端口）
mvn -pl mini-spring-demo exec:java -Dexec.mainClass=com.minispring.demo.app.DemoApplication
# 或直接 java 运行
java -cp <classpath> com.minispring.demo.app.DemoApplication
```

验证：浏览器访问 `http://localhost:9090/hello`、`http://localhost:9090/capability/aop/order` 等接口。

---

## 路线图

完整里程碑见 [docs/06-roadmap.md](docs/06-roadmap.md)，概要如下：

- **M0** ✅：环境就绪 + 规矩冻结 + 方案审批
- **M1** ✅：IoC 容器（Bean 定义 → 实例化 → 注入 → 生命周期）
- **M2** ✅：注解与类路径扫描
- **M3** ✅：AOP（切点 + 通知 + 动态代理）
- **M4** ✅：外部化配置（`application.properties` / `application.yml` / `@Value`）
- **M5** ✅：Web/MVC + 内嵌服务器
- **M6** ✅：自动配置与 Starter
- **M7** ✅：启动器（run 自动起服务器）、事件机制、后端 demo 收口
- **M8**：数据库接入（MySQL + 连接池）
- **M9**：React 前端 + 前后端联调
- **M10**：3 实例高可用 + 全链路终验

---

## 许可与致谢

框架内核为教学演示用途，demo 应用用于验证可用性与高可用。灵感与参照来自 Spring Framework 与 Spring Boot 的公开设计，向它们致以敬意——正是它们把 Java 生态带到了今天的高度，而我们要做的，是把它们的「黑盒」重新打开。

> 站在巨人的肩膀上，去拆解巨人的骨架。