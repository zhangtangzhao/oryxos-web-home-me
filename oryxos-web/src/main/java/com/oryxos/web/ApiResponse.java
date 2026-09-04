package com.oryxos.web;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Unified response envelope per contracts/rest-api.md:
 * success: {success:true, data:{...}, error:null};
 * failure: {success:false, data:null, error:{code,message}} with the HTTP
 * status expressing the same code.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {

    private final boolean success;
    private final T data;
    private final ErrorBody error;

    private ApiResponse(boolean success, T data, ErrorBody error) {
        this.success = success;
        this.data = data;
        this.error = error;
    }

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, data, null);
    }

    public static <T> ApiResponse<T> error(int code, String message) {
        return new ApiResponse<>(false, null, new ErrorBody(code, message));
    }

    public boolean isSuccess() { return success; }
    public T getData() { return data; }
    public ErrorBody getError() { return error; }

    public record ErrorBody(int code, String message) {
    }
}
