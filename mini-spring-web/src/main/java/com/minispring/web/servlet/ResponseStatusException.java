package com.minispring.web.servlet;

import com.minispring.web.http.HttpStatusException;

/**
 * 携带 HTTP 状态码的业务异常：抛出即以指定状态码
 * 响应，用于表达「资源不存在 → 404」「请求不合法 → 400」这类<b>客户端侧</b>错误——
 * 它们不是服务器故障，统一 500 会误导调用方与监控。
 *
 * <p>与 Spring 的 {@code ResponseStatusException} 同名同构（教学子集不带 HttpStatus 枚举，
 * 直接携带 int 状态码）。框架层的内建映射约定见
 * {@link DispatcherServlet#resolveStatus(Throwable)}：本异常 → 自带状态码；
 * {@code IllegalArgumentException} → 400（参数校验类）；其余 → 500。
 */
public class ResponseStatusException extends HttpStatusException {

    public ResponseStatusException(int status, String message) {
        super(status, message);
    }
}
