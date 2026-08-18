package com.minispring.web.resource;

import com.minispring.web.http.HttpRequest;
import com.minispring.web.http.HttpResponse;

import java.io.IOException;
import java.io.InputStream;

/**
 * 静态资源托管：从 classpath 的 {@code static/} 目录下按请求路径取文件。
 *
 * <p>命中则写出并返回 true，未命中返回 false（交给 404）。这是「内嵌服务器 + 前端静态页」
 * 场景下最朴素的一条能力：不引入专门的静态服务器，框架自己就能吐出 html/js/css。
 */
public class StaticResourceHandler {

    private static final String PREFIX = "static";

    public boolean handle(HttpRequest request, HttpResponse response) {
        String path = request.getPath();
        if (path == null || path.equals("/")) {
            path = "/index.html";
        }
        String location = PREFIX + path;
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(location)) {
            if (in == null) {
                return false;
            }
            byte[] bytes = in.readAllBytes();
            response.setContentType(contentTypeOf(path));
            response.write(bytes);
            return true;
        } catch (IOException e) {
            throw new IllegalStateException("读取静态资源失败: " + location, e);
        }
    }

    private String contentTypeOf(String path) {
        if (path.endsWith(".html")) {
            return "text/html; charset=utf-8";
        }
        if (path.endsWith(".css")) {
            return "text/css; charset=utf-8";
        }
        if (path.endsWith(".js")) {
            return "application/javascript; charset=utf-8";
        }
        if (path.endsWith(".json")) {
            return "application/json; charset=utf-8";
        }
        if (path.endsWith(".png")) {
            return "image/png";
        }
        if (path.endsWith(".jpg") || path.endsWith(".jpeg")) {
            return "image/jpeg";
        }
        if (path.endsWith(".svg")) {
            return "image/svg+xml";
        }
        return "application/octet-stream";
    }
}