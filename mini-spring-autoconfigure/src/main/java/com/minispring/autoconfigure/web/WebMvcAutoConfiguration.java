package com.minispring.autoconfigure.web;

import com.minispring.autoconfigure.condition.ConditionalOnClass;
import com.minispring.autoconfigure.condition.ConditionalOnMissingBean;
import com.minispring.context.Lifecycle;
import com.minispring.context.annotation.Bean;
import com.minispring.context.annotation.Configuration;
import com.minispring.core.BeanFactory;
import com.minispring.core.BeanFactoryAware;
import com.minispring.core.EnvironmentAware;
import com.minispring.core.ListableBeanFactory;
import com.minispring.core.SingletonBeanRegistry;
import com.minispring.core.env.Environment;
import com.minispring.web.mvc.RequestMappingHandlerAdapter;
import com.minispring.web.mvc.RequestMappingHandlerMapping;
import com.minispring.web.server.SunHttpServer;
import com.minispring.web.server.WebServer;
import com.minispring.web.servlet.DispatcherServlet;

/**
 * Web/MVC 自动配置：classpath 上有 {@code mini-spring-web} 时，自动把 MVC 三大基础设施 Bean
 * 与「内嵌服务器生命周期组件」装配进容器。
 *
 * <p>A-1 归位 + D45 收口：类级条件用 {@code name} 字符串形式（理由见 {@link AopAutoConfiguration}），
 * web jar 缺失时注解解析不碰类字面量，条件安全返回 false → 整个配置类跳过、不启动内嵌服务器。
 *
 * <p>内嵌服务器的启动/停止由 {@link EmbeddedServerBootstrap}（实现 context 层 {@link Lifecycle}）承担：
 * 启动器（boot）只驱动 Lifecycle 接口，不引用任何 web 具体类——boot 因此可以不依赖 web
 * （与 {@code spring-boot} 只认 {@code Lifecycle}、不认 Tomcat 同构）。
 *
 * <p>全部用 {@link ConditionalOnMissingBean} 兜底：用户自己已定义同类型 Bean 时回退，保证「用户优先、自动兜底」。
 */
@Configuration
@ConditionalOnClass(name = "com.minispring.web.servlet.DispatcherServlet")
public class WebMvcAutoConfiguration {

    /** 内嵌服务器注册进容器的单例名，业务可由此拿到 {@link WebServer} 做停机等控制。 */
    public static final String WEB_SERVER_BEAN_NAME = "webServer";

    @Bean
    @ConditionalOnMissingBean
    public RequestMappingHandlerMapping requestMappingHandlerMapping() {
        return new RequestMappingHandlerMapping();
    }

    @Bean
    @ConditionalOnMissingBean
    public RequestMappingHandlerAdapter requestMappingHandlerAdapter() {
        return new RequestMappingHandlerAdapter();
    }

    @Bean
    @ConditionalOnMissingBean
    public DispatcherServlet dispatcherServlet() {
        return new DispatcherServlet();
    }

    @Bean
    @ConditionalOnMissingBean
    public EmbeddedServerBootstrap embeddedServerBootstrap() {
        return new EmbeddedServerBootstrap();
    }

    /**
     * 内嵌服务器生命周期：start 时取容器里唯一的 {@link DispatcherServlet}，读 {@code server.port}
     * （缺省 9090）启动 {@link SunHttpServer}，并把服务器注册为 {@code webServer} 运行期单例；stop 停服务器。
     *
     * <p>放在 autoconfigure（而非 boot）：本类引用 web 具体类，只有 web 在 classpath 上才会随本配置类
     * 装配——「裁掉 web 模块 → 本组件不存在 → 不启动服务器」自然成立。依赖注入走 Aware 回调
     * （BeanFactoryAware/EnvironmentAware 在初始化阶段自动注入），与 {@code @Bean} 方法产物自洽。
     */
    public static class EmbeddedServerBootstrap implements Lifecycle, BeanFactoryAware, EnvironmentAware {

        static final int DEFAULT_PORT = 9090;

        private ListableBeanFactory beanFactory;
        private SingletonBeanRegistry singletonRegistry;
        private Environment environment;
        private WebServer webServer;

        @Override
        public void setBeanFactory(BeanFactory beanFactory) {
            this.beanFactory = (ListableBeanFactory) beanFactory;
            this.singletonRegistry = (SingletonBeanRegistry) beanFactory;
        }

        @Override
        public void setEnvironment(Environment environment) {
            this.environment = environment;
        }

        @Override
        public void start() {
            String[] names = beanFactory.getBeanNamesForType(DispatcherServlet.class);
            if (names.length == 0) {
                return;
            }
            DispatcherServlet servlet = beanFactory.getBean(names[0], DispatcherServlet.class);
            String portValue = environment.getProperty("server.port");
            // server.port 非数字时抛出携带配置值的可读错误。
            int port = (portValue == null || portValue.isEmpty()) ? DEFAULT_PORT : parsePort(portValue.trim());
            this.webServer = new SunHttpServer(servlet);
            this.webServer.start(port);
            singletonRegistry.registerSingleton(WEB_SERVER_BEAN_NAME, webServer);
            System.out.println("  [http] 内嵌服务器已启动: http://localhost:" + port);
        }

        private static int parsePort(String value) {
            try {
                return Integer.parseInt(value);
            } catch (NumberFormatException e) {
                throw new IllegalStateException("server.port 配置了非数字值: \"" + value + "\"（期望整数端口）", e);
            }
        }

        @Override
        public void stop() {
            if (webServer != null) {
                webServer.stop();
            }
        }
    }
}
