package com.bachdauduc.vocab_app.exception;

import com.bachdauduc.vocab_app.dto.response.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Objects;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {
    @ExceptionHandler(value = AppException.class)
    ResponseEntity<ApiResponse> handleAppException(AppException appException) {
        ErrorCode errorCode = appException.getErrorCode();
        log.warn("Application exception handled: code={}, message={}, httpStatus={}",
                errorCode.getCode(), errorCode.getMessage(), errorCode.getHttpStatus());

        return ResponseEntity.status(errorCode.getHttpStatus()).body(
                ApiResponse.builder()
                        .code(errorCode.getCode())
                        .message(appException.getMessage())
                        .traceId(MDC.get("traceId"))
                        .build()
        );
    }

    @ExceptionHandler(value = Exception.class)
    ResponseEntity<ApiResponse> handleGlobalException(Exception exception) {
        ApiResponse apiResponse = new ApiResponse();
        log.error("Unhandled exception occurred", exception);

        apiResponse.setCode(9999);
        apiResponse.setTraceId(MDC.get("traceId"));
        apiResponse.setMessage(exception.getMessage());
        return ResponseEntity.badRequest().body(apiResponse);
    }

    @ExceptionHandler(value = MethodArgumentNotValidException.class)
    ResponseEntity<ApiResponse> handleValidateRequestBody(MethodArgumentNotValidException exception) {
        ApiResponse apiResponse = new ApiResponse();
        log.warn("Request validation failed: field={}, message={}",
                Objects.requireNonNull(exception.getFieldError()).getField(),
                Objects.requireNonNull(exception.getFieldError()).getDefaultMessage());

        apiResponse.setCode(8888);
        apiResponse.setTraceId(MDC.get("traceId"));
        apiResponse.setMessage(Objects.requireNonNull(exception.getFieldError()).getField() + ": " + Objects.requireNonNull(exception.getFieldError()).getDefaultMessage());
        return ResponseEntity.badRequest().body(apiResponse);
    }


}
