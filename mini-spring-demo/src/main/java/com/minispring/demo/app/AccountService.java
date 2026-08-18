package com.minispring.demo.app;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 转账服务接口——<b>必须接口化</b>（D8：JDK 动态代理只代理接口方法，
 * {@code @Transactional} 声明式事务靠接口代理织入）。
 */
public interface AccountService {

    /**
     * 正常转账：扣款 + 入款同事务，成功才提交。余额不足抛异常（同样回滚）。
     * 返回两账户<b>事务内</b>余额（P0-6：与提交一致，避免提交后再读的竞态窗口）。
     */
    Map<String, Object> transfer(long fromId, long toId, BigDecimal amount);

    /** 演示回滚：扣款成功后、入款前刻意抛异常——两账户余额必须都保持原值（V3 验收）。 */
    void transferFailInMiddle(long fromId, long toId, BigDecimal amount);

    /** 查询账户余额。 */
    BigDecimal balance(long id);
}
