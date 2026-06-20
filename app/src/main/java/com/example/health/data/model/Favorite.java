package com.example.health.data.model;

import cn.leancloud.LCFile;
import cn.leancloud.LCObject;
import cn.leancloud.LCUser;
import cn.leancloud.annotation.LCClassName;
import java.util.List;

/**
 * 收藏数据模型，对应 LeanCloud Favorite 表。
 * 支持收藏社区动态（moment 类型）和自定义内容（custom 类型），可附带文字、图片、视频和附件。
 */
@LCClassName("Favorite")
public class Favorite extends LCObject {
    public static final String KEY_USER = "user";
    public static final String KEY_MOMENT = "moment";
    public static final String KEY_TYPE = "type";
    public static final String KEY_TEXT = "text";
    public static final String KEY_IMAGES = "images";
    public static final String KEY_VIDEO = "video";
    public static final String KEY_FILES = "files";
    public static final String KEY_SOURCE_MOMENT_ID = "sourceMomentId";

    public static final String TYPE_MOMENT = "moment";
    public static final String TYPE_CUSTOM = "custom";

    public LCUser getUser() {
        return getLCObject(KEY_USER);
    }

    public void setUser(LCUser user) {
        put(KEY_USER, user);
    }

    public Moment getMoment() {
        return getLCObject(KEY_MOMENT);
    }

    public void setMoment(Moment moment) {
        put(KEY_MOMENT, moment);
    }

    public String getType() {
        return getString(KEY_TYPE);
    }

    public void setType(String type) {
        put(KEY_TYPE, type);
    }

    public String getText() {
        return getString(KEY_TEXT);
    }

    public void setText(String text) {
        put(KEY_TEXT, text);
    }

    public List<LCFile> getImages() {
        return getList(KEY_IMAGES);
    }

    public void setImages(List<LCFile> images) {
        put(KEY_IMAGES, images);
    }

    public LCFile getVideo() {
        return getLCFile(KEY_VIDEO);
    }

    public void setVideo(LCFile video) {
        put(KEY_VIDEO, video);
    }

    public List<LCFile> getFiles() {
        return getList(KEY_FILES);
    }

    public void setFiles(List<LCFile> files) {
        put(KEY_FILES, files);
    }

    public String getSourceMomentId() {
        return getString(KEY_SOURCE_MOMENT_ID);
    }

    public void setSourceMomentId(String momentId) {
        put(KEY_SOURCE_MOMENT_ID, momentId);
    }
}
