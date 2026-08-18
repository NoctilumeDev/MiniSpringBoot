package com.minispring.autoconfigure.jdbc;

import com.minispring.autoconfigure.condition.ConditionalOnClass;
import com.minispring.autoconfigure.condition.ConditionalOnMissingBean;
import com.minispring.autoconfigure.condition.ConditionalOnProperty;
import com.minispring.context.annotation.Bean;
import com.minispring.context.annotation.Configuration;
import com.minispring.core.EnvironmentAware;
import com.minispring.core.env.Environment;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

/**
 * 数据源自动配置：classpath 上有 HikariCP 且配置了 {@code minispring.datasource.url} 时，
 * 自动装配连接池数据源。
 *
 * <p>D45 模式（M8 首个第三方能力落点）：autoconfigure 对 HikariCP 是 optional 依赖——
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
 *   <li>{@code minispring.datasource.max-pool-size}（缺省 10）</li>
 * </ul>
 */
@Configuration
@ConditionalOnClass(name = "com.zaxxer.hikari.HikariDataSource")
@ConditionalOnProperty(name = "minispring.datasource.url")
public class DataSourceAutoConfiguration implements EnvironmentAware {

    static final String PREFIX = "minispring.datasource";

    private Environment environment;

    @Override
    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }

    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean
    public HikariDataSource dataSource() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(environment.getProperty(PREFIX + ".url"));
        config.setUsername(environment.getProperty(PREFIX + ".username"));
        config.setPassword(environment.getProperty(PREFIX + ".password"));
        String driver = environment.getProperty(PREFIX + ".driver-class-name");
        if (driver != null && !driver.isEmpty()) {
            config.setDriverClassName(driver);
        }
        config.setMaximumPoolSize(intProperty(".max-pool-size", 10));
        config.setPoolName("minispring-hikari");
        return new HikariDataSource(config);
    }

    /** 整数配置读取：缺省用默认值；配了但非数字时抛带属性名的可读错误（审查修复 M9 I5——裸 NumberFormatException 无从定位是哪个属性配错）。 */
    private int intProperty(String suffix, int defaultValue) {
        String value = environment.getProperty(PREFIX + suffix);
        if (value == null || value.isEmpty()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            throw new IllegalStateException("配置 " + PREFIX + suffix + "=\"" + value + "\" 不是合法整数", e);
        }
    }
}
