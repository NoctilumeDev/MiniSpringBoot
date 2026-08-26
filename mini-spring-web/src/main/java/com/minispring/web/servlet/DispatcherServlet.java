package com.minispring.web.servlet;

import com.minispring.core.BeanFactory;
import com.minispring.core.BeanFactoryAware;
import com.minispring.core.InitializingBean;
import com.minispring.core.ListableBeanFactory;
import com.minispring.web.http.HttpErrorResponse;
import com.minispring.web.http.HttpRequest;
import com.minispring.web.http.HttpResponse;
import com.minispring.web.http.HttpStatusException;
import com.minispring.web.mvc.HandlerAdapter;
import com.minispring.web.mvc.HandlerMapping;
import com.minispring.web.mvc.HandlerMethod;
import com.minispring.web.resource.StaticResourceHandler;
import com.minispring.web.server.HttpHandler;

/**
 * 前端控制器：所有请求的统一入口，只做一件事——「分发」。
 *
 * <p>找到处理器 → 交给适配器执行并写响应；未命中映射则尝试静态资源，再不行返回 404；
 * 显式 HTTP/参数错误映射为 4xx，其余服务端异常统一转成脱敏 500。
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
            // 会绕过这里导致连接层无 HTTP 响应；Throwable 进入统一 4xx/5xx 边界。
            writeError(response, e);
        }
    }

    private void writeNotFound(HttpResponse response) {
        response.setStatus(404);
        response.setContentType("text/plain; charset=utf-8");
        response.write("404 Not Found");
    }

    private void writeError(HttpResponse response, Throwable e) {
        // 响应头已发出时不能改写状态码或追加错误体，只记录错误并保持已提交响应不变。
        if (response.isCommitted()) {
            System.err.println("请求处理异常（响应已提交，无法改写为错误状态）；类型="
                    + e.getClass().getName());
            return;
        }
        int status = resolveStatus(e);
        response.setStatus(status);
        response.setContentType("text/plain; charset=utf-8");
        if (status >= 500) {
            // 服务端异常细节只进服务端诊断面；对外响应保持稳定，避免泄露 SQL、路径、密钥或实现类名。
            System.err.println("请求处理异常，已返回通用 " + status + "；类型="
                    + e.getClass().getName());
        }
        response.write(HttpErrorResponse.body(status, e.getMessage()));
    }

    /**
     * 异常 → HTTP 状态码的内建映射：
     * <ul>
     *   <li>{@link HttpStatusException} → 自带状态码（资源缺失 404、请求体超限 413 等）；</li>
     *   <li>{@link IllegalArgumentException} → 400（参数/校验类错误是客户端的锅）；</li>
     *   <li>其余（含 {@code IllegalStateException}、{@code DataAccessException}）→ 500
     *       ——业务规则冲突与基础设施故障维持「服务器错误」口径，不与客户端错误混淆。</li>
     * </ul>
     * 包级可见：供同包单测直接断言映射结果（约束性锚点）。
     */
    int resolveStatus(Throwable e) {
        if (e instanceof HttpStatusException statusException) {
            return statusException.getStatus();
        }
        if (e instanceof IllegalArgumentException) {
            return 400;
        }
        return 500;
    }

}
