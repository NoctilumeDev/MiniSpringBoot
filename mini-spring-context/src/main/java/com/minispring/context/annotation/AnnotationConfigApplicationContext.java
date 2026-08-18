package com.minispring.context.annotation;

import com.minispring.context.ApplicationContext;
import com.minispring.core.BeanDefinition;
import com.minispring.core.BeanDefinitionRegistry;
import com.minispring.core.BeanPostProcessor;
import com.minispring.core.BeansException;
import com.minispring.core.ListableBeanFactory;
import com.minispring.core.env.Environment;
import com.minispring.core.env.StandardEnvironment;
import com.minispring.core.support.DefaultListableBeanFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * 注解驱动的应用上下文：一条构造器把「扫描 + 注册 + 条件装配 + 注入 + 预实例化」全部串起来——
 * 这正是 Spring Boot 启动时 {@code ApplicationContext} 背后那套装配流程的「可读版」。
 */
public class AnnotationConfigApplicationContext implements ApplicationContext {

    private final DefaultListableBeanFactory beanFactory;
    private final Environment environment;
    private final ClassPathScanningCandidateComponentProvider scanner;
    private final AnnotationBeanNameGenerator beanNameGenerator;
    private final AnnotatedBeanDefinitionReader reader;
    private final ConditionEvaluator conditionEvaluator;
    private final List<DeferredImport> deferredImports = new ArrayList<>();

    public AnnotationConfigApplicationContext(Class<?>... primarySources) {
        this(new StandardEnvironment(), primarySources);
    }

    public AnnotationConfigApplicationContext(Environment environment, Class<?>... primarySources) {
        this.environment = environment;
        this.beanFactory = new DefaultListableBeanFactory();
        this.beanFactory.setEnvironment(environment);
        this.scanner = new ClassPathScanningCandidateComponentProvider();
        this.beanNameGenerator = new AnnotationBeanNameGenerator();

        // 条件求值上下文：注册中心 / Bean 工厂 / 环境 / 类加载器，一并交给 @Conditional 机制
        ConditionContext conditionContext = new ConditionContext() {
            @Override
            public BeanDefinitionRegistry getRegistry() {
                return beanFactory;
            }

            @Override
            public ListableBeanFactory getBeanFactory() {
                return beanFactory;
            }

            @Override
            public Environment getEnvironment() {
                return environment;
            }

            @Override
            public ClassLoader getClassLoader() {
                return AnnotationConfigApplicationContext.class.getClassLoader();
            }
        };
        this.conditionEvaluator = new ConditionEvaluator(conditionContext);
        this.reader = new AnnotatedBeanDefinitionReader(beanFactory, conditionEvaluator);

        // 内置：@Autowired 注入处理器
        beanFactory.addBeanPostProcessor(new AutowiredAnnotationBeanPostProcessor(beanFactory, beanFactory));

        // 注册入口配置类（会递归处理 @Bean 与 @ComponentScan 与 @Import）
        for (Class<?> source : primarySources) {
            registerConfigClass(source);
        }
        // 用户配置落地后，再执行「延迟导入」（自动配置选择器）：先用户、后自动，@ConditionalOnMissingBean 才能正确回退
        invokeDeferredImports();

        // 预实例化所有单例
        refresh();
    }

    /** 返回当前上下文持有的配置环境（后续 Web 阶段读取 server.port 等会用到）。 */
    public Environment getEnvironment() {
        return environment;
    }

    private void registerConfigClass(Class<?> configClass) {
        // 类级条件不命中，整棵配置类（含 @Bean / 组件扫描 / 导入）都不注册
        if (conditionEvaluator.shouldSkip(SimpleAnnotationMetadata.of(configClass))) {
            return;
        }
        // 1) 配置类本身也是 Bean
        String configBeanName = registerComponent(configClass);
        // 2) 配置类里的 @Bean 方法（内部还会做方法级条件判断）
        reader.registerBeanMethods(configClass, configBeanName);
        // 3) @ComponentScan 扫描
        processComponentScan(configClass);
        // 4) @Import（含 @EnableAutoConfiguration 触发的自动配置导入）
        processImports(configClass);
    }

    private void processComponentScan(Class<?> configClass) {
        ComponentScan componentScan = configClass.getAnnotation(ComponentScan.class);
        String[] basePackages = (componentScan == null || componentScan.basePackages().length == 0)
                ? new String[]{configClass.getPackageName()}
                : componentScan.basePackages();
        for (String basePackage : basePackages) {
            for (Class<?> candidate : scanner.findCandidateComponents(basePackage)) {
                if (conditionEvaluator.shouldSkip(SimpleAnnotationMetadata.of(candidate))) {
                    continue;
                }
                registerComponent(candidate);
            }
        }
    }

    private void processImports(Class<?> configClass) {
        Import importAnnotation = SimpleAnnotationMetadata.findAnnotation(configClass, Import.class);
        if (importAnnotation == null) {
            return;
        }
        for (Class<?> imported : importAnnotation.value()) {
            if (DeferredImportSelector.class.isAssignableFrom(imported)) {
                // 延迟到用户配置全部落地后再执行，见 invokeDeferredImports()
                deferredImports.add(new DeferredImport(imported, SimpleAnnotationMetadata.of(configClass)));
            } else if (ImportSelector.class.isAssignableFrom(imported)) {
                registerImports(imported, SimpleAnnotationMetadata.of(configClass));
            } else {
                registerConfigClass(imported);
            }
        }
    }

    private void invokeDeferredImports() {
        // 处理过程中可能又派生出新的延迟导入，用索引驱动（而非 for-each）以免并发修改
        int index = 0;
        while (index < deferredImports.size()) {
            DeferredImport deferred = deferredImports.get(index++);
            registerImports(deferred.selectorClass, deferred.metadata);
        }
    }

    private void registerImports(Class<?> selectorClass, AnnotatedTypeMetadata importingMetadata) {
        ImportSelector selector = (ImportSelector) instantiate(selectorClass);
        String[] importedClassNames = selector.selectImports(importingMetadata);
        for (String className : importedClassNames) {
            try {
                registerConfigClass(Class.forName(className, true, AnnotationConfigApplicationContext.class.getClassLoader()));
            } catch (ClassNotFoundException e) {
                throw new BeansException("导入的配置类[" + className + "]不在 classpath 上", e);
            }
        }
    }

    private Object instantiate(Class<?> clazz) {
        try {
            return clazz.getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException e) {
            throw new BeansException("实例化[" + clazz.getName() + "]失败", e);
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

    /** 一次「延迟导入」的待办：选择器 + 触发它的导入类标注快照。 */
    private static final class DeferredImport {
        final Class<?> selectorClass;
        final AnnotatedTypeMetadata metadata;

        DeferredImport(Class<?> selectorClass, AnnotatedTypeMetadata metadata) {
            this.selectorClass = selectorClass;
            this.metadata = metadata;
        }
    }
}