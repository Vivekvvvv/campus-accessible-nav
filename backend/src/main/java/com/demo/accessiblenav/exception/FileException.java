package com.demo.accessiblenav.exception;

/**
 * 文件操作相关异常
 */
public class FileException extends BusinessException {

    public FileException(ErrorCode errorCode) {
        super(errorCode);
    }

    public FileException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }

    public FileException(ErrorCode errorCode, Throwable cause) {
        super(errorCode, cause);
    }

    public static FileException typeNotAllowed(String actualType) {
        return new FileException(ErrorCode.FILE_TYPE_NOT_ALLOWED,
                "不支持的文件类型: " + actualType);
    }

    public static FileException tooLarge(long actualSize, long maxSize) {
        return new FileException(ErrorCode.FILE_TOO_LARGE,
                String.format("文件大小 %d 字节超过限制 %d 字节", actualSize, maxSize));
    }

    public static FileException uploadFailed(Throwable cause) {
        return new FileException(ErrorCode.FILE_UPLOAD_FAILED, cause);
    }

    public static FileException notFound(String filename) {
        return new FileException(ErrorCode.FILE_NOT_FOUND, "文件不存在: " + filename);
    }
}
