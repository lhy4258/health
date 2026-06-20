package com.example.health.data.model;

import cn.leancloud.LCFile;
import cn.leancloud.LCObject;
import cn.leancloud.LCUser;
import cn.leancloud.annotation.LCClassName;

/**
 * 饮食记录数据模型，对应 LeanCloud DietRecord 表。
 * 记录用户每日饮食的图片、日期、备注和餐食内容。
 */
@LCClassName("DietRecord")
public class DietRecord extends LCObject {
    public static final String ATTR_IMAGE = "image";
    public static final String ATTR_DATE = "date";
    public static final String ATTR_REMARK = "remark";
    public static final String ATTR_USER = "user";
    public static final String ATTR_MEALS = "meals";

    public DietRecord() {
        super();
    }

    public LCFile getImage() {
        return getLCFile(ATTR_IMAGE);
    }

    public void setImage(LCFile file) {
        put(ATTR_IMAGE, file);
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

    public String getMeals() {
        return getString(ATTR_MEALS);
    }

    public void setMeals(String meals) {
        put(ATTR_MEALS, meals);
    }

    public LCUser getUser() {
        return getLCObject(ATTR_USER);
    }

    public void setUser(LCUser user) {
        put(ATTR_USER, user);
    }
}
