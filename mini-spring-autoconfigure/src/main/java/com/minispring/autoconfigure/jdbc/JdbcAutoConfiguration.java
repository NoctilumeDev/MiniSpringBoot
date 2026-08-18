package com.minispring.autoconfigure.jdbc;

import com.minispring.autoconfigure.condition.ConditionalOnBean;
import com.minispring.autoconfigure.condition.ConditionalOnClass;
import com.minispring.autoconfigure.condition.ConditionalOnMissingBean;
import com.minispring.context.annotation.Bean;
import com.minispring.context.annotation.Configuration;
import com.minispring.jdbc.JdbcTemplate;
import com.minispring.jdbc.transaction.TransactionAspect;
import com.minispring.jdbc.transaction.TransactionManager;

import javax.sql.DataSource;

/**
 * JDBC 自动配置：classpath 上有 {@code mini-spring-jdbc} 且容器里已有 {@link DataSource}
 * （来自 {@link DataSourceAutoConfiguration} 或用户自定义）时，装配模板 + 事务设施。
 *
 * <p>类级条件用 {@code name}（D45 纪律）；{@link ConditionalOnBean} 的 {@code DataSource}
 * 是 JDK 自带类（{@code java.sql} 模块），类字面量永远可解析，安全。
 *
 * <p>{@link TransactionAspect} 经 @Bean 显式注册（jdbc 模块不在应用的组件扫描包内）；
 * 切面类上标 @Aspect，AOP 代理创建器按 BeanDefinition 的 beanClass 识别并收集其 advisor。
 */
@Configuration
@ConditionalOnClass(name = "com.minispring.jdbc.JdbcTemplate")
@ConditionalOnBean(DataSource.class)
public class JdbcAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public JdbcTemplate jdbcTemplate(DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }

    @Bean
    @ConditionalOnMissingBean
    public TransactionManager transactionManager(DataSource dataSource) {
        return new TransactionManager(dataSource);
    }

    /**
     * 切面无构造依赖（M8 修复依赖链死结）：经 {@link com.minispring.core.BeanFactoryAware}
     * 拿工厂、首次拦截时懒解析 TransactionManager——否则「切面 → txManager → dataSource」与
     * 「dataSource 初始化触发 advisor 收集 → 创建切面」互为死结，纯自动配置应用必炸。
     */
    @Bean
    @ConditionalOnMissingBean
    public TransactionAspect transactionAspect() {
        return new TransactionAspect();
    }
}
