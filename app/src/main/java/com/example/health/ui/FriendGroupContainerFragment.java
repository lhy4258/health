package com.example.health.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.viewpager2.widget.ViewPager2;

import com.example.health.R;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

/**
 * 好友与群组容器 Fragment，使用 ViewPager2 + TabLayout 实现"好友列表"、"群组列表"、"好友请求"三个 Tab 的滑动切换。
 */
public class FriendGroupContainerFragment extends Fragment {
    private TabLayout tabLayout;
    private ViewPager2 viewPager;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_friend_group_container, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        tabLayout = view.findViewById(R.id.tabLayoutInner);
        viewPager = view.findViewById(R.id.viewPagerInner);
        viewPager.setAdapter(new FriendGroupPagerAdapter(this));
        new TabLayoutMediator(tabLayout, viewPager, (tab, position) -> {
            if (position == 0) {
                tab.setText("好友");
            } else if (position == 1) {
                tab.setText("群组");
            } else {
                tab.setText("好友请求");
            }
        }).attach();
    }
}
