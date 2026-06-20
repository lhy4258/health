package com.example.health.ui;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.viewpager2.adapter.FragmentStateAdapter;

/**
 * 好友页 ViewPager2 Adapter，管理两个 Tab：聊天消息、好友/群组。
 */
public class FriendsPagerAdapter extends FragmentStateAdapter {
    public FriendsPagerAdapter(@NonNull Fragment fragment) {
        super(fragment);
    }

    @NonNull
    @Override
    public Fragment createFragment(int position) {
        if (position == 0) {
            return new MessagesFragment();
        } else {
            return new FriendGroupContainerFragment();
        }
    }

    @Override
    public int getItemCount() {
        return 2;
    }
}

