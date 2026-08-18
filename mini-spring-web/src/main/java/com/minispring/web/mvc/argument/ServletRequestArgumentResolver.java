package com.minispring.web.mvc.argument;

import com.minispring.web.http.HttpRequest;
import com.minispring.web.http.HttpResponse;

import java.lang.reflect.Parameter;

/**
 * 解析框架对象：把 {@link HttpRequest} / {@link HttpResponse} 直接注入方法。
 */
public class ServletRequestArgumentResolver implements ArgumentResolver {

    @Override
    public boolean supports(Parameter parameter) {
        return HttpRequest.class.isAssignableFrom(parameter.getType())
                || HttpResponse.class.isAssignableFrom(parameter.getType());
    }

    @Override
    public Object resolveArgument(Parameter parameter, HttpRequest request, HttpResponse response) {
        if (HttpRequest.class.isAssignableFrom(parameter.getType())) {
            return request;
        }
        if (HttpResponse.class.isAssignableFrom(parameter.getType())) {
            return response;
        }
        throw new IllegalStateException("无法注入类型 " + parameter.getType());
    }
}