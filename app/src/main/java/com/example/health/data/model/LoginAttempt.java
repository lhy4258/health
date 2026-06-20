package com.example.health.data.model;

import cn.leancloud.LCObject;
import cn.leancloud.annotation.LCClassName;

/**
 * 登录尝试记录，对应 LeanCloud LoginAttempt 表。
 * 实现登录失败次数限制与账户锁定机制：连续失败 5 次后锁定 30 分钟。
 */
@LCClassName("LoginAttempt")
public class LoginAttempt extends LCObject {
    public static final String ATTR_EMAIL = "email";
    public static final String ATTR_ATTEMPT_COUNT = "attemptCount";
    public static final String ATTR_LAST_ATTEMPT_TIME = "lastAttemptTime";
    public static final String ATTR_IS_LOCKED = "isLocked";
    public static final String ATTR_LOCK_UNTIL = "lockUntil";

    public LoginAttempt() {
        super();
    }

    public String getEmail() {
        return getString(ATTR_EMAIL);
    }

    public void setEmail(String email) {
        put(ATTR_EMAIL, email);
    }

    public int getAttemptCount() {
        return getInt(ATTR_ATTEMPT_COUNT);
    }

    public void setAttemptCount(int attemptCount) {
        put(ATTR_ATTEMPT_COUNT, attemptCount);
    }

    public long getLastAttemptTime() {
        return getLong(ATTR_LAST_ATTEMPT_TIME);
    }

    public void setLastAttemptTime(long lastAttemptTime) {
        put(ATTR_LAST_ATTEMPT_TIME, lastAttemptTime);
    }

    public boolean isLocked() {
        return getBoolean(ATTR_IS_LOCKED);
    }

    public void setLocked(boolean locked) {
        put(ATTR_IS_LOCKED, locked);
    }

    public long getLockUntil() {
        return getLong(ATTR_LOCK_UNTIL);
    }

    public void setLockUntil(long lockUntil) {
        put(ATTR_LOCK_UNTIL, lockUntil);
    }
}
