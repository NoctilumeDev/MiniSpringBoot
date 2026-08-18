package com.minispring.aop;

import com.minispring.aop.annotation.After;
import com.minispring.aop.annotation.Around;
import com.minispring.aop.annotation.Aspect;
import com.minispring.aop.annotation.Before;
import com.minispring.aop.aspectj.AspectJAdvisorFactory;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AOP 代理机制单测（不经容器，直接装配 Advisor + JDK 代理）：
 * 前置 / 后置（finally 语义）/ 环绕 三类通知真实生效；@After 在目标抛异常时也执行。
 */
class JdkProxyAdviceTest {

    interface Greeter {
        String hello(String who);

        String unplanned();
    }

    static class GreeterImpl implements Greeter {
        @Override
        public String hello(String who) {
            return "hi " + who;
        }

        /** 不被任何切点命中（M8：等价「经代理但无 @Transactional 的方法」常态路径）。 */
        @Override
        public String unplanned() {
            throw new IllegalStateException("unplanned-failure");
        }
    }

    /** 切面必须自带 @Aspect（Advisor 工厂按「本类声明的方法」扫描，继承的方法不可见）。 */
    @Aspect
    static class TracingAspect {
        final List<String> calls = new ArrayList<>();

        @Before("execution(* com.minispring.aop.JdkProxyAdviceTest$GreeterImpl.hello(..))")
        public void before() {
            calls.add("before");
        }

        @After("execution(* com.minispring.aop.JdkProxyAdviceTest$GreeterImpl.hello(..))")
        public void after() {
            calls.add("after");
        }

        @Around("execution(* com.minispring.aop.JdkProxyAdviceTest$GreeterImpl.hello(..))")
        public Object around(ProceedingJoinPoint pjp) throws Throwable {
            calls.add("around-before");
            try {
                return pjp.proceed();
            } finally {
                calls.add("around-after");
            }
        }
    }

    interface Failing {
        void boom();
    }

    static class FailingImpl implements Failing {
        @Override
        public void boom() {
            throw new IllegalStateException("target-failure");
        }
    }

    /** 只有 @After：目标抛异常后，after 仍必须执行（finally 语义）。 */
    @Aspect
    static class AfterOnlyAspect {
        final List<String> trace = new ArrayList<>();

        @After("execution(* com.minispring.aop.JdkProxyAdviceTest$FailingImpl.boom(..))")
        public void after() {
            trace.add("after");
        }
    }

    @Test
    void allThreeAdvicesExecuteAroundTargetCall() {
        TracingAspect aspect = new TracingAspect();
        List<Advisor> advisors = new AspectJAdvisorFactory(aspect).getAdvisors();
        Greeter proxy = (Greeter) new JdkDynamicAopProxy(new GreeterImpl(), advisors).getProxy();

        assertEquals("hi mini", proxy.hello("mini"));
        // before 与 around 各执行一次；after 是 finally 语义，正常路径也执行
        assertTrue(aspect.calls.contains("before"));
        assertTrue(aspect.calls.contains("after"));
        assertTrue(aspect.calls.contains("around-before"));
        assertTrue(aspect.calls.contains("around-after"));
        assertEquals(1, aspect.calls.stream().filter("before"::equals).count());
        assertEquals(1, aspect.calls.stream().filter("after"::equals).count());
    }

    @Test
    void afterAdviceRunsEvenWhenTargetThrows() {
        AfterOnlyAspect aspect = new AfterOnlyAspect();
        List<Advisor> advisors = new AspectJAdvisorFactory(aspect).getAdvisors();
        Failing proxy = (Failing) new JdkDynamicAopProxy(new FailingImpl(), advisors).getProxy();

        IllegalStateException ex = assertThrows(IllegalStateException.class, proxy::boom);
        assertEquals("target-failure", ex.getMessage());
        assertTrue(aspect.trace.contains("after"), "@After 是 finally 语义：目标抛异常后仍必须执行");
    }

    /**
     * M8 修复的约束用例（对称层：不命中切点的直通路径）。
     * 修复前：{@code method.invoke} 抛 InvocationTargetException（message=null）直达调用方，
     * 业务异常类型与消息全部丢失（demo 实证「500 Internal Server Error: null」）。
     * 若本测试通过，说明直通路径与拦截路径（上一用例）的 ITE 拆包语义一致。
     */
    @Test
    void unmatchedMethodFailureKeepsOriginalException() {
        TracingAspect aspect = new TracingAspect();
        List<Advisor> advisors = new AspectJAdvisorFactory(aspect).getAdvisors();
        Greeter proxy = (Greeter) new JdkDynamicAopProxy(new GreeterImpl(), advisors).getProxy();

        IllegalStateException ex = assertThrows(IllegalStateException.class, proxy::unplanned);
        assertEquals("unplanned-failure", ex.getMessage());
        // 切点不命中：通知不得执行（证明该方法确实走了「空链直通」分支）
        assertTrue(aspect.calls.isEmpty());
    }
}
