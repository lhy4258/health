package com.example.health.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.health.R;
import com.bumptech.glide.Glide;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Date;
import java.text.SimpleDateFormat;
import java.util.Locale;

import cn.leancloud.LCObject;
import cn.leancloud.LCFile;
import cn.leancloud.LCQuery;
import cn.leancloud.LCUser;
import cn.leancloud.livequery.LCLiveQuery;
import cn.leancloud.livequery.LCLiveQueryEventHandler;
import cn.leancloud.livequery.LCLiveQuerySubscribeCallback;
import cn.leancloud.LCException;
import io.reactivex.Observer;
import io.reactivex.disposables.Disposable;

/**
 * 消息/会话列表页，展示用户的所有私聊会话（从 UserConversation 表查询）。
 * 每个会话显示对方头像、昵称、最后一条消息预览和时间，通过 LiveQuery 实时更新用户信息。
 */
public class MessagesFragment extends Fragment {
    private SwipeRefreshLayout swipeRefreshLayout;
    private RecyclerView recyclerView;
    private ConversationAdapter adapter;
    private final List<ConversationItem> items = new ArrayList<>();
    private LCLiveQuery liveQuery;
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("MM-dd HH:mm", Locale.getDefault());

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_messages, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        swipeRefreshLayout = view.findViewById(R.id.swipeRefreshLayout);
        recyclerView = view.findViewById(R.id.recyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new ConversationAdapter(items);
        recyclerView.setAdapter(adapter);
        if (swipeRefreshLayout != null) {
            swipeRefreshLayout.setOnRefreshListener(() -> loadData(true));
        }
        loadData(false);
    }

    @Override
    public void onResume() {
        super.onResume();
        loadData(false);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (liveQuery != null) {
            liveQuery.unsubscribeInBackground(new LCLiveQuerySubscribeCallback() {
                @Override
                public void done(LCException e) {}
            });
            liveQuery = null;
        }
    }

    private void loadData() {
        loadData(false);
    }

    private void loadData(boolean fromRefresh) {
        if (!isAdded()) {
            return;
        }
        LCUser currentUser = LCUser.currentUser();
        if (currentUser == null) {
            items.clear();
            items.add(new ConversationItem("我的消息", "请先登录查看会话", "", 0, null, null, null, null));
            adapter.notifyDataSetChanged();
            if (fromRefresh && swipeRefreshLayout != null) {
                swipeRefreshLayout.setRefreshing(false);
            }
            return;
        }
        String selfId = currentUser.getObjectId();
        if (selfId == null || selfId.isEmpty()) {
            items.clear();
            items.add(new ConversationItem("我的消息", "用户信息无效", "", 0, null, null, null, null));
            adapter.notifyDataSetChanged();
            if (fromRefresh && swipeRefreshLayout != null) {
                swipeRefreshLayout.setRefreshing(false);
            }
            return;
        }
        LCQuery<LCObject> query = new LCQuery<>("UserConversation");
        query.whereEqualTo("owner", currentUser);
        query.orderByDescending("updatedAt");
        query.include("otherUser");
        query.findInBackground().subscribe(new Observer<List<LCObject>>() {
            @Override
            public void onSubscribe(Disposable d) {
            }

            @Override
            public void onNext(List<LCObject> list) {
                if (!isAdded()) {
                    return;
                }
                if (list == null || list.isEmpty()) {
                    requireActivity().runOnUiThread(() -> {
                        items.clear();
                        items.add(new ConversationItem("我的消息", "暂无会话", "", 0, null, null, null, null));
                        adapter.notifyDataSetChanged();
                        if (fromRefresh && swipeRefreshLayout != null) {
                            swipeRefreshLayout.setRefreshing(false);
                        }
                    });
                    return;
                }

                List<String> userIds = new ArrayList<>();
                for (LCObject obj : list) {
                    if (obj == null) continue;
                    LCUser otherUser = obj.getLCObject("otherUser");
                    if (otherUser != null) {
                        userIds.add(otherUser.getObjectId());
                    }
                }

                LCQuery<LCUser> userQuery = LCUser.getQuery();
                userQuery.whereContainedIn("objectId", userIds);
                userQuery.findInBackground().subscribe(new Observer<List<LCUser>>() {
                    @Override
                    public void onSubscribe(Disposable d) {}

                    @Override
                    public void onNext(List<LCUser> users) {
                        updateUI(list, users);
                    }

                    @Override
                    public void onError(Throwable e) {
                        updateUI(list, null);
                    }

                    @Override
                    public void onComplete() {}
                });
            }

            @Override
            public void onError(Throwable e) {
                if (!isAdded()) {
                    return;
                }
                if (e != null) {
                    boolean isClassMissing = false;
                    if (e instanceof cn.leancloud.LCException) {
                        if (((cn.leancloud.LCException) e).getCode() == 101) {
                            isClassMissing = true;
                        }
                    }
                    
                    if (!isClassMissing) {
                        e.printStackTrace();
                        String msg = e.getMessage();
                        if (msg != null && !msg.isEmpty()) {
                            android.widget.Toast.makeText(requireContext(), "加载会话失败: " + msg, android.widget.Toast.LENGTH_SHORT).show();
                        }
                    }
                }
                requireActivity().runOnUiThread(() -> {
                    items.clear();
                    items.add(new ConversationItem("我的消息", "暂无会话", "", 0, null, null, null, null));
                    adapter.notifyDataSetChanged();
                    if (fromRefresh && swipeRefreshLayout != null) {
                        swipeRefreshLayout.setRefreshing(false);
                    }
                });
            }

            @Override
            public void onComplete() {
            }
        });
    }

    private void updateUI(List<LCObject> list, List<LCUser> users) {
        if (!isAdded()) return;
        requireActivity().runOnUiThread(() -> {
            items.clear();
            Map<String, LCUser> userMap = new HashMap<>();
            if (users != null) {
                for (LCUser u : users) {
                    userMap.put(u.getObjectId(), u);
                }
            }

            List<String> userIds = new ArrayList<>();
            for (LCObject obj : list) {
                if (obj == null) continue;
                String conversationId = obj.getString("conversationId");
                if (conversationId == null || conversationId.isEmpty()) continue;
                
                LCUser otherUserPtr = obj.getLCObject("otherUser");
                String otherUserId = otherUserPtr != null ? otherUserPtr.getObjectId() : null;
                if (otherUserId != null) userIds.add(otherUserId);
                
                LCUser freshUser = otherUserId != null ? userMap.get(otherUserId) : null;
                LCUser displayUser = freshUser != null ? freshUser : otherUserPtr;

                String title = "";
                if (displayUser != null) {
                    title = displayUser.getUsername();
                }
                if (title == null || title.isEmpty()) {
                    title = obj.getString("otherName");
                }
                if (title == null || title.isEmpty()) {
                    title = "会话";
                }
                
                String lastMessage = obj.getString("lastMessage");
                String preview = buildPreview(lastMessage);
                Date updatedAt = obj.getUpdatedAt();
                String timeText = "";
                if (updatedAt != null) {
                    timeText = timeFormat.format(updatedAt);
                }
                String avatarUrl = null;
                if (displayUser != null) {
                    LCFile avatar = displayUser.getLCFile("avatar");
                    if (avatar != null && avatar.getUrl() != null) {
                        avatarUrl = avatar.getUrl();
                    }
                }
                items.add(new ConversationItem(title, preview, timeText, 0, conversationId, avatarUrl, otherUserId, obj.getString("otherName")));
            }
            subscribeToUserUpdates(userIds);
            adapter.notifyDataSetChanged();
            if (swipeRefreshLayout != null) {
                swipeRefreshLayout.setRefreshing(false);
            }
        });
    }

    private void subscribeToUserUpdates(List<String> userIds) {
        if (userIds == null || userIds.isEmpty()) return;
        if (liveQuery != null) {
            liveQuery.unsubscribeInBackground(new LCLiveQuerySubscribeCallback() {
                @Override
                public void done(LCException e) {
                }
            });
        }
        LCQuery<LCUser> userQuery = LCUser.getQuery();
        userQuery.whereContainedIn("objectId", userIds);
        liveQuery = LCLiveQuery.initWithQuery(userQuery);
        liveQuery.setEventHandler(new LCLiveQueryEventHandler() {
            @Override
            public void onObjectUpdated(LCObject object, List<String> updatedKeys) {
                if (!isAdded()) return;
                requireActivity().runOnUiThread(() -> {
                    boolean matched = false;
                    for (int i = 0; i < items.size(); i++) {
                        ConversationItem item = items.get(i);
                        if (item.userId != null && item.userId.equals(object.getObjectId())) {
                            matched = true;
                            String newName = object.getString("username");
                            if (newName != null && !newName.equals(item.title)) {
                                item.title = newName;
                                adapter.notifyItemChanged(i);
                            }
                            // Also update avatar if changed
                            LCFile avatar = object.getLCFile("avatar");
                            if (avatar != null && avatar.getUrl() != null) {
                                if (!avatar.getUrl().equals(item.avatarUrl)) {
                                    item.avatarUrl = avatar.getUrl();
                                    adapter.notifyItemChanged(i);
                                }
                            }
                        }
                    }
                    /*
                    if (!matched) {
                         // Optional: Debug toast
                         // android.widget.Toast.makeText(requireContext(), "收到更新但未匹配: " + object.getObjectId(), android.widget.Toast.LENGTH_SHORT).show();
                    }
                    */
                });
            }
        });
        liveQuery.subscribeInBackground(new LCLiveQuerySubscribeCallback() {
            @Override
            public void done(LCException e) {
            }
        });
    }

    private String buildPreview(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        int maxChars = 20;
        int maxUnits = 40;
        int units = 0;
        StringBuilder sb = new StringBuilder();
        int len = text.length();
        for (int i = 0; i < len; i++) {
            char c = text.charAt(i);
            int add = c <= 0x007F ? 1 : 2;
            if (i >= maxChars || units + add > maxUnits) {
                sb.append(".....");
                return sb.toString();
            }
            sb.append(c);
            units += add;
        }
        return sb.toString();
    }

    static class ConversationItem {
        String title;
        String preview;
        String timeText;
        int unreadCount;
        String conversationId;
        String avatarUrl;
        String userId;
        String remarkName;

        ConversationItem(String title, String preview, String timeText, int unreadCount, String conversationId, String avatarUrl, String userId, String remarkName) {
            this.title = title;
            this.preview = preview;
            this.timeText = timeText;
            this.unreadCount = unreadCount;
            this.conversationId = conversationId;
            this.avatarUrl = avatarUrl;
            this.userId = userId;
            this.remarkName = remarkName;
        }
    }

    static class ConversationAdapter extends RecyclerView.Adapter<ConversationAdapter.ViewHolder> {
        private final List<ConversationItem> data;
        ConversationAdapter(List<ConversationItem> data) {
            this.data = data;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_conversation, parent, false);
            return new ViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            ConversationItem item = data.get(position);
            holder.title.setText(item.title);
            holder.preview.setText(item.preview);
            holder.time.setText(item.timeText != null ? item.timeText : "");
            if (item.avatarUrl != null && !item.avatarUrl.isEmpty()) {
                Glide.with(holder.avatar.getContext()).load(item.avatarUrl).centerCrop().into(holder.avatar);
            } else {
                holder.avatar.setImageResource(R.drawable.ic_nav_profile);
            }
            if (item.unreadCount > 0) {
                holder.unread.setVisibility(View.VISIBLE);
                holder.unread.setText(String.valueOf(item.unreadCount));
            } else {
                holder.unread.setVisibility(View.GONE);
            }
            holder.itemView.setOnClickListener(v -> {
                int pos = holder.getAdapterPosition();
                if (pos == RecyclerView.NO_POSITION) {
                    return;
                }
                ConversationItem it = data.get(pos);
                if (it == null || it.conversationId == null || it.conversationId.isEmpty()) {
                    return;
                }
                android.content.Context context = holder.itemView.getContext();
                android.content.Intent intent = new android.content.Intent(context, GroupChatActivity.class);
                intent.putExtra("conversationId", it.conversationId);
                intent.putExtra("title", it.title);
                context.startActivity(intent);
            });
        }

        @Override
        public int getItemCount() {
            return data.size();
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            TextView title;
            TextView preview;
            TextView time;
            TextView unread;
            ImageView avatar;
            ViewHolder(@NonNull View itemView) {
                super(itemView);
                title = itemView.findViewById(R.id.tvTitle);
                preview = itemView.findViewById(R.id.tvPreview);
                time = itemView.findViewById(R.id.tvTime);
                unread = itemView.findViewById(R.id.tvUnread);
                avatar = itemView.findViewById(R.id.ivAvatar);
            }
        }
    }
}
