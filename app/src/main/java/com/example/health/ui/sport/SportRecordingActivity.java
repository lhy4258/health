package com.example.health.ui.sport;

import android.os.Bundle;
import android.os.SystemClock;
import android.view.View;
import android.widget.Button;
import android.widget.Chronometer;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.health.R;
import com.example.health.data.model.SportRecord;
import com.example.health.data.model.SportType;
import com.example.health.data.model.User;

import java.util.Locale;

import cn.leancloud.LCObject;
import cn.leancloud.LCUser;
import io.reactivex.Observer;
import io.reactivex.disposables.Disposable;

/**
 * 运动计时页，实时显示运动类型、时长、估算卡路里和距离。
 * 卡路里公式：MET × 体重(kg) × 时间(h)，距离按运动类型估算速度计算。
 * 点击完成按钮将记录保存至 SportRecord 表。
 */
public class SportRecordingActivity extends AppCompatActivity {

    private TextView sportTypeTextView;
    private Chronometer timerChronometer;
    private TextView calorieValueTextView;
    private TextView distanceValueTextView;
    private Button finishButton;

    private SportType sportType;
    private long startTimeMillis;
    private double currentCalories = 0;
    private double currentDistance = 0; // Simulated
    private double userWeight = 70.0; // Default weight in kg

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sport_recording);

        sportTypeTextView = findViewById(R.id.sportTypeTextView);
        timerChronometer = findViewById(R.id.timerChronometer);
        calorieValueTextView = findViewById(R.id.calorieValueTextView);
        distanceValueTextView = findViewById(R.id.distanceValueTextView);
        finishButton = findViewById(R.id.finishButton);

        String typeName = getIntent().getStringExtra("SPORT_TYPE");
        if (typeName != null) {
            sportType = SportType.valueOf(typeName);
        } else {
            sportType = SportType.WALKING;
        }

        sportTypeTextView.setText(sportType.getDisplayName() + "中...");

        setupTimer();
        
        finishButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finishRecording();
            }
        });

        // Start recording
        startTimeMillis = System.currentTimeMillis();
        timerChronometer.setBase(SystemClock.elapsedRealtime());
        timerChronometer.start();
    }

    private void setupTimer() {
        timerChronometer.setOnChronometerTickListener(new Chronometer.OnChronometerTickListener() {
            @Override
            public void onChronometerTick(Chronometer chronometer) {
                long elapsedMillis = SystemClock.elapsedRealtime() - chronometer.getBase();
                long elapsedSeconds = elapsedMillis / 1000;

                updateStats(elapsedSeconds);
            }
        });
    }

    private void updateStats(long elapsedSeconds) {
        // Calorie Formula: MET * Weight(kg) * Time(hours)
        double hours = elapsedSeconds / 3600.0;
        currentCalories = sportType.getMetValue() * userWeight * hours;

        // Distance Formula (Simplified estimation): 
        // Walking: ~5 km/h, Running: ~10 km/h, Cycling: ~20 km/h
        double speedKmh = 0;
        switch (sportType) {
            case WALKING: speedKmh = 5.0; break;
            case RUNNING: speedKmh = 10.0; break;
            case CYCLING: speedKmh = 20.0; break;
            case FITNESS: speedKmh = 0.0; break; // Stationary
        }
        currentDistance = speedKmh * hours;

        calorieValueTextView.setText(String.format(Locale.getDefault(), "%.1f", currentCalories));
        distanceValueTextView.setText(String.format(Locale.getDefault(), "%.2f", currentDistance));
    }

    private void finishRecording() {
        timerChronometer.stop();
        long endTimeMillis = System.currentTimeMillis();
        long durationMillis = SystemClock.elapsedRealtime() - timerChronometer.getBase();
        long durationSeconds = durationMillis / 1000;

        // Create and save record
        SportRecord record = new SportRecord();
        record.setType(sportType);
        record.setStartTime(startTimeMillis);
        record.setEndTime(endTimeMillis);
        record.setDuration(durationSeconds);
        record.setCalories(currentCalories);
        record.setDistance(currentDistance);
        
        LCUser currentUser = LCUser.currentUser();
        if (currentUser != null) {
            record.setUser(currentUser);
        }

        record.saveInBackground().subscribe(new Observer<LCObject>() {
            @Override
            public void onSubscribe(Disposable d) {}

            @Override
            public void onNext(LCObject sportRecord) {
                Toast.makeText(SportRecordingActivity.this, "运动记录已保存", Toast.LENGTH_SHORT).show();
                finish();
            }

            @Override
            public void onError(Throwable e) {
                Toast.makeText(SportRecordingActivity.this, "保存失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                // Still finish or let user retry? For now finish to avoid getting stuck
                finish();
            }

            @Override
            public void onComplete() {}
        });
    }
}
