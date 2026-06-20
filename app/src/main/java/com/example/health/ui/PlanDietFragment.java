package com.example.health.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.viewpager2.widget.ViewPager2;

import com.example.health.R;
import com.example.health.ui.plandiet.DietListFragment;
import com.example.health.ui.plandiet.PlanListFragment;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

/**
 * 饮食计划 Tab 容器，使用 ViewPager2 切换"计划列表"和"饮食记录"两个子页面。
 * FAB 按钮根据当前选中的 Tab 触发对应页面的添加操作。
 */
public class PlanDietFragment extends Fragment {

    private ViewPager2 viewPager;
    private TextView tabPlan;
    private TextView tabDiet;
    private FloatingActionButton fabAdd;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_plan_diet, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        tabPlan = view.findViewById(R.id.tabPlan);
        tabDiet = view.findViewById(R.id.tabDiet);
        viewPager = view.findViewById(R.id.viewPager);
        fabAdd = view.findViewById(R.id.fabAdd);

        viewPager.setAdapter(new PlanDietPagerAdapter(this));
        viewPager.setOffscreenPageLimit(2);
        viewPager.setPageTransformer((page, position) -> {
            float abs = Math.abs(position);
            float scale = 0.9f + (1f - abs) * 0.1f;
            page.setScaleX(scale);
            page.setScaleY(scale);
            page.setAlpha(0.5f + (1f - abs) * 0.5f);
        });

        tabPlan.setOnClickListener(v -> viewPager.setCurrentItem(0, true));
        tabDiet.setOnClickListener(v -> viewPager.setCurrentItem(1, true));

        viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                super.onPageSelected(position);
                updateTabs(position);
            }
        });

        fabAdd.setOnClickListener(v -> {
            int index = viewPager.getCurrentItem();
            Fragment fragment = getChildFragmentManager().findFragmentByTag("f" + index);
            if (fragment instanceof PlanDietPage) {
                ((PlanDietPage) fragment).onAddItem();
            }
        });

        updateTabs(0);
    }

    private void updateTabs(int index) {
        int selectedColor = requireContext().getColor(R.color.primary_color);
        int unselectedColor = requireContext().getColor(R.color.secondary_text_color);
        int selectedBg = requireContext().getColor(R.color.white);
        int unselectedBg = requireContext().getColor(R.color.background_color);

        if (index == 0) {
            tabPlan.setTextColor(selectedColor);
            tabPlan.setBackgroundColor(selectedBg);
            tabDiet.setTextColor(unselectedColor);
            tabDiet.setBackgroundColor(unselectedBg);
        } else {
            tabDiet.setTextColor(selectedColor);
            tabDiet.setBackgroundColor(selectedBg);
            tabPlan.setTextColor(unselectedColor);
            tabPlan.setBackgroundColor(unselectedBg);
        }
    }

    private static class PlanDietPagerAdapter extends androidx.viewpager2.adapter.FragmentStateAdapter {

        PlanDietPagerAdapter(@NonNull Fragment fragment) {
            super(fragment);
        }

        @NonNull
        @Override
        public Fragment createFragment(int position) {
            if (position == 0) {
                return new PlanListFragment();
            } else {
                return new DietListFragment();
            }
        }

        @Override
        public int getItemCount() {
            return 2;
        }
    }

    public interface PlanDietPage {
        void onAddItem();
    }
}
