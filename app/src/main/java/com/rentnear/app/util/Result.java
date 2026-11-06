package com.rentnear.app.util;

/** Small success/error wrapper that ViewModels expose through LiveData. */
public class Result<T> {
    public final T data;
    public final String error;

    private Result(T data, String error) {
        this.data = data;
        this.error = error;
    }

    public static <T> Result<T> ok(T data) {
        return new Result<>(data, null);
    }

    public static <T> Result<T> fail(String error) {
        return new Result<>(null, error);
    }

    public boolean isOk() {
        return error == null;
    }
}
