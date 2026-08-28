package com.minispring.core.env;

import java.util.ArrayList;
import java.util.List;

/**
 * 有序的配置源头集合：下标越小优先级越高，查值时「谁先有值谁赢」。
 *
 * <p>这条优先级链允许上层显式加入更高优先级来源；当前启动器只接入系统属性、环境变量与配置文件。
 */
public class MutablePropertySources {

    private final List<PropertySource> sources = new ArrayList<>();

    /** 放到最高优先级（最前面）。 */
    public void addFirst(PropertySource propertySource) {
        removeIfPresent(propertySource);
        sources.add(0, propertySource);
    }

    /** 放到最低优先级（最后面）。 */
    public void addLast(PropertySource propertySource) {
        removeIfPresent(propertySource);
        sources.add(propertySource);
    }

    /** 插到某个已存在源头之前，使其优先级「高于」该源头。 */
    public void addBefore(String relativeName, PropertySource propertySource) {
        removeIfPresent(propertySource);
        sources.add(indexOf(relativeName), propertySource);
    }

    public PropertySource get(String name) {
        for (PropertySource ps : sources) {
            if (ps.getName().equals(name)) {
                return ps;
            }
        }
        return null;
    }

    public boolean contains(String name) {
        return get(name) != null;
    }

    /** 按优先级从高到低返回当前所有源头（不可变快照）。 */
    public List<PropertySource> asList() {
        return List.copyOf(sources);
    }

    public int size() {
        return sources.size();
    }

    private void removeIfPresent(PropertySource ps) {
        sources.removeIf(existing -> existing.getName().equals(ps.getName()));
    }

    private int indexOf(String name) {
        for (int i = 0; i < sources.size(); i++) {
            if (sources.get(i).getName().equals(name)) {
                return i;
            }
        }
        throw new IllegalArgumentException("未找到要相对插入的 PropertySource: " + name);
    }
}
