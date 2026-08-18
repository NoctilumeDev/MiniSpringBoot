# 06 · 路线图与验收标准

> 项目推进按里程碑（Milestone）滚动，每个里程碑有明确、可检验的「验收清单」。**完成验收前，不得推进到下一个里程碑**——这是「工程级完成度」的硬约束。

---

## 里程碑总览

```
M1  IoC 容器          ← 地基
M2  注解与类扫描       ← 让地基能被"自动填充"
M3  AOP              ← 第一块"魔法"
M4  外部化配置        ← 让魔法可配置
M5  Web / MVC        ← 串起端到端可运行
M6  自动配置 + Starter← Spring Boot 的"题眼"
M7  启动器 + 事件 + demo ← 收口成可体验的成品
```

**依赖关系**：M2 依赖 M1；M3 依赖 M1/M2；M4 可并行于 M3；M5 依赖 M1/M2/M3/M4；M6 依赖全部；M7 依赖全部。

---

## 各里程碑详细验收标准

### M1 · IoC 容器

- [ ] `BeanDefinition` / `BeanDefinitionRegistry` / `DefaultListableBeanFactory` 类图落地
- [ ] 支持构造器注入、字段注入、setter 注入
- [ ] 支持 `singleton` / `prototype` 两种作用域
- [ ] 支持 `InitializingBean` / `DisposableBean` 生命周期回调
- [ ] 支持 `BeanPostProcessor`，并能用它包装 Bean
- [ ] 单测：循环依赖 `A←→B`、`A→B→C→A` 正确解开
- [ ] 单测：prototype 每次获取都是新实例、不参与缓存

### M2 · 注解与类扫描

- [ ] `@Component` / `@Service` / `@Repository` / `@Configuration` / `@Bean` 落地
- [ ] `@Autowired` / `@Qualifier` / `@Primary` / `@Value` 落地
- [ ] `@Scope` / `@PostConstruct` / `@PreDestroy` 落地
- [ ] `ClassPathScanner` 能扫描包并识别候选 Bean
- [ ] 单测：扫描 → 注册 → 注入 → 初始化的完整链路

### M3 · AOP

- [ ] `Aspect` / `Advisor` / `Pointcut` / `Advice` 类图落地
- [ ] `@Before` / `@After` / `@AfterReturning` / `@AfterThrowing` / `@Around` 落地
- [ ] JDK 动态代理代理工厂落地
- [ ] 注解切点 + 方法名通配两种匹配
- [ ] 多切面通知顺序正确
- [ ] 单测：`@Around` 计时切面生效

### M4 · 外部化配置

- [ ] `Environment` / `PropertySource` 抽象落地
- [ ] properties / yaml 解析器落地
- [ ] `@Value` 占位符 + 默认值 + 类型转换
- [ ] Profile 多环境覆盖
- [ ] 单测：三种解析 + 覆盖优先级

### M5 · Web 与 MVC

- [ ] `WebServer` SPI + `SunHttpServer` 实现落地
- [ ] `DispatcherServlet` 前端控制器
- [ ] `HandlerMapping` / `HandlerAdapter` / `HandlerMethod`
- [ ] 参数解析：`@PathVariable` / `@RequestParam` / `@RequestBody`
- [ ] 返回值处理：`@ResponseBody` + 自写 JSON
- [ ] 类型转换 `Converter`
- [ ] 集成测试：`GET /hello`、`GET /users/{id}`、`POST /users`

### M6 · 自动配置 + Starter

- [ ] `@Conditional` 及派生注解落地
- [ ] `AutoConfigurationImportSelector` + SPI 读取落地
- [ ] (至少一个) 演示 Starter 落地
- [ ] 集成测试：引入 starter → Bean 自动就绪；缺依赖 → 优雅跳过

### M7 · 启动器 + 事件 + demo

- [ ] `MiniSpringApplication.run()` 编排完整启动流程
- [ ] `@MiniSpringBootApplication` 复合注解
- [ ] `ApplicationEvent` / `ApplicationListener` 事件总线
- [ ] Banner
- [ ] `demo/` 示例应用跑通端到端（配置注入 + AOP + 自动配置 + Web 接口）
- [ ] README 补全「快速开始」，让任何人能 3 步跑起来

---

## 「工程级完成度」的通用要求（贯穿所有里程碑）

这些不挂在某个里程碑下，而是**每一个里程碑都必须满足**的基线：

| 维度 | 要求 |
| --- | --- |
| 可读性 | 直白写法优先，注释到位（中文），命名稳定 |
| 测试 | 核心类有单测，跨模块链路有集成测试；`mvn test` 全绿 |
| 诚实标注 | 明确标注「未实现/简化」的边界，不冒充完整实现 |
| 可运行 | 每个里程碑结束都保证 `mvn test` 通过、demo 可启动 |
| 文档回填 | 实现完成后，回填对应设计文档的「实现细节」小节 |

---

## 明确不在范围内的东西

为避免目标蔓延，以下事项**刻意不做**（除非未来单独立项）：

- 生产级性能（NIO / Reactor / 异步 / 连接池调优）
- 安全（认证 / 授权 / CSRF / CORS 完整实现）
- 多线程并发正确性保证
- 与 Spring 的 API 二进制/源码级兼容
- 完整 SpEL、完整 AspectJ 表达式、完整 YAML 规范
- Servlet 容器规范（Servlet API、Filter 链、Session）

---

## 版本约定

- 里程碑推进用 Git tag 标记（如 `v0.1.0-m1`、`v0.2.0-m3`），方便回溯每个阶段的完整可运行状态。
- 每个里程碑合并前，需通过该里程碑的「验收清单」人工核对。

> 路线图不是承诺书，是会随实现反馈动态调整的「活文档」。任何调整都会记录在此，持续对齐。