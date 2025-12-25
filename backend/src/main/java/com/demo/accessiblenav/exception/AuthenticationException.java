package com.demo.accessiblenav.exception;

/**
 * 认证相关异常
 */
public class AuthenticationException extends BusinessException {

    public AuthenticationException(ErrorCode errorCode) {
        super(errorCode);
    }

    public AuthenticationException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }

    public AuthenticationException(ErrorCode errorCode, Throwable cause) {
        super(errorCode, cause);
    }

    public static AuthenticationException invalidCredentials() {
        return new AuthenticationException(ErrorCode.INVALID_CREDENTIALS);
    }

    public static AuthenticationException tokenExpired() {
        return new AuthenticationException(ErrorCode.TOKEN_EXPIRED);
    }

    public static AuthenticationException tokenInvalid() {
        return new AuthenticationException(ErrorCode.TOKEN_INVALID);
    }

    public static AuthenticationException refreshTokenExpired() {
        return new AuthenticationException(ErrorCode.REFRESH_TOKEN_EXPIRED);
    }

    public static AuthenticationException refreshTokenInvalid() {
        return new AuthenticationException(ErrorCode.REFRESH_TOKEN_INVALID);
    }

    public static AuthenticationException unauthorized() {
        return new AuthenticationException(ErrorCode.UNAUTHORIZED);
    }

    public static AuthenticationException forbidden() {
        return new AuthenticationException(ErrorCode.FORBIDDEN);
    }
}
