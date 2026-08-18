package com.minispring.context.annotation;

import java.io.File;
import java.lang.annotation.Annotation;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/**
 * 类路径扫描器：把一个包下的所有 {@code .class} 文件翻出来，
 * 识别出带 {@link Component}（含派生注解）的「候选组件」，交给容器注册。
 *
 * <p>实现走的是「真扫描」而非硬编码：靠 ClassLoader 把包名转目录、递归遍历文件系统。
 */
public class ClassPathScanningCandidateComponentProvider {

    public List<Class<?>> findCandidateComponents(String basePackage) {
        List<Class<?>> candidates = new ArrayList<>();
        String packagePath = basePackage.replace('.', '/');
        try {
            URL url = Thread.currentThread().getContextClassLoader().getResource(packagePath);
            if (url == null) {
                return candidates;
            }
            File root = new File(url.toURI());
            scanDirectory(root, basePackage, candidates);
        } catch (Exception e) {
            throw new IllegalStateException("扫描包[" + basePackage + "]失败", e);
        }
        return candidates;
    }

    private void scanDirectory(File dir, String currentPackage, List<Class<?>> candidates) {
        File[] files = dir.listFiles();
        if (files == null) {
            return;
        }
        for (File file : files) {
            if (file.isDirectory()) {
                scanDirectory(file, currentPackage + "." + file.getName(), candidates);
            } else if (file.getName().endsWith(".class")) {
                String simpleName = file.getName().substring(0, file.getName().length() - ".class".length());
                Class<?> clazz = loadClass(currentPackage + "." + simpleName);
                if (clazz != null && isComponent(clazz)) {
                    candidates.add(clazz);
                }
            }
        }
    }

    private Class<?> loadClass(String className) {
        try {
            return Thread.currentThread().getContextClassLoader().loadClass(className);
        } catch (Throwable e) {
            // 内部类 / 匿名类等加载失败的一律忽略
            return null;
        }
    }

    /** 判断类是否标注了 @Component（含 @Service/@Repository/@Configuration 元注解派生）。 */
    public boolean isComponent(Class<?> clazz) {
        return hasComponentAnnotation(clazz);
    }

    private boolean hasComponentAnnotation(Class<?> clazz) {
        for (Annotation ann : clazz.getAnnotations()) {
            Class<? extends Annotation> annType = ann.annotationType();
            if (annType == Component.class) {
                return true;
            }
            // 不钻进 JDK 元注解（@Target/@Retention/@Documented）的环里
            if (annType.getName().startsWith("java.lang.annotation")) {
                continue;
            }
            // 元注解递归：@Service 上标注了 @Component
            if (hasComponentAnnotation(annType)) {
                return true;
            }
        }
        return false;
    }
}