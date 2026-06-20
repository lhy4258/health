package com.example.health.ui.sport;

import android.Manifest;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.widget.NestedScrollView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.example.health.R;
import com.example.health.data.model.SportRecord;
import com.example.health.data.model.SportType;
import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.ValueFormatter;
import com.google.android.material.chip.ChipGroup;

import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import cn.leancloud.LCQuery;
import cn.leancloud.LCUser;
import io.reactivex.Observer;
import io.reactivex.disposables.Disposable;

/**
 * 运动详情页（仪表板），展示指定运动类型的趋势折线图、对比柱状图、运动介绍与训练计划。
 * 使用 MPAndroidChart 绘制图表，支持长按保存图表为图片。
 */
public class SportDetailActivity extends AppCompatActivity {

    private static final int PERMISSION_REQUEST_CODE = 1001;

    private TextView sportTitleTextView;
    private TextView btnHistory;
    private LineChart trendLineChart;
    private BarChart comparisonBarChart;
    private View startSportButton;
    private ChipGroup chipGroupTimeRange;
    private NestedScrollView nestedScrollView;
    private android.widget.ProgressBar loadingIndicator;
    private SwipeRefreshLayout swipeRefreshLayout;
    
    private View cvTrend;
    private View cvComparison;
    
    private TextView tvDateRangeLine;
    private TextView tvDateRangeBar;
    private TextView tvSportIntro;
    private TextView tvSportPlan;

    private SportType currentSportType;
    private int daysToLoad = 7; // Default to week
    
    // For saving functionality
    private View pendingSaveView;
    private String pendingSaveName;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sport_dashboard);

        initViews();
        setupCharts();
        setupFilters();

        String typeName = getIntent().getStringExtra("SPORT_TYPE");
        if (typeName != null) {
            try {
                currentSportType = SportType.valueOf(typeName);
                if (sportTitleTextView != null) {
                    sportTitleTextView.setText(currentSportType.getDisplayName());
                }
                setTitle(currentSportType.getDisplayName());
                updateSportDescription(currentSportType);
            } catch (IllegalArgumentException e) {
                Toast.makeText(this, "无效的运动类型", Toast.LENGTH_SHORT).show();
                finish();
                return;
            }
        } else {
            Toast.makeText(this, "未指定运动类型", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        setupInteractions();
        
        loadSportData(false);
    }

    private void initViews() {
        swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout);
        sportTitleTextView = findViewById(R.id.sportTitleTextView);
        
        View btnBack = findViewById(R.id.btnBack);
        if (btnBack != null) {
            btnBack.setOnClickListener(v -> finish());
        }

        btnHistory = findViewById(R.id.btnHistory);
        trendLineChart = findViewById(R.id.durationLineChart);
        comparisonBarChart = findViewById(R.id.comparisonBarChart);
        startSportButton = findViewById(R.id.startSportButton);
        chipGroupTimeRange = findViewById(R.id.chipGroupTimeRange);
        nestedScrollView = findViewById(R.id.nestedScrollView);
        loadingIndicator = findViewById(R.id.loadingIndicator);
        
        cvTrend = findViewById(R.id.cvTrend);
        cvComparison = findViewById(R.id.cvComparison);
        
        tvDateRangeLine = findViewById(R.id.tvDateRangeLine);
        tvDateRangeBar = findViewById(R.id.tvDateRangeBar);
        tvSportIntro = findViewById(R.id.tvSportIntro);
        tvSportPlan = findViewById(R.id.tvSportPlan);

        if (swipeRefreshLayout != null) {
            swipeRefreshLayout.setOnRefreshListener(() -> loadSportData(true));
        }
    }

    private void setupInteractions() {
        btnHistory.setOnClickListener(v -> {
            Intent intent = new Intent(SportDetailActivity.this, SportHistoryActivity.class);
            if (currentSportType != null) {
                intent.putExtra("SPORT_TYPE", currentSportType.name());
            }
            intent.putExtra("DAYS_TO_LOAD", daysToLoad);
            startActivity(intent);
        });

        if (startSportButton.getBackground() != null) {
            startSportButton.getBackground().setAlpha(204); // 255 * 0.8
        }
        
        startSportButton.setOnClickListener(v -> startRecording());
    }

    private void updateSportDescription(SportType type) {
        if (type == null) return;

        String intro = "";
        String plan = "";

        switch (type) {
            case WALKING:
                intro = "步行是一种低强度有氧运动，适合所有年龄段。它可以改善心血管健康，增强肌肉力量，并有助于维持健康的体重。长期坚持步行还能改善心情，减轻压力。";
                plan = "初学者：每天步行30分钟，每周5次，保持舒适的配速。\n\n进阶：每天快走45-60分钟，每周5-6次，尝试在不同地形行走以增加难度。";
                break;
            case RUNNING:
                intro = "跑步是一种高强度有氧运动，能有效燃烧卡路里，增强心肺功能，强健骨骼。跑步还能促进新陈代谢，提高身体耐力，是减脂塑形的理想选择。";
                plan = "初学者：采用跑走结合的方式，跑1分钟走2分钟，循环20分钟，每周3次。\n\n进阶：每周累计跑量20-30公里，包含一次长距离慢跑（LSD）和一次间歇跑。";
                break;
            case CYCLING:
                intro = "骑行是一种低冲击力的有氧运动，能锻炼下肢肌肉，提高心肺耐力，同时减少对关节的压力。它也是一种环保的出行方式。";
                plan = "初学者：每次骑行30-45分钟，保持平稳的踏频（约60-80rpm），每周2-3次。\n\n进阶：每次骑行1小时以上，尝试包含爬坡路段，提高踏频至90rpm左右。";
                break;
            case FITNESS:
                intro = "健身涵盖多种锻炼方式，包括力量训练、柔韧性训练等，旨在提高力量、耐力、灵活性和身体成分。它可以根据个人目标量身定制。";
                plan = "初学者：进行全身性力量训练，掌握正确动作模式，每周2-3次，每次45分钟。\n\n进阶：采用分部位训练（如推/拉/腿分化），每周4-5次，每次60-90分钟，增加负重。";
                break;
            default:
                intro = "暂无介绍";
                plan = "暂无计划";
                break;
        }

        if (tvSportIntro != null) tvSportIntro.setText(intro);
        if (tvSportPlan != null) tvSportPlan.setText(plan);
    }


    private void setupCharts() {
        setupLineChart();
        setupLongPressSave(trendLineChart, "Trend_Chart");

        setupBarChart();
        setupLongPressSave(comparisonBarChart, "Comparison_Chart");
    }

    private void setupLineChart() {
        trendLineChart.getDescription().setEnabled(false);
        trendLineChart.setTouchEnabled(true);
        trendLineChart.setDragEnabled(true);
        trendLineChart.setScaleEnabled(true);
        trendLineChart.setPinchZoom(true);
        trendLineChart.setDrawGridBackground(false);
        
        XAxis xAxis = trendLineChart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setDrawGridLines(false);
        xAxis.setGranularity(1f);
        xAxis.setTextColor(ContextCompat.getColor(this, R.color.secondary_text_color));
        xAxis.setDrawAxisLine(true);
        xAxis.setAxisLineColor(ContextCompat.getColor(this, R.color.secondary_text_color));
        
        YAxis leftAxis = trendLineChart.getAxisLeft();
        leftAxis.setDrawGridLines(true);
        leftAxis.setGridColor(Color.parseColor("#E0E0E0"));
        leftAxis.setAxisMinimum(0f);
        leftAxis.setTextColor(ContextCompat.getColor(this, R.color.secondary_text_color));
        leftAxis.setDrawAxisLine(true);
        leftAxis.setAxisLineColor(ContextCompat.getColor(this, R.color.secondary_text_color));
        leftAxis.setValueFormatter(new ValueFormatter() {
            @Override
            public String getFormattedValue(float value) {
                return String.format(Locale.getDefault(), "%.0f 分钟", value);
            }
        });
        leftAxis.setGranularity(10f);
        leftAxis.setGranularityEnabled(true);
        leftAxis.setAxisMaximum(60f);
        leftAxis.setLabelCount(7, true);
        
        trendLineChart.getAxisRight().setEnabled(false);
        trendLineChart.getLegend().setEnabled(false);
        trendLineChart.animateX(1000);
    }

    private void setupBarChart() {
        comparisonBarChart.getDescription().setEnabled(false);
        comparisonBarChart.setTouchEnabled(true);
        comparisonBarChart.setDragEnabled(true);
        comparisonBarChart.setScaleEnabled(true);
        comparisonBarChart.setPinchZoom(true);
        comparisonBarChart.setDrawGridBackground(false);

        XAxis xAxis = comparisonBarChart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setDrawGridLines(false);
        xAxis.setGranularity(1f);
        xAxis.setTextColor(ContextCompat.getColor(this, R.color.secondary_text_color));
        xAxis.setDrawLabels(true);
        xAxis.setDrawAxisLine(true);
        xAxis.setAxisLineColor(ContextCompat.getColor(this, R.color.secondary_text_color));

        YAxis leftAxis = comparisonBarChart.getAxisLeft();
        leftAxis.setDrawGridLines(true);
        leftAxis.setGridColor(Color.parseColor("#E0E0E0"));
        leftAxis.setAxisMinimum(0f);
        leftAxis.setTextColor(ContextCompat.getColor(this, R.color.secondary_text_color));
        leftAxis.setDrawAxisLine(true);
        leftAxis.setAxisLineColor(ContextCompat.getColor(this, R.color.secondary_text_color));
        leftAxis.setValueFormatter(new ValueFormatter() {
            @Override
            public String getFormattedValue(float value) {
                return String.format(Locale.getDefault(), "%.0f 分钟", value);
            }
        });
        leftAxis.setGranularity(10f);
        leftAxis.setGranularityEnabled(true);
        leftAxis.setAxisMaximum(60f);
        leftAxis.setLabelCount(7, true);

        comparisonBarChart.getAxisRight().setEnabled(false);
        comparisonBarChart.getLegend().setEnabled(false);
        comparisonBarChart.animateY(1000);
    }

    private void setupLongPressSave(View view, String name) {
        view.setOnLongClickListener(v -> {
            // @deprecated Vibration functionality removed as per new requirements
            // Vibrator logic has been removed to comply with the new design specification.
            // Feedback is now provided solely via the save success Toast.
            
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                        != PackageManager.PERMISSION_GRANTED) {
                    pendingSaveView = view;
                    pendingSaveName = name;
                    ActivityCompat.requestPermissions(this,
                            new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                            PERMISSION_REQUEST_CODE);
                    return true;
                }
            }
            saveViewToGallery(view, name);
            return true;
        });
    }

    private void setupFilters() {
        chipGroupTimeRange.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.chipWeek) {
                daysToLoad = 7;
            } else if (checkedId == R.id.chipMonth) {
                daysToLoad = 30;
            } else if (checkedId == R.id.chipYear) {
                daysToLoad = 365;
            }
            loadSportData();
        });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                if (pendingSaveView != null && pendingSaveName != null) {
                    saveViewToGallery(pendingSaveView, pendingSaveName);
                    pendingSaveView = null;
                    pendingSaveName = null;
                }
            } else {
                Toast.makeText(this, "需要存储权限以导出图表", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void saveViewToGallery(View view, String name) {
        Bitmap bitmap;
        if (view instanceof LineChart) {
            bitmap = ((LineChart) view).getChartBitmap();
        } else if (view instanceof BarChart) {
            bitmap = ((BarChart) view).getChartBitmap();
        } else {
            bitmap = Bitmap.createBitmap(view.getWidth(), view.getHeight(), Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            view.draw(canvas);
        }

        if (bitmap == null) return;

        ContentValues values = new ContentValues();
        values.put(MediaStore.Images.Media.TITLE, name + "_" + System.currentTimeMillis());
        values.put(MediaStore.Images.Media.MIME_TYPE, "image/png");

        try {
            Uri uri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
            if (uri != null) {
                OutputStream outStream = getContentResolver().openOutputStream(uri);
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, outStream);
                if (outStream != null) {
                    outStream.close();
                }
                Toast.makeText(this, "图表已保存到相册", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "保存失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadSportData();
    }

    private void startRecording() {
        Intent intent = new Intent(this, SportRecordingActivity.class);
        if (currentSportType != null) {
            intent.putExtra("SPORT_TYPE", currentSportType.name());
        }
        startActivity(intent);
    }

    private void loadSportData() {
        loadSportData(false);
    }

    private void loadSportData(boolean fromRefresh) {
        LCUser currentUser = LCUser.currentUser();
        if (currentUser == null) {
            if (fromRefresh && swipeRefreshLayout != null) {
                swipeRefreshLayout.setRefreshing(false);
            }
            return;
        }

        if (!fromRefresh) {
            showLoading();
        }

        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.DAY_OF_YEAR, -daysToLoad);
        Date startDate = calendar.getTime();
        Date endDate = new Date(); // Now

        // Update Date Range Text
        SimpleDateFormat rangeFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        String rangeText = rangeFormat.format(startDate) + " 至 " + rangeFormat.format(endDate);
        tvDateRangeLine.setText(rangeText);
        tvDateRangeBar.setText(rangeText);

        LCQuery<SportRecord> query = new LCQuery<>("SportRecord");
        query.whereEqualTo(SportRecord.ATTR_USER, currentUser);
        if (currentSportType != null) {
            query.whereEqualTo(SportRecord.ATTR_TYPE, currentSportType.name());
        }
        query.whereGreaterThanOrEqualTo(SportRecord.ATTR_START_TIME, startDate.getTime());
        query.whereLessThanOrEqualTo(SportRecord.ATTR_START_TIME, endDate.getTime());
        query.orderByAscending(SportRecord.ATTR_START_TIME);

        query.findInBackground().subscribe(new Observer<List<SportRecord>>() {
            @Override
            public void onSubscribe(Disposable d) {}

            @Override
            public void onNext(List<SportRecord> records) {
                if (!fromRefresh) {
                    hideLoading();
                }
                if (records == null || records.isEmpty()) {
                    showNoData();
                } else {
                    showData();
                    updateLineChart(records);
                    updateBarChart(records);
                    
                    List<SportRecord> reversed = new ArrayList<>(records);
                    java.util.Collections.reverse(reversed);
                }
            }

            @Override
            public void onError(Throwable e) {
                if (!fromRefresh) {
                    hideLoading();
                }
                Toast.makeText(SportDetailActivity.this, "加载数据失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onComplete() {}
        });

        if (fromRefresh && swipeRefreshLayout != null) {
            swipeRefreshLayout.setRefreshing(false);
        }
    }

    private void showLoading() {
        if (loadingIndicator != null) loadingIndicator.setVisibility(View.VISIBLE);
        if (nestedScrollView != null) nestedScrollView.setVisibility(View.GONE);
    }

    private void hideLoading() {
        if (loadingIndicator != null) loadingIndicator.setVisibility(View.GONE);
    }

    private void showNoData() {
        setLineChartEmptyState();
        setBarChartEmptyState();
        if (nestedScrollView != null) nestedScrollView.setVisibility(View.VISIBLE);
    }

    private void setLineChartEmptyState() {
        if (trendLineChart == null) return;
        List<Entry> entries = new ArrayList<>();
        entries.add(new Entry(0f, 0f));
        LineDataSet dataSet = new LineDataSet(entries, "");
        dataSet.setDrawCircles(false);
        dataSet.setColor(Color.TRANSPARENT);
        dataSet.setDrawValues(false);
        dataSet.setHighlightEnabled(false);
        dataSet.setDrawFilled(false);
        LineData lineData = new LineData(dataSet);
        trendLineChart.setData(lineData);

        XAxis xAxis = trendLineChart.getXAxis();
        xAxis.setLabelCount(0, false);
        xAxis.setValueFormatter(new ValueFormatter() {
            @Override
            public String getFormattedValue(float value) {
                return "";
            }
        });

        trendLineChart.invalidate();
    }

    private void setBarChartEmptyState() {
        if (comparisonBarChart == null) return;
        List<BarEntry> entries = new ArrayList<>();
        entries.add(new BarEntry(0f, 0f));
        BarDataSet dataSet = new BarDataSet(entries, "");
        dataSet.setColor(Color.TRANSPARENT);
        dataSet.setDrawValues(false);
        dataSet.setHighlightEnabled(false);
        BarData barData = new BarData(dataSet);
        barData.setBarWidth(0.5f);
        comparisonBarChart.setData(barData);

        XAxis xAxis = comparisonBarChart.getXAxis();
        xAxis.setLabelCount(0, false);
        xAxis.setValueFormatter(new ValueFormatter() {
            @Override
            public String getFormattedValue(float value) {
                return "";
            }
        });

        comparisonBarChart.invalidate();
    }

    private void showData() {
        if (cvTrend != null) cvTrend.setVisibility(View.VISIBLE);
        if (cvComparison != null) cvComparison.setVisibility(View.VISIBLE);
        if (nestedScrollView != null) nestedScrollView.setVisibility(View.VISIBLE);
    }

    private void updateLineChart(List<SportRecord> records) {
        List<Entry> entries = new ArrayList<>();
        List<Long> timestamps = new ArrayList<>();
        float maxMinutes = 0f;
        
        for (int i = 0; i < records.size(); i++) {
            SportRecord record = records.get(i);
            float minutes = (float) record.getDuration() / 60f;
            entries.add(new Entry(i, minutes));
            if (minutes > maxMinutes) {
                maxMinutes = minutes;
            }
            timestamps.add(record.getStartTime());
        }

        LineDataSet dataSet;
        if (trendLineChart.getData() != null && trendLineChart.getData().getDataSetCount() > 0) {
            dataSet = (LineDataSet) trendLineChart.getData().getDataSetByIndex(0);
            dataSet.setValues(entries);
            trendLineChart.getData().notifyDataChanged();
            trendLineChart.notifyDataSetChanged();
        } else {
            dataSet = new LineDataSet(entries, "运动时长");
            dataSet.setMode(LineDataSet.Mode.CUBIC_BEZIER);
            dataSet.setCubicIntensity(0.2f);
            dataSet.setDrawFilled(true);
            dataSet.setDrawCircles(true);
            dataSet.setLineWidth(2f);
            dataSet.setCircleRadius(4f);
            dataSet.setCircleColor(ContextCompat.getColor(this, R.color.primary_color));
            dataSet.setHighLightColor(ContextCompat.getColor(this, R.color.accent_color));
            dataSet.setColor(ContextCompat.getColor(this, R.color.primary_color));
            dataSet.setFillColor(ContextCompat.getColor(this, R.color.primary_color));
            dataSet.setFillAlpha(100);
            dataSet.setDrawValues(false);
            
            Drawable drawable = ContextCompat.getDrawable(this, R.drawable.fade_primary);
            dataSet.setFillDrawable(drawable);

            LineData lineData = new LineData(dataSet);
            trendLineChart.setData(lineData);
        }

        // X Axis Labels
        XAxis xAxis = trendLineChart.getXAxis();
        xAxis.setLabelCount(Math.min(records.size(), 5), false);
        xAxis.setValueFormatter(new ValueFormatter() {
            private final SimpleDateFormat mFormat = new SimpleDateFormat("MM-dd", Locale.getDefault());
            @Override
            public String getFormattedValue(float value) {
                int index = (int) value;
                if (index >= 0 && index < timestamps.size()) {
                    return mFormat.format(new Date(timestamps.get(index)));
                }
                return "";
            }
        });

        trendLineChart.fitScreen();
        YAxis leftAxis = trendLineChart.getAxisLeft();
        float axisMax = maxMinutes <= 0f ? 60f : Math.max(30f, (float) (Math.ceil(maxMinutes / 10f) * 10f));
        leftAxis.setAxisMaximum(axisMax);
        leftAxis.setLabelCount((int) (axisMax / 10f) + 1, true);
        trendLineChart.animateX(1000);
        trendLineChart.invalidate();
    }

    private void updateBarChart(List<SportRecord> records) {
        Map<String, Float> dailyDuration = new HashMap<>();
        List<String> sortedDates = new ArrayList<>();
        SimpleDateFormat dayFormat = new SimpleDateFormat("MM-dd", Locale.getDefault());

        for (SportRecord record : records) {
            String day = dayFormat.format(new Date(record.getStartTime()));
            float duration = (float) record.getDuration() / 60f;
            if (dailyDuration.containsKey(day)) {
                dailyDuration.put(day, dailyDuration.get(day) + duration);
            } else {
                dailyDuration.put(day, duration);
                sortedDates.add(day);
            }
        }

        List<BarEntry> entries = new ArrayList<>();
        for (int i = 0; i < sortedDates.size(); i++) {
            entries.add(new BarEntry(i, dailyDuration.get(sortedDates.get(i))));
        }

        BarDataSet dataSet;
        if (comparisonBarChart.getData() != null && comparisonBarChart.getData().getDataSetCount() > 0) {
            dataSet = (BarDataSet) comparisonBarChart.getData().getDataSetByIndex(0);
            dataSet.setValues(entries);
            comparisonBarChart.getData().notifyDataChanged();
            comparisonBarChart.notifyDataSetChanged();
        } else {
            dataSet = new BarDataSet(entries, "每日时长");
            dataSet.setColor(ContextCompat.getColor(this, R.color.primary_color));
            dataSet.setDrawValues(false);
            
            BarData barData = new BarData(dataSet);
            barData.setBarWidth(0.5f);
            comparisonBarChart.setData(barData);
        }

        XAxis xAxis = comparisonBarChart.getXAxis();
        xAxis.setLabelCount(Math.min(sortedDates.size(), 7), false);
        xAxis.setValueFormatter(new ValueFormatter() {
            @Override
            public String getFormattedValue(float value) {
                int index = (int) value;
                if (index >= 0 && index < sortedDates.size()) {
                    return sortedDates.get(index);
                }
                return "";
            }
        });

        comparisonBarChart.fitScreen();
        comparisonBarChart.animateY(1000);
        comparisonBarChart.invalidate();
    }

}
