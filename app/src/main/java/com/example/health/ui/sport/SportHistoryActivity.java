package com.example.health.ui.sport;

import android.os.Bundle;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.example.health.R;
import com.example.health.data.model.SportRecord;
import com.example.health.data.model.SportType;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import cn.leancloud.LCQuery;
import cn.leancloud.LCUser;
import io.reactivex.Observer;
import io.reactivex.disposables.Disposable;

/**
 * 运动历史记录页，以列表形式展示指定运动类型近期的运动记录（默认 7 天）。
 * 每条记录显示日期、时长和卡路里消耗。
 */
public class SportHistoryActivity extends AppCompatActivity {

    private RecyclerView historyRecyclerView;
    private SportType sportType;
    private int daysToLoad = 7;
    private SwipeRefreshLayout swipeRefreshLayout;
    private SportHistoryAdapter adapter;
    private final List<SportRecord> historyRecords = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sport_history);

        android.view.View btnBack = findViewById(R.id.btnBack);
        if (btnBack != null) {
            btnBack.setOnClickListener(v -> finish());
        }

        swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout);
        historyRecyclerView = findViewById(R.id.historyRecyclerView);
        historyRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new SportHistoryAdapter(historyRecords);
        historyRecyclerView.setAdapter(adapter);

        if (swipeRefreshLayout != null) {
            swipeRefreshLayout.setOnRefreshListener(() -> loadHistory(true));
        }

        String typeName = getIntent().getStringExtra("SPORT_TYPE");
        if (typeName != null) {
            try {
                sportType = SportType.valueOf(typeName);
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

        daysToLoad = getIntent().getIntExtra("DAYS_TO_LOAD", 7);

        setTitle(sportType.getDisplayName() + "历史记录");

        loadHistory(false);
    }

    private void loadHistory() {
        loadHistory(false);
    }

    private void loadHistory(boolean fromRefresh) {
        LCUser currentUser = LCUser.currentUser();
        if (currentUser == null) {
            Toast.makeText(this, "未登录", Toast.LENGTH_SHORT).show();
            if (fromRefresh && swipeRefreshLayout != null) {
                swipeRefreshLayout.setRefreshing(false);
            }
            finish();
            return;
        }

        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.DAY_OF_YEAR, -daysToLoad);
        Date startDate = calendar.getTime();

        LCQuery<SportRecord> query = new LCQuery<>("SportRecord");
        query.whereEqualTo(SportRecord.ATTR_USER, currentUser);
        query.whereEqualTo(SportRecord.ATTR_TYPE, sportType.name());
        query.whereGreaterThanOrEqualTo(SportRecord.ATTR_START_TIME, startDate.getTime());
        query.orderByDescending(SportRecord.ATTR_START_TIME);

        query.findInBackground().subscribe(new Observer<List<SportRecord>>() {
            @Override
            public void onSubscribe(Disposable d) {}

            @Override
            public void onNext(List<SportRecord> records) {
                if (records == null) {
                    records = new ArrayList<>();
                }
                if (records.isEmpty()) {
                    Toast.makeText(SportHistoryActivity.this, "暂无历史记录", Toast.LENGTH_SHORT).show();
                }
                historyRecords.clear();
                historyRecords.addAll(records);
                adapter.notifyDataSetChanged();
                if (fromRefresh && swipeRefreshLayout != null) {
                    swipeRefreshLayout.setRefreshing(false);
                }
            }

            @Override
            public void onError(Throwable e) {
                Toast.makeText(SportHistoryActivity.this, "加载历史记录失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                if (fromRefresh && swipeRefreshLayout != null) {
                    swipeRefreshLayout.setRefreshing(false);
                }
            }

            @Override
            public void onComplete() {}
        });
    }

    private static class SportHistoryAdapter extends RecyclerView.Adapter<SportHistoryAdapter.ViewHolder> {

        private final List<SportRecord> records;
        private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());

        SportHistoryAdapter(List<SportRecord> records) {
            this.records = records;
        }

        @Override
        public ViewHolder onCreateViewHolder(android.view.ViewGroup parent, int viewType) {
            android.view.View view = android.view.LayoutInflater.from(parent.getContext())
                    .inflate(android.R.layout.simple_list_item_2, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(ViewHolder holder, int position) {
            SportRecord record = records.get(position);
            holder.text1.setText(dateFormat.format(new Date(record.getStartTime())));
            long durationSeconds = record.getDuration();
            double calories = record.getCalories();
            holder.text2.setText(String.format(Locale.getDefault(), "时长: %d秒, 卡路里: %.1f", durationSeconds, calories));
        }

        @Override
        public int getItemCount() {
            return records.size();
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            final android.widget.TextView text1;
            final android.widget.TextView text2;

            ViewHolder(android.view.View itemView) {
                super(itemView);
                text1 = itemView.findViewById(android.R.id.text1);
                text2 = itemView.findViewById(android.R.id.text2);
            }
        }
    }
}
