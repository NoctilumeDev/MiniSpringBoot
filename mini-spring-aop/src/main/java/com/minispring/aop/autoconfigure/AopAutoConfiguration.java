package com.minispring.aop.autoconfigure;

import com.minispring.aop.framework.autoproxy.AspectJAutoProxyCreator;
import com.minispring.autoconfigure.condition.ConditionalOnClass;
import com.minispring.autoconfigure.condition.ConditionalOnMissingBean;
import com.minispring.context.annotation.Bean;
import com.minispring.context.annotation.Configuration;

/**
 * AOP 自动配置：注入依赖了 {@code mini-spring-aop} 时，自动把「自动代理创建器」装配进容器。
 *
 * <p>{@link ConditionalOnMissingBean} 兜底：用户已自定义时回退，避免重复注册基础设施。
 */
@Configuration
@ConditionalOnClass(AspectJAutoProxyCreator.class)
public class AopAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public AspectJAutoProxyCreator aspectJAutoProxyCreator() {
        return new AspectJAutoProxyCreator();
    }
}