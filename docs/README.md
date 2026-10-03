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

## 维护与历史

[路线图](06-roadmap.md) 保留阶段计划、验收记录和当前债务；[history/](../history/README.md)
分类索引旧计划、施工、历史验收与失败材料。冻结证据不搬动、不改写；当前修改的验证应另行记录对象和范围。
