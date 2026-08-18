package com.minispring.context.annotation;

/**
 * 导入选择器：在运行期动态决定「要导入哪些配置类」。
 *
 * <p>典型实现如自动配置的选择器——它不写死具体类，而是从 classpath 上的 SPI 文件里读出候选，
 * 再交给条件机制逐个裁决。与 Spring 的 {@code ImportSelector} 同源。
 */
public interface ImportSelector {

    /**
     * 返回要导入的配置类的全限定名列表。
     *
     * @param importingClassMetadata 标了 {@link Import} 的类（如应用入口）的标注快照
     */
    String[] selectImports(AnnotatedTypeMetadata importingClassMetadata);
}