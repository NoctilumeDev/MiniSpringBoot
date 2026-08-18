package com.minispring.demo.app;

import com.minispring.context.annotation.Autowired;
import com.minispring.web.mvc.annotation.GetMapping;
import com.minispring.web.mvc.annotation.PathVariable;
import com.minispring.web.mvc.annotation.PostMapping;
import com.minispring.web.mvc.annotation.RequestMapping;
import com.minispring.web.mvc.annotation.RequestParam;
import com.minispring.web.mvc.annotation.RestController;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 账户接口：V3（中途炸回滚）/ V4（正常转账提交）/ V5（无脏读对照读）的验收入口。
 */
@RestController
@RequestMapping("/accounts")
public class AccountController {

    private final AccountService accountService;

    @Autowired
    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @PostMapping("/transfer")
    public Map<String, Object> transfer(@RequestParam("from") long fromId,
                                        @RequestParam("to") long toId,
                                        @RequestParam("amount") String amount) {
        // P0-6：余额由 Service 在事务内返回（与提交一致），Controller 不再提交后二次读
        return accountService.transfer(fromId, toId, new BigDecimal(amount));
    }

    /** 刻意中途抛异常：预期 500 + 两账户余额保持原值（回滚取证）。 */
    @PostMapping("/transfer-fail")
    public Map<String, Object> transferFail(@RequestParam("from") long fromId,
                                            @RequestParam("to") long toId,
                                            @RequestParam("amount") String amount) {
        accountService.transferFailInMiddle(fromId, toId, new BigDecimal(amount));
        throw new IllegalStateException("不应到达这里");
    }

    @GetMapping("/{id}")
    public Map<String, Object> balance(@PathVariable("id") long id) {
        return Map.of("id", id, "balance", accountService.balance(id));
    }
}
