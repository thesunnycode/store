package com.ecommerce.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * ApiResponse<T> — a consistent envelope for all API responses.
 *
 * Instead of returning raw data, every endpoint returns:
 *   { "success": true, "message": "Product created", "data": { ... }, "timestamp": "..." }
 *
 * Benefits:
 *   - Consistent structure across all endpoints — frontend always knows where to find data
 *   - Error responses have the same shape as success responses
 *   - Timestamp helps with debugging (when did this response come from?)
 *
 * @JsonInclude(NON_NULL) → fields with null value are omitted from JSON output
 * (e.g., "data" won't appear in error responses where we have no data)
 */
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {

    private boolean success;
    private String message;
    private T data;                         // generic — can hold any response type

    @Builder.Default
    private LocalDateTime timestamp = LocalDateTime.now();

    // Convenience factory methods — shorthand for common cases

    public static <T> ApiResponse<T> success(String message, T data) {
        return ApiResponse.<T>builder()
                .success(true)
                .message(message)
                .data(data)
                .build();
    }

    public static <T> ApiResponse<T> success(String message) {
        return ApiResponse.<T>builder()
                .success(true)
                .message(message)
                .build();
    }

    public static <T> ApiResponse<T> error(String message) {
        return ApiResponse.<T>builder()
                .success(false)
                .message(message)
                .build();
    }
}
