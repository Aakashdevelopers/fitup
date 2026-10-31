package com.amstudio.fitup;

import android.animation.ValueAnimator;
import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
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

import java.util.Calendar;
import java.util.Map;

public class StreakActivity extends AppCompatActivity implements StreakRewardAdapter.OnRewardClaimListener {

    // Top Bar
    private ImageButton btnBack;
    private ImageButton btnRefresh;

    // States Container
    private View containerLoading;
    private View containerError;
    private View scrollContent;
    private TextView tvErrorMessage;
    private MaterialButton btnRetry;

    // Banner Views
    private LottieAnimationView lottieFlame;
    private LottieAnimationView lottieSuccess;
    private TextView tvStreakNumber;
    private TextView tvStreakHeadline;
    private TextView tvStreakStatusSub;
    private MaterialButton btnDailyCheckin;
    private ProgressBar progressCheckin;

    // Stats Overview
    private TextView tvStatCurrentStreak;
    private TextView tvStatLongestStreak;
    private TextView tvStatTotalCheckins;

    // Milestone Section
    private TextView tvMilestoneTitle;
    private TextView tvMilestonePercent;
    private ProgressBar progressMilestone;
    private TextView tvMilestoneSub;

    // Calendar Section
    private ImageButton btnPrevMonth;
    private ImageButton btnNextMonth;
    private TextView tvCalendarMonthTitle;
    private RecyclerView rvCalendarGrid;
    private StreakCalendarAdapter calendarAdapter;

    // Rewards Section
    private RecyclerView rvStreakRewards;
    private StreakRewardAdapter rewardAdapter;

    // Data State
    private StreakStatus currentStatus;
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

        lottieFlame = findViewById(R.id.lottie_flame);
        lottieSuccess = findViewById(R.id.lottie_success);
        tvStreakNumber = findViewById(R.id.tv_streak_number);
        tvStreakHeadline = findViewById(R.id.tv_streak_headline);
        tvStreakStatusSub = findViewById(R.id.tv_streak_status_sub);
        btnDailyCheckin = findViewById(R.id.btn_daily_checkin);
        progressCheckin = findViewById(R.id.progress_checkin);

        tvStatCurrentStreak = findViewById(R.id.tv_stat_current_streak);
        tvStatLongestStreak = findViewById(R.id.tv_stat_longest_streak);
        tvStatTotalCheckins = findViewById(R.id.tv_stat_total_checkins);

        tvMilestoneTitle = findViewById(R.id.tv_milestone_title);
        tvMilestonePercent = findViewById(R.id.tv_milestone_percent);
        progressMilestone = findViewById(R.id.progress_milestone);
        tvMilestoneSub = findViewById(R.id.tv_milestone_sub);

        btnPrevMonth = findViewById(R.id.btn_prev_month);
        btnNextMonth = findViewById(R.id.btn_next_month);
        tvCalendarMonthTitle = findViewById(R.id.tv_calendar_month_title);
        rvCalendarGrid = findViewById(R.id.rv_calendar_grid);

        rvStreakRewards = findViewById(R.id.rv_streak_rewards);
    }

    private void setupAdapters() {
        calendarAdapter = new StreakCalendarAdapter();
        rvCalendarGrid.setLayoutManager(new GridLayoutManager(this, 7));
        rvCalendarGrid.setAdapter(calendarAdapter);

        rewardAdapter = new StreakRewardAdapter(this);
        rvStreakRewards.setLayoutManager(new LinearLayoutManager(this));
        rvStreakRewards.setAdapter(rewardAdapter);
    }

    private void setupClickListeners() {
        btnBack.setOnClickListener(v -> finish());
        btnRefresh.setOnClickListener(v -> fetchStreakData());
        btnRetry.setOnClickListener(v -> fetchStreakData());

        btnDailyCheckin.setOnClickListener(v -> performDailyCheckIn());

        btnPrevMonth.setOnClickListener(v -> {
            calendarMonth--;
            if (calendarMonth < 1) {
                calendarMonth = 12;
                calendarYear--;
            }
            updateCalendarMonthTitle();
            fetchHistoryData();
        });

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

    private void updateCalendarMonthTitle() {
        String[] monthNames = {"January", "February", "March", "April", "May", "June",
                "July", "August", "September", "October", "November", "December"};
        String monthName = monthNames[Math.max(0, Math.min(11, calendarMonth - 1))];
        tvCalendarMonthTitle.setText(monthName + " " + calendarYear);
    }

    private void showLoading() {
        containerLoading.setVisibility(View.VISIBLE);
        containerError.setVisibility(View.GONE);
        scrollContent.setVisibility(View.GONE);
    }

    private void showContent() {
        containerLoading.setVisibility(View.GONE);
        containerError.setVisibility(View.GONE);
        scrollContent.setVisibility(View.VISIBLE);
    }

    private void showError(String message) {
        containerLoading.setVisibility(View.GONE);
        containerError.setVisibility(View.VISIBLE);
        scrollContent.setVisibility(View.GONE);
        if (message != null && !message.isEmpty()) {
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
                String todayStr = currentStatus != null ? currentStatus.getTodayDate() : "";
                calendarAdapter.setCalendarData(calendarYear, calendarMonth, todayStr, historyMap);
            }

            @Override
            public void onError(Exception e) {
                // Calendar silent fallback
            }
        });
    }

    private void bindStreakStatus(StreakStatus status) {
        if (status == null) return;

        int streakVal = status.getCurrentStreak();
        tvStreakNumber.setText(String.valueOf(streakVal));
        tvStreakHeadline.setText("You're on a " + streakVal + "-day streak! 🔥");

        tvStatCurrentStreak.setText(streakVal + " Days");
        tvStatLongestStreak.setText(status.getLongestStreak() + " Days");
        tvStatTotalCheckins.setText(status.getTotalCheckIns() + " Days");

        if (status.isCheckedInToday()) {
            btnDailyCheckin.setText("Checked In Today ✔");
            btnDailyCheckin.setEnabled(false);
            btnDailyCheckin.setBackgroundTintList(getColorStateList(R.color.subtle_border));
            tvStreakStatusSub.setText("Awesome job! You've already completed today's check-in!");
        } else {
            btnDailyCheckin.setText("Daily Check-In 🔥");
            btnDailyCheckin.setEnabled(true);
            btnDailyCheckin.setBackgroundTintList(getColorStateList(R.color.primary_blue));
            tvStreakStatusSub.setText("Check in today to keep your workout streak active!");
        }

        // Milestone Progress
        NextMilestoneInfo mInfo = status.getNextMilestoneInfo();
        if (mInfo != null) {
            tvMilestoneTitle.setText("Next Milestone: " + mInfo.getNextMilestone() + " Days");
            tvMilestonePercent.setText(mInfo.getProgressPercent() + "%");
            progressMilestone.setProgress(mInfo.getProgressPercent());
            tvMilestoneSub.setText(mInfo.getDaysRemaining() + " days remaining to unlock " + mInfo.getNextMilestoneReward() + "!");
        }

        // Rewards
        if (status.getRewards() != null) {
            rewardAdapter.setRewards(status.getRewards());
        }
    }

    private void performDailyCheckIn() {
        // Step 5: Disable button immediately to prevent duplicate taps
        btnDailyCheckin.setEnabled(false);
        btnDailyCheckin.setText("");
        progressCheckin.setVisibility(View.VISIBLE);

        // Step 7: Call dailyCheckIn Cloud Function
        StreakRepository.getInstance().dailyCheckIn(new StreakRepository.CheckInCallback() {
            @Override
            public void onSuccess(StreakRepository.CheckInResult result) {
                if (isFinishing() || isDestroyed()) return;

                progressCheckin.setVisibility(View.GONE);

                if (result.isNewCheckIn) {
                    // Smooth counter increase animation
                    int startVal = currentStatus != null ? currentStatus.getCurrentStreak() : 0;
                    int endVal = result.currentStreak;
                    animateStreakNumber(startVal, endVal);

                    // Update button state
                    btnDailyCheckin.setText("Checked In Today ✔");
                    btnDailyCheckin.setBackgroundTintList(getColorStateList(R.color.subtle_border));
                    tvStreakHeadline.setText("You're on a " + result.currentStreak + "-day streak! 🔥");
                    tvStreakStatusSub.setText("Streak Increased! Great workout commitment today!");

                    // Play Lottie Check-In Success Animation
                    playSuccessAnimation();

                    // Refresh history and stats
                    fetchHistoryData();

                    // If milestone unlocked, show celebration dialog
                    if (result.unlockedMilestone != null) {
                        showMilestoneCelebrationDialog(result.unlockedMilestone);
                    } else {
                        Toast.makeText(StreakActivity.this, "Check-in successful! Streak increased!", Toast.LENGTH_SHORT).show();
                    }

                    // Refresh status in background to update milestone progress
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
                    // Already checked in today
                    btnDailyCheckin.setText("Checked In Today ✔");
                    btnDailyCheckin.setEnabled(false);
                    btnDailyCheckin.setBackgroundTintList(getColorStateList(R.color.subtle_border));
                    Toast.makeText(StreakActivity.this, "Already checked in today!", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onError(Exception e) {
                if (isFinishing() || isDestroyed()) return;

                progressCheckin.setVisibility(View.GONE);
                btnDailyCheckin.setEnabled(true);
                btnDailyCheckin.setText("Daily Check-In 🔥");
                btnDailyCheckin.setBackgroundTintList(getColorStateList(R.color.primary_blue));

                Toast.makeText(StreakActivity.this, "Check-in failed: " + e.getLocalizedMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }

    private void animateStreakNumber(int from, int to) {
        ValueAnimator animator = ValueAnimator.ofInt(from, to);
        animator.setDuration(1200);
        animator.addUpdateListener(animation -> {
            int animatedValue = (int) animation.getAnimatedValue();
            tvStreakNumber.setText(String.valueOf(animatedValue));
        });
        animator.start();
    }

    private void playSuccessAnimation() {
        if (lottieSuccess != null && lottieFlame != null) {
            lottieFlame.setVisibility(View.GONE);
            lottieSuccess.setVisibility(View.VISIBLE);
            lottieSuccess.playAnimation();

            // After animation finishes, restore subtle flame
            lottieSuccess.postDelayed(() -> {
                if (!isFinishing() && !isDestroyed()) {
                    lottieSuccess.setVisibility(View.GONE);
                    lottieFlame.setVisibility(View.VISIBLE);
                }
            }, 2500);
        }
    }

    private void showMilestoneCelebrationDialog(StreakReward reward) {
        Context context = this;
        View dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_milestone_celebration, null);

        LottieAnimationView lottieCeleb = dialogView.findViewById(R.id.lottie_celebration);
        TextView tvTitle = dialogView.findViewById(R.id.tv_dialog_title);
        TextView tvSub = dialogView.findViewById(R.id.tv_dialog_sub);
        MaterialButton btnClaim = dialogView.findViewById(R.id.btn_dialog_claim);

        if (reward != null) {
            tvTitle.setText(reward.getTitle());
            tvSub.setText(reward.getDescription() + "\nUnlocked Reward: " + reward.getRewardValue());
        }

        AlertDialog dialog = new AlertDialog.Builder(context)
                .setView(dialogView)
                .setCancelable(true)
                .create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        btnClaim.setOnClickListener(v -> {
            dialog.dismiss();
            if (reward != null) {
                onClaimClick(reward);
            }
        });

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
