package com.example.health.ui;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.health.R;

import java.util.List;

import cn.leancloud.LCFile;

/**
 * 动态图片网格 RecyclerView Adapter，用 Glide 加载 Moment 中关联的 LCFile 图片。
 * 点击图片跳转 ImagePreviewActivity 大图预览。
 */
public class MomentImagesAdapter extends RecyclerView.Adapter<MomentImagesAdapter.ImageViewHolder> {

    private Context context;
    private List<LCFile> images;
    private OnImageClickListener listener;

    public interface OnImageClickListener {
        void onImageClick(int position, List<LCFile> images);
    }

    public MomentImagesAdapter(List<LCFile> images, OnImageClickListener listener) {
        this.images = images;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ImageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        context = parent.getContext();
        View view = LayoutInflater.from(context).inflate(R.layout.item_moment_image, parent, false);
        return new ImageViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ImageViewHolder holder, int position) {
        LCFile file = images.get(position);
        if (file != null && file.getUrl() != null) {
            Glide.with(context)
                    .load(file.getUrl())
                    .centerCrop()
                    .placeholder(R.color.placeholder_grey)
                    .into(holder.imageView);
        } else {
             holder.imageView.setImageResource(R.color.placeholder_grey);
        }

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onImageClick(position, images);
            }
        });
    }

    @Override
    public int getItemCount() {
        return images != null ? images.size() : 0;
    }

    static class ImageViewHolder extends RecyclerView.ViewHolder {
        ImageView imageView;

        public ImageViewHolder(@NonNull View itemView) {
            super(itemView);
            imageView = itemView.findViewById(R.id.imageView);
        }
    }
}
