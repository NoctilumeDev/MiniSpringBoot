package com.minispring.web.http;

/**
 * HTTP 错误响应的最小公共边界。
 *
 * <p>4xx 允许携带由服务端明确编写、可公开给客户端的业务消息；5xx 只返回稳定的通用正文，
 * 真实异常、SQL、约束名和基础设施细节只能写入服务端日志。
 */
public final class HttpErrorResponse {

    private HttpErrorResponse() {
    }

    public static String body(int status, Throwable failure) {
        String prefix = status + " " + statusLabel(status);
        if (status >= 500) {
            return prefix;
        }
        String message = failure == null ? null : failure.getMessage();
        return message == null || message.isBlank() ? prefix : prefix + ": " + message;
    }

    /** 详细原因只留在服务端；调用方负责确保日志目录和访问权限合适。 */
    public static void log(String context, Throwable failure) {
        System.err.println(context);
        if (failure != null) {
            failure.printStackTrace(System.err);
        }
    }

    private static String statusLabel(int status) {
        return switch (status) {
            case 400 -> "Bad Request";
            case 404 -> "Not Found";
            case 405 -> "Method Not Allowed";
            case 409 -> "Conflict";
            case 500 -> "Internal Server Error";
            case 502 -> "Bad Gateway";
            case 503 -> "Service Unavailable";
            default -> "Error";
        };
    }
}
