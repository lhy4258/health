package com.example.health.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.health.R;
import com.example.health.data.model.Photo;

import java.util.List;

/**
 * 相片网格 RecyclerView Adapter，使用 Glide 缩略图加载相册照片。
 * 支持选择模式（多选），用于 AlbumDetailActivity 等场景。
 */
public class PhotoGridAdapter extends RecyclerView.Adapter<PhotoGridAdapter.PhotoViewHolder> {

    public interface OnPhotoInteractionListener {
        void onPhotoClick(Photo photo);
        void onPhotoLongClick(Photo photo);
    }

    private final List<Photo> photos;
    private final OnPhotoInteractionListener listener;
    private boolean selectionMode = false;

    public PhotoGridAdapter(List<Photo> photos, OnPhotoInteractionListener listener) {
        this.photos = photos;
        this.listener = listener;
    }

    public void setSelectionMode(boolean enabled) {
        selectionMode = enabled;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public PhotoViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_photo_grid, parent, false);
        return new PhotoViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull PhotoViewHolder holder, int position) {
        Photo photo = photos.get(position);
        if (photo.getImage() != null && photo.getImage().getUrl() != null) {
            Glide.with(holder.imagePhoto.getContext())
                    .load(photo.getImage().getUrl())
                    .thumbnail(0.25f)
                    .centerCrop()
                    .placeholder(R.color.placeholder_grey)
                    .into(holder.imagePhoto);
        } else {
            holder.imagePhoto.setImageResource(R.color.placeholder_grey);
        }

        holder.selectionOverlay.setVisibility(selectionMode ? View.VISIBLE : View.GONE);
        holder.textSelectedMark.setVisibility(selectionMode ? View.VISIBLE : View.GONE);

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onPhotoClick(photo);
            }
        });

        holder.itemView.setOnLongClickListener(v -> {
            if (listener != null) {
                listener.onPhotoLongClick(photo);
                return true;
            }
            return false;
        });
    }

    @Override
    public int getItemCount() {
        return photos.size();
    }

    static class PhotoViewHolder extends RecyclerView.ViewHolder {
        ImageView imagePhoto;
        View selectionOverlay;
        TextView textSelectedMark;

        PhotoViewHolder(@NonNull View itemView) {
            super(itemView);
            imagePhoto = itemView.findViewById(R.id.imagePhoto);
            selectionOverlay = itemView.findViewById(R.id.selectionOverlay);
            textSelectedMark = itemView.findViewById(R.id.textSelectedMark);
        }
    }
}

