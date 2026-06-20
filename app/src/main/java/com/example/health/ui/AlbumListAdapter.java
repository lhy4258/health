package com.example.health.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.health.R;
import com.example.health.data.model.Album;

import java.util.List;

/**
 * 相册列表 RecyclerView Adapter，展示相册名称，支持点击进入、重命名和删除操作。
 */
public class AlbumListAdapter extends RecyclerView.Adapter<AlbumListAdapter.AlbumViewHolder> {

    public interface OnAlbumActionListener {
        void onAlbumClick(Album album);
        void onRenameClick(int position, Album album);
        void onDeleteClick(int position, Album album);
    }

    private final List<Album> albums;
    private final OnAlbumActionListener listener;

    public AlbumListAdapter(List<Album> albums, OnAlbumActionListener listener) {
        this.albums = albums;
        this.listener = listener;
    }

    @NonNull
    @Override
    public AlbumViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_album, parent, false);
        return new AlbumViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull AlbumViewHolder holder, int position) {
        Album album = albums.get(position);
        holder.textAlbumName.setText(album.getName());
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onAlbumClick(album);
            }
        });
        holder.buttonRename.setOnClickListener(v -> {
            if (listener != null) {
                listener.onRenameClick(holder.getAdapterPosition(), album);
            }
        });
        holder.buttonDelete.setOnClickListener(v -> {
            if (listener != null) {
                listener.onDeleteClick(holder.getAdapterPosition(), album);
            }
        });
    }

    @Override
    public int getItemCount() {
        return albums.size();
    }

    static class AlbumViewHolder extends RecyclerView.ViewHolder {
        TextView textAlbumName;
        TextView buttonRename;
        TextView buttonDelete;

        AlbumViewHolder(@NonNull View itemView) {
            super(itemView);
            textAlbumName = itemView.findViewById(R.id.textAlbumName);
            buttonRename = itemView.findViewById(R.id.buttonRename);
            buttonDelete = itemView.findViewById(R.id.buttonDelete);
        }
    }
}

