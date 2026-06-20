package com.example.health.ui;

import android.content.Context;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.health.R;
import com.example.health.data.model.Favorite;
import com.example.health.data.model.Moment;

import java.util.ArrayList;
import java.util.List;

import cn.leancloud.LCFile;

/**
 * 收藏列表 RecyclerView Adapter，显示用户收藏的动态或自定义收藏内容。
 * 展示来源类型、时间、文字摘要和图片信息。
 */
public class FavoriteAdapter extends RecyclerView.Adapter<FavoriteAdapter.FavoriteViewHolder> {

    private final List<Favorite> favorites = new ArrayList<>();
    private Context context;
    private OnFavoriteInteractionListener listener;

    public interface OnFavoriteInteractionListener {
        void onItemClick(Favorite favorite, int position);
        void onItemLongClick(Favorite favorite, int position);
    }

    public void setOnFavoriteInteractionListener(OnFavoriteInteractionListener listener) {
        this.listener = listener;
    }

    public void setFavorites(List<Favorite> items) {
        favorites.clear();
        if (items != null) {
            favorites.addAll(items);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public FavoriteViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        context = parent.getContext();
        View view = LayoutInflater.from(context).inflate(R.layout.item_favorite, parent, false);
        return new FavoriteViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull FavoriteViewHolder holder, int position) {
        Favorite favorite = favorites.get(position);

        String type = favorite.getType();
        if (Favorite.TYPE_MOMENT.equals(type)) {
            holder.sourceTextView.setText("来自动态");
        } else if (Favorite.TYPE_CUSTOM.equals(type)) {
            holder.sourceTextView.setText("自定义收藏");
        } else {
            holder.sourceTextView.setText("收藏内容");
        }

        if (favorite.getCreatedAt() != null) {
            CharSequence timeAgo = DateUtils.getRelativeTimeSpanString(
                    favorite.getCreatedAt().getTime(),
                    System.currentTimeMillis(),
                    DateUtils.MINUTE_IN_MILLIS
            );
            holder.timeTextView.setText(timeAgo);
            holder.timeTextView.setVisibility(View.VISIBLE);
        } else {
            holder.timeTextView.setVisibility(View.GONE);
        }

        String content = favorite.getText();
        if (TextUtils.isEmpty(content) && Favorite.TYPE_MOMENT.equals(type)) {
            Moment moment = favorite.getMoment();
            if (moment != null) {
                content = moment.getContent();
            }
        }

        if (!TextUtils.isEmpty(content)) {
            holder.contentTextView.setText(content);
            holder.contentTextView.setVisibility(View.VISIBLE);
        } else {
            holder.contentTextView.setText("");
            holder.contentTextView.setVisibility(View.GONE);
        }

        List<LCFile> images = favorite.getImages();
        if (images != null && !images.isEmpty()) {
            int count = images.size();
            holder.imageInfoTextView.setText("含 " + count + " 张图片");
            holder.imageInfoTextView.setVisibility(View.VISIBLE);
        } else {
            holder.imageInfoTextView.setText("");
            holder.imageInfoTextView.setVisibility(View.GONE);
        }

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                int adapterPosition = holder.getAdapterPosition();
                if (adapterPosition != RecyclerView.NO_POSITION) {
                    listener.onItemClick(favorite, adapterPosition);
                }
            }
        });

        holder.itemView.setOnLongClickListener(v -> {
            if (listener != null) {
                int adapterPosition = holder.getAdapterPosition();
                if (adapterPosition != RecyclerView.NO_POSITION) {
                    listener.onItemLongClick(favorite, adapterPosition);
                    return true;
                }
            }
            return false;
        });
    }

    @Override
    public int getItemCount() {
        return favorites.size();
    }

    static class FavoriteViewHolder extends RecyclerView.ViewHolder {
        TextView sourceTextView;
        TextView timeTextView;
        TextView contentTextView;
        TextView imageInfoTextView;

        FavoriteViewHolder(@NonNull View itemView) {
            super(itemView);
            sourceTextView = itemView.findViewById(R.id.tvFavoriteSource);
            timeTextView = itemView.findViewById(R.id.tvFavoriteTime);
            contentTextView = itemView.findViewById(R.id.tvFavoriteContent);
            imageInfoTextView = itemView.findViewById(R.id.tvFavoriteImageInfo);
        }
    }
}
