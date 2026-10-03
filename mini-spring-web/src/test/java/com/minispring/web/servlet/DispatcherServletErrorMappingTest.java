package com.minispring.web.servlet;

import com.minispring.web.http.HttpResponse;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
    }

    @Test
    void errorResponseKeeps4xxBusinessMessageButNeverExposes5xxDetails() {
        DispatcherServlet servlet = new DispatcherServlet();

        RecordingResponse conflict = new RecordingResponse();
        servlet.writeError(conflict, new ResponseStatusException(409, "该邮箱已存在"));
        assertEquals(409, conflict.status);
        assertEquals("409 Conflict: 该邮箱已存在", conflict.body.toString());

        for (Throwable failure : new Throwable[] {
                new RuntimeException("INSERT INTO users ... uk_users_email ... secret@example.com"),
                new ResponseStatusException(500, "explicit-server-secret"),
                new Error("fatal-server-secret")
        }) {
            RecordingResponse response = new RecordingResponse();
            servlet.writeError(response, failure);
            assertEquals(500, response.status);
            assertEquals("500 Internal Server Error", response.body.toString());
            assertFalse(response.body.toString().contains("secret"));
        }

        RecordingResponse committed = new RecordingResponse();
        committed.committed = true;
        committed.status = 202;
        committed.body.append("already-written");
        servlet.writeError(committed, new RuntimeException("must-not-be-appended"));
        assertEquals(202, committed.status);
        assertEquals("already-written", committed.body.toString());
        assertTrue(committed.committed);
    }

    private static final class RecordingResponse implements HttpResponse {
        private int status;
        private boolean committed;
        private final StringBuilder body = new StringBuilder();

        @Override
        public void setStatus(int status) {
            this.status = status;
        }

        @Override
        public void setContentType(String contentType) {
        }

        @Override
        public void setHeader(String name, String value) {
        }

        @Override
        public void write(byte[] bytes) {
            body.append(new String(bytes, StandardCharsets.UTF_8));
        }

        @Override
        public void write(String text) {
            body.append(text);
        }

        @Override
        public boolean isCommitted() {
            return committed;
        }
    }
}
