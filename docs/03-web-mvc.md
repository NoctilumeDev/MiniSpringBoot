# 03 · Web 与 MVC 设计

> 对应模块：`mini-spring-web`
> 回答的问题：一个 HTTP 请求，是怎么从端口进来，精准命中你的 Controller 方法的？

---

## 1. 总体目标

不用 Servlet、不用 Tomcat，用 JDK 内置能力重新搭起 Web 栈。完整回答「内嵌服务器」到底替我们做了什么。

组件全景：

```
WebServer(SPI 接口)
  └── SunHttpServer(基于 com.sun.net.httpserver.HttpServer)
          │
DispatcherServlet（前端控制器，请求统一入口）
          │
HandlerMapping（URL + 方法 → 处理器）
          │
HandlerAdapter（解析入参 → 调用 → 处理返回值）
          │
ArgumentResolver / ReturnValueHandler（一组可扩展的小零件）
```

---

## 2. 内嵌服务器：WebServer 抽象

把「服务器」抽象成接口，是为了让「协议细节」和「业务处理」解耦：

```java
interface WebServer {
    void start(int port);
    void stop();
}
```

首版实现 `SunHttpServer`，基于 JDK 自带的 `com.sun.net.httpserver.HttpServer`。它虽然「藏在 JDK 里」，但足够我们演示 HTTP 服务端；同时把 `HttpServletRequest`/`HttpServletResponse` 抽象成自己的轻量接口，避免直接耦合到 `HttpExchange`。

**设计要点**：哪怕只写一个实现，也要抽出 `WebServer` 接口——这让「换掉底层服务器」成为可能，也让学生看清「Spring Boot 所谓内嵌容器，本质就是一个可替换的 SPI」。

### 2.1 关于「HTTP 协议」的诚实说明

`SunHttpServer` 已经帮我们完成了 TCP 连接、HTTP 报文解析，所以我们不必手写 socket 逐字节解析。**作为教学补充，文档会把 HTTP 报文（请求行 / 头 / 空行 / 体）的结构、以及与自研 socket 服务器的差异讲清楚**，但不把「手写 socket 服务器」作为首版硬性要求——那是「另一个轮子」，值得单独做，不值得现在阻塞主线。

---

## 3. 前端控制器：DispatcherServlet

Spring MVC 的灵魂是「**所有请求都经过一个前端控制器**」。这个前端控制器只做一件事：**分发（Dispatch）**，把请求「派」给真正处理它的处理器。

```
doDispatch(request, response):
    1. 根据 request 找到合适的 Handler（HandlerMapping）
    2. 找到能处理该 Handler 的 Adapter（HandlerAdapter）
    3. Adapter 解析入参 → 调用 Handler → 拿到返回值
    4. 用 ReturnValueHandler 把返回值写成响应
    5. 途中任何异常 → 交给异常处理器（首版先记录日志并返回 500）
```

前端控制器的价值：**让「找处理器」「调方法」「写响应」这三件事彼此独立**，任何一环都能单独扩展，而不影响其它环。

---

## 4. HandlerMapping：路由是怎么命中的

首版支持两种映射来源：

1. **`@RequestMapping` / `@GetMapping` / `@PostMapping`**：注解驱动，最常用。
2. 预注册的精确路由（编程式）。

匹配逻辑（简化版）：

```
请求 URL  →  method(RequestMethod, path) 的映射表
         →  精确匹配 path + HTTP 方法
         →  否则尝试路径参数模板（如 /users/{id}）
         →  命中则返回 HandlerMethod（Controller 实例 + 方法）
```

**关键点**：`HandlerMethod` 是一个「价值对象」，它把「哪个 Controller 的哪个方法」封装成一个统一的可调用单元。这是 MVC 里承上启下的核心结构：

```java
class HandlerMethod {
    Object bean;      // Controller 实例
    Method method;    // 具体方法
    MethodParameter[] parameters; // 方法入参元数据
}
```

---

## 5. HandlerAdapter 与参数绑定

`HandlerAdapter` 负责真正「调用」`HandlerMethod`，其工作量集中在**参数解析**：

| 参数场景 | 负责的解析器 |
| --- | --- |
| `@PathVariable("id") Long id` | 从路径模板取值并做类型转换 |
| `@RequestParam("name") String name` | 从 query 参数取值 |
| `@RequestBody Order order` | 读 body 字节 → 反序列化成对象 |
| `HttpServletRequest` / `HttpServletResponse` | 直接注入框架对象 |
| 无注解的参数 | 按参数名尝试从 query/body 匹配 |

每个「参数场景」对应一个 `ArgumentResolver`（参数解析器），用「策略模式」串起来：遍历解析器，谁声明「我能解析这个参数」就交给谁。这让 MVC 的参数处理**天然可扩展**——想支持 `@RequestHeader`，加一个解析器即可，不用改动主流程。

---

## 6. 返回值处理与 JSON

| 返回值场景 | 处理方式 |
| --- | --- |
| `@ResponseBody` + 对象 | 对象 → JSON → 写入 body（`Content-Type: application/json`） |
| 返回 `String`（无 @ResponseBody） | 视为「视图名 / 重定向」，首版做简化处理 |
| 返回 `void` / `null` | 空 body + 对应状态码 |

### 6.1 自写 JSON 解析器

不引 Jackson。自写一个极简 JSON 栈，分两层：

- **`JsonParser`**（反序列化）：把 JSON 字符串 → `JsonNode`（Object/Array/String/Number/Boolean/null 的树结构）。
- **`JsonSerializer`**（序列化）：把 Java 对象（反射遍历字段）→ JSON 字符串。

定位清晰：**只为满足 MVC 的入参/出参，不追求 JSON 规范的 100% 覆盖**（不支持对重复键、极大数值、Unicode 转义等边角的完整实现，文档如实标注）。

---

## 7. 类型转换

参数从字符串（query/path 本质都是字符串）到强类型（`Long` / `Integer` / `Boolean` / 自定义类型），需要一套**类型转换器**：

```
"9527" --[StringToLongConverter]--> 9527L
```

首版实现常用基本类型的转换，并留出 `Converter<S, T>` 接口供扩展。这与 Spring 的 `ConversionService` 同构，是最能体现「麻雀虽小五脏俱全」的环节之一。

---

## 8. 完整请求时序（含 AOP）

```
HTTP "GET /users/42?verbose=true"
   │
   ▼
SunHttpServer 接收 → 封装成 MiniHttpServletRequest
   │
   ▼
DispatcherServlet.doDispatch()
   │
   ▼
HandlerMapping 匹配 → HandlerMethod(UserController#getUser)
   │
   ▼
HandlerAdapter：
   ├─ PathVariableResolver 解析 {id} → 42L
   ├─ RequestParamResolver 解析 verbose → true
   ├─ @RequestBody 反序列化（本例无）
   ▼
UserController#getUser(...) 执行（若被 @Aspect 增强，此处经 AOP 代理）
   │
   ▼
返回值 User 对象 → ReturnValueHandler → JsonSerializer → JSON
   │
   ▼
写回 HTTP 200 + application/json
```

---

## 9. 验收要点（M5 里程碑）

- demo 启动后，`GET /hello` 能返回字符串 ✅
- `GET /users/{id}` 路径参数绑定正确 ✅
- `POST /users` + `@RequestBody` 能反序列化 JSON 对象 ✅
- `@ResponseBody` 返回对象自动序列化为 JSON ✅
- 一个带 `@Around` 计时切面的接口，耗时被正确打印 ✅
- 单测覆盖路由命中、参数绑定、JSON 序列化/反序列化 ✅