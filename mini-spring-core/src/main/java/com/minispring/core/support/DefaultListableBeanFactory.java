package com.minispring.core.support;

import com.minispring.core.BeanDefinition;
import com.minispring.core.BeanDefinitionRegistry;
import com.minispring.core.BeanFactoryAware;
import com.minispring.core.BeanPostProcessor;
import com.minispring.core.BeansException;
import com.minispring.core.DisposableBean;
import com.minispring.core.EnvironmentAware;
import com.minispring.core.InitializingBean;
import com.minispring.core.InstantiationAwareBeanPostProcessor;
import com.minispring.core.ListableBeanFactory;
import com.minispring.core.ObjectFactory;
import com.minispring.core.SmartInstantiationAwareBeanPostProcessor;
import com.minispring.core.PropertyValue;
import com.minispring.core.SingletonBeanRegistry;
import com.minispring.core.env.Environment;

import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 默认的 Bean 工厂实现（放在 support 子包，对外只暴露 {@link BeanFactory} 等接口）。
 *
 * <p>负责：BeanDefinition 注册、Bean 创建、生命周期（实例化 → 填充 → 初始化 → 销毁），
 * 以及用「三级缓存」破解循环依赖。
 *
 * <h3>三级缓存</h3>
 * <ul>
 *   <li>一级 {@code singletonObjects}      ：已完全就绪的单例</li>
 *   <li>二级 {@code earlySingletonObjects} ：被提前暴露的半成品（将来可能已代理）</li>
 *   <li>三级 {@code singletonFactories}    ：能产出半成品的工厂</li>
 * </ul>
 */
public class DefaultListableBeanFactory implements ListableBeanFactory, BeanDefinitionRegistry, SingletonBeanRegistry, AutoCloseable {

    // 图纸仓库
    private final Map<String, BeanDefinition> beanDefinitionMap = new ConcurrentHashMap<>();

    // 三级缓存
    private final Map<String, Object> singletonObjects = new ConcurrentHashMap<>();
    private final Map<String, Object> earlySingletonObjects = new ConcurrentHashMap<>();
    private final Map<String, ObjectFactory<?>> singletonFactories = new ConcurrentHashMap<>();

    // 质检员（BeanPostProcessor）
    private final List<BeanPostProcessor> beanPostProcessors = new ArrayList<>();

    // 正在创建中的单例：用于识别「构造器注入卡死型循环依赖」（三级缓存尚未暴露时就折返，直接给出可读错误而非 StackOverflow）
    private final Set<String> currentlyInCreation = ConcurrentHashMap.newKeySet();

    // 配置环境（Environment），由上下文注入；@Value 处理器靠它以占位符查值
    private Environment environment;

    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }

    public void addBeanPostProcessor(BeanPostProcessor processor) {
        this.beanPostProcessors.add(processor);
    }

    // ---------- BeanDefinitionRegistry ----------

    @Override
    public void registerBeanDefinition(String beanName, BeanDefinition beanDefinition) {
        this.beanDefinitionMap.put(beanName, beanDefinition);
    }

    @Override
    public BeanDefinition getBeanDefinition(String beanName) {
        BeanDefinition bd = this.beanDefinitionMap.get(beanName);
        if (bd == null) {
            throw new BeansException("未找到 Bean 定义: " + beanName);
        }
        return bd;
    }

    @Override
    public boolean containsBeanDefinition(String beanName) {
        return this.beanDefinitionMap.containsKey(beanName);
    }

    @Override
    public String[] getBeanDefinitionNames() {
        return this.beanDefinitionMap.keySet().toArray(new String[0]);
    }

    // ---------- BeanFactory ----------

    @Override
    public Object getBean(String name) {
        // 运行期注册的单例（registerSingleton，如启动器放入的 webServer）没有 BeanDefinition，先查一级缓存
        Object registered = singletonObjects.get(name);
        if (registered != null) {
            return registered;
        }
        BeanDefinition bd = getBeanDefinition(name);
        if (!bd.isSingleton()) {
            // prototype：每次新建，不缓存、不参与循环依赖破解、容器不负责销毁
            return createBean(name, bd);
        }

        // 一级缓存命中 → 直接返回已就绪的单例
        Object bean = singletonObjects.get(name);
        if (bean != null) {
            return bean;
        }
        // 二级缓存命中 → 返回提前暴露的半成品
        bean = earlySingletonObjects.get(name);
        if (bean != null) {
            return bean;
        }
        // 三级缓存命中 → 从工厂取出半成品，升级到二级缓存后返回
        ObjectFactory<?> factory = singletonFactories.get(name);
        if (factory != null) {
            bean = factory.getObject();
            earlySingletonObjects.put(name, bean);
            singletonFactories.remove(name);
            return bean;
        }
        // A-4：三级缓存还没暴露就折返创建自己 —— 构造器注入型循环依赖（无法用提前暴露破解），给出可读错误
        if (currentlyInCreation.contains(name)) {
            throw new BeansException("检测到无法提前暴露的循环依赖（构造器注入或 prototype 作用域）: " + name
                    + " —— 请改用「单例 + 字段/方法注入」，或调整依赖方向");
        }
        // 都没有 → 完整创建
        return createBean(name, bd);
    }

    @Override
    public <T> T getBean(String name, Class<T> requiredType) {
        Object bean = getBean(name);
        if (!requiredType.isInstance(bean)) {
            throw new BeansException("Bean[" + name + "] 类型不匹配");
        }
        return requiredType.cast(bean);
    }

    @Override
    public boolean containsBean(String name) {
        // 运行期手动注册的单例（registerSingleton，如 webServer）没有 BeanDefinition，须同时查一级缓存
        return singletonObjects.containsKey(name) || containsBeanDefinition(name);
    }

    @Override
    public String[] getBeanNamesForType(Class<?> type) {
        List<String> matched = new ArrayList<>();
        for (Map.Entry<String, BeanDefinition> entry : beanDefinitionMap.entrySet()) {
            Class<?> beanClass = entry.getValue().getBeanClass();
            if (beanClass != null && type.isAssignableFrom(beanClass)) {
                matched.add(entry.getKey());
            }
        }
        // 运行期手动注册的单例（registerSingleton）无 BeanDefinition，按实例类型补查（与 containsBean 对称）
        for (Map.Entry<String, Object> entry : singletonObjects.entrySet()) {
            if (!beanDefinitionMap.containsKey(entry.getKey()) && entry.getValue() != null
                    && type.isAssignableFrom(entry.getValue().getClass())) {
                matched.add(entry.getKey());
            }
        }
        return matched.toArray(new String[0]);
    }

    /** 覆盖（或新增）某个单例：供后处理器（如 AOP 代理器）在 Bean 创建完成后补代理回填一级缓存。 */
    @Override
    public void registerSingleton(String beanName, Object singletonObject) {
        singletonObjects.put(beanName, singletonObject);
        earlySingletonObjects.remove(beanName);
        singletonFactories.remove(beanName);
    }

    // ---------- 创建主流程 ----------

    private Object createBean(String beanName, BeanDefinition bd) {
        // TEMP-PROBE（M8 调试用，验收后撤）
        System.out.println("[createBean] " + beanName + " inCreation=" + currentlyInCreation);
        // 同名单例折返（构造器注入循环）或同名 prototype 循环：提前暴露救不了，直接给出可读错误而非 StackOverflow
        if (!currentlyInCreation.add(beanName)) {
            throw new BeansException("检测到无法提前暴露的循环依赖（构造器注入或 prototype 作用域）: " + beanName
                    + " —— 请改用「单例 + 字段/方法注入」，或调整依赖方向");
        }
        try {
            return doCreateBean(beanName, bd);
        } finally {
            currentlyInCreation.remove(beanName);
        }
    }

    private Object doCreateBean(String beanName, BeanDefinition bd) {
        Object bean = instantiate(beanName, bd);
        // 提前暴露的引用需单独捕获，避免 bean 后续被重新赋值导致 lambda 无法引用
        Object exposed = bean;

        if (bd.isSingleton()) {
            // 提前暴露：把「能产出半成品」的工厂放进三级缓存
            ObjectFactory<?> factory = () -> getEarlyBeanReference(beanName, exposed);
            singletonFactories.put(beanName, factory);
        }

        populateBean(beanName, bd, bean);
        bean = initializeBean(beanName, bd, bean);

        // 循环依赖时，提前暴露的可能是代理；若初始化后仍是原对象，则采用提前暴露的代理作最终单例（B2）
        if (bd.isSingleton()) {
            Object earlyReference = earlySingletonObjects.get(beanName);
            if (earlyReference != null && earlyReference != bean) {
                bean = earlyReference;
            }
        }

        // 把「BeanPostProcessor 类型的单例 Bean」自动注册进后处理器链——AOP 代理器就靠这个机制生效。
        // B-3：prototype 作用域的 BPP 不注册——它每次 getBean 都是新实例，注册会让处理器链无限膨胀
        if (bd.isSingleton() && bean instanceof BeanPostProcessor) {
            addBeanPostProcessor((BeanPostProcessor) bean);
        }

        if (bd.isSingleton()) {
            singletonObjects.put(beanName, bean);
            earlySingletonObjects.remove(beanName);
            singletonFactories.remove(beanName);
        }
        return bean;
    }

    /** 获取提前引用：委托 {@link SmartInstantiationAwareBeanPostProcessor}（如 AOP 代理器）提前生成代理。 */
    private Object getEarlyBeanReference(String beanName, Object bean) {
        Object exposed = bean;
        for (BeanPostProcessor processor : beanPostProcessors) {
            if (processor instanceof SmartInstantiationAwareBeanPostProcessor) {
                exposed = ((SmartInstantiationAwareBeanPostProcessor) processor)
                        .getEarlyBeanReference(exposed, beanName);
            }
        }
        return exposed;
    }

    private Object instantiate(String beanName, BeanDefinition bd) {
        if (bd.isFactoryMethod()) {
            return instantiateUsingFactoryMethod(beanName, bd);
        }
        // A-4（D3 收口）：@Autowired 构造器注入 —— BPP 选出候选构造器，容器解析参数后调用
        Constructor<?> candidate = determineAutowiredConstructor(beanName, bd);
        if (candidate != null) {
            return instantiateUsingConstructor(beanName, candidate);
        }
        try {
            Constructor<?> constructor = bd.getBeanClass().getDeclaredConstructor();
            // D43：非 public 类（包私有配置类/组件）同样允许实例化，与 Spring 对齐
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (Exception e) {
            throw new BeansException("实例化 Bean[" + beanName + "] 失败", e);
        }
    }

    /** 询问各 InstantiationAwareBeanPostProcessor：这个类有没有标注注入注解的构造器。 */
    private Constructor<?> determineAutowiredConstructor(String beanName, BeanDefinition bd) {
        for (BeanPostProcessor processor : beanPostProcessors) {
            if (!(processor instanceof InstantiationAwareBeanPostProcessor)) {
                continue;
            }
            Constructor<?>[] candidates =
                    ((InstantiationAwareBeanPostProcessor) processor).determineCandidateConstructors(bd.getBeanClass(), beanName);
            if (candidates == null || candidates.length == 0) {
                continue;
            }
            if (candidates.length > 1) {
                throw new BeansException("Bean[" + beanName + "] 标注了多个 @Autowired 构造器，请只保留一个");
            }
            return candidates[0];
        }
        return null;
    }

    private Object instantiateUsingConstructor(String beanName, Constructor<?> constructor) {
        constructor.setAccessible(true);
        Object[] args = resolveArgs(constructor.getParameters(), beanName);
        try {
            return constructor.newInstance(args);
        } catch (Exception e) {
            throw new BeansException("构造器[" + constructor + "]实例化 Bean[" + beanName + "] 失败", e);
        }
    }

    /**
     * 由 @Bean 方法生产 Bean：先拿到工厂 Bean，再调用其工厂方法。
     *
     * <p>D22：工厂方法可带参数——每个参数按「{@code @Qualifier} 名 → 唯一类型 → {@code @Primary}」从容器解析。
     * 为了不让 core 反向依赖 context 的注解，{@code @Qualifier} 用全限定名字符串反射识别。
     */
    private Object instantiateUsingFactoryMethod(String beanName, BeanDefinition bd) {
        Object factoryBean = getBean(bd.getFactoryBeanName());
        try {
            Method method = findFactoryMethod(factoryBean.getClass(), bd.getFactoryMethodName());
            Object[] args = resolveFactoryMethodArgs(method);
            return method.invoke(factoryBean, args);
        } catch (Exception e) {
            throw new BeansException("工厂方法[" + bd.getFactoryMethodName() + "]实例化 Bean[" + beanName + "] 失败", e);
        }
    }

    private Method findFactoryMethod(Class<?> factoryClass, String methodName) {
        // D43：先扫本类声明的方法（含非 public 的包私有 @Bean），再回退 public 方法（含接口默认方法）
        for (Method method : factoryClass.getDeclaredMethods()) {
            if (method.getName().equals(methodName)) {
                method.setAccessible(true);
                return method;
            }
        }
        for (Method method : factoryClass.getMethods()) {
            if (method.getName().equals(methodName)) {
                return method;
            }
        }
        throw new BeansException("工厂方法[" + methodName + "]不存在");
    }

    private Object[] resolveFactoryMethodArgs(Method method) {
        return resolveArgs(method.getParameters(), null);
    }

    /** 构造器 / @Bean 工厂方法共用的参数解析：@Qualifier 限定名 → beanName → 唯一类型 → @Primary；支持参数级 @Autowired(required=false)。 */
    private Object[] resolveArgs(Parameter[] parameters, String beanName) {
        Object[] args = new Object[parameters.length];
        for (int i = 0; i < parameters.length; i++) {
            args[i] = resolveArg(parameters[i], beanName);
        }
        return args;
    }

    private Object resolveArg(Parameter parameter, String beanName) {
        String qualifier = findQualifierValue(parameter);
        if (qualifier != null && !qualifier.isEmpty()) {
            // D34（M8 收口，与 AutowiredAnnotationBeanPostProcessor.resolveByQualifier 对称）：
            // 限定名匹配 BeanDefinition.qualifier（限定名 ≠ beanName 也认）→ 回退 beanName
            return resolveArgByQualifier(qualifier, parameter.getType(), beanName);
        }
        Class<?> type = parameter.getType();
        String[] candidates = getBeanNamesForType(type);
        if (candidates.length == 1) {
            return getBean(candidates[0]);
        }
        if (candidates.length > 1) {
            for (String name : candidates) {
                if (getBeanDefinition(name).isPrimary()) {
                    return getBean(name);
                }
            }
            throw new BeansException("注入参数类型[" + type.getName() + "]有多个候选: " + Arrays.toString(candidates)
                    + "，请用 @Qualifier 或 @Primary 拍板");
        }
        // 无候选：参数级 @Autowired(required=false)（按全限定名识别）允许缺省注入 null；默认 required 报错
        if (isOptionalAutowired(parameter)) {
            return null;
        }
        String where = (beanName == null ? "工厂方法" : "Bean[" + beanName + "]构造器");
        throw new BeansException(where + "的参数类型[" + type.getName() + "]找不到可用 Bean");
    }

    /** D34：参数的限定名裁决——匹配 qualifier 字段优先，回退 beanName，两层都空给可读错误。 */
    private Object resolveArgByQualifier(String qualifier, Class<?> type, String beanName) {
        String where = (beanName == null ? "工厂方法" : "Bean[" + beanName + "]构造器");
        String matched = null;
        for (String name : getBeanNamesForType(type)) {
            if (qualifier.equals(getBeanDefinition(name).getQualifier())) {
                if (matched != null) {
                    throw new BeansException(where + "参数 qualifier=\"" + qualifier + "\" 命中多个 Bean（" + matched + ", " + name + "）");
                }
                matched = name;
            }
        }
        if (matched != null) {
            return getBean(matched);
        }
        if (containsBean(qualifier)) {
            return getBean(qualifier);
        }
        throw new BeansException(where + "参数找不到 qualifier=\"" + qualifier + "\"（既无此限定名，也无此 beanName），类型 " + type.getName());
    }

    /** 反射判断参数是否标了 @Autowired(required=false)（按全限定名，避免 core 反向依赖 context 注解）。 */
    private boolean isOptionalAutowired(Parameter parameter) {
        for (Annotation annotation : parameter.getAnnotations()) {
            if (!annotation.annotationType().getName().equals("com.minispring.context.annotation.Autowired")) {
                continue;
            }
            try {
                Object required = annotation.annotationType().getMethod("required").invoke(annotation);
                return Boolean.FALSE.equals(required);
            } catch (ReflectiveOperationException ignored) {
                return false;
            }
        }
        return false;
    }

    /** 反射读取参数上的 @Qualifier value（按全限定名，避免 core 反向依赖 context 注解）。 */
    private String findQualifierValue(Parameter parameter) {
        for (Annotation annotation : parameter.getAnnotations()) {
            if (!annotation.annotationType().getName().equals("com.minispring.context.annotation.Qualifier")) {
                continue;
            }
            try {
                Object value = annotation.annotationType().getMethod("value").invoke(annotation);
                return value == null ? null : String.valueOf(value);
            } catch (ReflectiveOperationException ignored) {
                return null;
            }
        }
        return null;
    }

    /** 属性填充：先注入 XML 风格的 PropertyValue，再触发注解注入钩子（@Autowired）。 */
    private void populateBean(String beanName, BeanDefinition bd, Object bean) {
        for (PropertyValue pv : bd.getPropertyValues()) {
            Object value = pv.isRef() ? getBean((String) pv.getValue()) : pv.getValue();
            applyPropertyValue(beanName, bean, pv.getName(), value);
        }
        // 注解驱动的字段/构造注入（@Autowired）：在填充阶段、初始化回调之前执行
        for (BeanPostProcessor processor : beanPostProcessors) {
            if (processor instanceof InstantiationAwareBeanPostProcessor) {
                ((InstantiationAwareBeanPostProcessor) processor).postProcessProperties(bean, beanName);
            }
        }
    }

    private void applyPropertyValue(String beanName, Object bean, String name, Object value) {
        try {
            Field field = findField(bean.getClass(), name);
            if (field == null) {
                throw new BeansException("Bean[" + beanName + "] 中找不到字段: " + name);
            }
            field.setAccessible(true);
            field.set(bean, value);
        } catch (IllegalAccessException e) {
            throw new BeansException("注入字段[" + name + "]失败", e);
        }
    }

    private Field findField(Class<?> clazz, String name) {
        Class<?> current = clazz;
        while (current != null && current != Object.class) {
            try {
                return current.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            }
        }
        return null;
    }

    /** 初始化：注入 Aware → before → InitializingBean / initMethod → after。 */
    private Object initializeBean(String beanName, BeanDefinition bd, Object bean) {
        if (bean instanceof BeanFactoryAware) {
            ((BeanFactoryAware) bean).setBeanFactory(this);
        }
        if (bean instanceof EnvironmentAware) {
            ((EnvironmentAware) bean).setEnvironment(environment);
        }
        for (BeanPostProcessor processor : beanPostProcessors) {
            bean = processor.postProcessBeforeInitialization(bean, beanName);
        }

        invokeInitMethods(beanName, bd, bean);

        for (BeanPostProcessor processor : beanPostProcessors) {
            bean = processor.postProcessAfterInitialization(bean, beanName);
        }
        return bean;
    }

    private void invokeInitMethods(String beanName, BeanDefinition bd, Object bean) {
        if (bean instanceof InitializingBean) {
            try {
                ((InitializingBean) bean).afterPropertiesSet();
            } catch (Exception e) {
                throw new BeansException("Bean[" + beanName + "] 初始化回调失败", e);
            }
        }
        String initMethod = bd.getInitMethodName();
        if (initMethod != null && !initMethod.isEmpty()) {
            invokeNoArgMethod(beanName, bean, initMethod);
        }
    }

    private void invokeNoArgMethod(String beanName, Object bean, String methodName) {
        try {
            Method method;
            try {
                // D43：init/destroy 回调允许非 public（与注册侧 getDeclaredMethods 对称）
                method = bean.getClass().getDeclaredMethod(methodName);
                method.setAccessible(true);
            } catch (NoSuchMethodException e) {
                method = bean.getClass().getMethod(methodName);
            }
            method.invoke(bean);
        } catch (Exception e) {
            throw new BeansException("Bean[" + beanName + "] 方法[" + methodName + "] 调用失败", e);
        }
    }

    // ---------- 销毁 ----------

    public void destroySingletons() {
        for (Map.Entry<String, BeanDefinition> entry : beanDefinitionMap.entrySet()) {
            if (entry.getValue().isSingleton()) {
                try {
                    destroyBean(entry.getKey(), singletonObjects.get(entry.getKey()));
                } catch (RuntimeException e) {
                    // M8（V10 前置）：单个 Bean 销毁失败不得中断其余销毁——
                    // 关闭链上一个炸全链停，会掩盖后续 Bean（如连接池）的释放
                    System.err.println("销毁 Bean[" + entry.getKey() + "]失败: " + e);
                }
            }
        }
        singletonObjects.clear();
        earlySingletonObjects.clear();
        singletonFactories.clear();
    }

    @Override
    public void close() {
        destroySingletons();
    }

    private void destroyBean(String beanName, Object bean) {
        if (bean == null) {
            return;
        }
        if (bean instanceof DisposableBean) {
            try {
                ((DisposableBean) bean).destroy();
            } catch (Exception e) {
                throw new BeansException("Bean[" + beanName + "] 销毁回调失败", e);
            }
        }
        BeanDefinition bd = beanDefinitionMap.get(beanName);
        String destroyMethod = bd == null ? null : bd.getDestroyMethodName();
        if (destroyMethod != null && !destroyMethod.isEmpty()) {
            invokeNoArgMethod(beanName, bean, destroyMethod);
        }
    }
}