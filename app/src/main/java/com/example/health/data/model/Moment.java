package com.example.health.data.model;

import cn.leancloud.LCFile;
import cn.leancloud.LCObject;
import cn.leancloud.LCUser;
import cn.leancloud.annotation.LCClassName;
import java.util.List;

/**
 * 动态数据模型，对应 LeanCloud Moment 表。
 * 存储用户发布的社区动态，包括文字内容、图片、点赞用户列表和评论数。
 */
@LCClassName("Moment")
public class Moment extends LCObject {
    public static final String KEY_CONTENT = "content";
    public static final String KEY_IMAGES = "images";
    public static final String KEY_AUTHOR = "author";
    public static final String KEY_LIKES = "likes";
    public static final String KEY_COMMENTS_COUNT = "commentsCount";

    public String getContent() {
        return getString(KEY_CONTENT);
    }

    public void setContent(String content) {
        put(KEY_CONTENT, content);
    }

    public List<LCFile> getImages() {
        return getList(KEY_IMAGES);
    }

    public void setImages(List<LCFile> images) {
        put(KEY_IMAGES, images);
    }

    public LCUser getAuthor() {
        return getLCObject(KEY_AUTHOR);
    }

    public void setAuthor(LCUser author) {
        put(KEY_AUTHOR, author);
    }

    public List<String> getLikes() {
        return getList(KEY_LIKES);
    }

    public void setLikes(List<String> likes) {
        put(KEY_LIKES, likes);
    }
    
    public int getCommentsCount() {
        return getInt(KEY_COMMENTS_COUNT);
    }
    
    public void setCommentsCount(int count) {
        put(KEY_COMMENTS_COUNT, count);
    }
}
