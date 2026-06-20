package com.example.health.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.health.R;
import com.example.health.data.api.FriendApi;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import cn.leancloud.LCException;
import cn.leancloud.LCFile;
import cn.leancloud.LCObject;
import cn.leancloud.LCQuery;
import cn.leancloud.LCUser;
import cn.leancloud.im.v2.LCIMClient;
import cn.leancloud.im.v2.LCIMConversation;
import cn.leancloud.im.v2.callback.LCIMClientCallback;
import cn.leancloud.im.v2.callback.LCIMConversationCreatedCallback;
import cn.leancloud.im.v2.LCIMException;
import cn.leancloud.livequery.LCLiveQuery;
import cn.leancloud.livequery.LCLiveQueryEventHandler;
import cn.leancloud.livequery.LCLiveQuerySubscribeCallback;
import io.reactivex.Observer;
import io.reactivex.disposables.Disposable;

/**
 * 好友请求页，使用 Tab 切换"收到的请求"与"发出的请求"。
 * 收到的请求可接受/拒绝；发出的请求可撤销删除。
 * 通过 LeanCloud LiveQuery 实时更新请求中用户的头像和昵称。
 */
public class FriendRequestsFragment extends Fragment {
    private TabLayout tabLayout;
    private androidx.swiperefreshlayout.widget.SwipeRefreshLayout swipeRefresh;
    private RecyclerView recyclerView;
    private RequestAdapter adapter;
    private final List<FriendApi.FriendRequest> incoming = new ArrayList<>();
    private final List<FriendApi.FriendRequest> outgoing = new ArrayList<>();
    private int currentTab = 0; // 0 incoming, 1 outgoing
    private LCLiveQuery liveQuery;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_friend_requests, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        tabLayout = view.findViewById(R.id.tabLayoutRequests);
        swipeRefresh = view.findViewById(R.id.swipeRefresh);
        recyclerView = view.findViewById(R.id.recyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new RequestAdapter(incoming, new RequestAdapter.Listener() {
            @Override
            public void onAccept(FriendApi.FriendRequest req) {
                performAction(req, true);
            }
            @Override
            public void onReject(FriendApi.FriendRequest req) {
                performAction(req, false);
            }
            @Override
            public void onDelete(FriendApi.FriendRequest req) {
                deleteRequest(req);
            }
        });
        recyclerView.setAdapter(adapter);

        tabLayout.addTab(tabLayout.newTab().setText("收到的请求"));
        tabLayout.addTab(tabLayout.newTab().setText("发出的请求"));
        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                currentTab = tab.getPosition();
                updateAdapterData();
                loadRequests();
            }
            @Override
            public void onTabUnselected(TabLayout.Tab tab) {}
            @Override
            public void onTabReselected(TabLayout.Tab tab) {}
        });
        if (swipeRefresh != null) {
            swipeRefresh.setOnRefreshListener(this::loadRequests);
        }
        loadRequests();
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

    private void refreshSubscription() {
        java.util.Set<String> userIds = new java.util.HashSet<>();
        for (FriendApi.FriendRequest r : incoming) {
            if (r.fromUserId != null) userIds.add(r.fromUserId);
        }
        for (FriendApi.FriendRequest r : outgoing) {
            if (r.toUserId != null) userIds.add(r.toUserId);
        }
        subscribeToUserUpdates(new ArrayList<>(userIds));
    }

    private void subscribeToUserUpdates(List<String> userIds) {
        if (userIds == null || userIds.isEmpty()) return;
        if (liveQuery != null) {
            liveQuery.unsubscribeInBackground(new LCLiveQuerySubscribeCallback() {
                @Override
                public void done(LCException e) {}
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
                    String objectId = object.getObjectId();
                    String newName = object.getString("username");
                    LCFile avatar = object.getLCFile("avatar");
                    String newAvatarUrl = (avatar != null && avatar.getUrl() != null) ? avatar.getUrl() : null;
                    
                    boolean changed = false;
                    // Update incoming
                    for (int i = 0; i < incoming.size(); i++) {
                        FriendApi.FriendRequest item = incoming.get(i);
                        if (item.fromUserId != null && item.fromUserId.equals(objectId)) {
                            if (newName != null && !newName.equals(item.fromUsername)) {
                                item.fromUsername = newName;
                                changed = true;
                            }
                            if (newAvatarUrl != null && !newAvatarUrl.equals(item.fromAvatarUrl)) {
                                item.fromAvatarUrl = newAvatarUrl;
                                changed = true;
                            }
                        }
                    }
                    // Update outgoing
                    for (int i = 0; i < outgoing.size(); i++) {
                        FriendApi.FriendRequest item = outgoing.get(i);
                        if (item.toUserId != null && item.toUserId.equals(objectId)) {
                            if (newName != null && !newName.equals(item.toUsername)) {
                                item.toUsername = newName;
                                changed = true;
                            }
                            if (newAvatarUrl != null && !newAvatarUrl.equals(item.toAvatarUrl)) {
                                item.toAvatarUrl = newAvatarUrl;
                                changed = true;
                            }
                        }
                    }
                    
                    if (changed) {
                        adapter.notifyDataSetChanged();
                    }
                });
            }
        });
        liveQuery.subscribeInBackground(new LCLiveQuerySubscribeCallback() {
            @Override
            public void done(LCException e) {}
        });
    }

    private void loadRequests() {
        incoming.clear();
        outgoing.clear();
        LCUser currentUser = LCUser.currentUser();
        if (currentUser == null) {
            if (isAdded()) {
                Toast.makeText(requireContext(), "请先登录", Toast.LENGTH_SHORT).show();
                updateAdapterData();
            }
            return;
        }
        LCQuery<LCObject> incomingQuery = new LCQuery<>("FriendRequest");
        incomingQuery.whereEqualTo("toUser", currentUser);
        incomingQuery.include("fromUser");
        incomingQuery.include("toUser");
        incomingQuery.orderByDescending("createdAt");
        incomingQuery.findInBackground().subscribe(new Observer<List<LCObject>>() {
            @Override
            public void onSubscribe(Disposable d) {
            }

            @Override
            public void onNext(List<LCObject> result) {
                if (!isAdded()) {
                    return;
                }
                incoming.clear();
                if (result != null) {
                    for (LCObject obj : result) {
                        if (obj == null) {
                            continue;
                        }
                        FriendApi.FriendRequest r = new FriendApi.FriendRequest();
                        r.requestId = obj.getObjectId();
                        LCUser from = obj.getLCObject("fromUser");
                        LCUser to = obj.getLCObject("toUser");
                        if (from != null) {
                            r.fromUserId = from.getObjectId();
                            String uname = from.getUsername();
                            r.fromUsername = uname;
                            LCFile avatar = from.getLCFile("avatar");
                            if (avatar != null && avatar.getUrl() != null) {
                                r.fromAvatarUrl = avatar.getUrl();
                            }
                        }
                        if (to != null) {
                            r.toUserId = to.getObjectId();
                            String uname2 = to.getUsername();
                            r.toUsername = uname2;
                            LCFile avatar2 = to.getLCFile("avatar");
                            if (avatar2 != null && avatar2.getUrl() != null) {
                                r.toAvatarUrl = avatar2.getUrl();
                            }
                        }
                        r.status = obj.getString("status");
                        incoming.add(r);
                    }
                }
                refreshSubscription();
                updateAdapterData();
                if (swipeRefresh != null) {
                    swipeRefresh.setRefreshing(false);
                }
            }

            @Override
            public void onError(Throwable e) {
                if (!isAdded()) {
                    return;
                }
                Toast.makeText(requireContext(), "加载失败", Toast.LENGTH_SHORT).show();
                if (swipeRefresh != null) {
                    swipeRefresh.setRefreshing(false);
                }
            }

            @Override
            public void onComplete() {
            }
        });
        LCQuery<LCObject> outgoingQuery = new LCQuery<>("FriendRequest");
        outgoingQuery.whereEqualTo("fromUser", currentUser);
        outgoingQuery.include("fromUser");
        outgoingQuery.include("toUser");
        outgoingQuery.orderByDescending("createdAt");
        outgoingQuery.findInBackground().subscribe(new Observer<List<LCObject>>() {
            @Override
            public void onSubscribe(Disposable d) {
            }

            @Override
            public void onNext(List<LCObject> result) {
                if (!isAdded()) {
                    return;
                }
                outgoing.clear();
                if (result != null) {
                    for (LCObject obj : result) {
                        if (obj == null) {
                            continue;
                        }
                        FriendApi.FriendRequest r = new FriendApi.FriendRequest();
                        r.requestId = obj.getObjectId();
                        LCUser from = obj.getLCObject("fromUser");
                        LCUser to = obj.getLCObject("toUser");
                        if (from != null) {
                            r.fromUserId = from.getObjectId();
                            String uname = from.getUsername();
                            if (uname == null || uname.isEmpty()) {
                                uname = from.getEmail();
                            }
                            r.fromUsername = uname;
                            LCFile avatar = from.getLCFile("avatar");
                            if (avatar != null && avatar.getUrl() != null) {
                                r.fromAvatarUrl = avatar.getUrl();
                            }
                        }
                        if (to != null) {
                            r.toUserId = to.getObjectId();
                            String uname2 = to.getUsername();
                            if (uname2 == null || uname2.isEmpty()) {
                                uname2 = to.getEmail();
                            }
                            r.toUsername = uname2;
                            LCFile avatar2 = to.getLCFile("avatar");
                            if (avatar2 != null && avatar2.getUrl() != null) {
                                r.toAvatarUrl = avatar2.getUrl();
                            }
                        }
                        r.status = obj.getString("status");
                        outgoing.add(r);
                    }
                }
                refreshSubscription();
                updateAdapterData();
                if (swipeRefresh != null) {
                    swipeRefresh.setRefreshing(false);
                }
            }

            @Override
            public void onError(Throwable e) {
                if (!isAdded()) {
                    return;
                }
                Toast.makeText(requireContext(), "加载失败", Toast.LENGTH_SHORT).show();
                if (swipeRefresh != null) {
                    swipeRefresh.setRefreshing(false);
                }
            }

            @Override
            public void onComplete() {
            }
        });
    }

    private void updateAdapterData() {
        if (currentTab == 0) {
            adapter.setData(incoming);
            adapter.setModeIncoming(true);
        } else {
            adapter.setData(outgoing);
            adapter.setModeIncoming(false);
        }
    }

    private void deleteRequest(FriendApi.FriendRequest req) {
        LCUser currentUser = LCUser.currentUser();
        if (currentUser == null) {
            if (isAdded()) {
                Toast.makeText(requireContext(), "请先登录", Toast.LENGTH_SHORT).show();
            }
            return;
        }
        if (req == null || req.requestId == null || req.requestId.isEmpty()) {
            return;
        }
        if (!isAdded()) {
            return;
        }
        new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle("删除请求")
                .setMessage("确定要删除这条好友请求吗？删除后双方都将看不到该请求。")
                .setPositiveButton("删除", (dialog, which) -> {
                    LCObject obj = LCObject.createWithoutData("FriendRequest", req.requestId);
                    obj.deleteInBackground().subscribe(new Observer<cn.leancloud.types.LCNull>() {
                        @Override
                        public void onSubscribe(Disposable d) {
                        }

                        @Override
                        public void onNext(cn.leancloud.types.LCNull lcNull) {
                            removeRequestFromLists(req.requestId);
                            if (isAdded()) {
                                Toast.makeText(requireContext(), "已删除请求", Toast.LENGTH_SHORT).show();
                            }
                        }

                        @Override
                        public void onError(Throwable e) {
                            if (isAdded()) {
                                Toast.makeText(requireContext(), "删除失败", Toast.LENGTH_SHORT).show();
                            }
                        }

                        @Override
                        public void onComplete() {
                        }
                    });
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void removeRequestFromLists(String requestId) {
        if (requestId == null || requestId.isEmpty()) {
            return;
        }
        for (int i = incoming.size() - 1; i >= 0; i--) {
            FriendApi.FriendRequest r = incoming.get(i);
            if (requestId.equals(r.requestId)) {
                incoming.remove(i);
            }
        }
        for (int i = outgoing.size() - 1; i >= 0; i--) {
            FriendApi.FriendRequest r = outgoing.get(i);
            if (requestId.equals(r.requestId)) {
                outgoing.remove(i);
            }
        }
        updateAdapterData();
    }

    private void performAction(FriendApi.FriendRequest req, boolean accept) {
        LCUser currentUser = LCUser.currentUser();
        if (currentUser == null) {
            if (isAdded()) {
                Toast.makeText(requireContext(), "请先登录", Toast.LENGTH_SHORT).show();
            }
            return;
        }
        if (req == null || req.requestId == null || req.requestId.isEmpty()) {
            if (isAdded()) {
                Toast.makeText(requireContext(), "操作失败", Toast.LENGTH_SHORT).show();
            }
            return;
        }
        LCQuery<LCObject> query = new LCQuery<>("FriendRequest");
        query.getInBackground(req.requestId).subscribe(new Observer<LCObject>() {
            @Override
            public void onSubscribe(Disposable d) {
            }

            @Override
            public void onNext(LCObject obj) {
                if (!isAdded()) {
                    return;
                }
                if (obj == null) {
                    Toast.makeText(requireContext(), "操作失败", Toast.LENGTH_SHORT).show();
                    return;
                }
                String status = obj.getString("status");
                if (status != null && !"pending".equals(status)) {
                    Toast.makeText(requireContext(), "请求已处理", Toast.LENGTH_SHORT).show();
                    loadRequests();
                    return;
                }
                LCUser from = obj.getLCObject("fromUser");
                LCUser to = obj.getLCObject("toUser");
                if (from == null || to == null) {
                    Toast.makeText(requireContext(), "操作失败", Toast.LENGTH_SHORT).show();
                    return;
                }
                if (accept) {
                    LCObject friend = new LCObject("Friend");
                    friend.put("user1", from);
                    friend.put("user2", to);
                    friend.saveInBackground().subscribe(new Observer<LCObject>() {
                        @Override
                        public void onSubscribe(Disposable d) {
                        }

                        @Override
                        public void onNext(LCObject lcObject) {
                            obj.put("status", "accepted");
                            obj.saveInBackground().subscribe(new Observer<LCObject>() {
                                @Override
                                public void onSubscribe(Disposable d) {
                                }

                                @Override
                                public void onNext(LCObject lcObject2) {
                                    Toast.makeText(requireContext(), "已接受请求", Toast.LENGTH_SHORT).show();
                                    loadRequests();
                                    createConversationIfNeeded(from, to);
                                }

                                @Override
                                public void onError(Throwable e) {
                                    Toast.makeText(requireContext(), "操作失败", Toast.LENGTH_SHORT).show();
                                }

                                @Override
                                public void onComplete() {
                                }
                            });
                        }

                        @Override
                        public void onError(Throwable e) {
                            Toast.makeText(requireContext(), "操作失败", Toast.LENGTH_SHORT).show();
                        }

                        @Override
                        public void onComplete() {
                        }
                    });
                } else {
                    obj.put("status", "rejected");
                    obj.saveInBackground().subscribe(new Observer<LCObject>() {
                        @Override
                        public void onSubscribe(Disposable d) {
                        }

                        @Override
                        public void onNext(LCObject lcObject) {
                            Toast.makeText(requireContext(), "已拒绝请求", Toast.LENGTH_SHORT).show();
                            loadRequests();
                        }

                        @Override
                        public void onError(Throwable e) {
                            Toast.makeText(requireContext(), "操作失败", Toast.LENGTH_SHORT).show();
                        }

                        @Override
                        public void onComplete() {
                        }
                    });
                }
            }

            @Override
            public void onError(Throwable e) {
                if (!isAdded()) {
                    return;
                }
                Toast.makeText(requireContext(), "操作失败", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onComplete() {
            }
        });
    }

    private void createConversationIfNeeded(LCUser from, LCUser to) {
        LCUser currentUser = LCUser.currentUser();
        if (currentUser == null) {
            return;
        }
        if (from == null || to == null) {
            return;
        }
        String selfId = currentUser.getObjectId();
        if (selfId == null || selfId.isEmpty()) {
            return;
        }
        String fromId = from.getObjectId();
        String toId = to.getObjectId();
        if (fromId == null || toId == null || fromId.isEmpty() || toId.isEmpty()) {
            return;
        }
        String otherId;
        if (selfId.equals(fromId)) {
            otherId = toId;
        } else if (selfId.equals(toId)) {
            otherId = fromId;
        } else {
            return;
        }
        LCIMClient client = LCIMClient.getInstance(selfId);
        client.open(new LCIMClientCallback() {
            @Override
            public void done(LCIMClient openedClient, LCIMException e) {
                if (e != null) {
                    return;
                }
                List<String> members = Arrays.asList(selfId, otherId);
                openedClient.createConversation(members, null, null, false, true, new LCIMConversationCreatedCallback() {
                    @Override
                    public void done(LCIMConversation conversation, LCIMException e) {
                        if (conversation == null || e != null) {
                            return;
                        }
                        String conversationId = conversation.getConversationId();
                        saveUserConversationRecords(currentUser, from, to, conversationId);
                    }
                });
            }
        });
    }

    private void saveUserConversationRecords(LCUser currentUser, LCUser from, LCUser to, String conversationId) {
        if (currentUser == null || from == null || to == null || conversationId == null || conversationId.isEmpty()) {
            return;
        }
        LCUser selfUser = currentUser;
        LCUser otherUser = selfUser.getObjectId().equals(from.getObjectId()) ? to : from;
        
        LCQuery<LCObject> q1 = new LCQuery<>("UserConversation");
        q1.whereEqualTo("owner", selfUser);
        q1.whereEqualTo("conversationId", conversationId);
        q1.limit(1);
        q1.findInBackground().subscribe(new Observer<List<LCObject>>() {
            @Override
            public void onSubscribe(Disposable d) {}
            @Override
            public void onNext(List<LCObject> list) {
                boolean exists = list != null && !list.isEmpty();
                if (!exists) {
                    saveConversationForUser(selfUser, otherUser, conversationId);
                }
                saveConversationForOtherUser(otherUser, selfUser, conversationId);
            }
            @Override
            public void onError(Throwable e) {
                if (e instanceof LCException && ((LCException) e).getCode() == 101) {
                     saveConversationForUser(selfUser, otherUser, conversationId);
                     saveConversationForOtherUser(otherUser, selfUser, conversationId);
                }
            }
            @Override
            public void onComplete() {}
        });
    }

    private void saveConversationForUser(LCUser owner, LCUser other, String conversationId) {
        LCObject obj = new LCObject("UserConversation");
        obj.put("owner", owner);
        obj.put("otherUser", other);
        String name = other.getUsername();
        if (name != null && !name.isEmpty()) {
            obj.put("otherName", name);
        }
        obj.put("conversationId", conversationId);
        obj.saveInBackground().subscribe(new Observer<LCObject>() {
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

    private void saveConversationForOtherUser(LCUser owner, LCUser other, String conversationId) {
        LCQuery<LCObject> q = new LCQuery<>("UserConversation");
        q.whereEqualTo("owner", owner);
        q.whereEqualTo("conversationId", conversationId);
        q.limit(1);
        q.findInBackground().subscribe(new Observer<List<LCObject>>() {
            @Override
            public void onSubscribe(Disposable d) {}
            @Override
            public void onNext(List<LCObject> list) {
                boolean exists = list != null && !list.isEmpty();
                if (!exists) {
                    saveConversationForUser(owner, other, conversationId);
                }
            }
            @Override
            public void onError(Throwable e) {
                if (e instanceof LCException && ((LCException) e).getCode() == 101) {
                    saveConversationForUser(owner, other, conversationId);
                }
            }
            @Override
            public void onComplete() {}
        });
    }

    static class RequestAdapter extends RecyclerView.Adapter<RequestAdapter.ViewHolder> {
        interface Listener {
            void onAccept(FriendApi.FriendRequest req);
            void onReject(FriendApi.FriendRequest req);
            void onDelete(FriendApi.FriendRequest req);
        }
        private final Listener listener;
        private final List<FriendApi.FriendRequest> data = new ArrayList<>();
        private boolean modeIncoming = true;
        RequestAdapter(List<FriendApi.FriendRequest> initial, Listener listener) {
            this.listener = listener;
            setData(initial);
        }
        void setData(List<FriendApi.FriendRequest> list) {
            data.clear();
            if (list != null) data.addAll(list);
            notifyDataSetChanged();
        }
        void setModeIncoming(boolean incoming) {
            this.modeIncoming = incoming;
            notifyDataSetChanged();
        }
        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_friend_request, parent, false);
            return new ViewHolder(v);
        }
        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            FriendApi.FriendRequest req = data.get(position);
            String username = modeIncoming ? req.fromUsername : req.toUsername;
            String avatar = modeIncoming ? req.fromAvatarUrl : req.toAvatarUrl;
            holder.name.setText(username != null ? username : (modeIncoming ? req.fromUserId : req.toUserId));
            if (avatar != null && !avatar.isEmpty()) {
                Glide.with(holder.avatar.getContext()).load(avatar).circleCrop().into(holder.avatar);
            } else {
                holder.avatar.setImageResource(R.drawable.ic_nav_profile);
            }
            boolean isPending = "pending".equals(req.status);
            holder.btnAccept.setVisibility(modeIncoming && isPending ? View.VISIBLE : View.GONE);
            holder.btnReject.setVisibility(modeIncoming && isPending ? View.VISIBLE : View.GONE);
            String statusText;
            if ("accepted".equals(req.status)) {
                statusText = modeIncoming ? "已同意" : "同意";
            } else if ("rejected".equals(req.status)) {
                statusText = modeIncoming ? "已拒绝" : "拒绝";
            } else {
                statusText = "待确认";
            }
            holder.status.setText(statusText);
            holder.btnAccept.setOnClickListener(v -> listener.onAccept(req));
            holder.btnReject.setOnClickListener(v -> listener.onReject(req));
            holder.itemView.setOnLongClickListener(v -> {
                listener.onDelete(req);
                return true;
            });
        }
        @Override
        public int getItemCount() {
            return data.size();
        }
        static class ViewHolder extends RecyclerView.ViewHolder {
            ImageView avatar;
            TextView name;
            TextView status;
            Button btnAccept;
            Button btnReject;
            ViewHolder(@NonNull View itemView) {
                super(itemView);
                avatar = itemView.findViewById(R.id.ivAvatar);
                name = itemView.findViewById(R.id.tvName);
                status = itemView.findViewById(R.id.tvStatus);
                btnAccept = itemView.findViewById(R.id.btnAccept);
                btnReject = itemView.findViewById(R.id.btnReject);
            }
        }
    }
}
