package com.example.health.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.health.R;
import com.example.health.auth.AuthManager;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import com.google.android.material.bottomnavigation.BottomNavigationView;

/**
 * 应用主页面，使用 BottomNavigationView 实现运动、饮食计划、好友、个人资料四个 Tab 的切换。
 * 每次 onResume 检测设备绑定状态，若在其他设备登录则自动跳转登录页。
 */
public class MainActivity extends AppCompatActivity {
    
    private BottomNavigationView bottomNavigationView;
    private AuthManager authManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        authManager = new AuthManager(this);
        bottomNavigationView = findViewById(R.id.bottom_navigation);

        if (savedInstanceState == null) {
            loadFragment(new SportFragment());
        }

        bottomNavigationView.setOnItemSelectedListener(item -> {
            Fragment fragment = null;
            int itemId = item.getItemId();
            
            if (itemId == R.id.nav_sport) {
                fragment = new SportFragment();
            } else if (itemId == R.id.nav_plan_diet) {
                fragment = new PlanDietFragment();
            } else if (itemId == R.id.nav_friends) {
                fragment = new FriendsFragment();
            } else if (itemId == R.id.nav_profile) {
                fragment = new ProfileFragment();
            }
            
            return loadFragment(fragment);
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (authManager != null) {
            authManager.checkCurrentUserDevice((valid, hasUser, message) -> {
                if (!valid) {
                    runOnUiThread(() -> {
                        if (message != null && !message.isEmpty()) {
                            android.widget.Toast.makeText(this, message, android.widget.Toast.LENGTH_SHORT).show();
                        }
                        authManager.logout();
                        Intent intent = new Intent(this, LoginActivity.class);
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        startActivity(intent);
                        finish();
                    });
                } else if (!hasUser) {
                    runOnUiThread(() -> {
                        authManager.logout();
                        Intent intent = new Intent(this, LoginActivity.class);
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        startActivity(intent);
                        finish();
                    });
                }
            });
        }
    }

    private boolean loadFragment(Fragment fragment) {
        if (fragment != null) {
            getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragment_container, fragment)
                .commit();
            return true;
        }
        return false;
    }
}
