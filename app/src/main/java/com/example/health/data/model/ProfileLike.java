package com.example.health.data.model;

import cn.leancloud.LCObject;
import cn.leancloud.LCUser;
import cn.leancloud.annotation.LCClassName;

/**
 * 用户主页点赞数据模型，对应 LeanCloud ProfileLike 表。
 * 记录用户间的点赞行为，通过查询 toUser 统计用户总获赞数。
 */
@LCClassName("ProfileLike")
public class ProfileLike extends LCObject {
    public static final String KEY_FROM_USER = "fromUser";
    public static final String KEY_TO_USER = "toUser";

    public LCUser getFromUser() {
        return getLCObject(KEY_FROM_USER);
    }

    public void setFromUser(LCUser user) {
        put(KEY_FROM_USER, user);
    }

    public LCUser getToUser() {
        return getLCObject(KEY_TO_USER);
    }

    public void setToUser(LCUser user) {
        put(KEY_TO_USER, user);
    }
}
