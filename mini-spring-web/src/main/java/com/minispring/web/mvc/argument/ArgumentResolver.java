package com.minispring.web.mvc.argument;

import com.minispring.web.http.HttpRequest;
import com.minispring.web.http.HttpResponse;

import java.lang.reflect.Parameter;

/**
 * 参数解析器：声明「我能解析哪种参数」，并把请求里的信息翻译成方法实参。
 *
 * <p>用「策略模式」串起来：遍历解析器，谁声明能解析就交给谁，让参数处理天然可扩展
 * （想支持 @RequestHeader，加一个解析器即可，主流程不动）。
 */
public interface ArgumentResolver {

    boolean supports(Parameter parameter);

    Object resolveArgument(Parameter parameter, HttpRequest request, HttpResponse response) throws Exception;
}