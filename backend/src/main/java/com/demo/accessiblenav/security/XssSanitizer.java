package com.demo.accessiblenav.security;

import org.springframework.web.util.HtmlUtils;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * XSS 防护工具类
 * 提供输入净化和验证功能
 */
public final class XssSanitizer {

    private XssSanitizer() {
        // 工具类不允许实例化
    }

    // 常见XSS攻击模式
    private static final Pattern[] XSS_PATTERNS = {
            // Script tags
            Pattern.compile("<script>(.*?)</script>", Pattern.CASE_INSENSITIVE),
            Pattern.compile("</script>", Pattern.CASE_INSENSITIVE),
            Pattern.compile("<script(.*?)>", Pattern.CASE_INSENSITIVE | Pattern.MULTILINE | Pattern.DOTALL),
            // src='...'
            Pattern.compile("src[\r\n]*=[\r\n]*'(.*?)'", Pattern.CASE_INSENSITIVE | Pattern.MULTILINE | Pattern.DOTALL),
            Pattern.compile("src[\r\n]*=[\r\n]*\"(.*?)\"", Pattern.CASE_INSENSITIVE | Pattern.MULTILINE | Pattern.DOTALL),
            // eval(...)
            Pattern.compile("eval\\((.*?)\\)", Pattern.CASE_INSENSITIVE | Pattern.MULTILINE | Pattern.DOTALL),
            // expression(...)
            Pattern.compile("expression\\((.*?)\\)", Pattern.CASE_INSENSITIVE | Pattern.MULTILINE | Pattern.DOTALL),
            // javascript:
            Pattern.compile("javascript:", Pattern.CASE_INSENSITIVE),
            // vbscript:
            Pattern.compile("vbscript:", Pattern.CASE_INSENSITIVE),
            // onload=
            Pattern.compile("onload(.*?)=", Pattern.CASE_INSENSITIVE | Pattern.MULTILINE | Pattern.DOTALL),
            // onerror=
            Pattern.compile("onerror(.*?)=", Pattern.CASE_INSENSITIVE | Pattern.MULTILINE | Pattern.DOTALL),
            // onclick=
            Pattern.compile("onclick(.*?)=", Pattern.CASE_INSENSITIVE | Pattern.MULTILINE | Pattern.DOTALL),
            // onmouseover=
            Pattern.compile("onmouseover(.*?)=", Pattern.CASE_INSENSITIVE | Pattern.MULTILINE | Pattern.DOTALL)
    };

    /**
     * 净化字符串，移除潜在的XSS攻击代码
     *
     * @param input 输入字符串
     * @return 净化后的字符串
     */
    public static String sanitize(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }

        String result = stripXssPatterns(input);

        // HTML转义
        result = Objects.requireNonNull(HtmlUtils.htmlEscape(Objects.requireNonNull(result)));

        return result;
    }

    /**
     * 轻量级净化，仅HTML转义，不移除模式
     * 适用于需要保留部分特殊字符的场景
     *
     * @param input 输入字符串
     * @return 转义后的字符串
     */
    public static String escapeHtml(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }
        return Objects.requireNonNull(HtmlUtils.htmlEscape(input));
    }

    /**
     * 检查字符串是否包含潜在的XSS攻击代码
     *
     * @param input 输入字符串
     * @return 如果包含XSS代码返回true
     */
    public static boolean containsXss(String input) {
        if (input == null || input.isEmpty()) {
            return false;
        }

        for (Pattern pattern : XSS_PATTERNS) {
            if (pattern.matcher(input).find()) {
                return true;
            }
        }
        return false;
    }

    /**
     * 净化用户名，只允许字母、数字、下划线和中文
     *
     * @param username 用户名
     * @return 净化后的用户名
     */
    public static String sanitizeUsername(String username) {
        if (username == null || username.isEmpty()) {
            return username;
        }
        // Strip "<script>" etc first, otherwise the "script" token could survive the char whitelist.
        String stripped = stripXssPatterns(username);
        // 只保留字母、数字、下划线、中文
        return stripped.replaceAll("[^a-zA-Z0-9_\\u4e00-\\u9fa5]", "");
    }

    /**
     * 净化文件名，移除路径遍历字符
     *
     * @param filename 文件名
     * @return 净化后的文件名
     */
    public static String sanitizeFilename(String filename) {
        if (filename == null || filename.isEmpty()) {
            return filename;
        }

        // Keep only the last path segment (works for both '/' and '\\').
        String normalized = filename.replace('\\', '/');
        int lastSlash = normalized.lastIndexOf('/');
        String base = lastSlash >= 0 ? normalized.substring(lastSlash + 1) : normalized;

        // 移除路径遍历字符和特殊字符
        return base
                .replaceAll("\\.\\.", "")
                .replaceAll("/", "")
                .replaceAll("\\\\", "")
                .replaceAll("[<>:\"|?*]", "");
    }

    private static String stripXssPatterns(String input) {
        String result = input;
        for (Pattern pattern : XSS_PATTERNS) {
            result = pattern.matcher(result).replaceAll("");
        }
        return result;
    }

    /**
     * 截断字符串到指定最大长度
     *
     * @param input     输入字符串
     * @param maxLength 最大长度
     * @return 截断后的字符串
     */
    public static String truncate(String input, int maxLength) {
        if (input == null || input.length() <= maxLength) {
            return input;
        }
        return input.substring(0, maxLength);
    }
}
