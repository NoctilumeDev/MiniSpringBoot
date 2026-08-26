package com.minispring.autoconfigure.jdbc;

import com.minispring.autoconfigure.condition.ConditionalOnClass;
import com.minispring.autoconfigure.condition.ConditionalOnMissingBean;
import com.minispring.autoconfigure.condition.ConditionalOnProperty;
import com.minispring.context.annotation.Bean;
import com.minispring.context.annotation.Configuration;
import com.minispring.core.EnvironmentAware;
import com.minispring.core.env.Environment;
import com.minispring.jdbc.transaction.ManagedDataSource;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import javax.sql.DataSource;
/**
 * 数据源自动配置：classpath 上有 HikariCP 且配置了 {@code minispring.datasource.url} 时，
 * 自动装配连接池数据源。
 *
 * <p>autoconfigure 对 HikariCP 使用 optional 依赖：
 * 类级条件必须用 {@code name} 字符串（注解里出现 {@code HikariDataSource.class} 类字面量的话，
 * HikariCP 缺失时注解代理解析即抛 NoClassDefFoundError）；方法体内 {@code new HikariDataSource()}
 * 只在条件命中后执行，安全。没配 url 的纯内存应用整个配置类跳过。
 *
 * <p>{@code destroyMethod = "close"}（D2 能力，V10 验收点）：容器关闭时释放池，
 * 否则 Hikari 的 housekeeping 线程泄漏、JVM 退不干净。
 *
 * <p>配置约定（决策点 A）：
 * <ul>
 *   <li>{@code minispring.datasource.url} —— JDBC 连接串（必填，无则不装配）</li>
 *   <li>{@code minispring.datasource.username} / {@code minispring.datasource.password}</li>
 *   <li>{@code minispring.datasource.driver-class-name}（可选，驱动可从 url 推断）</li>
 *   <li>{@code minispring.datasource.max-pool-size}（缺省 10，硬上限 256）</li>
 * </ul>
 */
@Configuration
@ConditionalOnClass(name = "com.zaxxer.hikari.HikariDataSource")
@ConditionalOnProperty(name = "minispring.datasource.url")
public class DataSourceAutoConfiguration implements EnvironmentAware {

    static final String PREFIX = "minispring.datasource";
    static final int DEFAULT_MAX_POOL_SIZE = 10;
    static final int MAX_POOL_SIZE = 256;

    private Environment environment;

    @Override
    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }

    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean
    public DataSource dataSource() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(environment.getProperty(PREFIX + ".url"));
        config.setUsername(environment.getProperty(PREFIX + ".username"));
        config.setPassword(environment.getProperty(PREFIX + ".password"));
        String driver = environment.getProperty(PREFIX + ".driver-class-name");
        if (driver != null && !driver.isEmpty()) {
            config.setDriverClassName(driver);
        }
        config.setMaximumPoolSize(boundedIntProperty(
                ".max-pool-size", DEFAULT_MAX_POOL_SIZE, 1, MAX_POOL_SIZE));
        config.setPoolName("minispring-hikari");
        HikariDataSource pool = new HikariDataSource(config);
        return new ManagedDataSource(pool, pool::evictConnection);
    }

    /** 有界整数配置；配置值不能绕过资源预算重新打开无界连接增长风险。 */
    private int boundedIntProperty(String suffix, int defaultValue, int minimum, int maximum) {
        String value = environment.getProperty(PREFIX + suffix);
        if (value == null || value.isEmpty()) {
            return defaultValue;
        }
        try {
            int parsed = Integer.parseInt(value.trim());
            if (parsed < minimum || parsed > maximum) {
                throw new IllegalStateException("配置 " + PREFIX + suffix + " 必须在 "
                        + minimum + ".." + maximum + " 范围内: " + parsed);
            }
            return parsed;
        } catch (NumberFormatException e) {
            throw new IllegalStateException("配置 " + PREFIX + suffix + "=\"" + value + "\" 不是合法整数", e);
        }
    }
}
