package com.minispring.web.mvc.argument;

import com.minispring.web.http.HttpRequest;
import com.minispring.web.http.HttpResponse;
import com.minispring.web.json.JsonObjectMapper;
import com.minispring.web.mvc.annotation.RequestBody;

import java.lang.reflect.Parameter;

/**
 * 解析 {@link RequestBody}：读请求体 → 反序列化成方法入参对象。
 */
public class RequestBodyArgumentResolver implements ArgumentResolver {

    private final JsonObjectMapper mapper = new JsonObjectMapper();

    @Override
    public boolean supports(Parameter parameter) {
        return parameter.isAnnotationPresent(RequestBody.class);
    }

    @Override
    public Object resolveArgument(Parameter parameter, HttpRequest request, HttpResponse response) {
        return mapper.readValue(request.getBody(), parameter.getType());
    }
}