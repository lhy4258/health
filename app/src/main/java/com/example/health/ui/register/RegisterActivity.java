package com.example.health.ui.register;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.health.R;
import com.example.health.auth.AuthManager;
import com.example.health.ui.LoginActivity;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

/**
 * 用户注册页面，提供用户名、邮箱、手机号、密码的输入与格式校验。
 * 实时显示密码强度（弱/中/强），注册成功后自动跳转登录页。
 */
public class RegisterActivity extends AppCompatActivity {
    private TextInputLayout usernameLayout;
    private TextInputLayout emailLayout;
    private TextInputLayout phoneLayout;
    private TextInputLayout passwordLayout;
    private TextInputLayout confirmPasswordLayout;
    private TextInputEditText usernameEditText;
    private TextInputEditText emailEditText;
    private TextInputEditText phoneEditText;
    private TextInputEditText passwordEditText;
    private TextInputEditText confirmPasswordEditText;
    private TextView passwordStrengthTextView;
    private CheckBox agreeCheckBox;
    private Button registerButton;
    private TextView loginTextView;
    private ProgressBar progressBar;

    private AuthManager authManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        authManager = new AuthManager(this);

        initViews();
        setupListeners();
    }

    private void initViews() {
        usernameLayout = findViewById(R.id.usernameLayout);
        emailLayout = findViewById(R.id.emailLayout);
        phoneLayout = findViewById(R.id.phoneLayout);
        passwordLayout = findViewById(R.id.passwordLayout);
        confirmPasswordLayout = findViewById(R.id.confirmPasswordLayout);
        usernameEditText = findViewById(R.id.usernameEditText);
        emailEditText = findViewById(R.id.emailEditText);
        phoneEditText = findViewById(R.id.phoneEditText);
        passwordEditText = findViewById(R.id.passwordEditText);
        confirmPasswordEditText = findViewById(R.id.confirmPasswordEditText);
        passwordStrengthTextView = findViewById(R.id.passwordStrengthTextView);
        agreeCheckBox = findViewById(R.id.agreeCheckBox);
        registerButton = findViewById(R.id.registerButton);
        loginTextView = findViewById(R.id.loginTextView);
        progressBar = findViewById(R.id.progressBar);
    }

    private void setupListeners() {
        usernameEditText.addTextChangedListener(createTextWatcher(usernameLayout));
        emailEditText.addTextChangedListener(createTextWatcher(emailLayout));
        phoneEditText.addTextChangedListener(createTextWatcher(phoneLayout));
        passwordEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                passwordLayout.setError(null);
                updatePasswordStrength(s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        confirmPasswordEditText.addTextChangedListener(createTextWatcher(confirmPasswordLayout));

        registerButton.setOnClickListener(v -> attemptRegister());
        loginTextView.setOnClickListener(v -> navigateToLogin());
    }

    private TextWatcher createTextWatcher(TextInputLayout layout) {
        return new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                layout.setError(null);
            }

            @Override
            public void afterTextChanged(Editable s) {}
        };
    }

    private void updatePasswordStrength(String password) {
        if (password.isEmpty()) {
            passwordStrengthTextView.setText("密码强度：未输入");
            passwordStrengthTextView.setTextColor(getResources().getColor(R.color.secondary_text_color));
            return;
        }

        int strength = calculatePasswordStrength(password);
        String strengthText;
        int color;

        switch (strength) {
            case 0:
                strengthText = "密码强度：弱";
                color = getResources().getColor(R.color.error_color);
                break;
            case 1:
                strengthText = "密码强度：中";
                color = getResources().getColor(R.color.warning_color);
                break;
            case 2:
                strengthText = "密码强度：强";
                color = getResources().getColor(R.color.success_color);
                break;
            default:
                strengthText = "密码强度：未输入";
                color = getResources().getColor(R.color.secondary_text_color);
        }

        passwordStrengthTextView.setText(strengthText);
        passwordStrengthTextView.setTextColor(color);
    }

    private int calculatePasswordStrength(String password) {
        if (password.length() < 8) {
            return 0;
        }

        boolean hasUpperCase = false;
        boolean hasLowerCase = false;
        boolean hasDigit = false;
        boolean hasSpecialChar = false;

        for (char c : password.toCharArray()) {
            if (Character.isUpperCase(c)) {
                hasUpperCase = true;
            } else if (Character.isLowerCase(c)) {
                hasLowerCase = true;
            } else if (Character.isDigit(c)) {
                hasDigit = true;
            } else if (!Character.isLetterOrDigit(c)) {
                hasSpecialChar = true;
            }
        }

        int criteriaMet = 0;
        if (hasUpperCase) criteriaMet++;
        if (hasLowerCase) criteriaMet++;
        if (hasDigit) criteriaMet++;
        if (hasSpecialChar) criteriaMet++;

        if (criteriaMet >= 4) {
            return 2;
        } else if (criteriaMet >= 2) {
            return 1;
        } else {
            return 0;
        }
    }

    private void attemptRegister() {
        String username = usernameEditText.getText().toString().trim();
        String email = emailEditText.getText().toString().trim();
        String phone = phoneEditText.getText().toString().trim();
        String password = passwordEditText.getText().toString().trim();
        String confirmPassword = confirmPasswordEditText.getText().toString().trim();
        boolean agreedToTerms = agreeCheckBox.isChecked();

        if (username.isEmpty()) {
            usernameLayout.setError("用户名不能为空");
            return;
        }

        if (email.isEmpty()) {
            emailLayout.setError("邮箱不能为空");
            return;
        }

        if (phone.isEmpty()) {
            phoneLayout.setError("手机号不能为空");
            return;
        }

        if (password.isEmpty()) {
            passwordLayout.setError("密码不能为空");
            return;
        }

        if (confirmPassword.isEmpty()) {
            confirmPasswordLayout.setError("请确认密码");
            return;
        }

        if (!password.equals(confirmPassword)) {
            confirmPasswordLayout.setError("两次输入的密码不一致");
            return;
        }

        if (!agreedToTerms) {
            showToast("请同意用户协议和隐私政策");
            return;
        }

        showLoading(true);

        authManager.register(username, email, phone, password, confirmPassword, agreedToTerms, 
            new AuthManager.AuthCallback() {
                @Override
                public void onSuccess(String message) {
                    runOnUiThread(() -> {
                        showLoading(false);
                        showToast(message);
                        navigateToLogin();
                    });
                }

                @Override
                public void onError(String error) {
                    runOnUiThread(() -> {
                        showLoading(false);
                        showToast(error);
                    });
                }
            });
    }

    private void navigateToLogin() {
        Intent intent = new Intent(this, LoginActivity.class);
        startActivity(intent);
        finish();
    }

    private void showLoading(boolean show) {
        progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
        registerButton.setEnabled(!show);
    }

    private void showToast(String message) {
        android.widget.Toast.makeText(this, message, android.widget.Toast.LENGTH_SHORT).show();
    }
}
