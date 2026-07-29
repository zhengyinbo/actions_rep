package com.bo.personalwebsite.common;

public record ApiResponse<T>(int code, String message, T data) {

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(200, "success", data);
    }

    public static <T> ApiResponse<T> failed(String message) {
        return new ApiResponse<>(500, message, null);
    }
}

