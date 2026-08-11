package com.oryxos.web;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;

/**
 * Standard API response envelope.
 * Used by all controllers for consistent JSON structure.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {

    private int code;
    private String message;
    private T data;
    private Instant timestamp;

    public ApiResponse() {
        this.timestamp = Instant.now();
    }

    public static <T> ApiResponse<T> success(T data) {
        ApiResponse<T> response = new ApiResponse<>();
        response.code = 200;
        response.message = "OK";
        response.data = data;
        return response;
    }

    public static <T> ApiResponse<T> success(String message, T data) {
        ApiResponse<T> response = new ApiResponse<>();
        response.code = 200;
        response.message = message;
        response.data = data;
        return response;
    }

    public static <T> ApiResponse<T> error(int code, String message) {
        ApiResponse<T> response = new ApiResponse<>();
        response.code = code;
        response.message = message;
        return response;
    }

    // Getters
    public int getCode() { return code; }
    public String getMessage() { return message; }
    public T getData() { return data; }
    public Instant getTimestamp() { return timestamp; }
}
