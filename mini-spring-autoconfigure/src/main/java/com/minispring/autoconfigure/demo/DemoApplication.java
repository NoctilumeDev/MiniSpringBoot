package com.minispring.autoconfigure.demo;

import com.minispring.autoconfigure.EnableAutoConfiguration;
import com.minispring.context.annotation.Bean;
import com.minispring.context.annotation.Configuration;

/**
 * M6 演示入口：开启自动配置，并「用户自定」一个 {@link NamingService} 以覆盖自动配置默认实现。
 *
 * <p>注意：不写 {@code @ComponentScan}——演示的是「SPI 发现自动配置」而非「扫描发现」，
 * 避免把同包的 AutoConfiguration 类也当作普通组件扫进来造成重复注册。
 */
@Configuration
@EnableAutoConfiguration
public class DemoApplication {

    @Bean
    public NamingService namingService() {
        return new UserNamingService();
    }
}