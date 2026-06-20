package com.example.health.utils;

import java.util.regex.Pattern;

/**
 * 输入校验工具类，提供邮箱、手机号、用户名的格式验证及错误提示信息。
 */
public class ValidationUtils {
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
        "^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$"
    );

    private static final Pattern PHONE_PATTERN = Pattern.compile(
        "^1[3-9]\\d{9}$"
    );

    public static boolean isValidEmail(String email) {
        if (email == null || email.trim().isEmpty()) {
            return false;
        }
        return EMAIL_PATTERN.matcher(email).matches();
    }

    public static boolean isValidPhone(String phone) {
        if (phone == null || phone.trim().isEmpty()) {
            return false;
        }
        return PHONE_PATTERN.matcher(phone).matches();
    }

    public static boolean isValidUsername(String username) {
        if (username == null || username.trim().isEmpty()) {
            return false;
        }
        if (username.length() < 3 || username.length() > 20) {
            return false;
        }
        return username.matches("^[a-zA-Z0-9_]+$");
    }

    public static String getEmailValidationMessage(String email) {
        if (email == null || email.trim().isEmpty()) {
            return "邮箱不能为空";
        }
        if (!EMAIL_PATTERN.matcher(email).matches()) {
            return "邮箱格式不正确";
        }
        return "邮箱格式正确";
    }

    public static String getPhoneValidationMessage(String phone) {
        if (phone == null || phone.trim().isEmpty()) {
            return "手机号不能为空";
        }
        if (!PHONE_PATTERN.matcher(phone).matches()) {
            return "手机号格式不正确";
        }
        return "手机号格式正确";
    }

    public static String getUsernameValidationMessage(String username) {
        if (username == null || username.trim().isEmpty()) {
            return "用户名不能为空";
        }
        if (username.length() < 3) {
            return "用户名至少3个字符";
        }
        if (username.length() > 20) {
            return "用户名最多20个字符";
        }
        if (!username.matches("^[a-zA-Z0-9_]+$")) {
            return "用户名只能包含字母、数字和下划线";
        }
        return "用户名格式正确";
    }
}
