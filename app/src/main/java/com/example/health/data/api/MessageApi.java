package com.example.health.data.api;

import java.util.List;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.Query;

/**
 * 消息 REST API 接口定义（基于 Retrofit）。
 * 提供会话列表查询、聊天消息拉取、消息发送等功能。
 * 注意：实际聊天实时消息走 LeanCloud IM SDK，本 API 用于拉取历史消息和会话元数据。
 */
public interface MessageApi {

    /** 聊天消息实体 */
    class Message {
        /** 消息唯一标识 */
        public String id;
        /** 所属会话 ID（对应 LeanCloud IM 的 conversationId） */
        public String conversationId;
        /** 发送者用户 ID */
        public String senderId;
        /** 消息文本内容 */
        public String content;
        /** 消息时间戳（毫秒） */
        public long timestamp;
    }

    /** 发送消息的请求体 */
    class SendPayload {
        /** 目标会话 ID */
        public String conversationId;
        /** 消息文本内容 */
        public String content;
    }

    /**
     * 获取当前用户的所有会话 ID 列表。
     * GET /messages/conversations
     * @return 会话 ID 字符串列表
     */
    @GET("messages/conversations")
    Call<List<String>> listConversations();

    /**
     * 拉取指定会话的历史消息记录。
     * GET /messages/list?conversationId=xxx
     * @param conversationId 会话 ID
     * @return 该会话的历史消息列表
     */
    @GET("messages/list")
    Call<List<Message>> list(@Query("conversationId") String conversationId);

    /**
     * 发送一条消息到指定会话。
     * POST /messages/send
     * @param payload 含会话 ID 和消息内容
     * @return 发送成功的消息记录
     */
    @POST("messages/send")
    Call<Message> send(@Body SendPayload payload);
}

