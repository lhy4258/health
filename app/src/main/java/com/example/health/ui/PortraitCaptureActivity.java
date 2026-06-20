package com.example.health.ui;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.Nullable;

import com.example.health.R;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.NotFoundException;
import com.google.zxing.Result;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.RGBLuminanceSource;
import com.journeyapps.barcodescanner.CaptureActivity;

import java.io.InputStream;

/**
 * 自定义扫码页面，继承 ZXing CaptureActivity，增加"从相册选择二维码图片"的悬浮按钮。
 * 支持相机扫码和相册图片识别两种方式，结果通过 Activity Result 返回。
 */
public class PortraitCaptureActivity extends CaptureActivity {
    private static final int REQUEST_PICK_QR_IMAGE = 8801;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        FrameLayout root = findViewById(android.R.id.content);
        if (root == null) {
            return;
        }

        ImageView pickButton = new ImageView(this);
        pickButton.setImageResource(R.drawable.ic_add_photo);
        pickButton.setClickable(true);
        pickButton.setFocusable(true);
        pickButton.setBackgroundResource(R.drawable.circle_background_translucent);
        int padding = dpToPx(10);
        pickButton.setPadding(padding, padding, padding, padding);
        pickButton.setContentDescription("从相册选择二维码图片");

        int size = dpToPx(44);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(size, size);
        lp.gravity = Gravity.BOTTOM | Gravity.END;
        int margin = dpToPx(16);
        lp.rightMargin = margin;
        lp.bottomMargin = dpToPx(120);
        root.addView(pickButton, lp);

        pickButton.setOnClickListener(v -> openImagePicker());
    }

    private void openImagePicker() {
        Intent intent;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent = new Intent(MediaStore.ACTION_PICK_IMAGES);
            intent.setType("image/*");
        } else {
            intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("image/*");
        }
        startActivityForResult(intent, REQUEST_PICK_QR_IMAGE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_PICK_QR_IMAGE) {
            return;
        }
        if (resultCode != Activity.RESULT_OK || data == null) {
            return;
        }
        Uri uri = data.getData();
        if (uri == null) {
            return;
        }
        String content = decodeQrFromImageUri(uri);
        if (content == null || content.isEmpty()) {
            Toast.makeText(this, "未识别到二维码", Toast.LENGTH_SHORT).show();
            return;
        }

        Intent resultIntent = new Intent();
        resultIntent.putExtra("SCAN_RESULT", content);
        setResult(Activity.RESULT_OK, resultIntent);
        finish();
    }

    private String decodeQrFromImageUri(Uri uri) {
        InputStream inputStream = null;
        try {
            inputStream = getContentResolver().openInputStream(uri);
            if (inputStream == null) {
                return null;
            }
            Bitmap bitmap = BitmapFactory.decodeStream(inputStream);
            if (bitmap == null) {
                return null;
            }
            int width = bitmap.getWidth();
            int height = bitmap.getHeight();
            if (width <= 0 || height <= 0) {
                return null;
            }
            int[] pixels = new int[width * height];
            bitmap.getPixels(pixels, 0, width, 0, 0, width, height);
            RGBLuminanceSource source = new RGBLuminanceSource(width, height, pixels);
            BinaryBitmap binaryBitmap = new BinaryBitmap(new HybridBinarizer(source));
            Result result = new MultiFormatReader().decode(binaryBitmap);
            return result != null ? result.getText() : null;
        } catch (NotFoundException e) {
            return null;
        } catch (Exception e) {
            return null;
        } finally {
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (Exception ignored) {
                }
            }
        }
    }

    private int dpToPx(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density + 0.5f);
    }
}
