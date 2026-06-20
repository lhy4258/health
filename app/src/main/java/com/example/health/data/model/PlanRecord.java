package com.example.health.data.model;

import cn.leancloud.LCObject;
import cn.leancloud.LCUser;
import cn.leancloud.annotation.LCClassName;

/**
 * 健身计划数据模型，对应 LeanCloud PlanRecord 表。
 * 存储用户的运动计划，支持日计划/周计划，按运动类型设置目标时长。
 */
@LCClassName("PlanRecord")
public class PlanRecord extends LCObject {
    public static final String ATTR_TITLE = "title";
    public static final String ATTR_DATE = "date";
    public static final String ATTR_REMARK = "remark";
    public static final String ATTR_USER = "user";
    public static final String ATTR_PLAN_TYPE = "planType";
    public static final String ATTR_WALKING_MINUTES = "walkingMinutes";
    public static final String ATTR_RUNNING_MINUTES = "runningMinutes";
    public static final String ATTR_RIDE_MINUTES = "rideMinutes";
    public static final String ATTR_FITNESS_MINUTES = "fitnessMinutes";

    public static final String TYPE_DAILY = "daily";
    public static final String TYPE_WEEKLY = "weekly";

    public PlanRecord() {
        super();
    }

    public String getTitle() {
        return getString(ATTR_TITLE);
    }

    public void setTitle(String title) {
        put(ATTR_TITLE, title);
    }

    public long getDate() {
        return getLong(ATTR_DATE);
    }

    public void setDate(long date) {
        put(ATTR_DATE, date);
    }

    public String getRemark() {
        return getString(ATTR_REMARK);
    }

    public void setRemark(String remark) {
        put(ATTR_REMARK, remark);
    }

    public String getPlanType() {
        return getString(ATTR_PLAN_TYPE);
    }

    public void setPlanType(String planType) {
        put(ATTR_PLAN_TYPE, planType);
    }

    public int getWalkingMinutes() {
        return getInt(ATTR_WALKING_MINUTES);
    }

    public void setWalkingMinutes(int minutes) {
        put(ATTR_WALKING_MINUTES, minutes);
    }

    public int getRunningMinutes() {
        return getInt(ATTR_RUNNING_MINUTES);
    }

    public void setRunningMinutes(int minutes) {
        put(ATTR_RUNNING_MINUTES, minutes);
    }

    public int getRideMinutes() {
        return getInt(ATTR_RIDE_MINUTES);
    }

    public void setRideMinutes(int minutes) {
        put(ATTR_RIDE_MINUTES, minutes);
    }

    public int getFitnessMinutes() {
        return getInt(ATTR_FITNESS_MINUTES);
    }

    public void setFitnessMinutes(int minutes) {
        put(ATTR_FITNESS_MINUTES, minutes);
    }

    public LCUser getUser() {
        return getLCObject(ATTR_USER);
    }

    public void setUser(LCUser user) {
        put(ATTR_USER, user);
    }
}
