package com.example.health.data.model;

import cn.leancloud.LCUser;
import cn.leancloud.annotation.LCClassName;

/**
 * 用户数据模型，映射 LeanCloud _User 表。
 * 扩展 LCUser，增加 phone / emailVerified / id 等便捷访问方法。
 */
@LCClassName("_User")
public class User extends LCUser {

    public User() {
        super();
    }

    public User(String email, String password, String username, String phone) {
        super();
        setEmail(email);
        setPassword(password);
        setUsername(username);
        setMobilePhoneNumber(phone);
    }

    public String getPhone() {
        return getMobilePhoneNumber();
    }

    public void setPhone(String phone) {
        setMobilePhoneNumber(phone);
    }

    public boolean isEmailVerified() {
        return getBoolean("emailVerified");
    }

    public String getId() {
        return getObjectId();
    }
}
