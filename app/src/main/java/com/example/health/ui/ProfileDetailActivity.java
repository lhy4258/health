package com.example.health.ui;

import android.app.Activity;
import android.app.DatePickerDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.Layout;
import android.text.style.AlignmentSpan;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.ViewGroup;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.NumberPicker;
import android.widget.ProgressBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.example.health.R;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.Calendar;

import cn.leancloud.LCFile;
import cn.leancloud.LCObject;
import cn.leancloud.LCUser;
import io.reactivex.Observer;
import io.reactivex.disposables.Disposable;

/**
 * 个人资料编辑页，支持修改头像、用户名、性别、地区、生日、社交账号、联系方式、个性签名。
 * 集成二维码生成功能，可选择拍照或从相册上传头像。
 */
public class ProfileDetailActivity extends AppCompatActivity {

    private static final String KEY_SIGNATURE = "signature";
    private static final String KEY_AVATAR = "avatar";
    private static final String KEY_GENDER = "gender";
    private static final String KEY_REGION = "region";
    private static final String KEY_BIRTHDAY = "birthday";
    private static final String KEY_SOCIAL_ACCOUNT = "socialAccount";

    private static final int REQUEST_PICK_AVATAR = 3001;
    private static final int REQUEST_CAPTURE_AVATAR = 3002;

    private ImageView avatarImageView;
    private TextView usernameValueTextView;
    private TextView genderValueTextView;
    private TextView regionValueTextView;
    private TextView birthdayValueTextView;
    private TextView socialAccountValueTextView;
    private TextView emailValueTextView;
    private TextView signatureValueTextView;
    private View qrRow;
    private ProgressBar loadingIndicator;
    private Switch privacyEmailSwitch;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile_detail);

        initViews();
        setupListeners();
        loadUserInfo();
    }

    private void initViews() {
        avatarImageView = findViewById(R.id.avatarValueImageView);
        usernameValueTextView = findViewById(R.id.usernameValueTextView);
        genderValueTextView = findViewById(R.id.genderValueTextView);
        regionValueTextView = findViewById(R.id.regionValueTextView);
        birthdayValueTextView = findViewById(R.id.birthdayValueTextView);
        socialAccountValueTextView = findViewById(R.id.socialAccountValueTextView);
        emailValueTextView = findViewById(R.id.emailValueTextView);
        signatureValueTextView = findViewById(R.id.signatureValueTextView);
        qrRow = findViewById(R.id.rowQrCode);
        loadingIndicator = findViewById(R.id.loadingIndicator);
        privacyEmailSwitch = findViewById(R.id.switchPrivacyEmail);
    }

    private void setupListeners() {
        View avatarRow = findViewById(R.id.rowAvatar);
        View usernameRow = findViewById(R.id.rowUsername);
        View genderRow = findViewById(R.id.rowGender);
        View regionRow = findViewById(R.id.rowRegion);
        View birthdayRow = findViewById(R.id.rowBirthday);
        View socialRow = findViewById(R.id.rowSocialAccount);
        View emailRow = findViewById(R.id.rowEmail);
        View signatureRow = findViewById(R.id.rowSignature);
        View backButton = findViewById(R.id.backButton);

        backButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                handleClickFeedback(v);
                finish();
            }
        });

        avatarRow.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                handleClickFeedback(v);
                openAvatarSourceChooser();
            }
        });

        avatarImageView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                handleClickFeedback(v);
                showAvatarPreviewDialog();
            }
        });

        usernameRow.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                handleClickFeedback(v);
                SpannableString title = new SpannableString("用户名");
                title.setSpan(new AlignmentSpan.Standard(Layout.Alignment.ALIGN_CENTER),
                        0, title.length(), Spanned.SPAN_INCLUSIVE_INCLUSIVE);
                showEditTextDialog(title, usernameValueTextView.getText().toString(), 20, new OnValueChangedListener() {
                    @Override
                        public void onValueChanged(String value) {
                            usernameValueTextView.setText(value);
                            saveUsername(value);
                        }
                });
            }
        });

        genderRow.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                handleClickFeedback(v);
                showGenderDialog();
            }
        });

        regionRow.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                handleClickFeedback(v);
                showRegionPickerDialog();
            }
        });

        birthdayRow.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                handleClickFeedback(v);
                showBirthdayPicker();
            }
        });

        socialRow.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                handleClickFeedback(v);
                SpannableString title = new SpannableString("社交账号");
                title.setSpan(new AlignmentSpan.Standard(Layout.Alignment.ALIGN_CENTER),
                        0, title.length(), Spanned.SPAN_INCLUSIVE_INCLUSIVE);
                showEditTextDialog(title, socialAccountValueTextView.getText().toString(), 40, new OnValueChangedListener() {
                    @Override
                    public void onValueChanged(String value) {
                        socialAccountValueTextView.setText(value);
                        saveUserField(KEY_SOCIAL_ACCOUNT, value);
                    }
                });
            }
        });

        emailRow.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                handleClickFeedback(v);
                SpannableString title = new SpannableString("联系方式");
                title.setSpan(new AlignmentSpan.Standard(Layout.Alignment.ALIGN_CENTER),
                        0, title.length(), Spanned.SPAN_INCLUSIVE_INCLUSIVE);
                showEditTextDialog(title, emailValueTextView.getText().toString(), 50, new OnValueChangedListener() {
                    @Override
                    public void onValueChanged(String value) {
                        emailValueTextView.setText(value);
                        saveEmail(value);
                    }
                });
            }
        });

        signatureRow.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                handleClickFeedback(v);
                SpannableString title = new SpannableString("个性签名");
                title.setSpan(new AlignmentSpan.Standard(Layout.Alignment.ALIGN_CENTER),
                        0, title.length(), Spanned.SPAN_INCLUSIVE_INCLUSIVE);
                showEditTextDialog(title, signatureValueTextView.getText().toString(), 60, new OnValueChangedListener() {
                    @Override
                    public void onValueChanged(String value) {
                        signatureValueTextView.setText(value);
                        saveUserField(KEY_SIGNATURE, value);
                    }
                });
            }
        });

        qrRow.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                handleClickFeedback(v);
                showQRCodeDialog();
            }
        });

        privacyEmailSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            Toast.makeText(ProfileDetailActivity.this, isChecked ? "已允许他人查看联系方式" : "已隐藏联系方式", Toast.LENGTH_SHORT).show();
        });
    }

    private void loadUserInfo() {
        loadingIndicator.setVisibility(View.VISIBLE);

        LCUser currentUser = LCUser.currentUser();
        if (currentUser == null) {
            loadingIndicator.setVisibility(View.GONE);
            Toast.makeText(this, "请先登录后查看个人资料", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        String username = currentUser.getUsername();
        if (TextUtils.isEmpty(username)) {
            username = "用户";
        }
        usernameValueTextView.setText(username);

        String gender = currentUser.getString(KEY_GENDER);
        if (TextUtils.isEmpty(gender)) {
            gender = "保密";
        }
        genderValueTextView.setText(gender);

        String region = currentUser.getString(KEY_REGION);
        if (TextUtils.isEmpty(region)) {
            region = "未设置";
        }
        regionValueTextView.setText(region);

        String birthday = currentUser.getString(KEY_BIRTHDAY);
        if (TextUtils.isEmpty(birthday)) {
            birthday = "未设置";
        }
        birthdayValueTextView.setText(birthday);

        String socialAccount = currentUser.getString(KEY_SOCIAL_ACCOUNT);
        if (TextUtils.isEmpty(socialAccount)) {
            socialAccount = "未绑定";
        }
        socialAccountValueTextView.setText(socialAccount);

        String email = currentUser.getEmail();
        if (TextUtils.isEmpty(email)) {
            email = "未设置";
        }
        emailValueTextView.setText(email);

        String signature = currentUser.getString(KEY_SIGNATURE);
        if (TextUtils.isEmpty(signature)) {
            signature = "这个人很懒，还没有写签名";
        }
        signatureValueTextView.setText(signature);

        LCFile avatar = currentUser.getLCFile(KEY_AVATAR);
        if (avatar != null && avatar.getUrl() != null) {
            loadImageFromUrl(avatar.getUrl());
        }

        loadingIndicator.setVisibility(View.GONE);
    }

    private void saveUserField(String key, String value) {
        LCUser currentUser = LCUser.currentUser();
        if (currentUser == null) {
            Toast.makeText(this, "未登录", Toast.LENGTH_SHORT).show();
            return;
        }
        currentUser.put(key, value);
        currentUser.saveInBackground().subscribe(new Observer<LCObject>() {
            @Override
            public void onSubscribe(Disposable d) {
            }

            @Override
            public void onNext(LCObject lcObject) {
            }

            @Override
            public void onError(Throwable e) {
                Toast.makeText(ProfileDetailActivity.this, "保存失败", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onComplete() {
                Toast.makeText(ProfileDetailActivity.this, "已保存", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void saveUsername(String username) {
        LCUser currentUser = LCUser.currentUser();
        if (currentUser == null) {
            Toast.makeText(this, "未登录", Toast.LENGTH_SHORT).show();
            return;
        }
        currentUser.setUsername(username);
        currentUser.saveInBackground().subscribe(new Observer<LCObject>() {
            @Override
            public void onSubscribe(Disposable d) {
            }

            @Override
            public void onNext(LCObject lcObject) {
            }

            @Override
            public void onError(Throwable e) {
                Toast.makeText(ProfileDetailActivity.this, "保存失败", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onComplete() {
                Toast.makeText(ProfileDetailActivity.this, "已保存", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void saveEmail(String email) {
        LCUser currentUser = LCUser.currentUser();
        if (currentUser == null) {
            Toast.makeText(this, "未登录", Toast.LENGTH_SHORT).show();
            return;
        }
        currentUser.setEmail(email);
        currentUser.saveInBackground().subscribe(new Observer<LCObject>() {
            @Override
            public void onSubscribe(Disposable d) {
            }

            @Override
            public void onNext(LCObject lcObject) {
            }

            @Override
            public void onError(Throwable e) {
                Toast.makeText(ProfileDetailActivity.this, "保存失败", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onComplete() {
                Toast.makeText(ProfileDetailActivity.this, "已保存", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showEditTextDialog(CharSequence title, String initialValue, int maxLength, OnValueChangedListener listener) {
        final EditText editText = new EditText(this);
        editText.setText(initialValue);
        if (!TextUtils.isEmpty(initialValue)) {
            editText.setSelection(initialValue.length());
        }
        editText.setMaxLines(1);
        editText.setGravity(android.view.Gravity.CENTER);
        int padding = (int) (getResources().getDisplayMetrics().density * 14);
        editText.setPadding(padding, padding, padding, padding);
        editText.setBackgroundResource(R.drawable.bg_dialog_round_white);
        editText.setAlpha(0.8f);
        editText.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                float targetAlpha = hasFocus ? 1f : 0.8f;
                v.animate().alpha(targetAlpha).setDuration(220).start();
            }
        });

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(title)
                .setView(editText)
                .setPositiveButton("保存", (dialogInterface, which) -> {
                    String value = editText.getText().toString().trim();
                    if (value.length() > maxLength) {
                        value = value.substring(0, maxLength);
                    }
                    listener.onValueChanged(value);
                })
                .setNegativeButton("取消", null)
                .create();
        dialog.setOnShowListener(d -> {
            if (dialog.getWindow() != null) {
                dialog.getWindow().setBackgroundDrawableResource(R.drawable.bg_dialog_round_white);
            }
            editText.requestFocus();
            editText.setScaleX(0.96f);
            editText.setScaleY(0.96f);
            editText.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(220)
                    .start();
        });
        dialog.show();
    }

    private void showGenderDialog() {
        final String[] genders = new String[]{"男", "女", "保密"};
        SpannableString title = new SpannableString("选择性别");
        title.setSpan(new AlignmentSpan.Standard(Layout.Alignment.ALIGN_CENTER),
                0, title.length(), Spanned.SPAN_INCLUSIVE_INCLUSIVE);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(title)
                .setItems(genders, (d, which) -> {
                    String selected = genders[which];
                    genderValueTextView.setText(selected);
                    saveUserField(KEY_GENDER, selected);
                })
                .create();
        dialog.setOnShowListener(d -> {
            if (dialog.getWindow() != null) {
                dialog.getWindow().setBackgroundDrawableResource(R.drawable.bg_dialog_round_white);
            }
        });
        dialog.show();
    }

    private void showBirthdayPicker() {
        Calendar calendar = Calendar.getInstance();
        DatePickerDialog dialog = new DatePickerDialog(this, new DatePickerDialog.OnDateSetListener() {
            @Override
            public void onDateSet(DatePicker view, int year, int month, int dayOfMonth) {
                String value = year + "-" + (month + 1) + "-" + dayOfMonth;
                birthdayValueTextView.setText(value);
                saveUserField(KEY_BIRTHDAY, value);
            }
        }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH));
        dialog.show();
    }

    private void showAvatarPreviewDialog() {
        if (avatarImageView.getDrawable() == null) {
            Toast.makeText(this, "还没有头像", Toast.LENGTH_SHORT).show();
            return;
        }
        ImageView preview = new ImageView(this);
        preview.setImageDrawable(avatarImageView.getDrawable());
        preview.setAdjustViewBounds(true);
        preview.setScaleType(ImageView.ScaleType.FIT_CENTER);

        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        int padding = (int) (getResources().getDisplayMetrics().density * 16);
        container.setPadding(padding, padding, padding, padding);
        container.setBackgroundResource(R.drawable.bg_dialog_round_white);
        container.addView(preview, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(container)
                .setNegativeButton("关闭", null)
                .create();
        dialog.setOnShowListener(d -> {
            if (dialog.getWindow() != null) {
                dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            }
            preview.setAlpha(0f);
            preview.animate()
                    .alpha(1f)
                    .setDuration(220)
                    .start();
        });
        dialog.show();
    }

    private void openAvatarSourceChooser() {
        String[] items = new String[]{"从相册选择", "拍摄照片"};
        new AlertDialog.Builder(this)
                .setItems(items, (dialog, which) -> {
                    if (which == 0) {
                        Intent intent;
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                            intent = new Intent(android.provider.MediaStore.ACTION_PICK_IMAGES);
                            intent.setType("image/*");
                        } else {
                            intent = new Intent(Intent.ACTION_GET_CONTENT);
                            intent.setType("image/*");
                        }
                        startActivityForResult(intent, REQUEST_PICK_AVATAR);
                    } else {
                        Intent intent = new Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE);
                        if (intent.resolveActivity(getPackageManager()) != null) {
                            startActivityForResult(intent, REQUEST_CAPTURE_AVATAR);
                        } else {
                            Toast.makeText(this, "无法启动相机", Toast.LENGTH_SHORT).show();
                        }
                    }
                })
                .show();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != Activity.RESULT_OK || data == null) {
            return;
        }
        Bitmap bitmap = null;
        if (requestCode == REQUEST_PICK_AVATAR) {
            Uri uri = data.getData();
            if (uri == null) {
                return;
            }
            bitmap = readBitmapFromUri(uri);
        } else if (requestCode == REQUEST_CAPTURE_AVATAR) {
            android.os.Bundle extras = data.getExtras();
            if (extras != null) {
                Object obj = extras.get("data");
                if (obj instanceof Bitmap) {
                    bitmap = (Bitmap) obj;
                }
            }
        }
        if (bitmap == null) {
            return;
        }
        Bitmap square = cropCenterSquare(bitmap);
        if (square == null) {
            square = bitmap;
        }
        avatarImageView.setImageBitmap(square);
        byte[] bytes = compressBitmap(square);
        if (bytes == null || bytes.length == 0) {
            return;
        }
        uploadAvatar(bytes);
    }

    private Bitmap readBitmapFromUri(Uri uri) {
        try {
            InputStream is = getContentResolver().openInputStream(uri);
            if (is == null) {
                return null;
            }
            Bitmap bitmap = BitmapFactory.decodeStream(is);
            is.close();
            return bitmap;
        } catch (Exception e) {
            return null;
        }
    }

    private Bitmap cropCenterSquare(Bitmap src) {
        if (src == null) {
            return null;
        }
        int width = src.getWidth();
        int height = src.getHeight();
        int size = Math.min(width, height);
        int x = (width - size) / 2;
        int y = (height - size) / 2;
        if (x < 0 || y < 0 || size <= 0) {
            return src;
        }
        try {
            return Bitmap.createBitmap(src, x, y, size, size);
        } catch (IllegalArgumentException e) {
            return src;
        }
    }

    private byte[] compressBitmap(Bitmap bitmap) {
        if (bitmap == null) {
            return null;
        }
        int quality = 90;
        byte[] data;
        do {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, bos);
            data = bos.toByteArray();
            quality -= 10;
        } while (data.length > 5 * 1024 * 1024 && quality >= 40);
        return data;
    }

    private void uploadAvatar(byte[] data) {
        LCUser currentUser = LCUser.currentUser();
        if (currentUser == null) {
            return;
        }
        String fileName = KEY_AVATAR + "_" + System.currentTimeMillis() + ".jpg";
        LCFile file = new LCFile(fileName, data);
        file.saveInBackground().subscribe(new Observer<LCFile>() {
            @Override
            public void onSubscribe(Disposable d) {
            }

            @Override
            public void onNext(LCFile lcFile) {
                currentUser.put(KEY_AVATAR, lcFile);
                currentUser.saveInBackground().subscribe(new Observer<LCObject>() {
                    @Override
                    public void onSubscribe(Disposable d) {
                    }

                    @Override
                    public void onNext(LCObject lcObject) {
                        Bitmap bitmap = BitmapFactory.decodeByteArray(data, 0, data.length);
                        if (bitmap != null) {
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    avatarImageView.setImageBitmap(bitmap);
                                }
                            });
                        }
                    }

                    @Override
                    public void onError(Throwable e) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                Toast.makeText(ProfileDetailActivity.this, "头像保存失败", Toast.LENGTH_SHORT).show();
                            }
                        });
                    }

                    @Override
                    public void onComplete() {
                    }
                });
            }

            @Override
            public void onError(Throwable e) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(ProfileDetailActivity.this, "图片上传失败", Toast.LENGTH_SHORT).show();
                    }
                });
            }

            @Override
            public void onComplete() {
            }
        });
    }

    private void loadImageFromUrl(String url) {
        if (TextUtils.isEmpty(url)) {
            return;
        }
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    InputStream is = new java.net.URL(url).openStream();
                    Bitmap bitmap = BitmapFactory.decodeStream(is);
                    is.close();
                    if (bitmap != null) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                avatarImageView.setImageBitmap(bitmap);
                            }
                        });
                    }
                } catch (Exception ignored) {
                }
            }
        }).start();
    }

    private void showRegionPickerDialog() {
        String[] countries = new String[]{"中国", "美国"};
        String[][] cities = new String[][]{
                new String[]{"北京", "上海", "广州"},
                new String[]{"纽约", "洛杉矶", "旧金山"}
        };
        String[][][] districts = new String[][][]{
                new String[][]{
                        new String[]{"朝阳区", "海淀区"},
                        new String[]{"浦东新区", "徐汇区"},
                        new String[]{"天河区", "越秀区"}
                },
                new String[][]{
                        new String[]{"曼哈顿", "布鲁克林"},
                        new String[]{"市中心", "好莱坞"},
                        new String[]{"金融区", "渔人码头"}
                }
        };

        NumberPicker countryPicker = new NumberPicker(this);
        NumberPicker cityPicker = new NumberPicker(this);
        NumberPicker districtPicker = new NumberPicker(this);

        countryPicker.setMinValue(0);
        countryPicker.setMaxValue(countries.length - 1);
        countryPicker.setDisplayedValues(countries);

        cityPicker.setMinValue(0);
        cityPicker.setMaxValue(cities[0].length - 1);
        cityPicker.setDisplayedValues(cities[0]);

        districtPicker.setMinValue(0);
        districtPicker.setMaxValue(districts[0][0].length - 1);
        districtPicker.setDisplayedValues(districts[0][0]);

        countryPicker.setOnValueChangedListener(new NumberPicker.OnValueChangeListener() {
            @Override
            public void onValueChange(NumberPicker picker, int oldVal, int newVal) {
                String[] cityArray = cities[newVal];
                cityPicker.setDisplayedValues(null);
                cityPicker.setMinValue(0);
                cityPicker.setMaxValue(cityArray.length - 1);
                cityPicker.setDisplayedValues(cityArray);

                String[] districtArray = districts[newVal][0];
                districtPicker.setDisplayedValues(null);
                districtPicker.setMinValue(0);
                districtPicker.setMaxValue(districtArray.length - 1);
                districtPicker.setDisplayedValues(districtArray);
            }
        });

        cityPicker.setOnValueChangedListener(new NumberPicker.OnValueChangeListener() {
            @Override
            public void onValueChange(NumberPicker picker, int oldVal, int newVal) {
                int countryIndex = countryPicker.getValue();
                String[] districtArray = districts[countryIndex][newVal];
                districtPicker.setDisplayedValues(null);
                districtPicker.setMinValue(0);
                districtPicker.setMaxValue(districtArray.length - 1);
                districtPicker.setDisplayedValues(districtArray);
            }
        });

        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.HORIZONTAL);
        int padding = (int) (getResources().getDisplayMetrics().density * 16);
        container.setPadding(padding, padding, padding, padding);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        container.addView(countryPicker, params);
        container.addView(cityPicker, params);
        container.addView(districtPicker, params);

        SpannableString title = new SpannableString("选择地区");
        title.setSpan(new AlignmentSpan.Standard(Layout.Alignment.ALIGN_CENTER),
                0, title.length(), Spanned.SPAN_INCLUSIVE_INCLUSIVE);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(title)
                .setView(container)
                .setPositiveButton("确定", (d, which) -> {
                    int countryIndex = countryPicker.getValue();
                    int cityIndex = cityPicker.getValue();
                    int districtIndex = districtPicker.getValue();
                    String value = countries[countryIndex] + " " + cities[countryIndex][cityIndex] + " " + districts[countryIndex][cityIndex][districtIndex];
                    regionValueTextView.setText(value);
                    saveUserField(KEY_REGION, value);
                })
                .setNegativeButton("取消", null)
                .create();
        dialog.setOnShowListener(d -> {
            if (dialog.getWindow() != null) {
                dialog.getWindow().setBackgroundDrawableResource(R.drawable.bg_dialog_round_white);
            }
            container.setScaleX(0.96f);
            container.setScaleY(0.96f);
            container.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(220)
                    .start();
        });
        dialog.show();
    }

    private void showQRCodeDialog() {
        LCUser currentUser = LCUser.currentUser();
        if (currentUser == null) {
            Toast.makeText(this, "请先登录", Toast.LENGTH_SHORT).show();
            return;
        }

        String qrContent = "health_app_user:" + currentUser.getObjectId();
        int size = (int) (getResources().getDisplayMetrics().density * 200);
        Bitmap qrBitmap = createQRCodeBitmap(qrContent, size, size);

        if (qrBitmap == null) {
            Toast.makeText(this, "生成二维码失败", Toast.LENGTH_SHORT).show();
            return;
        }

        ImageView qrImageView = new ImageView(this);
        qrImageView.setImageBitmap(qrBitmap);
        qrImageView.setScaleType(ImageView.ScaleType.FIT_CENTER);

        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        int padding = (int) (getResources().getDisplayMetrics().density * 24);
        container.setPadding(padding, padding, padding, padding);
        container.setGravity(android.view.Gravity.CENTER);

        TextView hintText = new TextView(this);
        hintText.setText("扫一扫加我好友");
        hintText.setTextSize(14);
        hintText.setTextColor(Color.parseColor("#999999"));
        hintText.setGravity(android.view.Gravity.CENTER);
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        textParams.topMargin = (int) (getResources().getDisplayMetrics().density * 16);

        container.addView(qrImageView, new LinearLayout.LayoutParams(size, size));
        container.addView(hintText, textParams);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(container)
                .setNegativeButton("关闭", null)
                .create();

        dialog.setOnShowListener(d -> {
            if (dialog.getWindow() != null) {
                dialog.getWindow().setBackgroundDrawableResource(R.drawable.bg_dialog_round_white);
            }
            container.setScaleX(0.8f);
            container.setScaleY(0.8f);
            container.setAlpha(0f);
            container.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .alpha(1f)
                    .setDuration(250)
                    .setInterpolator(new android.view.animation.OvershootInterpolator())
                    .start();
        });
        dialog.show();
    }

    private Bitmap createQRCodeBitmap(String content, int width, int height) {
        try {
            BitMatrix bitMatrix = new MultiFormatWriter().encode(content, BarcodeFormat.QR_CODE, width, height);
            int w = bitMatrix.getWidth();
            int h = bitMatrix.getHeight();
            int[] pixels = new int[w * h];
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    if (bitMatrix.get(x, y)) {
                        pixels[y * w + x] = 0xFF000000;
                    } else {
                        pixels[y * w + x] = 0x00FFFFFF;
                    }
                }
            }
            Bitmap bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            bitmap.setPixels(pixels, 0, w, 0, 0, w, h);
            return bitmap;
        } catch (WriterException e) {
            e.printStackTrace();
            return null;
        }
    }

    private void handleClickFeedback(View view) {
        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
        view.animate()
                .scaleX(0.97f)
                .scaleY(0.97f)
                .setDuration(100)
                .withEndAction(new Runnable() {
                    @Override
                    public void run() {
                        view.animate()
                                .scaleX(1f)
                                .scaleY(1f)
                                .setDuration(120)
                                .start();
                    }
                })
                .start();
    }

    interface OnValueChangedListener {
        void onValueChanged(String value);
    }
}
