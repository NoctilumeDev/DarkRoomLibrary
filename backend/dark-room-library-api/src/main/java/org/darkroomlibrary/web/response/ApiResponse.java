package org.darkroomlibrary.web.response;

import java.util.Objects;

/**
 * Stable HTTP response envelope used by every JSON endpoint.
 */
public class ApiResponse<T> {

    private Integer code;
    private String msg;
    private T data;

    public ApiResponse() {
    }

    public ApiResponse(Integer code, String msg) {
        this(code, msg, null);
    }

    public ApiResponse(Integer code, String msg, T data) {
        this.code = Objects.requireNonNull(code, "code");
        this.msg = Objects.requireNonNullElse(msg, "");
        this.data = data;
    }

    public static <T> ApiResponse<T> success() {
        return success((T) null);
    }

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(ApiStatusCode.SUCCESS.value(), "操作成功", data);
    }

    public static <T> ApiResponse<T> success(String message) {
        return new ApiResponse<>(ApiStatusCode.SUCCESS.value(), message);
    }

    public static <T> ApiResponse<T> success(String message, T data) {
        return new ApiResponse<>(ApiStatusCode.SUCCESS.value(), message, data);
    }

    public static <T> ApiResponse<T> error(String message) {
        return new ApiResponse<>(ApiStatusCode.BAD_REQUEST.value(), message);
    }

    public Integer getCode() {
        return code;
    }

    public void setCode(Integer code) {
        this.code = code;
    }

    public String getMsg() {
        return msg;
    }

    public void setMsg(String msg) {
        this.msg = msg;
    }

    public T getData() {
        return data;
    }

    public void setData(T data) {
        this.data = data;
    }

    @Override
    public String toString() {
        return "ApiResponse{code=" + code + ", msg='" + msg + "'}";
    }
}
