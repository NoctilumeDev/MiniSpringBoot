package com.minispring.core.demo;

import com.minispring.core.BeanDefinition;
import com.minispring.core.BeanPostProcessor;
import com.minispring.core.PropertyValue;
import com.minispring.core.support.DefaultListableBeanFactory;

/**
 * M1 落地演示：证明 IoC 容器「真能用」——
 * 依赖注入、循环依赖解开、生命周期回调（初始化 / 销毁）真实发生。
 *
 * <p>验收标准：本 main 真实跑起来，进程正常退出（exit 0），且以下行为在运行期真实发生
 * ——绝不是「单元测试断言通过」或「日志打印正确」这么简单。
 */
public class IocDemo {

    /** 相互依赖的一方 A：依赖 B。 */
    public static class ServiceA {
        // 由容器注入（字段注入）
        private ServiceB b;

        private boolean initialized = false;
        private boolean destroyed = false;

        public ServiceB getB() {
            return b;
        }

        public void init() {
            this.initialized = true;
            System.out.println("    [生命周期] ServiceA.init() 被容器调用");
        }

        public void cleanup() {
            this.destroyed = true;
            System.out.println("    [生命周期] ServiceA.cleanup() 被容器调用");
        }

        public boolean isInitialized() {
            return initialized;
        }

        public boolean isDestroyed() {
            return destroyed;
        }
    }

    /** 相互依赖的另一方 B：依赖 A。 */
    public static class ServiceB {
        private ServiceA a;

        public ServiceA getA() {
            return a;
        }
    }

    public static void main(String[] args) {
        System.out.println("=== M1 IoC 容器落地演示 ===");

        DefaultListableBeanFactory factory = new DefaultListableBeanFactory();

        // 1. 注册 BeanPostProcessor，证明它能介入「成品」的初始化
        factory.addBeanPostProcessor(new BeanPostProcessor() {
            @Override
            public Object postProcessBeforeInitialization(Object bean, String beanName) {
                System.out.println("    [BeanPostProcessor] before -> " + beanName);
                return bean;
            }

            @Override
            public Object postProcessAfterInitialization(Object bean, String beanName) {
                System.out.println("    [BeanPostProcessor] after  -> " + beanName);
                return bean;
            }
        });

        // 2. 注册循环依赖 A ↔ B
        BeanDefinition bdA = new BeanDefinition(ServiceA.class);
        bdA.addPropertyValue(PropertyValue.ref("b", "b"));
        bdA.setInitMethodName("init");
        bdA.setDestroyMethodName("cleanup");
        factory.registerBeanDefinition("a", bdA);

        BeanDefinition bdB = new BeanDefinition(ServiceB.class);
        bdB.addPropertyValue(PropertyValue.ref("a", "a"));
        factory.registerBeanDefinition("b", bdB);

        // 3. 从容器取 Bean，触发循环依赖的完整创建
        ServiceA a = factory.getBean("a", ServiceA.class);
        ServiceB b = factory.getBean("b", ServiceB.class);

        // 4. 真实校验：失败即抛异常，绝不「只打日志糊弄过去」
        check(a.getB() != null, "ServiceA.b 未被注入");
        check(b.getA() != null, "ServiceB.a 未被注入");
        check(a.getB() == b, "ServiceA.b 与容器中的 b 不是同一实例");
        check(b.getA() == a, "ServiceB.a 与容器中的 a 不是同一实例（循环依赖未正确解开）");
        check(a.isInitialized(), "ServiceA.init() 未被容器调用");
        check(a == factory.getBean("a", ServiceA.class), "单例被错误地创建了多次");

        System.out.println("    ✓ 依赖注入成功");
        System.out.println("    ✓ 循环依赖 A↔B 已正确解开（a.b == b 且 b.a == a）");
        System.out.println("    ✓ 初始化回调真实发生");

        // 5. 关闭容器，验证销毁回调真实发生
        factory.close();
        check(a.isDestroyed(), "ServiceA.cleanup() 未被容器调用");
        System.out.println("    ✓ 销毁回调真实发生");

        System.out.println("=== M1 落地验证通过 ===");
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException("M1 落地校验失败: " + message);
        }
    }
}