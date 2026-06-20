package com.example.health.data.model;

import cn.leancloud.LCObject;
import cn.leancloud.LCUser;
import cn.leancloud.annotation.LCClassName;

/**
 * 评论数据模型，对应 LeanCloud Comment 表。
 * 存储动态的评论信息，关联评论作者、评论内容和所属动态。
 */
@LCClassName("Comment")
public class Comment extends LCObject {
    public static final String KEY_CONTENT = "content";
    public static final String KEY_AUTHOR = "author";
    public static final String KEY_MOMENT = "moment";

    public String getContent() {
        return getString(KEY_CONTENT);
    }

    public void setContent(String content) {
        put(KEY_CONTENT, content);
    }

    public LCUser getAuthor() {
        return getLCObject(KEY_AUTHOR);
    }

    public void setAuthor(LCUser author) {
        put(KEY_AUTHOR, author);
    }

    public Moment getMoment() {
        return getLCObject(KEY_MOMENT);
    }

    public void setMoment(Moment moment) {
        put(KEY_MOMENT, moment);
    }
}
