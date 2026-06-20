package com.example.health.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.health.R;
import com.example.health.data.ProfileLikeManager;
import com.example.health.data.model.Moment;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import cn.leancloud.LCException;
import cn.leancloud.LCFile;
import cn.leancloud.LCObject;
import cn.leancloud.LCQuery;
import cn.leancloud.LCUser;
import cn.leancloud.livequery.LCLiveQuery;
import cn.leancloud.livequery.LCLiveQueryEventHandler;
import cn.leancloud.livequery.LCLiveQuerySubscribeCallback;
import cn.leancloud.im.v2.LCIMClient;
import cn.leancloud.im.v2.LCIMConversation;
import cn.leancloud.im.v2.LCIMException;
import cn.leancloud.im.v2.callback.LCIMClientCallback;
import cn.leancloud.im.v2.callback.LCIMConversationCreatedCallback;
import io.reactivex.Observer;
import io.reactivex.disposables.Disposable;
import com.example.health.data.model.ProfileLike;

/**
 * 他人用户主页，展示指定用户的头像、昵称、签名、主页点赞数和被点赞数。
 * 支持发送好友请求、发起私聊、查看动态列表，通过 LeanCloud LiveQuery 实时更新用户信息与点赞数据。
 */
public class UserProfileActivity extends AppCompatActivity {

    public static final String EXTRA_USER_ID = "user_id";

    private static final long CLICK_DEBOUNCE_INTERVAL_MS = 15;

    private ImageView ivAvatar;
    private TextView tvUsername;
    private TextView tvSignature;
    private View likeContainer;
    private TextView textLikeCount;
    private TextView textTodayLikeCount;
    private Button btnAddFriend;
    private Button btnChat;
    private Button btnMoments;
    private RecyclerView recyclerView;
    private MomentsAdapter adapter;
    private String userId;
    private LCLiveQuery liveQuery;
    private LCLiveQuery likeLiveQuery;
    private long lastProfileLikeClickTime = 0L;
    private boolean profileLikeInFlight = false;
    private int pendingProfileLikeClicks = 0;
    private final ProfileLikeManager profileLikeManager = new ProfileLikeManager();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_user_profile);

        userId = getIntent().getStringExtra(EXTRA_USER_ID);
        if (userId == null) {
            finish();
            return;
        }

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowHomeEnabled(true);
            getSupportActionBar().setTitle("");
        }
        toolbar.setNavigationOnClickListener(v -> onBackPressed());

        ivAvatar = findViewById(R.id.ivAvatar);
        tvUsername = findViewById(R.id.tvUsername);
        tvSignature = findViewById(R.id.tvSignature);
        likeContainer = findViewById(R.id.likeContainer);
        textLikeCount = findViewById(R.id.textLikeCount);
        textTodayLikeCount = findViewById(R.id.textTodayLikeCount);
        btnAddFriend = findViewById(R.id.btnAddFriend);
        btnChat = findViewById(R.id.btnChat);
        btnMoments = findViewById(R.id.btnMoments);
        recyclerView = findViewById(R.id.recyclerView);

        if (likeContainer != null) {
            likeContainer.setOnClickListener(v -> handleProfileLikeClick());
        }

        btnMoments.setOnClickListener(v -> {
            Intent intent = new Intent(UserProfileActivity.this, MomentsActivity.class);
            intent.putExtra(MomentsActivity.EXTRA_USER_ID, userId);
            intent.putExtra(MomentsActivity.EXTRA_USER_NAME, tvUsername.getText().toString());
            startActivity(intent);
        });

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new MomentsAdapter();
        adapter.setOnMomentInteractionListener(new MomentsAdapter.OnMomentInteractionListener() {
            @Override
            public void onLikeClick(Moment moment, int position) {
                 // Implement like logic if needed, or disable interaction
                 // For consistency, we can replicate the logic or leave it empty for read-only view
                 // Let's assume we want to allow liking here too
                 likeMoment(moment, position);
            }

            @Override
            public void onCommentClick(Moment moment, int position) {
                Intent intent = new Intent(UserProfileActivity.this, MomentDetailActivity.class);
                intent.putExtra(MomentDetailActivity.EXTRA_MOMENT_ID, moment.getObjectId());
                startActivity(intent);
            }

            @Override
            public void onShareClick(Moment moment, int position) {
                // Share logic
            }

            @Override
            public void onMoreClick(Moment moment, android.view.View anchor, int position) {
                // More logic
            }

            @Override
            public void onAvatarClick(LCUser user) {
                // Already in user profile, do nothing or refresh if different user
            }

            @Override
            public void onItemClick(Moment moment, int position) {
                Intent intent = new Intent(UserProfileActivity.this, MomentDetailActivity.class);
                intent.putExtra(MomentDetailActivity.EXTRA_MOMENT_ID, moment.getObjectId());
                startActivity(intent);
            }
        });
        recyclerView.setAdapter(adapter);

        setupAddFriendButton();
        setupChatButton();
        loadUserInfo();
        loadUserMoments();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (liveQuery != null) {
            liveQuery.unsubscribeInBackground(new LCLiveQuerySubscribeCallback() {
                @Override
                public void done(LCException e) {}
            });
            liveQuery = null;
        }
        if (likeLiveQuery != null) {
            likeLiveQuery.unsubscribeInBackground(new LCLiveQuerySubscribeCallback() {
                @Override
                public void done(LCException e) {}
            });
            likeLiveQuery = null;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshTodayLikeCount();
        refreshTotalLikeCount();
    }

    private void loadUserInfo() {
        LCQuery<LCUser> query = LCUser.getQuery();
        query.getInBackground(userId).subscribe(new Observer<LCUser>() {
            @Override
            public void onSubscribe(Disposable d) {}

            @Override
            public void onNext(LCUser user) {
                subscribeToUserUpdate(user.getObjectId());
                subscribeToLikeUpdate(user.getObjectId());
                String username = user.getUsername();
                if (username == null || username.isEmpty()) username = "用户";
                tvUsername.setText(username);

                String signature = user.getString("signature");
                if (signature != null && !signature.isEmpty()) {
                    tvSignature.setText(signature);
                }

                LCFile avatar = user.getLCFile("avatar");
                if (avatar != null && avatar.getUrl() != null) {
                    Glide.with(UserProfileActivity.this)
                            .load(avatar.getUrl())
                            .placeholder(R.drawable.ic_nav_profile)
                            .circleCrop()
                            .into(ivAvatar);
                }

                refreshTodayLikeCount();
                refreshTotalLikeCount();
            }

            @Override
            public void onError(Throwable e) {
                Toast.makeText(UserProfileActivity.this, "加载用户信息失败", Toast.LENGTH_SHORT).show();
                refreshTodayLikeCount();
                refreshTotalLikeCount();
            }

            @Override
            public void onComplete() {}
        });
    }

    private void subscribeToUserUpdate(String targetUserId) {
        if (targetUserId == null) return;
        if (liveQuery != null) {
            liveQuery.unsubscribeInBackground(new LCLiveQuerySubscribeCallback() {
                @Override
                public void done(LCException e) {}
            });
        }
        LCQuery<LCUser> userQuery = LCUser.getQuery();
        userQuery.whereEqualTo("objectId", targetUserId);
        liveQuery = LCLiveQuery.initWithQuery(userQuery);
        liveQuery.setEventHandler(new LCLiveQueryEventHandler() {
            @Override
            public void onObjectUpdated(LCObject object, List<String> updatedKeys) {
                if (isFinishing() || isDestroyed()) return;
                runOnUiThread(() -> {
                    String username = object.getString("username");
                    if (username != null && !username.isEmpty()) {
                        tvUsername.setText(username);
                    }
                    String signature = object.getString("signature");
                    if (signature != null && !signature.isEmpty()) {
                         tvSignature.setText(signature);
                    }
                    LCFile avatar = object.getLCFile("avatar");
                    if (avatar != null && avatar.getUrl() != null) {
                        Glide.with(UserProfileActivity.this)
                                .load(avatar.getUrl())
                                .placeholder(R.drawable.ic_nav_profile)
                                .circleCrop()
                                .into(ivAvatar);
                    }
                });
            }
        });
        liveQuery.subscribeInBackground(new LCLiveQuerySubscribeCallback() {
            @Override
            public void done(LCException e) {}
        });
    }

    private void subscribeToLikeUpdate(String targetUserId) {
        if (targetUserId == null || targetUserId.isEmpty()) {
            return;
        }
        if (likeLiveQuery != null) {
            likeLiveQuery.unsubscribeInBackground(new LCLiveQuerySubscribeCallback() {
                @Override
                public void done(LCException e) {}
            });
        }
        LCUser targetUser;
        try {
            targetUser = LCUser.createWithoutData(LCUser.class, targetUserId);
        } catch (LCException e) {
            return;
        }
        LCQuery<ProfileLike> q = LCQuery.getQuery(ProfileLike.class);
        q.whereEqualTo(ProfileLike.KEY_TO_USER, targetUser);
        likeLiveQuery = LCLiveQuery.initWithQuery(q);
        likeLiveQuery.setEventHandler(new LCLiveQueryEventHandler() {
            @Override
            public void onObjectCreated(LCObject object) {
                if (isFinishing() || isDestroyed()) return;
                runOnUiThread(() -> {
                    profileLikeManager.invalidateCounts(targetUserId);
                    refreshTotalLikeCount();
                });
            }
        });
        likeLiveQuery.subscribeInBackground(new LCLiveQuerySubscribeCallback() {
            @Override
            public void done(LCException e) {}
        });
    }

    private void refreshTotalLikeCount() {
        if (textLikeCount == null || userId == null || userId.isEmpty()) {
            return;
        }
        profileLikeManager.loadTotalCount(userId, new ProfileLikeManager.TotalCountCallback() {
            @Override
            public void onSuccess(int totalCount) {
                runOnUiThread(() -> {
                    if (textLikeCount != null) {
                        textLikeCount.setText(String.valueOf(totalCount));
                    }
                });
            }

            @Override
            public void onError(String message) {
            }
        });
    }

    private void refreshTodayLikeCount() {
        if (textTodayLikeCount == null || userId == null || userId.isEmpty()) {
            return;
        }

        LCUser currentUser = LCUser.currentUser();
        if (currentUser == null) {
            updateTodayLikeText(0);
            setProfileLikeEnabled(false);
            return;
        }

        profileLikeManager.loadTodayCount(userId, new ProfileLikeManager.TodayCountCallback() {
            @Override
            public void onSuccess(int todayCount) {
                runOnUiThread(() -> {
                    updateTodayLikeText(todayCount);
                    setProfileLikeEnabled(todayCount < ProfileLikeManager.MAX_LIKES_PER_DAY);
                });
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> {
                    updateTodayLikeText(0);
                    setProfileLikeEnabled(true);
                });
            }
        });
    }

    private void updateTodayLikeText(int todayCount) {
        if (textTodayLikeCount == null) {
            return;
        }
        if (todayCount >= ProfileLikeManager.MAX_LIKES_PER_DAY) {
            textTodayLikeCount.setText("今日已赞：" + ProfileLikeManager.MAX_LIKES_PER_DAY + "/" + ProfileLikeManager.MAX_LIKES_PER_DAY + "（已达上限）");
        } else {
            textTodayLikeCount.setText("今日已赞：" + todayCount + "/" + ProfileLikeManager.MAX_LIKES_PER_DAY);
        }
    }

    private void setProfileLikeEnabled(boolean enabled) {
        if (likeContainer == null) {
            return;
        }
        likeContainer.setEnabled(enabled);
        likeContainer.setAlpha(enabled ? 1f : 0.5f);
    }

    private void handleProfileLikeClick() {
        long now = System.currentTimeMillis();
        if (now - lastProfileLikeClickTime < CLICK_DEBOUNCE_INTERVAL_MS) {
            return;
        }
        lastProfileLikeClickTime = now;

        LCUser currentUser = LCUser.currentUser();
        if (currentUser == null) {
            Toast.makeText(this, "请先登录后再点赞", Toast.LENGTH_SHORT).show();
            setProfileLikeEnabled(false);
            return;
        }
        if (userId == null || userId.isEmpty()) {
            Toast.makeText(this, "用户信息无效", Toast.LENGTH_SHORT).show();
            return;
        }

        if (pendingProfileLikeClicks >= ProfileLikeManager.MAX_LIKES_PER_DAY) {
            return;
        }
        pendingProfileLikeClicks += 1;
        drainProfileLikeQueue();
    }

    private void drainProfileLikeQueue() {
        if (profileLikeInFlight) {
            return;
        }
        if (pendingProfileLikeClicks <= 0) {
            return;
        }
        if (userId == null || userId.isEmpty()) {
            pendingProfileLikeClicks = 0;
            return;
        }

        LCUser currentUser = LCUser.currentUser();
        if (currentUser == null) {
            pendingProfileLikeClicks = 0;
            setProfileLikeEnabled(false);
            return;
        }

        profileLikeInFlight = true;
        pendingProfileLikeClicks -= 1;

        profileLikeManager.like(userId, new ProfileLikeManager.LikeCallback() {
            @Override
            public void onSuccess(int todayCount, int totalCount) {
                runOnUiThread(() -> {
                    profileLikeInFlight = false;
                    updateTodayLikeText(todayCount);
                    boolean canLikeMore = todayCount < ProfileLikeManager.MAX_LIKES_PER_DAY;
                    setProfileLikeEnabled(canLikeMore);

                    if (textLikeCount != null) {
                        textLikeCount.setText(String.valueOf(totalCount));
                    }

                    if (likeContainer != null) {
                        likeContainer.animate()
                                .scaleX(1.1f)
                                .scaleY(1.1f)
                                .setDuration(150)
                                .withEndAction(() -> likeContainer.animate().scaleX(1f).scaleY(1f).setDuration(150).start())
                                .start();
                    }

                    if (!canLikeMore) {
                        pendingProfileLikeClicks = 0;
                        return;
                    }
                    drainProfileLikeQueue();
                });
            }

            @Override
            public void onLimitReached(int todayCount, int totalCount) {
                runOnUiThread(() -> {
                    profileLikeInFlight = false;
                    pendingProfileLikeClicks = 0;
                    updateTodayLikeText(todayCount);
                    setProfileLikeEnabled(false);
                    if (textLikeCount != null) {
                        textLikeCount.setText(String.valueOf(totalCount));
                    }
                    Toast.makeText(UserProfileActivity.this, "今日点赞已达上限（" + ProfileLikeManager.MAX_LIKES_PER_DAY + "次）", Toast.LENGTH_SHORT).show();
                });
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> {
                    profileLikeInFlight = false;
                    pendingProfileLikeClicks = 0;
                    Toast.makeText(UserProfileActivity.this, message, Toast.LENGTH_SHORT).show();
                    refreshTodayLikeCount();
                    refreshTotalLikeCount();
                });
            }
        });
    }

    private void setupAddFriendButton() {
        LCUser currentUser = LCUser.currentUser();
        if (currentUser == null) {
            btnAddFriend.setEnabled(false);
            btnAddFriend.setText("请先登录");
            return;
        }
        if (userId != null && userId.equals(currentUser.getObjectId())) {
            btnAddFriend.setEnabled(false);
            btnAddFriend.setText("这是你自己");
            return;
        }
        try {
            LCUser targetUser = LCUser.createWithoutData(LCUser.class, userId);
            LCQuery<cn.leancloud.LCObject> q1 = new LCQuery<>("Friend");
            q1.whereEqualTo("user1", currentUser);
            q1.whereEqualTo("user2", targetUser);
            LCQuery<cn.leancloud.LCObject> q2 = new LCQuery<>("Friend");
            q2.whereEqualTo("user1", targetUser);
            q2.whereEqualTo("user2", currentUser);
            java.util.List<LCQuery<cn.leancloud.LCObject>> qs = new java.util.ArrayList<>();
            qs.add(q1);
            qs.add(q2);
            LCQuery<cn.leancloud.LCObject> query = LCQuery.or(qs);
            query.limit(1);
            query.findInBackground().subscribe(new Observer<java.util.List<cn.leancloud.LCObject>>() {
                @Override
                public void onSubscribe(Disposable d) {}

                @Override
                public void onNext(java.util.List<cn.leancloud.LCObject> result) {
                    boolean isFriend = result != null && !result.isEmpty();
                    if (isFriend) {
                        btnAddFriend.setEnabled(true);
                        btnAddFriend.setText("删除好友");
                        // btnAddFriend.setBackgroundColor(getResources().getColor(android.R.color.holo_red_light)); // Keep consistent style
                        btnAddFriend.setOnClickListener(v -> showDeleteConfirmationDialog());
                        btnMoments.setVisibility(android.view.View.VISIBLE);
                    } else {
                        btnAddFriend.setEnabled(true);
                        btnAddFriend.setText("添加好友");
                        // btnAddFriend.setBackgroundColor(getResources().getColor(R.color.primary_color)); // Restore color
                        btnAddFriend.setOnClickListener(v -> sendFriendRequest());
                        btnMoments.setVisibility(android.view.View.GONE);
                    }
                }

                @Override
                public void onError(Throwable e) {
                    btnAddFriend.setEnabled(true);
                    btnAddFriend.setText("添加好友");
                    btnAddFriend.setOnClickListener(v -> sendFriendRequest());
                    btnMoments.setVisibility(android.view.View.GONE);
                }

                @Override
                public void onComplete() {}
            });
        } catch (LCException e) {
            btnAddFriend.setEnabled(false);
            btnAddFriend.setText("操作失败");
        }
    }

    private void showDeleteConfirmationDialog() {
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("删除好友")
                .setMessage("确定要删除该好友吗？删除后聊天记录也将一并删除。")
                .setPositiveButton("删除", (dialog, which) -> deleteFriend())
                .setNegativeButton("取消", null)
                .show();
    }

    private void deleteFriend() {
        LCUser currentUser = LCUser.currentUser();
        if (currentUser == null || userId == null) return;
        
        try {
            LCUser targetUser = LCUser.createWithoutData(LCUser.class, userId);
            
            // 1. Delete Friend record
            LCQuery<cn.leancloud.LCObject> q1 = new LCQuery<>("Friend");
            q1.whereEqualTo("user1", currentUser);
            q1.whereEqualTo("user2", targetUser);
            LCQuery<cn.leancloud.LCObject> q2 = new LCQuery<>("Friend");
            q2.whereEqualTo("user1", targetUser);
            q2.whereEqualTo("user2", currentUser);
            java.util.List<LCQuery<cn.leancloud.LCObject>> qs = new java.util.ArrayList<>();
            qs.add(q1);
            qs.add(q2);
            LCQuery<cn.leancloud.LCObject> query = LCQuery.or(qs);
            query.findInBackground().subscribe(new Observer<List<LCObject>>() {
                @Override
                public void onSubscribe(Disposable d) {}

                @Override
                public void onNext(List<LCObject> list) {
                    if (list != null && !list.isEmpty()) {
                        LCObject.deleteAllInBackground(list).subscribe(new Observer<cn.leancloud.types.LCNull>() {
                            @Override
                            public void onSubscribe(Disposable d) {}
                            @Override
                            public void onNext(cn.leancloud.types.LCNull lcNull) {
                                deleteConversationRecord(currentUser, targetUser);
                            }
                            @Override
                            public void onError(Throwable e) {
                                Toast.makeText(UserProfileActivity.this, "删除好友失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                            }
                            @Override
                            public void onComplete() {}
                        });
                    } else {
                        // Maybe already deleted? try deleting conversation
                        deleteConversationRecord(currentUser, targetUser);
                    }
                }

                @Override
                public void onError(Throwable e) {
                    Toast.makeText(UserProfileActivity.this, "删除出错: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                }

                @Override
                public void onComplete() {}
            });

        } catch (LCException e) {
            e.printStackTrace();
        }
    }

    private void deleteConversationRecord(LCUser currentUser, LCUser targetUser) {
         // 2. Delete UserConversation record
         LCQuery<LCObject> q = new LCQuery<>("UserConversation");
         q.whereEqualTo("owner", currentUser);
         q.whereEqualTo("otherUser", targetUser);
         q.findInBackground().subscribe(new Observer<List<LCObject>>() {
             @Override
             public void onSubscribe(Disposable d) {}

             @Override
             public void onNext(List<LCObject> list) {
                 if (list != null && !list.isEmpty()) {
                     LCObject.deleteAllInBackground(list).subscribe(new Observer<cn.leancloud.types.LCNull>() {
                         @Override
                         public void onSubscribe(Disposable d) {}
                         @Override
                         public void onNext(cn.leancloud.types.LCNull lcNull) {
                             runOnUiThread(() -> {
                                 Toast.makeText(UserProfileActivity.this, "删除成功", Toast.LENGTH_SHORT).show();
                                 setupAddFriendButton(); // Refresh UI
                             });
                         }
                         @Override
                         public void onError(Throwable e) {
                             runOnUiThread(() -> {
                                 Toast.makeText(UserProfileActivity.this, "删除会话记录失败", Toast.LENGTH_SHORT).show();
                                 setupAddFriendButton(); // Refresh UI anyway
                             });
                         }
                         @Override
                         public void onComplete() {}
                     });
                 } else {
                     runOnUiThread(() -> {
                         Toast.makeText(UserProfileActivity.this, "删除成功", Toast.LENGTH_SHORT).show();
                         setupAddFriendButton();
                     });
                 }
             }

             @Override
             public void onError(Throwable e) {
                 // Ignore error if conversation not found or other issue
                 runOnUiThread(() -> {
                     Toast.makeText(UserProfileActivity.this, "删除成功", Toast.LENGTH_SHORT).show();
                     setupAddFriendButton();
                 });
             }

             @Override
             public void onComplete() {}
         });
    }

    private void setupChatButton() {
        LCUser currentUser = LCUser.currentUser();
        if (currentUser == null) {
            btnChat.setEnabled(false);
            btnChat.setText("请先登录");
            return;
        }
        if (userId != null && userId.equals(currentUser.getObjectId())) {
            btnChat.setEnabled(false);
            btnChat.setText("无法与自己聊天");
            return;
        }
        btnChat.setEnabled(true);
        btnChat.setText("聊天");
        btnChat.setOnClickListener(v -> startChat());
    }

    private void startChat() {
        LCUser currentUser = LCUser.currentUser();
        if (currentUser == null) {
            Toast.makeText(this, "请先登录", Toast.LENGTH_SHORT).show();
            return;
        }
        if (userId == null || userId.isEmpty()) {
            Toast.makeText(this, "用户信息无效", Toast.LENGTH_SHORT).show();
            return;
        }
        String selfId = currentUser.getObjectId();
        if (selfId == null || selfId.isEmpty()) {
            Toast.makeText(this, "用户信息无效", Toast.LENGTH_SHORT).show();
            return;
        }
        if (selfId.equals(userId)) {
            Toast.makeText(this, "不能和自己聊天", Toast.LENGTH_SHORT).show();
            return;
        }
        btnChat.setEnabled(false);
        LCIMClient client = LCIMClient.getInstance(selfId);
        client.open(new LCIMClientCallback() {
            @Override
            public void done(LCIMClient openedClient, LCIMException e) {
                if (e != null) {
                    runOnUiThread(() -> {
                        btnChat.setEnabled(true);
                        Toast.makeText(UserProfileActivity.this, "打开会话失败", Toast.LENGTH_SHORT).show();
                    });
                    return;
                }
                List<String> members = Arrays.asList(selfId, userId);
                openedClient.createConversation(members, null, null, false, true, new LCIMConversationCreatedCallback() {
                    @Override
                    public void done(LCIMConversation conversation, LCIMException e) {
                        if (conversation == null || e != null) {
                            runOnUiThread(() -> {
                                btnChat.setEnabled(true);
                                Toast.makeText(UserProfileActivity.this, "创建会话失败", Toast.LENGTH_SHORT).show();
                            });
                            return;
                        }
                        String conversationId = conversation.getConversationId();
                        saveUserConversationRecords(currentUser, userId, tvUsername.getText().toString(), conversationId);
                        runOnUiThread(() -> {
                            btnChat.setEnabled(true);
                            Intent intent = new Intent(UserProfileActivity.this, GroupChatActivity.class);
                            intent.putExtra("conversationId", conversationId);
                            intent.putExtra("title", tvUsername.getText().toString());
                            startActivity(intent);
                        });
                    }
                });
            }
        });
    }

    private void saveUserConversationRecords(LCUser selfUser, String otherUserId, String otherName, String conversationId) {
        if (selfUser == null || otherUserId == null || otherUserId.isEmpty() || conversationId == null || conversationId.isEmpty()) {
            return;
        }
        LCUser otherUser;
        try {
            otherUser = LCUser.createWithoutData(LCUser.class, otherUserId);
        } catch (LCException e) {
            otherUser = null;
        }
        final LCUser finalOtherUser = otherUser;
        LCQuery<LCObject> q1 = new LCQuery<>("UserConversation");
        q1.whereEqualTo("owner", selfUser);
        q1.whereEqualTo("conversationId", conversationId);
        q1.limit(1);
        q1.findInBackground().subscribe(new Observer<List<LCObject>>() {
            @Override
            public void onSubscribe(Disposable d) {
            }

            @Override
            public void onNext(List<LCObject> list) {
                boolean exists = list != null && !list.isEmpty();
                if (!exists) {
                    saveSelfConversation(selfUser, finalOtherUser, otherName, conversationId);
                }
                if (finalOtherUser != null) {
                    saveOtherUserConversation(finalOtherUser, selfUser, conversationId);
                }
            }

            @Override
            public void onError(Throwable e) {
                // If class does not exist (101), we should create it
                if (e instanceof cn.leancloud.LCException && ((cn.leancloud.LCException) e).getCode() == 101) {
                    saveSelfConversation(selfUser, finalOtherUser, otherName, conversationId);
                    if (finalOtherUser != null) {
                        saveOtherUserConversation(finalOtherUser, selfUser, conversationId);
                    }
                }
            }

            @Override
            public void onComplete() {
            }
        });
    }

    private void saveSelfConversation(LCUser selfUser, LCUser otherUser, String otherName, String conversationId) {
        LCObject obj = new LCObject("UserConversation");
        obj.put("owner", selfUser);
        if (otherUser != null) {
            obj.put("otherUser", otherUser);
        }
        if (otherName != null && !otherName.isEmpty()) {
            obj.put("otherName", otherName);
        }
        obj.put("conversationId", conversationId);
        obj.saveInBackground().subscribe(new Observer<LCObject>() {
            @Override
            public void onSubscribe(Disposable d) {}
            @Override
            public void onNext(LCObject lcObject) {}
            @Override
            public void onError(Throwable e) {
                // Log or show error if needed, but avoid blocking flow
            }
            @Override
            public void onComplete() {}
        });
    }

    private void saveOtherUserConversation(LCUser owner, LCUser otherUser, String conversationId) {
        LCQuery<LCObject> q2 = new LCQuery<>("UserConversation");
        q2.whereEqualTo("owner", owner);
        q2.whereEqualTo("conversationId", conversationId);
        q2.limit(1);
        q2.findInBackground().subscribe(new Observer<List<LCObject>>() {
            @Override
            public void onSubscribe(Disposable d) {}
            @Override
            public void onNext(List<LCObject> list2) {
                boolean exists2 = list2 != null && !list2.isEmpty();
                if (!exists2) {
                    createOtherUserRecord(owner, otherUser, conversationId);
                }
            }
            @Override
            public void onError(Throwable e) {
                 if (e instanceof cn.leancloud.LCException && ((cn.leancloud.LCException) e).getCode() == 101) {
                     createOtherUserRecord(owner, otherUser, conversationId);
                 }
            }
            @Override
            public void onComplete() {}
        });
    }

    private void createOtherUserRecord(LCUser owner, LCUser otherUser, String conversationId) {
        LCObject obj2 = new LCObject("UserConversation");
        obj2.put("owner", owner);
        obj2.put("otherUser", otherUser);
        String selfName = tvUsername.getText().toString(); // Use current user name or display name logic
        // But here 'owner' is the other person, 'otherUser' is me.
        // Wait, the logic in original code:
        // owner=finalOtherUser, otherUser=selfUser.
        // selfName = tvUsername.getText().toString() which is actually the other user's name displayed on screen?
        // Let's check original logic carefully.
        // Original: String selfName = tvUsername.getText().toString();
        // Context: UserProfileActivity shows the OTHER user's profile.
        // tvUsername is the OTHER user's name.
        // We want to save a record for the OTHER user, where 'otherName' is MY name.
        // So fetching tvUsername is WRONG if it's meant to be MY name.
        // But let's fix the name logic: for the other user, the "other name" is ME.
        // So we should use selfUser.getUsername() or similar.
        String myName = otherUser.getUsername();
        if (myName == null || myName.isEmpty()) {
            myName = "用户";
        }
        obj2.put("otherName", myName);

        obj2.put("conversationId", conversationId);
        obj2.saveInBackground().subscribe(new Observer<LCObject>() {
            @Override
            public void onSubscribe(Disposable d) {}
            @Override
            public void onNext(LCObject lcObject) {}
            @Override
            public void onError(Throwable e) {}
            @Override
            public void onComplete() {}
        });
    }

    private void sendFriendRequest() {
        LCUser currentUser = LCUser.currentUser();
        if (currentUser == null) {
            Toast.makeText(this, "请先登录", Toast.LENGTH_SHORT).show();
            return;
        }
        if (userId == null || userId.isEmpty()) {
            Toast.makeText(this, "发送失败：用户信息无效", Toast.LENGTH_SHORT).show();
            return;
        }
        if (userId.equals(currentUser.getObjectId())) {
            Toast.makeText(this, "不能添加自己为好友", Toast.LENGTH_SHORT).show();
            return;
        }
        LCUser targetUser;
        try {
            targetUser = LCUser.createWithoutData(LCUser.class, userId);
        } catch (LCException e) {
            Toast.makeText(this, "发送失败：" + e.getMessage(), Toast.LENGTH_SHORT).show();
            return;
        }
        checkExistingRequestAndCreate(currentUser, targetUser);
    }

    private void checkExistingRequestAndCreate(LCUser currentUser, LCUser targetUser) {
        LCQuery<cn.leancloud.LCObject> query = new LCQuery<>("FriendRequest");
        query.whereEqualTo("fromUser", currentUser);
        query.whereEqualTo("toUser", targetUser);
        query.whereEqualTo("status", "pending");
        query.limit(1);
        query.findInBackground().subscribe(new Observer<java.util.List<cn.leancloud.LCObject>>() {
            @Override
            public void onSubscribe(Disposable d) {}

            @Override
            public void onNext(java.util.List<cn.leancloud.LCObject> existing) {
                if (existing != null && !existing.isEmpty()) {
                    Toast.makeText(UserProfileActivity.this, "已发送过好友请求，请等待对方确认", Toast.LENGTH_SHORT).show();
                    return;
                }
                createFriendRequest(currentUser, targetUser);
            }

            @Override
            public void onError(Throwable e) {
                if (e instanceof LCException && ((LCException) e).getCode() == 101) {
                    createFriendRequest(currentUser, targetUser);
                } else {
                    Toast.makeText(UserProfileActivity.this, "发送失败：" + e.getMessage(), Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onComplete() {}
        });
    }

    private void createFriendRequest(LCUser currentUser, LCUser targetUser) {
        cn.leancloud.LCObject req = new cn.leancloud.LCObject("FriendRequest");
        req.put("fromUser", currentUser);
        req.put("toUser", targetUser);
        req.put("status", "pending");
        req.saveInBackground().subscribe(new Observer<cn.leancloud.LCObject>() {
            @Override
            public void onSubscribe(Disposable d) {}

            @Override
            public void onNext(cn.leancloud.LCObject lcObject) {
                Toast.makeText(UserProfileActivity.this, "已发送好友请求", Toast.LENGTH_SHORT).show();
                btnAddFriend.setEnabled(false);
                btnAddFriend.setText("已发送请求");
            }

            @Override
            public void onError(Throwable e) {
                Toast.makeText(UserProfileActivity.this, "发送失败：" + e.getMessage(), Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onComplete() {}
        });
    }

    private void loadUserMoments() {
        LCUser currentUser = LCUser.currentUser();
        if (currentUser != null && userId != null && !userId.equals(currentUser.getObjectId())) {
            adapter.setMoments(new ArrayList<>());
            return;
        }

        LCQuery<Moment> query = LCQuery.getQuery(Moment.class);
        query.include("author");
        try {
            LCUser author = LCUser.createWithoutData(LCUser.class, userId);
            query.whereEqualTo("author", author);
        } catch (LCException e) {
            Toast.makeText(this, "加载用户动态失败", Toast.LENGTH_SHORT).show();
            return;
        }
        query.orderByDescending("createdAt");
        query.findInBackground().subscribe(new Observer<List<Moment>>() {
            @Override
            public void onSubscribe(Disposable d) {}

            @Override
            public void onNext(List<Moment> moments) {
                adapter.setMoments(moments);
            }

            @Override
            public void onError(Throwable e) {
                Toast.makeText(UserProfileActivity.this, "加载动态失败", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onComplete() {}
        });
    }

    private void likeMoment(Moment moment, int position) {
         LCUser currentUser = LCUser.currentUser();
        if (currentUser == null) {
            Toast.makeText(this, "请先登录", Toast.LENGTH_SHORT).show();
            return;
        }

        List<String> likes = moment.getLikes();
        if (likes == null) {
            likes = new ArrayList<>();
            moment.setLikes(likes);
        }

        final List<String> likesRef = likes;
        final String currentUserId = currentUser.getObjectId();
        final boolean wasLiked = likesRef.contains(currentUserId);

        if (wasLiked) {
            likesRef.remove(currentUserId);
        } else {
            likesRef.add(currentUserId);
        }

        moment.setLikes(likesRef);
        adapter.notifyItemChanged(position);
        
        moment.saveInBackground().subscribe(new Observer<cn.leancloud.LCObject>() {
            @Override
            public void onSubscribe(Disposable d) {}
            @Override
            public void onNext(cn.leancloud.LCObject obj) {}
            @Override
            public void onError(Throwable e) {
                if (wasLiked) {
                    likesRef.add(currentUserId);
                } else {
                    likesRef.remove(currentUserId);
                }
                adapter.notifyItemChanged(position);
            }
            @Override
            public void onComplete() {}
        });
    }
}
