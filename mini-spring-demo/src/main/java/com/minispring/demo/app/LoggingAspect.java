package com.minispring.demo.app;

import com.minispring.aop.ProceedingJoinPoint;
import com.minispring.aop.annotation.After;
import com.minispring.aop.annotation.Around;
import com.minispring.aop.annotation.Aspect;
import com.minispring.aop.annotation.Before;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 切面：对 OrderService 的三个横切动作（前置 / 后置 / 环绕）真实生效一次。
 * 用计数器「留存证据」，供运行期断言——不靠肉眼，靠事实。
 */
@Aspect
public class LoggingAspect {

    private static final String ORDER_POINTCUT =
            "execution(* com.minispring.demo.app.OrderServiceImpl.*(..))";

    private final AtomicInteger beforeCount = new AtomicInteger();
    private final AtomicInteger afterCount = new AtomicInteger();
    private final AtomicInteger aroundCount = new AtomicInteger();
    private final AtomicLong aroundCostNanos = new AtomicLong();

    @Before(ORDER_POINTCUT)
    public void before() {
        beforeCount.incrementAndGet();
        System.out.println("    [Before] 前置通知：准备执行下单");
    }

    @After(ORDER_POINTCUT)
    public void after() {
        afterCount.incrementAndGet();
        System.out.println("    [After] 后置通知：下单已正常返回");
    }

    @Around(ORDER_POINTCUT)
    public Object around(ProceedingJoinPoint pjp) throws Throwable {
        aroundCount.incrementAndGet();
        long start = System.nanoTime();
        try {
            return pjp.proceed();
        } finally {
            long cost = System.nanoTime() - start;
            aroundCostNanos.addAndGet(cost);
            System.out.println("    [Around] 环绕通知：" + pjp.getMethodName() + " 耗时 " + cost + " ns");
        }
    }

    public int beforeCount() {
        return beforeCount.get();
    }

    public int afterCount() {
        return afterCount.get();
    }

    public int aroundCount() {
        return aroundCount.get();
    }

    public long aroundCostNanos() {
        return aroundCostNanos.get();
    }
}