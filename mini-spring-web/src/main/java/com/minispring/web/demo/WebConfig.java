package com.minispring.web.demo;

import com.minispring.context.annotation.Bean;
import com.minispring.context.annotation.ComponentScan;
import com.minispring.context.annotation.Configuration;
import com.minispring.web.mvc.RequestMappingHandlerAdapter;
import com.minispring.web.mvc.RequestMappingHandlerMapping;
import com.minispring.web.servlet.DispatcherServlet;

/**
 * Web 入口配置：扫描本包（Controller），并把 MVC 三大基础设施 Bean 注册进容器。
 */
@Configuration
@ComponentScan
public class WebConfig {

    @Bean
    public RequestMappingHandlerMapping requestMappingHandlerMapping() {
        return new RequestMappingHandlerMapping();
    }

    @Bean
    public RequestMappingHandlerAdapter requestMappingHandlerAdapter() {
        return new RequestMappingHandlerAdapter();
    }

    @Bean
    public DispatcherServlet dispatcherServlet() {
        return new DispatcherServlet();
    }
}