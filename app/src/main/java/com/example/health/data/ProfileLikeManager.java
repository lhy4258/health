package com.example.health.data;

import com.example.health.data.model.ProfileLike;

import java.util.Calendar;
import java.util.Date;
import java.util.concurrent.ConcurrentHashMap;

import android.util.Log;

import cn.leancloud.LCException;
import cn.leancloud.LCObject;
import cn.leancloud.LCQuery;
import cn.leancloud.LCUser;
import io.reactivex.Observer;
import io.reactivex.disposables.Disposable;

/**
 * 用户主页点赞管理器，封装点赞的增删查逻辑，包含缓存机制和每日点赞次数限制。
 * 每次点赞前先查询当日已点赞次数，超过 MAX_LIKES_PER_DAY 则拒绝。
 * 使用内存缓存（TTL 8 秒）减少网络请求。
 */
public class ProfileLikeManager {
    public static final int MAX_LIKES_PER_DAY = 10;
    private static final long CACHE_TTL_MS = 8000;
    private static final String TAG = "ProfileLikeManager";
    private static final ConcurrentHashMap<String, CacheEntry> totalCountCache = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, CacheEntry> todayCountCache = new ConcurrentHashMap<>();

    private static class CacheEntry {
        final int value;
        final long tsMs;

        CacheEntry(int value, long tsMs) {
            this.value = value;
            this.tsMs = tsMs;
        }
    }

    public interface TodayCountCallback {
        void onSuccess(int todayCount);

        void onError(String message);
    }

    public interface TotalCountCallback {
        void onSuccess(int totalCount);

        void onError(String message);
    }

    public interface LikeCallback {
        void onSuccess(int todayCount, int totalCount);

        void onLimitReached(int todayCount, int totalCount);

        void onError(String message);
    }

    public void loadTodayCount(String targetUserId, TodayCountCallback callback) {
        LCUser currentUser = LCUser.currentUser();
        if (currentUser == null) {
            callback.onSuccess(0);
            return;
        }
        String fromUserId = currentUser.getObjectId();
        if (fromUserId == null || fromUserId.isEmpty() || targetUserId == null || targetUserId.isEmpty()) {
            callback.onSuccess(0);
            return;
        }

        String cacheKey = buildTodayKey(fromUserId, targetUserId);
        CacheEntry cached = todayCountCache.get(cacheKey);
        if (cached != null && isFresh(cached.tsMs)) {
            callback.onSuccess(cached.value);
            return;
        }

        LCUser targetUser;
        try {
            targetUser = LCUser.createWithoutData(LCUser.class, targetUserId);
        } catch (LCException e) {
            callback.onError("用户信息无效");
            return;
        }

        Date start = getStartOfToday();
        Date end = getStartOfTomorrow();

        LCQuery<ProfileLike> query = LCQuery.getQuery(ProfileLike.class);
        query.whereEqualTo(ProfileLike.KEY_FROM_USER, currentUser);
        query.whereEqualTo(ProfileLike.KEY_TO_USER, targetUser);
        query.whereGreaterThanOrEqualTo("createdAt", start);
        query.whereLessThan("createdAt", end);
        query.countInBackground().subscribe(new Observer<Integer>() {
            @Override
            public void onSubscribe(Disposable d) {
            }

            @Override
            public void onNext(Integer count) {
                int safe = count != null ? count : 0;
                todayCountCache.put(cacheKey, new CacheEntry(safe, System.currentTimeMillis()));
                callback.onSuccess(safe);
            }

            @Override
            public void onError(Throwable e) {
                if (e instanceof LCException && ((LCException) e).getCode() == 101) {
                    todayCountCache.put(cacheKey, new CacheEntry(0, System.currentTimeMillis()));
                    callback.onSuccess(0);
                    return;
                }
                callback.onError(toErrorMessage("加载失败", e));
            }

            @Override
            public void onComplete() {
            }
        });
    }

    public void loadTotalCount(String targetUserId, TotalCountCallback callback) {
        if (targetUserId == null || targetUserId.isEmpty()) {
            callback.onSuccess(0);
            return;
        }

        CacheEntry cached = totalCountCache.get(targetUserId);
        if (cached != null && isFresh(cached.tsMs)) {
            callback.onSuccess(cached.value);
            return;
        }

        LCUser targetUser;
        try {
            targetUser = LCUser.createWithoutData(LCUser.class, targetUserId);
        } catch (LCException e) {
            callback.onError("用户信息无效");
            return;
        }

        LCQuery<ProfileLike> query = LCQuery.getQuery(ProfileLike.class);
        query.whereEqualTo(ProfileLike.KEY_TO_USER, targetUser);
        query.countInBackground().subscribe(new Observer<Integer>() {
            @Override
            public void onSubscribe(Disposable d) {
            }

            @Override
            public void onNext(Integer count) {
                int safe = count != null ? count : 0;
                totalCountCache.put(targetUserId, new CacheEntry(safe, System.currentTimeMillis()));
                callback.onSuccess(safe);
            }

            @Override
            public void onError(Throwable e) {
                if (e instanceof LCException && ((LCException) e).getCode() == 101) {
                    totalCountCache.put(targetUserId, new CacheEntry(0, System.currentTimeMillis()));
                    callback.onSuccess(0);
                    return;
                }
                callback.onError(toErrorMessage("加载失败", e));
            }

            @Override
            public void onComplete() {
            }
        });
    }

    public void like(String targetUserId, LikeCallback callback) {
        LCUser currentUser = LCUser.currentUser();
        if (currentUser == null) {
            callback.onError("请先登录");
            return;
        }
        String fromUserId = currentUser.getObjectId();
        if (fromUserId == null || fromUserId.isEmpty() || targetUserId == null || targetUserId.isEmpty()) {
            callback.onError("用户信息无效");
            return;
        }

        LCUser targetUser;
        try {
            targetUser = LCUser.createWithoutData(LCUser.class, targetUserId);
        } catch (LCException e) {
            callback.onError("用户信息无效");
            return;
        }

        String todayCacheKey = buildTodayKey(fromUserId, targetUserId);
        Date start = getStartOfToday();
        Date end = getStartOfTomorrow();

        LCQuery<ProfileLike> todayQuery = LCQuery.getQuery(ProfileLike.class);
        todayQuery.whereEqualTo(ProfileLike.KEY_FROM_USER, currentUser);
        todayQuery.whereEqualTo(ProfileLike.KEY_TO_USER, targetUser);
        todayQuery.whereGreaterThanOrEqualTo("createdAt", start);
        todayQuery.whereLessThan("createdAt", end);
        todayQuery.countInBackground().subscribe(new Observer<Integer>() {
            @Override
            public void onSubscribe(Disposable d) {
            }

            @Override
            public void onNext(Integer todayCountRaw) {
                int todayCount = todayCountRaw != null ? todayCountRaw : 0;
                if (todayCount >= MAX_LIKES_PER_DAY) {
                    loadTotalCount(targetUserId, new TotalCountCallback() {
                        @Override
                        public void onSuccess(int totalCount) {
                            callback.onLimitReached(todayCount, totalCount);
                        }

                        @Override
                        public void onError(String message) {
                            callback.onLimitReached(todayCount, getCachedTotalOrZero(targetUserId));
                        }
                    });
                    return;
                }

                ProfileLike like = new ProfileLike();
                like.setFromUser(currentUser);
                like.setToUser(targetUser);
                like.saveInBackground().subscribe(new Observer<LCObject>() {
                    @Override
                    public void onSubscribe(Disposable d) {
                    }

                    @Override
                    public void onNext(LCObject lcObject) {
                        int newToday = todayCount + 1;
                        todayCountCache.put(todayCacheKey, new CacheEntry(newToday, System.currentTimeMillis()));

                        CacheEntry before = totalCountCache.get(targetUserId);
                        int beforeValue = before != null ? before.value : 0;
                        totalCountCache.remove(targetUserId);
                        loadTotalCount(targetUserId, new TotalCountCallback() {
                            @Override
                            public void onSuccess(int totalCount) {
                                callback.onSuccess(newToday, totalCount);
                            }

                            @Override
                            public void onError(String message) {
                                int fallback = beforeValue + 1;
                                totalCountCache.put(targetUserId, new CacheEntry(fallback, System.currentTimeMillis()));
                                callback.onSuccess(newToday, fallback);
                            }
                        });
                    }

                    @Override
                    public void onError(Throwable e) {
                        String msg = toErrorMessage("点赞失败", e);
                        Log.e(TAG, msg, e);
                        callback.onError(msg);
                    }

                    @Override
                    public void onComplete() {
                    }
                });
            }

            @Override
            public void onError(Throwable e) {
                if (e instanceof LCException && ((LCException) e).getCode() == 101) {
                    onNext(0);
                    return;
                }
                String msg = toErrorMessage("点赞失败", e);
                Log.e(TAG, msg, e);
                callback.onError(msg);
            }

            @Override
            public void onComplete() {
            }
        });
    }

    public void invalidateCounts(String targetUserId) {
        if (targetUserId != null && !targetUserId.isEmpty()) {
            totalCountCache.remove(targetUserId);
        }
        LCUser currentUser = LCUser.currentUser();
        if (currentUser != null) {
            String fromId = currentUser.getObjectId();
            if (fromId != null && !fromId.isEmpty() && targetUserId != null && !targetUserId.isEmpty()) {
                todayCountCache.remove(buildTodayKey(fromId, targetUserId));
            }
        }
    }

    private static boolean isFresh(long tsMs) {
        return System.currentTimeMillis() - tsMs <= CACHE_TTL_MS;
    }

    private static String buildTodayKey(String fromUserId, String targetUserId) {
        return fromUserId + "->" + targetUserId;
    }

    private static int getCachedTotalOrZero(String targetUserId) {
        CacheEntry cached = totalCountCache.get(targetUserId);
        return cached != null ? cached.value : 0;
    }

    private static Date getStartOfToday() {
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTime();
    }

    private static Date getStartOfTomorrow() {
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        calendar.add(Calendar.DAY_OF_YEAR, 1);
        return calendar.getTime();
    }

    private static String toErrorMessage(String fallback, Throwable e) {
        if (e instanceof LCException) {
            LCException le = (LCException) e;
            String msg = le.getMessage();
            if (msg == null) msg = "";
            return fallback + "（" + le.getCode() + "）" + (msg.isEmpty() ? "" : "：" + msg);
        }
        String msg = e != null ? e.getMessage() : null;
        if (msg == null || msg.isEmpty()) {
            return fallback;
        }
        return fallback + "：" + msg;
    }
}
