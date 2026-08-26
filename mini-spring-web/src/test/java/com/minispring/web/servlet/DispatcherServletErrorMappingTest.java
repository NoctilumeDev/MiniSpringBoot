package com.minispring.web.servlet;

import com.minispring.web.http.HttpResponse;
import com.minispring.web.http.HttpErrorResponse;
import com.minispring.web.http.HttpStatusException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 外审复核第三轮（D16 部分收口）的约束用例：异常 → HTTP 状态码的内建映射。
 * 直接断言 {@link DispatcherServlet#resolveStatus}（约束性锚点，沿用 M2 追修纪律：
 * 行为断言对映射退化不敏感，必须断言映射结果本身）。
 */
class DispatcherServletErrorMappingTest {

    @Test
    void responseStatusExceptionUsesItsOwnStatus() {
        DispatcherServlet servlet = new DispatcherServlet();
        assertEquals(404, servlet.resolveStatus(new ResponseStatusException(404, "用户不存在")));
        assertEquals(400, servlet.resolveStatus(new ResponseStatusException(400, "请求不合法")));
        assertEquals(409, servlet.resolveStatus(new ResponseStatusException(409, "冲突")));
        assertEquals(413, servlet.resolveStatus(new HttpStatusException(413, "请求体过大")));
        assertThrows(IllegalArgumentException.class,
                () -> new ResponseStatusException(200, "异常不得伪造成功"));
    }

    @Test
    void illegalArgumentMapsToBadRequest() {
        // 参数/校验类错误 → 400（修复前统一 500，调用方无法区分客户端错误与服务器故障）
        assertEquals(400, new DispatcherServlet().resolveStatus(
                new IllegalArgumentException("转账金额必须为正数: -5")));
    }

    @Test
    void otherThrowablesStayInternalServerError() {
        DispatcherServlet servlet = new DispatcherServlet();
        // 业务规则冲突 / 基础设施故障维持 500 —— transfer-fail 回滚演示（V3 叙事）依赖此口径
        assertEquals(500, servlet.resolveStatus(new IllegalStateException("transfer-fail-in-middle")));
        assertEquals(500, servlet.resolveStatus(new RuntimeException("SQL 执行失败")));
        assertEquals(500, servlet.resolveStatus(new Error("溢出")));
        assertEquals("503 Service Unavailable",
                HttpErrorResponse.body(503, "jdbc:mysql://internal?password=secret"));
    }

    @Test
    void internalErrorResponseDoesNotExposeImplementationDetails() throws Exception {
        DispatcherServlet servlet = new DispatcherServlet();
        RecordingResponse response = new RecordingResponse();

        // 未初始化的 handlerMapping 会触发服务端 NPE；从完整 handle 边界验证响应，而非只测格式化函数。
        servlet.handle(null, response);

        assertEquals(500, response.status);
        assertEquals("text/plain; charset=utf-8", response.contentType);
        assertEquals("500 Internal Server Error", response.body);
        assertFalse(response.body.contains("NullPointerException"));
    }

    private static final class RecordingResponse implements HttpResponse {
        private int status;
        private String contentType;
        private String body;

        @Override
        public void setStatus(int status) {
            this.status = status;
        }

        @Override
        public void setContentType(String contentType) {
            this.contentType = contentType;
        }

        @Override
        public void setHeader(String name, String value) {
        }

        @Override
        public void write(byte[] bytes) {
            this.body = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
        }

        @Override
        public void write(String text) {
            this.body = text;
        }

        @Override
        public boolean isCommitted() {
            return body != null;
        }
    }
}
