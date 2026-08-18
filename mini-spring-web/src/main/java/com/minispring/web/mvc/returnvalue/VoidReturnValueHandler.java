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
        // 空 body
    }
}