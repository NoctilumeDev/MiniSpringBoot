package com.minispring.web.mvc;

import com.minispring.core.BeanDefinition;
import com.minispring.core.support.DefaultListableBeanFactory;
import com.minispring.web.mvc.annotation.GetMapping;
import com.minispring.web.mvc.annotation.RequestMapping;
import com.minispring.web.mvc.annotation.RestController;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * N1/N2（M0-M9 复审）的约束性用例：
 * <ul>
 *   <li>N1：同「方法 + 路径」重复映射必须在注册期抛 Ambiguous，不得静默取其一</li>
 *   <li>N2：类级 {@code @RequestMapping(path = ...)} 别名必须生效（与方法级 value/path 对称）</li>
 * </ul>
 */
class RequestMappingHandlerMappingTest {

    @RestController
    static class DupController {
        @GetMapping("/same")
        public String first() {
            return "a";
        }

        @GetMapping("/same")
        public String second() {
            return "b";
        }
    }

    /** 类级用 path 别名声明前缀（N2：修复前只读 value()，前缀 "/dup" 会静默丢失）。 */
    @RestController
    @RequestMapping(path = "/dup")
    static class PathAliasController {
        @GetMapping("/x")
        public String x() {
            return "x";
        }
    }

    /** 类级用 value 形式声明同样的前缀——与上一类拼出相同完整路径 /dup/x。 */
    @RestController
    @RequestMapping("/dup")
    static class ValueFormController {
        @GetMapping("/x")
        public String x() {
            return "x";
        }
    }

    private RequestMappingHandlerMapping newMappingWith(Object... controllers) {
        DefaultListableBeanFactory factory = new DefaultListableBeanFactory();
        for (Object controller : controllers) {
            factory.registerBeanDefinition(
                    controller.getClass().getSimpleName(),
                    new BeanDefinition(controller.getClass()));
        }
        RequestMappingHandlerMapping mapping = new RequestMappingHandlerMapping();
        mapping.setBeanFactory(factory);
        return mapping;
    }

    /** N1：单控制器内两个方法映射同一路径 + 方法 → 启动期必须报 Ambiguous mapping。 */
    @Test
    void ambiguousMappingFailsAtStartup() {
        RequestMappingHandlerMapping mapping = newMappingWith(new DupController());
        assertThrows(IllegalStateException.class, mapping::afterPropertiesSet);
    }

    /** N2：path 别名生效时，两个控制器拼出相同完整路径 /dup/x → 必须报 Ambiguous；
     *  若 path 别名失效（修复前的 bug），完整路径分别为 /x 与 /dup/x，不会抛错——本用例即失败。 */
    @Test
    void classLevelPathAliasApplies() {
        RequestMappingHandlerMapping mapping =
                newMappingWith(new PathAliasController(), new ValueFormController());
        assertThrows(IllegalStateException.class, mapping::afterPropertiesSet);
    }
}
