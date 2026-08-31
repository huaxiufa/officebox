package com.officebox.common.exception;

import com.officebox.common.api.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {
  @ExceptionHandler(BusinessException.class)
  public ResponseEntity<ApiError> handleBusiness(BusinessException ex, HttpServletRequest request) {
    return ResponseEntity.badRequest().body(
        new ApiError(ex.getCode(), ex.getMessage(), request.getRequestURI(), Instant.now()));
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ApiError> handleUnexpected(Exception ex, HttpServletRequest request) {
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
        new ApiError("INTERNAL_ERROR", "Internal server error", request.getRequestURI(), Instant.now()));
  }
}
