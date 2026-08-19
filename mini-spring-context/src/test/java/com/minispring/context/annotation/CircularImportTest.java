package com.minispring.context.annotation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * H2（M0-M9 复审第二轮）的约束用例：@Import 循环导入（A→B→A / 自导入）必须得到
 * 可读的 IllegalStateException 而非 StackOverflowError。
 * 修复前：processImports ↔ registerConfigClass 相互递归无 visiting 防护，环直接打爆栈。
 */
class CircularImportTest {

    @Configuration
    @Import(CyclicB.class)
    static class CyclicA {
    }

    @Configuration
    @Import(CyclicA.class)
    static class CyclicB {
    }

    @Configuration
    @Import(SelfImport.class)
    static class SelfImport {
    }

    @Test
    void mutualImportCycleFailsWithReadableError() {
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> new AnnotationConfigApplicationContext(CyclicA.class));
        assertTrue(ex.getMessage().contains("循环导入"),
                "应给出可读的 @Import 循环导入错误，实际: " + ex.getMessage());
    }

    @Test
    void selfImportCycleFailsWithReadableError() {
        assertThrows(IllegalStateException.class,
                () -> new AnnotationConfigApplicationContext(SelfImport.class));
    }
}
