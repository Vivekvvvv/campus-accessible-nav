package com.demo.accessiblenav.exception;

/**
 * 路由计算相关异常
 */
public class RouteException extends BusinessException {

    public RouteException(ErrorCode errorCode) {
        super(errorCode);
    }

    public RouteException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }

    public RouteException(ErrorCode errorCode, Throwable cause) {
        super(errorCode, cause);
    }

    public static RouteException notFound() {
        return new RouteException(ErrorCode.ROUTE_NOT_FOUND);
    }

    public static RouteException notFound(String reason) {
        return new RouteException(ErrorCode.ROUTE_NOT_FOUND, reason);
    }

    public static RouteException startOutOfBounds() {
        return new RouteException(ErrorCode.ROUTE_START_OUT_OF_BOUNDS);
    }

    public static RouteException endOutOfBounds() {
        return new RouteException(ErrorCode.ROUTE_END_OUT_OF_BOUNDS);
    }

    public static RouteException snapFailed(String detail) {
        return new RouteException(ErrorCode.ROUTE_SNAP_FAILED, detail);
    }

    public static RouteException timeout() {
        return new RouteException(ErrorCode.ROUTE_CALCULATION_TIMEOUT);
    }
}
