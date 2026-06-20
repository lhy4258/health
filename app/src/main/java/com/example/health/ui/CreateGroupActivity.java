package com.example.health.ui;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.health.R;
import com.example.health.data.api.FriendApi;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import cn.leancloud.LCFile;
import cn.leancloud.LCObject;
import cn.leancloud.LCQuery;
import cn.leancloud.LCUser;
import cn.leancloud.im.v2.LCIMClient;
import cn.leancloud.im.v2.LCIMConversation;
import cn.leancloud.im.v2.LCIMException;
import cn.leancloud.im.v2.callback.LCIMClientCallback;
import cn.leancloud.im.v2.callback.LCIMConversationCreatedCallback;
import io.reactivex.Observer;
import io.reactivex.disposables.Disposable;

/**
 * 创建群组页面，选择两位以上好友并输入群名称后通过 LeanCloud IM 创建群聊。
 * 群聊创建成功后为每位成员保存 UserConversation 关联记录。
 */
public class CreateGroupActivity extends AppCompatActivity {
    private EditText editGroupName;
    private RecyclerView recyclerView;
    private Button buttonCreate;
    private FriendAdapter adapter;
    private final List<FriendApi.Friend> friends = new ArrayList<>();
    private final Set<String> selectedIds = new HashSet<>();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create_group);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowHomeEnabled(true);
            getSupportActionBar().setDisplayShowTitleEnabled(false);
        }
        toolbar.setNavigationOnClickListener(v -> onBackPressed());
        editGroupName = findViewById(R.id.editGroupName);
        recyclerView = findViewById(R.id.recyclerViewFriends);
        buttonCreate = findViewById(R.id.buttonCreateGroup);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new FriendAdapter(friends, selectedIds, this::onSelectionChanged);
        recyclerView.setAdapter(adapter);
        buttonCreate.setOnClickListener(v -> submit());
        editGroupName.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                updateButtonState();
            }
            @Override
            public void afterTextChanged(Editable s) {}
        });
        loadFriends();
        updateButtonState();
    }

    private void loadFriends() {
        LCUser currentUser = LCUser.currentUser();
        if (currentUser == null) {
            friends.clear();
            adapter.notifyDataSetChanged();
            updateButtonState();
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
                friends.clear();
                if (result != null) {
                    LCUser self = currentUser;
                    for (LCObject obj : result) {
                        if (obj == null) {
                            continue;
                        }
                        LCUser user1 = obj.getLCObject("user1");
                        LCUser user2 = obj.getLCObject("user2");
                        LCUser other;
                        if (user1 != null && self.getObjectId().equals(user1.getObjectId())) {
                            other = user2;
                        } else if (user2 != null && self.getObjectId().equals(user2.getObjectId())) {
                            other = user1;
                        } else {
                            continue;
                        }
                        if (other == null) {
                            continue;
                        }
                        FriendApi.Friend f = new FriendApi.Friend();
                        f.id = other.getObjectId();
                        String username = other.getUsername();
                        if (username == null || username.isEmpty()) {
                            username = "用户";
                        }
                        f.username = username;
                        LCFile avatar = other.getLCFile("avatar");
                        if (avatar != null && avatar.getUrl() != null) {
                            f.avatarUrl = avatar.getUrl();
                        }
                        f.online = false;
                        friends.add(f);
                    }
                    Collections.sort(friends, new Comparator<FriendApi.Friend>() {
                        @Override
                        public int compare(FriendApi.Friend a, FriendApi.Friend b) {
                            String na = a.username != null ? a.username : "";
                            String nb = b.username != null ? b.username : "";
                            return na.compareToIgnoreCase(nb);
                        }
                    });
                }
                adapter.notifyDataSetChanged();
                updateButtonState();
            }

            @Override
            public void onError(Throwable e) {
                Toast.makeText(CreateGroupActivity.this, "加载好友失败", Toast.LENGTH_SHORT).show();
                updateButtonState();
            }

            @Override
            public void onComplete() {
            }
        });
    }

    private void onSelectionChanged() {
        updateButtonState();
    }

    private void updateButtonState() {
        String name = editGroupName.getText().toString().trim();
        boolean enabled = !TextUtils.isEmpty(name) && selectedIds.size() >= 2;
        buttonCreate.setEnabled(enabled);
    }

    private void submit() {
        String name = editGroupName.getText().toString().trim();
        if (TextUtils.isEmpty(name)) {
            Toast.makeText(this, "请输入群组名称", Toast.LENGTH_SHORT).show();
            return;
        }
        if (selectedIds.size() < 2) {
            Toast.makeText(this, "请选择至少两位好友", Toast.LENGTH_SHORT).show();
            return;
        }
        LCUser currentUser = LCUser.currentUser();
        if (currentUser == null || currentUser.getObjectId() == null || currentUser.getObjectId().isEmpty()) {
            Toast.makeText(this, "请先登录后再创建群组", Toast.LENGTH_SHORT).show();
            return;
        }
        String selfId = currentUser.getObjectId();
        List<String> memberIds = new ArrayList<>(selectedIds);
        if (!memberIds.contains(selfId)) {
            memberIds.add(0, selfId);
        }
        if (memberIds.size() < 3) {
            Toast.makeText(this, "群成员不足", Toast.LENGTH_SHORT).show();
            return;
        }
        buttonCreate.setEnabled(false);
        LCIMClient client = LCIMClient.getInstance(selfId);
        client.open(new LCIMClientCallback() {
            @Override
            public void done(LCIMClient openedClient, LCIMException e) {
                if (e != null) {
                    runOnUiThread(() -> {
                        buttonCreate.setEnabled(true);
                        Toast.makeText(CreateGroupActivity.this, "打开聊天服务失败", Toast.LENGTH_SHORT).show();
                    });
                    return;
                }
                openedClient.createConversation(memberIds, name, null, false, false, new LCIMConversationCreatedCallback() {
                    @Override
                    public void done(LCIMConversation conversation, LCIMException e) {
                        if (conversation == null || e != null) {
                            runOnUiThread(() -> {
                                buttonCreate.setEnabled(true);
                                Toast.makeText(CreateGroupActivity.this, "创建群组失败", Toast.LENGTH_SHORT).show();
                            });
                            return;
                        }
                        String conversationId = conversation.getConversationId();
                        saveGroupConversationRecords(memberIds, name, conversationId);
                        runOnUiThread(() -> {
                            buttonCreate.setEnabled(true);
                            Toast.makeText(CreateGroupActivity.this, "创建群组成功", Toast.LENGTH_SHORT).show();
                            android.content.Intent intent = new android.content.Intent(CreateGroupActivity.this, GroupChatActivity.class);
                            intent.putExtra("conversationId", conversationId);
                            intent.putExtra("title", name);
                            startActivity(intent);
                            finish();
                        });
                    }
                });
            }
        });
    }

    private void saveGroupConversationRecords(List<String> memberIds, String name, String conversationId) {
        if (memberIds == null || memberIds.isEmpty() || conversationId == null || conversationId.isEmpty()) {
            return;
        }
        int memberCount = memberIds.size();
        for (String id : memberIds) {
            if (id == null || id.isEmpty()) {
                continue;
            }
            LCUser owner;
            try {
                owner = LCUser.createWithoutData(LCUser.class, id);
            } catch (cn.leancloud.LCException e) {
                continue;
            }
            LCQuery<LCObject> q = new LCQuery<>("UserConversation");
            q.whereEqualTo("owner", owner);
            q.whereEqualTo("conversationId", conversationId);
            q.limit(1);
            LCUser finalOwner = owner;
            q.findInBackground().subscribe(new Observer<List<LCObject>>() {
                @Override
                public void onSubscribe(Disposable d) {
                }

                @Override
                public void onNext(List<LCObject> list) {
                    boolean exists = list != null && !list.isEmpty();
                    if (!exists) {
                        createGroupConversationRecord(finalOwner, name, conversationId, memberCount);
                    }
                }

                @Override
                public void onError(Throwable e) {
                    if (e instanceof cn.leancloud.LCException && ((cn.leancloud.LCException) e).getCode() == 101) {
                        createGroupConversationRecord(finalOwner, name, conversationId, memberCount);
                    }
                }

                @Override
                public void onComplete() {
                }
            });
        }
    }

    private void createGroupConversationRecord(LCUser owner, String name, String conversationId, int memberCount) {
        if (owner == null || conversationId == null || conversationId.isEmpty()) {
            return;
        }
        LCObject obj = new LCObject("UserConversation");
        obj.put("owner", owner);
        if (name != null && !name.isEmpty()) {
            obj.put("otherName", name);
        }
        obj.put("conversationId", conversationId);
        obj.put("isGroup", true);
        obj.put("memberCount", memberCount);
        obj.saveInBackground().subscribe(new Observer<LCObject>() {
            @Override
            public void onSubscribe(Disposable d) {
            }

            @Override
            public void onNext(LCObject lcObject) {
            }

            @Override
            public void onError(Throwable e) {
            }

            @Override
            public void onComplete() {
            }
        });
    }

    static class FriendAdapter extends RecyclerView.Adapter<FriendAdapter.ViewHolder> {
        interface Listener {
            void onSelectionChanged();
        }
        private final List<FriendApi.Friend> data;
        private final Set<String> selectedIds;
        private final Listener listener;
        FriendAdapter(List<FriendApi.Friend> data, Set<String> selectedIds, Listener listener) {
            this.data = data;
            this.selectedIds = selectedIds;
            this.listener = listener;
        }
        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_group_friend_select, parent, false);
            return new ViewHolder(v);
        }
        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            FriendApi.Friend f = data.get(position);
            String id = f.id;
            String name = f.username != null ? f.username : id;
            holder.name.setText(name);
            holder.checkBox.setOnCheckedChangeListener(null);
            holder.checkBox.setChecked(id != null && selectedIds.contains(id));
            holder.status.setImageResource(f.online ? R.drawable.ic_status_online : R.drawable.ic_status_offline);
            if (f.avatarUrl != null && !f.avatarUrl.isEmpty()) {
                Glide.with(holder.avatar.getContext()).load(f.avatarUrl).circleCrop().into(holder.avatar);
            } else {
                holder.avatar.setImageResource(R.drawable.ic_nav_profile);
            }
            View.OnClickListener toggle = v -> holder.checkBox.setChecked(!holder.checkBox.isChecked());
            holder.itemView.setOnClickListener(toggle);
            holder.checkBox.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (id == null) return;
                if (isChecked) {
                    selectedIds.add(id);
                } else {
                    selectedIds.remove(id);
                }
                if (listener != null) {
                    listener.onSelectionChanged();
                }
            });
        }
        @Override
        public int getItemCount() {
            return data.size();
        }
        static class ViewHolder extends RecyclerView.ViewHolder {
            ImageView avatar;
            TextView name;
            ImageView status;
            CheckBox checkBox;
            ViewHolder(@NonNull View itemView) {
                super(itemView);
                avatar = itemView.findViewById(R.id.ivAvatar);
                name = itemView.findViewById(R.id.tvName);
                status = itemView.findViewById(R.id.ivStatus);
                checkBox = itemView.findViewById(R.id.checkBox);
            }
        }
    }
}
