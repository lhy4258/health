package com.example.health.data.api;

import java.util.List;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;

/**
 * 群组 REST API 接口定义（基于 Retrofit）。
 * 提供群组列表查询、创建、更新名称、删除等功能。
 * 群组的实际 IM 通信由 LeanCloud IM SDK 承载，本 API 仅管理群组的元数据和成员关系。
 */
public interface GroupApi {

    /** 群组信息实体 */
    class Group {
        /** 群组唯一标识 */
        public String id;
        /** 群组名称 */
        public String name;
        /** 当前成员数量 */
        public int memberCount;
    }

    /** 创建群组的请求体 */
    class CreateGroupPayload {
        /** 群组名称 */
        public String name;
        /** 初始成员的用户 ID 列表 */
        public List<String> memberIds;
    }

    /** 更新群组信息的请求体（用于重命名） */
    class UpdateGroupPayload {
        /** 目标群组 ID */
        public String groupId;
        /** 新的群组名称 */
        public String name;
    }

    /** 删除群组的请求体 */
    class DeleteGroupPayload {
        /** 要删除的群组 ID */
        public String groupId;
    }

    /**
     * 获取当前用户参与的所有群组列表。
     * GET /groups/list
     * @return 群组列表
     */
    @GET("groups/list")
    Call<List<Group>> list();

    /**
     * 创建新群组并指定初始成员。
     * POST /groups/create
     * @param payload 含群组名称和初始成员 ID 列表
     * @return 创建成功的群组信息
     */
    @POST("groups/create")
    Call<Group> create(@Body CreateGroupPayload payload);

    /**
     * 更新群组信息（当前仅支持重命名）。
     * POST /groups/update
     * @param payload 含群组 ID 和新名称
     * @return 更新后的群组信息
     */
    @POST("groups/update")
    Call<Group> update(@Body UpdateGroupPayload payload);

    /**
     * 退出/解散群组。
     * POST /groups/delete
     * @param payload 含群组 ID
     * @return 无返回体
     */
    @POST("groups/delete")
    Call<Void> delete(@Body DeleteGroupPayload payload);
}
