package com.example.health.data.api;

import java.util.List;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.Query;

/**
 * 好友关系 REST API 接口定义（基于 Retrofit）。
 * 提供好友搜索、列表查询、好友请求发送/接受/拒绝/删除等功能。
 * 所有接口均在 LeanCloud 云引擎上实现，客户端通过 Retrofit 动态代理调用。
 */
public interface FriendApi {

    /** 好友信息实体 */
    class Friend {
        /** 用户唯一标识 */
        public String id;
        /** 用户名 */
        public String username;
        /** 头像 URL */
        public String avatarUrl;
        /** 用户名拼音（用于搜索排序） */
        public String py;
        /** 是否在线 */
        public boolean online;
    }

    /** 好友请求实体 */
    class FriendRequest {
        /** 请求记录 ID */
        public String requestId;
        /** 发送方用户 ID */
        public String fromUserId;
        /** 接收方用户 ID */
        public String toUserId;
        /** 请求状态：pending（待处理）、accepted（已接受）、rejected（已拒绝） */
        public String status;
        /** 发送方用户名 */
        public String fromUsername;
        /** 接收方用户名 */
        public String toUsername;
        /** 发送方头像 URL */
        public String fromAvatarUrl;
        /** 接收方头像 URL */
        public String toAvatarUrl;
    }

    /** 发送好友请求的请求体 */
    class FriendRequestPayload {
        /** 目标用户 ID */
        public String toUserId;
    }

    /** 操作好友请求的请求体（接受/拒绝共用） */
    class FriendRequestActionPayload {
        /** 要操作的好友请求 ID */
        public String requestId;
    }

    /** 好友请求列表响应包装 */
    class FriendRequestsResponse {
        /** 好友请求列表 */
        public java.util.List<FriendRequest> requests;
    }

    /** 删除好友的请求体 */
    class DeleteFriendPayload {
        /** 要删除的好友用户 ID */
        public String friendId;
    }

    /**
     * 搜索用户（精确匹配用户名或拼音）。
     * GET /friends/search?q=关键字&amp;type=user
     * @param q    搜索关键字
     * @param type 搜索类型，如 "user" 表示搜索用户
     * @return 匹配的用户列表
     */
    @GET("friends/search")
    Call<List<Friend>> search(@Query("q") String q, @Query("type") String type);

    /**
     * 获取当前用户的好友列表。
     * GET /friends/list
     * @return 好友列表
     */
    @GET("friends/list")
    Call<List<Friend>> list();

    /**
     * 向指定用户发送好友请求。
     * POST /friends/requests
     * @param payload 含目标用户 ID
     * @return 创建的好友请求记录
     */
    @POST("friends/requests")
    Call<FriendRequest> sendRequest(@Body FriendRequestPayload payload);

    /**
     * 查询好友请求列表，可按方向和状态筛选。
     * GET /friends/requests?direction=received|sent&amp;status=pending|accepted|rejected
     * @param direction 方向：received（收到的）或 sent（发出的）
     * @param status    状态：pending、accepted、rejected
     * @return 好友请求列表
     */
    @GET("friends/requests")
    Call<FriendRequestsResponse> listRequests(@Query("direction") String direction, @Query("status") String status);

    /**
     * 接受一条好友请求。
     * POST /friends/requests/accept
     * @param payload 含好友请求 ID
     * @return 更新后的好友请求记录
     */
    @POST("friends/requests/accept")
    Call<FriendRequest> accept(@Body FriendRequestActionPayload payload);

    /**
     * 拒绝一条好友请求。
     * POST /friends/requests/reject
     * @param payload 含好友请求 ID
     * @return 更新后的好友请求记录
     */
    @POST("friends/requests/reject")
    Call<FriendRequest> reject(@Body FriendRequestActionPayload payload);

    /**
     * 删除好友关系。
     * POST /friends/delete
     * @param payload 含要删除的好友用户 ID
     * @return 无返回体
     */
    @POST("friends/delete")
    Call<Void> delete(@Body DeleteFriendPayload payload);
}
