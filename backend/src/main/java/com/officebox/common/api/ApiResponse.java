package com.officebox.common.api;

import java.time.Instant;

/**
 * Stable envelope for new V2 APIs. Existing V1 endpoints are intentionally left unchanged.
 */
public record ApiResponse<T>(
    String code,
    String message,
    T data,
    Instant timestamp
) {
  public static <T> ApiResponse<T> success(T data) {
    return new ApiResponse<>("OK", "success", data, Instant.now());
  }

  public static <T> ApiResponse<T> success(String message, T data) {
    return new ApiResponse<>("OK", message, data, Instant.now());
  }
}
