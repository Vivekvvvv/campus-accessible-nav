package com.demo.accessiblenav.security;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 密码策略验证器
 * 提供密码强度检查和策略验证
 */
public final class PasswordPolicy {

    private PasswordPolicy() {
        // 工具类不允许实例化
    }

    // 密码策略配置
    public static final int MIN_LENGTH = 8;
    public static final int MAX_LENGTH = 128;
    public static final boolean REQUIRE_UPPERCASE = true;
    public static final boolean REQUIRE_LOWERCASE = true;
    public static final boolean REQUIRE_DIGIT = true;
    public static final boolean REQUIRE_SPECIAL = false; // 可选

    // 常见弱密码列表
    private static final String[] WEAK_PASSWORDS = {
            "password", "12345678", "123456789", "qwerty", "abc123",
            "password1", "admin123", "letmein", "welcome", "monkey",
            "dragon", "master", "login", "princess", "admin",
            "passw0rd", "iloveyou", "sunshine", "000000", "111111",
            "123123", "654321", "qwerty123", "password123"
    };

    // 正则模式
    private static final Pattern UPPERCASE_PATTERN = Pattern.compile("[A-Z]");
    private static final Pattern LOWERCASE_PATTERN = Pattern.compile("[a-z]");
    private static final Pattern DIGIT_PATTERN = Pattern.compile("[0-9]");
    private static final Pattern SPECIAL_PATTERN = Pattern.compile("[!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>/?]");
    // Match sequential runs of length >= 4 (e.g. "1234", "abcd") so "123" doesn't reject otherwise strong passwords.
    private static final Pattern SEQUENTIAL_PATTERN = Pattern.compile("(0123|1234|2345|3456|4567|5678|6789|7890|abcd|bcde|cdef|defg|efgh|fghi|ghij|hijk|ijkl|jklm|klmn|lmno|mnop|nopq|opqr|pqrs|qrst|rstu|stuv|tuvw|uvwx|vwxy|wxyz)", Pattern.CASE_INSENSITIVE);
    private static final Pattern REPEATED_PATTERN = Pattern.compile("(.)\\1{2,}");

    /**
     * 验证密码是否符合策略
     *
     * @param password 密码
     * @return 验证结果
     */
    @SuppressWarnings("all")
    public static ValidationResult validate(String password) {
        List<String> errors = new ArrayList<>();

        if (password == null || password.isEmpty()) {
            errors.add("密码不能为空");
            return new ValidationResult(false, errors, 0);
        }

        // 长度检查
        if (password.length() < MIN_LENGTH) {
            errors.add("密码长度至少 " + MIN_LENGTH + " 个字符");
        }
        if (password.length() > MAX_LENGTH) {
            errors.add("密码长度不能超过 " + MAX_LENGTH + " 个字符");
        }

        // 复杂度检查
        if (REQUIRE_UPPERCASE && !UPPERCASE_PATTERN.matcher(password).find()) {
            errors.add("密码必须包含至少一个大写字母");
        }
        if (REQUIRE_LOWERCASE && !LOWERCASE_PATTERN.matcher(password).find()) {
            errors.add("密码必须包含至少一个小写字母");
        }
        if (REQUIRE_DIGIT && !DIGIT_PATTERN.matcher(password).find()) {
            errors.add("密码必须包含至少一个数字");
        }
        if (REQUIRE_SPECIAL && !SPECIAL_PATTERN.matcher(password).find()) {
            errors.add("密码必须包含至少一个特殊字符");
        }

        // 弱密码检查
        String lowerPassword = password.toLowerCase();
        for (String weak : WEAK_PASSWORDS) {
            if (lowerPassword.equals(weak) || lowerPassword.contains(weak)) {
                errors.add("密码过于简单，请选择更复杂的密码");
                break;
            }
        }

        // 连续字符检查
        if (SEQUENTIAL_PATTERN.matcher(password).find()) {
            errors.add("密码不能包含连续的字母或数字序列");
        }

        // 重复字符检查
        if (REPEATED_PATTERN.matcher(password).find()) {
            errors.add("密码不能包含连续重复的字符");
        }

        // 计算强度分数
        int strength = calculateStrength(password);

        return new ValidationResult(errors.isEmpty(), errors, strength);
    }

    /**
     * 计算密码强度分数 (0-100)
     *
     * @param password 密码
     * @return 强度分数
     */
    public static int calculateStrength(String password) {
        if (password == null || password.isEmpty()) {
            return 0;
        }

        int score = 0;

        // 基础长度分数
        score += Math.min(password.length() * 4, 40);

        // 复杂度加分
        if (UPPERCASE_PATTERN.matcher(password).find()) score += 10;
        if (LOWERCASE_PATTERN.matcher(password).find()) score += 10;
        if (DIGIT_PATTERN.matcher(password).find()) score += 10;
        if (SPECIAL_PATTERN.matcher(password).find()) score += 15;

        // 混合类型加分
        int typeCount = 0;
        if (UPPERCASE_PATTERN.matcher(password).find()) typeCount++;
        if (LOWERCASE_PATTERN.matcher(password).find()) typeCount++;
        if (DIGIT_PATTERN.matcher(password).find()) typeCount++;
        if (SPECIAL_PATTERN.matcher(password).find()) typeCount++;
        score += typeCount * 5;

        // 扣分项
        if (SEQUENTIAL_PATTERN.matcher(password).find()) score -= 10;
        if (REPEATED_PATTERN.matcher(password).find()) score -= 10;

        String lowerPassword = password.toLowerCase();
        for (String weak : WEAK_PASSWORDS) {
            if (lowerPassword.contains(weak)) {
                score -= 20;
                break;
            }
        }

        return Math.max(0, Math.min(100, score));
    }

    /**
     * 获取强度等级描述
     *
     * @param strength 强度分数
     * @return 等级描述
     */
    public static String getStrengthLabel(int strength) {
        if (strength < 20) return "很弱";
        if (strength < 40) return "弱";
        if (strength < 60) return "中等";
        if (strength < 80) return "强";
        return "很强";
    }

    /**
     * 验证结果类
     */
    public static class ValidationResult {
        private final boolean valid;
        private final List<String> errors;
        private final int strength;

        public ValidationResult(boolean valid, List<String> errors, int strength) {
            this.valid = valid;
            this.errors = errors;
            this.strength = strength;
        }

        public boolean isValid() {
            return valid;
        }

        public List<String> getErrors() {
            return errors;
        }

        public int getStrength() {
            return strength;
        }

        public String getStrengthLabel() {
            return PasswordPolicy.getStrengthLabel(strength);
        }

        public String getErrorMessage() {
            if (errors.isEmpty()) return null;
            return String.join("; ", errors);
        }
    }
}
