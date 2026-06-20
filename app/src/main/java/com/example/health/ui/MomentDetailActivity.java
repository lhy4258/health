package com.example.health.ui;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.PopupWindow;
import android.view.Gravity;
import android.graphics.drawable.ColorDrawable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.health.R;
import com.example.health.data.model.Comment;
import com.example.health.data.model.Moment;
import com.example.health.data.model.Favorite;

import java.util.ArrayList;
import java.util.List;

import cn.leancloud.LCException;
import cn.leancloud.LCFile;
import cn.leancloud.LCObject;
import cn.leancloud.LCQuery;
import cn.leancloud.LCUser;
import io.reactivex.Observer;
import io.reactivex.disposables.Disposable;

/**
 * 动态详情页，展示动态内容和评论列表，支持发表评论。
 * 通过 EXTRA_FROM_FAVORITE 标记可从收藏页进入（只读模式，隐藏评论输入框）。
 */
public class MomentDetailActivity extends AppCompatActivity {

    public static final String EXTRA_MOMENT_ID = "moment_id";
    public static final String EXTRA_FROM_FAVORITE = "from_favorite";

    private RecyclerView recyclerView;
    private EditText etComment;
    private Button btnSend;
    private View inputLayout;
    private DetailAdapter adapter;
    private Moment currentMoment;
    private String momentId;
    private ProgressDialog progressDialog;
    private boolean fromFavorite;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_moment_detail);

        momentId = getIntent().getStringExtra(EXTRA_MOMENT_ID);
        fromFavorite = getIntent().getBooleanExtra(EXTRA_FROM_FAVORITE, false);
        if (momentId == null) {
            finish();
            return;
        }

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowHomeEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> onBackPressed());

        recyclerView = findViewById(R.id.recyclerView);
        etComment = findViewById(R.id.etComment);
        btnSend = findViewById(R.id.btnSend);
        inputLayout = findViewById(R.id.inputLayout);

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new DetailAdapter();
        recyclerView.setAdapter(adapter);

        if (fromFavorite && inputLayout != null) {
            inputLayout.setVisibility(View.GONE);
        }

        etComment.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                btnSend.setEnabled(s.toString().trim().length() > 0);
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        btnSend.setOnClickListener(v -> sendComment());

        loadMoment();
    }

    private void loadMoment() {
        LCQuery<Moment> query = LCQuery.getQuery(Moment.class);
        query.include("author");
        query.include(Moment.KEY_IMAGES);
        query.getInBackground(momentId).subscribe(new Observer<Moment>() {
            @Override
            public void onSubscribe(Disposable d) {}

            @Override
            public void onNext(Moment moment) {
                LCUser currentUser = LCUser.currentUser();
                if (currentUser == null
                        || moment == null
                        || moment.getAuthor() == null) {
                    Toast.makeText(MomentDetailActivity.this, "无权限查看该动态", Toast.LENGTH_SHORT).show();
                    finish();
                    return;
                }
                currentMoment = moment;
                adapter.setMoment(moment);
                if (!fromFavorite) {
                    loadComments();
                }
            }

            @Override
            public void onError(Throwable e) {
                Toast.makeText(MomentDetailActivity.this, "加载动态失败", Toast.LENGTH_SHORT).show();
                finish();
            }

            @Override
            public void onComplete() {}
        });
    }

    private void loadComments() {
        LCQuery<Comment> query = LCQuery.getQuery(Comment.class);
        query.whereEqualTo(Comment.KEY_MOMENT, currentMoment);
        query.include("author");
        query.orderByAscending("createdAt");
        query.findInBackground().subscribe(new Observer<List<Comment>>() {
            @Override
            public void onSubscribe(Disposable d) {}

            @Override
            public void onNext(List<Comment> comments) {
                adapter.setComments(comments);
            }

            @Override
            public void onError(Throwable e) {
                Toast.makeText(MomentDetailActivity.this, "加载评论失败", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onComplete() {}
        });
    }

    private void sendComment() {
        String content = etComment.getText().toString().trim();
        if (content.isEmpty()) return;

        LCUser currentUser = LCUser.currentUser();
        if (currentUser == null) {
            Toast.makeText(this, "请先登录", Toast.LENGTH_SHORT).show();
            return;
        }

        progressDialog = new ProgressDialog(this);
        progressDialog.setMessage("发送中...");
        progressDialog.show();

        Comment comment = new Comment();
        comment.setContent(content);
        comment.setAuthor(currentUser);
        comment.setMoment(currentMoment);

        comment.saveInBackground().subscribe(new Observer<LCObject>() {
            @Override
            public void onSubscribe(Disposable d) {}

            @Override
            public void onNext(LCObject lcObject) {
                progressDialog.dismiss();
                etComment.setText("");
                adapter.addComment(comment);
                adapter.notifyItemChanged(0); // Update header comment count
                recyclerView.smoothScrollToPosition(adapter.getItemCount() - 1);
                
                // Update comment count on Moment (Best effort)
                currentMoment.increment(Moment.KEY_COMMENTS_COUNT);
                currentMoment.saveInBackground().subscribe();
            }

            @Override
            public void onError(Throwable e) {
                progressDialog.dismiss();
                Toast.makeText(MomentDetailActivity.this, "发送失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onComplete() {}
        });
    }

    private class DetailAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        private static final int TYPE_HEADER = 0;
        private static final int TYPE_COMMENT = 1;

        private Moment moment;
        private List<Comment> comments = new ArrayList<>();

        public void setMoment(Moment moment) {
            this.moment = moment;
            notifyDataSetChanged();
        }

        public void setComments(List<Comment> comments) {
            this.comments = comments;
            notifyDataSetChanged();
        }

        public void addComment(Comment comment) {
            this.comments.add(comment);
            notifyItemInserted(getItemCount() - 1);
        }

        @Override
        public int getItemViewType(int position) {
            return position == 0 ? TYPE_HEADER : TYPE_COMMENT;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            if (viewType == TYPE_HEADER) {
                View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_moment, parent, false);
                return new HeaderViewHolder(view);
            } else {
                View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_comment, parent, false);
                return new CommentViewHolder(view);
            }
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            if (holder instanceof HeaderViewHolder) {
                bindHeader((HeaderViewHolder) holder);
            } else if (holder instanceof CommentViewHolder) {
                bindComment((CommentViewHolder) holder, position - 1);
            }
        }

        private void bindHeader(HeaderViewHolder holder) {
            if (moment == null) return;
            Context context = holder.itemView.getContext();
            LCUser author = moment.getAuthor();

            if (fromFavorite) {
                holder.avatarImageView.setVisibility(View.GONE);
                holder.usernameTextView.setVisibility(View.GONE);
                holder.timeTextView.setVisibility(View.GONE);
                holder.likeContainer.setVisibility(View.GONE);
                holder.commentContainer.setVisibility(View.GONE);
                holder.moreButton.setVisibility(View.GONE);
            } else {
                if (author != null) {
                    String username = author.getUsername();
                    if (username == null || username.isEmpty()) username = "用户";
                    holder.usernameTextView.setText(username);

                    LCFile avatar = author.getLCFile("avatar");
                    if (avatar != null && avatar.getUrl() != null) {
                        Glide.with(context).load(avatar.getUrl()).placeholder(R.drawable.ic_nav_profile).circleCrop().into(holder.avatarImageView);
                    } else {
                        holder.avatarImageView.setImageResource(R.drawable.ic_nav_profile);
                    }

                    holder.avatarImageView.setOnClickListener(v -> {
                        Intent intent = new Intent(context, UserProfileActivity.class);
                        intent.putExtra(UserProfileActivity.EXTRA_USER_ID, author.getObjectId());
                        context.startActivity(intent);
                    });
                }

                if (moment.getCreatedAt() != null) {
                    CharSequence timeAgo = DateUtils.getRelativeTimeSpanString(moment.getCreatedAt().getTime(), System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS);
                    holder.timeTextView.setText(timeAgo);
                }
            }

            // Bind Content
            String content = moment.getContent();
            if (content != null && !content.isEmpty()) {
                holder.contentTextView.setText(content);
                holder.contentTextView.setVisibility(View.VISIBLE);
            } else {
                holder.contentTextView.setVisibility(View.GONE);
            }

            // Bind Images
            List<LCFile> images = moment.getImages();
            if (images != null && !images.isEmpty()) {
                holder.imageGrid.setVisibility(View.VISIBLE);
                GridLayoutManager layoutManager = new GridLayoutManager(context, 3);
                holder.imageGrid.setLayoutManager(layoutManager);
                holder.imageGrid.setNestedScrollingEnabled(false);
                
                MomentImagesAdapter imagesAdapter = new MomentImagesAdapter(images, (pos, imgs) -> {
                     ArrayList<String> urls = new ArrayList<>();
                     for (LCFile file : imgs) {
                         urls.add(file.getUrl());
                     }
                     Intent intent = new Intent(context, ImagePreviewActivity.class);
                     intent.putStringArrayListExtra(ImagePreviewActivity.EXTRA_IMAGE_URLS, urls);
                     intent.putExtra(ImagePreviewActivity.EXTRA_START_POSITION, pos);
                     context.startActivity(intent);
                });
                holder.imageGrid.setAdapter(imagesAdapter);
            } else {
                holder.imageGrid.setVisibility(View.GONE);
            }

            if (!fromFavorite) {
                List<String> likes = moment.getLikes();
                int likeCount = likes != null ? likes.size() : 0;
                holder.likeCountText.setText(String.valueOf(likeCount));
                
                // Check if current user liked
                LCUser currentUser = LCUser.currentUser();
                boolean isLiked = false;
                if (currentUser != null && likes != null) {
                    isLiked = likes.contains(currentUser.getObjectId());
                }
                
                if (isLiked) {
                    holder.likeIcon.setColorFilter(context.getResources().getColor(R.color.error_color));
                } else {
                    holder.likeIcon.setColorFilter(context.getResources().getColor(R.color.secondary_text_color));
                }

                int commentCount = moment.getCommentsCount();
                holder.commentCountText.setText(commentCount > 0 ? String.valueOf(commentCount) : "评论");

                holder.moreButton.setVisibility(View.VISIBLE);
                holder.moreButton.setOnClickListener(v -> showMoreMenu(v, moment));
                
                holder.likeContainer.setOnClickListener(v -> {
                    v.animate()
                        .scaleX(1.1f)
                        .scaleY(1.1f)
                        .setDuration(150)
                        .withEndAction(() -> v.animate().scaleX(1f).scaleY(1f).setDuration(150).start())
                        .start();
                    MomentDetailActivity.this.onLikeClick(moment, 0);
                });
                
                holder.commentContainer.setOnClickListener(v -> {
                    if (etComment != null) {
                        etComment.requestFocus();
                        android.view.inputmethod.InputMethodManager imm = (android.view.inputmethod.InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                        if (imm != null) {
                            imm.showSoftInput(etComment, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);
                        }
                    }
                });
            }
        }

        private void bindComment(CommentViewHolder holder, int position) {
            Comment comment = comments.get(position);
            Context context = holder.itemView.getContext();
            LCUser author = comment.getAuthor();

            if (author != null) {
                String username = author.getUsername();
                if (username == null || username.isEmpty()) username = "用户";
                holder.tvName.setText(username);
                
                LCFile avatar = author.getLCFile("avatar");
                if (avatar != null && avatar.getUrl() != null) {
                    Glide.with(context).load(avatar.getUrl()).placeholder(R.drawable.ic_nav_profile).circleCrop().into(holder.ivAvatar);
                } else {
                    holder.ivAvatar.setImageResource(R.drawable.ic_nav_profile);
                }
                
                holder.ivAvatar.setOnClickListener(v -> {
                    Intent intent = new Intent(context, UserProfileActivity.class);
                    intent.putExtra(UserProfileActivity.EXTRA_USER_ID, author.getObjectId());
                    context.startActivity(intent);
                });
            } else {
                 holder.tvName.setText("未知用户");
                 holder.ivAvatar.setImageResource(R.drawable.ic_nav_profile);
            }

            if (comment.getCreatedAt() != null) {
                CharSequence timeAgo = DateUtils.getRelativeTimeSpanString(comment.getCreatedAt().getTime(), System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS);
                holder.tvTime.setText(timeAgo);
            }

            holder.tvContent.setText(comment.getContent());
        }

        @Override
        public int getItemCount() {
            return (moment != null ? 1 : 0) + comments.size();
        }
    }

    static class HeaderViewHolder extends RecyclerView.ViewHolder {
        ImageView avatarImageView;
        TextView usernameTextView;
        TextView timeTextView;
        TextView contentTextView;
        RecyclerView imageGrid;
        ImageView moreButton;
        View likeContainer;
        ImageView likeIcon;
        TextView likeCountText;
        View commentContainer;
        TextView commentCountText;

        public HeaderViewHolder(@NonNull View itemView) {
            super(itemView);
            avatarImageView = itemView.findViewById(R.id.avatarImageView);
            usernameTextView = itemView.findViewById(R.id.usernameTextView);
            timeTextView = itemView.findViewById(R.id.timeTextView);
            contentTextView = itemView.findViewById(R.id.contentTextView);
            imageGrid = itemView.findViewById(R.id.imageGrid);
            moreButton = itemView.findViewById(R.id.moreButton);
            likeContainer = itemView.findViewById(R.id.likeContainer);
            likeIcon = itemView.findViewById(R.id.likeIcon);
            likeCountText = itemView.findViewById(R.id.likeCountText);
            commentContainer = itemView.findViewById(R.id.commentContainer);
            commentCountText = itemView.findViewById(R.id.commentCountText);
        }
    }

    static class CommentViewHolder extends RecyclerView.ViewHolder {
        ImageView ivAvatar;
        TextView tvName;
        TextView tvTime;
        TextView tvContent;

        public CommentViewHolder(@NonNull View itemView) {
            super(itemView);
            ivAvatar = itemView.findViewById(R.id.ivAvatar);
            tvName = itemView.findViewById(R.id.tvName);
            tvTime = itemView.findViewById(R.id.tvTime);
            tvContent = itemView.findViewById(R.id.tvContent);
        }
    }

    public void onLikeClick(Moment moment, int position) {
        LCUser currentUser = LCUser.currentUser();
        if (currentUser == null) {
            Toast.makeText(this, "请先登录", Toast.LENGTH_SHORT).show();
            return;
        }

        List<String> likes = moment.getLikes();
        if (likes == null) {
            likes = new ArrayList<>();
        }

        String userId = currentUser.getObjectId();
        boolean isLiked = likes.contains(userId);

        if (isLiked) {
            likes.remove(userId);
        } else {
            likes.add(userId);
        }

        moment.setLikes(likes);
        // Optimistic update
        adapter.notifyItemChanged(position);

        moment.saveInBackground().subscribe(new Observer<LCObject>() {
            @Override
            public void onSubscribe(Disposable d) {}

            @Override
            public void onNext(LCObject lcObject) {
                // Success
            }

            @Override
            public void onError(Throwable e) {
                // Revert on error
                if (isLiked) {
                    moment.getLikes().add(userId);
                } else {
                    moment.getLikes().remove(userId);
                }
                adapter.notifyItemChanged(position);
                Toast.makeText(MomentDetailActivity.this, "操作失败", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onComplete() {}
        });
    }

    private void showMoreMenu(View anchor, Moment moment) {
        LCUser currentUser = LCUser.currentUser();
        boolean isMine = currentUser != null && moment.getAuthor() != null &&
                currentUser.getObjectId().equals(moment.getAuthor().getObjectId());

        View contentView = LayoutInflater.from(this).inflate(R.layout.view_moment_more_menu, null);
        PopupWindow popup = new PopupWindow(contentView,
                RecyclerView.LayoutParams.WRAP_CONTENT,
                RecyclerView.LayoutParams.WRAP_CONTENT,
                true);
        popup.setBackgroundDrawable(new ColorDrawable(0x00000000));
        popup.setOutsideTouchable(true);

        contentView.setAlpha(0f);
        contentView.setScaleX(0.95f);
        contentView.setScaleY(0.95f);
        contentView.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(200)
                .start();

        TextView menuDelete = contentView.findViewById(R.id.menuDelete);
        TextView menuFavorite = contentView.findViewById(R.id.menuFavorite);

        menuDelete.setOnClickListener(v -> {
            popup.dismiss();
            if (isMine) {
                deleteCurrentMoment();
            } else {
                Toast.makeText(this, "只能删除自己的动态", Toast.LENGTH_SHORT).show();
            }
        });

        menuFavorite.setOnClickListener(v -> {
            popup.dismiss();
            addToFavorite(moment);
        });

        contentView.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
        int popupWidth = contentView.getMeasuredWidth();
        int popupHeight = contentView.getMeasuredHeight();

        int[] location = new int[2];
        anchor.getLocationOnScreen(location);
        int anchorCenterY = location[1] + anchor.getHeight() / 2;
        int x = location[0] - popupWidth;
        int y = anchorCenterY - popupHeight / 2;

        popup.showAtLocation(anchor, Gravity.START | Gravity.TOP, x, y);
    }

    private void deleteCurrentMoment() {
        if (currentMoment == null) return;
        currentMoment.deleteInBackground().subscribe(new Observer<cn.leancloud.types.LCNull>() {
            @Override
            public void onSubscribe(Disposable d) {}

            @Override
            public void onNext(cn.leancloud.types.LCNull lcNull) {
                Toast.makeText(MomentDetailActivity.this, "已删除", Toast.LENGTH_SHORT).show();
                finish();
            }

            @Override
            public void onError(Throwable e) {
                Toast.makeText(MomentDetailActivity.this, "删除失败", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onComplete() {}
        });
    }

    private void addToFavorite(Moment moment) {
        LCUser currentUser = LCUser.currentUser();
        if (currentUser == null) {
            Toast.makeText(this, "请先登录", Toast.LENGTH_SHORT).show();
            return;
        }

        LCQuery<Favorite> query = LCQuery.getQuery(Favorite.class);
        query.whereEqualTo(Favorite.KEY_USER, currentUser);
        query.whereEqualTo(Favorite.KEY_MOMENT, moment);
        query.limit(1);

        query.findInBackground().subscribe(new Observer<List<Favorite>>() {
            @Override
            public void onSubscribe(Disposable d) {}

            @Override
            public void onNext(List<Favorite> favorites) {
                if (favorites != null && !favorites.isEmpty()) {
                    Toast.makeText(MomentDetailActivity.this, "已在收藏", Toast.LENGTH_SHORT).show();
                    return;
                }

                Favorite favorite = new Favorite();
                favorite.setUser(currentUser);
                favorite.setMoment(moment);
                favorite.setType(Favorite.TYPE_MOMENT);
                favorite.setSourceMomentId(moment.getObjectId());
                favorite.setText(moment.getContent());
                favorite.setImages(moment.getImages());
                favorite.saveInBackground().subscribe(new Observer<LCObject>() {
                    @Override
                    public void onSubscribe(Disposable d) {}

                    @Override
                    public void onNext(LCObject lcObject) {
                        Toast.makeText(MomentDetailActivity.this, "已收藏", Toast.LENGTH_SHORT).show();
                    }

                    @Override
                    public void onError(Throwable e) {
                        Toast.makeText(MomentDetailActivity.this, "收藏失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    }

                    @Override
                    public void onComplete() {}
                });
            }

            @Override
            public void onError(Throwable e) {
                if (e instanceof LCException && ((LCException) e).getCode() == 101) {
                    Favorite favorite = new Favorite();
                    favorite.setUser(currentUser);
                    favorite.setMoment(moment);
                    favorite.saveInBackground().subscribe(new Observer<LCObject>() {
                        @Override
                        public void onSubscribe(Disposable d) {}

                        @Override
                        public void onNext(LCObject lcObject) {
                            Toast.makeText(MomentDetailActivity.this, "已收藏", Toast.LENGTH_SHORT).show();
                        }

                        @Override
                        public void onError(Throwable saveError) {
                            Toast.makeText(MomentDetailActivity.this, "收藏失败: " + saveError.getMessage(), Toast.LENGTH_SHORT).show();
                        }

                        @Override
                        public void onComplete() {}
                    });
                    return;
                }
                Toast.makeText(MomentDetailActivity.this, "收藏失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onComplete() {}
        });
    }
}
