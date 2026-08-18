package com.minispring.web.demo;

import com.minispring.web.mvc.annotation.GetMapping;
import com.minispring.web.mvc.annotation.RequestParam;
import com.minispring.web.mvc.annotation.RestController;

/**
 * 最简单的示例：GET /hello 返回字符串，演示 @RequestParam 绑定与默认值。
 */
@RestController
public class HelloController {

    @GetMapping("/hello")
    public String hello(
            @RequestParam(value = "name", required = false, defaultValue = "MiniSpringBoot") String name) {
        return "Hello, " + name + "!";
    }

    /** 返回 void：验证空响应也会正确提交 200，不触发 Empty reply 断连（BUG-1 回归）。 */
    @GetMapping("/void")
    public void doNothing() {
    }

    /** 睡一小会再返回：验证内嵌服务器确实并发（每请求一线程），而非单线程串行（BUG-2 回归）。 */
    @GetMapping("/sleep")
    public String sleep(@RequestParam(value = "ms", required = false, defaultValue = "2000") long ms)
            throws InterruptedException {
        Thread.sleep(ms);
        return "slept " + ms + "ms";
    }
}