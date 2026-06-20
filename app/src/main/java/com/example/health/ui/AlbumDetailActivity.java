package com.example.health.ui;

import android.app.AlertDialog;
import android.content.ContentValues;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import android.graphics.Color;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.bumptech.glide.Glide;
import com.example.health.R;
import com.example.health.data.model.Album;
import com.example.health.data.model.Photo;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import cn.leancloud.LCFile;
import cn.leancloud.LCQuery;
import cn.leancloud.LCUser;
import io.reactivex.Observer;
import io.reactivex.disposables.Disposable;

/**
 * 相册详情页，以网格展示相册中所有照片，支持多选删除、从相册添加和拍照添加。
 * 长按照片进入多选模式，底部栏显示已选数量并提供批量删除。
 */
public class AlbumDetailActivity extends AppCompatActivity implements PhotoGridAdapter.OnPhotoInteractionListener {

    public static final String EXTRA_ALBUM_ID = "extra_album_id";
    public static final String EXTRA_ALBUM_NAME = "extra_album_name";

    private SwipeRefreshLayout swipeRefreshLayout;
    private RecyclerView recyclerView;
    private TextView emptyView;
    private View multiSelectBar;
    private TextView textSelectedCount;
    private TextView buttonDeleteSelected;
    private View fabAddPhoto;

    private PhotoGridAdapter adapter;
    private final List<Photo> photos = new ArrayList<>();
    private final Set<String> selectedPhotoIds = new HashSet<>();
    private boolean inSelectionMode = false;
    private String albumId;

    private ActivityResultLauncher<PickVisualMediaRequest> pickImagesLauncher;
    private ActivityResultLauncher<Uri> takePictureLauncher;
    private Uri pendingCameraUri;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_album_detail);

        swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout);
        recyclerView = findViewById(R.id.recyclerViewPhotos);
        emptyView = findViewById(R.id.emptyView);
        multiSelectBar = findViewById(R.id.multiSelectBar);
        textSelectedCount = findViewById(R.id.textSelectedCount);
        buttonDeleteSelected = findViewById(R.id.buttonDeleteSelected);
        fabAddPhoto = findViewById(R.id.fabAddPhoto);
        swipeRefreshLayout.setColorSchemeColors(Color.TRANSPARENT, Color.TRANSPARENT, Color.TRANSPARENT);
        swipeRefreshLayout.setProgressBackgroundColorSchemeColor(Color.TRANSPARENT);
        swipeRefreshLayout.setEnabled(false);

        Toolbar toolbar = findViewById(R.id.toolbar);
        TextView textTitle = findViewById(R.id.textTitle);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowHomeEnabled(true);
            getSupportActionBar().setDisplayShowTitleEnabled(false);
        }
        toolbar.setNavigationOnClickListener(v -> onBackPressed());

        Intent intent = getIntent();
        albumId = intent.getStringExtra(EXTRA_ALBUM_ID);
        String albumName = intent.getStringExtra(EXTRA_ALBUM_NAME);
        if (albumName != null) {
            textTitle.setText(albumName);
        }

        adapter = new PhotoGridAdapter(photos, this);
        GridLayoutManager layoutManager = new GridLayoutManager(this, 3);
        recyclerView.setLayoutManager(layoutManager);
        recyclerView.setAdapter(adapter);

        buttonDeleteSelected.setOnClickListener(v -> confirmDeleteSelected());

        fabAddPhoto.setOnClickListener(v -> showAddPhotoOptions());

        pickImagesLauncher = registerForActivityResult(
                new ActivityResultContracts.PickMultipleVisualMedia(),
                uris -> {
                    if (uris != null && !uris.isEmpty()) {
                        addPhotosFromUris(uris);
                    }
                });

        takePictureLauncher = registerForActivityResult(new ActivityResultContracts.TakePicture(), success -> {
            if (success && pendingCameraUri != null) {
                List<Uri> list = new ArrayList<>();
                list.add(pendingCameraUri);
                addPhotosFromUris(list);
            }
            pendingCameraUri = null;
        });

        loadPhotos();
    }

    private void loadPhotos() {
        LCUser currentUser = LCUser.currentUser();
        if (currentUser == null || albumId == null) {
            Toast.makeText(this, "请先登录", Toast.LENGTH_SHORT).show();
            return;
        }

        LCQuery<Album> albumQuery = LCQuery.getQuery(Album.class);
        albumQuery.getInBackground(albumId).subscribe(new Observer<Album>() {
            @Override
            public void onSubscribe(Disposable d) {}

            @Override
            public void onNext(Album album) {
                LCQuery<Photo> query = LCQuery.getQuery(Photo.class);
                query.whereEqualTo(Photo.KEY_ALBUM, album);
                query.orderByDescending("createdAt");
                query.findInBackground().subscribe(new Observer<List<Photo>>() {
                    @Override
                    public void onSubscribe(Disposable d) {}

                    @Override
                    public void onNext(List<Photo> result) {
                        photos.clear();
                        if (result != null) {
                            photos.addAll(result);
                        }
                        adapter.setSelectionMode(false);
                        inSelectionMode = false;
                        selectedPhotoIds.clear();
                        updateSelectionBar();
                        adapter.notifyDataSetChanged();
                        updateEmptyView();
                    }

                    @Override
                    public void onError(Throwable e) {
                        Toast.makeText(AlbumDetailActivity.this, "加载照片失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        updateEmptyView();
                    }

                    @Override
                    public void onComplete() {}
                });
            }

            @Override
            public void onError(Throwable e) {
                Toast.makeText(AlbumDetailActivity.this, "相册不存在或已删除", Toast.LENGTH_SHORT).show();
                finish();
            }

            @Override
            public void onComplete() {}
        });
    }

    private void updateEmptyView() {
        if (photos.isEmpty()) {
            emptyView.setVisibility(View.VISIBLE);
            recyclerView.setVisibility(View.GONE);
        } else {
            emptyView.setVisibility(View.GONE);
            recyclerView.setVisibility(View.VISIBLE);
        }
    }

    private void enterSelectionMode(Photo photo) {
        inSelectionMode = true;
        adapter.setSelectionMode(true);
        togglePhotoSelection(photo);
        updateSelectionBar();
    }

    private void togglePhotoSelection(Photo photo) {
        String id = photo.getObjectId();
        if (id == null) {
            return;
        }
        if (selectedPhotoIds.contains(id)) {
            selectedPhotoIds.remove(id);
        } else {
            selectedPhotoIds.add(id);
        }
        updateSelectionBar();
        adapter.notifyDataSetChanged();
    }

    private void updateSelectionBar() {
        int count = selectedPhotoIds.size();
        if (inSelectionMode && count > 0) {
            multiSelectBar.setVisibility(View.VISIBLE);
            textSelectedCount.setText("已选择 " + count + " 张");
        } else {
            multiSelectBar.setVisibility(View.GONE);
            textSelectedCount.setText("已选择 0 张");
            if (count == 0) {
                inSelectionMode = false;
                adapter.setSelectionMode(false);
            }
        }
    }

    private void confirmDeleteSelected() {
        if (selectedPhotoIds.isEmpty()) {
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("删除照片")
                .setMessage("确定要删除选中的照片吗？")
                .setPositiveButton("删除", (dialog, which) -> deleteSelectedPhotos())
                .setNegativeButton("取消", null)
                .show();
    }

    private void deleteSelectedPhotos() {
        List<Photo> toDelete = new ArrayList<>();
        for (Photo photo : photos) {
            String id = photo.getObjectId();
            if (id != null && selectedPhotoIds.contains(id)) {
                toDelete.add(photo);
            }
        }
        photos.removeAll(toDelete);
        adapter.setSelectionMode(false);
        inSelectionMode = false;
        selectedPhotoIds.clear();
        adapter.notifyDataSetChanged();
        updateSelectionBar();
        updateEmptyView();
        for (Photo photo : toDelete) {
            photo.deleteInBackground().subscribe();
        }
    }

    private void showAddPhotoOptions() {
        String[] items = new String[]{"从相册选择", "拍摄照片"};
        new AlertDialog.Builder(this)
                .setItems(items, (dialog, which) -> {
                    if (which == 0) {
                        ActivityResultContracts.PickVisualMedia.VisualMediaType mediaType =
                                (ActivityResultContracts.PickVisualMedia.VisualMediaType)
                                        ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE;
                        PickVisualMediaRequest request = new PickVisualMediaRequest.Builder()
                                .setMediaType(mediaType)
                                .build();
                        pickImagesLauncher.launch(request);
                    } else {
                        Uri uri = createImageUri();
                        if (uri == null) {
                            Toast.makeText(this, "无法创建照片文件", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        pendingCameraUri = uri;
                        takePictureLauncher.launch(uri);
                    }
                })
                .show();
    }

    private Uri createImageUri() {
        String name = "album_photo_" + System.currentTimeMillis();
        ContentValues values = new ContentValues();
        values.put(MediaStore.Images.Media.DISPLAY_NAME, name + ".jpg");
        values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/HealthAlbum");
        }
        return getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
    }

    private void addPhotosFromUris(List<Uri> uris) {
        LCUser currentUser = LCUser.currentUser();
        if (currentUser == null || albumId == null) {
            Toast.makeText(this, "请先登录", Toast.LENGTH_SHORT).show();
            return;
        }
        LCQuery<Album> albumQuery = LCQuery.getQuery(Album.class);
        albumQuery.getInBackground(albumId).subscribe(new Observer<Album>() {
            @Override
            public void onSubscribe(Disposable d) {}

            @Override
            public void onNext(Album album) {
                for (Uri uri : uris) {
                    byte[] data = getBytesFromUri(uri);
                    if (data == null) {
                        Toast.makeText(AlbumDetailActivity.this, "读取图片失败", Toast.LENGTH_SHORT).show();
                        continue;
                    }
                    LCFile file = new LCFile("photo_" + System.currentTimeMillis() + ".jpg", data);
                    file.saveInBackground().subscribe(new Observer<LCFile>() {
                        @Override
                        public void onSubscribe(Disposable d) {}

                        @Override
                        public void onNext(LCFile lcFile) {
                            Photo photo = new Photo();
                            photo.setAlbum(album);
                            photo.setOwner(currentUser);
                            photo.setImage(lcFile);
                            photo.saveInBackground().subscribe();
                            photos.add(0, photo);
                            adapter.notifyItemInserted(0);
                            recyclerView.scrollToPosition(0);
                            updateEmptyView();
                        }

                        @Override
                        public void onError(Throwable e) {
                            Toast.makeText(AlbumDetailActivity.this, "添加照片失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        }

                        @Override
                        public void onComplete() {}
                    });
                }
            }

            @Override
            public void onError(Throwable e) {
                Toast.makeText(AlbumDetailActivity.this, "相册不存在或已删除", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onComplete() {}
        });
    }

    private byte[] getBytesFromUri(Uri uri) {
        try {
            InputStream inputStream = getContentResolver().openInputStream(uri);
            if (inputStream == null) {
                return null;
            }
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] data = new byte[4096];
            int n;
            while ((n = inputStream.read(data, 0, data.length)) != -1) {
                buffer.write(data, 0, n);
            }
            buffer.flush();
            return buffer.toByteArray();
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public void onPhotoClick(Photo photo) {
        if (inSelectionMode) {
            togglePhotoSelection(photo);
        } else {
            if (photo.getImage() != null && photo.getImage().getUrl() != null) {
                List<String> urls = new ArrayList<>();
                urls.add(photo.getImage().getUrl());
                Intent intent = new Intent(this, ImagePreviewActivity.class);
                intent.putStringArrayListExtra(ImagePreviewActivity.EXTRA_IMAGE_URLS, new ArrayList<>(urls));
                intent.putExtra(ImagePreviewActivity.EXTRA_START_POSITION, 0);
                startActivity(intent);
            }
        }
    }

    @Override
    public void onPhotoLongClick(Photo photo) {
        if (!inSelectionMode) {
            recyclerView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
            enterSelectionMode(photo);
        }
    }
}
