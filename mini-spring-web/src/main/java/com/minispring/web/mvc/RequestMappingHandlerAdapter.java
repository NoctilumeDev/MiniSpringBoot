package com.minispring.web.mvc;

import com.minispring.web.http.HttpRequest;
import com.minispring.web.http.HttpResponse;
import com.minispring.web.mvc.argument.ArgumentResolver;
import com.minispring.web.mvc.argument.PathVariableArgumentResolver;
import com.minispring.web.mvc.argument.RequestBodyArgumentResolver;
import com.minispring.web.mvc.argument.RequestParamArgumentResolver;
import com.minispring.web.mvc.argument.ServletRequestArgumentResolver;
import com.minispring.web.mvc.returnvalue.ResponseBodyReturnValueHandler;
import com.minispring.web.mvc.returnvalue.ReturnValueHandler;
import com.minispring.web.mvc.returnvalue.VoidReturnValueHandler;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.List;

/**
 * 处理 {@code @RequestMapping} 场景的适配器：组装默认的参数解析器与返回值处理器，
 * 完成一次真实的方法调用。
 */
public class RequestMappingHandlerAdapter implements HandlerAdapter {

    private final List<ArgumentResolver> argumentResolvers = new ArrayList<>();
    private final List<ReturnValueHandler> returnValueHandlers = new ArrayList<>();

    public RequestMappingHandlerAdapter() {
        this.argumentResolvers.add(new ServletRequestArgumentResolver());
        this.argumentResolvers.add(new PathVariableArgumentResolver());
        this.argumentResolvers.add(new RequestParamArgumentResolver());
        this.argumentResolvers.add(new RequestBodyArgumentResolver());
        this.returnValueHandlers.add(new ResponseBodyReturnValueHandler());
        this.returnValueHandlers.add(new VoidReturnValueHandler());
    }

    @Override
    public boolean supports(HandlerMethod handlerMethod) {
        return true; // 本阶段唯一适配器
    }

    @Override
    public void handle(HttpRequest request, HttpResponse response, HandlerMethod handlerMethod) throws Exception {
        Object[] args = resolveArguments(request, response, handlerMethod.getMethod());
        Object result = invoke(handlerMethod, args);
        applyReturnValue(result, handlerMethod.getMethod(), request, response);
    }

    /** 反射调用并拆掉 InvocationTargetException（B1 教训：让原始异常原样透传给调用方/前端控制器）。 */
    private Object invoke(HandlerMethod handlerMethod, Object[] args) throws Exception {
        try {
            return handlerMethod.getMethod().invoke(handlerMethod.getBean(), args);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getTargetException();
            if (cause instanceof Exception) {
                throw (Exception) cause;
            }
            if (cause instanceof Error) {
                throw (Error) cause;
            }
            throw new IllegalStateException("目标方法执行失败", cause);
        }
    }

    private Object[] resolveArguments(HttpRequest request, HttpResponse response, Method method) throws Exception {
        Parameter[] parameters = method.getParameters();
        Object[] args = new Object[parameters.length];
        for (int i = 0; i < parameters.length; i++) {
            ArgumentResolver resolver = null;
            for (ArgumentResolver candidate : argumentResolvers) {
                if (candidate.supports(parameters[i])) {
                    resolver = candidate;
                    break;
                }
            }
            if (resolver == null) {
                throw new IllegalStateException("无法解析参数[" + parameters[i] + "]（第 " + i + " 个参数）");
            }
            args[i] = resolver.resolveArgument(parameters[i], request, response);
        }
        return args;
    }

    private void applyReturnValue(Object result, Method method, HttpRequest request, HttpResponse response) throws Exception {
        for (ReturnValueHandler handler : returnValueHandlers) {
            if (handler.supports(result, method)) {
                handler.handleReturnValue(result, method, request, response);
                return;
            }
        }
        // 兜底：没有匹配处理器时按文本原样输出
        if (result != null) {
            response.setContentType("text/plain; charset=utf-8");
            response.write(String.valueOf(result));
        }
    }
}