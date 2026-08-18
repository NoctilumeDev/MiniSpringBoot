# 06 · 路线图与验收标准（M0 → M10）

> 推进方式：**严格串行**。前一里程碑未「落地验收」通过，后一个绝对不启动——不跳、不并行、不提前埋后续的坑。

---

## 1. 验收总则（规矩，先锁死）

1. **落地为准**：里程碑通过 = 真实可观测结果落地（真实 `main` 启动 / 浏览器真实访问 / 数据库真实落库）。**单测与日志只作最低基线，不作为验收依据。**
2. **分阶段落地口径**：无 Web/数据库的里程碑（M1~M4、M6、M7）以「真实 `main` 启动 + 运行期真实行为」验收；自 M5 起用浏览器 F12 实证；自 M8 起用数据库真落库实证。
3. **严格串行**：M0→M10 顺序推进，未经前述落地验收，绝不触碰后续代码。
4. **双轨制**：框架内核（M1~M7）零第三方依赖；demo 层（M8~M10）可引真实依赖（MySQL 驱动 + HikariCP）。
5. **接口契约**：跨模块只依赖下层公开接口，禁止触碰实现类；内部重构不改接口签名。
6. **三次质量审查门闩**：每个里程碑「打 tag / 判定通过」前，必须完成并记录三次审查——①**回顾审查**（是否回归破坏既有里程碑、是否违反接口契约、依赖方向是否仍单向）；②**当前审查**（happy path 之外，还要覆盖异常路径、边界值、null 输入等真实运行全路径）；③**前瞻审查**（本里程碑埋了哪些假设与简化，会在 Mₙ₊₁ / Mₙ₊₂ 的什么场景被打破）。三次审查未完成，不得进入下一里程碑。
7. **负向/边界用例必须进「落地证据」**：除 happy path 外，每个里程碑至少跑通一条负向/边界用例（void/null 返回、异常路径、并发、循环依赖、依赖缺失等），否则视为未验收。
8. **平台/第三方默认行为不得假设，先查证再下结论**：凡依赖 JDK / 库的默认行为（线程模型、资源加载、编码、加载顺序），必须查文档或写最小实测，禁止把「假设」写进 roadmap / README 当现状。
9. **stub / 空实现必须显式标注**：留空方法体或钩子必须带 `TODO` / `FIXME` 或登记 debt，禁止「空注释」带病合入打 tag。
10. **递归 / 缓存 / 并发类钩子必须闭环**：三级缓存、提前暴露、防递归开关等，要么实现完整，要么在接口契约标注「未实现」，禁止半成品合入。
11. **前瞻审查须「反例驱动」**：前瞻审查不得以「脑内枚举风险」代替，须针对本里程碑接口契约构造至少一条反例并实测验证。

---

## 2. 里程碑总览

```
 M0  环境就绪 + 规矩冻结 + 方案审批
 M1  IoC 容器（core）
 M2  注解与类扫描（context）
 M3  AOP（aop）
 M4  外部化配置（config）
 M5  Web/MVC + 内嵌服务器（web）
 M6  自动配置 + Starter（autoconfigure）
 M7  启动器 + 事件 + 后端 demo 收口（boot）
 M8  数据库接入（MySQL + HikariCP）
 M9  React 前端 + 前后端联调
 M10 3 实例 + Nginx 高可用 + 全链路终验
```

顺序：仅线性，M_{n+1} 依赖 M_n 已落地验收。

### 端口约定

- 后端：`server.port = 9090`（`application.yml` 默认值 + `WebDemo` 兜底一致）
- 前端：React/Vite 开发服务器 = `9010`（M9 `demo-frontend/` 落地生效）

---

## 3. 各里程碑产出与落地证据

### M0 · 环境就绪 + 规矩冻结 + 方案审批

- **产出**：工具链确认；「落地验收 / React / 串行 / 接口契约」写入文档；本里程碑表获批；整机环境实测 + 16GB 内存预算锁定。
- **实测环境（2026-08-18）**：
  - 硬件：16 GB RAM（实测 15.8 GB）/ 24 逻辑核
  - 工具链：JDK 17.0.12（Oracle，`JAVA_HOME=D:\IDEA\JDK17`）/ Maven 3.9.11 / Node v24.14.0 + npm 11.9.0 / Docker 29.7.2 + Compose v5.3.1（已装，引擎当前未运行，M8 前启动即可）
- **16GB 内存预算（Docker Compose 编排）**：Docker Desktop VM ~2GB + MySQL 8 容器 512MB（mem_limit）+ 后端×3 各 512MB（JVM -Xmx256m）≈1.5GB + Nginx 64MB；容器内合计 ≈2.1GB，加宿主（Windows+IDE+浏览器）峰值 ≈11GB，总在 16GB 内，留 ~4GB 余量。
- **落地证据**：文档更新为 React + 落地口径 + 环境实测记录，git 提交，方案获用户批准。

### M1 · IoC 容器

- **产出**：`BeanDefinition` / `BeanDefinitionRegistry` / `DefaultListableBeanFactory` / 三级缓存 / 生命周期 / `BeanPostProcessor`。
- **落地证据**：demo `main` 真启动，依赖注入成功、循环依赖 `A↔B` 正确解开、`@PostConstruct/@PreDestroy` 真实回调。

### M2 · 注解与类扫描

- **产出**：`@Component/@Service/@Repository/@Configuration/@Bean`、`@Autowired/@Qualifier/@Primary`、`@Scope`、`ClassPathScanner`。
- **落地证据**：demo 用注解真实扫描出 Bean 并完成注入，main 运行可观察。

### M3 · AOP

- **产出**：`Pointcut/Advice/Aspect/Advisor`、`@Before/@After/@Around`、JDK 动态代理。
- **落地证据**：demo 里 `@Around` 计时切面真实拦截目标方法，耗时真实打印。
- **落地边界（显式技术债）**：`DefaultListableBeanFactory.getEarlyBeanReference` 目前仍直接返回原始对象，尚未委托 AOP 代理器提前生成代理；因此「被代理 Bean 同时参与循环依赖」时，提前暴露的是裸对象而非代理，与容器最终持有的代理不一致。**M3 验收口径不含此联合场景，不阻塞**；归到 **M7 收口**（最迟 M8 数据接入前）接上——core 新增 `SmartInstantiationAwareBeanPostProcessor#getEarlyBeanReference` 扩展点，`AspectJAutoProxyCreator` 实现并用 `earlyProxyReferences` 去重，同时补「AOP × 循环依赖」集成场景验收。

### M4 · 外部化配置

- **产出**：`Environment/PropertySource`、properties/yaml 解析、`@Value` 占位符与默认值、Profile。
- **落地证据**：`ConfigDemo` 真实 `main` 启动，从 `application.yml` + `application.properties` 读值注入字段，并逐一断言——yaml 二级嵌套、列表拍平（`app.features[0]/[1]`）、int 类型转换、默认值（`${app.missing:3000}`）、嵌套占位符（`${app.${app.pointer}}`）、properties 解析（`app.version`）、prod profile 覆盖 `server.port`（9090→8443），全部在运行期真实通过。
- **落地边界（显式技术债）**：YAML 仅为「教学子集」、`@Value` 不支持 SpEL 与复杂类型、Profile 仅手动激活、配置文件仅 classpath 根——详见 §7 的 D10–D13。

### M5 · Web 与 MVC

- **产出**：`WebServer` SPI + `SunHttpServer`、前端控制器（`DispatcherServlet`）、`HandlerMapping/HandlerAdapter`、参数绑定（`ArgumentResolver` 策略链）、自写 JSON（`JsonParser`/`JsonSerializer`/`JsonObjectMapper`）、类型转换（`Converter`/`TypeConversionService`）、静态资源托管。
- **落地证据**：**浏览器真实打开** `GET /hello`、`GET /hello?name=World`、`GET /users/{id}`、`POST /users`（`@RequestBody` JSON）、`GET /`（静态首页），F12 Network 真实看到请求/响应；异常路径（`GET /users/999` → 500 原始异常、未知路径 → 404）真实返回。
- **落地边界（显式技术债）**：JSON 反序列化仅扁平 POJO；参数绑定依赖注解显式 `value()`（未启用 `-parameters`）；业务异常统一 500、无状态码映射；`SunHttpServer` 已补 cached 线程池（每请求一线程），仅无 NIO/池上限调优——详见 §7 的 D17。M5 不引入 AOP（`web` 不依赖 `aop`；AOP 计时已于 M3 验证，Controller 无接口无法被 JDK 代理，见 D8，M7 收口）。

### M6 · 自动配置 + Starter

- **产出**：
  - context：条件装配内核——`@Conditional` + `Condition/ConditionContext/AnnotatedTypeMetadata`（含元注解查找）+ `ConditionEvaluator`（AND 语义）；`@Import` + `ImportSelector` + `DeferredImportSelector`（延迟导入，保证「用户优先、自动兜底」）。
  - autoconfigure：`@EnableAutoConfiguration` + `AutoConfigurationImportSelector` + `AutoConfigurationLoader`（读 classpath 上所有 `META-INF/minispring/EnableAutoConfiguration.imports`）；`@ConditionalOnClass/@ConditionalOnBean/@ConditionalOnMissingBean/@ConditionalOnProperty` 及对应三个 `Condition` 实现。
  - starter-demo：`FormatService` + `FormatAutoConfiguration` + SPI 文件，演示「引入 starter → 自动装配」。
- **落地证据**：`AutoConfigDemo` 真实 `main` 启动并逐一断言——依赖存在（`@ConditionalOnClass`）装配、依赖缺失跳过、配置开关（`@ConditionalOnProperty`，`feature.optional.enabled=true`）装配、`@ConditionalOnMissingBean` 兜底默认实现与用户覆盖；`StarterDemo` 未显式注册任何 Bean、纯靠 starter 的 SPI 自动装配出 `FormatService`（转大写）。全部运行期真实通过。
- **落地边界（显式技术债）**：D19–D21，详见 §7。

### M7 · 启动器 + 事件 + 后端 demo 收口

- **设计**：详见 [07-boot.md](./07-boot.md)。
- **产出**：`mini-spring-boot` 模块——`MiniSpringApplication.run`（A-3 收口后：自动探测 `DispatcherServlet` → 启动内嵌服务器 + 注册关闭钩子，一条 `run()` 无样板）、`@MiniSpringBootApplication` 复合注解、事件总线（`ApplicationEvent`/`Publisher`/`Multicaster`/`Listener` + `ApplicationEventPublisherAware`）、`Banner`；自动配置类归位 autoconfigure 模块（A-1，与 spring-boot-autoconfigure 同构）；AOP 收口（B2 提前暴露代理、D30 收集期漏代理补偿、D5 通知排序）；后端 demo 迁到 `run()` 一键启动（`mini-spring-demo`）。
- **落地证据**：一条 `run()` 真实启动后端 demo（curl 实测 `/hello`、`/users/1`、POST `/users`（中文）、`/void`、`/capability/aop/order`（proxied=true）、`/capability/aop/fail`（500）、`/capability/autoconfig`、`/capability/starter/format`、静态资源 `/`，负向 `/../secret` 不泄露）；事件按序触发（含初始化期事件，A-6）；banner 打印；三大件/`@Value` 自动装配生效；`java -cp` 直启进程保活（服务器线程非守护）。回归：M1~M6 demo 全部通过；单测 28 个（6 模块，含负例/边界）。
- **落地边界（显式技术债）**：D8/D31（维持 JDK 代理、不引 CGLIB）、D32（无 CGLIB 教学边界）、D44（切面收集期注入引用与容器缓存不一致，已打警告）、D45（autoconfigure 聚合依赖的按需装配边界）——详见 §7；D2/D16 等延后项见 §7。

#### M7 三次质量审查记录（按 §1.6 门闩补档）

| 审查 | 时间 | 结论与证据 |
| --- | --- | --- |
| ① 回顾审查（回归/契约/依赖方向） | 2026-08-19 | M1~M6 demo 全量重跑通过；**发现并修复 A-1**：aop/config/web 反向依赖 autoconfigure（违反 §1.5 单向依赖），三个 AutoConfiguration 归位后恢复 `boot > autoconfigure > web > aop > context > core`；A-2 框架模块 demo 清零（web 5 类 + aop 5 类 + core 1 类）。 |
| ② 当前审查（异常/边界/null 全路径） | 2026-08-19 | 外审两轮共 14 项（B-1~B-8、A-1~A-6）全部实证复现并修复：JSON 数字加引号/元注解三处递归/prototype BPP 膨胀/监听器异常阻断/List 泛型擦除/asBoolean 静默 false/多次 write/误报 D28 撤销/run 不起服务器/@Target 撒谎（A-4 构造器+方法注入落地）/D30 残余（D44）/初始化期事件丢失（D35）；负例实测：`/../secret` 403/404、`/void` 200、构造器循环依赖可读错误。测试 5→28。 |
| ③ 前瞻审查（反例驱动，假设会在何时被打破） | 2026-08-19 | 反例①「`mvn exec:java` 启动服务器静默死亡」→ 实证 JDK HttpServer dispatcher 继承创建线程 daemon 属性 → 修复为非守护线程确定性保活；反例②「切面依赖被自己切点命中的 Bean」→ 登记为 D44（与 Spring 早期创建限制同源，打警告）；反例③「裁剪能力模块后自动配置仍装配」→ 第三轮以方案 B 修复并分离 classpath 实证关闭（D45）；反例④「运行期单例 containsBean 查不到」→ 分离 classpath 实测暴露 → D46 已修。M8 风险预置：D34（@Qualifier 裁决）在多数据源场景必现，M8 第一批修；DataSource/HikariCP 自动配置按 D45 模式（optional + name 探测）。 |

### M8 · 数据库接入

- **产出**：`mini-spring-jdbc` 模块（JdbcTemplate / RowMapper / DataAccessException 体系 / 编程式 TransactionManager + 声明式 @Transactional+AOP 切面，纯 `java.sql.*` 零第三方）、`DataSourceAutoConfiguration` + `JdbcAutoConfiguration`（D45 模式：optional 依赖 + `@ConditionalOnClass(name)` 探测 + `@ConditionalOnProperty`）、MySQL 8 容器（`deploy/mysql/`，mem 512M、宿主 13306）+ 建表脚本、demo 层 UserController（直接注入 JdbcTemplate，无独立 Repository 层——教学子集不设 DAO 抽象）/ AccountService（接口化，D8 约束）/ AccountController。
- **落地证据（V1~V10 唯一事实，docker exec 直查 MySQL / 真实 HTTP）**：
  - V1 CRUD 落库：POST /users → 响应 `{id:39}`，`docker exec` 直查同 email 同 id 同 name；V2 自增回填：响应 id=39 = DB 行 id，AUTO_INCREMENT=40 一致；
  - V3 事务回滚：`transfer-fail`（扣款后刻意抛异常）→ HTTP 500 + docker exec 直查两账户余额 900.00/1100.00 分文未动（无半扣半入）；V4 事务提交：`transfer` → 850.00/1150.00，变动精确等于 amount；
  - V5 无脏读（RR）：CLI 事务 A `UPDATE balance=999` 未提交时应用读 850（旧值），COMMIT 后读 999——未提交不可见、提交后可见双向实证；
  - V6 池无泄漏：100 并发（50 并行）全部 200，MySQL 侧连接 11 条 = 10（max-pool-size 上限）+ 1（CLI 自身），无膨胀；
  - V7 断连自愈（**揪出 B9 后复验**）：`docker stop mysql` → 请求 30s（Hikari connectionTimeout 默认值，有限阻塞非挂死）后 500「SQL 执行失败: SELECT balance FROM accounts WHERE id = ?」（原始异常可读，修复前是 ITE 包装的「null」）；`docker start` 后 86ms 恢复 200；
  - V8 SQL 负例：重复 email 唯一键冲突 → 500（DuplicateKeyException 翻译），后续请求 200、连接仍 11 条（归还正常）；
  - V9 模块裁剪（D45 延续）：classpath 裁掉 mini-spring-jdbc/HikariCP/mysql 驱动——**application.yml 明明配置了 `minispring.datasource.url`** 但类不在，应用照常启动、服务器正常起、dataSource/jdbcTemplate/transactionManager 三个 Bean 全部不存在、exit 0（「配置在、类不在 → 安全跳过」锚定）；
  - V10 关闭钩子（D2 生效）：`System.exit(0)`（与 Ctrl+C 同一 JVM shutdown 序列）→ `ContextClosedEvent` 发布 → MySQL 侧池连接 4→1（仅 CLI 残留，`@Bean(destroyMethod="close")` 真实释放）→ 9090 端口释放 → exit 0。
- **M8 期间揪出并修复**：①TransactionAspect 构造注入 TransactionManager 与 dataSource 初始化互为依赖死结（纯自动配置应用必炸）→ 改 BeanFactoryAware 运行期懒解析（对齐 Spring TransactionInterceptor）；②`JdkDynamicAopProxy` 不命中切点的直通路径无 ITE 拆包（M3 对称遗漏，V7「500 null」根因）→ 修复 + 约束用例；③`ConditionEvaluator` 多个 `@Conditional` 派生注解只求值第一个（AND 语义破坏）→ `findAnnotations` 全收集逐一求值；④`RequestMappingHandlerMapping` 派生映射注解硬编码 Get/Post → 元注解统一处理（新增 @PutMapping/@DeleteMapping）；⑤`@annotation` 切点支持实现类方法命中（声明式事务前置）。
- **落地边界（显式技术债）**：事务仅 REQUIRED 传播 / 无隔离级别定制（MySQL 默认 RR）；HikariCP 仅 max-pool-size 可配（connection-timeout 等维持默认 30s，登记 D47）；JdbcTemplate 为教学子集（无批量/命名参数/分页）；D1（JAR 扫描）维持 M10。

#### M8 三次质量审查记录（按 §1.6 门闩）

| 审查 | 时间 | 结论与证据 |
| --- | --- | --- |
| ① 回顾审查（回归/契约/依赖方向） | 2026-08-19 | 依赖方向保持 `boot > autoconfigure > {web, jdbc} > aop > context > core` 单向无环（jdbc 仅依赖 aop，纯 JDBC 部分零模块依赖）；全量单测 44 个（M7 的 28 → 44，含 D2 生命周期负例、D34 双数据源裁决、多条件 AND、AOP 直通路径对称用例）全部通过；M0~M7 demo 接口回归通过；V9 分离 classpath 复证 D45 模式对 jdbc/HikariCP 成立。 |
| ② 当前审查（异常/边界/null 全路径） | 2026-08-19 | V1~V10 全部以 docker exec / 真实 HTTP 唯一事实跑通（见上）；揪出三处：B9（JdkDynamicAopProxy ITE 对称遗漏——M8 接口化 Service + 部分方法 @Transactional 使「经代理不命中切点」成常态路径，激活 M3 遗留缺陷，V7 实证 500 消息为 null）、TransactionAspect 死结、ConditionEvaluator AND 语义——全部修复且复验（V7 null→可读 SQL 消息）；负例实测：唯一键冲突 500 + 连接归还、断库 30s 有限阻塞 + 自愈、`../secret` 等既有负例保持。 |
| ③ 前瞻审查（反例驱动，假设会在何时被打破） | 2026-08-19 | 反例①「配置了 datasource.url 但裁掉驱动」→ V9 实证安全跳过（无 NoClassDefFoundError）；反例②「池内连接全死后的第一请求」→ V7 实证 Hikari evict + 30s 有限阻塞 + restart 自愈（86ms）；反例③「线程池复用串事务」→ TransactionContext 的 clear 在 finally（V6 并发 100 无脏事务佐证）；反例④「切面收集期依赖」→ 死结已按 Spring 同构机制（Aware 懒解析）消除，启动序列实证 transactionAspect 先于 dataSource 创建；登记 D47（Hikari 高级参数不可配）。M9 风险预置：CORS 未实现（浏览器跨源联调需前端代理或 M9 补）；JSON 日期/长整型精度边界。 |

### M9 · React 前端 + 联调

- **产出**：`demo-frontend/`（React 18 + Vite 5，JavaScript/jsx，端口 9010；Vite proxy `/api`→9090 rewrite 去前缀——决策点 A①/B① 已批）；两页极简 UI（用户管理：列表/新建/编辑/删除；转账演示：双按钮 + 余额卡），零路由/状态/UI 库；fetch 统一封装 + 全局错误横幅（后端可读错误原样到 UI）。
- **落地证据（V1~V8，浏览器真实操作 + F12 Network + docker exec 直查三方对照）**：
  - V1 渲染：浏览器打开 :9010，标题/tab/表格骨架完整，控制台零报错（截图存档）；
  - V2 列表=库：页面唯一行（id=23/甲/dup@v8.com）与 `docker exec` 直查完全一致；`GET /api/users` 200，payload 与 UI 逐字段相等（StrictMode 双请求为开发模式特征）；
  - V3 新建落库：表单新建 → 横幅「已新建用户 #48（落库 MySQL）」→ docker exec 出现 id=48/fe-m9@minispring.dev；
  - V4 编辑/删除落库：PUT #48 name→M9-Edited（DB 同步变化）；DELETE #48（confirm 弹窗接受）→ DB 行消失、表回到 1 行；
  - V5 转账提交：页面余额卡 989/1160 = MySQL 精确 ±10；POST /api/accounts/transfer 200，横幅回报双侧余额；
  - V6 转账回滚：[中途失败转账] → 红色横幅逐字透出后端异常「HTTP 500: … transfer-fail-in-middle（V3 回滚验收的刻意异常）」，余额卡与 DB 均不变（989/1160）；
  - V7 断库自愈（**揪出并修复 B10 后复验**）：`docker stop mysql` → 前端点击转账 → 有限阻塞（~15-20s，Hikari connectionTimeout）→ 红色横幅完整透出「事务执行失败（受检异常触发回滚）: minispring-hikari - Connection is not available, request timed out after 30003ms (total=0, active=0, idle=0, waiting=2)」——根因（含连接池状态）直达浏览器 UI；`docker start` 后页面刷新即恢复；
  - V8 F12 全链路：Network 面板见 /api/users、/api/accounts/*、POST/PUT/DELETE 全部请求与 200/500 状态、JSON 响应体。
- **M9 期间揪出并修复（B10，错误保真对称缺失）**：M8 的 V7 只验了事务外读路径（「SQL 执行失败: …」），事务内写路径（@Transactional）经 TransactionManager 包装后只剩「事务执行失败（受检异常触发回滚）」——根因文本丢失。按对称纪律修全家族四处：`TransactionManager` 两处包装 + `JdbcTemplate.translate` 两处（SQL 与 DuplicateKey 分支），包装消息一律携带 `e.getMessage()`；浏览器复验根因直达 UI。
- **落地边界（显式）**：CORS 显式不做（决策点 A① 推论：dev 用 Vite proxy、生产 M10 Nginx 同源反代）；前端不做构建产物部署（dist 留给 M10）；断连期间余额卡显示旧值且不标识陈旧性、多条错误横幅相互覆盖（demo 层 UX 简化，无正确性影响，如实标注）；D1/D47 维持 M10。

#### M9 三次质量审查记录（按 §1.6 门闩）

| 审查 | 时间 | 结论与证据 |
| --- | --- | --- |
| ① 回顾审查（回归/契约/依赖方向） | 2026-08-19 | 后端零改动兑现（唯一变更 jdbc 错误保真修复，属 bug 修正非契约变更）；前端与后端唯一耦合=JSON 契约+proxy 配置；M0~M8 回归：后端冒烟全绿（/hello、/void、/、/../secret 403、capability×4、accounts JSON）+ 44 单测全过 + MySQL 数据经多轮写删后与页面一致；教训沉淀：boot 模块 WebServer 单测绑 9090，跑全量测试前须停 demo 进程。 |
| ② 当前审查（异常/边界/null 全路径） | 2026-08-19 | V1~V8 三方对照（浏览器/Network/DB）全过；揪出 B10（事务路径错误保真对称缺失）并修全家族四处、浏览器复验根因直达 UI；负例实测：断库 500 横幅可读、回滚横幅逐字透出后端异常、confirm 删除可取消路径正常。 |
| ③ 前瞻审查（反例驱动） | 2026-08-19 | 反例①「前端在、后端挂」→ proxy 层 ECONNREFUSED 会以网络错误形态到 UI（fetch 封装已统一 catch，横幅呈现）；反例②「断连期间余额卡陈旧值误导操作者」→ 如实标注为边界（教学取舍）；反例③「StrictMode 双请求导致重复写」→ 核对：写操作均由按钮触发（非 effect），双请求仅出现在 effect 内的读路径，无重复写风险；反例④「M10 Nginx 托管 dist 后路由 404」→ 无 react-router（单页 tab），不触发 SPA 刷新回退问题。M10 风险预置：D1（JAR 扫描）与 D47（Hikari 参数面）在部署阶段一并处置。 |

### M0–M9 全量复审修复（外审 35 条 + 补充 29–35 号，进入 M10 前收口）

- **P0 系（外审实锤，全部修复）**：P0-1 深嵌套 JSON → `JsonParser` MAX_DEPTH=512 + `DispatcherServlet` 改 catch Throwable（StackOverflowError 也得 500 而非连接层异常）；P0-2 D37 优先级反转纠正（见债务表 D37 条目）；P0-3 08-jdbc.md 的 UserRepository 虚报删除（M8 产出描述与代码对齐：无 Repository 层）；P0-4 转账 check-then-act 竞态 → 单条原子 `UPDATE ... WHERE balance >= ?`（行锁保证，影响行数≠1 即回滚）；P0-5 context 模块 main 源集 10 个 demo 类迁移 test 源集；P0-6 转账响应改「事务内读」——`transfer` 返回事务内快照余额（与提交一致），Controller 不再提交后二次读（前端 fromBalance/toBalance 契约不变）。
- **29–31（负数转账零校验，外审补充实测）**：`debit` 入口 `amount > 0` 校验——负数金额此前让 `WHERE balance >= ?` 恒真（余额可被打负、负余额账户单向困死），前端拦截形同虚设，API 层必须自校验。
- **32/34/35（DoS 面 + 版本 + 文档）**：32 `SunHttpRequest` 请求体 1 MiB 上限（`readNBytes` 多读 1 字节判超限，防单请求 OOM）；34 Banner 版本改读 jar 清单 Implementation-Version（boot pom 注入 `${project.version}`，永不漂移）；35 测试计数按本轮实测回写（见尾注）。
- **N 系修复（含约束用例，修复前行为均有复现路径）**：N1 重复「方法+路径」映射启动即抛 Ambiguous（单测锚定：单控制器双同路径方法必炸）；N2 类级 `@RequestMapping(path=…)` 别名生效（与方法级对称；单测用「path 形式与 value 形式拼出同一路径必炸 Ambiguous」锚定，别名失效则不炸、用例即失败）；N4 单例按创建逆序销毁（`singletonCreationOrder` 记录 + reverse；单测 bean 名刻意选桶序先于销毁序的组合，旧无序实现必先销毁依赖方）；N5 并发 getBean 同名单例加锁+双重检查（慢构造+CyclicBarrier 单测，修复前第二线程撞 currentlyInCreation 误报循环依赖）；N7 序列化 NaN/Infinity 显式报错（Java 对象路径 + JsonNode 溢出路径双单测，修复前静默产出自家解析器读不回的非法 JSON）；N18 `server.port` 非数字报可读错误（与同模块 `intProperty` 风格对称）。
- **N 系登记未修（D48–D60）**：见债务表；其中 N12（切点热路径性能）、N13（405）、N16（自动配置顺序依赖）在 M10 部署/压测前评估优先级。


### M10 · 3 实例 + Nginx 高可用 + 全链路终验

- **产出**：Nginx `upstream` + 健康检查、3 个无状态实例、健康检查接口、压测脚本、部署手册。
- **落地证据**：**浏览器经 Nginx 访问**，3 实例同时服务；kill 1 实例后浏览器访问不中断、数据仍在库；产出压测报告。

---

## 4. 通用基线（贯穿所有里程碑，但不作为验收）

| 维度 | 要求 |
| --- | --- |
| 可读性 | 直白写法优先，中文注释，命名稳定 |
| 接口契约 | 跨模块只依赖公开接口，不碰实现类 |
| 测试 | 核心类有单测、链路有集成测试（**仅基线，不作验收**） |
| 诚实标注 | 明确标注「未实现/简化」边界 |

---

## 5. 明确不在范围

- 生产级极致性能（NIO / Reactor / 异步 / 连接池调优）
- 安全（认证 / 授权 / CSRF / CORS 完整实现）
- 框架内核内置分布式协调（高可用由 demo 层 Nginx 承担）
- 与 Spring 的 API 二进制/源码级兼容
- 完整 SpEL / AspectJ 表达式 / YAML 规范 / Servlet 规范

---

## 6. 版本约定

- 每个里程碑**落地验收通过后**打 Git tag（如 `v0.m1`、`v0.m2`…），方便回溯每个阶段的完整可运行状态。
- 里程碑未经落地验收，绝不进入下一个。

---

## 7. 已知局限与技术债（代码审查登记）

> 下列各项经 M0–M4 代码审查显式登记，当前 demo 均不触发，仅作诚实标注；按「接上时机」逐项关闭。

| 编号 | 局限 / 技术债 | 触发条件 | 接上时机 / 做法 |
| --- | --- | --- | --- |
| D1 | 类路径扫描仅支持文件目录，不支持 JAR | 打成可执行 JAR 部署时 `jar:` 协议 URL 无法 `toURI()` 遍历 | M10 部署前补 `JarURLConnection` 分支，或改用 exploded classes |
| D2 | ~~`@Bean` 不支持 `initMethod/destroyMethod`~~ 已修于 M8：`@Bean(initMethod/…, destroyMethod/…)` 双属性落地（`AnnotatedBeanDefinitionReader` 读取 → BeanDefinition 已有字段），生命周期回调与「方法不存在时报错」负例均有单测；V10 实证 `HikariDataSource.close()`（destroyMethod）在 shutdown hook 里真实执行（MySQL 连接归零） | 需要方法级生命周期回调时 | 已关闭 |
| D3 | ~~`@Autowired` 声明支持构造器/方法/参数注入，实际只实现字段注入~~ 已修于 M7 终审（A-4）：构造器注入（BPP `determineCandidateConstructors` 选构造器、容器解析参数、多个 `@Autowired` 构造器报错）、方法注入（字段注入后调用，`required=false` 依赖缺失仅跳过该方法）、参数级 `@Autowired(required=false)`（缺省注入 null）全部落地；构造器/prototype 循环依赖改为可读错误而非 StackOverflow | 需要构造或方法注入时 | 已关闭 |
| D4 | ~~`@After` 注释写「正常返回后」，实现为 finally 语义~~ 已修于 M7（D42 + A 系终审）：注释订正、异常 suppressed 附加，拦截器更名为 `AfterAdviceInterceptor`（名称与 finally 语义一致） | 目标方法抛异常时后置仍会执行 | 已关闭 |
| D5 | 无通知排序（`@Order`） | 多切面命中同一方法时顺序不确定 | M6/M7 多切面场景补 `Ordered` 优先级 |
| D6 | `getBeanNamesForType` 不感知 JDK 代理，按具体类注入会 ClassCastException | 按具体类而非接口注入被代理 Bean | 保持「按接口注入」约定（JDK 代理固有限制，与 Spring 一致） |
| D7 | 单例创建无线程安全保护 | 运行时懒加载/动态注册单例时存在竞态 | 单例 refresh 预实例化 + 服务器其后启动即安全；未来懒加载再加锁 |
| D8 | AOP 仅 JDK 动态代理，无接口的类无法被织入 | 对具体类（如 M5 的 Controller）做 AOP 时 | M5/M6 前评估：引入 CGLIB 代理策略，或将被增强 Bean 接口化 |
| D9 | demo 包名跨模块重复：`com.minispring.demo` 同时出现在 core（IocDemo）与 context（demo 组件），Scanner 的 `getResource` 按 classpath 顺序命中首个目录，扫描结果依赖 classpath 顺序 | 同一包名跨模块、以文件目录扫描时 | 已修于 M5 前置（demo 包模块化为 `core.demo` / `context.demo`） |
| D10 | Profile 激活仅支持手动 `setActiveProfiles`，未从 `spring.profiles.active` / `SPRING_PROFILES_ACTIVE` 自动读取 | 部署阶段按环境变量切换 profile | M10 部署前接上 |
| D11 | YAML 解析为「教学子集」：无 anchor、多行字符串（`|`/`>`）、flow style（`{k:v}`/`[a,b]`）、list-of-map 嵌套；值内 `#` 被当作注释截断（无法转义） | 配置用到这些语法 | 按需扩展 `YamlPropertySourceLoader` |
| D12 | `@Value` 不支持 SpEL（`#{...}`）；类型转换仅 String + 基本/包装类型（无 List/Map/枚举） | 注入复杂类型或 SpEL 表达式 | M5 参数绑定时一并评估，或收窄标注 |
| D13 | 配置文件仅 classpath 根 `application.*`，不支持 `config/` 子目录、命令行参数覆盖 | 外部部署自定义配置位置 | M10 部署规范统一约定 |
| D14 | JSON 反序列化仅支持扁平 POJO 与 `List<String>`，不支持 `List<POJO>` / 嵌套集合 | POST 请求体含对象数组时 | 已修于 M7（B-6）：List 字段按泛型实参逐元素映射，`List<Integer>`/`List<Long>`/`List<POJO>` 均正确；raw/顶层 List 按节点自然类型 |
| D15 | `@PathVariable`/`@RequestParam` 依赖注解显式 `value()`，未启用 `-parameters`（参数名未编译保留），省略 value 会解析失败 | 按参数名隐式绑定时 | 需要时启用 `-parameters` 编译，或补参数名解析 |
| D16 | 错误处理简化：业务异常统一 500，无 `@ExceptionHandler`/`@ResponseStatus`（无法区分 404/400） | 需要按异常类型返回不同状态码时 | M8/M9 细化错误响应时补异常解析器 |
| D17 | `SunHttpServer` 仅 cached 线程池（每请求一线程），无 NIO/Reactor、无线程池上限控制 | 高并发压测时线程数无上限、吞吐受限 | M10 3 实例 + Nginx 分摊（教学项目可接受），必要时配固定线程池或换实现 |
| D18 | 静态资源 `"static" + path` 拼接未显式拒绝 `..` | 直觉上担心路径穿越，但 JDK HttpServer `URI.getPath()` 已规范化、`ClassLoader.getResourceAsStream` 不逃逸 classpath 根，当前不构成漏洞 | 已修于 M7：`StaticResourceHandler.handle` 入口显式拒绝 `..` → 403 |
| D19 | ~~`mini-spring-autoconfigure` 的 `main` 源集混入 demo 自动配置类~~ 已修于 M7；M7 终审（A-2）补齐同类残留：web（WebDemo/WebConfig/HelloController/UserController/User）、aop（AopConfig/AopDemo/LoggingAspect/OrderService/OrderServiceImpl）、core（IocDemo）的框架模块 demo 类全部清除，四个框架模块 `main` 源集零 demo | 任何模块依赖 `mini-spring-autoconfigure` 并开启自动配置时 | 已关闭 |
| D20 | `AutoConfigurationLoader.load` 不去重、不排序：候选顺序 = classpath 上 SPI 文件遍历序 + 文件内行序；重复类名靠 `registerComponent` 的 `containsBeanDefinition` 去重兜底，自动配置类之间无 `@Order/@AutoConfigureOrder` 排序保证 | 多 starter 重复列举同一自动配置；自动配置类 A 依赖 B 提供的 Bean（跨配置的 `@ConditionalOnBean`/`@ConditionalOnMissingBean`）而 B 恰在 A 之后加载时误判 | 已修于 M7：`AutoConfigurationImportSelector` 去重 + `@Order`/`@AutoConfigureOrder` 排序 |
| D21 | 条件注解仅 AND 语义，无 OR/NOT 组合与嵌套条件（Spring 的 `AllNestedConditions`/`AnyNestedCondition`） | 需要「A 或 B」这类复合条件时 | 按需扩展 `ConditionEvaluator` 支持嵌套条件，或新增 `@ConditionalOnAny` |
| D22 | `@Bean` 方法不支持参数注入 | `instantiateUsingFactoryMethod` 硬调 `getMethod(name)` 只找无参，带参 `@Bean` 抛 `NoSuchMethodException` | 已修于 M7：`@Bean` 工厂方法参数按类型/名字从容器解析 |
| D23 | `@Bean` 方法上的 `@Primary`/`@Qualifier` 不生效 | `resolveByPrimary` 读 `bd.getBeanClass()`（=返回类型），读不到方法上的注解 | 已修于 M7：`@Bean` 方法注解留存到 `BeanDefinition`；`@Primary` 完整生效，`@Qualifier` 残留「存而不用」半截 → 见 D34 |
| D24 | `@Controller("name")`/`@RestController("name")` 显式 beanName 无效 | `AnnotationBeanNameGenerator.explicitName` 未查 Controller/RestController | 已修于 M7：识别 `@Controller` 等元注解显式名 |
| D25 | `@ComponentScan` 扫到的 `@Configuration` 不处理其 `@Bean` | `processComponentScan` 只 `registerComponent` 不递归 | 已修于 M7：扫描器识别 `@Configuration` 后回调 `registerBeanMethods` |
| D26 | ~~prototype 循环依赖无防护（StackOverflow）~~ 已修于 M7 终审（A-4 连带）：`createBean` 加 `currentlyInCreation` 防护，prototype/构造器循环依赖抛可读错误「检测到无法提前暴露的循环依赖」 | prototype 相互注入 / 构造器互注时 | 已关闭（教学子集仍不支持解开，但错误可读） |
| D27 | 占位符循环引用防护不完整 | `doResolve` 的 `visiting.remove(key)` 在取 value 前移除，`${a}` 自引用 / `a↔b` 会 StackOverflow | 已修于 M7：try-finally 整段解析完成才移出 `visiting` |
| D28 | ~~`@PathVariable` 不做 URL 解码~~ **误报撤销**：`SunHttpRequest` 用 `exchange.getRequestURI().getPath()`，Java `URI.getPath()` 本身即解码百分号编码，中文路径参数拿到的是原文（`/users/%E5%BC%A0%E4%B8%89` → `张三`）。登记时未查证平台默认行为，违反本表第 8 条纪律，留此行为鉴 | 无 | 无需修复；M8 起「平台默认行为先查证再登记」 |
| D29 | ~~`SunHttpResponse.write()` 多次调用会失败~~ 已修于 M7（B-8）：首次 write 改用 chunked（length=0），流不自关、交给 `exchange.close()` 终结，支持任意多次 write；空 body 仍用 -1 | 响应需多次/流式写（如文件下载）时 | 已关闭 |
| D30 | AOP：收集切面期间创建的业务 Bean 不会被代理 | `getAdvisors` 用 `buildingAdvisors` 防递归、期间返回空列表且不重试，切面 Bean 有依赖时命中 | 已修于 M7：收集期缓存 deferredProxyTargets，完成后补代理并更新单例；已知残余见 D44 |
| D31 | Controller 被代理后 `HandlerMethod` 调用失败 | `HandlerMethod` 存原始类 `Method`，`method.invoke(代理实例)` 抛 IllegalArgumentException；因 D8 当前 Controller 无接口不会被 JDK 代理故暂不触发 | 与 D8 一起在 M7 评估（CGLIB / 接口化） |
| D32 | `@Configuration` 无 CGLIB 增强 | `@Bean` 方法互相调用直接 new、破坏单例语义 | 明确为「教学子集」边界（保持零依赖不引 CGLIB），M7 文档化 |
| D33 | autoconfigure / config 框架模块自带 application.yml | 与用户应用同名文件在 classpath 上冲突 | 已修于 M7：demo 归位 `mini-spring-demo`，框架模块移除自带 `application.*` |
| D34 | ~~`@Qualifier` 半截：`BeanDefinition.qualifier` 被 set 但 `getQualifier()` 从未被注入裁决读取，注入端 `@Qualifier(v)` 仍直接按 beanName `getBean`，不支持「限定名 ≠ beanName」的按名匹配~~ 已修于 M8：注入裁决完整链「限定名（BeanDefinition.qualifier）→ beanName → 类型唯一 → @Primary」落地，双数据源（限定名 ≠ beanName）用例锚定；连带修复 `ConditionEvaluator` 多 `@Conditional` 派生注解只求值第一个的 AND 语义破坏 | 需要按限定名（而非 beanName）解析多候选注入时 | 已关闭 |
| D35 | ~~`ApplicationListener` 在单例预实例化之后才收集注册（`refresh()` 第 3 步），Bean 初始化期间发布的事件丢失~~ 已修于 M7 终审（A-6）：监听器收集提前到「BPP 就位后、其余单例预实例化前」；同时补齐 `ApplicationEventPublisherAware`（初始化回调前注入发布器），初始化期事件端到端可达（单测覆盖） | Bean 初始化期间发布事件时 | 已关闭 |
| D36 | web 框架模块自带 `static/index.html`（M5 demo 遗留），与 demo 静态资源在 classpath 上冲突，`ClassLoader.getResourceAsStream` 按 classpath 顺序命中 web 模块那份 | 真实全链路（demo 依赖 web）访问 `/` 时 | 已修于 M7：删除 web 模块自带静态资源，静态资源归位 `mini-spring-demo` |
| D37 | ~~`ConfigFilePropertySourceLoader` properties/yml 优先级与 Spring 相反~~ **M0-M9 复审纠正（P0-2）**：M7 曾把优先级「修」成 yml 覆盖 properties，但 Spring Boot 官方（Externalized Configuration）明确「同位置 `.properties` 优先于 `.yml`」——M7 修复方向反了，恰违反本表第 8 条「平台行为先查证」纪律（与当年 D28 同源自我检讨）。已把默认层与 profile 层均改回「properties 优先于 yml」并反转注释与断言 | 同 key 同时出现在 properties 与 yml 时 | 已纠正于 M0-M9 复审（代码+断言+注释同步反转） |
| D38 | `SimpleAnnotationMetadata.findAnnotation` 循环元注解（A→@B、B→@A）无防护 → 无限递归 StackOverflow；同型递归另有两处：`AnnotationBeanNameGenerator.isComponentAnnotation`、`ClassPathScanningCandidateComponentProvider.hasComponentAnnotation`（初修只覆盖第一处，M7 终审补齐全部三处并全局确认无第四处） | 用户自定义互相标注的语义注解级联被查时 | 已修于 M7：三处递归均携带 visiting 集合 |
| D39 | `SimpleApplicationEventMulticaster.resolveEventType` 只反解本体实现的泛型，不认父类固化泛型（`class Foo extends BaseListener` 且 BaseListener implements ApplicationListener\<MyEvent\>）→ 退化成接收所有事件 | 监听器经父类声明监听类型时 | 已修于 M7：沿 getSuperclass 向上遍历接口泛型 |
| D40 | `ClassPathScanningCandidateComponentProvider.loadClass` `catch (Throwable) return null` 吞掉顶级类的真实加载失败（依赖缺失/静态初始化崩溃） | 扫描包内某个顶级类初始化失败时 | 已修于 M7：仅静默忽略内部类/匿名类（名字含 `$`），顶级类失败上抛 |
| D41 | `JsonParser.parseNumber` 对 `1.` / `1e` / `1e+` 等非法数字放行（延迟到 asDouble 才报错） | POST body 含非法数字字面量时 | 已修于 M7：严格校验整数/小数/指数部分都必须有数字 |
| D42 | `@After` 注释写「正常返回后」但实现是 finally 语义（D4）；且 finally 内 afterMethod 自身抛异常会覆盖目标异常 | @After 通知自身抛异常时 | 已修于 M7：注释订正为 finally 语义 + afterMethod 异常用 addSuppressed 附加到目标异常 |
| D43 | 注册与实例化的可见性不对称：注册侧（`registerBeanMethods`/`getDeclaredMethods`）接受非 public，实例化侧三处只认 public——① `instantiate` 构造器无 `setAccessible`（包私有配置类直接 IllegalAccessException）；② `findFactoryMethod` 用 `getMethods()`（包私有 @Bean 注册成功但解析必炸「工厂方法不存在」）；③ `invokeNoArgMethod` 用 `getMethod()`（非 public init/destroy 回调找不到） | 用户写包私有配置类 / 包私有 @Bean 方法 / 非 public 生命周期回调时（TempRepro 复现实证：注册成功、启动即炸） | 已修于 M7：三处补 setAccessible + getDeclaredMethods 优先；复现用例重跑通过（bean=ok） |
| D44 | A-5（D30 残余）：切面收集期内被提前创建的业务 Bean，D30 补偿会把代理回填容器缓存，但收集期已注入给其他 Bean 的引用仍是裸对象——容器缓存与早期引用不一致 | 切面 `@Autowired` 了被自己（或同期切面）切点命中的业务 Bean 时 | 已如实标注：补偿时打警告日志（与 Spring「not eligible for auto-proxying」同源语义）；规避方式：切面不依赖被自己切点命中的 Bean。若 M8 需要，可评估「按 BeanDefinition 元数据构建 Advisor（不实例化切面）」的深修 |
| D45 | ~~A-1 归位的已知边界：autoconfigure 聚合依赖 web/aop/config，「裁掉某能力模块、自动配置随之消失」的模块级隔离不成立~~ **已修于 M7 终审第三轮（方案 B，与 Spring 实证结构一致）**。事实核查：Spring Boot 3.2 的 spring-boot-autoconfigure 发布 pom 对下游仅传递 spring-boot 本体一个 compile 依赖，spring-web 等全部 optional（初版 D45 写「与 Spring 同构」不成立，Spring 恰恰相反）。修法：①autoconfigure 对 web/aop/config 全部 `<optional>`；②三个自动配置类级条件改 `@ConditionalOnClass(name="…")` 字符串形式——注解若含类字面量，jar 缺失时注解代理解析即抛 NoClassDefFoundError（HotSpot 平台行为，已实测），name 形式只碰字符串；③boot 不再依赖 web：新增 context 层 `Lifecycle` 接口，内嵌服务器由 `WebMvcAutoConfiguration.EmbeddedServerBootstrap`（Lifecycle 实现）装配，run() 只驱动接口（与 spring-boot 只认 Lifecycle 不认 Tomcat 同构）。**分离 classpath 实测（唯一事实）**：裁掉 aop+web → 启动正常、三类 Web/AOP Bean 不存在、无服务器、退出 0；只裁 aop → 服务器起（probe=404）、AOP 自动配置安全跳过。M8 的 DataSource/HikariCP 自动配置即按此模式（optional + name 探测），内核保持零第三方依赖 | 想做「裁剪某能力模块、自动配置随之消失」的精细化装配时 | 已关闭（模式成为 M8 前置） |
| D46 | M7 终审第三轮实测发现：`registerSingleton` 注册的运行期单例（如 webServer）可见性不对称——`getBean` 能取（前轮已修），但 `containsBean` 只查 BeanDefinition、`getBeanNamesForType` 只遍历 BeanDefinition，对运行期单例全部返回「不存在」（分离 classpath 实测 webServer=true 已注册但 containsBean=false 实证）。已修：containsBean 同查一级缓存；getBeanNamesForType 补查「无定义的手动单例」按实例类型匹配（与 Spring 的 ManualSingletonNames 语义对齐） | 业务按类型/存在性查询运行期注册的单例时 | 已修于 M7 终审第三轮（分离 classpath 复测通过） |
| D47 | HikariCP 仅 `minispring.datasource.max-pool-size` 可配，connection-timeout / minimum-idle / keepalive 等维持库默认（connectionTimeout=30s：DB 断连时请求有限阻塞 30 秒才返回 500，V7 实证——非挂死但体验欠佳） | 生产部署需要快速失败 / 池参数调优时 | M10 部署前评估：按需透传 `minispring.datasource.connection-timeout` 等参数；教学子集暂显式不做 |
| D48 | N3：`RequestMappingHandlerMapping.isController` 用 `isAnnotationPresent` 不递归元注解，而类扫描器递归——自定义「元标注 @RestController」的组合注解能注册 Bean 却永远没有路由 | 用户定义 `@MyRestController`（其上标注 @RestController）并只加在类上时 | 按需：isController 改为递归元注解查找（带 visiting 防环，与 D38 三处同构） |
| D49 | N6：`JsonObjectMapper.mapToPojo` Map 字段静默映射为空 HashMap（数据全丢无报错）；String 字段对 object/array 节点静默注入 null（与 asInt 显式报错不对称） | POST body 的 POJO 含 Map 字段 / String 字段收到结构化节点时 | 需要结构化字段绑定时：Map 分支按节点类型填充或显式报错，String 分支对非叶子节点报错 |
| D50 | N8：占位符 `visiting` 集合跨整个拼接串递归——`b=${a}x${a}`（同一占位符在值内出现两次）被误判循环引用 | 配置值内同一占位符出现多次时 | 按 Spring 语义改为逐占位符进出栈 |
| D51 | N9：`@Autowired(required=false)` + `@Qualifier` 组合下 `resolveByQualifier` 找不到直接抛异常而非注入 null，与方法注入「解析按非必需」注释自相矛盾 | 可选依赖带限定名且无候选时 | 对称修复：required=false 时限定名未命中走 null 注入 |
| D52 | N10：`registerComponent` 同 beanName 冲突静默跳过第二个类（`@Service("foo")`×2），Spring 抛 ConflictingBeanDefinitionException | 两个组件显式同名时 | 启动期抛冲突错误（与 N1 的 Ambiguous mapping 同纪律：配置错误启动即报） |
| D53 | N11：`processComponentScan` 被扫到的 `@Configuration` 不递归处理其 `@ComponentScan`/`@Import`（D25 只修了 @Bean 半截） | 嵌套配置类链路（配置类里再 @Import 另一配置类）时 | 教学子集显式不支持；或递归处理 @Import |
| D54 | N12：`AspectJExpressionPointcut.globMatch` 每次 matches 重新 StringBuilder+Pattern.compile（每次代理调用的热路径）；且 `com.x.service.*` 会跨包匹配 `com.x.service.sub.Foo`（Spring 中 `.` 不跨包） | 高频代理调用（压测）/ 深包子包类被意外命中 | M10 压测前：解析期缓存 Pattern；语义对齐按需 |
| D55 | N13：路径存在但方法不匹配返回 404 而非 405 + Allow 头（实测 DELETE /hello → 404） | 客户端用错 HTTP 方法时 | 需要 REST 语义完整性时：HandlerMapping 区分「无路径」与「方法不匹配」 |
| D56 | N14：`AspectJAdvisorFactory` 通知方法签名零校验——@Around 不带 ProceedingJoinPoint 参数要到第一次调用才炸（启动期静默） | 切面通知方法签名写错时 | 启动期校验通知方法参数签名 |
| D57 | N15：`YamlPropertySourceLoader` Tab 缩进 `countIndent` 返回 0 → 静默错误解析为顶级 key（YAML 规范应报错）；`stripQuotes` 对 `"a" "b"` 类值错误剥离 | 配置文件用 Tab 缩进 / 值含多个引号段时 | 需要时：Tab 缩进显式报错（与 N1「配置错误启动即报」同纪律） |
| D58 | N16：`JdbcAutoConfiguration` 的 `@ConditionalOnBean(DataSource.class)` 正确性依赖 imports 文件行序（两个自动配置类都没标 `@AutoConfigureOrder`）——重排 SPI 文件两行即静默失效，与 D20「排序保证」宣称不符 | 维护者重排 imports 文件行序时 | 给两配置类标 `@AutoConfigureOrder`（D20 排序器已支持） |
| D59 | N17：Windows 控制台 `mvn exec:java` 下全部中文日志 GBK 乱码——父 pom 无 `file.encoding`/surefire `argLine` | Windows 平台运行时 | 父 pom 统一 `-Dfile.encoding=UTF-8`（run 脚本与 exec 配置双处） |
| D60 | 33：REQUIRED 嵌套事务无 rollbackOnly 标记——内层事务 catch 吞掉异常时外层照常提交半途数据（Spring 有 rollbackOnly 全局回滚）；与「仅 REQUIRED」的教学边界声明并存，语义差距未标注 | 嵌套 @Transactional 且内层吞异常时 | 教学子集显式标注；或补 TransactionStatus.rollbackOnly |

> 注：B1（ITE 拆包）、B3（Object 方法过滤）为已发布 M3 代码的真实 bug，已单独修复并回归，不列入本表；B2（AOP×循环依赖）已在 M3「落地边界」登记。M6 后审查又修掉 B4（void/null 空响应断连）、B5（内嵌服务器未设线程池导致单线程串行）两个 M5 代码真实 bug，均已修复并回归。M7 审查再修掉 B6（`processComponentScan` 对未标 `@ComponentScan` 的 `@Configuration` 隐式扫描所在包）。M7「极端边界」复合审查又修掉 B7（`SunHttpRequest.decode` 非法百分号编码兜底）、B8（`JsonNode.asInt/asLong/asDouble` null 防护）。M7 终审第一轮（外审 B-1~B-8）修掉：B-1 `JsonSerializer` NUMBER 节点误加引号、B-2 元注解循环递归补齐另两处（见 D38）、B-3 prototype BPP 重复注册、B-4 监听器异常阻断广播链、B-6 List 元素一律 asString（D14 随之关闭）、B-7 `asBoolean` 无类型防护；B-8 即 D29 关闭；B-5 即 D28 误报撤销。
> M7 终审第二轮（外审 A-1~A-6，架构与契约层）修掉：A-1 三个 AutoConfiguration 归位 autoconfigure 模块、依赖方向恢复单向 `boot > autoconfigure > web > aop > context > core`（config 落回 core 之上，聚合边界见 D45）；A-2 框架模块 demo 清零（D19 补齐 web/aop/core）；A-3 `run()` 自动探测 DispatcherServlet 并启动内嵌服务器 + 关闭钩子（连带修复 JDK HttpServer dispatcher 线程继承 daemon 属性导致的保活不确定性）；A-4 `@Autowired` 构造器/方法/参数注入全落地（D3/D26 关闭）；A-5 补偿代理不一致如实标注为 D44；A-6 监听器先注册 + `ApplicationEventPublisherAware`（D35 关闭）；`AfterReturningAdviceInterceptor` 更名 `AfterAdviceInterceptor`。测试从 5 个增至 28 个（core/context/aop/autoconfigure/boot/web 六模块），含负例与边界用例。
> M7 终审第三轮（D45 方案 B）：optional 依赖 + `@ConditionalOnClass(name)` 字符串探测 + context 层 `Lifecycle`（boot 摘除 web 依赖，服务器启动归位 web 自动配置），模块级「裁剪即消失」经分离 classpath 真实启动实证（裁 aop+web / 只裁 aop 两场景）；实测顺带揪出并修复 D46（运行期单例可见性不对称）。至此 M0~M7 三轮外审 + 自审全部闭环，唯一残留 D44（已如实标注 + 警告日志）。
> M8 审查（V1~V10 唯一事实验收）修掉：B9（`JdkDynamicAopProxy` 不命中切点的直通路径无 ITE 拆包——M3 修 B1 时只覆盖拦截链路，直通路径为对称遗漏；M8 接口化 Service + 仅部分方法 @Transactional 使该路径成为常态，V7 断连实测「500 Internal Server Error: null」暴露后修复，加对称约束用例）、TransactionAspect 构造依赖死结（改 BeanFactoryAware 懒解析，启动序列实证切面先于 dataSource 创建）、`ConditionEvaluator` 多条件 AND 语义（`findAnnotations` 全收集）、`RequestMappingHandlerMapping` 派生注解元注解化（@PutMapping/@DeleteMapping 落地）。D2/D34 按 M8 计划关闭；新登记 D47（Hikari 参数面）。测试 28→44。
> M0–M9 全量复审（外审 35 条 + 补充 29–35 号）修掉：P0-1~P0-6、29-31（负数转账）、32（请求体上限）、34（Banner 版本联动）、N1/N2/N4/N5/N7/N18（详见 §3「M0–M9 全量复审修复」小节）；P0-2 即 D37 纠正、P0-3 即 M8 产出描述更正。另修 M9 复审遗留的 TransactionManager Error 路径隐式提交（committed 标记 + finally 统一回滚，含 transactionRollsBackOnError 用例）。未修各项登记 D48–D60。测试 45→52（core +2：逆序销毁/并发误报；web +2：Ambiguous/类级 path 别名；web json +3：NaN/Infinity 家族）。