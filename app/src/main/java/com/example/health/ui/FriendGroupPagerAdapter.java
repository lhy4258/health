package com.example.health.ui;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.viewpager2.adapter.FragmentStateAdapter;

/**
 * 好友 Tab 的 ViewPager2 Adapter，管理三个子页签：好友列表、群组列表、好友请求。
 */
public class FriendGroupPagerAdapter extends FragmentStateAdapter {
    public FriendGroupPagerAdapter(@NonNull Fragment fragment) {
        super(fragment);
    }

    @NonNull
    @Override
    public Fragment createFragment(int position) {
        if (position == 0) {
            return new FriendListFragment();
        } else if (position == 1) {
            return new GroupListFragment();
        } else {
            return new FriendRequestsFragment();
        }
    }

    @Override
    public int getItemCount() {
        return 3;
    }
}
