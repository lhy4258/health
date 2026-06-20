package com.example.health.data.model;

import cn.leancloud.LCObject;
import cn.leancloud.LCUser;
import cn.leancloud.annotation.LCClassName;

/**
 * 每日点赞限制记录，对应 LeanCloud LikeDailyRecord 表。
 * 配合 LikeDailyRecord 实现防刷赞机制：每次点赞前查询当日记录，超过限制则拒绝。
 */
@LCClassName("LikeDailyRecord")
public class LikeDailyRecord extends LCObject {
    public static final String KEY_FROM_USER = "fromUser";
    public static final String KEY_TO_USER = "toUser";
    public static final String KEY_DATE_KEY = "dateKey";
    public static final String KEY_COUNT = "count";

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

    public String getDateKey() {
        return getString(KEY_DATE_KEY);
    }

    public void setDateKey(String dateKey) {
        put(KEY_DATE_KEY, dateKey);
    }

    public int getCount() {
        return getInt(KEY_COUNT);
    }

    public void setCount(int count) {
        put(KEY_COUNT, count);
    }
}
