package com.minispring.autoconfigure.aop;

import com.minispring.aop.framework.autoproxy.AspectJAutoProxyCreator;
import com.minispring.autoconfigure.condition.ConditionalOnClass;
import com.minispring.autoconfigure.condition.ConditionalOnMissingBean;
import com.minispring.context.annotation.Bean;
import com.minispring.context.annotation.Configuration;

/**
 * AOP 自动配置：classpath 上有 {@code mini-spring-aop} 时，自动把「自动代理创建器」装配进容器。
 *
 * <p>A-1 归位 + D45 收口：类级条件必须用 {@code name} 字符串形式——注解里若出现
 * {@code AspectJAutoProxyCreator.class} 类字面量，aop jar 缺失时注解代理解析即抛
 * {@code NoClassDefFoundError}（反射读取注解成员会触发类加载），条件求值根本走不到。
 * name 形式只碰字符串，缺失时 {@code Class.forName(name, false, cl)} 安全返回 false → 整个配置类跳过。
 *
 * <p>方法返回类型引用框架类是安全的：类级条件 skip 发生在 {@code registerBeanMethods}
 * 枚举方法之前，方法签名（惰性解析）不会被触碰。
 *
 * <p>{@link ConditionalOnMissingBean} 兜底：用户已自定义时回退，避免重复注册基础设施。
 */
@Configuration
@ConditionalOnClass(name = "com.minispring.aop.framework.autoproxy.AspectJAutoProxyCreator")
public class AopAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public AspectJAutoProxyCreator aspectJAutoProxyCreator() {
        return new AspectJAutoProxyCreator();
    }
}
