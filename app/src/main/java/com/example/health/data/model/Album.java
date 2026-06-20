package com.example.health.data.model;

import cn.leancloud.LCObject;
import cn.leancloud.LCUser;
import cn.leancloud.annotation.LCClassName;

/**
 * 相册数据模型，对应 LeanCloud Album 表。
 * 存储用户创建的相册名称及所有者信息。
 */
@LCClassName("Album")
public class Album extends LCObject {
    public static final String KEY_NAME = "name";
    public static final String KEY_OWNER = "owner";

    public String getName() {
        return getString(KEY_NAME);
    }

    public void setName(String name) {
        put(KEY_NAME, name);
    }

    public LCUser getOwner() {
        return getLCObject(KEY_OWNER);
    }

    public void setOwner(LCUser owner) {
        put(KEY_OWNER, owner);
    }
}

