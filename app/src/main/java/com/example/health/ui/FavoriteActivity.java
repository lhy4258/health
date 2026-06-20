package com.example.health.ui;

import android.app.ProgressDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import android.graphics.Color;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.example.health.R;
import com.example.health.data.model.Favorite;
import com.example.health.data.model.Moment;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
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
 * 用户收藏页，展示自定义收藏内容列表，支持创建文字+图片形式的新收藏。
 */
public class FavoriteActivity extends AppCompatActivity implements FavoriteAdapter.OnFavoriteInteractionListener {

    private SwipeRefreshLayout swipeRefreshLayout;
    private RecyclerView recyclerView;
    private TextView emptyView;
    private FavoriteAdapter adapter;
    private final List<Favorite> favoriteList = new ArrayList<>();
    private ActivityResultLauncher<PickVisualMediaRequest> pickImagesLauncher;
    private final List<Uri> selectedImageUris = new ArrayList<>();
    private TextView dialogImageCountView;
    private ProgressDialog progressDialog;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_favorite);

        swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout);
        recyclerView = findViewById(R.id.recyclerView);
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

        pickImagesLauncher = registerForActivityResult(
                new ActivityResultContracts.PickMultipleVisualMedia(9),
                uris -> {
                    if (uris == null || uris.isEmpty()) {
                        return;
                    }
                    int currentSize = selectedImageUris.size();
                    int spaceLeft = 9 - currentSize;
                    if (spaceLeft <= 0) {
                        Toast.makeText(this, "最多只能选择9张图片", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    int addCount = Math.min(spaceLeft, uris.size());
                    selectedImageUris.addAll(uris.subList(0, addCount));
                    updateDialogImageCount();
                });

        adapter = new FavoriteAdapter();
        adapter.setOnFavoriteInteractionListener(this);
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        recyclerView.setLayoutManager(layoutManager);
        recyclerView.setAdapter(adapter);

        View fabAddFavorite = findViewById(R.id.fabAddFavorite);
        if (fabAddFavorite != null) {
            fabAddFavorite.setOnClickListener(v -> {
                showCreateCustomFavoriteDialog();
            });
        }

        loadFavorites();
    }

    private void showCreateCustomFavoriteDialog() {
        LCUser currentUser = LCUser.currentUser();
        if (currentUser == null) {
            Toast.makeText(this, "请先登录", Toast.LENGTH_SHORT).show();
            return;
        }

        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        int padding = (int) (getResources().getDisplayMetrics().density * 16);
        container.setPadding(padding, padding, padding, padding);

        EditText editText = new EditText(this);
        editText.setMinLines(3);
        editText.setGravity(Gravity.TOP | Gravity.START);
        editText.setHint("输入要收藏的内容");
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        container.addView(editText, textParams);

        Button buttonAddImages = new Button(this);
        buttonAddImages.setText("添加图片");
        LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        buttonParams.topMargin = (int) (getResources().getDisplayMetrics().density * 12);
        container.addView(buttonAddImages, buttonParams);

        TextView imageCountView = new TextView(this);
        imageCountView.setText("已选择 0 张图片");
        LinearLayout.LayoutParams countParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        countParams.topMargin = (int) (getResources().getDisplayMetrics().density * 8);
        container.addView(imageCountView, countParams);

        selectedImageUris.clear();
        dialogImageCountView = imageCountView;
        updateDialogImageCount();

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("添加收藏")
                .setView(container)
                .setPositiveButton("保存", null)
                .setNegativeButton("取消", (d, which) -> d.dismiss())
                .create();

        dialog.setOnShowListener(d -> {
            if (dialog.getButton(AlertDialog.BUTTON_POSITIVE) != null) {
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                    String text = editText.getText().toString().trim();
                    if (text.isEmpty() && selectedImageUris.isEmpty()) {
                        Toast.makeText(this, "请先输入内容或选择图片", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    saveCustomFavorite(text, dialog);
                });
            }
        });

        buttonAddImages.setOnClickListener(v -> openImagePicker());

        dialog.show();
    }

    private void openImagePicker() {
        ActivityResultContracts.PickVisualMedia.VisualMediaType mediaType =
                (ActivityResultContracts.PickVisualMedia.VisualMediaType)
                        ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE;
        PickVisualMediaRequest request = new PickVisualMediaRequest.Builder()
                .setMediaType(mediaType)
                .build();
        pickImagesLauncher.launch(request);
    }

    private void updateDialogImageCount() {
        if (dialogImageCountView != null) {
            dialogImageCountView.setText("已选择 " + selectedImageUris.size() + " 张图片");
        }
    }

    private void saveCustomFavorite(String text, AlertDialog dialog) {
        LCUser currentUser = LCUser.currentUser();
        if (currentUser == null) {
            Toast.makeText(this, "请先登录", Toast.LENGTH_SHORT).show();
            return;
        }

        progressDialog = new ProgressDialog(this);
        progressDialog.setMessage("正在保存收藏...");
        progressDialog.setCancelable(false);
        progressDialog.show();

        if (selectedImageUris.isEmpty()) {
            Favorite favorite = new Favorite();
            favorite.setUser(currentUser);
            favorite.setType(Favorite.TYPE_CUSTOM);
            favorite.setText(text);
            favorite.saveInBackground().subscribe(new Observer<LCObject>() {
                @Override
                public void onSubscribe(Disposable d) {}

                @Override
                public void onNext(LCObject lcObject) {
                    if (progressDialog != null && progressDialog.isShowing()) {
                        progressDialog.dismiss();
                    }
                    dialog.dismiss();
                    loadFavorites();
                }

                @Override
                public void onError(Throwable e) {
                    if (progressDialog != null && progressDialog.isShowing()) {
                        progressDialog.dismiss();
                    }
                    Toast.makeText(FavoriteActivity.this, "保存失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                }

                @Override
                public void onComplete() {}
            });
        } else {
            List<LCFile> files = new ArrayList<>();
            uploadNextImageForFavorite(0, text, currentUser, dialog, files);
        }
    }

    private void uploadNextImageForFavorite(int index, String text, LCUser user, AlertDialog dialog, List<LCFile> files) {
        if (index >= selectedImageUris.size()) {
            Favorite favorite = new Favorite();
            favorite.setUser(user);
            favorite.setType(Favorite.TYPE_CUSTOM);
            favorite.setText(text);
            favorite.setImages(files);
            favorite.saveInBackground().subscribe(new Observer<LCObject>() {
                @Override
                public void onSubscribe(Disposable d) {}

                @Override
                public void onNext(LCObject lcObject) {
                    if (progressDialog != null && progressDialog.isShowing()) {
                        progressDialog.dismiss();
                    }
                    dialog.dismiss();
                    loadFavorites();
                }

                @Override
                public void onError(Throwable e) {
                    if (progressDialog != null && progressDialog.isShowing()) {
                        progressDialog.dismiss();
                    }
                    Toast.makeText(FavoriteActivity.this, "保存失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                }

                @Override
                public void onComplete() {}
            });
            return;
        }

        Uri uri = selectedImageUris.get(index);
        byte[] data = getBytesFromUri(uri);
        if (data == null) {
            uploadNextImageForFavorite(index + 1, text, user, dialog, files);
            return;
        }

        LCFile file = new LCFile("favorite_img_" + System.currentTimeMillis() + ".jpg", data);
        file.saveInBackground().subscribe(new Observer<LCFile>() {
            @Override
            public void onSubscribe(Disposable d) {}

            @Override
            public void onNext(LCFile lcFile) {
                files.add(lcFile);
                uploadNextImageForFavorite(index + 1, text, user, dialog, files);
            }

            @Override
            public void onError(Throwable e) {
                if (progressDialog != null && progressDialog.isShowing()) {
                    progressDialog.dismiss();
                }
                Toast.makeText(FavoriteActivity.this, "图片上传失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onComplete() {}
        });
    }

    private byte[] getBytesFromUri(Uri uri) {
        try {
            InputStream inputStream = getContentResolver().openInputStream(uri);
            ByteArrayOutputStream byteBuffer = new ByteArrayOutputStream();
            int bufferSize = 1024;
            byte[] buffer = new byte[bufferSize];
            int len;
            while ((len = inputStream.read(buffer)) != -1) {
                byteBuffer.write(buffer, 0, len);
            }
            return byteBuffer.toByteArray();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private void createFavoriteForMoment(String momentId) {
        LCUser currentUser = LCUser.currentUser();
        if (currentUser == null) {
            loadFavorites();
            return;
        }

        LCQuery<Moment> momentQuery = LCQuery.getQuery(Moment.class);
        momentQuery.getInBackground(momentId).subscribe(new Observer<Moment>() {
            @Override
            public void onSubscribe(Disposable d) {}

            @Override
            public void onNext(Moment moment) {
                if (moment == null) {
                    loadFavorites();
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
                            loadFavorites();
                            return;
                        }

                        Favorite favorite = new Favorite();
                        favorite.setUser(currentUser);
                        favorite.setMoment(moment);
                        favorite.setType(Favorite.TYPE_MOMENT);
                        favorite.setText(moment.getContent());
                        favorite.setImages(moment.getImages());
                        favorite.saveInBackground().subscribe(new Observer<cn.leancloud.LCObject>() {
                            @Override
                            public void onSubscribe(Disposable d) {}

                            @Override
                            public void onNext(cn.leancloud.LCObject lcObject) {
                                loadFavorites();
                            }

                            @Override
                            public void onError(Throwable e) {
                                loadFavorites();
                            }

                            @Override
                            public void onComplete() {}
                        });
                    }

                    @Override
                    public void onError(Throwable e) {
                        if (e instanceof LCException && ((LCException) e).getCode() == 101) {
                            // 处理重复收藏错误
                                    Favorite favorite = new Favorite();
                            favorite.setUser(currentUser);
                            favorite.setMoment(moment);
                            favorite.saveInBackground().subscribe(new Observer<cn.leancloud.LCObject>() {
                                @Override
                                public void onSubscribe(Disposable d) {}

                                @Override
                                public void onNext(cn.leancloud.LCObject lcObject) {
                                    loadFavorites();
                                }

                                @Override
                                public void onError(Throwable saveError) {
                                    loadFavorites();
                                }

                                @Override
                                public void onComplete() {}
                            });
                            return;
                        }
                        loadFavorites();
                    }

                    @Override
                    public void onComplete() {}
                });
            }

            @Override
            public void onError(Throwable e) {
                loadFavorites();
            }

            @Override
            public void onComplete() {}
        });
    }

    private void loadFavorites() {
        LCUser currentUser = LCUser.currentUser();
        if (currentUser == null) {
            Toast.makeText(this, "请先登录", Toast.LENGTH_SHORT).show();
            return;
        }

        LCQuery<Favorite> query = LCQuery.getQuery(Favorite.class);
        query.whereEqualTo(Favorite.KEY_USER, currentUser);
        query.include(Favorite.KEY_MOMENT);
        query.include(Favorite.KEY_MOMENT + ".author");
        query.orderByDescending("createdAt");

        query.findInBackground().subscribe(new Observer<List<Favorite>>() {
            @Override
            public void onSubscribe(Disposable d) {}

            @Override
            public void onNext(List<Favorite> favorites) {
                favoriteList.clear();
                if (favorites != null) {
                    favoriteList.addAll(favorites);
                }
                adapter.setFavorites(new ArrayList<>(favoriteList));
                updateEmptyView();
            }

            @Override
            public void onError(Throwable e) {
                if (e instanceof LCException && ((LCException) e).getCode() == 101) {
                    favoriteList.clear();
                    adapter.setFavorites(new ArrayList<>(favoriteList));
                    updateEmptyView();
                    return;
                }
                Toast.makeText(FavoriteActivity.this, "加载收藏失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                updateEmptyView();
            }

            @Override
            public void onComplete() {}
        });
    }

    private void updateEmptyView() {
        if (favoriteList.isEmpty()) {
            emptyView.setVisibility(View.VISIBLE);
            recyclerView.setVisibility(View.GONE);
        } else {
            emptyView.setVisibility(View.GONE);
            recyclerView.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public void onItemClick(Favorite favorite, int position) {
        String type = favorite.getType();
        if (Favorite.TYPE_MOMENT.equals(type)) {
            String momentId = favorite.getSourceMomentId();
            if (momentId == null) {
                Moment moment = favorite.getMoment();
                if (moment != null) {
                    momentId = moment.getObjectId();
                }
            }
            if (momentId != null) {
                Intent intent = new Intent(this, MomentDetailActivity.class);
                intent.putExtra(MomentDetailActivity.EXTRA_MOMENT_ID, momentId);
                intent.putExtra(MomentDetailActivity.EXTRA_FROM_FAVORITE, true);
                startActivity(intent);
            }
        } else {
            String text = favorite.getText();
            if (text == null || text.isEmpty()) {
                Toast.makeText(this, "暂无可预览的内容", Toast.LENGTH_SHORT).show();
                return;
            }
            new AlertDialog.Builder(this)
                    .setTitle("收藏内容")
                    .setMessage(text)
                    .setPositiveButton("关闭", null)
                    .show();
        }
    }

    @Override
    public void onItemLongClick(Favorite favorite, int position) {
        new AlertDialog.Builder(this)
                .setTitle("删除收藏")
                .setMessage("确定要删除这条收藏吗？")
                .setPositiveButton("删除", (dialog, which) -> {
                    progressDialog = new ProgressDialog(this);
                    progressDialog.setMessage("正在删除收藏...");
                    progressDialog.setCancelable(false);
                    progressDialog.show();

                    favorite.deleteInBackground().subscribe(new Observer<cn.leancloud.types.LCNull>() {
                        @Override
                        public void onSubscribe(Disposable d) {}

                        @Override
                        public void onNext(cn.leancloud.types.LCNull lcNull) {
                            if (progressDialog != null && progressDialog.isShowing()) {
                                progressDialog.dismiss();
                            }
                            if (position >= 0 && position < favoriteList.size()) {
                                favoriteList.remove(position);
                                adapter.setFavorites(new ArrayList<>(favoriteList));
                                updateEmptyView();
                            } else {
                                loadFavorites();
                            }
                            Toast.makeText(FavoriteActivity.this, "已删除收藏", Toast.LENGTH_SHORT).show();
                        }

                        @Override
                        public void onError(Throwable e) {
                            if (progressDialog != null && progressDialog.isShowing()) {
                                progressDialog.dismiss();
                            }
                            Toast.makeText(FavoriteActivity.this, "删除失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        }

                        @Override
                        public void onComplete() {}
                    });
                })
                .setNegativeButton("取消", null)
                .show();
    }
}
