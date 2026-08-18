package com.minispring.web.mvc.returnvalue;

import com.minispring.web.http.HttpRequest;
import com.minispring.web.http.HttpResponse;

import java.lang.reflect.Method;

/**
 * 处理 void / null 返回值：空 body，状态码保持默认 200。
 */
public class VoidReturnValueHandler implements ReturnValueHandler {

    @Override
    public boolean supports(Object returnValue, Method method) {
        return returnValue == null || method.getReturnType() == void.class;
    }

    @Override
    public void handleReturnValue(Object returnValue, Method method, HttpRequest request, HttpResponse response) {
        // 空 body：但必须「提交」一次响应（发送 200 响应头）。底层 HttpServer 只在 write 时才
        // sendResponseHeaders，若这里什么都不做，客户端会收到 Empty reply 断连（BUG-1 修复）。
        response.write(new byte[0]);
    }
}