package com.minispring.core;

/**
 * 可列举的 Bean 工厂：在 {@link BeanFactory} 之上，增加「按类型查找」能力。
 *
 * <p>依赖按类型注入（{@code @Autowired}）就靠它：给定类型，反查出所有匹配的 Bean 名。
 */
public interface ListableBeanFactory extends BeanFactory {

    /**
     * 返回所有「类型匹配」的 Bean 名。
     * 匹配规则：Bean 的最终类型能被 {@code type} 赋值（即 bean instanceof type）。
     */
    String[] getBeanNamesForType(Class<?> type);
}