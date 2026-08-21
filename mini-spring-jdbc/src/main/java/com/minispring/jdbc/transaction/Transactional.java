package com.minispring.jdbc.transaction;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 声明式事务：标注在<b>方法</b>上，方法体整体包裹在一个事务里——
 * 正常返回提交，抛任何异常回滚（教学子集：无 rollbackFor 细化）。
 *
 * <p>由 {@code TransactionAspect}（AOP 环绕）驱动：标注方法被调用时，切面把执行委托给
 * {@link TransactionManager#execute}。配合 {@link TransactionContext}，方法内经由
 * {@code JdbcTemplate} 执行的所有 SQL 共用同一连接、同一提交边界。
 *
 * <p><b>D8 约束</b>：标注方法所在类必须接口化（JDK 动态代理只代理接口方法）。
 * 支持方法级与实现类级标注（类级 = 该类全部方法命中）；
 * 接口级继承标注仍延后（见 roadmap）。
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Transactional {
}
