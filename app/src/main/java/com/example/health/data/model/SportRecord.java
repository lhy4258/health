package com.example.health.data.model;

import cn.leancloud.LCObject;
import cn.leancloud.LCUser;
import cn.leancloud.annotation.LCClassName;

/**
 * 运动记录数据模型，对应 LeanCloud SportRecord 表。
 * 记录单次运动的类型、起止时间、时长、卡路里、步数、距离等数据。
 */
@LCClassName("SportRecord")
public class SportRecord extends LCObject {
    public static final String ATTR_TYPE = "type";
    public static final String ATTR_START_TIME = "startTime";
    public static final String ATTR_END_TIME = "endTime";
    public static final String ATTR_DURATION = "duration";
    public static final String ATTR_CALORIES = "calories";
    public static final String ATTR_STEPS = "steps";
    public static final String ATTR_DISTANCE = "distance";
    public static final String ATTR_USER = "user";

    public SportRecord() {
        super();
    }

    public String getType() {
        return getString(ATTR_TYPE);
    }

    public void setType(String type) {
        put(ATTR_TYPE, type);
    }

    public void setType(SportType type) {
        put(ATTR_TYPE, type.name());
    }

    public SportType getSportType() {
        String typeStr = getString(ATTR_TYPE);
        if (typeStr != null) {
            try {
                return SportType.valueOf(typeStr);
            } catch (IllegalArgumentException e) {
                return SportType.WALKING; // Default
            }
        }
        return SportType.WALKING;
    }

    public long getStartTime() {
        return getLong(ATTR_START_TIME);
    }

    public void setStartTime(long startTime) {
        put(ATTR_START_TIME, startTime);
    }

    public long getEndTime() {
        return getLong(ATTR_END_TIME);
    }

    public void setEndTime(long endTime) {
        put(ATTR_END_TIME, endTime);
    }

    public long getDuration() {
        return getLong(ATTR_DURATION);
    }

    public void setDuration(long duration) {
        put(ATTR_DURATION, duration);
    }

    public double getCalories() {
        return getDouble(ATTR_CALORIES);
    }

    public void setCalories(double calories) {
        put(ATTR_CALORIES, calories);
    }

    public int getSteps() {
        return getInt(ATTR_STEPS);
    }

    public void setSteps(int steps) {
        put(ATTR_STEPS, steps);
    }

    public double getDistance() {
        return getDouble(ATTR_DISTANCE);
    }

    public void setDistance(double distance) {
        put(ATTR_DISTANCE, distance);
    }

    public LCUser getUser() {
        return getLCObject(ATTR_USER);
    }

    public void setUser(LCUser user) {
        put(ATTR_USER, user);
    }
}
