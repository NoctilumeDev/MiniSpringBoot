package com.minispring.web.servlet;

import com.minispring.core.BeanFactory;
import com.minispring.core.BeanFactoryAware;
import com.minispring.core.InitializingBean;
import com.minispring.core.ListableBeanFactory;
import com.minispring.web.http.HttpRequest;
import com.minispring.web.http.HttpResponse;
import com.minispring.web.mvc.HandlerAdapter;
import com.minispring.web.mvc.HandlerMapping;
import com.minispring.web.mvc.HandlerMethod;
import com.minispring.web.resource.StaticResourceHandler;
import com.minispring.web.server.HttpHandler;

/**
 * 前端控制器：所有请求的统一入口，只做一件事——「分发」。
 *
 * <p>找到处理器 → 交给适配器执行并写响应；未命中映射则尝试静态资源，再不行返回 404；
 * 途中任何异常统一转 500。
 */
public class DispatcherServlet implements HttpHandler, BeanFactoryAware, InitializingBean {

    private ListableBeanFactory beanFactory;
    private HandlerMapping handlerMapping;
    private HandlerAdapter handlerAdapter;
    private StaticResourceHandler staticResourceHandler;

    @Override
    public void setBeanFactory(BeanFactory beanFactory) {
        this.beanFactory = (ListableBeanFactory) beanFactory;
    }

    @Override
    public void afterPropertiesSet() {
        this.handlerMapping = singleBean(HandlerMapping.class);
        this.handlerAdapter = singleBean(HandlerAdapter.class);
        this.staticResourceHandler = new StaticResourceHandler();
    }

    private <T> T singleBean(Class<T> type) {
        String[] names = beanFactory.getBeanNamesForType(type);
        if (names.length != 1) {
            throw new IllegalStateException("期望恰好 1 个 " + type.getSimpleName() + "，实际 " + names.length);
        }
        return type.cast(beanFactory.getBean(names[0]));
    }

    @Override
    public void handle(HttpRequest request, HttpResponse response) throws Exception {
        try {
            HandlerMethod handler = handlerMapping.getHandler(request);
            if (handler != null) {
                if (!handlerAdapter.supports(handler)) {
                    throw new IllegalStateException("没有适配器能处理 " + handler);
                }
                handlerAdapter.handle(request, response, handler);
                return;
            }
            if (staticResourceHandler.handle(request, response)) {
                return;
            }
            writeNotFound(response);
        } catch (Throwable e) {
            // P0-1：不能用 catch(Exception)——JSON 深嵌套等场景抛 StackOverflowError（Error 非 Exception），
            // 会绕过这里导致连接层无 HTTP 响应；Throwable 一并兜底为 500。
            writeError(response, e);
        }
    }

    private void writeNotFound(HttpResponse response) {
        response.setStatus(404);
        response.setContentType("text/plain; charset=utf-8");
        response.write("404 Not Found");
    }

    private void writeError(HttpResponse response, Throwable e) {
        // M4（M0-M9 复审第二轮）：响应头已发出（handler 中途 write 后再抛异常）时状态码不可改，
        // 继续写会把错误文本追加进已提交的响应体形成「200 + 错误尾巴」的混合体——记日志并放弃改写
        if (response.isCommitted()) {
            System.err.println("请求处理异常（响应已提交，无法改写为 500）: " + e);
            return;
        }
        response.setStatus(500);
        response.setContentType("text/plain; charset=utf-8");
        response.write("500 Internal Server Error: " + e.getMessage());
    }
}