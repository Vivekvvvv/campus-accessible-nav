package com.demo.accessiblenav.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 业务异常测试
 */
class BusinessExceptionTest {

    @Test
    @DisplayName("应该正确创建业务异常")
    void shouldCreateBusinessException() {
        // Given
        ErrorCode errorCode = ErrorCode.ROUTE_NOT_FOUND;

        // When
        BusinessException exception = new BusinessException(errorCode);

        // Then
        assertEquals(errorCode.getCode(), exception.getCode());
        assertEquals(errorCode.getMessage(), exception.getMessage());
        assertEquals(errorCode, exception.getErrorCode());
    }

    @Test
    @DisplayName("应该支持自定义消息")
    void shouldSupportCustomMessage() {
        // Given
        ErrorCode errorCode = ErrorCode.ROUTE_NOT_FOUND;
        String customMessage = "从A到B没有找到路线";

        // When
        BusinessException exception = new BusinessException(errorCode, customMessage);

        // Then
        assertEquals(errorCode.getCode(), exception.getCode());
        assertEquals(customMessage, exception.getMessage());
    }

    @Test
    @DisplayName("静态工厂方法应该正常工作")
    void staticFactoryMethodsShouldWork() {
        // Given
        ErrorCode errorCode = ErrorCode.USER_NOT_FOUND;

        // When
        BusinessException exception = BusinessException.of(errorCode);

        // Then
        assertEquals(errorCode, exception.getErrorCode());
        assertEquals(errorCode.getMessage(), exception.getMessage());
    }

    @Test
    @DisplayName("RouteException 静态方法应该正常工作")
    void routeExceptionStaticMethodsShouldWork() {
        // When
        RouteException notFound = RouteException.notFound();
        RouteException startOutOfBounds = RouteException.startOutOfBounds();
        RouteException endOutOfBounds = RouteException.endOutOfBounds();
        RouteException snapFailed = RouteException.snapFailed("无法吸附到路网");
        RouteException timeout = RouteException.timeout();

        // Then
        assertEquals(ErrorCode.ROUTE_NOT_FOUND, notFound.getErrorCode());
        assertEquals(ErrorCode.ROUTE_START_OUT_OF_BOUNDS, startOutOfBounds.getErrorCode());
        assertEquals(ErrorCode.ROUTE_END_OUT_OF_BOUNDS, endOutOfBounds.getErrorCode());
        assertEquals(ErrorCode.ROUTE_SNAP_FAILED, snapFailed.getErrorCode());
        assertEquals(ErrorCode.ROUTE_CALCULATION_TIMEOUT, timeout.getErrorCode());
    }

    @Test
    @DisplayName("AuthenticationException 静态方法应该正常工作")
    void authenticationExceptionStaticMethodsShouldWork() {
        // When
        AuthenticationException invalidCredentials = AuthenticationException.invalidCredentials();
        AuthenticationException tokenExpired = AuthenticationException.tokenExpired();
        AuthenticationException tokenInvalid = AuthenticationException.tokenInvalid();
        AuthenticationException refreshTokenExpired = AuthenticationException.refreshTokenExpired();
        AuthenticationException unauthorized = AuthenticationException.unauthorized();
        AuthenticationException forbidden = AuthenticationException.forbidden();

        // Then
        assertEquals(ErrorCode.INVALID_CREDENTIALS, invalidCredentials.getErrorCode());
        assertEquals(ErrorCode.TOKEN_EXPIRED, tokenExpired.getErrorCode());
        assertEquals(ErrorCode.TOKEN_INVALID, tokenInvalid.getErrorCode());
        assertEquals(ErrorCode.REFRESH_TOKEN_EXPIRED, refreshTokenExpired.getErrorCode());
        assertEquals(ErrorCode.UNAUTHORIZED, unauthorized.getErrorCode());
        assertEquals(ErrorCode.FORBIDDEN, forbidden.getErrorCode());
    }

    @Test
    @DisplayName("FileException 静态方法应该正常工作")
    void fileExceptionStaticMethodsShouldWork() {
        // When
        FileException typeNotAllowed = FileException.typeNotAllowed("application/exe");
        FileException tooLarge = FileException.tooLarge(10_000_000, 5_000_000);
        FileException notFound = FileException.notFound("test.jpg");

        // Then
        assertEquals(ErrorCode.FILE_TYPE_NOT_ALLOWED, typeNotAllowed.getErrorCode());
        assertTrue(typeNotAllowed.getMessage().contains("application/exe"));

        assertEquals(ErrorCode.FILE_TOO_LARGE, tooLarge.getErrorCode());
        assertTrue(tooLarge.getMessage().contains("10000000"));
        assertTrue(tooLarge.getMessage().contains("5000000"));

        assertEquals(ErrorCode.FILE_NOT_FOUND, notFound.getErrorCode());
        assertTrue(notFound.getMessage().contains("test.jpg"));
    }

    @Test
    @DisplayName("ResourceNotFoundException 应该正常工作")
    void resourceNotFoundExceptionShouldWork() {
        // When
        ResourceNotFoundException exception = new ResourceNotFoundException("User", 123);

        // Then
        assertEquals(ErrorCode.RESOURCE_NOT_FOUND, exception.getErrorCode());
        assertTrue(exception.getMessage().contains("User"));
        assertTrue(exception.getMessage().contains("123"));
    }

    @Test
    @DisplayName("ErrorCode toString 应该返回格式化字符串")
    void errorCodeToStringShouldReturnFormattedString() {
        // Given
        ErrorCode errorCode = ErrorCode.ROUTE_NOT_FOUND;

        // When
        String result = errorCode.toString();

        // Then
        assertEquals("[ROUTE_001] 未找到可行路线", result);
    }
}
