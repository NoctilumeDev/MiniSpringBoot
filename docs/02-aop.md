# 02 · AOP（面向切面编程）设计

> 对应模块：`mini-spring-aop`
> 回答的问题：切面是怎么让你「无感」地加上事务、日志、权限的？

---

## 1. 从一段「横切代码的腐化」说起

```java
void transfer(String from, String to, Money amount) {
    log.info("开始转账...");            // ← 日志
    try {
        auth.check(from);               // ← 权限
        // ... 真正的业务逻辑，只有这三行
        dao.move(from, to, amount);
    } catch (Exception e) {
        rollback();                     // ← 事务
        throw e;
    }
    log.info("转账结束");              // ← 日志
}
```

业务逻辑只占三行，却被日志、权限、事务这些「横切关注点」（Cross-cutting Concern）层层包围。更糟的是，**同样的模板要在成百上千个方法里复制**。

AOP 的解法很朴素：**把这些横切逻辑抽到一个「切面」里，容器在调用目标方法时，自动「编织」进去**，让业务方法回归纯净：

```java
@Transactional
void transfer(String from, String to, Money amount) {
    dao.move(from, to, amount);   // 只剩业务本身
}
```

---

## 2. 术语辨析（先建立词汇表）

| 术语 | 含义 | 类比 |
| --- | --- | --- |
| JoinPoint | 可以被切入的点（方法执行处） | 「可以下刀的位置」 |
| Pointcut | 切点——筛选**哪些** JoinPoint 命中 | 「下刀的位置筛选规则」 |
| Advice | 通知——命中后**做什么** | 「刀法」 |
| Aspect | 切面 = 切点 + 通知 | 「一套完整的刀法」 |
| Weaving | 编织——把切面织进目标 | 「下刀并缝合的过程」 |

一句话串起来：**「在满足切点规则的位置（JoinPoint）上，执行切面（Aspect）里的通知（Advice）」，这个过程叫编织（Weaving）。**

---

## 3. 通知类型

| 通知 | 时机 |
| --- | --- |
| `@Before` | 目标方法执行**前** |
| `@AfterReturning` | 目标方法**正常返回后** |
| `@AfterThrowing` | 目标方法**抛异常后** |
| `@After` | 目标方法**结束后**（无论成败，类似 finally） |
| `@Around` | **环绕**目标方法，最强大，可完全接管 |

`@Around` 之所以特殊，是因为它持有 `ProceedingJoinPoint`，能决定**是否继续**、**何时继续**、甚至**替换返回值**。事务、缓存、分布式锁等「需要包裹」的场景非它莫属。

---

## 4. 动态代理：AOP 的「幕后黑手」

AOP 并不修改你的原始字节码（那是 CGLIB/AspectJ 编译期织入的玩法）。MiniSpringBoot 采用 **JDK 动态代理**——在运行时，由 JVM 动态生成一个实现同样接口的代理类：

```
调用方 ──► 代理对象(Proxy) ──► InvocationHandler
                                   │
                                   ├─ 1. 执行 Before 通知
                                   ├─ 2. Method.invoke(原始对象, args)   ← 真正的业务
                                   └─ 3. 执行 After / AfterReturning 通知
```

关键认知：在常规稳定路径中，调用方从容器取得的、命中切点的业务 Bean 是代理对象；所谓「无感」，是业务调用先进入代理，再由代理调用原始目标，而不是修改原始字节码。这里不能绝对化成「原始对象从未被直接暴露」：循环依赖可能触发初始化前的早期代理；切面收集期还存在 D44 所记录的边界——已注入给其他 Bean 的早期裸对象引用不会因容器随后回填代理而自动替换。

代理接口不只取目标类直接声明的 `getInterfaces()`：容器会沿目标类的父类层级以及接口继承层级确定性收集完整业务接口。因此，`SpecializedService extends BaseService` 即使自己没有再次书写 `implements Service`，只要父类实现了该接口，它仍然可以进入同一条 JDK 自动代理链。无接口的目标依旧按教学边界直接放行，不引入类代理。

### 4.1 与 IoC 生命周期的衔接

回顾 [01 章](01-ioc-container.md)的 Bean 生命周期，常规路径在 **`postProcessAfterInitialization`** 生成代理：

1. 原始对象先完整创建（依赖注入、初始化全部完成）。
2. 容器在最后一步「狸猫换太子」，用代理替换掉原始对象放进缓存。

若单例因循环依赖被真正提前引用，三级缓存会委托 `SmartInstantiationAwareBeanPostProcessor#getEarlyBeanReference` 在初始化完成前生成同一代理，after 阶段只避免二次包装。另有切面收集期的 D44 已知边界，详见 [路线图](06-roadmap.md)。因此，`BeanPostProcessor` 是 **AOP 与 IoC 的缝合点**，但 `postProcessAfterInitialization` 不是所有路径唯一的代理生成时点。

---

## 5. 通知链的执行模型

一个目标方法可能命中多个切面。执行顺序需要有序、可预测。设计采用**责任链**：

```
Before A → Before B → 目标方法 → After B → After A
（先进先出的「栈」式结构：先织入的先执行 Before，后执行 After；目标抛错时 After 仍按 finally 语义执行）
```

`@Around` 是链中最灵活的节点：它自己决定是否调用 `proceed()`，从而「打断」或「短路」后续链条——这天然适合幂等、重试、断路器等场景。

---

## 6. 切点匹配设计

当前实现支持两种表达式：

1. **`@annotation(注解全限定名)`**：接口方法、实现类对应方法或目标类带指定注解时命中（如 `@Transactional`）。表达式在构造切点时就加载并校验类型；类不存在或实际不是注解都会启动期失败，不把配置错误拖进调用热路径。
2. **`execution(返回类型 包.类.方法(..))`**：类名和方法名支持 `*` 的简化 glob 子集。

> 完整 AspectJ 语法与任意正则表达式均未实现。MiniSpringBoot 只保留教学所需、可以明确解释和验证的子集。

---

## 7. 设计类图

```
Aspect             — 一个切面（含多个 Advisor）
Advisor            = Pointcut + Advice 的组合
Pointcut           — 判断某方法是否命中
Advice             — 命中后要执行的逻辑
MethodInterceptor  — 通知链节点（执行 proceed）
JdkDynamicAopProxy — 生成并执行 JDK 动态代理
AspectJAutoProxyCreator — 在 BeanPostProcessor 阶段判断并回填代理
```

---

## 8. 验收要点（M3 里程碑）

- 用 `@Around` 实现一个「方法计时」切面，demo 里输出耗时 ✅
- 用注解切点 `@Transactional` 实现一个「打印 begin/commit/rollback」的演示事务切面 ✅
- 多切面命中同一方法时，通知顺序符合预期 ✅
- 单测验证：被代理对象注入后，AOP 仍然生效（证明代理发生在注入之后且替换成功）✅
- 单测验证：业务接口只由父类实现时，子类 Bean 仍会被识别并暴露为 JDK 代理 ✅
- 文档明确标注「不支持类级代理」的边界 ✅
