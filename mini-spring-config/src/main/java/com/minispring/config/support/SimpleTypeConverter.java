package com.minispring.config.support;

/** 把配置字符串转成字段声明的目标类型（教学子集：String + 基本类型 + 包装类型）。 */
public final class SimpleTypeConverter {

    public Object convert(String text, Class<?> targetType) {
        if (text == null) {
            return null;
        }
        if (targetType == String.class) {
            return text;
        }
        if (targetType == int.class || targetType == Integer.class) {
            return Integer.parseInt(text.trim());
        }
        if (targetType == long.class || targetType == Long.class) {
            return Long.parseLong(text.trim());
        }
        if (targetType == boolean.class || targetType == Boolean.class) {
            return Boolean.parseBoolean(text.trim());
        }
        if (targetType == double.class || targetType == Double.class) {
            return Double.parseDouble(text.trim());
        }
        if (targetType == float.class || targetType == Float.class) {
            return Float.parseFloat(text.trim());
        }
        if (targetType == short.class || targetType == Short.class) {
            return Short.parseShort(text.trim());
        }
        if (targetType == byte.class || targetType == Byte.class) {
            return Byte.parseByte(text.trim());
        }
        if (targetType == char.class || targetType == Character.class) {
            return text.isEmpty() ? '\0' : text.charAt(0);
        }
        throw new IllegalArgumentException("不支持的 @Value 类型转换: " + targetType.getName());
    }
}