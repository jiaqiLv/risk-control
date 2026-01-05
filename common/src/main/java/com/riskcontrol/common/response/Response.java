package com.riskcontrol.common.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.riskcontrol.common.error.ErrorCode;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Response<T> {

    private int code;
    private String message;
    private T data;
    private String requestId;
    private Long timestamp;

    public static <T> Response<T> success(T data) {
        return Response.<T>builder()
                .code(ErrorCode.SUCCESS.getCode())
                .message(ErrorCode.SUCCESS.getMessage())
                .data(data)
                .timestamp(System.currentTimeMillis())
                .build();
    }

    public static <T> Response<T> success(T data, String requestId) {
        return Response.<T>builder()
                .code(ErrorCode.SUCCESS.getCode())
                .message(ErrorCode.SUCCESS.getMessage())
                .data(data)
                .requestId(requestId)
                .timestamp(System.currentTimeMillis())
                .build();
    }

    public static <T> Response<T> error(ErrorCode errorCode, String requestId) {
        return Response.<T>builder()
                .code(errorCode.getCode())
                .message(errorCode.getMessage())
                .requestId(requestId)
                .timestamp(System.currentTimeMillis())
                .build();
    }

    public static <T> Response<T> error(ErrorCode errorCode, String message, String requestId) {
        return Response.<T>builder()
                .code(errorCode.getCode())
                .message(message)
                .requestId(requestId)
                .timestamp(System.currentTimeMillis())
                .build();
    }
}
