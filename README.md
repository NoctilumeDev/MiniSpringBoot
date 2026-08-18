# MiniSpringBoot

> 假如这个世界上没有 Spring Boot，我们该怎么办？
> —— 那就从零把它造出来，把所有底层和内核拆开、摊平、写透。

MiniSpringBoot 是一个 **从零手写** 的 Spring Boot 内核复刻项目。它不依赖 Spring、不依赖任何第三方运行时库，只用 JDK 重新实现 Spring 家族最核心的那几块「魔法」：IoC 容器、AOP、Web/MVC、自动配置、外部化配置。

---

## ⚠️ 重要声明：这不是一个框架

请务必先认清它的身份——**这是一个教学脚手架，不是一个开箱即用的框架**。

- ❌ 不是给业务项目「拿来即用」的生产框架
- ❌ 没有高可用、没有高并发、没有安全加固、没有性能调优
- ❌ 不追求 API 与 Spring 完全兼容，只追求「机制」与「思想」的一致
- ❌ 不会为了「能用」而牺牲「能被看懂」

它的目标是给 **底层爱好者 / 想搞清楚 Spring 到底做了什么的人**，提供一个可以把每个环节都看清楚、甚至打断点一步步跟进去的脚手架。

> 麻雀虽小，五脏俱全。验收标准是「工程级、商业级」的完成度——即使它没有资格在生产环境里扛流量。

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

MiniSpringBoot 采用与 Spring 对齐的分层设计，从下到上依次是：

```
┌────────────────────────────────────────────────────────────────┐
│  boot          MiniSpringApplication.run() / 事件 / 启动流程   │
├────────────────────────────────────────────────────────────────┤
│  autoconfigure @Conditional / Starter / SPI 自动装配          │
├────────────────────────────────────────────────────────────────┤
│  web           DispatcherServlet / HandlerMapping / 参数绑定   │
│                （内嵌 HTTP 服务器，零第三方依赖）               │
├────────────────────────────────────────────────────────────────┤
│  aop           Pointcut / Advice / 动态代理                    │
├────────────────────────────────────────────────────────────────┤
│  core          ApplicationContext / BeanFactory / Bean 生命周期│
│                BeanPostProcessor / 三级缓存循环依赖            │
├────────────────────────────────────────────────────────────────┤
│  config        Environment / PropertySource / @Value           │
└────────────────────────────────────────────────────────────────┘
                   —— 全部基于 JDK 17，零第三方运行时依赖 ——
```

| 模块 | 对应 Spring 的概念 | 责任 |
| --- | --- | --- |
| `core` | IoC 容器 | Bean 定义、实例化、依赖注入、生命周期、循环依赖 |
| `context` | ApplicationContext | 注解扫描、配置类解析、`@ComponentScan` |
| `aop` | Spring AOP | 切点匹配、通知执行、动态代理 |
| `web` | Spring MVC + 内嵌容器 | HTTP 服务器、路由、参数绑定、响应序列化 |
| `config` | Environment / Binder | 配置文件解析、`@Value`、属性绑定 |
| `autoconfigure` | Spring Boot 自动配置 | `@Conditional`、Starter、SPI 装配 |
| `boot` | SpringApplication | 启动入口、事件机制、Banner |

> 完整的分层说明、类设计、数据流见 [docs/architecture.md](docs/architecture.md)。

---

## 设计哲学

本项目刻意选择了三条「不讨好」的原则，它们决定了整个代码库的气质：

### 1. 零依赖——造轮子本身就是目的

不引入 Spring、不引入 Jackson、不引入 Tomcat、不引入 YAML 库。JSON 解析、YAML 解析、HTTP 服务器，全部自己写。

这不代表「自己写的更好」，而是因为 **一个初学者只有亲手写过一个 HTTP 服务器，才真正理解 Spring Boot 内嵌的 Tomcat 到底替我们做了什么**。轮子不是用来省事的，而是用来理解「为什么需要这个轮子」的。

### 2. 剥洋葱——每一层都能被单独打开

在 Spring 里，很多机制被层层封装、工厂套工厂、代理套代理。MiniSpringBoot 刻意保持「扁平」：能用直白的 `if/else` 讲清楚，就绝不用晦涩的策略模式。**可读性优先于优雅性。**

### 3. 工程级验收——即使不上生产

它不能扛流量，但它的代码必须经得起看。目标包括：

- 每个模块都有单元测试，核心链路有集成测试
- 包结构清晰、命名稳定、注释到位（中文）
- 一个能真正跑起来的示例应用（`demo`）
- 每个里程碑都有明确的验收清单

---

## 目录结构（规划）

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
│   └── 06-roadmap.md          # 路线图与验收标准
├── mini-spring-core/          # 核心容器
├── mini-spring-context/       # 上下文与扫描
├── mini-spring-aop/           # AOP
├── mini-spring-web/           # Web/MVC + 内嵌服务器
├── mini-spring-config/        # 配置系统
├── mini-spring-autoconfigure/ # 自动配置
├── mini-spring-boot/          # 启动器
└── demo/                      # 示例应用
```

> 目录结构会随里程碑推进逐步落地，以实际编码为准。

---

## 构建与运行（规划中）

技术选型见 [docs/architecture.md](docs/architecture.md)，当前阶段尚未进入编码，目录与构建脚本将随里程碑逐步添加。

---

## 路线图

完整里程碑见 [docs/06-roadmap.md](docs/06-roadmap.md)，概要如下：

- **M1**：IoC 容器（Bean 定义 → 实例化 → 注入 → 生命周期）
- **M2**：注解与类路径扫描
- **M3**：AOP（切点 + 通知 + 动态代理）
- **M4**：外部化配置（`application.properties` / `application.yml` / `@Value`）
- **M5**：Web/MVC（内嵌服务器 + 路由 + 参数绑定）
- **M6**：自动配置与 Starter
- **M7**：启动器、事件机制、示例应用收口

---

## 许可与致谢

本项目为教学演示用途，不保证任何形式的可用性。灵感与参照来自 Spring Framework 与 Spring Boot 的公开设计，向它们致以敬意——正是它们把 Java 生态带到了今天的高度，而我们要做的，是把它们的「黑盒」重新打开。

> 站在巨人的肩膀上，去拆解巨人的骨架。