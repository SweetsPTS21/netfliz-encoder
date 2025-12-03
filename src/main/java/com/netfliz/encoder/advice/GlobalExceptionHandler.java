package com.netfliz.encoder.advice;

import com.netfliz.encoder.exception.BadCredentialException;
import com.netfliz.encoder.exception.BadRequestException;
import com.netfliz.encoder.model.DefaultResponse;
import jakarta.validation.ValidationException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public DefaultResponse<Object> handleException(Exception e) {
        return DefaultResponse.fail(e.getMessage());
    }

    @ExceptionHandler(BadRequestException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public DefaultResponse<Object> handleBadRequestException(BadRequestException e) {
        return DefaultResponse.fail(e.getMessage());
    }

    @ExceptionHandler(BadCredentialException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public DefaultResponse<Object> handleForbiddenException(BadCredentialException e) {
        return DefaultResponse.fail(e.getMessage());
    }

    @ExceptionHandler(ValidationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public DefaultResponse<Object> handleValidationException(ValidationException e) {
        return DefaultResponse.fail(e.getMessage());
    }
}
