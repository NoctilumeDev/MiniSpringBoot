package com.minispring.web.mvc.argument;

import com.minispring.web.conversion.TypeConversionService;
import com.minispring.web.http.HttpRequest;
import com.minispring.web.http.HttpResponse;
import com.minispring.web.mvc.RequestMappingHandlerMapping;
import com.minispring.web.mvc.annotation.PathVariable;

import java.lang.reflect.Parameter;
import java.util.Map;

/**
 * 解析 {@link PathVariable}：从「路径模板命中的变量表」里取值，再做类型转换。
 */
public class PathVariableArgumentResolver implements ArgumentResolver {

    private final TypeConversionService converter = new TypeConversionService();

    @Override
    public boolean supports(Parameter parameter) {
        return parameter.isAnnotationPresent(PathVariable.class);
    }

    @Override
    @SuppressWarnings("unchecked")
    public Object resolveArgument(Parameter parameter, HttpRequest request, HttpResponse response) {
        String name = parameter.getAnnotation(PathVariable.class).value();
        Map<String, String> variables =
                (Map<String, String>) request.getAttribute(RequestMappingHandlerMapping.URI_VARIABLES);
        if (variables == null || !variables.containsKey(name)) {
            throw new IllegalStateException("路径变量[" + name + "]不存在");
        }
        return converter.convert(variables.get(name), parameter.getType());
    }
}