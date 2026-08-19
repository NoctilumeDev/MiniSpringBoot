package com.minispring.web.mvc;

import com.minispring.core.BeanDefinitionRegistry;
import com.minispring.core.BeanFactory;
import com.minispring.core.BeanFactoryAware;
import com.minispring.core.InitializingBean;
import com.minispring.core.ListableBeanFactory;
import com.minispring.web.http.HttpMethod;
import com.minispring.web.http.HttpRequest;
import com.minispring.web.mvc.annotation.Controller;
import com.minispring.web.mvc.annotation.RequestMapping;
import com.minispring.web.mvc.annotation.RestController;

import java.lang.annotation.Annotation;
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

    /** L7：沿父类链收集方法（跳过桥接/合成）——基类声明的 handler 方法同样注册路由。 */
    private List<Method> collectMethods(Class<?> clazz) {
        List<Method> methods = new ArrayList<>();
        for (Class<?> current = clazz; current != null && current != Object.class; current = current.getSuperclass()) {
            for (Method method : current.getDeclaredMethods()) {
                if (!method.isBridge() && !method.isSynthetic()) {
                    methods.add(method);
                }
            }
        }
        return methods;
    }

    private void registerController(Object controller, Class<?> clazz) {
        String basePath = "";
        RequestMapping classMapping = clazz.getAnnotation(RequestMapping.class);
        // N2（M0-M9 复审）：类级与方法级对称——value/path 互为别名，此前类级只读 value()，
        // @RequestMapping(path = "/x") 的前缀静默丢失
        if (classMapping != null) {
            basePath = !classMapping.value().isEmpty() ? classMapping.value() : classMapping.path();
        }
        for (Method method : collectMethods(clazz)) {
            String path = null;
            HttpMethod[] httpMethods = null;
            // 1) 直接标注 @RequestMapping
            RequestMapping rm = method.getAnnotation(RequestMapping.class);
            if (rm != null) {
                path = !rm.value().isEmpty() ? rm.value() : rm.path();
                httpMethods = rm.method();
            } else {
                // 2) 派生映射注解（M8 修复：不再硬编码枚举 Get/PostMapping——Put/Delete 及自定义
                //    @XxxMapping 统一走「注解自身携带元 @RequestMapping」路径：路径取派生注解的
                //    value()，HTTP 方法取元 @RequestMapping 的 method()）
                for (Annotation ann : method.getAnnotations()) {
                    RequestMapping meta = ann.annotationType().getAnnotation(RequestMapping.class);
                    if (meta == null) {
                        continue;
                    }
                    path = derivedMappingValue(ann);
                    httpMethods = meta.method();
                    break;
                }
            }
            if (path == null) {
                continue;
            }
            String fullPath = combine(basePath, path);
            detectAmbiguous(httpMethods, fullPath, method);
            registrations.add(new MappingRegistration(httpMethods, fullPath,
                    new HandlerMethod(controller, method)));
        }
    }

    /**
     * N1（M0-M9 复审）：同一「方法 + 路径」重复映射启动即报，不再静默取第一个
     * （此前注册顺序依赖 ConcurrentHashMap 遍历序，跨运行不稳定；Spring 启动即抛 Ambiguous mapping）。
     */
    private void detectAmbiguous(HttpMethod[] httpMethods, String fullPath, Method method) {
        for (MappingRegistration r : registrations) {
            if (!r.path.equals(fullPath) || !methodsOverlap(r.httpMethods, httpMethods)) {
                continue;
            }
            throw new IllegalStateException("重复的映射: [" + method + "] 与 [" + r.handlerMethod
                    + "] 都映射到 " + (httpMethods == null || httpMethods.length == 0 ? "任意方法 " : "")
                    + fullPath + "（Ambiguous mapping）");
        }
    }

    /** 两组 HTTP 方法是否存在交集；任一组为 null/空（不限方法）则视为与任何集合有交集。 */
    private boolean methodsOverlap(HttpMethod[] a, HttpMethod[] b) {
        if (a == null || a.length == 0 || b == null || b.length == 0) {
            return true;
        }
        for (HttpMethod ma : a) {
            for (HttpMethod mb : b) {
                if (ma == mb) {
                    return true;
                }
            }
        }
        return false;
    }

    /** 读取派生映射注解（如 {@code @PutMapping}）的 {@code value()}；注解没有 String value 属性则视为无路径。 */
    private String derivedMappingValue(Annotation annotation) {
        try {
            Object value = annotation.annotationType().getMethod("value").invoke(annotation);
            return (value instanceof String) ? (String) value : null;
        } catch (ReflectiveOperationException | ClassCastException ignored) {
            return null;
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