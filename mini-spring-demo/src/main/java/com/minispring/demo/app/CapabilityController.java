package com.minispring.demo.app;

import com.minispring.context.annotation.Autowired;
import com.minispring.context.annotation.Qualifier;
import com.minispring.demo.autoconfig.GreetingService;
import com.minispring.demo.autoconfig.NamingService;
import com.minispring.starter.demo.FormatService;
import com.minispring.starter.demo.UpperCaseFormatService;
import com.minispring.web.mvc.annotation.GetMapping;
import com.minispring.web.mvc.annotation.RequestMapping;
import com.minispring.web.mvc.annotation.RequestParam;
import com.minispring.web.mvc.annotation.RestController;

import java.lang.reflect.Proxy;

/**
 * 全链路能力自检端点：把 AOP / 自动配置 / Starter 三条能力线串进同一条真实 HTTP 链路，
 * 用真实请求端到端触发并返回可核对的落地证据，而不是各自单点 demo 打印日志。
 *
 * <p>覆盖三件事：
 * <ul>
 *   <li>AOP：{@code OrderService} 由 JDK 代理织入切面，调用前后计数器变化 + {@code Proxy.isProxyClass} 双重证明；</li>
 *   <li>自动配置：{@code GreetingService}/{@code NamingService} 由 SPI 兜底装配，{@code presentFeature}/{@code optionalFeature} 由条件装配命中；</li>
 *   <li>Starter：{@code FormatService} 由 starter 的 SPI 自动装配，无需显式注册。</li>
 * </ul>
 */
@RestController
@RequestMapping("/capability")
public class CapabilityController {

    @Autowired
    private OrderService orderService;        // AOP：注入的是 JDK 代理

    @Autowired
    private LoggingAspect loggingAspect;      // AOP：切面计数器，证明织入

    @Autowired
    private GreetingService greetingService;  // 自动配置兜底默认实现

    @Autowired
    private NamingService namingService;      // 自动配置兜底默认实现

    @Autowired
    private FormatService formatService;      // Starter SPI 自动装配

    @Autowired
    @Qualifier("presentFeature")
    private String presentFeature;            // @ConditionalOnClass 命中

    @Autowired
    @Qualifier("optionalFeature")
    private String optionalFeature;           // @ConditionalOnProperty 命中

    /** AOP 落地：真实下单，触发 @Before/@After/@Around；用调用前后计数器证明切面确实织入。 */
    @GetMapping("/aop/order")
    public String placeOrder(@RequestParam(value = "product", defaultValue = "手机") String product) {
        int before = loggingAspect.beforeCount();
        int after = loggingAspect.afterCount();
        int around = loggingAspect.aroundCount();

        String result = orderService.placeOrder(product);

        boolean proxied = Proxy.isProxyClass(orderService.getClass());
        return String.format(
                "result=%s | proxied=%s | before %d->%d | after %d->%d | around %d->%d | costNanos=%d",
                result, proxied,
                before, loggingAspect.beforeCount(),
                after, loggingAspect.afterCount(),
                around, loggingAspect.aroundCount(),
                loggingAspect.aroundCostNanos());
    }

    /** AOP 异常透传：业务异常应原样抛到 HTTP 层（500 + 原始消息），不被代理包装成 InvocationTargetException。 */
    @GetMapping("/aop/fail")
    public String failOrder() {
        orderService.failOrder();
        return "unreachable";
    }

    /** 自动配置落地：默认兜底实现 + 条件装配结果，同一响应里可核对。 */
    @GetMapping("/autoconfig")
    public String autoconfig() {
        return String.format(
                "greeting=[%s](%s) | naming=[%s](%s) | present=[%s] | optional=[%s]",
                greetingService.greet(), greetingService.getClass().getSimpleName(),
                namingService.name(), namingService.getClass().getSimpleName(),
                presentFeature, optionalFeature);
    }

    /** Starter 落地：SPI 自动装配的 FormatService 真实生效。 */
    @GetMapping("/starter/format")
    public String format(@RequestParam(value = "text", defaultValue = "minispring") String text) {
        if (!(formatService instanceof UpperCaseFormatService)) {
            throw new IllegalStateException("formatService 未按 starter SPI 装配");
        }
        return "format(\"" + text + "\") -> " + formatService.format(text);
    }
}