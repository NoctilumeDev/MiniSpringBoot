package com.minispring.starter.demo;

import com.minispring.autoconfigure.EnableAutoConfiguration;
import com.minispring.context.annotation.Configuration;

/**
 * starter 演示应用入口：只开自动配置、不显式注册任何 {@link FormatService}。
 */
@Configuration
@EnableAutoConfiguration
public class StarterApplication {
}