package com.minispring.core.support;

import com.minispring.core.BeanDefinitionRegistry;
import com.minispring.core.BeansException;

/**
 * 多候选依赖的共享裁决规则。
 *
 * <p>构造器、工厂方法、字段和方法注入必须看到同一份 {@code @Primary} 语义：
 * 没有主候选时返回 {@code null}，恰有一个时返回其 beanName，超过一个则拒绝歧义配置。
 * 运行期手动注册的单例没有 BeanDefinition，因此不参与 Primary 裁决。
 */
public final class DependencyCandidateResolver {

    private DependencyCandidateResolver() {
    }

    public static String determinePrimaryCandidate(String[] candidates,
                                                   BeanDefinitionRegistry registry,
                                                   String description) {
        String primary = null;
        for (String name : candidates) {
            if (!registry.containsBeanDefinition(name)
                    || !registry.getBeanDefinition(name).isPrimary()) {
                continue;
            }
            if (primary != null) {
                throw new BeansException(description + "存在多个 @Primary 候选（"
                        + primary + ", " + name + "）");
            }
            primary = name;
        }
        return primary;
    }
}
