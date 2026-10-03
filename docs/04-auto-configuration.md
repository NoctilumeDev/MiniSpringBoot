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
@ConditionalOnClass(name = "com.zaxxer.hikari.HikariDataSource")
@ConditionalOnProperty(name = "minispring.datasource.url") // 还需配置连接地址
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
| `@ConditionalOnBean` | 容器中已有指定类型或名称的 Bean |

`@ConditionalOnMissingClass` 属于 Spring 对照概念，本项目未实现。

这些条件组合起来，就形成了「智能装配」的判断逻辑：**有依赖、没冲突、开关开，才动手。**

---

## 4. 第二把钥匙：自动导入（SPI 机制）

条件注解解决「要不要装配」，候选配置类则由 classpath 资源发现。当前 MiniSpringBoot 使用
`META-INF/minispring/EnableAutoConfiguration.imports`，每行一个全限定类名，空行和以 `#`
开头的行被忽略；不解析早期规划中的 `mini.factories` 键值格式。例如演示 starter 的真实资源是：

```text
# META-INF/minispring/EnableAutoConfiguration.imports
com.minispring.starter.demo.FormatAutoConfiguration
```

`AutoConfigurationLoader` 汇总各 jar 的同名资源，`AutoConfigurationImportSelector` 去重、排序并延迟导入；用户配置和组件扫描先落地，再由 `@Conditional` 筛选候选。于是：

```
启动 → 读取 EnableAutoConfiguration.imports → 得到候选配置类清单
     → 逐个 @Conditional 判定 → 命中的才注册其 Bean
```

本项目可直接观察的例子是 `FormatAutoConfiguration`：引入演示 starter 并开启自动配置后，用户未提供 `FormatService` 时注册 `UpperCaseFormatService`；用户已提供时由 `@ConditionalOnMissingBean` 让路。

---

## 5. Starter：把「依赖 + 自动配置」打包成一个约定

在本项目中，演示 starter 除依赖声明外还包含格式服务与自动配置类，用于展示这两件事：

1. 用 `pom.xml` 声明「真正干活的依赖」（传递依赖）。
2. 在自身 jar 的 `META-INF/minispring/EnableAutoConfiguration.imports` 中逐行声明自动配置类；框架的配置类清单另由 autoconfigure 模块持有。

这样用户只需引入一个 starter，就同时获得了「依赖」和「对这些依赖的自动装配」，二者缺一不可：

```
引入 mini-spring-starter-demo 并开启自动配置
     ├─ 依赖：mini-spring-autoconfigure
     └─ imports 记录：FormatAutoConfiguration
              │
              └─ 启动时被自动导入 + @Conditional 放行 → Bean 就绪
```

> 「依赖」是**弹药**，「SPI + 条件注解」是**装填机制**。只有弹药没有装填，用户得手写配置；只有装填没有弹药，条件永远不成立。这正是「约定优于配置」的物质基础。

---

## 6. 当前发现与导入链

```
@Conditional(注解)  →  Condition(接口)  →  具体 Condition 实现
        │
AutoConfigurationImportSelector  — 读 SPI 文件，批量导入候选
        │
@EnableAutoConfiguration        — 通过 @Import 触发选择器，无 SPI 键值项
        │
xxxAutoConfiguration(配置类)     — 带 @Conditional 的 @Configuration
```

---

## 7. 验收要点（M6 里程碑）

- 定义一个 `@ConditionalOnMissingBean`，证明「用户先注册则自动配置让路」✅
- 写一个演示 starter，引入后自动装配出一组 Bean（无需任何 `@Configuration`）✅
- 用 `@ConditionalOnClass` 演示「缺依赖时自动跳过、不报错」✅
- 验证 SPI 文件被正确读取、候选类被逐个条件判定 ✅
- 当前资源采用逐行 imports 格式，见 `AutoConfigurationLoader.IMPORT_FILE` 与演示 starter 的同名资源；旧 `mini.factories` 只是已被替代的规划，不是可运行输入。