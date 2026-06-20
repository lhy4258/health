package com.example.health.ui;

import android.Manifest;
import android.app.ProgressDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.health.R;
import com.example.health.data.model.Moment;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import cn.leancloud.LCFile;
import cn.leancloud.LCObject;
import cn.leancloud.LCUser;
import io.reactivex.Observable;
import io.reactivex.Observer;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;

/**
 * 发布动态页面。支持输入文字内容和选择最多 9 张图片，图片先上传至 LeanCloud 后关联保存。
 */
public class PublishMomentActivity extends AppCompatActivity {

    private EditText editContent;
    private RecyclerView recyclerViewImages;
    private ImageAdapter adapter;
    private final List<Uri> selectedImages = new ArrayList<>();
    private MenuItem publishMenuItem;
    private ActivityResultLauncher<PickVisualMediaRequest> pickImagesLauncher;
    private ProgressDialog progressDialog;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_publish_moment);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowHomeEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> onBackPressed());

        editContent = findViewById(R.id.editContent);
        recyclerViewImages = findViewById(R.id.recyclerViewImages);

        setupRecyclerView();
        setupImagePicker();

        editContent.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                updatePublishButtonState();
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
    }

    private void setupRecyclerView() {
        recyclerViewImages.setLayoutManager(new GridLayoutManager(this, 3));
        adapter = new ImageAdapter();
        recyclerViewImages.setAdapter(adapter);
    }

    private void setupImagePicker() {
        pickImagesLauncher = registerForActivityResult(
                new ActivityResultContracts.PickMultipleVisualMedia(9),
                uris -> {
                    if (uris != null && !uris.isEmpty()) {
                        int currentSize = selectedImages.size();
                        int spaceLeft = 9 - currentSize;
                        if (spaceLeft > 0) {
                            int addCount = Math.min(spaceLeft, uris.size());
                            selectedImages.addAll(uris.subList(0, addCount));
                            adapter.notifyDataSetChanged();
                            updatePublishButtonState();
                        } else {
                            Toast.makeText(this, "最多只能选择9张图片", Toast.LENGTH_SHORT).show();
                        }
                    }
                });
    }

    private void updatePublishButtonState() {
        if (publishMenuItem != null) {
            boolean hasContent = !editContent.getText().toString().trim().isEmpty();
            boolean hasImages = !selectedImages.isEmpty();
            publishMenuItem.setEnabled(hasContent || hasImages);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_publish_moment, menu);
        publishMenuItem = menu.findItem(R.id.action_publish);
        updatePublishButtonState();
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.action_publish) {
            publishMoment();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void publishMoment() {
        String content = editContent.getText().toString().trim();
        if (content.isEmpty() && selectedImages.isEmpty()) {
            return;
        }

        progressDialog = new ProgressDialog(this);
        progressDialog.setMessage("正在发布...");
        progressDialog.setCancelable(false);
        progressDialog.show();

        if (selectedImages.isEmpty()) {
            saveMoment(content, null);
        } else {
            uploadImagesAndSave(content);
        }
    }

    private void uploadImagesAndSave(String content) {
        List<LCFile> uploadedFiles = new ArrayList<>();
        uploadNextImage(0, content, uploadedFiles);
    }

    private void uploadNextImage(int index, String content, List<LCFile> uploadedFiles) {
        if (index >= selectedImages.size()) {
            saveMoment(content, uploadedFiles);
            return;
        }

        Uri uri = selectedImages.get(index);
        byte[] data = getBytesFromUri(uri);
        if (data == null) {
            // Skip this image or fail? Let's skip.
            uploadNextImage(index + 1, content, uploadedFiles);
            return;
        }

        LCFile file = new LCFile("moment_img_" + System.currentTimeMillis() + ".jpg", data);
        file.saveInBackground().subscribe(new Observer<LCFile>() {
            @Override
            public void onSubscribe(Disposable d) {}

            @Override
            public void onNext(LCFile lcFile) {
                uploadedFiles.add(lcFile);
                uploadNextImage(index + 1, content, uploadedFiles);
            }

            @Override
            public void onError(Throwable e) {
                if (progressDialog != null && progressDialog.isShowing()) {
                    progressDialog.dismiss();
                }
                Toast.makeText(PublishMomentActivity.this, "图片上传失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
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

    private void saveMoment(String content, List<LCFile> images) {
        LCUser currentUser = LCUser.currentUser();
        if (currentUser == null) {
            if (progressDialog != null) progressDialog.dismiss();
            Toast.makeText(this, "请先登录", Toast.LENGTH_SHORT).show();
            return;
        }

        Moment moment = new Moment();
        moment.setContent(content);
        moment.setAuthor(currentUser);
        moment.setLikes(new ArrayList<>());
        moment.setCommentsCount(0);
        if (images != null) {
            moment.setImages(images);
        }

        moment.saveInBackground().subscribe(new Observer<LCObject>() {
            @Override
            public void onSubscribe(Disposable d) {}

            @Override
            public void onNext(LCObject lcObject) {
                if (progressDialog != null) progressDialog.dismiss();
                Toast.makeText(PublishMomentActivity.this, "发布成功", Toast.LENGTH_SHORT).show();
                Intent data = new Intent();
                data.putExtra("published_moment_id", moment.getObjectId());
                setResult(RESULT_OK, data);
                finish();
            }

            @Override
            public void onError(Throwable e) {
                if (progressDialog != null) progressDialog.dismiss();
                Toast.makeText(PublishMomentActivity.this, "发布失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onComplete() {}
        });
    }

    private class ImageAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        private static final int TYPE_IMAGE = 0;
        private static final int TYPE_ADD = 1;

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            if (viewType == TYPE_IMAGE) {
                View view = getLayoutInflater().inflate(R.layout.item_selected_image, parent, false);
                return new ImageViewHolder(view);
            } else {
                View view = getLayoutInflater().inflate(R.layout.item_selected_image, parent, false); // Reuse layout but customize
                return new AddViewHolder(view);
            }
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            if (getItemViewType(position) == TYPE_IMAGE) {
                ImageViewHolder vh = (ImageViewHolder) holder;
                Uri uri = selectedImages.get(position);
                Glide.with(PublishMomentActivity.this).load(uri).into(vh.imageView);
                vh.btnDelete.setOnClickListener(v -> {
                    selectedImages.remove(position);
                    notifyDataSetChanged();
                    updatePublishButtonState();
                });
            } else {
                AddViewHolder vh = (AddViewHolder) holder;
                vh.imageView.setImageResource(R.drawable.ic_add_photo);
                vh.imageView.setScaleType(ImageView.ScaleType.CENTER);
                vh.btnDelete.setVisibility(View.GONE);
                vh.itemView.setOnClickListener(v -> {
                    ActivityResultContracts.PickVisualMedia.VisualMediaType mediaType =
                            (ActivityResultContracts.PickVisualMedia.VisualMediaType)
                                    ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE;
                    PickVisualMediaRequest request = new PickVisualMediaRequest.Builder()
                            .setMediaType(mediaType)
                            .build();
                    pickImagesLauncher.launch(request);
                });
            }
        }

        @Override
        public int getItemCount() {
            if (selectedImages.size() < 9) {
                return selectedImages.size() + 1;
            } else {
                return selectedImages.size();
            }
        }

        @Override
        public int getItemViewType(int position) {
            if (position == selectedImages.size() && selectedImages.size() < 9) {
                return TYPE_ADD;
            }
            return TYPE_IMAGE;
        }

        class ImageViewHolder extends RecyclerView.ViewHolder {
            ImageView imageView;
            ImageView btnDelete;

            ImageViewHolder(View itemView) {
                super(itemView);
                imageView = itemView.findViewById(R.id.imageView);
                btnDelete = itemView.findViewById(R.id.btnDelete);
            }
        }

        class AddViewHolder extends RecyclerView.ViewHolder {
            ImageView imageView;
            ImageView btnDelete;

            AddViewHolder(View itemView) {
                super(itemView);
                imageView = itemView.findViewById(R.id.imageView);
                btnDelete = itemView.findViewById(R.id.btnDelete);
            }
        }
    }
}
