package com.minispring.jdbc.transaction;

import com.minispring.aop.ProceedingJoinPoint;
import com.minispring.aop.annotation.Around;
import com.minispring.aop.annotation.Aspect;
import com.minispring.core.BeanFactory;
import com.minispring.core.BeanFactoryAware;
import com.minispring.core.ListableBeanFactory;

/**
 * 声明式事务切面：拦截 {@link Transactional} 标注的方法，把执行体包进
 * {@link TransactionManager#execute}——正常返回提交、抛异常回滚。
 *
 * <p>切点用 {@code @annotation(全限定名)}（M8 扩展），注解在实现类方法上也能命中
 * （AspectJExpressionPointcut 会回查 specific method）。
 *
 * <p><b>M8 修复（依赖链死结）</b>：切面不得构造注入 {@link TransactionManager}——否则
 * 「txAspect → txManager → dataSource」与「dataSource 初始化触发 advisor 收集 → getBean(txAspect)」
 * 互为死结（txAspect 在工厂方法参数解析期无 early 暴露，收集必撞「无法提前暴露的循环依赖」；
 * 纯自动配置、无业务 Bean 的应用必炸）。对齐 Spring TransactionInterceptor 的机制：切面经
 * {@link BeanFactoryAware} 拿工厂，<b>运行期首次拦截时才解析</b> TransactionManager——
 * 收集期零依赖，任意 Bean 定义序下安全。
 */
@Aspect
public class TransactionAspect implements BeanFactoryAware {

    private BeanFactory beanFactory;

    @Override
    public void setBeanFactory(BeanFactory beanFactory) {
        this.beanFactory = beanFactory;
    }

    @Around("@annotation(com.minispring.jdbc.transaction.Transactional)")
    public Object around(ProceedingJoinPoint pjp) throws Throwable {
        return transactionManager().execute(() -> {
            try {
                return pjp.proceed();
            } catch (Error | Exception e) {
                throw e;
            } catch (Throwable t) {
                throw new com.minispring.jdbc.DataAccessException("事务方法内出现未分类 Throwable", t);
            }
        });
    }

    /**
     * 运行期懒解析（首次拦截时 TransactionManager 早已就绪）。
     * BeanFactory 无按类型 getBean 重载，走项目惯例：getBeanNamesForType + 按名获取。
     */
    private TransactionManager transactionManager() {
        String[] names = ((ListableBeanFactory) beanFactory).getBeanNamesForType(TransactionManager.class);
        if (names.length == 0) {
            throw new com.minispring.jdbc.DataAccessException(
                    "容器中不存在 TransactionManager——检查 JdbcAutoConfiguration 是否装配"
                            + "（minispring.datasource.url 已配置？）");
        }
        // M5（M0-M9 复审第二轮）：多候选时取 names[0] 的结果依赖 ConcurrentHashMap 遍历序——
        // 双数据源场景下事务路由随机。与其静默赌一个，不如启动期语义前移：显式报错要求用户收敛
        if (names.length > 1) {
            throw new com.minispring.jdbc.DataAccessException("容器中存在多个 TransactionManager（"
                    + java.util.Arrays.toString(names) + "）——@Transactional 无法裁决事务边界；"
                    + "教学子集请只保留一个（或拆分应用）");
        }
        return beanFactory.getBean(names[0], TransactionManager.class);
    }
}
