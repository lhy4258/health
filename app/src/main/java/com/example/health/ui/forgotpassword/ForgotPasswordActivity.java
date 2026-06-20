package com.example.health.ui.forgotpassword;

import android.content.Intent;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.health.R;
import com.example.health.auth.AuthManager;
import com.example.health.ui.LoginActivity;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

/**
 * 忘记密码页面，通过邮箱发送验证码后重置密码。
 * 包含验证码发送按钮的 60 秒倒计时控制和密码强度指示。
 */
public class ForgotPasswordActivity extends AppCompatActivity {
    private ImageView backImageView;
    private TextInputLayout emailLayout;
    private TextInputLayout verificationCodeLayout;
    private TextInputLayout newPasswordLayout;
    private TextInputLayout confirmPasswordLayout;
    private TextInputEditText emailEditText;
    private TextInputEditText verificationCodeEditText;
    private TextInputEditText newPasswordEditText;
    private TextInputEditText confirmPasswordEditText;
    private TextView passwordStrengthTextView;
    private Button sendCodeButton;
    private Button resetPasswordButton;
    private ProgressBar progressBar;

    private AuthManager authManager;
    private CountDownTimer countDownTimer;
    private boolean isCodeSent = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_forgot_password);

        authManager = new AuthManager(this);

        initViews();
        setupListeners();
    }

    private void initViews() {
        backImageView = findViewById(R.id.backImageView);
        emailLayout = findViewById(R.id.emailLayout);
        verificationCodeLayout = findViewById(R.id.verificationCodeLayout);
        newPasswordLayout = findViewById(R.id.newPasswordLayout);
        confirmPasswordLayout = findViewById(R.id.confirmPasswordLayout);
        emailEditText = findViewById(R.id.emailEditText);
        verificationCodeEditText = findViewById(R.id.verificationCodeEditText);
        newPasswordEditText = findViewById(R.id.newPasswordEditText);
        confirmPasswordEditText = findViewById(R.id.confirmPasswordEditText);
        passwordStrengthTextView = findViewById(R.id.passwordStrengthTextView);
        sendCodeButton = findViewById(R.id.sendCodeButton);
        resetPasswordButton = findViewById(R.id.resetPasswordButton);
        progressBar = findViewById(R.id.progressBar);
    }

    private void setupListeners() {
        backImageView.setOnClickListener(v -> onBackPressed());

        emailEditText.addTextChangedListener(createTextWatcher(emailLayout));
        verificationCodeEditText.addTextChangedListener(createTextWatcher(verificationCodeLayout));
        
        newPasswordEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                newPasswordLayout.setError(null);
                updatePasswordStrength(s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        confirmPasswordEditText.addTextChangedListener(createTextWatcher(confirmPasswordLayout));

        sendCodeButton.setOnClickListener(v -> sendVerificationCode());
        resetPasswordButton.setOnClickListener(v -> resetPassword());
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

    private void sendVerificationCode() {
        String email = emailEditText.getText().toString().trim();

        if (email.isEmpty()) {
            emailLayout.setError("邮箱不能为空");
            return;
        }

        showLoading(true);

        authManager.sendVerificationCode(email, new AuthManager.AuthCallback() {
            @Override
            public void onSuccess(String message) {
                runOnUiThread(() -> {
                    showLoading(false);
                    showToast(message);
                    isCodeSent = true;
                    startCountDownTimer();
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

    private void startCountDownTimer() {
        if (countDownTimer != null) {
            countDownTimer.cancel();
        }

        countDownTimer = new CountDownTimer(60000, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                int seconds = (int) (millisUntilFinished / 1000);
                sendCodeButton.setEnabled(false);
                sendCodeButton.setText("重新发送(" + seconds + "s)");
            }

            @Override
            public void onFinish() {
                sendCodeButton.setEnabled(true);
                sendCodeButton.setText("发送验证码");
            }
        }.start();
    }

    private void resetPassword() {
        String email = emailEditText.getText().toString().trim();
        String code = verificationCodeEditText.getText().toString().trim();
        String newPassword = newPasswordEditText.getText().toString().trim();
        String confirmPassword = confirmPasswordEditText.getText().toString().trim();

        if (email.isEmpty()) {
            emailLayout.setError("邮箱不能为空");
            return;
        }

        if (code.isEmpty()) {
            verificationCodeLayout.setError("验证码不能为空");
            return;
        }

        if (newPassword.isEmpty()) {
            newPasswordLayout.setError("新密码不能为空");
            return;
        }

        if (confirmPassword.isEmpty()) {
            confirmPasswordLayout.setError("请确认新密码");
            return;
        }

        if (!newPassword.equals(confirmPassword)) {
            confirmPasswordLayout.setError("两次输入的密码不一致");
            return;
        }

        showLoading(true);

        authManager.resetPassword(email, code, newPassword, confirmPassword, 
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
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void showLoading(boolean show) {
        progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
        sendCodeButton.setEnabled(!show);
        resetPasswordButton.setEnabled(!show);
    }

    private void showToast(String message) {
        android.widget.Toast.makeText(this, message, android.widget.Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (countDownTimer != null) {
            countDownTimer.cancel();
        }
    }
}
