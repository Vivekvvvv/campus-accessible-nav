package com.demo.accessiblenav.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * XssSanitizer 单元测试
 */
class XssSanitizerTest {

    @Test
    @DisplayName("应该移除script标签")
    void shouldRemoveScriptTags() {
        String input = "<script>alert('xss')</script>";
        String result = XssSanitizer.sanitize(input);
        assertFalse(result.contains("<script>"));
        assertFalse(result.contains("</script>"));
    }

    @Test
    @DisplayName("应该移除javascript协议")
    void shouldRemoveJavascriptProtocol() {
        String input = "javascript:alert('xss')";
        String result = XssSanitizer.sanitize(input);
        assertFalse(result.toLowerCase().contains("javascript:"));
    }

    @Test
    @DisplayName("应该移除事件处理器属性")
    void shouldRemoveEventHandlers() {
        String input = "<img onerror=alert('xss') src='x'>";
        String result = XssSanitizer.sanitize(input);
        assertFalse(result.toLowerCase().contains("onerror"));
    }

    @Test
    @DisplayName("应该HTML转义特殊字符")
    void shouldEscapeHtmlCharacters() {
        String input = "<div>test</div>";
        String result = XssSanitizer.sanitize(input);
        assertTrue(result.contains("&lt;"));
        assertTrue(result.contains("&gt;"));
    }

    @Test
    @DisplayName("空输入应该返回空")
    void nullInputShouldReturnNull() {
        assertNull(XssSanitizer.sanitize(null));
        assertEquals("", XssSanitizer.sanitize(""));
    }

    @Test
    @DisplayName("正常文本不应该被修改")
    void normalTextShouldNotBeModified() {
        String input = "这是一段正常的中文文本 Hello World 123";
        String result = XssSanitizer.sanitize(input);
        assertEquals(input, result);
    }

    @Test
    @DisplayName("应该正确检测XSS内容")
    void shouldDetectXssContent() {
        assertTrue(XssSanitizer.containsXss("<script>alert(1)</script>"));
        assertTrue(XssSanitizer.containsXss("javascript:void(0)"));
        assertTrue(XssSanitizer.containsXss("<img onerror=alert(1)>"));
        assertFalse(XssSanitizer.containsXss("正常文本"));
        assertFalse(XssSanitizer.containsXss("Hello World"));
    }

    @Test
    @DisplayName("用户名净化应该只保留允许的字符")
    void usernameSanitizationShouldKeepAllowedChars() {
        assertEquals("user123", XssSanitizer.sanitizeUsername("user123"));
        assertEquals("用户名", XssSanitizer.sanitizeUsername("用户名"));
        assertEquals("user_name", XssSanitizer.sanitizeUsername("user_name"));
        assertEquals("username", XssSanitizer.sanitizeUsername("user<script>name"));
        assertEquals("username", XssSanitizer.sanitizeUsername("user@name!"));
    }

    @Test
    @DisplayName("文件名净化应该移除路径遍历字符")
    void filenameSanitizationShouldRemovePathTraversal() {
        assertEquals("file.txt", XssSanitizer.sanitizeFilename("file.txt"));
        assertEquals("file.txt", XssSanitizer.sanitizeFilename("../file.txt"));
        assertEquals("file.txt", XssSanitizer.sanitizeFilename("..\\..\\file.txt"));
        assertEquals("file.txt", XssSanitizer.sanitizeFilename("/etc/passwd/../file.txt"));
    }

    @Test
    @DisplayName("截断功能应该正确工作")
    void truncateShouldWorkCorrectly() {
        assertEquals("Hello", XssSanitizer.truncate("Hello World", 5));
        assertEquals("Hello", XssSanitizer.truncate("Hello", 10));
        assertNull(XssSanitizer.truncate(null, 10));
    }
}
