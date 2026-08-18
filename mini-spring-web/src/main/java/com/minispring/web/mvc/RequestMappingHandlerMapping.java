package com.minispring.web.mvc;

import com.minispring.core.BeanDefinitionRegistry;
import com.minispring.core.BeanFactory;
import com.minispring.core.BeanFactoryAware;
import com.minispring.core.InitializingBean;
import com.minispring.core.ListableBeanFactory;
import com.minispring.web.http.HttpMethod;
import com.minispring.web.http.HttpRequest;
import com.minispring.web.mvc.annotation.Controller;
import com.minispring.web.mvc.annotation.GetMapping;
import com.minispring.web.mvc.annotation.PostMapping;
import com.minispring.web.mvc.annotation.RequestMapping;
import com.minispring.web.mvc.annotation.RestController;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 基于 {@code @RequestMapping}/{@code @GetMapping}/{@code @PostMapping} 的处理器映射。
 *
 * <p>在容器初始化阶段把所有 {@code @Controller}/{@code @RestController} 的映射方法
 * 登记成一张「方法 + 路径」表，运行时据此做两级匹配：先精确匹配，再走路径模板（{@code {id}}）。
 */
public class RequestMappingHandlerMapping implements HandlerMapping, BeanFactoryAware, InitializingBean {

    public static final String URI_VARIABLES = "com.minispring.web.uriVariables";

    private ListableBeanFactory beanFactory;
    private BeanDefinitionRegistry registry;
    private final List<MappingRegistration> registrations = new ArrayList<>();

    @Override
    public void setBeanFactory(BeanFactory beanFactory) {
        this.beanFactory = (ListableBeanFactory) beanFactory;
        this.registry = (BeanDefinitionRegistry) beanFactory;
    }

    @Override
    public void afterPropertiesSet() {
        for (String beanName : registry.getBeanDefinitionNames()) {
            Class<?> beanClass = registry.getBeanDefinition(beanName).getBeanClass();
            if (beanClass != null && isController(beanClass)) {
                registerController(beanFactory.getBean(beanName), beanClass);
            }
        }
    }

    private boolean isController(Class<?> clazz) {
        return clazz.isAnnotationPresent(Controller.class) || clazz.isAnnotationPresent(RestController.class);
    }

    private void registerController(Object controller, Class<?> clazz) {
        String basePath = "";
        RequestMapping classMapping = clazz.getAnnotation(RequestMapping.class);
        if (classMapping != null && !classMapping.value().isEmpty()) {
            basePath = classMapping.value();
        }
        for (Method method : clazz.getDeclaredMethods()) {
            String path = null;
            HttpMethod[] httpMethods = null;
            RequestMapping rm = method.getAnnotation(RequestMapping.class);
            if (rm != null) {
                path = !rm.value().isEmpty() ? rm.value() : rm.path();
                httpMethods = rm.method();
            } else {
                GetMapping gm = method.getAnnotation(GetMapping.class);
                if (gm != null) {
                    path = gm.value();
                    httpMethods = new HttpMethod[]{HttpMethod.GET};
                } else {
                    PostMapping pm = method.getAnnotation(PostMapping.class);
                    if (pm != null) {
                        path = pm.value();
                        httpMethods = new HttpMethod[]{HttpMethod.POST};
                    }
                }
            }
            if (path == null) {
                continue;
            }
            registrations.add(new MappingRegistration(httpMethods, combine(basePath, path),
                    new HandlerMethod(controller, method)));
        }
    }

    /** 拼接类级前缀与方法级路径，统一成以 / 开头的完整路径。 */
    private String combine(String base, String child) {
        String b = base.isEmpty() ? "" : (base.endsWith("/") ? base.substring(0, base.length() - 1) : base);
        String c = child.isEmpty() ? "" : (child.startsWith("/") ? child : "/" + child);
        if (b.isEmpty()) {
            return c.isEmpty() ? "/" : c;
        }
        return c.isEmpty() ? b : b + c;
    }

    @Override
    public HandlerMethod getHandler(HttpRequest request) {
        String path = request.getPath();
        HttpMethod reqMethod = HttpMethod.resolve(request.getMethod());

        // 1) 精确匹配：方法 + 路径完全一致
        for (MappingRegistration r : registrations) {
            if (r.matchesMethod(reqMethod) && r.path.equals(path)) {
                request.setAttribute(URI_VARIABLES, new LinkedHashMap<String, String>());
                return r.handlerMethod;
            }
        }
        // 2) 路径模板匹配：如 /users/42 命中 /users/{id}
        for (MappingRegistration r : registrations) {
            if (!r.matchesMethod(reqMethod)) {
                continue;
            }
            Map<String, String> variables = matchTemplate(r.path, path);
            if (variables != null) {
                request.setAttribute(URI_VARIABLES, variables);
                return r.handlerMethod;
            }
        }
        return null;
    }

    /** 模板匹配：段数相同、静态段相等、{xxx} 段捕获为变量。 */
    private Map<String, String> matchTemplate(String pattern, String path) {
        String[] p = pattern.split("/");
        String[] a = path.split("/");
        if (p.length != a.length) {
            return null;
        }
        Map<String, String> variables = new LinkedHashMap<>();
        for (int i = 0; i < p.length; i++) {
            String seg = p[i];
            if (seg.startsWith("{") && seg.endsWith("}")) {
                variables.put(seg.substring(1, seg.length() - 1), a[i]);
            } else if (!seg.equals(a[i])) {
                return null;
            }
        }
        return variables;
    }

    private static final class MappingRegistration {
        final HttpMethod[] httpMethods; // 空数组表示不限制方法
        final String path;
        final HandlerMethod handlerMethod;

        MappingRegistration(HttpMethod[] httpMethods, String path, HandlerMethod handlerMethod) {
            this.httpMethods = httpMethods;
            this.path = path;
            this.handlerMethod = handlerMethod;
        }

        boolean matchesMethod(HttpMethod reqMethod) {
            if (httpMethods == null || httpMethods.length == 0) {
                return true;
            }
            for (HttpMethod m : httpMethods) {
                if (m == reqMethod) {
                    return true;
                }
            }
            return false;
        }
    }
}