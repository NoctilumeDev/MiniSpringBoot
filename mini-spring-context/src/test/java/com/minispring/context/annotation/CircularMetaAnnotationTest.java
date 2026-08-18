package com.minispring.context.annotation;

import org.junit.jupiter.api.Test;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/**
 * B-2 回归：@AnnA→@AnnB→@AnnA 互标的循环元注解不得引发 StackOverflow。
 * 修复前：三处元注解递归（scanner / beanName 生成器 / SimpleAnnotationMetadata）只修了一处，另两处仍会爆栈。
 */
class CircularMetaAnnotationTest {

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE)
    @AnnB
    @interface AnnA {
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE)
    @AnnA
    @interface AnnB {
    }

    @AnnA
    static class MarkedClass {
    }

    @Test
    void scannerDoesNotStackOverflowOnCircularMetaAnnotations() {
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider();
        assertDoesNotThrow(() -> scanner.isComponent(MarkedClass.class));
        // @AnnA/@AnnB 都不派生自 @Component：不是组件
        assertFalse(scanner.isComponent(MarkedClass.class));
    }

    @Test
    void beanNameGeneratorDoesNotStackOverflowOnCircularMetaAnnotations() {
        AnnotationBeanNameGenerator generator = new AnnotationBeanNameGenerator();
        assertDoesNotThrow(() -> generator.generateBeanName(MarkedClass.class));
        // 退化为「类名首字母小写」而非爆栈
        assertFalse(generator.generateBeanName(MarkedClass.class).isEmpty());
    }
}
