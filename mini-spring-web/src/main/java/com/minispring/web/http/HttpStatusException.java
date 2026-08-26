package com.minispring.web.http;

/**
 * 可安全映射到 HTTP 错误响应的显式失败。
 *
 * <p>只接受 4xx/5xx，防止调用方用“异常”伪造成功或重定向响应。是否向客户端公开
 * {@link #getMessage()} 由统一错误边界按状态族决定：4xx 可读，5xx 必须脱敏。
 */
public class HttpStatusException extends RuntimeException {

    private final int status;

    public HttpStatusException(int status, String message) {
        this(status, message, null);
    }

    public HttpStatusException(int status, String message, Throwable cause) {
        super(message, cause);
        if (status < 400 || status > 599) {
            throw new IllegalArgumentException("HTTP 异常状态码必须在 400..599 范围内: " + status);
        }
        this.status = status;
    }

    public int getStatus() {
        return status;
    }
}
