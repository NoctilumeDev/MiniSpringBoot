package com.minispring.web.conversion;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Web 参数绑定的类型转换服务（教学子集：String → 基本 / 包装类型）。
 *
 * <p>与 Spring 的 {@code ConversionService} 同构：把 query/path 里的字符串映射成方法声明的强类型。
 * 它独立于 config 模块的 {@code SimpleTypeConverter}，避免 web 反依赖 config 的内部支撑包，
 * 让「参数绑定」拥有自己的转换闭环。
 */
public class TypeConversionService {

    private final Map<Class<?>, Converter<String, Object>> converters = new LinkedHashMap<>();

    public TypeConversionService() {
        register(int.class, Integer::parseInt);
        register(Integer.class, Integer::parseInt);
        register(long.class, Long::parseLong);
        register(Long.class, Long::parseLong);
        register(boolean.class, Boolean::parseBoolean);
        register(Boolean.class, Boolean::parseBoolean);
        register(double.class, Double::parseDouble);
        register(Double.class, Double::parseDouble);
        register(float.class, Float::parseFloat);
        register(Float.class, Float::parseFloat);
        register(short.class, Short::parseShort);
        register(Short.class, Short::parseShort);
        register(byte.class, Byte::parseByte);
        register(Byte.class, Byte::parseByte);
        register(char.class, s -> s.isEmpty() ? '\0' : s.charAt(0));
        register(Character.class, s -> s.isEmpty() ? '\0' : s.charAt(0));
    }

    /** 扩展点：注册自定义转换器。 */
    public <T> void register(Class<T> targetType, Converter<String, T> converter) {
        converters.put(targetType, converter::convert);
    }

    public Object convert(String text, Class<?> targetType) {
        if (text == null) {
            return null;
        }
        if (targetType == String.class) {
            return text;
        }
        Converter<String, Object> converter = converters.get(targetType);
        if (converter == null) {
            throw new IllegalArgumentException("不支持的参数类型转换: " + targetType.getName());
        }
        return converter.convert(text.trim());
    }
}