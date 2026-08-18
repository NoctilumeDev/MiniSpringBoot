# 01 · IoC 容器设计

> 对应模块：`mini-spring-core` / `mini-spring-context`
> 回答的问题：Bean 是怎么被扫描、定义、实例化、注入、销毁的？循环依赖靠什么解开？

---

## 1. 为什么要 IoC

先讲清楚「反转控制」到底反转了什么。

传统写法里，**对象自己决定依赖**：

```java
class OrderService {
    // 自己 new 自己的依赖，控制权在 OrderService 手里
    private final OrderDao dao = new OrderDaoImpl();
}
```

IoC 之后，**对象的依赖由外部（容器）注入**：

```java
@Component
class OrderService {
    // 我不 new 了，谁要用我，谁负责把 dao 塞进来
    @Autowired
    private OrderDao dao;
}
```

「控制权」从「类自己 new」反转到「容器统一装配」。这一反转，带来了三个对开发者的切身好处：

1. **解耦**：`OrderService` 不再绑定具体实现，测试时可以注入 mock。
2. **单例管理**：容器保证 `OrderDao` 只有一个实例，随处复用。
3. **统一生命周期**：容器统一管理初始化、销毁、AOP 增强，调用者无需关心。

---

## 2. 核心抽象：BeanDefinition

容器不能拿着 `Class` 直接干活，它需要先「读懂」一个类，把它的元信息固化下来，这就是 `BeanDefinition`：

| 字段 | 含义 |
| --- | --- |
| `beanClass` | 目标类 |
| `beanName` | 容器内的唯一标识 |
| `scope` | `singleton` / `prototype` |
| `lazyInit` | 是否懒加载 |
| `initMethodName` / `destroyMethodName` | 生命周期回调方法名 |
| `propertyValues` | 待注入的依赖（字段/方法） |
| `dependsOn` | 显式声明的依赖顺序 |

`BeanDefinition` 是 Spring 内核的「第一公民」——**容器里流转的从来不是对象，而是对象的「图纸」**。有了图纸，才能在合适的时机去「施工」（实例化）。

---

## 3. BeanFactory 与 ApplicationContext 的分工

很多初学者混淆二者，这里用一句话划清：

- **`BeanFactory`**：一个纯粹的「Bean 工厂」，只负责`getBean`——够用，但缺少很多便利。
- **`ApplicationContext`**：在 `BeanFactory` 之上，叠加了**扫描、事件、国际化、配置解析**等能力，才是日常 `getBean` 背后真正的东西。

本项目的对应设计：`DefaultListableBeanFactory` 提供「生产 Bean」的最底层能力，`ApplicationContext` 组合它，并驱动「扫描 → 注册 → 刷新」的完整流程。

```
ApplicationContext（组合，负责编排）
        │ 持有
        ▼
DefaultListableBeanFactory（提供 getBean 的底层能力）
        │ 持有
        ▼
BeanDefinitionRegistry（保存 BeanDefinition 图纸）
```

---

## 4. Bean 的完整生命周期

这是全项目最重要的「主旋律」，务必烂熟于心：

```
实例化（构造器/工厂方法）
   ↓
属性填充（@Autowired / @Value 注入）
   ↓
Aware 回调（BeanNameAware / BeanFactoryAware）
   ↓
BeanPostProcessor.postProcessBeforeInitialization
   ↓
初始化（InitializingBean.afterPropertiesSet / @PostConstruct）
   ↓
BeanPostProcessor.postProcessAfterInitialization   ← AOP 代理在这里生成
   ↓
放入一级缓存，对外提供
   ↓
（容器关闭）DisposableBean.destroy / @PreDestroy
```

设计要点：

- **两步注解回调的顺序**：`@PostConstruct` 先于 `InitializingBean`（二者均为「初始化」环节，注解优先级更高）。
- **AOP 的插入点**：代理对象在 `postProcessAfterInitialization` 生成——所以「代理」永远发生在「原始对象完全就绪」之后。这是理解 AOP 与生命周期关系的关键。

---

## 5. 循环依赖与三级缓存（难点）

### 5.1 问题

```java
@Component
class A {
    @Autowired B b;
}
@Component
class B {
    @Autowired A a;
}
```

A 依赖 B，B 依赖 A。若按「先完整创建 A 再创建 B」的死板顺序，会陷入死锁。Spring 用「**提前暴露半成品**」的方式破解：**先把对象的引用暴露出去，再慢慢填充它的属性。**

### 5.2 三级缓存（同构复刻 Spring）

| 缓存 | 类型 | 存放什么 |
| --- | --- | --- |
| 一级 `singletonObjects` | `Map<String, Object>` | **已完全就绪**的单例 |
| 二级 `earlySingletonObjects` | `Map<String, Object>` | 提前暴露的**原始半成品**（可能已被代理） |
| 三级 `singletonFactories` | `Map<String, ObjectFactory<?>>` | 能产出半成品的**工厂**（将来可能用于生成代理） |

### 5.3 破解过程（以 `A ←→ B` 为例）

```
createBean(A):
    instantiate(A)                    → a 的原始对象（属性还没填）
    把 a 的工厂放入三级缓存（提前暴露入口）
    populate(A)：
        需要 B → getBean(B)
            createBean(B):
                instantiate(B)        → b 的原始对象
                b 放入三级缓存
                populate(B)：
                    需要 A → getBean(A)
                        → 命中三级缓存，从工厂拿到 a 的（可能被代理的）半成品
                        → 升级到二级缓存
                    B 的 a 注入完成 ✅
                initialize(B) → B 就绪 → 放入一级缓存
        从一级缓存拿到就绪的 B，注入 A ✅
    initialize(A) → A 就绪 → 放入一级缓存
```

关键结论：**三级缓存的存在，是为了在「对象还没就绪」和「对象已被代理」之间留出缓冲**——半成品先放三级（工厂），真正被提前引用时才升级到二级。

### 5.4 为什么三级而不是两级

这是面试必考、也是本项目想讲透的地方。三级存在的核心理由是 **AOP 的介入**：

如果对象将来需要被 AOP 代理，那么「提前暴露的引用」必须是**代理后的引用**，否则注入进来的就是裸对象，AOP 失效。三级缓存里放的是 `ObjectFactory`（一个「能判断要不要代理、并生成正确引用」的工厂），让容器在需要时再决定返回裸对象还是代理对象。

> MiniSpringBoot 首版若暂不实现类级 AOP，三级缓存可能退化为两级即可；这里保留「三级」设计是为了与 Spring 对齐，并在文档中如实说明「第三级何时才是必要的」。

---

## 6. 扩展点：两种 PostProcessor

这是 Spring 优雅扩展的根基，也是本项目「工程级」要求的重点之一：

| 扩展点 | 介入时机 | 用途 |
| --- | --- | --- |
| `BeanFactoryPostProcessor` | 所有 BeanDefinition 注册后、实例化前 | 修改「图纸」（如解析 `@Value` 里的 `${}`） |
| `BeanPostProcessor` | 每个 Bean 实例化后的两个环绕点 | 修改「成品」（如生成 AOP 代理） |

二者名字只差一个 `Factory`，意义天差地别：前者改**图纸**，后者改**成品**。务必区分。

---

## 7. 作用域

首版支持两种，覆盖 99% 场景：

- **singleton**（默认）：容器中只有一个实例，三级缓存管理的正是它。
- **prototype**：每次 `getBean` 都新建，**不进入缓存、不参与循环依赖破解**、容器也不负责销毁。

> 复刻时务必注意：prototype 的 Bean 容器只负责「生」，不负责「养」——这是新手实现时最容易忘记销毁管辖的地方。

---

## 8. 设计类图（规划）

```
BeanDefinition          — 图纸
BeanDefinitionRegistry  — 图纸仓库
DefaultListableBeanFactory — 施工队（getBean 底层能力）
ApplicationContext      — 项目经理（编排扫描/刷新）
BeanPostProcessor       — 质检员（改成品）
BeanFactoryPostProcessor — 图纸审批员（改图纸）
ObjectFactory           — 半成品工厂（三级缓存）
```

---

## 9. 验收要点（M1/M2 里程碑）

- 能 `@ComponentScan` 扫描到包内组件并注入依赖 ✅
- 支持构造器注入 + 字段注入 + setter 注入 ✅
- 支持 `@Scope` 两种作用域 ✅
- 支持 `@PostConstruct` / `@PreDestroy` ✅
- 用单测证明「循环依赖 A←→B」能够被正确解开 ✅
- BeanPostProcessor 介入能改变最终 Bean（用「包装 Bean」的 demo 验证）✅