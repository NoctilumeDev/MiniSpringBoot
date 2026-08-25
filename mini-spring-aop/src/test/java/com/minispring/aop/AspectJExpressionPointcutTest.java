package com.minispring.aop;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AspectJExpressionPointcutTest {

    static class AnnotatedService {
        @Deprecated
        public void execute() {
        }
    }

    @Test
    void annotationExpressionAcceptsRealAnnotationAndStillMatches() throws Exception {
        AspectJExpressionPointcut pointcut =
                new AspectJExpressionPointcut("@annotation(java.lang.Deprecated)");

        assertTrue(pointcut.matches(AnnotatedService.class.getMethod("execute"), AnnotatedService.class));
    }

    @Test
    void annotationExpressionRejectsNonAnnotationTypesAtConstruction() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> new AspectJExpressionPointcut("@annotation(java.lang.String)"));

        assertTrue(exception.getMessage().contains("不是注解"),
                "非法类型应在切点构造阶段给出可读错误，实际: " + exception.getMessage());
    }
}
