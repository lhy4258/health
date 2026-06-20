package com.example.health.ui;

import android.content.Context;
import android.content.Intent;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.health.R;
import com.example.health.data.model.Moment;

import java.util.ArrayList;
import java.util.List;

import cn.leancloud.LCFile;
import cn.leancloud.LCUser;

/**
 * 动态列表 RecyclerView Adapter，渲染 Moment 项的 ViewHolder，含作者头像、文字内容、
 * 图片网格、点赞数和评论数。支持只读模式，用于 ProfileDetailActivity 中查看他人动态。
 */
public class MomentsAdapter extends RecyclerView.Adapter<MomentsAdapter.MomentViewHolder> {

    private List<Moment> moments = new ArrayList<>();
    private Context context;
    private OnMomentInteractionListener listener;
    private boolean isReadOnly = false;

    public interface OnMomentInteractionListener {
        void onLikeClick(Moment moment, int position);
        void onCommentClick(Moment moment, int position);
        void onShareClick(Moment moment, int position);
        void onMoreClick(Moment moment, View anchor, int position);
        void onAvatarClick(LCUser user);
        void onItemClick(Moment moment, int position);  
    }

    public void setOnMomentInteractionListener(OnMomentInteractionListener listener) {
        this.listener = listener;
    }

    public void setReadOnly(boolean readOnly) {
        this.isReadOnly = readOnly;
        notifyDataSetChanged();
    }

    public void setMoments(List<Moment> moments) {
        this.moments = moments;
        notifyDataSetChanged();
    }
    
    public void addMoments(List<Moment> newMoments) {
        int startPos = this.moments.size();
        this.moments.addAll(newMoments);
        notifyItemRangeInserted(startPos, newMoments.size());
    }
    
    public void clear() {
        this.moments.clear();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public MomentViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        context = parent.getContext();
        View view = LayoutInflater.from(context).inflate(R.layout.item_moment, parent, false);
        return new MomentViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull MomentViewHolder holder, int position) {
        Moment moment = moments.get(position);
        LCUser author = moment.getAuthor();
        
        // Bind Author Info
        if (author != null) {
            String username = author.getUsername();
            if (username == null || username.isEmpty()) {
                username = "用户";
            }
            holder.usernameTextView.setText(username);
            
            LCFile avatar = author.getLCFile("avatar");
            if (avatar != null && avatar.getUrl() != null) {
                Glide.with(context)
                    .load(avatar.getUrl())
                    .placeholder(R.drawable.ic_nav_profile)
                    .circleCrop()
                    .into(holder.avatarImageView);
            } else {
                holder.avatarImageView.setImageResource(R.drawable.ic_nav_profile);
            }
            
            holder.avatarImageView.setOnClickListener(v -> {
                if (listener != null) listener.onAvatarClick(author);
            });
        } else {
            holder.usernameTextView.setText("未知用户");
            holder.avatarImageView.setImageResource(R.drawable.ic_nav_profile);
        }

        // Bind Time
        if (moment.getCreatedAt() != null) {
            CharSequence timeAgo = DateUtils.getRelativeTimeSpanString(
                moment.getCreatedAt().getTime(),
                System.currentTimeMillis(),
                DateUtils.MINUTE_IN_MILLIS
            );
            holder.timeTextView.setText(timeAgo);
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
            
            // Use GridLayoutManager with 3 columns
            GridLayoutManager layoutManager = new GridLayoutManager(context, 3);
            holder.imageGrid.setLayoutManager(layoutManager);
            holder.imageGrid.setNestedScrollingEnabled(false); // Disable nested scrolling
            
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
        
        // Bind Stats
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

        // Listeners
        holder.likeContainer.setOnClickListener(v -> {
            v.animate()
                    .scaleX(1.1f)
                    .scaleY(1.1f)
                    .setDuration(150)
                    .withEndAction(() -> v.animate().scaleX(1f).scaleY(1f).setDuration(150).start())
                    .start();
            if (listener != null) {
                int adapterPosition = holder.getAdapterPosition();
                if (adapterPosition != RecyclerView.NO_POSITION) {
                    listener.onLikeClick(moment, adapterPosition);
                }
            }
        });
        
        holder.commentContainer.setOnClickListener(v -> {
            if (listener != null) listener.onCommentClick(moment, position);
        });
        
        holder.shareContainer.setOnClickListener(v -> {
            if (listener != null) listener.onShareClick(moment, position);
        });
        
        holder.moreButton.setOnClickListener(v -> {
            if (listener != null) listener.onMoreClick(moment, v, position);
        });

        if (isReadOnly) {
            holder.shareContainer.setVisibility(View.GONE);
            holder.moreButton.setVisibility(View.GONE);
        } else {
            holder.shareContainer.setVisibility(View.VISIBLE);
            holder.moreButton.setVisibility(View.VISIBLE);
        }

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onItemClick(moment, position);
        });
    }

    @Override
    public int getItemCount() {
        return moments.size();
    }

    static class MomentViewHolder extends RecyclerView.ViewHolder {
        ImageView avatarImageView;
        TextView usernameTextView;
        TextView timeTextView;
        ImageView moreButton;
        TextView contentTextView;
        RecyclerView imageGrid;
        
        View likeContainer;
        ImageView likeIcon;
        TextView likeCountText;
        
        View commentContainer;
        TextView commentCountText;
        
        View shareContainer;

        public MomentViewHolder(@NonNull View itemView) {
            super(itemView);
            avatarImageView = itemView.findViewById(R.id.avatarImageView);
            usernameTextView = itemView.findViewById(R.id.usernameTextView);
            timeTextView = itemView.findViewById(R.id.timeTextView);
            moreButton = itemView.findViewById(R.id.moreButton);
            contentTextView = itemView.findViewById(R.id.contentTextView);
            imageGrid = itemView.findViewById(R.id.imageGrid);
            
            likeContainer = itemView.findViewById(R.id.likeContainer);
            likeIcon = itemView.findViewById(R.id.likeIcon);
            likeCountText = itemView.findViewById(R.id.likeCountText);
            
            commentContainer = itemView.findViewById(R.id.commentContainer);
            commentCountText = itemView.findViewById(R.id.commentCountText);
            
            shareContainer = itemView.findViewById(R.id.shareContainer);
        }
    }
}
