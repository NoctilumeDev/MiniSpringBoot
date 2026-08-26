# 07 · 启动器 + 事件 + 后端 demo 收口（boot）

## 1. 目标

把 M5/M6 里「手动 `new` 上下文 + 手动调 `ConfigFilePropertySourceLoader` + 手写 `@Bean` 注册 MVC 三大件 / `@Value` 处理器」的样板，收敛为一条 `MiniSpringApplication.run(...)`；同时收口 AOP 的两个正确性债务（**B2 提前暴露代理**、**D30 收集期漏代理**）。

## 2. 模块边界与依赖方向

**不变**：依赖单向、禁止环；内核（M1~M7）零第三方依赖。

```
依赖方向（A ← B 表示 B 依赖 A；自上而下、无环）：

core ← context ← autoconfigure ← starter-demo
  ↑       ↑          ↑
config   aop         boot（同时依赖 config）
          ↑
        jdbc
context ← web

autoconfigure --optional--> config / aop / web / jdbc / HikariCP

demo 层：mini-spring-demo ← (boot + web + aop + starter-demo)
```

- `mini-spring-boot`：依赖 `mini-spring-autoconfigure` + `mini-spring-config`（`run()` 内调 `ConfigFilePropertySourceLoader` 加载 `application.*`）。
- `mini-spring-demo`（后端 demo 收口）：依赖 boot + web + aop + starter-demo，用 `run()` 组装。

### 关键设计决策（M7 内固化）

1. **当前自动装配统一归位于 `mini-spring-autoconfigure`**：
   - `WebMvcAutoConfiguration`、`AopAutoConfiguration`、`ValueAutoConfiguration`、`DataSourceAutoConfiguration` 与 `JdbcAutoConfiguration` 均在该模块，并由 `META-INF/mini.factories` 发现。
   - 对 config/aop/web/jdbc/HikariCP 的编译依赖全部为 `optional`；靠 `@ConditionalOnClass(name=...)` 判断可能缺失的类，裁掉能力依赖时对应配置安全跳过且不向使用方强制传递。
2. **D19 已关闭**：demo 专属自动配置位于 `mini-spring-demo`，框架自动配置模块不承载业务 demo；其对具体框架能力的依赖是直接 optional 集成，不是“仅依赖 context”。
3. **D8/D31：维持 JDK 动态代理，不引 CGLIB**（恪守内核零第三方依赖红线）；需要被 AOP 的 Bean 必须接口化。Controller 代理需求延后，不在 M7 强上 CGLIB。

## 3. 关键类

- `MiniSpringApplication`：`public static AnnotationConfigApplicationContext run(Class<?> primarySource, String... args)`。顺序：建 `StandardEnvironment` → `ConfigFilePropertySourceLoader.load(env)`（关掉「配置加载需手动」）→ `new AnnotationConfigApplicationContext(env, primarySource)`（构造期完成 refresh）→ 启动全部 `Lifecycle` → 广播 `StartedEvent` → 返回上下文。当前 `args` 仅保留兼容入口，尚不解析为配置。
- `@MiniSpringBootApplication`：`@Configuration + @ComponentScan + @EnableAutoConfiguration` 复合注解，复用 M6 的元注解查找。
- 事件总线：`ApplicationEvent` / `ApplicationEventPublisher` / `ApplicationListener<E>` / `SimpleApplicationEventMulticaster`；在 refresh 前、上下文就绪后、启动后、关闭时广播（`ContextRefreshedEvent` / `StartedEvent` / `ClosedEvent`）。方法级同步广播（教学子集，无需异步）。
- `Banner`：打印框架名 + 版本 + 启动耗时。

## 4. AOP 收口（B2 + D30 + D5）

- **B2（提前暴露代理）**：core 新增 `SmartInstantiationAwareBeanPostProcessor#getEarlyBeanReference(bean, beanName)`；`DefaultListableBeanFactory.getEarlyBeanReference` 委托它（不再直接 `return bean`）；`AspectJAutoProxyCreator` 实现，并用 `earlyProxyReferences` 去重，确保三级缓存提前暴露与一级缓存最终持有**同一个代理**。
- **D30（收集期漏代理）**：`getAdvisors()` 用 `buildingAdvisors` 防递归导致收集期间实例化的 Bean 拿到空 advisor 列表且永久漏代理。改为：收集期间不丢弃，收集完成后对「期间已实例化」的 Bean 补一次 `wrapIfNecessary` 判定。
- **D5（通知排序）**：`Advice` / 切面支持 `Ordered` / `@Order`，收集后按优先级排序执行。

## 5. M7 收口债务清单

**M7 关闭（零依赖、价值高）**

- D18 静态资源显式 `..` 拒绝
- D20 自动配置去重 + 排序（`@Order`/`@AutoConfigureOrder`）
- D22 `@Bean` 方法参数注入
- D23 `@Bean` 上的 `@Primary` / `@Qualifier`
- D24 `@Controller("name")` / `@RestController("name")` 显式名
- D25 `@ComponentScan` 扫到的 `@Configuration` 递归处理 `@Bean`
- D27 占位符循环引用防护

**M7 决策、债务保留**

- D8 / D31：维持 JDK 代理、不引 CGLIB（AOP 目标接口化）
- D32：文档化「无 CGLIB」为教学边界
- D19 / D33：demo 归位到 `mini-spring-demo`，autoconfigure 内核零 demo、移除自带 application.yml

**明确延后（非 M7 阻塞）**

- D26：prototype 循环依赖 → M7 标注「不支持」，不实现
- D2（`@Bean` initMethod/destroyMethod）、D14 / D28 / D16 → 数据 / 前端联调时（M8/M9）

## 6. 任务清单（串行）

1. `mini-spring-boot` 骨架：pom + 依赖方向校验（boot → autoconfigure + config）
2. `@MiniSpringBootApplication` 复合注解
3. `MiniSpringApplication.run` 骨架（配置加载 + 上下文 + refresh）
4. 事件总线 + 生命周期事件广播
5. `Banner`
6. AOP 收口：B2 `getEarlyBeanReference`（core 扩展点 + `AspectJAutoProxyCreator` 实现）
7. AOP 收口：D30 收集期补偿 + D5 排序
8. 自动装配归位 + D19 demo 归位：`WebMvcAutoConfiguration` / `AopAutoConfiguration` / `ValueAutoConfiguration` + SPI 文件
9. 收口债务：D18 / D20 / D22 / D23 / D24 / D25 / D27
10. `mini-spring-demo`：后端 demo 迁到 `run()` 一键启动
11. 三次质量审查 + 负向/边界验收 + `git tag v0.m7`

## 7. 验收（含负向/边界 · 每条有实测证据）

**happy path**：一条 `run()` 启动后端 demo，curl / 浏览器访问接口，事件按序触发、banner 打印、MVC 三大件与 `@Value` 处理器自动装配生效。

**负向/边界（回归 + 收口）**

- B4 回归：`GET /void` 返回 200 空响应，不断连
- B5 回归：3 并发慢请求约 2s 完成，不串行
- B2 收口：`A ↔ B` 循环依赖且 `A` 被切面命中时，注入给 `B` 的是**代理**（`isProxy=true`）
- D30 收口：切面 Bean 有依赖时，被依赖 Bean 仍被代理
- D27 收口：占位符自引用 / 环不 StackOverflow
- M6 回归：缺依赖自动配置跳过、用户覆盖生效
