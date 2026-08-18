package com.minispring.aop.demo;

import com.minispring.aop.framework.autoproxy.AspectJAutoProxyCreator;
import com.minispring.context.annotation.Bean;
import com.minispring.context.annotation.ComponentScan;
import com.minispring.context.annotation.Configuration;

/** AOP 入口配置：扫描本包组件，并显式把「自动代理创建器」作为基础设施 Bean 注册进容器。 */
@Configuration
@ComponentScan
public class AopConfig {

    @Bean
    public AspectJAutoProxyCreator aspectJAutoProxyCreator() {
        return new AspectJAutoProxyCreator();
    }
}