package com.minispring.web.mvc.returnvalue;

import com.minispring.web.http.HttpRequest;
import com.minispring.web.http.HttpResponse;

import java.lang.reflect.Method;

/**
 * 返回值处理器：把 controller 方法的返回值写成响应。
 */
public interface ReturnValueHandler {

    boolean supports(Object returnValue, Method method);

    void handleReturnValue(Object returnValue, Method method, HttpRequest request, HttpResponse response) throws Exception;
}