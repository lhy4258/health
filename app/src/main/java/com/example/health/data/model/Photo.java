package com.example.health.data.model;

import cn.leancloud.LCFile;
import cn.leancloud.LCObject;
import cn.leancloud.LCUser;
import cn.leancloud.annotation.LCClassName;

/**
 * 照片数据模型，对应 LeanCloud Photo 表。
 * 存储相册中的照片，包含原图、缩略图、所属相册和所有者信息。
 */
@LCClassName("Photo")
public class Photo extends LCObject {
    public static final String KEY_ALBUM = "album";
    public static final String KEY_OWNER = "owner";
    public static final String KEY_IMAGE = "image";
    public static final String KEY_THUMBNAIL = "thumbnail";

    public Album getAlbum() {
        return getLCObject(KEY_ALBUM);
    }

    public void setAlbum(Album album) {
        put(KEY_ALBUM, album);
    }

    public LCUser getOwner() {
        return getLCObject(KEY_OWNER);
    }

    public void setOwner(LCUser owner) {
        put(KEY_OWNER, owner);
    }

    public LCFile getImage() {
        return getLCFile(KEY_IMAGE);
    }

    public void setImage(LCFile image) {
        put(KEY_IMAGE, image);
    }

    public LCFile getThumbnail() {
        return getLCFile(KEY_THUMBNAIL);
    }

    public void setThumbnail(LCFile thumbnail) {
        put(KEY_THUMBNAIL, thumbnail);
    }
}

