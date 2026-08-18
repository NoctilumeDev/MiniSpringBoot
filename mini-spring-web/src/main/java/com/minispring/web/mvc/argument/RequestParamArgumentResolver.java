package com.minispring.web.mvc.argument;

import com.minispring.web.conversion.TypeConversionService;
import com.minispring.web.http.HttpRequest;
import com.minispring.web.http.HttpResponse;
import com.minispring.web.mvc.annotation.RequestParam;

import java.lang.reflect.Parameter;

/**
 * 解析 {@link RequestParam}：从 query 参数取值，支持默认值与必填校验。
 */
public class RequestParamArgumentResolver implements ArgumentResolver {

    private final TypeConversionService converter = new TypeConversionService();

    @Override
    public boolean supports(Parameter parameter) {
        return parameter.isAnnotationPresent(RequestParam.class);
    }

    @Override
    public Object resolveArgument(Parameter parameter, HttpRequest request, HttpResponse response) {
        RequestParam requestParam = parameter.getAnnotation(RequestParam.class);
        String name = requestParam.value();
        String value = request.getQueryParameter(name);
        if (value == null && !requestParam.defaultValue().isEmpty()) {
            value = requestParam.defaultValue();
        }
        if (value == null && requestParam.required()) {
            throw new IllegalStateException("缺少必填查询参数[" + name + "]");
        }
        return value == null ? null : converter.convert(value, parameter.getType());
    }
}