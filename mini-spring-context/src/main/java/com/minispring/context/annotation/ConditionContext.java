package com.minispring.context.annotation;

import com.minispring.core.BeanDefinitionRegistry;
import com.minispring.core.ListableBeanFactory;
import com.minispring.core.env.Environment;

/**
 * 条件求值时的运行期上下文：把「登记 / 查找 / 查配置 / 类加载」四件事交给 {@link Condition} 实现。
 */
public interface ConditionContext {

    /** 注册中心：判断某个 beanName 是否已登记（{@code @ConditionalOnMissingBean} 靠它）。 */
    BeanDefinitionRegistry getRegistry();

    /** 可列举的 Bean 工厂：按类型反查 Bean 名（{@code @ConditionalOnMissingBean} 靠它）。 */
    ListableBeanFactory getBeanFactory();

    /** 配置环境：读取 property（{@code @ConditionalOnProperty} 靠它）。 */
    Environment getEnvironment();

    /** 当前类加载器：做「类是否在 classpath 上」的存在性判断（{@code @ConditionalOnClass} 靠它）。 */
    ClassLoader getClassLoader();
}