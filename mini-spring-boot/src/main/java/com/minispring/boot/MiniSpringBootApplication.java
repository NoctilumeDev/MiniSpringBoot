package com.minispring.boot;

import com.minispring.autoconfigure.EnableAutoConfiguration;
import com.minispring.context.annotation.ComponentScan;
import com.minispring.context.annotation.Configuration;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 应用入口的复合注解：{@link Configuration} + {@link ComponentScan} + {@link EnableAutoConfiguration} 三合一。
 *
 * <p>标注了它，一条 {@link MiniSpringApplication#run(Class, String...)} 就能把
 * 「组件扫描 + 用户配置 + 自动装配」全部串起来，等价于 Spring Boot 的 {@code @SpringBootApplication}。
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Configuration
@ComponentScan
@EnableAutoConfiguration
public @interface MiniSpringBootApplication {
}