package com.example.health.auth;

import android.content.Context;
import android.content.SharedPreferences;

import com.example.health.data.model.LoginAttempt;
import com.example.health.data.model.User;
import com.example.health.utils.ValidationUtils;

import java.util.UUID;

import cn.leancloud.LCObject;
import cn.leancloud.LCQuery;
import cn.leancloud.LCUser;
import io.reactivex.Observer;
import io.reactivex.disposables.Disposable;

/**
 * 认证管理器，统一处理注册、登录、登出、密码重置、邮箱验证等认证业务逻辑。
 * 包含单设备登录检测和登录失败次数限制/账户锁定安全机制。
 */
public class AuthManager {
    private static final int MAX_LOGIN_ATTEMPTS = 5;
    private static final long LOCK_DURATION = 30 * 60 * 1000;
    private static final String PREF_NAME_DEVICE = "auth_device_prefs";
    private static final String KEY_DEVICE_ID = "device_id";
    private static final String FIELD_DEVICE_ID = "currentDeviceId";

    private final Context appContext;

    public AuthManager(Context context) {
        this.appContext = context.getApplicationContext();
    }

    public interface AuthCallback {
        void onSuccess(String message);
        void onError(String error);
    }

    public interface DeviceCheckCallback {
        void onResult(boolean valid, boolean hasUser, String message);
    }

    private String getOrCreateDeviceId() {
        SharedPreferences prefs = appContext.getSharedPreferences(PREF_NAME_DEVICE, Context.MODE_PRIVATE);
        String id = prefs.getString(KEY_DEVICE_ID, null);
        if (id == null || id.isEmpty()) {
            id = UUID.randomUUID().toString();
            prefs.edit().putString(KEY_DEVICE_ID, id).apply();
        }
        return id;
    }

    /**
     * 将当前登录用户与设备 ID 绑定，实现单设备登录检测的基础数据。
     * 每次登录成功后调用，将设备唯一标识写入用户记录的 currentDeviceId 字段。
     */
    public void bindCurrentUserToThisDevice() {
        LCUser currentUser = LCUser.currentUser();
        if (currentUser == null) {
            return;
        }
        String deviceId = getOrCreateDeviceId();
        String objectId = currentUser.getObjectId();
        if (objectId == null || objectId.isEmpty()) {
            currentUser.put(FIELD_DEVICE_ID, deviceId);
            currentUser.saveInBackground().subscribe();
            return;
        }

        LCQuery<LCUser> query = LCUser.getQuery();
        query.getInBackground(objectId).subscribe(new Observer<LCUser>() {
            @Override
            public void onSubscribe(Disposable d) {
            }

            @Override
            public void onNext(LCUser freshUser) {
                String freshUsername = freshUser != null ? freshUser.getUsername() : null;
                if (freshUsername != null && !freshUsername.isEmpty()) {
                    currentUser.setUsername(freshUsername);
                }
                currentUser.put(FIELD_DEVICE_ID, deviceId);
                currentUser.saveInBackground().subscribe();
            }

            @Override
            public void onError(Throwable e) {
                currentUser.put(FIELD_DEVICE_ID, deviceId);
                currentUser.saveInBackground().subscribe();
            }

            @Override
            public void onComplete() {
            }
        });
    }

    /**
     * 检测当前用户是否在其他设备登录。比较本地设备 ID 与服务端存储的设备 ID，
     * 若不匹配则提示"该账号已在其他设备登录"。
     */
    public void checkCurrentUserDevice(DeviceCheckCallback callback) {
        LCUser currentUser = LCUser.currentUser();
        if (currentUser == null) {
            callback.onResult(false, false, "未登录");
            return;
        }
        String objectId = currentUser.getObjectId();
        if (objectId == null || objectId.isEmpty()) {
            callback.onResult(false, false, "未登录");
            return;
        }
        String localDeviceId = getOrCreateDeviceId();

        LCQuery<LCUser> query = LCUser.getQuery();
        query.getInBackground(objectId).subscribe(new Observer<LCUser>() {
            @Override
            public void onSubscribe(Disposable d) {
            }

            @Override
            public void onNext(LCUser user) {
                if (user == null) {
                    callback.onResult(false, false, "未登录");
                    return;
                }
                String serverDeviceId = user.getString(FIELD_DEVICE_ID);
                if (serverDeviceId == null || serverDeviceId.isEmpty() || serverDeviceId.equals(localDeviceId)) {
                    callback.onResult(true, true, null);
                } else {
                    callback.onResult(false, true, "该账号已在其他设备登录，如需继续使用请重新登录");
                }
            }

            @Override
            public void onError(Throwable e) {
                callback.onResult(true, true, null);
            }

            @Override
            public void onComplete() {
            }
        });
    }

    /**
     * 用户注册，校验输入格式并调用 LeanCloud 注册接口。
     */
    public void register(String username, String email, String phone, String password, 
                         String confirmPassword, boolean agreedToTerms, AuthCallback callback) {
        
        if (!ValidationUtils.isValidUsername(username)) {
            callback.onError(ValidationUtils.getUsernameValidationMessage(username));
            return;
        }

        if (!ValidationUtils.isValidEmail(email)) {
            callback.onError(ValidationUtils.getEmailValidationMessage(email));
            return;
        }

        if (!ValidationUtils.isValidPhone(phone)) {
            callback.onError(ValidationUtils.getPhoneValidationMessage(phone));
            return;
        }

        // Password strength check (optional, can rely on server or utils)
        // if (!PasswordUtils.isPasswordStrong(password)) ... 

        if (!password.equals(confirmPassword)) {
            callback.onError("两次输入的密码不一致");
            return;
        }

        if (!agreedToTerms) {
            callback.onError("请同意用户协议和隐私政策");
            return;
        }

        User user = new User();
        user.setUsername(username);
        user.setPassword(password);
        user.setEmail(email);
        user.setMobilePhoneNumber(phone);
        
        user.signUpInBackground().subscribe(new Observer<LCUser>() {
            @Override
            public void onSubscribe(Disposable d) {}

            @Override
            public void onNext(LCUser lcUser) {
                callback.onSuccess("注册成功，请查收验证邮件");
            }

            @Override
            public void onError(Throwable e) {
                callback.onError("注册失败：" + e.getMessage());
            }

            @Override
            public void onComplete() {}
        });
    }

    /**
     * 用户登录，先检查登录锁定状态，通过后执行密码验证。
     */
    public void login(String email, String password, AuthCallback callback) {
        if (!ValidationUtils.isValidEmail(email)) {
            callback.onError("邮箱格式不正确");
            return;
        }

        LCQuery<LoginAttempt> query = new LCQuery<>("LoginAttempt");
        query.whereEqualTo("email", email);
        query.getFirstInBackground().subscribe(new Observer<LoginAttempt>() {
            private boolean handled = false;
            @Override
            public void onSubscribe(Disposable d) {}

            @Override
            public void onNext(LoginAttempt attempt) {
                handled = true;
                if (attempt != null && attempt.isLocked()) {
                    long lockUntil = attempt.getLockUntil();
                    if (System.currentTimeMillis() < lockUntil) {
                        long remainingTime = (lockUntil - System.currentTimeMillis()) / 60000;
                        callback.onError("账户已被锁定，请" + remainingTime + "分钟后再试");
                        return;
                    } else {
                        // Unlock
                        attempt.setLocked(false);
                        attempt.setAttemptCount(0);
                        attempt.saveInBackground().subscribe();
                    }
                }
                
                performLogin(email, password, attempt, callback);
            }

            @Override
            public void onError(Throwable e) {
                if (!handled) {
                    handled = true;
                    performLogin(email, password, null, callback);
                }
            }

            @Override
            public void onComplete() {
                if (!handled) {
                    handled = true;
                    performLogin(email, password, null, callback);
                }
            }
        });
    }

    private void performLogin(String email, String password, LoginAttempt attempt, AuthCallback callback) {
        if (email != null && email.contains("@")) {
            queryUserByEmailAndLogin(email, password, attempt, new AuthCallback() {
                @Override
                public void onSuccess(String message) {
                    callback.onSuccess(message);
                }

                @Override
                public void onError(String error) {
                    LCUser.logIn(email, password, User.class).subscribe(new Observer<User>() {
                        @Override
                        public void onSubscribe(Disposable d) {
                        }

                        @Override
                        public void onNext(User loggedInUser) {
                            handleLoginSuccess(attempt, callback);
                        }

                        @Override
                        public void onError(Throwable e) {
                            callback.onError("登录失败：" + e.getMessage());
                        }

                        @Override
                        public void onComplete() {
                        }
                    });
                }
            });
            return;
        }

        LCUser.logIn(email, password, User.class).subscribe(new Observer<User>() {
            @Override
            public void onSubscribe(Disposable d) {
            }

            @Override
            public void onNext(User loggedInUser) {
                handleLoginSuccess(attempt, callback);
            }

            @Override
            public void onError(Throwable e) {
                callback.onError("登录失败：" + e.getMessage());
            }

            @Override
            public void onComplete() {
            }
        });
    }

    private void queryUserByEmailAndLogin(String email, String password, LoginAttempt attempt, AuthCallback callback) {
        LCQuery<LCUser> userQuery = LCUser.getQuery();
        userQuery.whereEqualTo("email", email);
        userQuery.getFirstInBackground().subscribe(new Observer<LCUser>() {
            private boolean found = false;
            @Override
            public void onSubscribe(Disposable d) {}

            @Override
            public void onNext(LCUser user) {
                found = true;
                String username = user.getUsername();
                LCUser.logIn(username, password, User.class).subscribe(new Observer<User>() {
                    @Override
                    public void onSubscribe(Disposable d) {}

                    @Override
                    public void onNext(User loggedInUser) {
                        handleLoginSuccess(attempt, callback);
                    }

                    @Override
                    public void onError(Throwable e) {
                        handleLoginFailure(email, attempt, callback);
                    }

                    @Override
                    public void onComplete() {}
                });
            }

            @Override
            public void onError(Throwable e) {
                if (!found) {
                    callback.onError("该账号尚未注册，请先注册");
                } else {
                    callback.onError("登录失败：" + e.getMessage());
                }
            }

            @Override
            public void onComplete() {
                if (!found) {
                    callback.onError("该账号尚未注册，请先注册");
                }
            }
        });
    }

    /**
     * 处理登录成功：删除 LoginAttempt 记录，绑定设备，回调成功。
     */
    private void handleLoginSuccess(LoginAttempt attempt, AuthCallback callback) {
        if (attempt != null) {
            attempt.deleteInBackground().subscribe();
        }
        bindCurrentUserToThisDevice();
        callback.onSuccess("登录成功");
    }
    
    /**
     * 处理登录失败：递增失败次数，达到阈值后锁定账户 30 分钟，否则提示剩余尝试次数。
     */
    private void handleLoginFailure(String email, LoginAttempt attempt, AuthCallback callback) {
        if (attempt == null) {
            attempt = new LoginAttempt();
            attempt.setEmail(email);
            attempt.setAttemptCount(0);
        }
        
        int count = attempt.getAttemptCount() + 1;
        attempt.setAttemptCount(count);
        attempt.setLastAttemptTime(System.currentTimeMillis());
        
        if (count >= MAX_LOGIN_ATTEMPTS) {
            attempt.setLocked(true);
            attempt.setLockUntil(System.currentTimeMillis() + LOCK_DURATION);
            attempt.saveInBackground().subscribe(new Observer<LCObject>() {
                @Override
                public void onSubscribe(Disposable d) {}

                @Override
                public void onNext(LCObject lcObject) {
                    callback.onError("密码错误次数过多，账户已被锁定30分钟");
                }

                @Override
                public void onError(Throwable e) {
                    callback.onError("系统错误: " + e.getMessage());
                }

                @Override
                public void onComplete() {}
            });
        } else {
            attempt.saveInBackground().subscribe(new Observer<LCObject>() {
                @Override
                public void onSubscribe(Disposable d) {}

                @Override
                public void onNext(LCObject lcObject) {
                    int remaining = MAX_LOGIN_ATTEMPTS - count;
                    callback.onError("密码错误，还剩" + remaining + "次尝试机会");
                }

                @Override
                public void onError(Throwable e) {
                     callback.onError("系统错误: " + e.getMessage());
                }

                @Override
                public void onComplete() {}
            });
        }
    }

    public void verifyEmail(String email, String code, AuthCallback callback) {
        // LeanCloud uses link verification
        callback.onError("请直接点击注册邮箱中的验证链接进行验证");
    }

    /**
     * 发送邮箱验证邮件。
     */
    public void sendVerificationCode(String email, AuthCallback callback) {
        LCUser.requestEmailVerifyInBackground(email).subscribe(new Observer<Object>() {
            @Override
            public void onSubscribe(Disposable d) {}

            @Override
            public void onNext(Object object) {
                callback.onSuccess("验证邮件已发送");
            }

            @Override
            public void onError(Throwable e) {
                callback.onError("发送失败：" + e.getMessage());
            }

            @Override
            public void onComplete() {}
        });
    }

    /**
     * 重置密码：通过 LeanCloud 发送密码重置邮件。
     */
    public void resetPassword(String email, String code, String newPassword, 
                             String confirmPassword, AuthCallback callback) {
        // LeanCloud sends password reset email
        LCUser.requestPasswordResetInBackground(email).subscribe(new Observer<Object>() {
            @Override
            public void onSubscribe(Disposable d) {}

            @Override
            public void onNext(Object object) {
                callback.onSuccess("重置密码邮件已发送，请查收");
            }

            @Override
            public void onError(Throwable e) {
                callback.onError("请求失败：" + e.getMessage());
            }

            @Override
            public void onComplete() {}
        });
    }

    public void logout() {
        LCUser.logOut();
    }

    public boolean isLoggedIn() {
        return LCUser.currentUser() != null;
    }

    public String getCurrentUsername() {
        LCUser user = LCUser.currentUser();
        return user != null ? user.getUsername() : null;
    }
}
