package com.example.health.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import com.bumptech.glide.Glide;
import com.example.health.R;

import java.util.ArrayList;
import java.util.List;

/**
 * 图片预览页，使用 ViewPager2 实现多张图片的左右滑动预览。
 * 顶部显示当前位置指示器（如 "2 / 5"）和关闭按钮。
 */
public class ImagePreviewActivity extends AppCompatActivity {

    public static final String EXTRA_IMAGE_URLS = "extra_image_urls";
    public static final String EXTRA_START_POSITION = "extra_start_position";

    private ViewPager2 viewPager;
    private TextView tvIndicator;
    private ImageView btnClose;
    private List<String> imageUrls;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_image_preview);

        viewPager = findViewById(R.id.viewPager);
        tvIndicator = findViewById(R.id.tvIndicator);
        btnClose = findViewById(R.id.btnClose);

        imageUrls = getIntent().getStringArrayListExtra(EXTRA_IMAGE_URLS);
        int startPosition = getIntent().getIntExtra(EXTRA_START_POSITION, 0);

        if (imageUrls == null) {
            imageUrls = new ArrayList<>();
        }

        PreviewAdapter adapter = new PreviewAdapter(imageUrls);
        viewPager.setAdapter(adapter);
        viewPager.setCurrentItem(startPosition, false);

        updateIndicator(startPosition);

        viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                updateIndicator(position);
            }
        });

        btnClose.setOnClickListener(v -> finish());
    }

    private void updateIndicator(int position) {
        if (imageUrls.isEmpty()) {
            tvIndicator.setText("");
        } else {
            tvIndicator.setText((position + 1) + " / " + imageUrls.size());
        }
    }

    private static class PreviewAdapter extends RecyclerView.Adapter<PreviewAdapter.ViewHolder> {
        private final List<String> urls;

        PreviewAdapter(List<String> urls) {
            this.urls = urls;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_preview_image, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            String url = urls.get(position);
            Glide.with(holder.imageView)
                    .load(url)
                    .fitCenter()
                    .into(holder.imageView);
        }

        @Override
        public int getItemCount() {
            return urls.size();
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            ImageView imageView;

            ViewHolder(@NonNull View itemView) {
                super(itemView);
                imageView = itemView.findViewById(R.id.imageView);
            }
        }
    }
}
