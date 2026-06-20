package com.example.health.ui;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.PopupWindow;
import android.view.LayoutInflater;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.example.health.R;
import com.example.health.data.model.Moment;
import com.example.health.data.model.Favorite;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import cn.leancloud.LCException;
import cn.leancloud.LCObject;
import cn.leancloud.LCQuery;
import cn.leancloud.LCUser;
import io.reactivex.Observer;
import io.reactivex.disposables.Disposable;

/**
 * 社区动态列表页。展示当前用户或指定用户发布的动态，支持上拉加载更多和点赞功能。
 * 通过 EXTRA_USER_ID 可指定查看他人动态（只读模式），否则默认展示自己的动态。
 */
public class MomentsActivity extends AppCompatActivity implements MomentsAdapter.OnMomentInteractionListener {

    private SwipeRefreshLayout swipeRefreshLayout;
    private RecyclerView recyclerView;
    private TextView emptyView;
    private FloatingActionButton fabAddMoment;
    private MomentsAdapter adapter;
    private List<Moment> momentList = new ArrayList<>();
    
    private ActivityResultLauncher<Intent> publishLauncher;
    private boolean isLoading = false;
    private boolean hasMore = true;
    private static final int PAGE_SIZE = 10;
    
    public static final String EXTRA_USER_ID = "user_id";
    public static final String EXTRA_USER_NAME = "user_name";
    private String targetUserId;
    private String targetUserName;
    
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_moments);
        
        targetUserId = getIntent().getStringExtra(EXTRA_USER_ID);
        targetUserName = getIntent().getStringExtra(EXTRA_USER_NAME);
        
        publishLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK) {
                        loadMoments(true);
                    }
                });

        initViews();
        setupRecyclerView();
        loadMoments(true);
    }
    
    private void initViews() {
        swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout);
        recyclerView = findViewById(R.id.recyclerView);
        emptyView = findViewById(R.id.emptyView);
        fabAddMoment = findViewById(R.id.fabAddMoment);
        swipeRefreshLayout.setColorSchemeColors(Color.TRANSPARENT, Color.TRANSPARENT, Color.TRANSPARENT);
        swipeRefreshLayout.setProgressBackgroundColorSchemeColor(Color.TRANSPARENT);
        swipeRefreshLayout.setEnabled(false);
        
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayShowTitleEnabled(false);
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> finish());
        
        fabAddMoment.setOnClickListener(v -> {
            Intent intent = new Intent(MomentsActivity.this, PublishMomentActivity.class);
            publishLauncher.launch(intent);
        });
        
        if (targetUserId != null) {
            fabAddMoment.setVisibility(View.GONE);
            TextView tvTitle = findViewById(R.id.tvTitle);
            if (tvTitle != null && targetUserName != null) {
                tvTitle.setText(targetUserName + "的动态");
            }
        }
    }
    
    private void setupRecyclerView() {
        adapter = new MomentsAdapter();
        if (targetUserId != null) {
            adapter.setReadOnly(true);
        }
        adapter.setOnMomentInteractionListener(this);
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        recyclerView.setLayoutManager(layoutManager);
        recyclerView.setAdapter(adapter);
        
        recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                if (dy > 0) { // Scrolling down
                    int visibleItemCount = layoutManager.getChildCount();
                    int totalItemCount = layoutManager.getItemCount();
                    int firstVisibleItemPosition = layoutManager.findFirstVisibleItemPosition();
                    
                    if (!isLoading && hasMore) {
                        if ((visibleItemCount + firstVisibleItemPosition) >= totalItemCount
                                && firstVisibleItemPosition >= 0) {
                            loadMoments(false);
                        }
                    }
                }
            }
        });
    }
    
    private void loadMoments(boolean isRefresh) {
        if (isLoading) return;
        isLoading = true;
        
        LCUser currentUser = LCUser.currentUser();
        if (currentUser == null) {
            isLoading = false;
            Toast.makeText(this, "请先登录", Toast.LENGTH_SHORT).show();
            return;
        }

        LCQuery<Moment> query = LCQuery.getQuery(Moment.class);
        query.include("author");
        query.include(Moment.KEY_IMAGES);
        
        if (targetUserId != null) {
            try {
                LCUser targetUser = LCUser.createWithoutData(LCUser.class, targetUserId);
                query.whereEqualTo(Moment.KEY_AUTHOR, targetUser);
            } catch (LCException e) {
                e.printStackTrace();
            }
        } else {
            query.whereEqualTo(Moment.KEY_AUTHOR, currentUser);
        }
        
        query.orderByDescending("createdAt");
        query.limit(PAGE_SIZE);
        
        if (isRefresh) {
            hasMore = true;
        } else {
            if (!momentList.isEmpty()) {
                Moment lastMoment = momentList.get(momentList.size() - 1);
                Date lastDate = lastMoment.getCreatedAt();
                if (lastDate != null) {
                    query.whereLessThan("createdAt", lastDate);
                }
            }
        }
        
        query.findInBackground().subscribe(new Observer<List<Moment>>() {
            @Override
            public void onSubscribe(Disposable d) {
            }

            @Override
            public void onNext(List<Moment> moments) {
                if (isRefresh) {
                    momentList.clear();
                    adapter.clear();
                }
                
                if (moments != null && !moments.isEmpty()) {
                    momentList.addAll(moments);
                    if (isRefresh) {
                        adapter.setMoments(new ArrayList<>(moments));
                    } else {
                        adapter.addMoments(moments);
                    }
                    
                    if (moments.size() < PAGE_SIZE) {
                        hasMore = false;
                    }
                } else {
                    hasMore = false;
                }
                
                updateEmptyView();
                isLoading = false;
            }

            @Override
            public void onError(Throwable e) {
                isLoading = false;
                Toast.makeText(MomentsActivity.this, "加载失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onComplete() {
            }
        });
    }
    
    private void updateEmptyView() {
        if (momentList.isEmpty()) {
            emptyView.setVisibility(View.VISIBLE);
            recyclerView.setVisibility(View.GONE);
        } else {
            emptyView.setVisibility(View.GONE);
            recyclerView.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public void onLikeClick(Moment moment, int position) {
        LCUser currentUser = LCUser.currentUser();
        if (currentUser == null) {
            Toast.makeText(this, "请先登录", Toast.LENGTH_SHORT).show();
            return;
        }
        
        List<String> likes = moment.getLikes();
        if (likes == null) {
            likes = new ArrayList<>();
        }
        
        String userId = currentUser.getObjectId();
        boolean isLiked = likes.contains(userId);
        
        if (isLiked) {
            likes.remove(userId);
        } else {
            likes.add(userId);
        }
        
        moment.setLikes(likes);
        // Optimistic update
        adapter.notifyItemChanged(position);
        
        moment.saveInBackground().subscribe(new Observer<LCObject>() {
            @Override
            public void onSubscribe(Disposable d) {}

            @Override
            public void onNext(LCObject lcObject) {
                // Success
            }

            @Override
            public void onError(Throwable e) {
                // Revert on error
                if (isLiked) {
                    moment.getLikes().add(userId);
                } else {
                    moment.getLikes().remove(userId);
                }
                adapter.notifyItemChanged(position);
                Toast.makeText(MomentsActivity.this, "操作失败", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onComplete() {}
        });
    }

    @Override
    public void onCommentClick(Moment moment, int position) {
        Intent intent = new Intent(this, MomentDetailActivity.class);
        intent.putExtra(MomentDetailActivity.EXTRA_MOMENT_ID, moment.getObjectId());
        startActivity(intent);
    }

    @Override
    public void onItemClick(Moment moment, int position) {
        Intent intent = new Intent(this, MomentDetailActivity.class);
        intent.putExtra(MomentDetailActivity.EXTRA_MOMENT_ID, moment.getObjectId());
        startActivity(intent);
    }

    @Override
    public void onShareClick(Moment moment, int position) {
        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("text/plain");
        shareIntent.putExtra(Intent.EXTRA_TEXT, moment.getContent());
        startActivity(Intent.createChooser(shareIntent, "分享动态"));
    }

    @Override
    public void onMoreClick(Moment moment, View anchor, int position) {
        showMoreMenu(anchor, moment, position);
    }
    
    private void showMoreMenu(View anchor, Moment moment, int position) {
        LCUser currentUser = LCUser.currentUser();
        boolean isMine = currentUser != null && moment.getAuthor() != null &&
                currentUser.getObjectId().equals(moment.getAuthor().getObjectId());

        View contentView = LayoutInflater.from(this).inflate(R.layout.view_moment_more_menu, null);
        PopupWindow popup = new PopupWindow(contentView,
                RecyclerView.LayoutParams.WRAP_CONTENT,
                RecyclerView.LayoutParams.WRAP_CONTENT,
                true);
        popup.setBackgroundDrawable(new ColorDrawable(0x00000000));
        popup.setOutsideTouchable(true);

        contentView.setAlpha(0f);
        contentView.setScaleX(0.95f);
        contentView.setScaleY(0.95f);
        contentView.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(200)
                .start();

        TextView menuDelete = contentView.findViewById(R.id.menuDelete);
        TextView menuFavorite = contentView.findViewById(R.id.menuFavorite);

        menuDelete.setOnClickListener(v -> {
            popup.dismiss();
            if (isMine) {
                deleteMoment(moment, position);
            } else {
                Toast.makeText(this, "只能删除自己的动态", Toast.LENGTH_SHORT).show();
            }
        });

        menuFavorite.setOnClickListener(v -> {
            popup.dismiss();
            addToFavorite(moment);
        });

        contentView.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
        int popupWidth = contentView.getMeasuredWidth();
        int popupHeight = contentView.getMeasuredHeight();

        int[] location = new int[2];
        anchor.getLocationOnScreen(location);
        int anchorCenterY = location[1] + anchor.getHeight() / 2;
        int x = location[0] - popupWidth;
        int y = anchorCenterY - popupHeight / 2;

        popup.showAtLocation(anchor, Gravity.START | Gravity.TOP, x, y);
    }
    
    private void addToFavorite(Moment moment) {
        LCUser currentUser = LCUser.currentUser();
        if (currentUser == null) {
            Toast.makeText(this, "请先登录", Toast.LENGTH_SHORT).show();
            return;
        }

        LCQuery<Favorite> query = LCQuery.getQuery(Favorite.class);
        query.whereEqualTo(Favorite.KEY_USER, currentUser);
        query.whereEqualTo(Favorite.KEY_MOMENT, moment);
        query.limit(1);

        query.findInBackground().subscribe(new Observer<List<Favorite>>() {
            @Override
            public void onSubscribe(Disposable d) {}

            @Override
            public void onNext(List<Favorite> favorites) {
                if (favorites != null && !favorites.isEmpty()) {
                    Toast.makeText(MomentsActivity.this, "已在收藏", Toast.LENGTH_SHORT).show();
                    openFavoritePage();
                    return;
                }

                Favorite favorite = new Favorite();
                favorite.setUser(currentUser);
                favorite.setMoment(moment);
                favorite.setType(Favorite.TYPE_MOMENT);
                favorite.setSourceMomentId(moment.getObjectId());
                favorite.setText(moment.getContent());
                favorite.setImages(moment.getImages());
                favorite.saveInBackground().subscribe(new Observer<LCObject>() {
                    @Override
                    public void onSubscribe(Disposable d) {}

                    @Override
                    public void onNext(LCObject lcObject) {
                        Toast.makeText(MomentsActivity.this, "已收藏", Toast.LENGTH_SHORT).show();
                        openFavoritePage();
                    }

                    @Override
                    public void onError(Throwable e) {
                        Toast.makeText(MomentsActivity.this, "收藏失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    }

                    @Override
                    public void onComplete() {}
                });
            }

            @Override
            public void onError(Throwable e) {
                if (e instanceof LCException && ((LCException) e).getCode() == 101) {
                    Favorite favorite = new Favorite();
                    favorite.setUser(currentUser);
                    favorite.setMoment(moment);
                    favorite.saveInBackground().subscribe(new Observer<LCObject>() {
                        @Override
                        public void onSubscribe(Disposable d) {}

                        @Override
                        public void onNext(LCObject lcObject) {
                            Toast.makeText(MomentsActivity.this, "已收藏", Toast.LENGTH_SHORT).show();
                            openFavoritePage();
                        }

                        @Override
                        public void onError(Throwable saveError) {
                            Toast.makeText(MomentsActivity.this, "收藏失败: " + saveError.getMessage(), Toast.LENGTH_SHORT).show();
                        }

                        @Override
                        public void onComplete() {}
                    });
                    return;
                }
                Toast.makeText(MomentsActivity.this, "收藏失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onComplete() {}
        });
    }

    private void openFavoritePage() {
        Intent intent = new Intent(this, FavoriteActivity.class);
        startActivity(intent);
    }
    
    private void deleteMoment(Moment moment, int position) {
        moment.deleteInBackground().subscribe(new Observer<cn.leancloud.types.LCNull>() {
            @Override
            public void onSubscribe(Disposable d) {}

            @Override
            public void onNext(cn.leancloud.types.LCNull lcNull) {
                momentList.remove(moment);
                adapter.setMoments(momentList);
                updateEmptyView();
                Toast.makeText(MomentsActivity.this, "已删除", Toast.LENGTH_SHORT).show();
            }   

            @Override
            public void onError(Throwable e) {
                Toast.makeText(MomentsActivity.this, "删除失败", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onComplete() {}
        });
    }

    @Override
    public void onAvatarClick(LCUser user) {
        Intent intent = new Intent(this, UserProfileActivity.class);
        intent.putExtra(UserProfileActivity.EXTRA_USER_ID, user.getObjectId());
        startActivity(intent);
    }
}
