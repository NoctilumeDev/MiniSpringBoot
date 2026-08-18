package com.minispring.web.http;

/**
 * 框架自己的轻量 HTTP 响应抽象。
 */
public interface HttpResponse {

    void setStatus(int status);

    void setContentType(String contentType);

    void setHeader(String name, String value);

    void write(byte[] bytes);

    void write(String text);

    /** 响应头是否已发送。一旦发送，状态码与 Content-Type 就不能再改。 */
    boolean isCommitted();
}