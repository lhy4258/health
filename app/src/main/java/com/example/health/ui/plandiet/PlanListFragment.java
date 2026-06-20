package com.example.health.ui.plandiet;

import android.app.DatePickerDialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.example.health.R;
import com.example.health.data.model.PlanRecord;
import com.example.health.ui.PlanDietFragment;
import com.google.android.material.button.MaterialButtonToggleGroup;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import cn.leancloud.LCObject;
import cn.leancloud.LCQuery;
import cn.leancloud.LCUser;
import io.reactivex.Observer;
import io.reactivex.disposables.Disposable;

/**
 * 运动计划列表，支持日计划（可编辑四种运动的分钟数）和周计划（自动汇总）。
 * 保存日计划时自动更新对应周的周计划数据。
 * 实现 PlanDietFragment.PlanDietPage 接口，响应 FAB 按钮点击。
 */
public class PlanListFragment extends Fragment implements PlanDietFragment.PlanDietPage {

    private RecyclerView recyclerView;
    private PlanAdapter adapter;
    private MaterialButtonToggleGroup toggleGroup;
    private int currentFilter = 0;
    private SwipeRefreshLayout swipeRefreshLayout;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_plan_list, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        swipeRefreshLayout = view.findViewById(R.id.swipeRefreshLayout);
        recyclerView = view.findViewById(R.id.recyclerViewPlans);
        toggleGroup = view.findViewById(R.id.toggleGroupPlanTime);

        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new PlanAdapter();
        recyclerView.setAdapter(adapter);

        if (swipeRefreshLayout != null) {
            swipeRefreshLayout.setOnRefreshListener(() -> loadPlans(true));
        }

        toggleGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) return;
            if (checkedId == R.id.btnPlanAll) {
                currentFilter = 0;
            } else if (checkedId == R.id.btnPlanWeek) {
                currentFilter = 1;
            }
            loadPlans();
        });

        loadPlans(false);
    }

    @Override
    public void onAddItem() {
        if (currentFilter == 1) {
            Toast.makeText(requireContext(), "周计划由每日计划自动汇总，无法手动添加", Toast.LENGTH_SHORT).show();
        } else {
            showEditDialog(null);
        }
    }

    private void loadPlans() {
        loadPlans(false);
    }

    private void loadPlans(boolean fromRefresh) {
        LCUser currentUser = LCUser.currentUser();
        if (currentUser == null) {
            if (fromRefresh && swipeRefreshLayout != null) {
                swipeRefreshLayout.setRefreshing(false);
            }
            return;
        }

        LCQuery<PlanRecord> query = new LCQuery<>("PlanRecord");
        query.whereEqualTo(PlanRecord.ATTR_USER, currentUser);

        if (currentFilter == 0) {
            query.whereEqualTo(PlanRecord.ATTR_PLAN_TYPE, PlanRecord.TYPE_DAILY);
        } else {
            query.whereEqualTo(PlanRecord.ATTR_PLAN_TYPE, PlanRecord.TYPE_WEEKLY);
        }

        query.orderByDescending(PlanRecord.ATTR_DATE);

        query.findInBackground().subscribe(new Observer<List<PlanRecord>>() {
            @Override
            public void onSubscribe(Disposable d) {
            }

            @Override
            public void onNext(List<PlanRecord> planRecords) {
                if (isAdded()) {
                    adapter.setItems(planRecords);
                }
                if (fromRefresh && swipeRefreshLayout != null) {
                    swipeRefreshLayout.setRefreshing(false);
                }
            }

            @Override
            public void onError(Throwable e) {
                if (fromRefresh && swipeRefreshLayout != null) {
                    swipeRefreshLayout.setRefreshing(false);
                }
            }

            @Override
            public void onComplete() {
            }
        });
    }

    private void showEditDialog(@Nullable PlanRecord record) {
        View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_plan_edit, null, false);
        TextView textDate = dialogView.findViewById(R.id.textPlanDate);
        TextView editRemark = dialogView.findViewById(R.id.editPlanRemark);
        TextView editMinutesWalk = dialogView.findViewById(R.id.editMinutesWalk);
        TextView editMinutesRun = dialogView.findViewById(R.id.editMinutesRun);
        TextView editMinutesRide = dialogView.findViewById(R.id.editMinutesRide);
        TextView editMinutesFitness = dialogView.findViewById(R.id.editMinutesFitness);

        Calendar calendar = Calendar.getInstance();
        if (record != null && record.getDate() > 0) {
            calendar.setTimeInMillis(record.getDate());
        }
        long[] selectedTime = new long[]{calendar.getTimeInMillis()};
        final long[] originalDate = new long[]{record != null ? record.getDate() : 0L};

        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        textDate.setText(dateFormat.format(new Date(selectedTime[0])));

        textDate.setOnClickListener(v -> {
            Calendar c = Calendar.getInstance();
            c.setTimeInMillis(selectedTime[0]);
            DatePickerDialog picker = new DatePickerDialog(requireContext(), (view1, year, month, dayOfMonth) -> {
                Calendar chosen = Calendar.getInstance();
                chosen.set(year, month, dayOfMonth, 0, 0, 0);
                chosen.set(Calendar.MILLISECOND, 0);
                selectedTime[0] = chosen.getTimeInMillis();
                textDate.setText(dateFormat.format(chosen.getTime()));
            }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH));
            picker.show();
        });

        if (record != null) {
            if (record.getRemark() != null) {
                editRemark.setText(record.getRemark());
            }
            int walking = record.getWalkingMinutes();
            int running = record.getRunningMinutes();
            int ride = record.getRideMinutes();
            int fitness = record.getFitnessMinutes();
            if (walking > 0) {
                editMinutesWalk.setText(String.valueOf(walking));
            }
            if (running > 0) {
                editMinutesRun.setText(String.valueOf(running));
            }
            if (ride > 0) {
                editMinutesRide.setText(String.valueOf(ride));
            }
            if (fitness > 0) {
                editMinutesFitness.setText(String.valueOf(fitness));
            }
        }

        TextView titleView = new TextView(requireContext());
        titleView.setText(record == null ? "添加计划" : "编辑计划");
        titleView.setGravity(Gravity.CENTER);
        titleView.setTextSize(18);
        titleView.setPadding(32, 40, 32, 32);

        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setCustomTitle(titleView)
                .setView(dialogView)
                .setPositiveButton("保存", null)
                .setNegativeButton("取消", (d, which) -> d.dismiss())
                .create();

        dialog.setOnShowListener(d -> {
            if (dialog.getWindow() != null) {
                dialog.getWindow().setBackgroundDrawableResource(R.drawable.bg_dialog_round_white);
            }
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                String remark = editRemark.getText().toString().trim();
                int walkingMinutes = parseMinutes(editMinutesWalk.getText().toString().trim());
                int runningMinutes = parseMinutes(editMinutesRun.getText().toString().trim());
                int rideMinutes = parseMinutes(editMinutesRide.getText().toString().trim());
                int fitnessMinutes = parseMinutes(editMinutesFitness.getText().toString().trim());

                LCUser currentUser = LCUser.currentUser();
                if (currentUser == null) {
                    Toast.makeText(requireContext(), "请先登录", Toast.LENGTH_SHORT).show();
                    return;
                }

                PlanRecord target = record != null ? record : new PlanRecord();
                target.setRemark(remark);
                target.setDate(selectedTime[0]);
                target.setUser(currentUser);
                target.setPlanType(PlanRecord.TYPE_DAILY);
                target.setWalkingMinutes(walkingMinutes);
                target.setRunningMinutes(runningMinutes);
                target.setRideMinutes(rideMinutes);
                target.setFitnessMinutes(fitnessMinutes);
                if (target.getTitle() == null || target.getTitle().isEmpty()) {
                    target.setTitle("每日计划");
                }

                target.saveInBackground().subscribe(new Observer<LCObject>() {
                    @Override
                    public void onSubscribe(Disposable d2) {
                    }

                    @Override
                    public void onNext(LCObject lcObject) {
                        if (isAdded()) {
                            Toast.makeText(requireContext(), "保存成功", Toast.LENGTH_SHORT).show();
                            updateWeeklyPlan(currentUser, selectedTime[0]);
                            if (originalDate[0] != 0 && originalDate[0] != selectedTime[0]) {
                                updateWeeklyPlan(currentUser, originalDate[0]);
                            }
                            loadPlans();
                        }
                    }

                    @Override
                    public void onError(Throwable e) {
                        if (isAdded()) {
                            Toast.makeText(requireContext(), "保存失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        }
                    }

                    @Override
                    public void onComplete() {
                        dialog.dismiss();
                    }
                });
            });
        });

        dialog.show();
    }

    private int parseMinutes(String value) {
        if (value == null || value.isEmpty()) {
            return 0;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private void updateWeeklyPlan(LCUser user, long dayMillis) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(dayMillis);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        int dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK);
        int firstDayOfWeek = calendar.getFirstDayOfWeek();
        int diff = dayOfWeek - firstDayOfWeek;
        if (diff < 0) {
            diff += 7;
        }
        calendar.add(Calendar.DAY_OF_YEAR, -diff);
        long weekStart = calendar.getTimeInMillis();
        calendar.add(Calendar.DAY_OF_YEAR, 7);
        long weekEnd = calendar.getTimeInMillis();

        LCQuery<PlanRecord> dailyQuery = new LCQuery<>("PlanRecord");
        dailyQuery.whereEqualTo(PlanRecord.ATTR_USER, user);
        dailyQuery.whereEqualTo(PlanRecord.ATTR_PLAN_TYPE, PlanRecord.TYPE_DAILY);
        dailyQuery.whereGreaterThanOrEqualTo(PlanRecord.ATTR_DATE, weekStart);
        dailyQuery.whereLessThan(PlanRecord.ATTR_DATE, weekEnd);

        dailyQuery.findInBackground().subscribe(new Observer<List<PlanRecord>>() {
            @Override
            public void onSubscribe(Disposable d) {
            }

            @Override
            public void onNext(List<PlanRecord> dailyPlans) {
                if (dailyPlans == null || dailyPlans.isEmpty()) {
                    LCQuery<PlanRecord> weeklyQuery = new LCQuery<>("PlanRecord");
                    weeklyQuery.whereEqualTo(PlanRecord.ATTR_USER, user);
                    weeklyQuery.whereEqualTo(PlanRecord.ATTR_PLAN_TYPE, PlanRecord.TYPE_WEEKLY);
                    weeklyQuery.whereGreaterThanOrEqualTo(PlanRecord.ATTR_DATE, weekStart);
                    weeklyQuery.whereLessThan(PlanRecord.ATTR_DATE, weekEnd);
                    weeklyQuery.findInBackground().subscribe(new Observer<List<PlanRecord>>() {
                        @Override
                        public void onSubscribe(Disposable d) {
                        }

                        @Override
                        public void onNext(List<PlanRecord> weekRecords) {
                            if (weekRecords != null) {
                                for (PlanRecord r : weekRecords) {
                                    r.deleteInBackground().subscribe();
                                }
                            }
                        }

                        @Override
                        public void onError(Throwable e) {
                        }

                        @Override
                        public void onComplete() {
                        }
                    });
                    return;
                }

                final int[] totals = new int[4];
                for (PlanRecord r : dailyPlans) {
                    totals[0] += r.getWalkingMinutes();
                    totals[1] += r.getRunningMinutes();
                    totals[2] += r.getRideMinutes();
                    totals[3] += r.getFitnessMinutes();
                }

                LCQuery<PlanRecord> weeklyQuery = new LCQuery<>("PlanRecord");
                weeklyQuery.whereEqualTo(PlanRecord.ATTR_USER, user);
                weeklyQuery.whereEqualTo(PlanRecord.ATTR_PLAN_TYPE, PlanRecord.TYPE_WEEKLY);
                weeklyQuery.whereGreaterThanOrEqualTo(PlanRecord.ATTR_DATE, weekStart);
                weeklyQuery.whereLessThan(PlanRecord.ATTR_DATE, weekEnd);
                weeklyQuery.findInBackground().subscribe(new Observer<List<PlanRecord>>() {
                    @Override
                    public void onSubscribe(Disposable d) {
                    }

                    @Override
                    public void onNext(List<PlanRecord> weekRecords) {
                        PlanRecord weekRecord;
                        if (weekRecords != null && !weekRecords.isEmpty()) {
                            weekRecord = weekRecords.get(0);
                        } else {
                            weekRecord = new PlanRecord();
                            weekRecord.setUser(user);
                        }
                        weekRecord.setPlanType(PlanRecord.TYPE_WEEKLY);
                        weekRecord.setDate(weekStart);
                        weekRecord.setTitle("每周计划");
                        weekRecord.setWalkingMinutes(totals[0]);
                        weekRecord.setRunningMinutes(totals[1]);
                        weekRecord.setRideMinutes(totals[2]);
                        weekRecord.setFitnessMinutes(totals[3]);
                        weekRecord.saveInBackground().subscribe();
                    }

                    @Override
                    public void onError(Throwable e) {
                    }

                    @Override
                    public void onComplete() {
                    }
                });
            }

            @Override
            public void onError(Throwable e) {
            }

            @Override
            public void onComplete() {
            }
        });
    }

    private class PlanAdapter extends RecyclerView.Adapter<PlanViewHolder> {

        private final List<PlanRecord> items = new ArrayList<>();
        private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());

        void setItems(List<PlanRecord> list) {
            items.clear();
            if (list != null) {
                items.addAll(list);
            }
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public PlanViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_plan_record, parent, false);
            return new PlanViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull PlanViewHolder holder, int position) {
            PlanRecord record = items.get(position);
            holder.bind(record);
        }

        @Override
        public int getItemCount() {
            return items.size();
        }
    }

    private class PlanViewHolder extends RecyclerView.ViewHolder {

        private final TextView textTitle;
        private final TextView textDate;
        private final TextView textRemark;

        PlanViewHolder(@NonNull View itemView) {
            super(itemView);
            textTitle = itemView.findViewById(R.id.textTitle);
            textDate = itemView.findViewById(R.id.textDate);
            textRemark = itemView.findViewById(R.id.textRemark);
        }

        void bind(PlanRecord record) {
            String planType = record.getPlanType();
            boolean isWeekly = PlanRecord.TYPE_WEEKLY.equals(planType);

            int walking = record.getWalkingMinutes();
            int running = record.getRunningMinutes();
            int ride = record.getRideMinutes();
            int fitness = record.getFitnessMinutes();
            int total = walking + running + ride + fitness;

            if (isWeekly) {
                textTitle.setText("周计划");
                long startMillis = record.getDate();
                if (startMillis > 0) {
                    Calendar c = Calendar.getInstance();
                    c.setTimeInMillis(startMillis);
                    Date startDate = c.getTime();
                    c.add(Calendar.DAY_OF_YEAR, 6);
                    Date endDate = c.getTime();
                    SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
                    textDate.setText(df.format(startDate) + " 至 " + df.format(endDate));
                } else {
                    textDate.setText("");
                }
            } else {
                textTitle.setText("每日计划");
                long dateValue = record.getDate();
                if (dateValue > 0) {
                    textDate.setText(new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date(dateValue)));
                } else {
                    textDate.setText("");
                }
            }

            String summary = "步行 " + walking + " 分钟，跑步 " + running + " 分钟，骑行 " + ride + " 分钟，健身 " + fitness + " 分钟，总计 " + total + " 分钟";
            textRemark.setText(summary);

            if (isWeekly) {
                itemView.setOnClickListener(v -> Toast.makeText(requireContext(), "周计划由每日计划自动汇总，不能编辑", Toast.LENGTH_SHORT).show());
                itemView.setOnLongClickListener(v -> {
                    Toast.makeText(requireContext(), "周计划由每日计划自动汇总，不能删除", Toast.LENGTH_SHORT).show();
                    return true;
                });
            } else {
                itemView.setOnClickListener(v -> showEditDialog(record));
                itemView.setOnLongClickListener(v -> {
                    AlertDialog dialog = new AlertDialog.Builder(requireContext())
                            .setTitle("删除计划")
                            .setMessage("确定删除这条计划吗？")
                            .setPositiveButton("删除", (d, which) -> {
                                record.deleteInBackground().subscribe(new Observer<cn.leancloud.types.LCNull>() {
                                    @Override
                                    public void onSubscribe(Disposable d) {
                                    }

                                    @Override
                                    public void onNext(cn.leancloud.types.LCNull lcNull) {
                                        if (isAdded()) {
                                            Toast.makeText(requireContext(), "已删除", Toast.LENGTH_SHORT).show();
                                            LCUser user = LCUser.currentUser();
                                            if (user != null) {
                                                updateWeeklyPlan(user, record.getDate());
                                            }
                                            loadPlans();
                                        }
                                    }

                                    @Override
                                    public void onError(Throwable e) {
                                        if (isAdded()) {
                                            Toast.makeText(requireContext(), "删除失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                                        }
                                    }

                                    @Override
                                    public void onComplete() {
                                    }
                                });
                            })
                            .setNegativeButton("取消", null)
                            .create();
                    if (dialog.getWindow() != null) {
                        dialog.getWindow().setBackgroundDrawableResource(R.drawable.bg_dialog_round_white);
                    }
                    dialog.setOnShowListener(dlg -> {
                        TextView title = dialog.findViewById(androidx.appcompat.R.id.alertTitle);
                        if (title == null) {
                            title = dialog.findViewById(android.R.id.title);
                        }
                        TextView message = dialog.findViewById(android.R.id.message);
                        if (title != null) {
                            title.setGravity(Gravity.CENTER_HORIZONTAL);
                            title.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
                        }
                        if (message != null) {
                            message.setGravity(Gravity.CENTER_HORIZONTAL);
                            message.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
                        }
                    });
                    dialog.show();
                    return true;
                });
            }
        }
    }
}
