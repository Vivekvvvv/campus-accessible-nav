package com.demo.accessiblenav.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * PasswordPolicy 单元测试
 */
class PasswordPolicyTest {

    @Test
    @DisplayName("强密码应该通过验证")
    void strongPasswordShouldPass() {
        PasswordPolicy.ValidationResult result = PasswordPolicy.validate("StrongPass123");
        assertTrue(result.isValid(), "强密码应该通过验证");
        assertTrue(result.getStrength() >= 60, "强密码强度应该 >= 60");
    }

    @Test
    @DisplayName("太短的密码应该失败")
    void shortPasswordShouldFail() {
        PasswordPolicy.ValidationResult result = PasswordPolicy.validate("Abc1");
        assertFalse(result.isValid(), "太短的密码应该失败");
        assertTrue(result.getErrorMessage().contains("至少"));
    }

    @Test
    @DisplayName("没有大写字母的密码应该失败")
    void noUppercaseShouldFail() {
        PasswordPolicy.ValidationResult result = PasswordPolicy.validate("password123");
        assertFalse(result.isValid(), "没有大写字母应该失败");
        assertTrue(result.getErrorMessage().contains("大写字母"));
    }

    @Test
    @DisplayName("没有小写字母的密码应该失败")
    void noLowercaseShouldFail() {
        PasswordPolicy.ValidationResult result = PasswordPolicy.validate("PASSWORD123");
        assertFalse(result.isValid(), "没有小写字母应该失败");
        assertTrue(result.getErrorMessage().contains("小写字母"));
    }

    @Test
    @DisplayName("没有数字的密码应该失败")
    void noDigitShouldFail() {
        PasswordPolicy.ValidationResult result = PasswordPolicy.validate("StrongPassword");
        assertFalse(result.isValid(), "没有数字应该失败");
        assertTrue(result.getErrorMessage().contains("数字"));
    }

    @Test
    @DisplayName("常见弱密码应该失败")
    void commonWeakPasswordShouldFail() {
        PasswordPolicy.ValidationResult result = PasswordPolicy.validate("password123");
        assertFalse(result.isValid(), "常见弱密码应该失败");
    }

    @Test
    @DisplayName("包含连续字符的密码应该被标记")
    void sequentialCharsShouldBeFlagged() {
        PasswordPolicy.ValidationResult result = PasswordPolicy.validate("Abc12345xyz");
        assertFalse(result.isValid(), "包含连续字符应该失败");
        assertTrue(result.getErrorMessage().contains("连续"));
    }

    @Test
    @DisplayName("包含重复字符的密码应该被标记")
    void repeatedCharsShouldBeFlagged() {
        PasswordPolicy.ValidationResult result = PasswordPolicy.validate("Passssword1");
        assertFalse(result.isValid(), "包含重复字符应该失败");
        assertTrue(result.getErrorMessage().contains("重复"));
    }

    @Test
    @DisplayName("空密码应该失败")
    void emptyPasswordShouldFail() {
        PasswordPolicy.ValidationResult result = PasswordPolicy.validate("");
        assertFalse(result.isValid());

        result = PasswordPolicy.validate(null);
        assertFalse(result.isValid());
    }

    @Test
    @DisplayName("密码强度计算应该正确")
    void strengthCalculationShouldBeCorrect() {
        // 弱密码
        assertTrue(PasswordPolicy.calculateStrength("abc") < 30);

        // 中等密码
        int mediumStrength = PasswordPolicy.calculateStrength("Password1");
        assertTrue(mediumStrength >= 40 && mediumStrength < 80);

        // 强密码
        int strongStrength = PasswordPolicy.calculateStrength("MyStr0ng!Pass#2024");
        assertTrue(strongStrength >= 70);
    }

    @Test
    @DisplayName("强度标签应该正确")
    void strengthLabelShouldBeCorrect() {
        assertEquals("很弱", PasswordPolicy.getStrengthLabel(10));
        assertEquals("弱", PasswordPolicy.getStrengthLabel(30));
        assertEquals("中等", PasswordPolicy.getStrengthLabel(50));
        assertEquals("强", PasswordPolicy.getStrengthLabel(70));
        assertEquals("很强", PasswordPolicy.getStrengthLabel(90));
    }

    @Test
    @DisplayName("验证结果方法应该正确工作")
    void validationResultMethodsShouldWork() {
        PasswordPolicy.ValidationResult result = PasswordPolicy.validate("StrongPass123");
        assertTrue(result.isValid());
        assertTrue(result.getErrors().isEmpty());
        assertNull(result.getErrorMessage());
        assertNotNull(result.getStrengthLabel());
    }
}
