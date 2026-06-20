package com.example.health.ui;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import android.graphics.Color;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.example.health.R;
import com.example.health.data.model.Album;

import java.util.ArrayList;
import java.util.List;

import cn.leancloud.LCQuery;
import cn.leancloud.LCUser;
import io.reactivex.Observer;
import io.reactivex.disposables.Disposable;

/**
 * 相册列表页，展示用户创建的所有相册，支持创建、重命名和删除操作。
 * 点击相册进入相册详情页浏览和管理照片。
 */
public class AlbumActivity extends AppCompatActivity implements AlbumListAdapter.OnAlbumActionListener {

    private SwipeRefreshLayout swipeRefreshLayout;
    private RecyclerView recyclerView;
    private TextView emptyView;
    private AlbumListAdapter adapter;
    private final List<Album> albums = new ArrayList<>();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_album);

        swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout);
        recyclerView = findViewById(R.id.recyclerViewAlbums);
        emptyView = findViewById(R.id.emptyView);
        swipeRefreshLayout.setColorSchemeColors(Color.TRANSPARENT, Color.TRANSPARENT, Color.TRANSPARENT);
        swipeRefreshLayout.setProgressBackgroundColorSchemeColor(Color.TRANSPARENT);
        swipeRefreshLayout.setEnabled(false);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayShowTitleEnabled(false);
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> finish());

        adapter = new AlbumListAdapter(albums, this);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);

        View fabAddAlbum = findViewById(R.id.fabAddAlbum);
        if (fabAddAlbum != null) {
            fabAddAlbum.setOnClickListener(v -> showCreateAlbumDialog());
        }

        loadAlbums();
    }

    private void loadAlbums() {
        LCUser currentUser = LCUser.currentUser();
        if (currentUser == null) {
            Toast.makeText(this, "请先登录", Toast.LENGTH_SHORT).show();
            return;
        }

        LCQuery<Album> query = LCQuery.getQuery(Album.class);
        query.whereEqualTo(Album.KEY_OWNER, currentUser);
        query.orderByAscending(Album.KEY_NAME);
        query.findInBackground().subscribe(new Observer<List<Album>>() {
            @Override
            public void onSubscribe(Disposable d) {}

            @Override
            public void onNext(List<Album> result) {
                albums.clear();
                if (result != null && !result.isEmpty()) {
                    albums.addAll(result);
                }
                adapter.notifyDataSetChanged();
                updateEmptyView();
            }

            @Override
            public void onError(Throwable e) {
                Toast.makeText(AlbumActivity.this, "加载相册失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                updateEmptyView();
            }

            @Override
            public void onComplete() {}
        });
    }

    private void updateEmptyView() {
        if (albums.isEmpty()) {
            emptyView.setVisibility(View.VISIBLE);
            recyclerView.setVisibility(View.GONE);
        } else {
            emptyView.setVisibility(View.GONE);
            recyclerView.setVisibility(View.VISIBLE);
        }
    }

    private void showCreateAlbumDialog() {
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_album_rename, null, false);
        EditText editName = view.findViewById(R.id.editAlbumName);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("新建相册")
                .setView(view)
                .setPositiveButton("保存", null)
                .setNegativeButton("取消", (d, which) -> d.dismiss())
                .create();
        dialog.setOnShowListener(d -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                String name = editName.getText().toString().trim();
                if (!validateAlbumName(name)) {
                    return;
                }
                LCUser currentUser = LCUser.currentUser();
                if (currentUser == null) {
                    Toast.makeText(AlbumActivity.this, "请先登录", Toast.LENGTH_SHORT).show();
                    return;
                }
                Album album = new Album();
                album.setName(name);
                album.setOwner(currentUser);
                album.saveInBackground().subscribe();
                albums.add(album);
                adapter.notifyItemInserted(albums.size() - 1);
                updateEmptyView();
                dialog.dismiss();
            });
        });
        dialog.show();
    }

    private boolean validateAlbumName(String name) {
        if (TextUtils.isEmpty(name)) {
            Toast.makeText(this, "相册名称不能为空", Toast.LENGTH_SHORT).show();
            return false;
        }
        if (name.length() > 20) {
            Toast.makeText(this, "相册名称过长", Toast.LENGTH_SHORT).show();
            return false;
        }
        return true;
    }

    @Override
    public void onAlbumClick(Album album) {
        if (album.getObjectId() == null) {
            return;
        }
        Intent intent = new Intent(this, AlbumDetailActivity.class);
        intent.putExtra(AlbumDetailActivity.EXTRA_ALBUM_ID, album.getObjectId());
        intent.putExtra(AlbumDetailActivity.EXTRA_ALBUM_NAME, album.getName());
        startActivity(intent);
    }

    @Override
    public void onRenameClick(int position, Album album) {
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_album_rename, null, false);
        EditText editName = view.findViewById(R.id.editAlbumName);
        editName.setText(album.getName());
        editName.setSelection(editName.getText().length());
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("重命名相册")
                .setView(view)
                .setPositiveButton("保存", null)
                .setNegativeButton("取消", (d, which) -> d.dismiss())
                .create();
        dialog.setOnShowListener(d -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                String name = editName.getText().toString().trim();
                if (!validateAlbumName(name)) {
                    return;
                }
                album.setName(name);
                album.saveInBackground().subscribe();
                adapter.notifyItemChanged(position);
                dialog.dismiss();
            });
        });
        dialog.show();
    }

    @Override
    public void onDeleteClick(int position, Album album) {
        new AlertDialog.Builder(this)
                .setTitle("删除相册")
                .setMessage("确定要删除该相册及其中的照片吗？")
                .setPositiveButton("删除", (dialog, which) -> {
                    albums.remove(position);
                    adapter.notifyItemRemoved(position);
                    updateEmptyView();
                    album.deleteInBackground().subscribe();
                })
                .setNegativeButton("取消", null)
                .show();
    }
}
