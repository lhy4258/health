package com.example.health.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.example.health.R;
import com.example.health.auth.AuthManager;

/**
 * 设置页面，提供退出登录功能，退出后清除本地 Token 并跳转到登录页。
 */
public class SettingsActivity extends AppCompatActivity {

    private AuthManager authManager;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        authManager = new AuthManager(this);

        View btnBack = findViewById(R.id.btnBack);
        if (btnBack != null) {
            btnBack.setOnClickListener(v -> finish());
        }

        View rowLogout = findViewById(R.id.rowLogout);
        rowLogout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showLogoutConfirmDialog();
            }
        });
    }

    private void showLogoutConfirmDialog() {
        new AlertDialog.Builder(this)
                .setTitle("确认退出登录")
                .setMessage("退出后需要重新登录，是否继续？")
                .setPositiveButton("退出", (dialog, which) -> performLogout())
                .setNegativeButton("取消", null)
                .show();
    }

    private void performLogout() {
        authManager.logout();
        Intent intent = new Intent(this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}

