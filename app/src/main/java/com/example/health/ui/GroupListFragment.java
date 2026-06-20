package com.example.health.ui;

import android.app.AlertDialog;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.health.R;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.ArrayList;
import java.util.List;

import cn.leancloud.LCException;
import cn.leancloud.LCObject;
import cn.leancloud.LCQuery;
import cn.leancloud.LCUser;
import cn.leancloud.im.v2.LCIMClient;
import cn.leancloud.im.v2.LCIMConversation;
import cn.leancloud.im.v2.LCIMException;
import cn.leancloud.im.v2.callback.LCIMClientCallback;
import cn.leancloud.im.v2.callback.LCIMConversationCallback;
import io.reactivex.Observer;
import io.reactivex.disposables.Disposable;

/**
 * 群组列表页，展示用户参与的所有群组（从 UserConversation 表查询），支持重命名和退出群组。
 * 点击群组进入群聊页面，FAB 按钮跳转创建群组。
 */
public class GroupListFragment extends Fragment {
    private androidx.swiperefreshlayout.widget.SwipeRefreshLayout swipeRefresh;
    private RecyclerView recyclerView;
    private GroupAdapter adapter;
    private FloatingActionButton fabCreate;
    private final List<GroupItem> items = new ArrayList<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_group_list, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        swipeRefresh = view.findViewById(R.id.swipeRefresh);
        recyclerView = view.findViewById(R.id.recyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new GroupAdapter(items, new GroupAdapter.Listener() {
            @Override
            public void onItemClick(int position) {
                if (position < 0 || position >= items.size()) return;
                GroupItem item = items.get(position);
                if (item == null || item.id == null) return;
                android.content.Intent intent = new android.content.Intent(requireContext(), GroupChatActivity.class);
                intent.putExtra("conversationId", item.id);
                intent.putExtra("title", item.name);
                startActivity(intent);
            }
            @Override
            public void onItemLongClick(int position) {
                if (position < 0 || position >= items.size()) return;
                GroupItem item = items.get(position);
                if (item == null || item.id == null) return;
                showGroupOptionsDialog(item);
            }
        });
        recyclerView.setAdapter(adapter);
        fabCreate = view.findViewById(R.id.fabCreateGroup);
        fabCreate.setOnClickListener(v -> {
            android.content.Intent intent = new android.content.Intent(requireContext(), CreateGroupActivity.class);
            startActivity(intent);
        });
        if (swipeRefresh != null) {
            swipeRefresh.setOnRefreshListener(this::loadData);
        }
        loadData();
    }

    @Override
    public void onResume() {
        super.onResume();
        loadData();
    }

    private void loadData() {
        LCUser currentUser = LCUser.currentUser();
        if (currentUser == null) {
            if (isAdded()) {
                items.clear();
                adapter.notifyDataSetChanged();
                Toast.makeText(requireContext(), "请先登录", Toast.LENGTH_SHORT).show();
            }
            return;
        }
        LCQuery<LCObject> query = new LCQuery<>("UserConversation");
        query.whereEqualTo("owner", currentUser);
        query.whereEqualTo("isGroup", true);
        query.orderByDescending("updatedAt");
        query.findInBackground().subscribe(new Observer<List<LCObject>>() {
            @Override
            public void onSubscribe(Disposable d) {
            }

            @Override
            public void onNext(List<LCObject> list) {
                if (!isAdded()) {
                    return;
                }
                requireActivity().runOnUiThread(() -> {
                    items.clear();
                    if (list != null) {
                        for (LCObject obj : list) {
                            if (obj == null) {
                                continue;
                            }
                            String conversationId = obj.getString("conversationId");
                            if (conversationId == null || conversationId.isEmpty()) {
                                continue;
                            }
                            String name = obj.getString("otherName");
                            if (name == null || name.isEmpty()) {
                                name = "群聊";
                            }
                            int count = obj.getInt("memberCount");
                            String membersText = count > 0 ? (count + " 人") : "";
                            items.add(new GroupItem(conversationId, name, membersText));
                        }
                    }
                    adapter.notifyDataSetChanged();
                    if (swipeRefresh != null) {
                        swipeRefresh.setRefreshing(false);
                    }
                });
            }

            @Override
            public void onError(Throwable e) {
                if (!isAdded()) {
                    return;
                }
                if (!(e instanceof LCException && ((LCException) e).getCode() == 101)) {
                    Toast.makeText(requireContext(), "加载群组失败", Toast.LENGTH_SHORT).show();
                }
                requireActivity().runOnUiThread(() -> {
                    items.clear();
                    adapter.notifyDataSetChanged();
                    if (swipeRefresh != null) {
                        swipeRefresh.setRefreshing(false);
                    }
                });
            }

            @Override
            public void onComplete() {
            }
        });
    }

    private void showGroupOptionsDialog(GroupItem item) {
        String[] options = new String[]{"重命名群组", "删除群组"};
        new AlertDialog.Builder(requireContext())
                .setTitle(item.name)
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        showRenameGroupDialog(item);
                    } else if (which == 1) {
                        confirmDeleteGroup(item);
                    }
                })
                .show();
    }

    private void showRenameGroupDialog(GroupItem item) {
        EditText input = new EditText(requireContext());
        input.setText(item.name);
        input.setSelection(item.name != null ? item.name.length() : 0);
        new AlertDialog.Builder(requireContext())
                .setTitle("重命名群组")
                .setView(input)
                .setPositiveButton("保存", (d, w) -> {
                    String name = input.getText().toString().trim();
                    if (!TextUtils.isEmpty(name)) {
                        updateGroupName(item, name);
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void updateGroupName(GroupItem item, String newName) {
        LCUser currentUser = LCUser.currentUser();
        if (currentUser == null) {
            if (isAdded()) {
                Toast.makeText(requireContext(), "请先登录", Toast.LENGTH_SHORT).show();
            }
            return;
        }
        String conversationId = item.id;
        if (conversationId == null || conversationId.isEmpty()) {
            if (isAdded()) {
                Toast.makeText(requireContext(), "更新失败", Toast.LENGTH_SHORT).show();
            }
            return;
        }
        LCQuery<LCObject> query = new LCQuery<>("UserConversation");
        query.whereEqualTo("owner", currentUser);
        query.whereEqualTo("conversationId", conversationId);
        query.findInBackground().subscribe(new Observer<List<LCObject>>() {
            @Override
            public void onSubscribe(Disposable d) {
            }

            @Override
            public void onNext(List<LCObject> list) {
                if (!isAdded()) {
                    return;
                }
                if (list != null) {
                    for (LCObject obj : list) {
                        obj.put("otherName", newName);
                        obj.saveInBackground().subscribe();
                    }
                }
                if (isAdded()) {
                    requireActivity().runOnUiThread(() -> {
                        item.name = newName;
                        adapter.notifyDataSetChanged();
                        Toast.makeText(requireContext(), "已更新群组名称", Toast.LENGTH_SHORT).show();
                    });
                }
            }

            @Override
            public void onError(Throwable e) {
                if (!isAdded()) {
                    return;
                }
                requireActivity().runOnUiThread(() -> Toast.makeText(requireContext(), "更新失败", Toast.LENGTH_SHORT).show());
            }

            @Override
            public void onComplete() {
            }
        });
    }

    private void confirmDeleteGroup(GroupItem item) {
        new AlertDialog.Builder(requireContext())
                .setTitle("删除群组")
                .setMessage("确定要删除 " + item.name + " 吗？")
                .setPositiveButton("删除", (d, w) -> deleteGroup(item))
                .setNegativeButton("取消", null)
                .show();
    }

    private void deleteGroup(GroupItem item) {
        LCUser currentUser = LCUser.currentUser();
        if (currentUser == null) {
            if (isAdded()) {
                Toast.makeText(requireContext(), "请先登录", Toast.LENGTH_SHORT).show();
            }
            return;
        }
        String conversationId = item.id;
        if (conversationId == null || conversationId.isEmpty()) {
            if (isAdded()) {
                Toast.makeText(requireContext(), "删除失败", Toast.LENGTH_SHORT).show();
            }
            return;
        }
        LCIMClient client = LCIMClient.getInstance(currentUser.getObjectId());
        client.open(new LCIMClientCallback() {
            @Override
            public void done(LCIMClient openedClient, LCIMException e) {
                LCIMConversation conversation = openedClient != null ? openedClient.getConversation(conversationId) : null;
                if (conversation != null) {
                    conversation.quit(new LCIMConversationCallback() {
                        @Override
                        public void done(LCIMException e) {
                        }
                    });
                }
                LCQuery<LCObject> query = new LCQuery<>("UserConversation");
                query.whereEqualTo("owner", currentUser);
                query.whereEqualTo("conversationId", conversationId);
                query.findInBackground().subscribe(new Observer<List<LCObject>>() {
                    @Override
                    public void onSubscribe(Disposable d) {
                    }

                    @Override
                    public void onNext(List<LCObject> list) {
                        if (list != null && !list.isEmpty()) {
                            LCObject.deleteAllInBackground(list).subscribe();
                        }
                        if (!isAdded()) {
                            return;
                        }
                        requireActivity().runOnUiThread(() -> {
                            int index = -1;
                            for (int i = 0; i < items.size(); i++) {
                                if (items.get(i).id.equals(item.id)) {
                                    index = i;
                                    break;
                                }
                            }
                            if (index >= 0) {
                                items.remove(index);
                                adapter.notifyItemRemoved(index);
                            } else {
                                adapter.notifyDataSetChanged();
                            }
                            Toast.makeText(requireContext(), "已删除群组", Toast.LENGTH_SHORT).show();
                        });
                    }

                    @Override
                    public void onError(Throwable e) {
                        if (!isAdded()) {
                            return;
                        }
                        requireActivity().runOnUiThread(() -> Toast.makeText(requireContext(), "删除失败", Toast.LENGTH_SHORT).show());
                    }

                    @Override
                    public void onComplete() {
                    }
                });
            }
        });
    }

    static class GroupItem {
        String id;
        String name;
        String members;
        GroupItem(String id, String name, String members) {
            this.id = id;
            this.name = name;
            this.members = members;
        }
    }

    static class GroupAdapter extends RecyclerView.Adapter<GroupAdapter.ViewHolder> {
        interface Listener {
            void onItemClick(int position);
            void onItemLongClick(int position);
        }
        private final List<GroupItem> data;
        private final Listener listener;
        GroupAdapter(List<GroupItem> data, Listener listener) {
            this.data = data;
            this.listener = listener;
        }
        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_group, parent, false);
            return new ViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            GroupItem item = data.get(position);
            holder.name.setText(item.name);
            holder.members.setText(item.members);
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
            TextView members;
            ViewHolder(@NonNull View itemView) {
                super(itemView);
                name = itemView.findViewById(R.id.tvGroupName);
                members = itemView.findViewById(R.id.tvGroupMembers);
            }
        }
    }
}
