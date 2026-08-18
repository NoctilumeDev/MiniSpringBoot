package com.minispring.context.annotation;

import com.minispring.context.ApplicationContext;
import com.minispring.core.BeanDefinition;
import com.minispring.core.BeanPostProcessor;
import com.minispring.core.support.DefaultListableBeanFactory;

/**
 * 注解驱动的应用上下文：一条构造器把「扫描 + 注册 + 注入 + 预实例化」全部串起来——
 * 这正是 Spring Boot 启动时 {@code ApplicationContext} 背后那套装配流程的「可读版」。
 */
public class AnnotationConfigApplicationContext implements ApplicationContext {

    private final DefaultListableBeanFactory beanFactory;
    private final ClassPathScanningCandidateComponentProvider scanner;
    private final AnnotationBeanNameGenerator beanNameGenerator;
    private final AnnotatedBeanDefinitionReader reader;

    public AnnotationConfigApplicationContext(Class<?>... primarySources) {
        this.beanFactory = new DefaultListableBeanFactory();
        this.scanner = new ClassPathScanningCandidateComponentProvider();
        this.beanNameGenerator = new AnnotationBeanNameGenerator();
        this.reader = new AnnotatedBeanDefinitionReader(beanFactory);

        // 内置：@Autowired 注入处理器
        beanFactory.addBeanPostProcessor(new AutowiredAnnotationBeanPostProcessor(beanFactory, beanFactory));

        // 注册入口配置类（会递归处理 @Bean 与 @ComponentScan）
        for (Class<?> source : primarySources) {
            registerConfigClass(source);
        }
        // 预实例化所有单例
        refresh();
    }

    private void registerConfigClass(Class<?> configClass) {
        // 1) 配置类本身也是 Bean
        String configBeanName = registerComponent(configClass);
        // 2) 配置类里的 @Bean 方法
        reader.registerBeanMethods(configClass, configBeanName);
        // 3) @ComponentScan 扫描
        ComponentScan componentScan = configClass.getAnnotation(ComponentScan.class);
        String[] basePackages = (componentScan == null || componentScan.basePackages().length == 0)
                ? new String[]{configClass.getPackageName()}
                : componentScan.basePackages();
        for (String basePackage : basePackages) {
            for (Class<?> candidate : scanner.findCandidateComponents(basePackage)) {
                registerComponent(candidate);
            }
        }
    }

    private String registerComponent(Class<?> clazz) {
        String beanName = beanNameGenerator.generateBeanName(clazz);
        // 入口配置类本身也会被 @ComponentScan 扫到，避免同名重复注册
        if (beanFactory.containsBeanDefinition(beanName)) {
            return beanName;
        }
        BeanDefinition bd = new BeanDefinition(clazz);
        if (clazz.isAnnotationPresent(Scope.class)) {
            bd.setScope(clazz.getAnnotation(Scope.class).value());
        }
        beanFactory.registerBeanDefinition(beanName, bd);
        return beanName;
    }

    /** 刷新：先把基础设施（BeanPostProcessor）就位，再预实例化其余单例，顺序不能颠倒。 */
    private void refresh() {
        // 1) BeanPostProcessor 必须先实例化并注册生效（AOP 代理器等基础设施）
        for (String name : beanFactory.getBeanDefinitionNames()) {
            Class<?> beanClass = beanFactory.getBeanDefinition(name).getBeanClass();
            if (BeanPostProcessor.class.isAssignableFrom(beanClass)) {
                beanFactory.getBean(name);
            }
        }
        // 2) 再预实例化其余单例
        for (String name : beanFactory.getBeanDefinitionNames()) {
            if (beanFactory.getBeanDefinition(name).isSingleton()) {
                beanFactory.getBean(name);
            }
        }
    }

    // ----- 委托给底层工厂 -----

    @Override
    public Object getBean(String name) {
        return beanFactory.getBean(name);
    }

    @Override
    public <T> T getBean(String name, Class<T> requiredType) {
        return beanFactory.getBean(name, requiredType);
    }

    @Override
    public boolean containsBean(String name) {
        return beanFactory.containsBean(name);
    }

    @Override
    public String[] getBeanNamesForType(Class<?> type) {
        return beanFactory.getBeanNamesForType(type);
    }

    @Override
    public void close() {
        beanFactory.close();
    }
}