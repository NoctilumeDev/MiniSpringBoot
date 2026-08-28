# 05 · 外部化配置设计

> 对应模块：`mini-spring-config`
> 回答的问题：`application.yml` 里的一行配置，是怎么跑到你 `@Value` 字段上的？

---

## 1. 目标

把「散落在文件里的配置字符串」变成「可被 Bean 直接访问的强类型值」。核心能力四件事：

1. 解析 `application.properties`（扁平 `key=value`）。
2. 解析 `application.yml`（带缩进的树形结构 → 扁平化）。
3. 支持 `@Value("${key}")` 占位符注入。
4. 支持 `Profile`（多环境切换）。

---

## 2. 统一抽象：Environment 与 PropertySource

一切配置，无论来自文件、系统属性还是环境变量，都抽象成同一个「源头」：

```java
interface PropertySource {
    String getName();
    Object getProperty(String key);
}
```

`Environment` 聚合多个 `PropertySource`，按**优先级顺序**查找：

```
Environment
  ├─ systemProperties（系统属性，最高优先级）
  ├─ environmentVariables（环境变量）
  ├─ application-{profile}.properties/yml（带 profile 的配置）
  └─ application.properties/yml（默认配置）
```

查值时逐个 `PropertySource` 询问，谁先有值谁赢。`MutablePropertySources` 提供插入高优先级来源的机制，
但当前 `MiniSpringApplication.run(..., args)` **尚不解析命令行参数**，不能把可扩展机制写成已交付能力。

---

## 3. 解析器：properties 与 yaml

### 3.1 properties（简单）

`application.properties` 天生扁平，解析几乎无歧义：

```properties
server.port=8080
app.name=MiniSpringBoot
```

解析成 `Map<String, String>` 即可。

### 3.2 yaml（需要「拍平」）

yaml 用缩进表达层级，本质是「树」，而配置系统要的是「平铺的 key」。所以核心动作是 **把树拍平成 `.` 连接的 key**：

```yaml
server:
  port: 8080
  ssl:
    enabled: true
app:
  name: MiniSpringBoot
```

拍平后：

```properties
server.port=8080
server.ssl.enabled=true
app.name=MiniSpringBoot
```

> MiniSpringBoot 只实现 YAML 的**教学子集**：缩进映射、列表、标量、注释。**明确不支持** anchor(`&`/`*`)、多行字符串、复杂 tag 等冷门特性——这些不会出现在 99% 的配置场景，实现它们会淹没主线。

---

## 4. @Value 与占位符解析

`@Value("${app.name}")` 的解析是一个**「拉取值」动作**，发生在 Bean 属性填充阶段（对应 [01 章](01-ioc-container.md)生命周期第 2 步）：

```
属性填充阶段，发现字段标注 @Value("${app.name}")
   ↓
占位符解析器解析 "${app.name}" → 得到 key "app.name"
   ↓
从 Environment 按优先级查值 → "MiniSpringBoot"
   ↓
类型转换 → 注入字段
```

支持能力：

| 写法 | 含义 |
| --- | --- |
| `@Value("${key}")` | 必须有值，找不到则报错 |
| `@Value("${key:default}")` | 找不到用默认值（冒号后） |
| `@Value("#{...}")` | SpEL（首版标注为 TODO，不属于核心主线） |
| 嵌套占位符 `${a.${b}}` | 递归解析，Spring 支持，本项目作为进阶项 |

---

## 5. Profile 多环境

`Profile` 回答「同一份代码，怎么适配开发/测试/生产三套环境」。

机制：调用方在加载配置前通过 `StandardEnvironment#setActiveProfiles(...)` 手动指定 `active profile`，
加载器据此**多加载一份对应的配置文件**，并让其优先级**高于默认文件**。当前实现要求 classpath
中至少存在一份默认 `application.properties` / `application.yml` 作为插入锚点；若只有 profile 文件而
没有默认文件，加载器不会读取该 profile 文件。当前也未从 `spring.profiles.active`、
`SPRING_PROFILES_ACTIVE` 或 `run(..., args)` 自动激活 Profile。

```
profile = "prod"
   ├─ application.yml          （默认，低优先级）
   └─ application-prod.yml     （覆盖默认，高优先级）
```

配合 [04 章](04-auto-configuration.md) 的 `@ConditionalOnProperty`，还能做到「不同环境装配不同 Bean」，例如开发环境用 mock、生产环境用真实实现。

---

## 6. 配置优先级总表（从高到低）

| 优先级 | 来源 |
| --- | --- |
| 1 | 系统属性 `System.getProperties()` |
| 2 | 环境变量 |
| 3 | 手动激活的 `application-{profile}.properties/yml`（后激活者优先，同层 `.properties` 优先） |
| 4 | `application.properties/yml`（同层 `.properties` 优先） |

> 这张表描述的是当前代码事实。命令行覆盖与自动 Profile 激活仍是显式技术债，排障时不能假定它们存在。

---

## 7. 验收要点（M4 里程碑）

- `application.properties` 解析正确，`@Value` 能注入 ✅
- `application.yml` 拍平正确（含多级缩进与列表）✅
- `${key:default}` 默认值生效 ✅
- `@Value` 支持字符串 → 基本类型/包装类型的转换 ✅
- 指定 `profile` 后，`application-prod.yml` 覆盖默认值 ✅
- `ConfigDemo` 的运行时断言覆盖 properties、yaml、默认值、嵌套占位符、类型转换与 Profile 覆盖；当前测试树没有单独的占位符单测，不能把这份 main 演示证据写成单测覆盖。
