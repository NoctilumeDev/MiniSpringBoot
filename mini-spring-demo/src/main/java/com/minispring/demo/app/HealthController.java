package com.minispring.demo.app;

import com.minispring.context.annotation.Autowired;
import com.minispring.jdbc.JdbcTemplate;
import com.minispring.web.mvc.annotation.GetMapping;
import com.minispring.web.mvc.annotation.RequestMapping;
import com.minispring.web.mvc.annotation.RestController;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * M10 多实例探针：live 只证明当前 JVM/HTTP 链路存活，ready 还会真实穿透连接池与 MySQL。
 * 实例标识来自系统属性 {@code -Dapp.instance-id=msb-N}，便于负载均衡分布与故障切换取证。
 */
@RestController
@RequestMapping("/health")
public class HealthController {

    private final AppProperties appProperties;
    private final JdbcTemplate jdbc;
    private final String startedAt = Instant.now().toString();

    @Autowired
    public HealthController(AppProperties appProperties, JdbcTemplate jdbc) {
        this.appProperties = appProperties;
        this.jdbc = jdbc;
    }

    /** 不访问外部资源：用于判断进程和 HTTP 服务器是否仍能接请求。 */
    @GetMapping("/live")
    public Map<String, Object> live() {
        return base("UP");
    }

    /**
     * 就绪探针：执行真实 {@code SELECT 1}。数据库或连接池不可用时沿既有错误链返回 500，
     * 不用一个“永远 UP”的假健康接口掩盖全链路故障。
     */
    @GetMapping
    public Map<String, Object> ready() {
        Integer probe = jdbc.queryOne("SELECT 1 AS probe", rs -> rs.getInt("probe"));
        if (probe == null || probe != 1) {
            throw new IllegalStateException("数据库就绪探针返回异常: " + probe);
        }
        Map<String, Object> result = base("UP");
        result.put("database", "UP");
        return result;
    }

    private Map<String, Object> base(String status) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", status);
        result.put("instance", appProperties.getInstanceId());
        result.put("port", appProperties.getPort());
        result.put("startedAt", startedAt);
        return result;
    }
}
