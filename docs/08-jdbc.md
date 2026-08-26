# 08 · 数据库接入（jdbc + MySQL + HikariCP）

## 1. 目标

给内核接上真实数据库：`mini-spring-jdbc` 模块（JdbcTemplate 等价物，纯 `java.sql.*` 标准接口，零第三方）、`DataSourceAutoConfiguration`（D45 模式：optional + name 探测）、demo 层 MySQL 8（Docker，512MB 预算）+ HikariCP + mysql-connector-j。**验收的唯一事实：MySQL 表里真实查到数据（docker exec mysql CLI 直查）、CRUD 真实落库、事务回滚真实生效、并发下连接池无泄漏。**

## 2. 模块边界与依赖方向

```
core ← context ← aop
 ↑        ↑
 jdbc（新，零依赖：纯 java.sql.*，不依赖任何 minispring 模块）
 ↑
autoconfigure（对 jdbc / HikariCP 均 optional，@ConditionalOnClass(name) 探测）
 ↑
boot / demo 层（demo 引 mysql-connector-j + HikariCP，双轨制允许）
```

- **`mini-spring-jdbc`（新模块）**：`DataAccessException` 体系、`JdbcTemplate`、`RowMapper<T>`、`TransactionManager`（编程式）+ `@Transactional`（声明式，基于 M3 的 AOP）。**不依赖任何 minispring 模块**——它只面向 JDBC 标准接口，天然零第三方（内核纪律延伸）。
- **`DataSourceAutoConfiguration` / `JdbcAutoConfiguration` 放 autoconfigure**：对 `mini-spring-jdbc`、HikariCP 全 optional（D45 已验证的模式）；类级条件 `@ConditionalOnClass(name = "com.zaxxer.hikari.HikariDataSource")`，方法体 `new HikariDataSource()` 只在条件命中后执行。
- **demo 层**（`mini-spring-demo`）引真实依赖：`com.mysql:mysql-connector-j:8.4.0` + `com.zaxxer:HikariCP:5.1.0`（runtime）。

## 3. 关键设计

### 3.1 配置约定（决策点 A，推荐 ①）

| 前缀 | 理由 |
| --- | --- |
| ① `minispring.datasource.{url,username,password,max-pool-size}` | 前缀显式归属本框架，机制与 Spring 的 `spring.datasource.*` 同构不同名 |
| ② `datasource.*` | 更短，但与用户习惯的 spring.* 混在 classpath 时语义模糊 |

配套：`@ConditionalOnProperty("minispring.datasource.url")`——没配数据源就不装配（纯内存应用照常跑）。

### 3.2 JdbcTemplate（教学子集，够用即止）

```java
<T> List<T> query(String sql, RowMapper<T> mapper, Object... args)
<T> T queryOne(...)
int update(String sql, Object... args)
Long insertAndReturnKey(String sql, Object... args)   // GENERATED_KEY，users 自增主键需要
```

- 每次操作从 DataSource 取连接、用完即还（池语义由 HikariCP 承担）；
- 事务上下文（ThreadLocal）里有连接时**复用不归还**（归事务边界负责）；
- `SQLException` 统一翻译为 `DataAccessException`（unchecked），保留 cause。

### 3.3 事务（决策点 B，推荐入 M8）

- **编程式**：`TransactionManager.execute(callback)`——`setAutoCommit(false)` → ThreadLocal 绑定事务状态 → commit/rollback（异常回滚）→ finally 归还。参与同一 REQUIRED 事务的内层调用失败时会标记 rollback-only；即使外层业务捕获了该异常，最外层边界也会回滚并抛出 `UnexpectedRollbackException`。
- **声明式**：`@Transactional` 注解 + `TransactionAspect`（`@Around` AOP）；受 D8 约束，**Service 必须接口化**（demo 的 AccountService 落接口）。
- 不做：REQUIRED 以外的传播行为（如 REQUIRES_NEW / savepoint 式 NESTED）、隔离级别定制（用 MySQL 默认 RR）、`@RollbackFor` 细化（任何 RuntimeException/Exception/Error 都回滚）。

### 3.4 D34 修复（M8 第一批，前置）

注入裁决改为：**限定名匹配 BeanDefinition.qualifier → beanName → 类型唯一 → @Primary**。多数据源（用户自定义第二个 DataSource）时靠它拍板，不修则 M8 必现。

### 3.5 D2 修复（M8 第一批，前置）

`@Bean(initMethod=…, destroyMethod=…)`：`AnnotatedBeanDefinitionReader` 读取两属性 → `BeanDefinition` 已有 `initMethodName/destroyMethodName` 字段（M1 就有，只缺注解入口）。**DataSource 销毁必须靠它**（HikariDataSource.close() 释放池，否则 demo 停止时线程泄漏）。

## 4. demo 数据流（MySQL 容器：mysql:8.0，mem_limit 512M，宿主端口 3306——占用则 13306，决策点 C）

- 表：`users(id PK AI, name, email)`（对齐现有 User）、`accounts(id PK, balance DECIMAL)`（转账）；
- `UserController`（直接注入 `JdbcTemplate`，无独立 Repository 层——教学子集不设 DAO 抽象）：GET/POST/PUT/DELETE `/users`，**写后 docker exec 直查 MySQL 取证**；
- `AccountService.transfer(from, to, amount)`（`@Transactional`）：扣款+入款两条 UPDATE；构造「扣款后抛异常」→ 两账户余额不变（回滚取证）；
- 隔离实证：MySQL 默认 RR，事务 A 改未提交、事务 B 读旧值 → 「无脏读」的运行期证据。

## 5. 验收清单（唯一事实约束，§1.7/§1.8 纪律）

| # | 用例 | 事实 |
| --- | --- | --- |
| V1 | CRUD 全链路 | POST /users 后 `docker exec mysql mysql -e "select..."` 查到同一行 |
| V2 | 自增主键回填 | 响应 JSON 的 id 与 DB 的 AUTO_INCREMENT 一致 |
| V3 | 事务回滚 | 转账中途抛异常 → 两账户余额 = 转账前（CLI 查证），非半扣半入 |
| V4 | 事务提交 | 正常转账 → 余额变动精确等于 amount |
| V5 | 无脏读 | 事务 A 未提交时 B 读到旧值 |
| V6 | 池无泄漏 | 100 并发请求后 Hikari active=0、total ≤ max-pool-size |
| V7 | 负例：DB 断连 | `docker stop mysql` → 接口 500（可读错误）非线程挂死；restart 后自愈 |
| V8 | 负例：SQL 错误 | 唯一键冲突/语法错 → DataAccessException → 500，连接仍归还（V6 复测） |
| V9 | D45 回归 | 不引 jdbc/HikariCP 的 SplitVerify 场景照常启动（自动配置安全跳过） |
| V10 | 关闭钩子 | Ctrl+C → HikariDataSource.close() 执行（D2 生效），JVM 干净退出 |

## 6. 任务清单（严格串行）

1. 启动 Docker Desktop + MySQL 8 容器（512M）+ 建表 SQL（`deploy/mysql/`）
2. D2：`@Bean` initMethod/destroyMethod（+ 负例：方法不存在时报错）
3. D34：注入裁决 qualifier → beanName → 类型 → primary（+ 双数据源用例）
4. `mini-spring-jdbc` 模块：异常体系 + JdbcTemplate + RowMapper（+ 单测：H2？**否——用 MySQL 容器真连**，教学项目不引 H2）
5. 编程式 TransactionManager + 声明式 @Transactional（AOP）
6. `DataSourceAutoConfiguration` + `JdbcAutoConfiguration`（optional + name + @ConditionalOnProperty）+ SPI
7. demo：UserController（直接注入 JdbcTemplate）/ AccountService(接口化) / Controller + application.yml
8. V1~V10 全跑 + M0~M7 回归（30 单测 + demo 10 接口 + 分离 classpath 两场景）
9. roadmap / README 更新 + 三次审查记录 → tag `v0.m8`

## 7. 边界与债务

- 不做：ORM/实体映射、连接池参数调优（仅 max-pool-size 可配，默认 10、硬上限 256）、REQUIRED 以外的传播行为、savepoint 式嵌套事务、分布式事务；
- 事务 ThreadLocal 不清理的防护：TransactionManager 必须 try-finally remove（否则固定 worker 被复用时会串事务，形成跨请求脏数据）；commit/rollback 失败则把终局标成 UNKNOWN 并丢弃连接，禁止恢复 auto-commit 后盲目归还连接池；
- D1（JAR 扫描）维持 M10；D44（切面收集期引用）维持如实标注。
