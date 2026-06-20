package com.example.health.ui;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.health.R;
import com.bumptech.glide.Glide;
import com.example.health.utils.ValidationUtils;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

import cn.leancloud.LCException;
import cn.leancloud.LCFile;
import cn.leancloud.LCObject;
import cn.leancloud.LCQuery;
import cn.leancloud.LCUser;
import cn.leancloud.livequery.LCLiveQuery;
import cn.leancloud.livequery.LCLiveQueryEventHandler;
import cn.leancloud.livequery.LCLiveQuerySubscribeCallback;
import io.reactivex.Observer;
import io.reactivex.disposables.Disposable;

/**
 * 好友列表页，展示当前用户的好友列表，支持按用户名搜索过滤。
 * 长按好友可删除，点击进入好友主页，通过 FAB 提供扫码/搜索添加好友入口。
 */
public class FriendListFragment extends Fragment {
    private EditText searchEditText;
    private androidx.swiperefreshlayout.widget.SwipeRefreshLayout swipeRefresh;
    private RecyclerView recyclerView;
    private FriendAdapter adapter;
    private final List<FriendItem> items = new ArrayList<>();
    private final List<FriendItem> displayItems = new ArrayList<>();
    private FloatingActionButton fabAddFriend;
    private LCLiveQuery liveQuery;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_friend_list, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        searchEditText = view.findViewById(R.id.searchEditText);
        swipeRefresh = view.findViewById(R.id.swipeRefresh);
        recyclerView = view.findViewById(R.id.recyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new FriendAdapter(displayItems, new FriendAdapter.Listener() {
            @Override
            public void onItemClick(int position) {
                if (position < 0 || position >= displayItems.size()) return;
                FriendItem item = displayItems.get(position);
                if (item == null || item.id == null || item.id.isEmpty()) return;
                openUserProfile(item.id);
            }

            @Override
            public void onItemLongClick(int position) {
                if (position < 0 || position >= displayItems.size()) return;
                FriendItem item = displayItems.get(position);
                if (item == null || item.id == null) return;
                new android.app.AlertDialog.Builder(requireContext())
                        .setTitle("删除好友")
                        .setMessage("确定要删除 " + item.name + " 吗？")
                        .setPositiveButton("删除", (dialog, which) -> deleteFriend(item))
                        .setNegativeButton("取消", null)
                        .show();
            }
        });
        if (swipeRefresh != null) {
            swipeRefresh.setOnRefreshListener(() -> loadData(true));
        }
        recyclerView.setAdapter(adapter);
        android.widget.ImageView buttonSearchFriend = view.findViewById(R.id.buttonSearchFriend);
        if (buttonSearchFriend != null) {
            buttonSearchFriend.setOnClickListener(v -> performAddFriendSearch());
        }
        fabAddFriend = view.findViewById(R.id.fabAddFriend);
        fabAddFriend.setOnClickListener(v -> showAddFriendOptions());
        searchEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                filter(s.toString());
            }
            @Override
            public void afterTextChanged(Editable s) {}
        });
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

    private void deleteFriend(FriendItem item) {
        LCUser currentUser = LCUser.currentUser();
        if (currentUser == null) {
            if (isAdded()) {
                Toast.makeText(requireContext(), "请先登录", Toast.LENGTH_SHORT).show();
            }
            return;
        }
        if (item == null || item.id == null || item.id.isEmpty()) {
            return;
        }
        LCUser otherUser;
        try {
            otherUser = LCUser.createWithoutData(LCUser.class, item.id);
        } catch (LCException e) {
            if (isAdded()) {
                Toast.makeText(requireContext(), "删除失败", Toast.LENGTH_SHORT).show();
            }
            return;
        }
        LCQuery<LCObject> q1 = new LCQuery<>("Friend");
        q1.whereEqualTo("user1", currentUser);
        q1.whereEqualTo("user2", otherUser);
        LCQuery<LCObject> q2 = new LCQuery<>("Friend");
        q2.whereEqualTo("user1", otherUser);
        q2.whereEqualTo("user2", currentUser);
        List<LCQuery<LCObject>> qs = new ArrayList<>();
        qs.add(q1);
        qs.add(q2);
        LCQuery<LCObject> query = LCQuery.or(qs);
        query.findInBackground().subscribe(new Observer<List<LCObject>>() {
            @Override
            public void onSubscribe(Disposable d) {
            }

            @Override
            public void onNext(List<LCObject> result) {
                if (!isAdded()) {
                    return;
                }
                if (result != null) {
                    List<String> userIds = new ArrayList<>();
                    for (LCObject obj : result) {
                        if (obj != null) {
                            obj.deleteInBackground().subscribe();
                        }
                    }
                }
                for (int i = 0; i < items.size(); i++) {
                    if (items.get(i).id.equals(item.id)) {
                        items.remove(i);
                        break;
                    }
                }
                for (int i = 0; i < displayItems.size(); i++) {
                    if (displayItems.get(i).id.equals(item.id)) {
                        displayItems.remove(i);
                        break;
                    }
                }
                adapter.notifyDataSetChanged();
                Toast.makeText(requireContext(), "已删除好友", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onError(Throwable e) {
                if (!isAdded()) {
                    return;
                }
                Toast.makeText(requireContext(), "删除失败", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onComplete() {
            }
        });
    }

    private void loadData() {
        loadData(false);
    }

    private void loadData(boolean fromRefresh) {
        LCUser currentUser = LCUser.currentUser();
        if (currentUser == null) {
            if (isAdded()) {
                items.clear();
                displayItems.clear();
                adapter.notifyDataSetChanged();
            }
            return;
        }
        LCQuery<LCObject> q1 = new LCQuery<>("Friend");
        q1.whereEqualTo("user1", currentUser);
        LCQuery<LCObject> q2 = new LCQuery<>("Friend");
        q2.whereEqualTo("user2", currentUser);
        List<LCQuery<LCObject>> qs = new ArrayList<>();
        qs.add(q1);
        qs.add(q2);
        LCQuery<LCObject> query = LCQuery.or(qs);
        query.include("user1");
        query.include("user2");
        query.findInBackground().subscribe(new Observer<List<LCObject>>() {
            @Override
            public void onSubscribe(Disposable d) {
            }

            @Override
            public void onNext(List<LCObject> result) {
                if (!isAdded()) {
                    return;
                }
                
                List<String> userIds = new ArrayList<>();
                if (result != null) {
                    for (LCObject obj : result) {
                         if (obj == null) continue;
                         LCUser user1 = obj.getLCObject("user1");
                         LCUser user2 = obj.getLCObject("user2");
                         LCUser other = null;
                         if (user1 != null && currentUser.getObjectId().equals(user1.getObjectId())) {
                             other = user2;
                         } else if (user2 != null && currentUser.getObjectId().equals(user2.getObjectId())) {
                             other = user1;
                         }
                         if (other != null) {
                             userIds.add(other.getObjectId());
                         }
                    }
                }
                
                if (userIds.isEmpty()) {
                     updateUI(result, new ArrayList<>(), fromRefresh);
                     return;
                }

                LCQuery<LCUser> userQuery = LCUser.getQuery();
                userQuery.whereContainedIn("objectId", userIds);
                userQuery.findInBackground().subscribe(new Observer<List<LCUser>>() {
                     @Override
                     public void onSubscribe(Disposable d) {}
                     @Override
                     public void onNext(List<LCUser> users) {
                         updateUI(result, users, fromRefresh);
                     }
                     @Override
                     public void onError(Throwable e) {
                         updateUI(result, null, fromRefresh);
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
                android.widget.Toast.makeText(requireContext(), "加载好友列表失败", android.widget.Toast.LENGTH_SHORT).show();
                if (fromRefresh && swipeRefresh != null) {
                    swipeRefresh.setRefreshing(false);
                }
            }

            @Override
            public void onComplete() {
            }
        });
    }

    private void updateUI(List<LCObject> result, List<LCUser> users, boolean fromRefresh) {
        if (!isAdded()) return;
        LCUser currentUser = LCUser.currentUser();
        if (currentUser == null) return;
        
        requireActivity().runOnUiThread(() -> {
            items.clear();
            displayItems.clear();
            
            Map<String, LCUser> userMap = new HashMap<>();
            if (users != null) {
                for (LCUser u : users) {
                    userMap.put(u.getObjectId(), u);
                }
            }
            
            List<String> finalUserIds = new ArrayList<>();

            if (result != null) {
                for (LCObject obj : result) {
                    if (obj == null) {
                        continue;
                    }
                    LCUser user1 = obj.getLCObject("user1");
                    LCUser user2 = obj.getLCObject("user2");
                    LCUser otherPtr;
                    if (user1 != null && currentUser.getObjectId().equals(user1.getObjectId())) {
                        otherPtr = user2;
                    } else if (user2 != null && currentUser.getObjectId().equals(user2.getObjectId())) {
                        otherPtr = user1;
                    } else {
                        continue;
                    }
                    if (otherPtr == null) {
                        continue;
                    }
                    
                    LCUser freshUser = userMap.get(otherPtr.getObjectId());
                    LCUser displayUser = freshUser != null ? freshUser : otherPtr;
                    
                    finalUserIds.add(displayUser.getObjectId());
                    String name = displayUser.getUsername();
                    if (name == null || name.isEmpty()) {
                        name = "用户";
                    }
                    String avatarUrl = null;
                    LCFile avatar = displayUser.getLCFile("avatar");
                    if (avatar != null && avatar.getUrl() != null) {
                        avatarUrl = avatar.getUrl();
                    }
                    items.add(new FriendItem(displayUser.getObjectId(), name, false, avatarUrl));
                }
                subscribeToFriendUpdates(finalUserIds);
                Collections.sort(items, new Comparator<FriendItem>() {
                    @Override
                    public int compare(FriendItem a, FriendItem b) {
                        String na = a.name != null ? a.name : "";
                        String nb = b.name != null ? b.name : "";
                        return na.compareToIgnoreCase(nb);
                    }
                });
                displayItems.addAll(items);
            }
            adapter.notifyDataSetChanged();
            if (fromRefresh && swipeRefresh != null) {
                swipeRefresh.setRefreshing(false);
            }
        });
    }

    private void performAddFriendSearch() {
        String text = searchEditText.getText().toString().trim();
        if (text.isEmpty()) {
            Toast.makeText(requireContext(), "请输入用户名或邮箱", Toast.LENGTH_SHORT).show();
            return;
        }
        if (ValidationUtils.isValidEmail(text)) {
            performServerSearch(text, "email");
        } else {
            performServerSearch(text, "username");
        }
    }

    private void filter(String q) {
        displayItems.clear();
        if (q == null || q.isEmpty()) {
            displayItems.addAll(items);
        } else {
            String query = q.toLowerCase();
            for (FriendItem item : items) {
                if (item.name.toLowerCase().contains(query)) {
                    displayItems.add(item);
                }
            }
        }
        adapter.notifyDataSetChanged();
    }

    private void showAddFriendOptions() {
        String[] items = new String[]{"扫描二维码添加", "按用户名搜索", "按邮箱搜索"};
        new android.app.AlertDialog.Builder(requireContext())
                .setItems(items, (dialog, which) -> {
                    if (which == 0) {
                        IntentIntegrator integrator = IntentIntegrator.forSupportFragment(FriendListFragment.this);
                        integrator.setDesiredBarcodeFormats(IntentIntegrator.QR_CODE);
                        integrator.setPrompt("扫描用户二维码");
                        integrator.setCameraId(0);
                        integrator.setBeepEnabled(true);
                        integrator.setBarcodeImageEnabled(false);
                        integrator.setCaptureActivity(PortraitCaptureActivity.class);
                        integrator.setOrientationLocked(false);
                        integrator.initiateScan();
                    } else if (which == 1) {
                        String text = searchEditText.getText().toString().trim();
                        if (text.isEmpty()) {
                            Toast.makeText(requireContext(), "请输入用户名", Toast.LENGTH_SHORT).show();
                        } else {
                            performServerSearch(text, "username");
                        }
                    } else {
                        String email = searchEditText.getText().toString().trim();
                        if (!ValidationUtils.isValidEmail(email)) {
                            Toast.makeText(requireContext(), "邮箱格式不正确", Toast.LENGTH_SHORT).show();
                        } else {
                            performServerSearch(email, "email");
                        }
                    }
                })
                .show();
    }

    private void performServerSearch(String q, String type) {
        String text = q != null ? q.trim() : "";
        if (text.isEmpty()) {
            Toast.makeText(requireContext(), "请输入用户名或邮箱", Toast.LENGTH_SHORT).show();
            return;
        }
        LCUser currentUser = LCUser.currentUser();
        if (currentUser == null) {
            Toast.makeText(requireContext(), "请先登录", Toast.LENGTH_SHORT).show();
            return;
        }
        LCQuery<LCUser> query = LCUser.getQuery();
        if ("email".equals(type)) {
            query.whereEqualTo("email", text);
        } else {
            query.whereEqualTo("username", text);
        }
        query.limit(1);
        query.getFirstInBackground().subscribe(new Observer<LCUser>() {
            private boolean found = false;

            @Override
            public void onSubscribe(Disposable d) {
            }

            @Override
            public void onNext(LCUser user) {
                found = true;
                if (!isAdded()) {
                    return;
                }
                if (user == null) {
                    Toast.makeText(requireContext(), "未找到用户", Toast.LENGTH_SHORT).show();
                    return;
                }
                String targetId = user.getObjectId();
                if (targetId == null || targetId.isEmpty()) {
                    Toast.makeText(requireContext(), "未找到用户", Toast.LENGTH_SHORT).show();
                    return;
                }
                String selfId = currentUser.getObjectId();
                if (selfId != null && selfId.equals(targetId)) {
                    Toast.makeText(requireContext(), "不能添加自己为好友", Toast.LENGTH_SHORT).show();
                    return;
                }
                openUserProfile(targetId);
            }

            @Override
            public void onError(Throwable e) {
                if (!isAdded()) {
                    return;
                }
                if (e instanceof LCException && ((LCException) e).getCode() == 101 && !found) {
                    Toast.makeText(requireContext(), "未找到用户", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(requireContext(), "搜索失败，请检查网络或稍后重试", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onComplete() {
            }
        });
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable android.content.Intent data) {
        IntentResult result = IntentIntegrator.parseActivityResult(requestCode, resultCode, data);
        if (result != null) {
            if (result.getContents() != null) {
                String content = result.getContents();
                if (content.startsWith("health_app_user:")) {
                    String userId = content.substring("health_app_user:".length());
                    openUserProfile(userId);
                } else {
                    Toast.makeText(requireContext(), "无效二维码", Toast.LENGTH_SHORT).show();
                }
            }
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    private void openUserProfile(String userId) {
        if (!isAdded()) {
            return;
        }
        if (userId == null || userId.isEmpty()) {
            Toast.makeText(requireContext(), "未找到用户", Toast.LENGTH_SHORT).show();
            return;
        }
        android.content.Context context = requireContext();
        android.content.Intent intent = new android.content.Intent(context, UserProfileActivity.class);
        intent.putExtra(UserProfileActivity.EXTRA_USER_ID, userId);
        context.startActivity(intent);
    }

    private void sendFriendRequest(String toUserId) {
        LCUser currentUser = LCUser.currentUser();
        if (currentUser == null) {
            if (isAdded()) {
                Toast.makeText(requireContext(), "请先登录", Toast.LENGTH_SHORT).show();
            }
            return;
        }
        if (toUserId == null || toUserId.isEmpty()) {
            if (isAdded()) {
                Toast.makeText(requireContext(), "发送失败", Toast.LENGTH_SHORT).show();
            }
            return;
        }
        String selfId = currentUser.getObjectId();
        if (selfId != null && selfId.equals(toUserId)) {
            if (isAdded()) {
                Toast.makeText(requireContext(), "不能添加自己为好友", Toast.LENGTH_SHORT).show();
            }
            return;
        }
        LCUser targetUser;
        try {
            targetUser = LCUser.createWithoutData(LCUser.class, toUserId);
        } catch (LCException e) {
            if (isAdded()) {
                Toast.makeText(requireContext(), "发送失败", Toast.LENGTH_SHORT).show();
            }
            return;
        }
        LCQuery<LCObject> query = new LCQuery<>("FriendRequest");
        query.whereEqualTo("fromUser", currentUser);
        query.whereEqualTo("toUser", targetUser);
        query.whereEqualTo("status", "pending");
        query.limit(1);
        query.findInBackground().subscribe(new Observer<List<LCObject>>() {
            @Override
            public void onSubscribe(Disposable d) {
            }

            @Override
            public void onNext(List<LCObject> existing) {
                if (!isAdded()) {
                    return;
                }
                if (existing != null && !existing.isEmpty()) {
                    Toast.makeText(requireContext(), "已发送过好友请求，请等待对方确认", Toast.LENGTH_SHORT).show();
                    return;
                }
                LCObject req = new LCObject("FriendRequest");
                req.put("fromUser", currentUser);
                req.put("toUser", targetUser);
                req.put("status", "pending");
                req.saveInBackground().subscribe(new Observer<LCObject>() {
                    @Override
                    public void onSubscribe(Disposable d) {
                    }

                    @Override
                    public void onNext(LCObject lcObject) {
                        if (!isAdded()) {
                            return;
                        }
                        Toast.makeText(requireContext(), "已发送好友请求", Toast.LENGTH_SHORT).show();
                    }

                    @Override
                    public void onError(Throwable e) {
                        if (!isAdded()) {
                            return;
                        }
                        Toast.makeText(requireContext(), "发送失败", Toast.LENGTH_SHORT).show();
                    }

                    @Override
                    public void onComplete() {
                    }
                });
            }

            @Override
            public void onError(Throwable e) {
                if (!isAdded()) {
                    return;
                }
                Toast.makeText(requireContext(), "发送失败", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onComplete() {
            }
        });
    }

    private void subscribeToFriendUpdates(List<String> userIds) {
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

                    // Update source list
                    for (FriendItem item : items) {
                        if (item.id != null && item.id.equals(objectId)) {
                            if (newName != null) item.name = newName;
                            if (newAvatarUrl != null) item.avatarUrl = newAvatarUrl;
                        }
                    }

                    // Update display list and notify adapter
                    for (int i = 0; i < displayItems.size(); i++) {
                        FriendItem item = displayItems.get(i);
                        if (item.id != null && item.id.equals(objectId)) {
                            boolean changed = false;
                            if (newName != null && !newName.equals(item.name)) {
                                item.name = newName;
                                changed = true;
                            }
                            if (newAvatarUrl != null && !newAvatarUrl.equals(item.avatarUrl)) {
                                item.avatarUrl = newAvatarUrl;
                                changed = true;
                            }
                            if (changed) {
                                adapter.notifyItemChanged(i);
                            }
                        }
                    }
                });
            }
        });
        liveQuery.subscribeInBackground(new LCLiveQuerySubscribeCallback() {
            @Override
            public void done(LCException e) {}
        });
    }

    static class FriendItem {
        String id;
        String name;
        boolean online;
        String avatarUrl;
        FriendItem(String id, String name, boolean online, String avatarUrl) {
            this.id = id;
            this.name = name;
            this.online = online;
            this.avatarUrl = avatarUrl;
        }
    }

    static class FriendAdapter extends RecyclerView.Adapter<FriendAdapter.ViewHolder> {
        interface Listener {
            void onItemClick(int position);
            void onItemLongClick(int position);
        }
        private final List<FriendItem> data;
        private final Listener listener;
        FriendAdapter(List<FriendItem> data, Listener listener) {
            this.data = data;
            this.listener = listener;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_friend, parent, false);
            return new ViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            FriendItem item = data.get(position);
            holder.name.setText(item.name);
            holder.status.setImageResource(item.online ? R.drawable.ic_status_online : R.drawable.ic_status_offline);
            if (item.avatarUrl != null && !item.avatarUrl.isEmpty()) {
                Glide.with(holder.avatar.getContext()).load(item.avatarUrl).centerCrop().into(holder.avatar);
            } else {
                holder.avatar.setImageResource(R.drawable.ic_nav_profile);
            }
            holder.itemView.setOnClickListener(v -> {
                if (listener != null) {
                    int pos = holder.getAdapterPosition();
                    if (pos != RecyclerView.NO_POSITION) {
                        listener.onItemClick(pos);
                    }
                }
            });
            holder.itemView.setOnLongClickListener(v -> {
                if (listener != null) {
                    int pos = holder.getAdapterPosition();
                    if (pos != RecyclerView.NO_POSITION) {
                        listener.onItemLongClick(pos);
                    }
                }
                return true;
            });
        }

        @Override
        public int getItemCount() {
            return data.size();
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            TextView name;
            ImageView status;
            ImageView avatar;
            ViewHolder(@NonNull View itemView) {
                super(itemView);
                name = itemView.findViewById(R.id.tvName);
                status = itemView.findViewById(R.id.ivStatus);
                avatar = itemView.findViewById(R.id.ivAvatar);
            }
        }
    }
}
