package com.amstudio.fitup;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.airbnb.lottie.LottieAnimationView;
import com.amstudio.fitup.model.NextMilestoneInfo;
import com.amstudio.fitup.model.StreakHistoryItem;
import com.amstudio.fitup.model.StreakReward;
import com.amstudio.fitup.model.StreakStatus;
import com.amstudio.fitup.repository.StreakRepository;
import com.google.android.material.button.MaterialButton;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.Map;

public class StreakActivity extends AppCompatActivity implements StreakRewardAdapter.OnRewardClaimListener {

    // Top Bar
    private View btnBack;
    private ImageButton btnRefresh;

    // States Container
    private View containerLoading;
    private View containerError;
    private View scrollContent;
    private TextView tvErrorMessage;
    private MaterialButton btnRetry;

    // Hero Section
    private View ivStreakFlame;
    private LottieAnimationView lottieFlame;
    private LottieAnimationView lottieSuccess;
    private TextView tvStreakNumber;
    private TextView tvStreakHeadline;
    private TextView tvStreakStatusSub;

    // Card 1: Weekly Streak View
    private View cardWeeklyStreak;
    private View vStreakConnectorPill;
    private TextView tvDaySun, tvDayMon, tvDayTue, tvDayWed, tvDayThu, tvDayFri, tvDaySat;
    private ImageView ivCheckSun, ivCheckMon, ivCheckTue, ivCheckWed, ivCheckThu, ivCheckFri, ivCheckSat;
    private TextView tvNumSun, tvNumMon, tvNumTue, tvNumWed, tvNumThu, tvNumFri, tvNumSat;

    // Card 2: Next Reward Milestone View
    private TextView tvNextRewardHeader;
    private LinearLayout itemMilestone1, itemMilestone2, itemMilestone3, itemMilestone4;
    private TextView tvM1Days, tvM1Value;
    private TextView tvM2Days, tvM2Value;
    private TextView tvM3Days, tvM3Value;
    private TextView tvM4Days, tvM4Value;

    // Bottom Action CTA Button
    private MaterialButton btnDailyCheckin;
    private ProgressBar progressCheckin;

    // Optional Expandable Calendar & Rewards
    private ImageButton btnPrevMonth;
    private ImageButton btnNextMonth;
    private TextView tvCalendarMonthTitle;
    private RecyclerView rvCalendarGrid;
    private StreakCalendarAdapter calendarAdapter;

    private RecyclerView rvStreakRewards;
    private StreakRewardAdapter rewardAdapter;

    // Data State
    private StreakStatus currentStatus;
    private Map<String, StreakHistoryItem> latestHistoryMap;
    private int calendarYear;
    private int calendarMonth; // 1-based (1..12)

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_streak);

        initViews();
        setupAdapters();
        setupClickListeners();

        // Initialize calendar to current month/year
        Calendar now = Calendar.getInstance();
        calendarYear = now.get(Calendar.YEAR);
        calendarMonth = now.get(Calendar.MONTH) + 1;

        updateCalendarMonthTitle();
        fetchStreakData();
    }

    private void initViews() {
        btnBack = findViewById(R.id.btn_back);
        btnRefresh = findViewById(R.id.btn_refresh);

        containerLoading = findViewById(R.id.container_loading);
        containerError = findViewById(R.id.container_error);
        scrollContent = findViewById(R.id.scroll_content);
        tvErrorMessage = findViewById(R.id.tv_error_message);
        btnRetry = findViewById(R.id.btn_retry);

        ivStreakFlame = findViewById(R.id.iv_streak_flame);
        lottieFlame = findViewById(R.id.lottie_flame);
        lottieSuccess = findViewById(R.id.lottie_success);
        tvStreakNumber = findViewById(R.id.tv_streak_number);
        tvStreakHeadline = findViewById(R.id.tv_streak_headline);
        tvStreakStatusSub = findViewById(R.id.tv_streak_status_sub);

        // Card 1 Views
        cardWeeklyStreak = findViewById(R.id.card_weekly_streak);
        vStreakConnectorPill = findViewById(R.id.v_streak_connector_pill);

        tvDaySun = findViewById(R.id.tv_day_sun);
        tvDayMon = findViewById(R.id.tv_day_mon);
        tvDayTue = findViewById(R.id.tv_day_tue);
        tvDayWed = findViewById(R.id.tv_day_wed);
        tvDayThu = findViewById(R.id.tv_day_thu);
        tvDayFri = findViewById(R.id.tv_day_fri);
        tvDaySat = findViewById(R.id.tv_day_sat);

        ivCheckSun = findViewById(R.id.iv_check_sun);
        ivCheckMon = findViewById(R.id.iv_check_mon);
        ivCheckTue = findViewById(R.id.iv_check_tue);
        ivCheckWed = findViewById(R.id.iv_check_wed);
        ivCheckThu = findViewById(R.id.iv_check_thu);
        ivCheckFri = findViewById(R.id.iv_check_fri);
        ivCheckSat = findViewById(R.id.iv_check_sat);

        tvNumSun = findViewById(R.id.tv_num_sun);
        tvNumMon = findViewById(R.id.tv_num_mon);
        tvNumTue = findViewById(R.id.tv_num_tue);
        tvNumWed = findViewById(R.id.tv_num_wed);
        tvNumThu = findViewById(R.id.tv_num_thu);
        tvNumFri = findViewById(R.id.tv_num_fri);
        tvNumSat = findViewById(R.id.tv_num_sat);

        // Card 2 Views
        tvNextRewardHeader = findViewById(R.id.tv_next_reward_header);
        itemMilestone1 = findViewById(R.id.item_milestone_1);
        itemMilestone2 = findViewById(R.id.item_milestone_2);
        itemMilestone3 = findViewById(R.id.item_milestone_3);
        itemMilestone4 = findViewById(R.id.item_milestone_4);

        tvM1Days = findViewById(R.id.tv_m1_days);
        tvM1Value = findViewById(R.id.tv_m1_value);
        tvM2Days = findViewById(R.id.tv_m2_days);
        tvM2Value = findViewById(R.id.tv_m2_value);
        tvM3Days = findViewById(R.id.tv_m3_days);
        tvM3Value = findViewById(R.id.tv_m3_value);
        tvM4Days = findViewById(R.id.tv_m4_days);
        tvM4Value = findViewById(R.id.tv_m4_value);

        // CTA
        btnDailyCheckin = findViewById(R.id.btn_daily_checkin);
        progressCheckin = findViewById(R.id.progress_checkin);

        // Optional views
        btnPrevMonth = findViewById(R.id.btn_prev_month);
        btnNextMonth = findViewById(R.id.btn_next_month);
        tvCalendarMonthTitle = findViewById(R.id.tv_calendar_month_title);
        rvCalendarGrid = findViewById(R.id.rv_calendar_grid);
        rvStreakRewards = findViewById(R.id.rv_streak_rewards);
    }

    private void setupAdapters() {
        if (rvCalendarGrid != null) {
            calendarAdapter = new StreakCalendarAdapter();
            rvCalendarGrid.setLayoutManager(new GridLayoutManager(this, 7));
            rvCalendarGrid.setAdapter(calendarAdapter);
        }

        if (rvStreakRewards != null) {
            rewardAdapter = new StreakRewardAdapter(this);
            rvStreakRewards.setLayoutManager(new LinearLayoutManager(this));
            rvStreakRewards.setAdapter(rewardAdapter);
        }
    }

    private void setupClickListeners() {
        if (btnBack != null) btnBack.setOnClickListener(v -> finish());
        if (btnRefresh != null) btnRefresh.setOnClickListener(v -> fetchStreakData());
        if (btnRetry != null) btnRetry.setOnClickListener(v -> fetchStreakData());

        if (btnDailyCheckin != null) {
            btnDailyCheckin.setOnClickListener(v -> {
                if (currentStatus != null && currentStatus.isCheckedInToday()) {
                    finish(); // Continue back to main screen
                } else {
                    performDailyCheckIn();
                }
            });
        }

        if (btnPrevMonth != null) {
            btnPrevMonth.setOnClickListener(v -> {
                calendarMonth--;
                if (calendarMonth < 1) {
                    calendarMonth = 12;
                    calendarYear--;
                }
                updateCalendarMonthTitle();
                fetchHistoryData();
            });
        }

        if (btnNextMonth != null) {
            btnNextMonth.setOnClickListener(v -> {
                calendarMonth++;
                if (calendarMonth > 12) {
                    calendarMonth = 1;
                    calendarYear++;
                }
                updateCalendarMonthTitle();
                fetchHistoryData();
            });
        }
    }

    private void updateCalendarMonthTitle() {
        if (tvCalendarMonthTitle == null) return;
        String[] monthNames = {"January", "February", "March", "April", "May", "June",
                "July", "August", "September", "October", "November", "December"};
        String monthName = monthNames[Math.max(0, Math.min(11, calendarMonth - 1))];
        tvCalendarMonthTitle.setText(monthName + " " + calendarYear);
    }

    private void showLoading() {
        if (containerLoading != null) containerLoading.setVisibility(View.VISIBLE);
        if (containerError != null) containerError.setVisibility(View.GONE);
        if (scrollContent != null) scrollContent.setVisibility(View.GONE);
    }

    private void showContent() {
        if (containerLoading != null) containerLoading.setVisibility(View.GONE);
        if (containerError != null) containerError.setVisibility(View.GONE);
        if (scrollContent != null) scrollContent.setVisibility(View.VISIBLE);
    }

    private void showError(String message) {
        if (containerLoading != null) containerLoading.setVisibility(View.GONE);
        if (containerError != null) containerError.setVisibility(View.VISIBLE);
        if (scrollContent != null) scrollContent.setVisibility(View.GONE);
        if (message != null && !message.isEmpty() && tvErrorMessage != null) {
            tvErrorMessage.setText(message);
        }
    }

    private void fetchStreakData() {
        showLoading();
        StreakRepository.getInstance().getStreakStatus(new StreakRepository.StreakStatusCallback() {
            @Override
            public void onSuccess(StreakStatus status) {
                if (isFinishing() || isDestroyed()) return;
                currentStatus = status;
                showContent();
                bindStreakStatus(status);
                fetchHistoryData();
            }

            @Override
            public void onError(Exception e) {
                if (isFinishing() || isDestroyed()) return;
                showError("Could not connect to Firebase server: " + e.getLocalizedMessage());
            }
        });
    }

    private void fetchHistoryData() {
        StreakRepository.getInstance().getStreakHistory(calendarYear, calendarMonth, new StreakRepository.StreakHistoryCallback() {
            @Override
            public void onSuccess(Map<String, StreakHistoryItem> historyMap) {
                if (isFinishing() || isDestroyed()) return;
                latestHistoryMap = historyMap;
                String todayStr = currentStatus != null ? currentStatus.getTodayDate() : "";
                if (calendarAdapter != null) {
                    calendarAdapter.setCalendarData(calendarYear, calendarMonth, todayStr, historyMap);
                }
                updateWeeklyStreakView(historyMap);
            }

            @Override
            public void onError(Exception e) {
                if (isFinishing() || isDestroyed()) return;
                updateWeeklyStreakView(null);
            }
        });
    }

    private void bindStreakStatus(StreakStatus status) {
        if (status == null) return;

        int streakVal = status.getCurrentStreak();
        if (tvStreakNumber != null) tvStreakNumber.setText(String.valueOf(streakVal));
        if (tvStreakHeadline != null) tvStreakHeadline.setText("days streak");

        if (btnDailyCheckin != null && tvStreakStatusSub != null) {
            if (status.isCheckedInToday()) {
                btnDailyCheckin.setText("Continue");
                btnDailyCheckin.setEnabled(true);
                tvStreakStatusSub.setText("Awesome job! You've already completed today's check-in!");
            } else {
                btnDailyCheckin.setText("Daily Check-In 🔥");
                btnDailyCheckin.setEnabled(true);
                tvStreakStatusSub.setText("Keep mining daily to increase your rewards.");
            }
        }

        bindMilestonesCard(status);
        updateWeeklyStreakView(latestHistoryMap);

        if (rewardAdapter != null && status.getRewards() != null) {
            rewardAdapter.setRewards(status.getRewards());
        }
    }

    private void updateWeeklyStreakView(Map<String, StreakHistoryItem> historyMap) {
        Calendar cal = Calendar.getInstance();
        int currentDayOfWeek = cal.get(Calendar.DAY_OF_WEEK); // 1 = Sun, 2 = Mon ... 7 = Sat

        // Highlight current day header name in orange
        TextView[] dayHeaderViews = {tvDaySun, tvDayMon, tvDayTue, tvDayWed, tvDayThu, tvDayFri, tvDaySat};
        for (int i = 0; i < 7; i++) {
            if (dayHeaderViews[i] != null) {
                if (i + 1 == currentDayOfWeek) {
                    dayHeaderViews[i].setTextColor(ContextCompat.getColor(this, R.color.electric_blue));
                } else {
                    dayHeaderViews[i].setTextColor(Color.parseColor("#8E8E93"));
                }
            }
        }

        // Move calendar to Sunday of current week
        cal.add(Calendar.DAY_OF_MONTH, -(currentDayOfWeek - 1));

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        ImageView[] checkViews = {ivCheckSun, ivCheckMon, ivCheckTue, ivCheckWed, ivCheckThu, ivCheckFri, ivCheckSat};
        TextView[] numViews = {tvNumSun, tvNumMon, tvNumTue, tvNumWed, tvNumThu, tvNumFri, tvNumSat};

        int consecutiveCheckedDays = 0;
        String todayStr = currentStatus != null && currentStatus.getTodayDate() != null ? currentStatus.getTodayDate() : sdf.format(new Date());

        for (int i = 0; i < 7; i++) {
            String dateStr = sdf.format(cal.getTime());
            int dayNum = cal.get(Calendar.DAY_OF_MONTH);

            boolean isChecked = (historyMap != null && historyMap.containsKey(dateStr)) || (dateStr.equals(todayStr) && currentStatus != null && currentStatus.isCheckedInToday());
            boolean isToday = dateStr.equals(todayStr);

            if (checkViews[i] != null && numViews[i] != null) {
                if (isChecked) {
                    consecutiveCheckedDays = i + 1;
                    checkViews[i].setVisibility(View.VISIBLE);
                    numViews[i].setVisibility(View.GONE);
                    if (isToday) {
                        checkViews[i].setImageResource(R.drawable.ic_check_circle_active);
                    } else {
                        checkViews[i].setImageResource(R.drawable.ic_check_circle_filled);
                    }
                } else {
                    numViews[i].setText(String.valueOf(dayNum));
                    numViews[i].setVisibility(View.VISIBLE);
                    checkViews[i].setVisibility(View.GONE);
                }
            }

            cal.add(Calendar.DAY_OF_MONTH, 1);
        }

        // Adjust streak connector pill width dynamically safely
        if (vStreakConnectorPill != null) {
            final int checkedDaysCount = consecutiveCheckedDays;
            if (checkedDaysCount > 0) {
                vStreakConnectorPill.setVisibility(View.VISIBLE);
                vStreakConnectorPill.post(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    if (cardWeeklyStreak != null && cardWeeklyStreak.getWidth() > 0 && vStreakConnectorPill != null) {
                        float density = getResources().getDisplayMetrics().density;
                        int cardPaddingHorizontal = (int) (32 * density); // 16dp * 2
                        int availableWidth = Math.max(0, cardWeeklyStreak.getWidth() - cardPaddingHorizontal);
                        int itemWidth = availableWidth / 7;
                        int pillWidth = Math.max(0, (itemWidth * checkedDaysCount) + (int) (8 * density));
                        ViewGroup.LayoutParams params = vStreakConnectorPill.getLayoutParams();
                        if (params != null) {
                            params.width = pillWidth;
                            vStreakConnectorPill.setLayoutParams(params);
                        }
                    }
                });
            } else {
                vStreakConnectorPill.setVisibility(View.INVISIBLE);
            }
        }
    }

    private void bindMilestonesCard(StreakStatus status) {
        if (status == null) return;

        NextMilestoneInfo mInfo = status.getNextMilestoneInfo();
        if (tvNextRewardHeader != null) {
            if (mInfo != null && mInfo.getNextMilestoneReward() != null) {
                tvNextRewardHeader.setText("NEXT REWARD: " + mInfo.getNextMilestoneReward().toUpperCase());
            } else {
                tvNextRewardHeader.setText("NEXT REWARD: +200 SP");
            }
        }

        int streak = status.getCurrentStreak();

        // Milestone 1 (7 Days)
        if (tvM1Days != null && tvM1Value != null && itemMilestone1 != null) {
            if (streak >= 7) {
                tvM1Days.setTextColor(Color.parseColor("#666666"));
                tvM1Value.setTextColor(Color.parseColor("#555555"));
                itemMilestone1.setBackground(null);
            } else {
                tvM1Days.setTextColor(Color.WHITE);
                tvM1Value.setTextColor(Color.WHITE);
                itemMilestone1.setBackgroundResource(R.drawable.bg_reward_dashed_box);
            }
        }

        // Milestone 2 (14 Days)
        if (tvM2Days != null && tvM2Value != null && itemMilestone2 != null) {
            if (streak >= 7 && streak < 14) {
                tvM2Days.setTextColor(Color.WHITE);
                tvM2Value.setTextColor(Color.WHITE);
                itemMilestone2.setBackgroundResource(R.drawable.bg_reward_dashed_box);
            } else if (streak >= 14) {
                tvM2Days.setTextColor(Color.parseColor("#666666"));
                tvM2Value.setTextColor(Color.parseColor("#555555"));
                itemMilestone2.setBackground(null);
            } else {
                tvM2Days.setTextColor(Color.parseColor("#8E8E93"));
                tvM2Value.setTextColor(Color.WHITE);
                itemMilestone2.setBackground(null);
            }
        }

        // Milestone 3 (21 Days)
        if (tvM3Days != null && tvM3Value != null && itemMilestone3 != null) {
            if (streak >= 14 && streak < 21) {
                tvM3Days.setTextColor(Color.WHITE);
                tvM3Value.setTextColor(Color.WHITE);
                itemMilestone3.setBackgroundResource(R.drawable.bg_reward_dashed_box);
            } else if (streak >= 21) {
                tvM3Days.setTextColor(Color.parseColor("#666666"));
                tvM3Value.setTextColor(Color.parseColor("#555555"));
                itemMilestone3.setBackground(null);
            } else {
                tvM3Days.setTextColor(Color.parseColor("#8E8E93"));
                tvM3Value.setTextColor(Color.WHITE);
                itemMilestone3.setBackground(null);
            }
        }

        // Milestone 4 (28 Days)
        if (tvM4Days != null && tvM4Value != null && itemMilestone4 != null) {
            if (streak >= 21 && streak < 28) {
                tvM4Days.setTextColor(Color.WHITE);
                tvM4Value.setTextColor(Color.WHITE);
                itemMilestone4.setBackgroundResource(R.drawable.bg_reward_dashed_box);
            } else if (streak >= 28) {
                tvM4Days.setTextColor(Color.parseColor("#666666"));
                tvM4Value.setTextColor(Color.parseColor("#555555"));
                itemMilestone4.setBackground(null);
            } else {
                tvM4Days.setTextColor(Color.parseColor("#8E8E93"));
                tvM4Value.setTextColor(Color.WHITE);
                itemMilestone4.setBackground(null);
            }
        }
    }

    private void performDailyCheckIn() {
        if (btnDailyCheckin != null) {
            btnDailyCheckin.setEnabled(false);
            btnDailyCheckin.setText("");
        }
        if (progressCheckin != null) {
            progressCheckin.setVisibility(View.VISIBLE);
        }

        StreakRepository.getInstance().dailyCheckIn(new StreakRepository.CheckInCallback() {
            @Override
            public void onSuccess(StreakRepository.CheckInResult result) {
                if (isFinishing() || isDestroyed()) return;

                if (progressCheckin != null) progressCheckin.setVisibility(View.GONE);

                if (result.isNewCheckIn) {
                    int startVal = currentStatus != null ? currentStatus.getCurrentStreak() : 0;
                    int endVal = result.currentStreak;
                    animateStreakNumber(startVal, endVal);

                    if (btnDailyCheckin != null) {
                        btnDailyCheckin.setText("Continue");
                        btnDailyCheckin.setEnabled(true);
                    }
                    if (tvStreakHeadline != null) tvStreakHeadline.setText("days streak");
                    if (tvStreakStatusSub != null) tvStreakStatusSub.setText("Streak Increased! Great workout commitment today!");

                    playSuccessAnimation();
                    fetchHistoryData();

                    if (result.unlockedMilestone != null) {
                        showMilestoneCelebrationDialog(result.unlockedMilestone);
                    } else {
                        Toast.makeText(StreakActivity.this, "Check-in successful! Streak increased!", Toast.LENGTH_SHORT).show();
                    }

                    StreakRepository.getInstance().getStreakStatus(new StreakRepository.StreakStatusCallback() {
                        @Override
                        public void onSuccess(StreakStatus updatedStatus) {
                            if (!isFinishing() && !isDestroyed() && updatedStatus != null) {
                                currentStatus = updatedStatus;
                                bindStreakStatus(updatedStatus);
                            }
                        }

                        @Override
                        public void onError(Exception e) {}
                    });

                } else {
                    if (btnDailyCheckin != null) {
                        btnDailyCheckin.setText("Continue");
                        btnDailyCheckin.setEnabled(true);
                    }
                    Toast.makeText(StreakActivity.this, "Already checked in today!", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onError(Exception e) {
                if (isFinishing() || isDestroyed()) return;

                if (progressCheckin != null) progressCheckin.setVisibility(View.GONE);
                if (btnDailyCheckin != null) {
                    btnDailyCheckin.setEnabled(true);
                    btnDailyCheckin.setText("Daily Check-In 🔥");
                }

                Toast.makeText(StreakActivity.this, "Check-in failed: " + e.getLocalizedMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }

    private void animateStreakNumber(int from, int to) {
        ValueAnimator animator = ValueAnimator.ofInt(from, to);
        animator.setDuration(1200);
        animator.addUpdateListener(animation -> {
            if (isFinishing() || isDestroyed()) return;
            int animatedValue = (int) animation.getAnimatedValue();
            if (tvStreakNumber != null) {
                tvStreakNumber.setText(String.valueOf(animatedValue));
            }
        });
        animator.start();
    }

    private void playSuccessAnimation() {
        if (lottieSuccess != null) {
            if (ivStreakFlame != null) ivStreakFlame.setVisibility(View.GONE);
            if (lottieFlame != null) lottieFlame.setVisibility(View.GONE);
            lottieSuccess.setVisibility(View.VISIBLE);
            lottieSuccess.playAnimation();

            lottieSuccess.postDelayed(() -> {
                if (!isFinishing() && !isDestroyed()) {
                    lottieSuccess.setVisibility(View.GONE);
                    if (ivStreakFlame != null) ivStreakFlame.setVisibility(View.VISIBLE);
                }
            }, 2500);
        }
    }

    private void showMilestoneCelebrationDialog(StreakReward reward) {
        if (isFinishing() || isDestroyed()) return;
        Context context = this;
        View dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_milestone_celebration, null);

        TextView tvTitle = dialogView.findViewById(R.id.tv_dialog_title);
        TextView tvSub = dialogView.findViewById(R.id.tv_dialog_sub);
        MaterialButton btnClaim = dialogView.findViewById(R.id.btn_dialog_claim);

        if (reward != null) {
            if (tvTitle != null) tvTitle.setText(reward.getTitle());
            if (tvSub != null) tvSub.setText(reward.getDescription() + "\nUnlocked Reward: " + reward.getRewardValue());
        }

        AlertDialog dialog = new AlertDialog.Builder(context)
                .setView(dialogView)
                .setCancelable(true)
                .create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        if (btnClaim != null) {
            btnClaim.setOnClickListener(v -> {
                dialog.dismiss();
                if (reward != null) {
                    onClaimClick(reward);
                }
            });
        }

        dialog.show();
    }

    @Override
    public void onClaimClick(StreakReward reward) {
        if (reward == null) return;

        Toast.makeText(this, "Claiming reward...", Toast.LENGTH_SHORT).show();
        StreakRepository.getInstance().claimStreakReward(reward.getRewardId(), new StreakRepository.ClaimRewardCallback() {
            @Override
            public void onSuccess(String rewardId, String status, String rewardValue) {
                if (isFinishing() || isDestroyed()) return;

                Toast.makeText(StreakActivity.this, "Reward claimed: " + rewardValue + "! 🎉", Toast.LENGTH_LONG).show();
                fetchStreakData();
            }

            @Override
            public void onError(Exception e) {
                if (isFinishing() || isDestroyed()) return;
                Toast.makeText(StreakActivity.this, "Claim failed: " + e.getLocalizedMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }
}
