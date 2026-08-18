package com.minispring.context.annotation;

import com.minispring.core.BeansException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * D34（M8 收口）回归：@Qualifier 限定名裁决——支持「限定名 ≠ beanName」（多数据源的关键语义）。
 * 修复前：注入端只把 @Qualifier(v) 当 beanName 用，BeanDefinition.qualifier 存而不用；
 * 多数据源下限定名命不中直接 NoSuchBeanDefinitionException。
 */
class QualifierResolutionTest {

    static class DsStub {
        final String tag;

        DsStub(String tag) {
            this.tag = tag;
        }
    }

    static class DataSourceConfig {

        @Bean("orderDataSource")
        @Qualifier("ordersDb")
        public DsStub orderDataSource() {
            return new DsStub("orders");
        }

        @Bean("userDataSource")
        @Qualifier("usersDb")
        public DsStub userDataSource() {
            return new DsStub("users");
        }
    }

    /** 字段注入：限定名 usersDb ≠ beanName userDataSource。 */
    static class FieldInjected {
        @Autowired
        @Qualifier("usersDb")
        DsStub dataSource;

        @Autowired
        @Qualifier("ordersDb")
        DsStub orderDs;
    }

    /** 构造器注入（core 侧对称路径）：限定名走 BeanDefinition.qualifier。 */
    static class CtorInjected {
        final DsStub dataSource;

        @Autowired
        CtorInjected(@Qualifier("usersDb") DsStub dataSource) {
            this.dataSource = dataSource;
        }
    }

    /** 回退语义：@Qualifier 直接用 beanName（无此限定名时）。 */
    static class FallbackByName {
        @Autowired
        @Qualifier("orderDataSource")
        DsStub dataSource;
    }

    /** 负例：限定名既不在 qualifier 也不是 beanName。 */
    static class BadInjected {
        @Autowired
        @Qualifier("ghostDb")
        DsStub dataSource;
    }

    @Test
    void qualifierResolvesByNameDifferentFromBeanName() {
        AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext(DataSourceConfig.class, FieldInjected.class);
        try {
            FieldInjected bean = ctx.getBean("fieldInjected", FieldInjected.class);
            assertEquals("users", bean.dataSource.tag, "@Qualifier 应按限定名（而非 beanName）命中 usersDb");
            assertEquals("orders", bean.orderDs.tag, "第二个限定名同样正确命中");
        } finally {
            ctx.close();
        }
    }

    @Test
    void qualifierWorksForConstructorArgs() {
        AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext(DataSourceConfig.class, CtorInjected.class);
        try {
            CtorInjected bean = ctx.getBean("ctorInjected", CtorInjected.class);
            assertEquals("users", bean.dataSource.tag, "构造器参数的限定名裁决与字段注入对称（D34 core 侧）");
        } finally {
            ctx.close();
        }
    }

    @Test
    void qualifierFallsBackToBeanNameWhenNoQualifierMatches() {
        AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext(DataSourceConfig.class, FallbackByName.class);
        try {
            FallbackByName bean = ctx.getBean("fallbackByName", FallbackByName.class);
            assertEquals("orders", bean.dataSource.tag, "限定名无匹配时应回退按 beanName 解析");
        } finally {
            ctx.close();
        }
    }

    @Test
    void unknownQualifierFailsWithReadableError() {
        BeansException ex = assertThrows(BeansException.class,
                () -> new AnnotationConfigApplicationContext(DataSourceConfig.class, BadInjected.class));
        assertTrue(ex.getMessage().contains("ghostDb"), "限定名不存在时应给出指名道姓的可读错误，实际: " + ex.getMessage());
    }
}
