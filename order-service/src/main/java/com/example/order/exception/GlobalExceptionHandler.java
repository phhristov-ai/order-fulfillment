package com.example.order.exception;

import com.example.order.dto.ApiErrorResponse;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidationException(
            MethodArgumentNotValidException exception) {

        String message = exception.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .distinct()
                .collect(Collectors.joining("; "));

        return createErrorResponse(HttpStatus.BAD_REQUEST, message);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ApiErrorResponse> handleMethodValidationException(
            HandlerMethodValidationException exception) {

        return createErrorResponse(
                HttpStatus.BAD_REQUEST,
                "Invalid request parameters"
        );
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleConstraintViolation(
            ConstraintViolationException exception) {

        return createErrorResponse(
                HttpStatus.BAD_REQUEST,
                exception.getMessage()
        );
    }

    private ResponseEntity<ApiErrorResponse> createErrorResponse(
            HttpStatus status,
            String message) {

        return ResponseEntity.status(status).body(
                new ApiErrorResponse(
                        status.value(),
                        status.getReasonPhrase(),
                        message
                )
        );
    }

    @ExceptionHandler(StatusRuntimeException.class)
    public ResponseEntity<ApiErrorResponse> handleGrpcException(
            StatusRuntimeException exception) {

        Status.Code grpcCode = exception.getStatus().getCode();

        HttpStatus httpStatus = mapGrpcStatus(grpcCode);

        String message = exception.getStatus().getDescription();

        ApiErrorResponse response = new ApiErrorResponse(
                httpStatus.value(),
                httpStatus.getReasonPhrase(),
                message != null ? message : "An error occurred"
        );

        return ResponseEntity
                .status(httpStatus)
                .body(response);
    }

    private HttpStatus mapGrpcStatus(Status.Code grpcCode) {

        return switch (grpcCode) {
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case INVALID_ARGUMENT -> HttpStatus.BAD_REQUEST;
            case FAILED_PRECONDITION, ALREADY_EXISTS -> HttpStatus.CONFLICT;
            case UNAVAILABLE -> HttpStatus.SERVICE_UNAVAILABLE;
            case DEADLINE_EXCEEDED -> HttpStatus.GATEWAY_TIMEOUT;
            case UNAUTHENTICATED -> HttpStatus.UNAUTHORIZED;
            case PERMISSION_DENIED -> HttpStatus.FORBIDDEN;
            default -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }
}