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
}