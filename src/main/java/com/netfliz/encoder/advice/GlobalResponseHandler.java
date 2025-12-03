package com.netfliz.encoder.advice;

import com.netfliz.encoder.model.DefaultResponse;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

@ControllerAdvice
public class GlobalResponseHandler implements ResponseBodyAdvice<Object> {

    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        // áp dụng cho mọi controller (trừ khi bạn muốn exclude)
        return true;
    }

    @Override
    public Object beforeBodyWrite(
            Object body,
            MethodParameter returnType,
            MediaType selectedContentType,
            Class<? extends HttpMessageConverter<?>> selectedConverterType,
            ServerHttpRequest request,
            ServerHttpResponse response
    ) {
        // Nếu đã là DefaultResponse rồi thì không bọc thêm nữa
        if (body instanceof DefaultResponse<?>) {
            return body;
        }

        // Nếu là lỗi (ExceptionHandler xử lý riêng)
        if (body instanceof String) {
            // Khi controller trả String thì phải serialize thủ công
            return "{\"success\":true,\"message\":\"OK\",\"data\":\"" + body + "\"}";
        }

        return DefaultResponse.success(body);
    }
}
