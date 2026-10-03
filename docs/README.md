# MiniSpringBoot 文档导航

## 初次使用

1. [项目 README](../README.md)：定位、当前教学子集与本机首跑。
2. [数据库初始化](../deploy/mysql/README.md)：Docker 和本机 MySQL 的结构、数据与端口边界。
3. [总体设计](architecture.md) 与 [POM 派生架构图](architecture-overview.svg)：模块和依赖方向。

## 按机制学习

| 主题 | 教程 |
| --- | --- |
| IoC 与生命周期 | [01 · IoC](01-ioc-container.md) |
| 代理与切面 | [02 · AOP](02-aop.md) |
| HTTP Server 与 MVC | [03 · Web MVC](03-web-mvc.md) |
| 环境与配置 | [05 · 外部化配置](05-externalized-configuration.md) |
| 条件装配 | [04 · 自动配置](04-auto-configuration.md) |
| 启动与事件 | [07 · Boot](07-boot.md) |
| JDBC 与事务 | [08 · JDBC](08-jdbc.md) |
| 前端 | [09 · React](09-frontend.md) |
| 多实例与容量 | [10 · 高可用](10-high-availability.md) |

教程中的 Spring 对照概念与本项目已实现子集分别标注，不能把未实现接口当成可用 API。
工程边界见 [从教学到工程](teaching-to-engineering.md)。

## 当前教学子集边界

下表集中列出与本项目教程相关的明确边界。Spring 对照概念和旧计划中的 API 不代表本项目已交付；
这些项也不自动成为后续里程碑承诺。

| 领域 | 当前明确不支持的能力 | 详细说明 |
| --- | --- | --- |
| 容器 | `BeanFactoryPostProcessor`、`lazyInit`、`dependsOn`、`@PostConstruct` / `@PreDestroy`、国际化；构造器与 prototype 循环依赖不由三级缓存化解 | [IoC](01-ioc-container.md)；正常构造器注入、单例属性循环依赖和接口式生命周期回调已实现 |
| 事件 | 方法注解式 `@EventListener`、异步广播、刷新前事件 | [Boot](07-boot.md)；当前通过 `ApplicationListener<E>` 同步分发 |
| AOP | `@AfterReturning`、`@AfterThrowing`、类代理、完整 AspectJ 语法 | [AOP](02-aop.md)；当前使用 JDK 接口代理及 Before/After/Around |
| Web | 完整 Servlet API、视图与重定向解析、普通无注解参数自动绑定、编程式路由注册、`@ExceptionHandler` / `@ResponseStatus` | [Web MVC](03-web-mvc.md)；当前为 JDK HTTP Server 上的自有请求接口和注解路由 |
| 配置 | SpEL、`@ConfigurationProperties` / Binder 对象绑定、启动参数解析、配置项或环境变量自动激活 Profile、完整 YAML 规范 | [外部化配置](05-externalized-configuration.md)；当前为文件加载、占位符、`@Value` 字段注入与手动 Profile |
| 条件装配 | `@ConditionalOnMissingClass`、旧 `mini.factories` 键值输入 | [自动配置](04-auto-configuration.md)；当前发现入口为逐行 imports 文件 |
| 事务 | REQUIRED 之外的传播、`rollbackFor` 细化、声明式隔离级别配置、接口级继承事务注解 | [JDBC](08-jdbc.md)；当前复用线程内事务，异常与 Error 回滚，隔离级别来自数据源默认 |

JSON/YAML 的边角语法、类型转换范围和线程/部署边界仍应按对应章节阅读，不能从“同名类”推断与 Spring 完全兼容。

## 维护与历史

[路线图](06-roadmap.md) 保留阶段计划、验收记录和当前债务；[history/](../history/README.md)
分类索引旧计划、施工、历史验收与失败材料。冻结证据不搬动、不改写；当前修改的验证应另行记录对象和范围。
