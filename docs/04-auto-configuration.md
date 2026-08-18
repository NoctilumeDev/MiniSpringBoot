# 04 · 自动配置与 Starter 设计

> 对应模块：`mini-spring-autoconfigure`
> 回答的问题：为什么只引入一个 Starter 依赖，就能「凭空」多出一堆可用的 Bean？

---

## 1. 灵魂：约定优于配置

Spring Boot 最著名的口头禅是「约定优于配置」（Convention over Configuration）。它的含义是：

> **绝大多数情况下，你需要的配置是「可预测的默认值」。** 既然如此，就别让用户写，框架帮你在幕后把这些默认 Bean 组装好；只有当你偏离约定时，才需要显式配置。

自动配置（Auto-configuration）就是这句口号的机器化实现：**框架在你启动时，根据 classpath 上「有什么」、容器里「缺什么」，自动决定要不要装配某些 Bean。**

---

## 2. 从「手动装配」到「自动装配」

先看手动装配（没有自动配置的世界）：

```java
@Configuration
class MyConfig {
    @Bean
    DataSource dataSource() {
        return new HikariDataSource(...);  // 用户每次都要手写这几个 Bean
    }
}
```

自动配置的世界里，框架替你准备好这份「配置清单」，并附带触发条件：

```java
@Configuration
@ConditionalOnClass(name = "javax.sql.DataSource")      // 有 JDBC 就有资格
class DataSourceAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean                          // 用户没自己配才自动造
    DataSource dataSource() { ... }
}
```

这就是自动配置的两把钥匙：**条件注解** + **自动导入**。下面分别拆解。

---

## 3. 第一把钥匙：@Conditional 条件装配

`@Conditional` 回答一个问题：**「这个 Bean / 配置类，在当前环境里该不该生效？」** 判定逻辑委托给一个实现 `Condition` 接口的类：

```java
@FunctionalInterface
interface Condition {
    boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata);
}
```

基于它派生出几个高频条件注解：

| 注解 | 生效条件 |
| --- | --- |
| `@ConditionalOnClass` | classpath 存在某类（判断「有没有相关依赖」） |
| `@ConditionalOnMissingBean` | 容器里还没有该 Bean（判断「用户是否已自己配」） |
| `@ConditionalOnProperty` | 某个配置项等于某值（判断「开关是否打开」） |
| `@ConditionalOnMissingClass` | 与第一个相反 |

这些条件组合起来，就形成了「智能装配」的判断逻辑：**有依赖、没冲突、开关开，才动手。**

---

## 4. 第二把钥匙：自动导入（SPI 机制）

条件注解解决「要不要装配」，但框架还得知道「**有哪些自动配置类等着被考虑**」。Spring Boot 用 `META-INF/spring.factories`（SPI 文件）解决「发现」的问题。MiniSpringBoot 复刻这一机制，命名为 `META-INF/mini.factories`：

```properties
# META-INF/mini.factories
io.github.noctilumdev.minispring.boot.EnableAutoConfiguration=\
com.example.DataSourceAutoConfiguration,\
com.example.WebMvcAutoConfiguration
```

启动流程中，`AutoConfigurationImportSelector` 去读这个文件，把所有候选的自动配置类「批量导入」到容器里，再由 `@Conditional` 逐个筛选。于是：

```
启动 → 读取 mini.factories → 得到候选配置类清单
     → 逐个 @Conditional 判定 → 命中的才注册其 Bean
```

**这一段是理解 Spring Boot 的「题眼」**：它解释了为什么引入 `spring-boot-starter-web` 后，`DispatcherServlet`、内嵌 Tomcat、Jackson 全都自动就绪——因为 starter 里那条 SPI 记录指向了 `WebMvcAutoConfiguration`，而它又被 `@ConditionalOnClass` 验证「相关类都在 classpath」后放行。

---

## 5. Starter：把「依赖 + 自动配置」打包成一个约定

一个 Starter 本质上是**一个几乎不含代码的聚合模块**，它只做两件事：

1. 用 `pom.xml` 声明「真正干活的依赖」（传递依赖）。
2. 附带一份 `META-INF/mini.factories`，声明「这些依赖对应的自动配置类」。

这样用户只需引入一个 starter，就同时获得了「依赖」和「对这些依赖的自动装配」，二者缺一不可：

```
引入 spring-boot-starter-web
     ├─ 传递依赖：web / json / server 等实现
     └─ SPI 记录：WebMvcAutoConfiguration 等
              │
              └─ 启动时被自动导入 + @Conditional 放行 → Bean 就绪
```

> 「依赖」是**弹药**，「SPI + 条件注解」是**装填机制**。只有弹药没有装填，用户得手写配置；只有装填没有弹药，条件永远不成立。这正是「约定优于配置」的物质基础。

---

## 6. 设计类图（规划）

```
@Conditional(注解)  →  Condition(接口)  →  具体 Condition 实现
        │
AutoConfigurationImportSelector  — 读 SPI 文件，批量导入候选
        │
EnableAutoConfiguration(SpiKey)  — SPI 文件的 key
        │
xxxAutoConfiguration(配置类)     — 带 @Conditional 的 @Configuration
```

---

## 7. 验收要点（M6 里程碑）

- 定义一个 `@ConditionalOnMissingBean`，证明「用户先注册则自动配置让路」✅
- 写一个演示 starter，引入后自动装配出一组 Bean（无需任何 `@Configuration`）✅
- 用 `@ConditionalOnClass` 演示「缺依赖时自动跳过、不报错」✅
- 验证 SPI 文件被正确读取、候选类被逐个条件判定 ✅
- 文档清晰标注 MiniSpringBoot 的 `mini.factories` 与 Spring `spring.factories` 的对应关系 ✅