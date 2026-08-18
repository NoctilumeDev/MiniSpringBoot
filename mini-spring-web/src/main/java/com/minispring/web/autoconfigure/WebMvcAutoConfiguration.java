package com.minispring.web.autoconfigure;

import com.minispring.autoconfigure.condition.ConditionalOnClass;
import com.minispring.autoconfigure.condition.ConditionalOnMissingBean;
import com.minispring.context.annotation.Bean;
import com.minispring.context.annotation.Configuration;
import com.minispring.web.mvc.RequestMappingHandlerAdapter;
import com.minispring.web.mvc.RequestMappingHandlerMapping;
import com.minispring.web.servlet.DispatcherServlet;

/**
 * Web/MVC 自动配置：注入依赖了 {@code mini-spring-web} 时，自动把 MVC 三大基础设施 Bean 装配进容器。
 *
 * <p>全部用 {@link ConditionalOnMissingBean} 兜底：用户自己已定义同类型 Bean 时回退，保证「用户优先、自动兜底」。
 */
@Configuration
@ConditionalOnClass(DispatcherServlet.class)
public class WebMvcAutoConfiguration {

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
}