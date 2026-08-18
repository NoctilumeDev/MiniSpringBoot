package com.minispring.web.mvc.returnvalue;

import com.minispring.web.http.HttpRequest;
import com.minispring.web.http.HttpResponse;
import com.minispring.web.json.JsonObjectMapper;
import com.minispring.web.mvc.annotation.ResponseBody;
import com.minispring.web.mvc.annotation.RestController;

import java.lang.reflect.Method;

/**
 * 处理 {@link ResponseBody}（含 {@link RestController}）返回值：对象 → JSON，String → 纯文本。
 */
public class ResponseBodyReturnValueHandler implements ReturnValueHandler {

    private final JsonObjectMapper mapper = new JsonObjectMapper();

    @Override
    public boolean supports(Object returnValue, Method method) {
        return method.isAnnotationPresent(ResponseBody.class)
                || method.getDeclaringClass().isAnnotationPresent(ResponseBody.class)
                || method.getDeclaringClass().isAnnotationPresent(RestController.class);
    }

    @Override
    public void handleReturnValue(Object returnValue, Method method, HttpRequest request, HttpResponse response) {
        if (returnValue == null) {
            response.setContentType("application/json; charset=utf-8");
            response.write(new byte[0]); // 空响应也需提交响应头，否则客户端断连（BUG-1 修复）
            return;
        }
        if (returnValue instanceof String) {
            response.setContentType("text/plain; charset=utf-8");
            response.write((String) returnValue);
        } else {
            response.setContentType("application/json; charset=utf-8");
            response.write(mapper.writeValueAsString(returnValue));
        }
    }
}