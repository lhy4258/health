package com.example.health.ui;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.AbsoluteSizeSpan;
import android.text.style.ForegroundColorSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.example.health.R;
import com.example.health.data.model.SportRecord;
import com.example.health.data.model.SportType;
import com.example.health.ui.sport.SportDetailActivity;
import com.example.health.ui.widget.AnnularHeatMapView;
import com.google.android.material.button.MaterialButtonToggleGroup;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
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
 * 运动 Tab 首页，展示四类运动（步行/跑步/骑行/健身）的累计时长及环形热力图。
 * 热力图支持日/周/月三种时间维度切换，上半环显示 0-12 时，下半环显示 12-24 时。
 * 数据带 60 秒缓存，减少重复请求。
 */
public class SportFragment extends Fragment {

    private static final long CACHE_VALID_DURATION_MS = 60 * 1000;

    private TextView tvDurationWalk;
    private TextView tvDurationRun;
    private TextView tvDurationRide;
    private TextView tvDurationFitness;
    
    private AnnularHeatMapView heatMapAM;
    private AnnularHeatMapView heatMapPM;
    private MaterialButtonToggleGroup toggleGroupTime;
    private com.google.android.material.button.MaterialButton btnDaily;
    private com.google.android.material.button.MaterialButton btnWeekly;
    private com.google.android.material.button.MaterialButton btnMonthly;
    private View progressHeatMap;
    
    private int currentHeatMapMode = 0; // 0: Daily, 1: Weekly, 2: Monthly
    private SwipeRefreshLayout swipeRefreshLayout;
    private List<SportRecord> cachedSummaryRecords;
    private long summaryCacheTime;
    private Map<Integer, List<SportRecord>> cachedHeatMapRecords = new HashMap<>();
    private Map<Integer, Long> heatMapCacheTime = new HashMap<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_sport, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        swipeRefreshLayout = view.findViewById(R.id.swipeRefreshLayout);
        if (swipeRefreshLayout != null) {
            swipeRefreshLayout.setOnRefreshListener(() -> {
                loadSummaryData(true);
                loadHeatMapData(true);
            });
        }

        tvDurationWalk = view.findViewById(R.id.tv_duration_walk);
        tvDurationRun = view.findViewById(R.id.tv_duration_run);
        tvDurationRide = view.findViewById(R.id.tv_duration_ride);
        tvDurationFitness = view.findViewById(R.id.tv_duration_fitness);
        progressHeatMap = view.findViewById(R.id.progressHeatMap);

        // Initialize Navigation Listeners
        setupNavigation(view.findViewById(R.id.nav_walk), SportType.WALKING);
        setupNavigation(view.findViewById(R.id.nav_run), SportType.RUNNING);
        setupNavigation(view.findViewById(R.id.nav_ride), SportType.CYCLING);
        setupNavigation(view.findViewById(R.id.nav_fitness), SportType.FITNESS);
        
        heatMapAM = view.findViewById(R.id.heatMapAM);
        heatMapPM = view.findViewById(R.id.heatMapPM);
        heatMapAM.setStartHour(0);
        heatMapPM.setStartHour(12);
        
        toggleGroupTime = view.findViewById(R.id.toggleGroupTime);
        btnDaily = view.findViewById(R.id.btnDaily);
        btnWeekly = view.findViewById(R.id.btnWeekly);
        btnMonthly = view.findViewById(R.id.btnMonthly);

        if (btnDaily != null) {
            btnDaily.setSingleLine(false);
            btnDaily.setMaxLines(3);
            btnDaily.setEllipsize(null);
        }
        if (btnWeekly != null) {
            btnWeekly.setSingleLine(false);
            btnWeekly.setMaxLines(3);
            btnWeekly.setEllipsize(null);
        }
        if (btnMonthly != null) {
            btnMonthly.setSingleLine(false);
            btnMonthly.setMaxLines(3);
            btnMonthly.setEllipsize(null);
        }
        toggleGroupTime.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (isChecked) {
                if (checkedId == R.id.btnDaily) {
                    currentHeatMapMode = 0;
                } else if (checkedId == R.id.btnWeekly) {
                    currentHeatMapMode = 1;
                } else if (checkedId == R.id.btnMonthly) {
                    currentHeatMapMode = 2;
                }
                loadHeatMapData();
            }
        });

        updateTimeLabels();

        loadSummaryData();
        loadHeatMapData();
    }

    private void setupNavigation(View navItem, SportType type) {
        if (navItem == null) return;
        navItem.setOnClickListener(v -> {
            try {
                Intent intent = new Intent(requireContext(), SportDetailActivity.class);
                intent.putExtra("SPORT_TYPE", type.name());
                startActivity(intent);
            } catch (Exception e) {
                e.printStackTrace();
                Toast.makeText(requireContext(), "无法打开运动详情页: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }
    
    @Override
    public void onResume() {
        super.onResume();
        updateTimeLabels();
        loadSummaryData();
        loadHeatMapData();
    }

    private void loadSummaryData() {
        loadSummaryData(false);
    }

    private void loadSummaryData(boolean fromRefresh) {
        LCUser currentUser = LCUser.currentUser();
        if (currentUser == null) {
            if (fromRefresh && swipeRefreshLayout != null) {
                swipeRefreshLayout.setRefreshing(false);
            }
            return;
        }

        if (!fromRefresh && cachedSummaryRecords != null && System.currentTimeMillis() - summaryCacheTime < CACHE_VALID_DURATION_MS) {
            if (isAdded()) {
                calculateSummary(cachedSummaryRecords);
            }
            return;
        }

        LCQuery<SportRecord> query = new LCQuery<>("SportRecord");
        query.whereEqualTo(SportRecord.ATTR_USER, currentUser);
        
        query.findInBackground().subscribe(new Observer<List<SportRecord>>() {
            @Override
            public void onSubscribe(Disposable d) {}

            @Override
            public void onNext(List<SportRecord> records) {
                cachedSummaryRecords = records != null ? new ArrayList<>(records) : new ArrayList<>();
                summaryCacheTime = System.currentTimeMillis();
                if (isAdded()) {
                    calculateSummary(cachedSummaryRecords);
                }
                if (fromRefresh && swipeRefreshLayout != null) {
                    swipeRefreshLayout.setRefreshing(false);
                }
            }

            @Override
            public void onError(Throwable e) {
                // Handle error silently or log
                if (fromRefresh && swipeRefreshLayout != null) {
                    swipeRefreshLayout.setRefreshing(false);
                }
            }

            @Override
            public void onComplete() {}
        });
    }

    private void calculateSummary(List<SportRecord> records) {
        Map<String, Long> durationMap = new HashMap<>();
        durationMap.put(SportType.WALKING.name(), 0L);
        durationMap.put(SportType.RUNNING.name(), 0L);
        durationMap.put(SportType.CYCLING.name(), 0L);
        durationMap.put(SportType.FITNESS.name(), 0L);

        for (SportRecord record : records) {
            String type = record.getType();
            if (type != null && durationMap.containsKey(type)) {
                durationMap.put(type, durationMap.get(type) + record.getDuration());
            }
        }

        updateDurationText(tvDurationWalk, durationMap.get(SportType.WALKING.name()));
        updateDurationText(tvDurationRun, durationMap.get(SportType.RUNNING.name()));
        updateDurationText(tvDurationRide, durationMap.get(SportType.CYCLING.name()));
        updateDurationText(tvDurationFitness, durationMap.get(SportType.FITNESS.name()));
    }

    private void updateDurationText(TextView textView, Long seconds) {
        if (textView != null && seconds != null) {
            long minutes = seconds / 60;
            textView.setText(minutes + " 分钟");
        }
    }
    
    private void loadHeatMapData() {
        loadHeatMapData(false);
    }

    private void loadHeatMapData(boolean fromRefresh) {
        LCUser currentUser = LCUser.currentUser();
        if (currentUser == null) {
            if (progressHeatMap != null) {
                progressHeatMap.setVisibility(View.GONE);
            }
            if (fromRefresh && swipeRefreshLayout != null) {
                swipeRefreshLayout.setRefreshing(false);
            }
            return;
        }

        if (progressHeatMap != null) {
            progressHeatMap.setVisibility(View.VISIBLE);
        }

        final int modeForQuery = currentHeatMapMode;

        if (!fromRefresh) {
            Long cacheTime = heatMapCacheTime.get(modeForQuery);
            List<SportRecord> cached = cachedHeatMapRecords.get(modeForQuery);
            if (cacheTime != null && cached != null && System.currentTimeMillis() - cacheTime < CACHE_VALID_DURATION_MS) {
                if (isAdded() && modeForQuery == currentHeatMapMode) {
                    processHeatMapData(cached);
                }
                if (progressHeatMap != null) {
                    progressHeatMap.setVisibility(View.GONE);
                }
                return;
            }
        }

        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);

        if (modeForQuery == 1) {
            calendar.set(Calendar.DAY_OF_WEEK, calendar.getFirstDayOfWeek());
        } else if (modeForQuery == 2) {
            calendar.set(Calendar.DAY_OF_MONTH, 1);
        }

        Date startDate = calendar.getTime();

        LCQuery<SportRecord> query = new LCQuery<>("SportRecord");
        query.whereEqualTo(SportRecord.ATTR_USER, currentUser);
        query.whereGreaterThanOrEqualTo(SportRecord.ATTR_START_TIME, startDate.getTime());

        query.findInBackground().subscribe(new Observer<List<SportRecord>>() {
            @Override
            public void onSubscribe(Disposable d) {}

            @Override
            public void onNext(List<SportRecord> records) {
                List<SportRecord> data = records != null ? new ArrayList<>(records) : new ArrayList<>();
                cachedHeatMapRecords.put(modeForQuery, data);
                heatMapCacheTime.put(modeForQuery, System.currentTimeMillis());
                if (isAdded() && modeForQuery == currentHeatMapMode) {
                    processHeatMapData(data);
                }
                if (fromRefresh && swipeRefreshLayout != null) {
                    swipeRefreshLayout.setRefreshing(false);
                }
                if (progressHeatMap != null) {
                    progressHeatMap.setVisibility(View.GONE);
                }
            }

            @Override
            public void onError(Throwable e) {
                e.printStackTrace();
                if (isAdded() && modeForQuery == currentHeatMapMode) {
                    processHeatMapData(new ArrayList<>());
                }
                if (fromRefresh && swipeRefreshLayout != null) {
                    swipeRefreshLayout.setRefreshing(false);
                }
                if (progressHeatMap != null) {
                    progressHeatMap.setVisibility(View.GONE);
                }
            }

            @Override
            public void onComplete() {}
        });
    }

    private void updateTimeLabels() {
        Calendar calendar = Calendar.getInstance();
        Date now = calendar.getTime();
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        SimpleDateFormat monthFormat = new SimpleDateFormat("yyyy-MM", Locale.getDefault());
        int timeColor = Color.parseColor("#999999");
        int timeSizeSp = 12;

        if (btnDaily != null) {
            String dailyTime = toNoBreakHyphen(dateFormat.format(now));
            SpannableStringBuilder sb = new SpannableStringBuilder();
            sb.append("每日");
            sb.append("\n");
            int start = sb.length();
            sb.append(dailyTime);
            int end = sb.length();
            sb.setSpan(new ForegroundColorSpan(timeColor), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            sb.setSpan(new AbsoluteSizeSpan(timeSizeSp, true), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            btnDaily.setText(sb);
        }

        if (btnWeekly != null) {
            Calendar weekStart = (Calendar) calendar.clone();
            weekStart.set(Calendar.DAY_OF_WEEK, weekStart.getFirstDayOfWeek());
            weekStart.set(Calendar.HOUR_OF_DAY, 0);
            weekStart.set(Calendar.MINUTE, 0);
            weekStart.set(Calendar.SECOND, 0);
            weekStart.set(Calendar.MILLISECOND, 0);

            Calendar weekEnd = (Calendar) weekStart.clone();
            weekEnd.add(Calendar.DAY_OF_YEAR, 6);
            String startStr = dateFormat.format(weekStart.getTime());
            String endStr = dateFormat.format(weekEnd.getTime());
            SpannableStringBuilder sb = new SpannableStringBuilder();
            sb.append("每周");
            sb.append("\n");
            int start = sb.length();
            sb.append(toNoBreakHyphen(startStr));
            sb.append("\n");
            sb.append(toNoBreakHyphen(endStr));
            int end = sb.length();
            sb.setSpan(new ForegroundColorSpan(timeColor), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            sb.setSpan(new AbsoluteSizeSpan(timeSizeSp, true), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            btnWeekly.setText(sb);
        }

        if (btnMonthly != null) {
            String monthlyTime = toNoBreakHyphen(monthFormat.format(now));
            SpannableStringBuilder sb = new SpannableStringBuilder();
            sb.append("每月");
            sb.append("\n");
            int start = sb.length();
            sb.append(monthlyTime);
            int end = sb.length();
            sb.setSpan(new ForegroundColorSpan(timeColor), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            sb.setSpan(new AbsoluteSizeSpan(timeSizeSp, true), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            btnMonthly.setText(sb);
        }
    }

    private String toNoBreakHyphen(String text) {
        if (text == null) return "";
        return text.replace("-", "\u2011");
    }
    
    private void processHeatMapData(List<SportRecord> records) {
        // Initialize data structures for AM and PM
        // We use AnnularHeatMapView.HeatMapData instead of simple Double
        
        List<AnnularHeatMapView.HeatMapData> dataAM = new ArrayList<>();
        List<AnnularHeatMapView.HeatMapData> dataPM = new ArrayList<>();
        
        for (int i = 0; i < 12; i++) {
            dataAM.add(new AnnularHeatMapView.HeatMapData(0, new HashMap<>()));
            dataPM.add(new AnnularHeatMapView.HeatMapData(0, new HashMap<>()));
        }
        
        Calendar calendar = Calendar.getInstance();
        
        for (SportRecord record : records) {
            long startTime = record.getStartTime();
            long duration = record.getDuration(); // seconds
            String type = record.getType();
            
            if (startTime > 0) {
                calendar.setTimeInMillis(startTime);
                int hour = calendar.get(Calendar.HOUR_OF_DAY);
                
                double minutes = duration / 60.0;
                
                AnnularHeatMapView.HeatMapData targetData;
                if (hour < 12) {
                    targetData = dataAM.get(hour);
                } else {
                    int index = hour - 12;
                    if (index < 12) {
                        targetData = dataPM.get(index);
                    } else {
                        continue;
                    }
                }
                
                // Update total
                targetData.totalDuration += minutes;
                
                // Update type breakdown
                String displayType = getDisplayType(type);
                targetData.typeDetails.put(displayType, 
                    targetData.typeDetails.getOrDefault(displayType, 0.0) + minutes);
            }
        }
        
        heatMapAM.setHeatMapData(dataAM);
        heatMapPM.setHeatMapData(dataPM);
    }
    
    private String getDisplayType(String type) {
        if (type == null) return "未知";
        if (type.equals(SportType.WALKING.name())) return "步行";
        if (type.equals(SportType.RUNNING.name())) return "跑步";
        if (type.equals(SportType.CYCLING.name())) return "骑行";
        if (type.equals(SportType.FITNESS.name())) return "健身";
        return type;
    }
}
