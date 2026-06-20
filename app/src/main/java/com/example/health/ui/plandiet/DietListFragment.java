package com.example.health.ui.plandiet;

import android.app.Activity;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Gravity;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.example.health.R;
import com.example.health.data.model.DietRecord;
import com.example.health.ui.PlanDietFragment;
import com.google.android.material.button.MaterialButtonToggleGroup;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import cn.leancloud.LCFile;
import cn.leancloud.LCObject;
import cn.leancloud.LCQuery;
import cn.leancloud.LCUser;
import io.reactivex.Observer;
import io.reactivex.disposables.Disposable;

/**
 * 饮食记录列表，支持日视图（可编辑三餐+加餐）和周视图（统计数据）。
 * 每餐可添加图片（最多 5 张），同一自然日最多提交一次记录。
 * 实现 PlanDietFragment.PlanDietPage 接口，响应 FAB 按钮点击。
 */
public class DietListFragment extends Fragment implements PlanDietFragment.PlanDietPage {

    private static final int REQUEST_PICK_IMAGE = 1001;
    private static final int REQUEST_CAPTURE_IMAGE = 1002;

    private static final String PREF_NAME_UPLOAD = "diet_upload_prefs";
    private static final String KEY_LAST_UPLOAD_DAY = "last_upload_day";

    private RecyclerView recyclerView;
    private DietAdapter adapter;
    private MaterialButtonToggleGroup toggleGroup;
    private int currentFilter = 0;
    private SwipeRefreshLayout swipeRefreshLayout;

    private DietRecord editingRecord;
    private long[] selectedTimeForDialog;
    private SimpleDateFormat dateFormatDialog;
    private final List<MealEntry> currentMeals = new ArrayList<>();
    private MealEntry pendingImageMeal;
    private AlertDialog uploadProgressDialog;
    private ActivityResultLauncher<PickVisualMediaRequest> pickMealImagesLauncher;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_diet_list, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        pickMealImagesLauncher = registerForActivityResult(
                new ActivityResultContracts.PickMultipleVisualMedia(5),
                uris -> {
                    if (pendingImageMeal == null || uris == null || uris.isEmpty()) {
                        pendingImageMeal = null;
                        return;
                    }
                    int currentCount = pendingImageMeal.images != null ? pendingImageMeal.images.size() : 0;
                    boolean sizeExceeded = false;
                    for (Uri uri : uris) {
                        if (currentCount >= 5) {
                            sizeExceeded = true;
                            break;
                        }
                        byte[] compressed = readAndCompressFromUri(uri);
                        if (compressed == null) {
                            Toast.makeText(requireContext(), "读取图片失败", Toast.LENGTH_SHORT).show();
                            continue;
                        }
                        if (compressed.length > 5 * 1024 * 1024) {
                            Toast.makeText(requireContext(), "图片大小不能超过5MB", Toast.LENGTH_SHORT).show();
                            continue;
                        }
                        applyImageToPendingMeal(compressed);
                        currentCount++;
                    }
                    if (sizeExceeded) {
                        Toast.makeText(requireContext(), "每餐最多允许添加5张图片", Toast.LENGTH_SHORT).show();
                    }
                    pendingImageMeal = null;
                });
        swipeRefreshLayout = view.findViewById(R.id.swipeRefreshLayout);
        recyclerView = view.findViewById(R.id.recyclerViewDiets);
        toggleGroup = view.findViewById(R.id.toggleGroupDietTime);

        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new DietAdapter();
        recyclerView.setAdapter(adapter);

        if (swipeRefreshLayout != null) {
            swipeRefreshLayout.setOnRefreshListener(() -> loadDiets(true));
        }

        toggleGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) return;
            if (checkedId == R.id.btnDietDaily) {
                currentFilter = 0;
            } else if (checkedId == R.id.btnDietWeekly) {
                currentFilter = 1;
            }
            loadDiets();
        });

        loadDiets(false);
    }

    @Override
    public void onAddItem() {
        if (currentFilter == 1) {
            Toast.makeText(requireContext(), "每周视图为自动统计，只能查看，不能新增", Toast.LENGTH_SHORT).show();
        } else {
            if (hasUploadedToday()) {
                showDailyUploadLimitToast();
            } else {
                showEditDialog(null);
            }
        }
    }

    private void loadDiets() {
        loadDiets(false);
    }

    private void loadDiets(boolean fromRefresh) {
        LCUser currentUser = LCUser.currentUser();
        if (currentUser == null) {
            if (fromRefresh && swipeRefreshLayout != null) {
                swipeRefreshLayout.setRefreshing(false);
            }
            return;
        }

        LCQuery<DietRecord> query = new LCQuery<>("DietRecord");
        query.whereEqualTo(DietRecord.ATTR_USER, currentUser);
        query.orderByAscending(DietRecord.ATTR_DATE);

        query.findInBackground().subscribe(new Observer<List<DietRecord>>() {
            @Override
            public void onSubscribe(Disposable d) {
            }

            @Override
            public void onNext(List<DietRecord> dietRecords) {
                if (isAdded()) {
                    if (currentFilter == 0) {
                        adapter.setItems(dietRecords);
                    } else {
                        adapter.setItems(buildWeeklySummaries(dietRecords));
                    }
                    syncTodayUploadFlag(dietRecords);
                }
                if (fromRefresh && swipeRefreshLayout != null) {
                    swipeRefreshLayout.setRefreshing(false);
                }
            }

            @Override
            public void onError(Throwable e) {
                if (fromRefresh && swipeRefreshLayout != null) {
                    swipeRefreshLayout.setRefreshing(false);
                }
            }

            @Override
            public void onComplete() {
            }
        });
    }

    private void showEditDialog(@Nullable DietRecord record) {
        View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_diet_edit, null, false);
        TextView textDate = dialogView.findViewById(R.id.textDietDate);
        LinearLayout containerMeals = dialogView.findViewById(R.id.containerMeals);
        Button buttonAddExtraMeal = dialogView.findViewById(R.id.buttonAddExtraMeal);
        EditText editRemark = dialogView.findViewById(R.id.editDietRemark);

        Calendar calendar = Calendar.getInstance();
        if (record != null && record.getDate() > 0) {
            calendar.setTimeInMillis(record.getDate());
        }
        selectedTimeForDialog = new long[]{calendar.getTimeInMillis()};

        dateFormatDialog = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        textDate.setText(dateFormatDialog.format(new Date(selectedTimeForDialog[0])));

        textDate.setOnClickListener(v -> {
            Calendar c = Calendar.getInstance();
            c.setTimeInMillis(selectedTimeForDialog[0]);
            DatePickerDialog picker = new DatePickerDialog(requireContext(), (view1, year, month, dayOfMonth) -> {
                Calendar chosen = Calendar.getInstance();
                chosen.set(year, month, dayOfMonth, 0, 0, 0);
                chosen.set(Calendar.MILLISECOND, 0);
                selectedTimeForDialog[0] = chosen.getTimeInMillis();
                textDate.setText(dateFormatDialog.format(chosen.getTime()));
            }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH));
            picker.show();
        });

        if (record != null) {
            if (record.getRemark() != null) {
                editRemark.setText(record.getRemark());
            }
        }

        editingRecord = record;
        dialogMealsContainer = containerMeals;

        currentMeals.clear();
        if (record != null && record.getMeals() != null && !record.getMeals().isEmpty()) {
            currentMeals.addAll(parseMealsJson(record.getMeals()));
        }
        if (currentMeals.isEmpty()) {
            initDefaultMeals(selectedTimeForDialog[0]);
        }
        rebuildMealsUI();

        buttonAddExtraMeal.setOnClickListener(v -> {
            int extraCount = 0;
            for (MealEntry m : currentMeals) {
                if (!m.fixed) {
                    extraCount++;
                }
            }
            if (extraCount >= 3) {
                Toast.makeText(requireContext(), "最多添加3个额外时段", Toast.LENGTH_SHORT).show();
                return;
            }
            MealEntry extra = createExtraMeal(extraCount);
            currentMeals.add(extra);
            rebuildMealsUI();
        });

        TextView titleView = new TextView(requireContext());
        titleView.setText(record == null ? "添加饮食记录" : "编辑饮食记录");
        titleView.setGravity(Gravity.CENTER);
        titleView.setTextSize(18);
        titleView.setPadding(32, 40, 32, 32);

        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setCustomTitle(titleView)
                .setView(dialogView)
                .setPositiveButton("保存", null)
                .setNegativeButton("取消", (d, which) -> d.dismiss())
                .create();

        dialog.setOnShowListener(d -> {
            if (dialog.getWindow() != null) {
                dialog.getWindow().setBackgroundDrawableResource(R.drawable.bg_dialog_round_white);
            }
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                if (!validateMeals()) {
                    return;
                }
                String remark = editRemark.getText().toString().trim();

                LCUser currentUser = LCUser.currentUser();
                if (currentUser == null) {
                    Toast.makeText(requireContext(), "请先登录", Toast.LENGTH_SHORT).show();
                    return;
                }

                DietRecord target = editingRecord != null ? editingRecord : new DietRecord();
                target.setRemark(remark);
                target.setDate(selectedTimeForDialog[0]);
                target.setUser(currentUser);
                target.setMeals(buildMealsJson());
                ensureUniqueDailyAndSave(target, dialog);
            });
        });

        dialog.show();
    }

    private void saveDietRecord(DietRecord record, @Nullable LCFile file, AlertDialog dialog) {
        if (file != null) {
            record.setImage(file);
        }
        record.saveInBackground().subscribe(new Observer<LCObject>() {
            @Override
            public void onSubscribe(Disposable d) {
            }

            @Override
            public void onNext(LCObject lcObject) {
                if (isAdded()) {
                    markTodayUploadedIfNeeded(record);
                    Toast.makeText(requireContext(), "保存成功", Toast.LENGTH_SHORT).show();
                    loadDiets();
                }
            }

            @Override
            public void onError(Throwable e) {
                if (isAdded()) {
                    Toast.makeText(requireContext(), "保存失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onComplete() {
                dialog.dismiss();
            }
        });
    }

    private void uploadMealImagesAndSave(DietRecord target, AlertDialog dialog) {
        List<MealImage> toUpload = new ArrayList<>();
        for (MealEntry m : currentMeals) {
            if (m.images == null) {
                continue;
            }
            for (MealImage img : m.images) {
                if (img.data != null && img.data.length > 0 && (img.url == null || img.url.isEmpty())) {
                    toUpload.add(img);
                }
            }
        }
        if (toUpload.isEmpty()) {
            target.setMeals(buildMealsJson());
            saveDietRecord(target, null, dialog);
            return;
        }
        showUploadProgressDialog(toUpload.size());
        uploadNextMealImage(0, toUpload, target, dialog);
    }

    private void uploadNextMealImage(int index, List<MealImage> list, DietRecord target, AlertDialog dialog) {
        if (index >= list.size()) {
            dismissUploadProgressDialog();
            target.setMeals(buildMealsJson());
            saveDietRecord(target, null, dialog);
            return;
        }
        MealImage image = list.get(index);
        byte[] data = image.data;
        if (data == null || data.length == 0) {
            uploadNextMealImage(index + 1, list, target, dialog);
            return;
        }
        LCFile file = new LCFile("diet_meal_" + System.currentTimeMillis() + "_" + index + ".jpg", data);
        file.saveInBackground().subscribe(new Observer<LCFile>() {
            @Override
            public void onSubscribe(Disposable d) {
            }

            @Override
            public void onNext(LCFile lcFile) {
                image.url = lcFile.getUrl();
                image.data = null;
                if (isAdded()) {
                    updateUploadProgressDialog(index + 1, list.size());
                }
            }

            @Override
            public void onError(Throwable e) {
                if (isAdded()) {
                    Toast.makeText(requireContext(), "上传图片失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                }
                uploadNextMealImage(index + 1, list, target, dialog);
            }

            @Override
            public void onComplete() {
                uploadNextMealImage(index + 1, list, target, dialog);
            }
        });
    }

    private void showUploadProgressDialog(int total) {
        if (!isAdded()) {
            return;
        }
        uploadProgressDialog = new AlertDialog.Builder(requireContext())
                .setTitle("正在上传图片")
                .setMessage("0 / " + total)
                .setCancelable(false)
                .create();
        uploadProgressDialog.show();
    }

    private void updateUploadProgressDialog(int current, int total) {
        if (uploadProgressDialog != null && uploadProgressDialog.isShowing()) {
            uploadProgressDialog.setMessage(current + " / " + total);
        }
    }

    private void dismissUploadProgressDialog() {
        if (uploadProgressDialog != null && uploadProgressDialog.isShowing()) {
            uploadProgressDialog.dismiss();
        }
        uploadProgressDialog = null;
    }

    private SharedPreferences getUploadPrefs() {
        return requireContext().getSharedPreferences(PREF_NAME_UPLOAD, Context.MODE_PRIVATE);
    }

    private long getDayStart(long millis) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(millis);
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }

    private long getTodayDayStart() {
        return getDayStart(System.currentTimeMillis());
    }

    private boolean hasUploadedToday() {
        SharedPreferences prefs = getUploadPrefs();
        long storedDay = prefs.getLong(KEY_LAST_UPLOAD_DAY, -1L);
        return storedDay == getTodayDayStart();
    }

    private void markTodayUploadedIfNeeded(DietRecord record) {
        long recordDate = record.getDate();
        if (recordDate <= 0) {
            return;
        }
        long recordDay = getDayStart(recordDate);
        long today = getTodayDayStart();
        if (recordDay == today) {
            getUploadPrefs().edit().putLong(KEY_LAST_UPLOAD_DAY, today).apply();
        }
    }

    private void syncTodayUploadFlag(List<DietRecord> records) {
        long today = getTodayDayStart();
        boolean hasToday = false;
        if (records != null) {
            for (DietRecord r : records) {
                long date = r.getDate();
                if (date > 0 && getDayStart(date) == today) {
                    hasToday = true;
                    break;
                }
            }
        }
        SharedPreferences prefs = getUploadPrefs();
        SharedPreferences.Editor editor = prefs.edit();
        if (hasToday) {
            editor.putLong(KEY_LAST_UPLOAD_DAY, today);
        } else {
            editor.remove(KEY_LAST_UPLOAD_DAY);
        }
        editor.apply();
    }

    private void showDailyUploadLimitToast() {
        Toast.makeText(requireContext(),
                "今日上传已达上限，每天仅允许上传一条饮食记录",
                Toast.LENGTH_SHORT).show();
    }

    private void ensureUniqueDailyAndSave(DietRecord target, AlertDialog dialog) {
        LCUser user = target.getUser();
        if (user == null) {
            Toast.makeText(requireContext(), "请先登录", Toast.LENGTH_SHORT).show();
            return;
        }
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(target.getDate());
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        long dayStart = c.getTimeInMillis();
        c.add(Calendar.DAY_OF_YEAR, 1);
        long dayEnd = c.getTimeInMillis();

        LCQuery<DietRecord> query = new LCQuery<>("DietRecord");
        query.whereEqualTo(DietRecord.ATTR_USER, user);
        query.whereGreaterThanOrEqualTo(DietRecord.ATTR_DATE, dayStart);
        query.whereLessThan(DietRecord.ATTR_DATE, dayEnd);

        query.findInBackground().subscribe(new Observer<List<DietRecord>>() {
            @Override
            public void onSubscribe(Disposable d) {
            }

            @Override
            public void onNext(List<DietRecord> list) {
                if (!isAdded()) {
                    return;
                }
                String targetId = target.getObjectId();
                int otherCount = 0;
                if (list != null) {
                    for (DietRecord r : list) {
                        if (targetId == null || !targetId.equals(r.getObjectId())) {
                            otherCount++;
                        }
                    }
                }
                if (otherCount > 0) {
                    Toast.makeText(requireContext(), "每天只能有一条饮食记录，如需修改请编辑已有记录", Toast.LENGTH_SHORT).show();
                    return;
                }
                uploadMealImagesAndSave(target, dialog);
            }

            @Override
            public void onError(Throwable e) {
                if (isAdded()) {
                    Toast.makeText(requireContext(), "校验每日记录唯一性失败，将直接保存记录: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                }
                uploadMealImagesAndSave(target, dialog);
            }

            @Override
            public void onComplete() {
            }
        });
    }

    private List<DietRecord> buildWeeklySummaries(List<DietRecord> dailyRecords) {
        Map<Long, List<DietRecord>> byWeek = new HashMap<>();
        Calendar calendar = Calendar.getInstance();
        for (DietRecord r : dailyRecords) {
            long dateValue = r.getDate();
            if (dateValue <= 0) {
                continue;
            }
            calendar.setTimeInMillis(dateValue);
            calendar.set(Calendar.HOUR_OF_DAY, 0);
            calendar.set(Calendar.MINUTE, 0);
            calendar.set(Calendar.SECOND, 0);
            calendar.set(Calendar.MILLISECOND, 0);
            int dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK);
            int firstDayOfWeek = calendar.getFirstDayOfWeek();
            int diff = dayOfWeek - firstDayOfWeek;
            if (diff < 0) {
                diff += 7;
            }
            calendar.add(Calendar.DAY_OF_YEAR, -diff);
            long weekStart = calendar.getTimeInMillis();
            List<DietRecord> list = byWeek.get(weekStart);
            if (list == null) {
                list = new ArrayList<>();
                byWeek.put(weekStart, list);
            }
            list.add(r);
        }
        List<Long> keys = new ArrayList<>(byWeek.keySet());
        keys.sort((a, b) -> Long.compare(b, a));
        List<DietRecord> result = new ArrayList<>();
        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        Calendar endCal = Calendar.getInstance();
        for (Long weekStart : keys) {
            List<DietRecord> weekList = byWeek.get(weekStart);
            if (weekList == null || weekList.isEmpty()) {
                continue;
            }
            calendar.setTimeInMillis(weekStart);
            Date startDate = calendar.getTime();
            endCal.setTimeInMillis(weekStart);
            endCal.add(Calendar.DAY_OF_YEAR, 6);
            Date endDate = endCal.getTime();
            DietRecord summary = new DietRecord();
            summary.setDate(weekStart);
            summary.setRemark("本周共 " + weekList.size() + " 天有饮食记录（" + df.format(startDate) + " 至 " + df.format(endDate) + "）");
            result.add(summary);
        }
        return result;
    }

    private static class MealImage {
        String url;
        byte[] data;
        Bitmap thumbnail;
    }

    private static class MealEntry {
        String label;
        boolean fixed;
        long timeMillis;
        String content;
        List<MealImage> images = new ArrayList<>();
    }

    private void initDefaultMeals(long baseDayMillis) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(baseDayMillis);
        currentMeals.clear();
        currentMeals.add(createFixedMeal("早餐", 8, 0, c));
        currentMeals.add(createFixedMeal("午餐", 12, 0, c));
        currentMeals.add(createFixedMeal("晚餐", 18, 0, c));
    }

    private MealEntry createFixedMeal(String label, int hour, int minute, Calendar day) {
        Calendar c = (Calendar) day.clone();
        c.set(Calendar.HOUR_OF_DAY, hour);
        c.set(Calendar.MINUTE, minute);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        MealEntry entry = new MealEntry();
        entry.label = label;
        entry.fixed = true;
        entry.timeMillis = c.getTimeInMillis();
        entry.content = "";
        return entry;
    }

    private MealEntry createExtraMeal(int index) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(selectedTimeForDialog[0]);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        MealEntry entry = new MealEntry();
        entry.label = "加餐" + (index + 1);
        entry.fixed = false;
        entry.timeMillis = c.getTimeInMillis();
        entry.content = "";
        return entry;
    }

    private LinearLayout dialogMealsContainer;

    private void rebuildMealsUI() {
        if (dialogMealsContainer == null) {
            return;
        }
        LinearLayout container = dialogMealsContainer;
        container.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(requireContext());
        for (int i = 0; i < currentMeals.size(); i++) {
            MealEntry entry = currentMeals.get(i);
            View itemView = inflater.inflate(R.layout.item_diet_meal_edit, container, false);
            TextView textMealLabel = itemView.findViewById(R.id.textMealLabel);
            TextView textMealTime = itemView.findViewById(R.id.textMealTime);
            TextView textRemoveMeal = itemView.findViewById(R.id.textRemoveMeal);
            EditText editMealContent = itemView.findViewById(R.id.editMealContent);
            LinearLayout imagesContainer = itemView.findViewById(R.id.containerMealImages);
            TextView textImageCount = itemView.findViewById(R.id.textImageCount);
            textMealLabel.setText(entry.label);
            SimpleDateFormat tf = new SimpleDateFormat("HH:mm", Locale.getDefault());
            textMealTime.setText(tf.format(new Date(entry.timeMillis)));
            editMealContent.setText(entry.content);

            imagesContainer.removeAllViews();
            int totalImages = entry.images != null ? entry.images.size() : 0;
            textImageCount.setText(totalImages + "/5");

            int max = totalImages;
            for (int idx = 0; idx < max; idx++) {
                MealImage img = entry.images.get(idx);
                ImageView thumb = new ImageView(requireContext());
                int size = (int) (40 * getResources().getDisplayMetrics().density / 3 * 3);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(120, 120);
                lp.leftMargin = 8;
                thumb.setLayoutParams(lp);
                thumb.setScaleType(ImageView.ScaleType.CENTER_CROP);
                thumb.setBackgroundColor(getResources().getColor(R.color.background_color));
                if (img.data != null) {
                    Bitmap bitmap = BitmapFactory.decodeByteArray(img.data, 0, img.data.length);
                    thumb.setImageBitmap(bitmap);
                } else if (img.url != null && !img.url.isEmpty()) {
                    loadMealImageFromUrl(img, thumb);
                }
                final int imageIndex = idx;
                thumb.setOnClickListener(v -> showImagePreview(entry, imageIndex));
                thumb.setOnLongClickListener(v -> {
                    showImageOptions(entry, imageIndex);
                    return true;
                });
                imagesContainer.addView(thumb);
            }
            if (totalImages < 5) {
                ImageView addView = new ImageView(requireContext());
                LinearLayout.LayoutParams lpAdd = new LinearLayout.LayoutParams(120, 120);
                lpAdd.leftMargin = 8;
                addView.setLayoutParams(lpAdd);
                addView.setScaleType(ImageView.ScaleType.CENTER);
                addView.setBackgroundColor(getResources().getColor(R.color.background_color));
                addView.setImageResource(android.R.drawable.ic_input_add);
                addView.setOnClickListener(v -> {
                    pendingImageMeal = entry;
                    showImageSourceChooser();
                });
                imagesContainer.addView(addView);
            }
            if (!entry.fixed) {
                textRemoveMeal.setVisibility(View.VISIBLE);
                textRemoveMeal.setOnClickListener(v -> {
                    currentMeals.remove(entry);
                    rebuildMealsUI();
                });
                textMealLabel.setTextColor(getResources().getColor(R.color.accent_color));
                textMealLabel.setOnClickListener(v -> editExtraMealLabel(entry, textMealLabel));
            } else {
                textRemoveMeal.setVisibility(View.GONE);
            }
            textMealTime.setOnClickListener(v -> {
                Calendar c = Calendar.getInstance();
                c.setTimeInMillis(entry.timeMillis);
                int hour = c.get(Calendar.HOUR_OF_DAY);
                int minute = c.get(Calendar.MINUTE);
                TimePickerDialog picker = new TimePickerDialog(requireContext(), (view, h, m) -> {
                    Calendar chosen = Calendar.getInstance();
                    chosen.setTimeInMillis(selectedTimeForDialog[0]);
                    chosen.set(Calendar.HOUR_OF_DAY, h);
                    chosen.set(Calendar.MINUTE, m);
                    chosen.set(Calendar.SECOND, 0);
                    chosen.set(Calendar.MILLISECOND, 0);
                    entry.timeMillis = chosen.getTimeInMillis();
                    textMealTime.setText(tf.format(new Date(entry.timeMillis)));
                }, hour, minute, true);
                picker.show();
            });
            editMealContent.addTextChangedListener(new android.text.TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                }

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                }

                @Override
                public void afterTextChanged(android.text.Editable s) {
                    entry.content = s.toString();
                }
            });
            container.addView(itemView);
        }
    }

    private void showImageSourceChooser() {
        if (pendingImageMeal == null) {
            return;
        }
        int currentCount = pendingImageMeal.images != null ? pendingImageMeal.images.size() : 0;
        if (currentCount >= 5) {
            Toast.makeText(requireContext(), "每餐最多允许添加5张图片", Toast.LENGTH_SHORT).show();
            return;
        }
        String[] items = new String[]{"从相册选择", "拍摄照片"};
        new AlertDialog.Builder(requireContext())
                .setItems(items, (dialog, which) -> {
                    if (which == 0) {
                        ActivityResultContracts.PickVisualMedia.VisualMediaType mediaType =
                                (ActivityResultContracts.PickVisualMedia.VisualMediaType)
                                        ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE;
                        PickVisualMediaRequest request = new PickVisualMediaRequest.Builder()
                                .setMediaType(mediaType)
                                .build();
                        pickMealImagesLauncher.launch(request);
                    } else {
                        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                        if (intent.resolveActivity(requireContext().getPackageManager()) != null) {
                            startActivityForResult(intent, REQUEST_CAPTURE_IMAGE);
                        } else {
                            Toast.makeText(requireContext(), "无法启动相机", Toast.LENGTH_SHORT).show();
                        }
                    }
                })
                .show();
    }

    private void editExtraMealLabel(MealEntry entry, TextView labelView) {
        EditText input = new EditText(requireContext());
        input.setText(entry.label);
        input.setSelection(input.getText().length());
        new AlertDialog.Builder(requireContext())
                .setTitle("编辑加餐名称")
                .setView(input)
                .setPositiveButton("确定", (dialog, which) -> {
                    String text = input.getText().toString().trim();
                    if (text.length() > 20) {
                        text = text.substring(0, 20);
                        Toast.makeText(requireContext(), "名称已截断至20个字符", Toast.LENGTH_SHORT).show();
                    }
                    if (!text.isEmpty()) {
                        entry.label = text;
                        labelView.setText(text);
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void loadMealImageFromUrl(MealImage image, ImageView imageView) {
        String url = image.url;
        if (url == null || url.isEmpty()) {
            return;
        }
        new Thread(() -> {
            try {
                InputStream is = new java.net.URL(url).openStream();
                Bitmap bitmap = BitmapFactory.decodeStream(is);
                is.close();
                if (bitmap != null && isAdded()) {
                    image.thumbnail = bitmap;
                    requireActivity().runOnUiThread(() -> imageView.setImageBitmap(bitmap));
                }
            } catch (Exception ignored) {
            }
        }).start();
    }

    private boolean validateMeals() {
        if (currentMeals.size() < 3) {
            Toast.makeText(requireContext(), "请补全早餐、午餐和晚餐的信息", Toast.LENGTH_SHORT).show();
            return false;
        }
        List<MealEntry> fixedMeals = new ArrayList<>();
        for (MealEntry m : currentMeals) {
            if (m.fixed) {
                fixedMeals.add(m);
            }
        }
        if (fixedMeals.size() < 3) {
            Toast.makeText(requireContext(), "必须包含早餐、午餐和晚餐三个固定时段", Toast.LENGTH_SHORT).show();
            return false;
        }
        for (int i = 0; i < fixedMeals.size(); i++) {
            MealEntry a = fixedMeals.get(i);
            for (int j = i + 1; j < fixedMeals.size(); j++) {
                MealEntry b = fixedMeals.get(j);
                if (a.timeMillis == b.timeMillis) {
                    Toast.makeText(requireContext(), "三餐时间不能相同", Toast.LENGTH_SHORT).show();
                    return false;
                }
            }
        }
        return true;
    }

    private String buildMealsJson() {
        JSONArray array = new JSONArray();
        for (MealEntry m : currentMeals) {
            JSONObject obj = new JSONObject();
            try {
                obj.put("label", m.label);
                obj.put("fixed", m.fixed);
                obj.put("time", m.timeMillis);
                obj.put("content", m.content == null ? "" : m.content);
                JSONArray imagesArray = new JSONArray();
                if (m.images != null) {
                    for (MealImage image : m.images) {
                        if (image.url != null && !image.url.isEmpty()) {
                            imagesArray.put(image.url);
                        }
                    }
                }
                obj.put("images", imagesArray);
            } catch (JSONException e) {
            }
            array.put(obj);
        }
        return array.toString();
    }

    private List<MealEntry> parseMealsJson(String json) {
        List<MealEntry> result = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(json);
            for (int i = 0; i < array.length(); i++) {
                JSONObject obj = array.getJSONObject(i);
                MealEntry m = new MealEntry();
                m.label = obj.optString("label");
                m.fixed = obj.optBoolean("fixed", false);
                m.timeMillis = obj.optLong("time", 0L);
                m.content = obj.optString("content");
                String legacyImage = obj.optString("image", null);
                JSONArray imagesArray = obj.optJSONArray("images");
                if (imagesArray != null && imagesArray.length() > 0) {
                    for (int j = 0; j < imagesArray.length(); j++) {
                        String url = imagesArray.optString(j);
                        if (url != null && !url.isEmpty()) {
                            MealImage image = new MealImage();
                            image.url = url;
                            m.images.add(image);
                        }
                    }
                } else if (legacyImage != null && !legacyImage.isEmpty()) {
                    MealImage image = new MealImage();
                    image.url = legacyImage;
                    m.images.add(image);
                }
                result.add(m);
            }
        } catch (JSONException e) {
            return result;
        }
        return result;
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != Activity.RESULT_OK || pendingImageMeal == null) {
            return;
        }
        if (requestCode == REQUEST_PICK_IMAGE && data != null) {
            int currentCount = pendingImageMeal.images != null ? pendingImageMeal.images.size() : 0;
            boolean sizeExceeded = false;
            if (data.getClipData() != null) {
                android.content.ClipData clipData = data.getClipData();
                int count = clipData.getItemCount();
                for (int i = 0; i < count; i++) {
                    if (currentCount >= 5) {
                        sizeExceeded = true;
                        break;
                    }
                    Uri uri = clipData.getItemAt(i).getUri();
                    byte[] compressed = readAndCompressFromUri(uri);
                    if (compressed == null) {
                        Toast.makeText(requireContext(), "读取图片失败", Toast.LENGTH_SHORT).show();
                        continue;
                    }
                    if (compressed.length > 5 * 1024 * 1024) {
                        Toast.makeText(requireContext(), "图片大小不能超过5MB", Toast.LENGTH_SHORT).show();
                        continue;
                    }
                    applyImageToPendingMeal(compressed);
                    currentCount++;
                }
            } else if (data.getData() != null) {
                if (currentCount >= 5) {
                    sizeExceeded = true;
                } else {
                    Uri uri = data.getData();
                    byte[] compressed = readAndCompressFromUri(uri);
                    if (compressed == null) {
                        Toast.makeText(requireContext(), "读取图片失败", Toast.LENGTH_SHORT).show();
                    } else if (compressed.length > 5 * 1024 * 1024) {
                        Toast.makeText(requireContext(), "图片大小不能超过5MB", Toast.LENGTH_SHORT).show();
                    } else {
                        applyImageToPendingMeal(compressed);
                    }
                }
            }
            if (sizeExceeded) {
                Toast.makeText(requireContext(), "每餐最多允许添加5张图片", Toast.LENGTH_SHORT).show();
            }
            pendingImageMeal = null;
        } else if (requestCode == REQUEST_CAPTURE_IMAGE && data != null && data.getExtras() != null) {
            Object obj = data.getExtras().get("data");
            if (obj instanceof Bitmap) {
                Bitmap bitmap = (Bitmap) obj;
                byte[] compressed = compressBitmap(bitmap);
                if (compressed == null) {
                    Toast.makeText(requireContext(), "处理图片失败", Toast.LENGTH_SHORT).show();
                    return;
                }
                if (compressed.length > 5 * 1024 * 1024) {
                    Toast.makeText(requireContext(), "图片大小不能超过5MB", Toast.LENGTH_SHORT).show();
                    return;
                }
                applyImageToPendingMeal(compressed);
                pendingImageMeal = null;
            }
        }
    }

    @Nullable
    private byte[] readAndCompressFromUri(Uri uri) {
        try {
            InputStream is = requireContext().getContentResolver().openInputStream(uri);
            if (is == null) return null;
            Bitmap bitmap = BitmapFactory.decodeStream(is);
            is.close();
            if (bitmap == null) return null;
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

    private void applyImageToPendingMeal(byte[] data) {
        if (pendingImageMeal == null) {
            return;
        }
        if (pendingImageMeal.images == null) {
            pendingImageMeal.images = new ArrayList<>();
        }
        if (pendingImageMeal.images.size() >= 5) {
            Toast.makeText(requireContext(), "每餐最多允许添加5张图片", Toast.LENGTH_SHORT).show();
            return;
        }
        MealImage image = new MealImage();
        image.data = data;
        image.thumbnail = BitmapFactory.decodeByteArray(data, 0, data.length);
        pendingImageMeal.images.add(image);
        rebuildMealsUI();
    }

    private void showImagePreview(MealEntry entry, int imageIndex) {
        if (entry.images == null || imageIndex < 0 || imageIndex >= entry.images.size()) {
            return;
        }
        MealImage image = entry.images.get(imageIndex);
        ImageView imageView = new ImageView(requireContext());
        imageView.setAdjustViewBounds(true);
        imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        Bitmap bitmap = null;
        if (image.thumbnail != null) {
            bitmap = image.thumbnail;
        } else if (image.data != null && image.data.length > 0) {
            bitmap = BitmapFactory.decodeByteArray(image.data, 0, image.data.length);
        }
        if (bitmap != null) {
            imageView.setImageBitmap(bitmap);
        } else if (image.url != null && !image.url.isEmpty()) {
            loadMealImageFromUrl(image, imageView);
        }
        new AlertDialog.Builder(requireContext())
                .setView(imageView)
                .setPositiveButton("关闭", null)
                .show();
    }

    private void showImageOptions(MealEntry entry, int imageIndex) {
        if (entry.images == null || imageIndex < 0 || imageIndex >= entry.images.size()) {
            return;
        }
        String[] options = new String[]{"向左移动", "向右移动", "删除"};
        new AlertDialog.Builder(requireContext())
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        if (imageIndex <= 0) {
                            Toast.makeText(requireContext(), "已在最前面", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        MealImage img = entry.images.remove(imageIndex);
                        entry.images.add(imageIndex - 1, img);
                        rebuildMealsUI();
                    } else if (which == 1) {
                        if (imageIndex >= entry.images.size() - 1) {
                            Toast.makeText(requireContext(), "已在最后面", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        MealImage img = entry.images.remove(imageIndex);
                        entry.images.add(imageIndex + 1, img);
                        rebuildMealsUI();
                    } else if (which == 2) {
                        entry.images.remove(imageIndex);
                        rebuildMealsUI();
                    }
                })
                .show();
    }

    private class DietAdapter extends RecyclerView.Adapter<DietViewHolder> {

        private final List<DietRecord> items = new ArrayList<>();
        private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());

        void setItems(List<DietRecord> list) {
            items.clear();
            if (list != null) {
                items.addAll(list);
            }
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public DietViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_diet_record, parent, false);
            return new DietViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull DietViewHolder holder, int position) {
            DietRecord record = items.get(position);
            holder.bind(record);
        }

        @Override
        public int getItemCount() {
            return items.size();
        }
    }

    private class DietViewHolder extends RecyclerView.ViewHolder {

        private final ImageView imageFood;
        private final TextView textTitle;
        private final TextView textDate;
        private final TextView textRemark;

        DietViewHolder(@NonNull View itemView) {
            super(itemView);
            imageFood = itemView.findViewById(R.id.imageFood);
            textTitle = itemView.findViewById(R.id.textTitle);
            textDate = itemView.findViewById(R.id.textDate);
            textRemark = itemView.findViewById(R.id.textRemark);
        }

        void bind(DietRecord record) {
            long dateValue = record.getDate();
            String remark = record.getRemark();

            List<MealEntry> meals = new ArrayList<>();
            String mealsJson = record.getMeals();
            if (mealsJson != null && !mealsJson.isEmpty()) {
                meals.addAll(parseMealsJson(mealsJson));
            }

            boolean isWeekly = currentFilter == 1;

            if (isWeekly) {
                textTitle.setVisibility(View.VISIBLE);
                textTitle.setText("周记录");
                if (dateValue > 0) {
                    Calendar c = Calendar.getInstance();
                    c.setTimeInMillis(dateValue);
                    Date startDate = c.getTime();
                    c.add(Calendar.DAY_OF_YEAR, 6);
                    Date endDate = c.getTime();
                    SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
                    textDate.setText(df.format(startDate) + " 至 " + df.format(endDate));
                } else {
                    textDate.setText("");
                }
                imageFood.setVisibility(View.GONE);
                textRemark.setVisibility(View.VISIBLE);
                textRemark.setText(remark != null ? remark : "");
            } else {
                textTitle.setVisibility(View.GONE);
                if (dateValue > 0) {
                    textDate.setText(new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date(dateValue)));
                } else {
                    textDate.setText("");
                }
                MealImage coverImage = null;
                if (!meals.isEmpty()) {
                    for (MealEntry m : meals) {
                        if (m.images != null && !m.images.isEmpty()) {
                            coverImage = m.images.get(0);
                            break;
                        }
                    }
                }

                if (coverImage != null && coverImage.url != null && !coverImage.url.isEmpty()) {
                    imageFood.setVisibility(View.VISIBLE);
                    imageFood.setImageDrawable(null);
                    loadMealImageFromUrl(coverImage, imageFood);
                } else {
                    imageFood.setVisibility(View.GONE);
                }

                String displayText;
                if (remark != null && !remark.trim().isEmpty()) {
                    displayText = remark;
                } else if (!meals.isEmpty()) {
                    StringBuilder sb = new StringBuilder();
                    int count = 0;
                    for (MealEntry m : meals) {
                        if (m.content != null && !m.content.trim().isEmpty()) {
                            if (sb.length() > 0) {
                                sb.append("  ");
                            }
                            String content = m.content.trim();
                            if (content.length() > 20) {
                                content = content.substring(0, 20) + "...";
                            }
                            sb.append(m.label).append("：").append(content);
                            count++;
                            if (count >= 3) {
                                break;
                            }
                        }
                    }
                    if (sb.length() == 0) {
                        displayText = "今日饮食已记录";
                    } else {
                        displayText = sb.toString();
                    }
                } else {
                    displayText = "今日饮食已记录";
                }
                textRemark.setVisibility(View.VISIBLE);
                textRemark.setText(displayText);
            }

            if (currentFilter == 1) {
                itemView.setOnClickListener(v -> Toast.makeText(requireContext(), "每周视图为自动统计，只能查看", Toast.LENGTH_SHORT).show());
                itemView.setOnLongClickListener(v -> {
                    Toast.makeText(requireContext(), "每周视图为自动统计，不能删除", Toast.LENGTH_SHORT).show();
                    return true;
                });
            } else {
                itemView.setOnClickListener(v -> showEditDialog(record));
                itemView.setOnLongClickListener(v -> {
                    AlertDialog dialog = new AlertDialog.Builder(requireContext())
                            .setTitle("删除饮食记录")
                            .setMessage("确定删除这条记录吗？")
                            .setPositiveButton("删除", (d, which) -> {
                                record.deleteInBackground().subscribe(new Observer<cn.leancloud.types.LCNull>() {
                                    @Override
                                    public void onSubscribe(Disposable d) {
                                    }

                                    @Override
                                    public void onNext(cn.leancloud.types.LCNull lcNull) {
                                        if (isAdded()) {
                                            Toast.makeText(requireContext(), "已删除", Toast.LENGTH_SHORT).show();
                                            loadDiets();
                                        }
                                    }

                                    @Override
                                    public void onError(Throwable e) {
                                        if (isAdded()) {
                                            Toast.makeText(requireContext(), "删除失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                                        }
                                    }

                                    @Override
                                    public void onComplete() {
                                    }
                                });
                            })
                            .setNegativeButton("取消", null)
                            .create();
                    if (dialog.getWindow() != null) {
                        dialog.getWindow().setBackgroundDrawableResource(R.drawable.bg_dialog_round_white);
                    }
                    dialog.setOnShowListener(dlg -> {
                        TextView title = dialog.findViewById(androidx.appcompat.R.id.alertTitle);
                        if (title == null) {
                            title = dialog.findViewById(android.R.id.title);
                        }
                        TextView message = dialog.findViewById(android.R.id.message);
                        if (title != null) {
                            title.setGravity(Gravity.CENTER_HORIZONTAL);
                            title.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
                        }
                        if (message != null) {
                            message.setGravity(Gravity.CENTER_HORIZONTAL);
                            message.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
                        }
                    });
                    dialog.show();
                    return true;
                });
            }
        }
    }
}
