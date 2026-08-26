package com.minispring.web.http;

/** 统一生成错误响应正文，确保传输层与 MVC 层使用同一套信息披露规则。 */
public final class HttpErrorResponse {

    private HttpErrorResponse() {
    }

    public static String body(int status, String detail) {
        String base = status + " " + reasonPhrase(status);
        if (status >= 500 || detail == null || detail.isBlank()) {
            return base;
        }
        return base + ": " + detail;
    }

    private static String reasonPhrase(int status) {
        return switch (status) {
            case 400 -> "Bad Request";
            case 404 -> "Not Found";
            case 405 -> "Method Not Allowed";
            case 409 -> "Conflict";
            case 413 -> "Payload Too Large";
            case 500 -> "Internal Server Error";
            case 503 -> "Service Unavailable";
            default -> "Error";
        };
    }
}
