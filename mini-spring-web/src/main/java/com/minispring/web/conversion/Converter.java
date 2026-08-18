package com.minispring.web.conversion;

/**
 * 类型转换器：把源类型 S 转换成目标类型 T（Web 参数绑定场景下，S 通常是 String）。
 *
 * <p>这是参数绑定环节的「可扩展点」：想支持枚举 / 日期等，注册一个 {@link Converter} 即可，主流程不动。
 */
@FunctionalInterface
public interface Converter<S, T> {

    T convert(S source);
}