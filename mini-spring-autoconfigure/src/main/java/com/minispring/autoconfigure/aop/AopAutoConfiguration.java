package com.minispring.autoconfigure.aop;

import com.minispring.aop.framework.autoproxy.AspectJAutoProxyCreator;
import com.minispring.autoconfigure.condition.ConditionalOnClass;
import com.minispring.autoconfigure.condition.ConditionalOnMissingBean;
import com.minispring.context.annotation.Bean;
import com.minispring.context.annotation.Configuration;

/**
 * AOP 自动配置：classpath 上有 {@code mini-spring-aop} 时，自动把「自动代理创建器」装配进容器。
 *
 * <p>A-1 归位：自动配置类统一放在 autoconfigure 模块（与 spring-boot-autoconfigure 同构），
 * 框架模块（aop/config/web）不再反向依赖 autoconfigure，依赖方向恢复单向：
 * boot &gt; autoconfigure &gt; web &gt; aop &gt; context &gt; core。
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
