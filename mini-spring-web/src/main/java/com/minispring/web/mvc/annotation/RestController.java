package com.minispring.web.mvc.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * {@link Controller} + {@link ResponseBody} 的合体：所有方法返回值默认直接写响应体（JSON / 文本），
 * 而不是当成视图名——即写 REST 接口时的默认选择。
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Controller
@ResponseBody
public @interface RestController {

    String value() default "";
}