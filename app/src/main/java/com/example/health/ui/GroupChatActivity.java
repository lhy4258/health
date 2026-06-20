package com.example.health.ui;

import android.app.Activity;
import android.content.Intent;
import android.content.ClipData;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Gravity;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.health.R;
import com.bumptech.glide.Glide;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Date;
import java.text.SimpleDateFormat;
import java.util.Locale;

import cn.leancloud.LCFile;
import cn.leancloud.LCUser;
import cn.leancloud.im.v2.LCIMClient;
import cn.leancloud.im.v2.LCIMConversation;
import cn.leancloud.im.v2.LCIMException;
import cn.leancloud.im.v2.LCIMMessage;
import cn.leancloud.im.v2.callback.LCIMClientCallback;
import cn.leancloud.im.v2.callback.LCIMConversationCallback;
import cn.leancloud.im.v2.callback.LCIMMessagesQueryCallback;
import cn.leancloud.im.v2.messages.LCIMTextMessage;
import io.reactivex.Observer;
import io.reactivex.disposables.Disposable;

/**
 * 群聊页面，基于 LeanCloud IM 实现群组消息收发。
 * 支持发送文本消息和图片消息，加载最近 50 条历史消息，自动预加载头像缓存。
 */
public class GroupChatActivity extends AppCompatActivity {
    private static final int REQUEST_PICK_IMAGE = 2001;
    private RecyclerView recyclerView;
    private EditText inputEditText;
    private ImageView sendButton;
    private ImageView imageButton;
    private ChatAdapter adapter;
    private final List<LCIMMessage> messages = new ArrayList<>();
    private String conversationId;
    private String title;
    private LCIMClient imClient;
    private LCIMConversation conversation;
    private String selfId;
    private final Map<String, String> avatarCache = new HashMap<>();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_group_chat);
        
        androidx.appcompat.widget.Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowHomeEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> finish());

        recyclerView = findViewById(R.id.recyclerView);
        inputEditText = findViewById(R.id.editInput);
        sendButton = findViewById(R.id.btnSend);
        imageButton = findViewById(R.id.btnImage);
        title = getIntent().getStringExtra("title");
        conversationId = getIntent().getStringExtra("conversationId");
        setTitle(title != null ? title : "群聊");
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new ChatAdapter(messages, avatarCache);
        recyclerView.setAdapter(adapter);
        sendButton.setOnClickListener(v -> sendMessage());
        imageButton.setOnClickListener(v -> pickImage());
        initIm();
    }

    private void initIm() {
        if (conversationId == null || conversationId.isEmpty()) {
            finish();
            return;
        }
        LCUser currentUser = LCUser.currentUser();
        if (currentUser == null) {
            finish();
            return;
        }
        String clientId = currentUser.getObjectId();
        if (clientId == null || clientId.isEmpty()) {
            finish();
            return;
        }
        selfId = clientId;
        LCFile avatar = currentUser.getLCFile("avatar");
        if (avatar != null && avatar.getUrl() != null) {
            avatarCache.put(clientId, avatar.getUrl());
        }
        if (adapter != null) {
            adapter.setSelfId(selfId);
        }
        imClient = LCIMClient.getInstance(clientId);
        imClient.open(new LCIMClientCallback() {
            @Override
            public void done(LCIMClient client, LCIMException e) {
                if (e != null) {
                    return;
                }
                conversation = client.getConversation(conversationId);
                loadMessages();
            }
        });
    }

    private void loadMessages() {
        if (conversation == null) {
            return;
        }
        conversation.queryMessages(50, new LCIMMessagesQueryCallback() {
            @Override
            public void done(List<LCIMMessage> list, LCIMException e) {
                if (e != null) {
                    return;
                }
                String lastText = null;
                if (list != null && !list.isEmpty()) {
                    LCIMMessage last = list.get(list.size() - 1);
                    if (last instanceof LCIMTextMessage) {
                        lastText = ((LCIMTextMessage) last).getText();
                    }
                }
                if (lastText != null) {
                    updateLastMessagePreview(lastText);
                }
                preloadAvatars(list);
                runOnUiThread(() -> {
                    messages.clear();
                    if (list != null) {
                        messages.addAll(list);
                    }
                    adapter.notifyDataSetChanged();
                    if (!messages.isEmpty()) {
                        recyclerView.scrollToPosition(messages.size() - 1);
                    }
                });
            }
        });
    }

    private void preloadAvatars(List<LCIMMessage> list) {
        if (list == null || list.isEmpty()) {
            return;
        }
        List<String> ids = new ArrayList<>();
        for (LCIMMessage m : list) {
            if (m == null) {
                continue;
            }
            String fromId = m.getFrom();
            if (fromId == null || fromId.isEmpty()) {
                continue;
            }
            if (avatarCache.containsKey(fromId)) {
                continue;
            }
            ids.add(fromId);
        }
        if (ids.isEmpty()) {
            return;
        }
        cn.leancloud.LCQuery<LCUser> query = LCUser.getQuery();
        query.whereContainedIn("objectId", ids);
        query.findInBackground().subscribe(new Observer<List<LCUser>>() {
            @Override
            public void onSubscribe(Disposable d) {
            }

            @Override
            public void onNext(List<LCUser> users) {
                if (users == null || users.isEmpty()) {
                    return;
                }
                for (LCUser u : users) {
                    if (u == null) {
                        continue;
                    }
                    String id = u.getObjectId();
                    if (id == null || id.isEmpty()) {
                        continue;
                    }
                    LCFile avatar = u.getLCFile("avatar");
                    if (avatar == null || avatar.getUrl() == null) {
                        continue;
                    }
                    avatarCache.put(id, avatar.getUrl());
                }
                runOnUiThread(() -> {
                    if (adapter != null) {
                        adapter.notifyDataSetChanged();
                    }
                });
            }

            @Override
            public void onError(Throwable e) {
            }

            @Override
            public void onComplete() {
            }
        });
    }

    private void sendMessage() {
        String text = inputEditText.getText().toString().trim();
        if (TextUtils.isEmpty(text)) return;
        if (conversation == null) {
            return;
        }
        LCIMTextMessage msg = new LCIMTextMessage();
        msg.setText(text);
        conversation.sendMessage(msg, new LCIMConversationCallback() {
            @Override
            public void done(LCIMException e) {
                if (e != null) {
                    return;
                }
                updateLastMessagePreview(text);
                runOnUiThread(() -> {
                    messages.add(msg);
                    adapter.notifyItemInserted(messages.size() - 1);
                    recyclerView.scrollToPosition(messages.size() - 1);
                    inputEditText.setText("");
                });
            }
        });
    }

    private void pickImage() {
        if (conversation == null) {
            return;
        }
        Intent intent;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent = new Intent(MediaStore.ACTION_PICK_IMAGES);
            intent.setType("image/*");
            intent.putExtra(MediaStore.EXTRA_PICK_IMAGES_MAX, 9);
        } else {
            intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("image/*");
            intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        }
        startActivityForResult(intent, REQUEST_PICK_IMAGE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_PICK_IMAGE) {
            return;
        }
        if (resultCode != Activity.RESULT_OK || data == null) {
            return;
        }
        if (conversation == null) {
            return;
        }
        ClipData clipData = data.getClipData();
        if (clipData != null && clipData.getItemCount() > 0) {
            int count = Math.min(clipData.getItemCount(), 9);
            for (int i = 0; i < count; i++) {
                ClipData.Item item = clipData.getItemAt(i);
                if (item != null) {
                    Uri uri = item.getUri();
                    if (uri != null) {
                        sendImageMessageFromUri(uri);
                    }
                }
            }
        } else {
            Uri uri = data.getData();
            if (uri != null) {
                sendImageMessageFromUri(uri);
            }
        }
    }

    private void sendImageMessageFromUri(Uri uri) {
        byte[] bytes = readAndCompressFromUri(uri);
        if (bytes == null || bytes.length == 0 || conversation == null) {
            return;
        }
        String fileName = "chat_image_" + System.currentTimeMillis() + ".jpg";
        LCFile file = new LCFile(fileName, bytes);
        file.saveInBackground().subscribe(new Observer<LCFile>() {
            @Override
            public void onSubscribe(Disposable d) {
            }

            @Override
            public void onNext(LCFile lcFile) {
                LCIMTextMessage msg = new LCIMTextMessage();
                msg.setText("[img]" + lcFile.getUrl());
                conversation.sendMessage(msg, new LCIMConversationCallback() {
                    @Override
                    public void done(LCIMException e) {
                        if (e != null) {
                            return;
                        }
                        updateLastMessagePreview("[图片]");
                        runOnUiThread(() -> {
                            messages.add(msg);
                            adapter.notifyItemInserted(messages.size() - 1);
                            recyclerView.scrollToPosition(messages.size() - 1);
                        });
                    }
                });
            }

            @Override
            public void onError(Throwable e) {
            }

            @Override
            public void onComplete() {
            }
        });
    }

    private byte[] readAndCompressFromUri(Uri uri) {
        try {
            InputStream is = getContentResolver().openInputStream(uri);
            if (is == null) {
                return null;
            }
            Bitmap bitmap = BitmapFactory.decodeStream(is);
            is.close();
            if (bitmap == null) {
                return null;
            }
            return compressBitmap(bitmap);
        } catch (Exception e) {
            return null;
        }
    }

    private byte[] compressBitmap(Bitmap bitmap) {
        int quality = 90;
        byte[] data;
        do {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, bos);
            data = bos.toByteArray();
            quality -= 10;
        } while (data.length > 5 * 1024 * 1024 && quality >= 40);
        return data;
    }

    private void updateLastMessagePreview(String text) {
        if (conversationId == null || conversationId.isEmpty()) {
            return;
        }
        String safe = text != null ? text : "";
        final String content = safe.startsWith("[img]") ? "[图片]" : safe;
        cn.leancloud.LCQuery<cn.leancloud.LCObject> q = new cn.leancloud.LCQuery<>("UserConversation");
        q.whereEqualTo("conversationId", conversationId);
        q.findInBackground().subscribe(new io.reactivex.Observer<java.util.List<cn.leancloud.LCObject>>() {
            @Override
            public void onSubscribe(io.reactivex.disposables.Disposable d) {
            }

            @Override
            public void onNext(java.util.List<cn.leancloud.LCObject> list) {
                if (list == null || list.isEmpty()) {
                    return;
                }
                for (cn.leancloud.LCObject obj : list) {
                    if (obj == null) {
                        continue;
                    }
                    obj.put("lastMessage", content);
                    obj.saveInBackground().subscribe();
                }
            }

            @Override
            public void onError(Throwable e) {
            }

            @Override
            public void onComplete() {
            }
        });
    }

    static class ChatAdapter extends RecyclerView.Adapter<ChatAdapter.ViewHolder> {
        private final List<LCIMMessage> data;
        private final Map<String, String> avatarCache;
        private String selfId;
        private final SimpleDateFormat timeFormat = new SimpleDateFormat("MM-dd HH:mm", Locale.getDefault());
        ChatAdapter(List<LCIMMessage> data, Map<String, String> avatarCache) {
            this.data = data;
            this.avatarCache = avatarCache != null ? avatarCache : new HashMap<>();
        }
        void setSelfId(String selfId) {
            this.selfId = selfId;
        }
        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_chat_message, parent, false);
            return new ViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            LCIMMessage m = data.get(position);
            String rawText = "";
            String imageUrl = null;
            if (m instanceof LCIMTextMessage) {
                rawText = ((LCIMTextMessage) m).getText();
            }

            long timestamp = m != null ? m.getTimestamp() : 0L;
            if (timestamp > 0) {
                holder.time.setText(timeFormat.format(new Date(timestamp)));
                holder.time.setVisibility(View.VISIBLE);
            } else {
                holder.time.setText("");
                holder.time.setVisibility(View.GONE);
            }
            String text = rawText;
            if (rawText != null && rawText.startsWith("[img]")) {
                imageUrl = rawText.substring(5);
                text = "";
            }
            holder.content.setText(text);
            if (imageUrl != null && !imageUrl.isEmpty()) {
                holder.image.setVisibility(View.VISIBLE);
                Glide.with(holder.image.getContext()).load(imageUrl).centerCrop().into(holder.image);
            } else {
                holder.image.setVisibility(View.GONE);
            }
            String fromId = m.getFrom();
            boolean isSelf = selfId != null && selfId.equals(fromId);
            ViewGroup.LayoutParams lpAvatar = holder.avatar.getLayoutParams();
            ViewGroup.LayoutParams lpCard = holder.card.getLayoutParams();
            ViewGroup.LayoutParams lpTime = holder.time.getLayoutParams();
            if (lpAvatar instanceof android.widget.RelativeLayout.LayoutParams
                    && lpCard instanceof android.widget.RelativeLayout.LayoutParams
                    && lpTime instanceof android.widget.RelativeLayout.LayoutParams) {
                android.widget.RelativeLayout.LayoutParams avatarParams = (android.widget.RelativeLayout.LayoutParams) lpAvatar;
                android.widget.RelativeLayout.LayoutParams cardParams = (android.widget.RelativeLayout.LayoutParams) lpCard;
                android.widget.RelativeLayout.LayoutParams timeParams = (android.widget.RelativeLayout.LayoutParams) lpTime;
                int margin = (int) (holder.itemView.getResources().getDisplayMetrics().density * 8);

                avatarParams.removeRule(android.widget.RelativeLayout.ALIGN_PARENT_START);
                avatarParams.removeRule(android.widget.RelativeLayout.ALIGN_PARENT_END);

                cardParams.removeRule(android.widget.RelativeLayout.START_OF);
                cardParams.removeRule(android.widget.RelativeLayout.END_OF);
                cardParams.removeRule(android.widget.RelativeLayout.ALIGN_PARENT_START);
                cardParams.removeRule(android.widget.RelativeLayout.ALIGN_PARENT_END);
                cardParams.removeRule(android.widget.RelativeLayout.LEFT_OF);
                cardParams.removeRule(android.widget.RelativeLayout.RIGHT_OF);

                timeParams.removeRule(android.widget.RelativeLayout.ALIGN_PARENT_START);
                timeParams.removeRule(android.widget.RelativeLayout.ALIGN_PARENT_END);
                timeParams.removeRule(android.widget.RelativeLayout.ALIGN_START);
                timeParams.removeRule(android.widget.RelativeLayout.ALIGN_END);
                timeParams.removeRule(android.widget.RelativeLayout.BELOW);
                timeParams.addRule(android.widget.RelativeLayout.BELOW, holder.card.getId());

                if (isSelf) {
                    avatarParams.addRule(android.widget.RelativeLayout.ALIGN_PARENT_END);
                    avatarParams.setMargins(margin, margin, margin, margin);

                    cardParams.addRule(android.widget.RelativeLayout.LEFT_OF, holder.avatar.getId());
                    cardParams.setMargins(margin, margin, margin, margin);

                    timeParams.addRule(android.widget.RelativeLayout.ALIGN_END, holder.card.getId());
                    timeParams.setMargins(0, 0, 0, margin / 2);

                    holder.content.setGravity(Gravity.END);
                    holder.time.setGravity(Gravity.END);
                } else {
                    avatarParams.addRule(android.widget.RelativeLayout.ALIGN_PARENT_START);
                    avatarParams.setMargins(margin, margin, margin, margin);

                    cardParams.addRule(android.widget.RelativeLayout.RIGHT_OF, holder.avatar.getId());
                    cardParams.setMargins(margin, margin, margin, margin);

                    timeParams.addRule(android.widget.RelativeLayout.ALIGN_START, holder.card.getId());
                    timeParams.setMargins(0, 0, 0, margin / 2);

                    holder.content.setGravity(Gravity.START);
                    holder.time.setGravity(Gravity.START);
                }

                holder.avatar.setLayoutParams(avatarParams);
                holder.card.setLayoutParams(cardParams);
                holder.time.setLayoutParams(timeParams);
            }
            if (fromId != null && !fromId.isEmpty()) {
                String cached = avatarCache != null ? avatarCache.get(fromId) : null;
                if (cached != null && !cached.isEmpty()) {
                    Glide.with(holder.avatar.getContext()).load(cached).centerCrop().into(holder.avatar);
                } else {
                    holder.avatar.setImageResource(R.drawable.ic_nav_profile);
                }
            } else {
                holder.avatar.setImageResource(R.drawable.ic_nav_profile);
            }
            
            holder.avatar.setOnClickListener(v -> {
                String uid = m.getFrom();
                if (uid != null && !uid.isEmpty()) {
                    android.content.Intent intent = new android.content.Intent(v.getContext(), UserProfileActivity.class);
                    intent.putExtra(UserProfileActivity.EXTRA_USER_ID, uid);
                    v.getContext().startActivity(intent);
                }
            });
        }

        @Override
        public int getItemCount() {
            return data.size();
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            TextView content;
            TextView time;
            ImageView avatar;
            ImageView image;
            androidx.cardview.widget.CardView card;
            ViewHolder(@NonNull View itemView) {
                super(itemView);
                content = itemView.findViewById(R.id.tvContent);
                time = itemView.findViewById(R.id.tvTime);
                avatar = itemView.findViewById(R.id.ivAvatar);
                image = itemView.findViewById(R.id.ivImage);
                card = itemView.findViewById(R.id.cardMessage);
            }
        }
    }
}
