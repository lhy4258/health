package com.example.health.ui;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;

import android.graphics.Color;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.example.health.R;
import com.example.health.data.ProfileLikeManager;
import com.example.health.data.model.User;
import com.example.health.data.model.ProfileLike;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import cn.leancloud.LCException;
import cn.leancloud.LCFile;
import cn.leancloud.LCObject;
import cn.leancloud.LCQuery;
import cn.leancloud.LCUser;
import cn.leancloud.livequery.LCLiveQuery;
import cn.leancloud.livequery.LCLiveQueryEventHandler;
import cn.leancloud.livequery.LCLiveQuerySubscribeCallback;
import io.reactivex.Observer;
import io.reactivex.disposables.Disposable;
import com.bumptech.glide.Glide;

/**
 * 个人主页 Tab，展示用户头像、昵称、签名、主页被点赞数。
 * 提供扫码添加好友、二维码名片、编辑签名、查看动态/收藏/相册/设置等入口。
 * 支持点赞按钮防抖和 ProfileLikeManager 每日点赞限制。
 */
public class ProfileFragment extends Fragment {

    private static final String KEY_SIGNATURE = "signature";
    private static final String KEY_AVATAR = "avatar";
    private static final String KEY_BACKGROUND = "background";
    private static final String KEY_LIKE_COUNT = "likeCount";
    private static final long CLICK_DEBOUNCE_INTERVAL_MS = 15;

    private static final int REQUEST_PICK_AVATAR = 2001;
    private static final int REQUEST_PICK_BACKGROUND = 2002;

    private ImageView avatarImageView;
    private ImageView backgroundImageView;
    private TextView usernameTextView;
    private TextView emailTextView;
    private TextView signatureTextView;
    private TextView textLikeCount;
    private View iconProfileArrow;
    private View iconQr;
    private View iconScan;
    private View likeContainer;
    private View buttonEditSignature;
    private View rowDynamic;
    private View rowFavorite;
    private View rowAlbum;
    private View rowSettings;
    private SwipeRefreshLayout swipeRefreshLayout;
    private long lastLikeClickTime = 0L;
    private final ProfileLikeManager profileLikeManager = new ProfileLikeManager();
    private LCLiveQuery likeLiveQuery;
    private int pendingLikeClicks = 0;
    private boolean likeInFlight = false;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_profile, container, false);
    }

    private void showQRCodeDialog() {
        LCUser currentUser = LCUser.currentUser();
        if (currentUser == null) {
            Toast.makeText(requireContext(), "请先登录", Toast.LENGTH_SHORT).show();
            return;
        }

        String qrContent = "health_app_user:" + currentUser.getObjectId();
        int size = (int) (getResources().getDisplayMetrics().density * 200);
        Bitmap qrBitmap = createQRCodeBitmap(qrContent, size, size);

        if (qrBitmap == null) {
            Toast.makeText(requireContext(), "生成二维码失败", Toast.LENGTH_SHORT).show();
            return;
        }

        ImageView qrImageView = new ImageView(requireContext());
        qrImageView.setImageBitmap(qrBitmap);
        qrImageView.setScaleType(ImageView.ScaleType.FIT_CENTER);

        LinearLayout container = new LinearLayout(requireContext());
        container.setOrientation(LinearLayout.VERTICAL);
        int padding = (int) (getResources().getDisplayMetrics().density * 24);
        container.setPadding(padding, padding, padding, padding);
        container.setGravity(android.view.Gravity.CENTER);

        TextView hintText = new TextView(requireContext());
        hintText.setText("扫一扫加我好友");
        hintText.setTextSize(14);
        hintText.setTextColor(Color.parseColor("#999999"));
        hintText.setGravity(android.view.Gravity.CENTER);
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        textParams.topMargin = (int) (getResources().getDisplayMetrics().density * 16);

        container.addView(qrImageView, new LinearLayout.LayoutParams(size, size));
        container.addView(hintText, textParams);

        AlertDialog dialog = new AlertDialog.Builder(requireContext())
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

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        avatarImageView = view.findViewById(R.id.avatarImageView);
        backgroundImageView = view.findViewById(R.id.backgroundImageView);
        usernameTextView = view.findViewById(R.id.usernameTextView);
        emailTextView = view.findViewById(R.id.emailTextView);
        signatureTextView = view.findViewById(R.id.signatureTextView);
        textLikeCount = view.findViewById(R.id.textLikeCount);
        iconProfileArrow = view.findViewById(R.id.iconProfileArrow);
        iconQr = view.findViewById(R.id.iconQr);
        iconScan = view.findViewById(R.id.iconScan);
        likeContainer = view.findViewById(R.id.likeContainer);
        buttonEditSignature = view.findViewById(R.id.buttonEditSignature);
        rowDynamic = view.findViewById(R.id.rowDynamic);
        rowFavorite = view.findViewById(R.id.rowFavorite);
        rowAlbum = view.findViewById(R.id.rowAlbum);
        rowSettings = view.findViewById(R.id.rowSettings);
        swipeRefreshLayout = view.findViewById(R.id.swipeRefreshProfile);

        loadUserInfo();

        if (swipeRefreshLayout != null) {
            swipeRefreshLayout.setOnRefreshListener(() -> {
                LCUser currentUser = LCUser.currentUser();
                if (currentUser == null) {
                    swipeRefreshLayout.setRefreshing(false);
                    return;
                }
                String selfUserId = currentUser.getObjectId();
                if (selfUserId == null || selfUserId.isEmpty()) {
                    swipeRefreshLayout.setRefreshing(false);
                    return;
                }
                profileLikeManager.invalidateCounts(selfUserId);
                refreshTotalLikeCount(selfUserId, () -> swipeRefreshLayout.setRefreshing(false));
            });
        }

        avatarImageView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                LCUser currentUser = LCUser.currentUser();
                if (currentUser != null) {
                    LCFile avatar = currentUser.getLCFile(KEY_AVATAR);
                    if (avatar != null && avatar.getUrl() != null) {
                        List<String> imageUrls = new ArrayList<>();
                        imageUrls.add(avatar.getUrl());
                        Intent intent = new Intent(requireContext(), ImagePreviewActivity.class);
                        intent.putStringArrayListExtra(ImagePreviewActivity.EXTRA_IMAGE_URLS, (ArrayList<String>) imageUrls);
                        intent.putExtra(ImagePreviewActivity.EXTRA_START_POSITION, 0);
                        startActivity(intent);
                    }
                }
            }
        });

        iconProfileArrow.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(requireContext(), ProfileDetailActivity.class);
                startActivity(intent);
            }
        });

        iconQr.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showQRCodeDialog();
            }
        });

        iconScan.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                IntentIntegrator integrator = IntentIntegrator.forSupportFragment(ProfileFragment.this);
                integrator.setDesiredBarcodeFormats(IntentIntegrator.QR_CODE);
                integrator.setPrompt("扫描二维码");
                integrator.setCameraId(0);
                integrator.setBeepEnabled(true);
                integrator.setBarcodeImageEnabled(false);
                integrator.setCaptureActivity(PortraitCaptureActivity.class);
                integrator.setOrientationLocked(false);
                integrator.initiateScan();
            }
        });

        buttonEditSignature.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showEditSignatureDialog();
            }
        });

        likeContainer.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                handleLikeClick();
            }
        });

        rowDynamic.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(requireContext(), MomentsActivity.class);
                startActivity(intent);
            }
        });

        rowAlbum.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(requireContext(), AlbumActivity.class);
                startActivity(intent);
            }
        });

        rowFavorite.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(requireContext(), FavoriteActivity.class);
                startActivity(intent);
            }
        });

        rowSettings.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(requireContext(), SettingsActivity.class);
                startActivity(intent);
            }
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        if (isAdded()) {
            loadUserInfo();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (likeLiveQuery != null) {
            likeLiveQuery.unsubscribeInBackground(new LCLiveQuerySubscribeCallback() {
                @Override
                public void done(LCException e) {
                }
            });
            likeLiveQuery = null;
        }
    }

    private void loadUserInfo() {
        LCUser currentUser = LCUser.currentUser();
        if (currentUser == null) {
            usernameTextView.setText("未登录");
            emailTextView.setText("");
            signatureTextView.setText("请先登录后编辑个人资料");
            if (textLikeCount != null) {
                textLikeCount.setText("0");
            }
            return;
        }

        bindUserInfo(currentUser);

        String objectId = currentUser.getObjectId();
        if (objectId == null || objectId.isEmpty()) {
            return;
        }

        subscribeToSelfLikeUpdate(objectId);

        LCQuery<LCUser> query = LCUser.getQuery();
        query.getInBackground(objectId).subscribe(new Observer<LCUser>() {
            @Override
            public void onSubscribe(Disposable d) {
            }

            @Override
            public void onNext(LCUser user) {
                bindUserInfo(user != null ? user : currentUser);
            }

            @Override
            public void onError(Throwable e) {
                bindUserInfo(currentUser);
            }

            @Override
            public void onComplete() {
            }
        });
    }

    private void subscribeToSelfLikeUpdate(String selfUserId) {
        if (selfUserId == null || selfUserId.isEmpty()) {
            return;
        }
        if (likeLiveQuery != null) {
            likeLiveQuery.unsubscribeInBackground(new LCLiveQuerySubscribeCallback() {
                @Override
                public void done(LCException e) {
                }
            });
        }
        LCUser selfUser;
        try {
            selfUser = LCUser.createWithoutData(LCUser.class, selfUserId);
        } catch (LCException e) {
            return;
        }
        LCQuery<ProfileLike> q = LCQuery.getQuery(ProfileLike.class);
        q.whereEqualTo(ProfileLike.KEY_TO_USER, selfUser);
        likeLiveQuery = LCLiveQuery.initWithQuery(q);
        likeLiveQuery.setEventHandler(new LCLiveQueryEventHandler() {
            @Override
            public void onObjectCreated(LCObject object) {
                if (!isAdded()) return;
                requireActivity().runOnUiThread(() -> {
                    profileLikeManager.invalidateCounts(selfUserId);
                    refreshTotalLikeCount(selfUserId);
                });
            }
        });
        likeLiveQuery.subscribeInBackground(new LCLiveQuerySubscribeCallback() {
            @Override
            public void done(LCException e) {
            }
        });
    }

    private void bindUserInfo(LCUser user) {
        if (!isAdded() || user == null) {
            return;
        }

        String username = user.getUsername();
        if (username == null || username.isEmpty()) {
            username = "用户";
        }
        usernameTextView.setText(username);

        String email = user.getEmail();
        if (email == null || email.isEmpty()) {
            email = "未设置邮箱";
        }
        emailTextView.setText(email);

        String signature = user.getString(KEY_SIGNATURE);
        if (signature == null || signature.isEmpty()) {
            signature = "这个人很懒，还没有写签名";
        }
        signatureTextView.setText(signature);

        String userId = user.getObjectId();
        if (userId != null && !userId.isEmpty()) {
            refreshTotalLikeCount(userId);
        } else if (textLikeCount != null) {
            textLikeCount.setText("0");
        }

        LCFile avatar = user.getLCFile(KEY_AVATAR);
        if (avatar != null && avatar.getUrl() != null) {
            String url = avatar.getUrl();
            if (isAdded()) {
                Glide.with(requireContext())
                        .load(url)
                        .placeholder(R.drawable.ic_nav_profile)
                        .error(R.drawable.ic_nav_profile)
                        .centerCrop()
                        .into(avatarImageView);
            }
        } else {
            avatarImageView.setImageResource(R.drawable.ic_nav_profile);
        }

        LCFile background = user.getLCFile(KEY_BACKGROUND);
        if (background != null && background.getUrl() != null) {
            String url = background.getUrl();
            if (isAdded()) {
                Glide.with(requireContext())
                        .load(url)
                        .centerCrop()
                        .into(backgroundImageView);
            }
        }
    }

    private void refreshTotalLikeCount(String targetUserId) {
        refreshTotalLikeCount(targetUserId, null);
    }

    private void refreshTotalLikeCount(String targetUserId, Runnable onDone) {
        if (targetUserId == null || targetUserId.isEmpty()) {
            if (onDone != null) {
                onDone.run();
            }
            return;
        }
        profileLikeManager.loadTotalCount(targetUserId, new ProfileLikeManager.TotalCountCallback() {
            @Override
            public void onSuccess(int totalCount) {
                if (!isAdded()) {
                    if (onDone != null) {
                        onDone.run();
                    }
                    return;
                }
                requireActivity().runOnUiThread(() -> {
                    if (textLikeCount != null) {
                        textLikeCount.setText(String.valueOf(totalCount));
                    }
                    if (onDone != null) {
                        onDone.run();
                    }
                });
            }

            @Override
            public void onError(String message) {
                if (onDone != null) {
                    onDone.run();
                }
            }
        });
    }

    private void handleLikeClick() {
        long now = System.currentTimeMillis();
        if (now - lastLikeClickTime < CLICK_DEBOUNCE_INTERVAL_MS) {
            return;
        }
        lastLikeClickTime = now;

        LCUser currentUser = LCUser.currentUser();
        if (currentUser == null) {
            if (isAdded()) {
                Toast.makeText(requireContext(), "请先登录后再点赞", Toast.LENGTH_SHORT).show();
            }
            return;
        }

        String userId = currentUser.getObjectId();
        if (userId == null || userId.isEmpty()) {
            return;
        }
        if (pendingLikeClicks >= ProfileLikeManager.MAX_LIKES_PER_DAY) {
            return;
        }
        pendingLikeClicks += 1;
        drainLikeQueue(userId);
    }

    private void drainLikeQueue(String userId) {
        if (likeInFlight) {
            return;
        }
        if (pendingLikeClicks <= 0) {
            return;
        }
        likeInFlight = true;
        pendingLikeClicks -= 1;

        profileLikeManager.like(userId, new ProfileLikeManager.LikeCallback() {
            @Override
            public void onSuccess(int todayCount, int totalCount) {
                likeInFlight = false;
                if (!isAdded()) {
                    pendingLikeClicks = 0;
                    return;
                }
                if (textLikeCount != null) {
                    textLikeCount.setText(String.valueOf(totalCount));
                }

                if (likeContainer != null) {
                    likeContainer.animate()
                            .scaleX(1.1f)
                            .scaleY(1.1f)
                            .setDuration(150)
                            .withEndAction(new Runnable() {
                                @Override
                                public void run() {
                                    likeContainer.animate()
                                            .scaleX(1f)
                                            .scaleY(1f)
                                            .setDuration(150)
                                            .start();
                                }
                            })
                            .start();
                }
                drainLikeQueue(userId);
            }

            @Override
            public void onLimitReached(int todayCount, int totalCount) {
                likeInFlight = false;
                pendingLikeClicks = 0;
                if (!isAdded()) {
                    return;
                }
                if (textLikeCount != null) {
                    textLikeCount.setText(String.valueOf(totalCount));
                }
                Toast.makeText(requireContext(), "今日点赞已达上限（" + ProfileLikeManager.MAX_LIKES_PER_DAY + "次）", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onError(String message) {
                likeInFlight = false;
                pendingLikeClicks = 0;
                if (!isAdded()) {
                    return;
                }
                Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showEditProfileDialog() {
        LCUser currentUser = LCUser.currentUser();
        if (currentUser == null) {
            return;
        }

        String currentNickname = currentUser.getUsername();
        String currentSignature = currentUser.getString(KEY_SIGNATURE);

        LinearLayout container = new LinearLayout(requireContext());
        container.setOrientation(LinearLayout.VERTICAL);
        int padding = (int) (getResources().getDisplayMetrics().density * 16);
        container.setPadding(padding, padding, padding, padding);

        final android.widget.EditText nicknameEdit = new android.widget.EditText(requireContext());
        nicknameEdit.setHint("请输入用户名");
        if (currentNickname != null) {
            nicknameEdit.setText(currentNickname);
            nicknameEdit.setSelection(currentNickname.length());
        }

        final android.widget.EditText signatureEdit = new android.widget.EditText(requireContext());
        signatureEdit.setHint("请输入个性签名");
        if (currentSignature != null) {
            signatureEdit.setText(currentSignature);
            signatureEdit.setSelection(currentSignature.length());
        }

        container.addView(nicknameEdit, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams signatureParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        signatureParams.topMargin = (int) (getResources().getDisplayMetrics().density * 12);
        container.addView(signatureEdit, signatureParams);

        new AlertDialog.Builder(requireContext())
                .setTitle("编辑个人资料")
                .setView(container)
                .setPositiveButton("保存", (dialog, which) -> {
                    String newNickname = nicknameEdit.getText().toString().trim();
                    String newSignature = signatureEdit.getText().toString().trim();

                    if (newNickname.length() > 20) {
                        newNickname = newNickname.substring(0, 20);
                    }
                    if (newSignature.length() > 60) {
                        newSignature = newSignature.substring(0, 60);
                    }

                    currentUser.setUsername(newNickname);
                    currentUser.put(KEY_SIGNATURE, newSignature);

                    currentUser.saveInBackground().subscribe(new Observer<LCObject>() {
                        @Override
                        public void onSubscribe(Disposable d) {
                        }

                        @Override
                        public void onNext(LCObject lcObject) {
                            if (!isAdded()) {
                                return;
                            }
                            String storedNickname = currentUser.getUsername();
                            String storedSignature = currentUser.getString(KEY_SIGNATURE);
                            String updatedNickname = storedNickname != null && !storedNickname.isEmpty()
                                    ? storedNickname
                                    : usernameTextView.getText().toString();
                            String updatedSignature = storedSignature != null && !storedSignature.isEmpty()
                                    ? storedSignature
                                    : signatureTextView.getText().toString();
                            usernameTextView.setText(updatedNickname);
                            signatureTextView.setText(updatedSignature);
                        }

                        @Override
                        public void onError(Throwable e) {
                        }

                        @Override
                        public void onComplete() {
                        }
                    });
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void showEditSignatureDialog() {
        LCUser currentUser = LCUser.currentUser();
        if (currentUser == null) {
            return;
        }

        String currentSignature = currentUser.getString(KEY_SIGNATURE);

        final android.widget.EditText signatureEdit = new android.widget.EditText(requireContext());
        signatureEdit.setHint("请输入个性签名");
        if (currentSignature != null) {
            signatureEdit.setText(currentSignature);
            signatureEdit.setSelection(currentSignature.length());
        }
        int padding = (int) (getResources().getDisplayMetrics().density * 16);
        signatureEdit.setPadding(padding, padding, padding, padding);

        new AlertDialog.Builder(requireContext())
                .setTitle("编辑个性签名")
                .setView(signatureEdit)
                .setPositiveButton("保存", (dialog, which) -> {
                    String newSignature = signatureEdit.getText().toString().trim();
                    if (newSignature.length() > 60) {
                        newSignature = newSignature.substring(0, 60);
                    }
                    currentUser.put(KEY_SIGNATURE, newSignature);
                    currentUser.saveInBackground().subscribe(new Observer<LCObject>() {
                        @Override
                        public void onSubscribe(Disposable d) {
                        }

                        @Override
                        public void onNext(LCObject lcObject) {
                            if (!isAdded()) {
                                return;
                            }
                            String storedSignature = currentUser.getString(KEY_SIGNATURE);
                            String updatedSignature = storedSignature != null && !storedSignature.isEmpty()
                                    ? storedSignature
                                    : signatureTextView.getText().toString();
                            signatureTextView.setText(updatedSignature);
                        }

                        @Override
                        public void onError(Throwable e) {
                        }

                        @Override
                        public void onComplete() {
                        }
                    });
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void pickImage(int requestCode) {
        LCUser currentUser = LCUser.currentUser();
        if (currentUser == null) {
            return;
        }
        Intent intent;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent = new Intent(MediaStore.ACTION_PICK_IMAGES);
            intent.setType("image/*");
        } else {
            intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("image/*");
        }
        startActivityForResult(intent, requestCode);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        IntentResult result = IntentIntegrator.parseActivityResult(requestCode, resultCode, data);
        if (result != null) {
            if (result.getContents() == null) {
                Toast.makeText(requireContext(), "已取消扫描", Toast.LENGTH_SHORT).show();
            } else {
                handleScanContent(result.getContents());
            }
            return;
        }

        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != Activity.RESULT_OK || data == null) {
            return;
        }
        Uri uri = data.getData();
        if (uri == null) {
            return;
        }

        byte[] bytes = readAndCompressFromUri(uri);
        if (bytes == null || bytes.length == 0) {
            return;
        }
        if (requestCode == REQUEST_PICK_AVATAR) {
            uploadProfileImage(bytes, KEY_AVATAR, avatarImageView);
        } else if (requestCode == REQUEST_PICK_BACKGROUND) {
            uploadProfileImage(bytes, KEY_BACKGROUND, backgroundImageView);
        }
    }

    private void handleScanContent(String content) {
        if (!isAdded()) {
            return;
        }
        if (content == null) {
            Toast.makeText(requireContext(), "未识别到二维码", Toast.LENGTH_SHORT).show();
            return;
        }
        if (content.startsWith("health_app_user:")) {
            String userId = content.substring("health_app_user:".length());
            sendFriendRequest(userId);
            return;
        }
        new AlertDialog.Builder(requireContext())
                .setTitle("扫描结果")
                .setMessage("内容: " + content)
                .setPositiveButton("确定", null)
                .show();
    }

 

    private void uploadProfileImage(byte[] data, String key, ImageView targetView) {
        LCUser currentUser = LCUser.currentUser();
        if (currentUser == null) {
            return;
        }
        String fileName = key + "_" + System.currentTimeMillis() + ".jpg";
        LCFile file = new LCFile(fileName, data);
        file.saveInBackground().subscribe(new Observer<LCFile>() {
            @Override
            public void onSubscribe(Disposable d) {
            }

            @Override
            public void onNext(LCFile lcFile) {
                currentUser.put(key, lcFile);
                currentUser.saveInBackground().subscribe(new Observer<LCObject>() {
                    @Override
                    public void onSubscribe(Disposable d) {
                    }

                    @Override
                    public void onNext(LCObject lcObject) {
                        if (!isAdded()) {
                            return;
                        }
                        Bitmap bitmap = BitmapFactory.decodeByteArray(data, 0, data.length);
                        if (bitmap != null) {
                            Glide.with(requireContext())
                                    .load(bitmap)
                                    .centerCrop()
                                    .into(targetView);
                        }
                    }

                    @Override
                    public void onError(Throwable e) {
                    }

                    @Override
                    public void onComplete() {
                    }
                });
            }

            @Override
            public void onError(Throwable e) {
                if (isAdded()) {
                    Toast.makeText(requireContext(), "图片上传失败", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onComplete() {
            }
        });
    }

    @Nullable
    private byte[] readAndCompressFromUri(Uri uri) {
        try {
            InputStream is = requireContext().getContentResolver().openInputStream(uri);
            if (is == null) {
                return null;
            }
            Bitmap bitmap = BitmapFactory.decodeStream(is);
            is.close();
            if (bitmap == null) {
                return null;
            }
            return compressBitmap(bitmap);
        } catch (Exception e) {
            return null;
        }
    }

    @Nullable
    private byte[] compressBitmap(Bitmap bitmap) {
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

    private void loadImageFromUrl(String url, ImageView target) {
        if (!isAdded() || url == null || url.isEmpty()) {
            return;
        }
        Glide.with(requireContext())
                .load(url)
                .centerCrop()
                .into(target);
    }

    private void sendFriendRequest(String toUserId) {
        LCUser currentUser = LCUser.currentUser();
        if (currentUser == null) {
            if (isAdded()) {
                Toast.makeText(requireContext(), "请先登录", Toast.LENGTH_SHORT).show();
            }
            return;
        }
        if (toUserId == null || toUserId.isEmpty()) {
            if (isAdded()) {
                Toast.makeText(requireContext(), "发送失败", Toast.LENGTH_SHORT).show();
            }
            return;
        }
        String selfId = currentUser.getObjectId();
        if (selfId != null && selfId.equals(toUserId)) {
            if (isAdded()) {
                Toast.makeText(requireContext(), "不能添加自己为好友", Toast.LENGTH_SHORT).show();
            }
            return;
        }
        LCUser targetUser;
        try {
            targetUser = LCUser.createWithoutData(LCUser.class, toUserId);
        } catch (LCException e) {
            if (isAdded()) {
                Toast.makeText(requireContext(), "发送失败", Toast.LENGTH_SHORT).show();
            }
            return;
        }
        LCQuery<LCObject> query = new LCQuery<>("FriendRequest");
        query.whereEqualTo("fromUser", currentUser);
        query.whereEqualTo("toUser", targetUser);
        query.whereEqualTo("status", "pending");
        query.limit(1);
        query.findInBackground().subscribe(new Observer<java.util.List<LCObject>>() {
            @Override
            public void onSubscribe(Disposable d) {
            }

            @Override
            public void onNext(java.util.List<LCObject> existing) {
                if (!isAdded()) {
                    return;
                }
                if (existing != null && !existing.isEmpty()) {
                    Toast.makeText(requireContext(), "已发送过好友请求，请等待对方确认", Toast.LENGTH_SHORT).show();
                    return;
                }
                LCObject req = new LCObject("FriendRequest");
                req.put("fromUser", currentUser);
                req.put("toUser", targetUser);
                req.put("status", "pending");
                req.saveInBackground().subscribe(new Observer<LCObject>() {
                    @Override
                    public void onSubscribe(Disposable d) {
                    }

                    @Override
                    public void onNext(LCObject lcObject) {
                        if (!isAdded()) {
                            return;
                        }
                        Toast.makeText(requireContext(), "已发送好友请求", Toast.LENGTH_SHORT).show();
                    }

                    @Override
                    public void onError(Throwable e) {
                        if (!isAdded()) {
                            return;
                        }
                        Toast.makeText(requireContext(), "发送失败", Toast.LENGTH_SHORT).show();
                    }

                    @Override
                    public void onComplete() {
                    }
                });
            }

            @Override
            public void onError(Throwable e) {
                if (!isAdded()) {
                    return;
                }
                Toast.makeText(requireContext(), "发送失败", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onComplete() {
            }
        });
    }
}
