package com.minispring.web.mvc;

import com.minispring.web.http.HttpRequest;
import com.minispring.web.http.HttpResponse;

/**
 * 处理器适配器：负责真正「解析入参 → 调用 controller → 处理返回值」。
 */
public interface HandlerAdapter {

    boolean supports(HandlerMethod handlerMethod);

    void handle(HttpRequest request, HttpResponse response, HandlerMethod handlerMethod) throws Exception;
}