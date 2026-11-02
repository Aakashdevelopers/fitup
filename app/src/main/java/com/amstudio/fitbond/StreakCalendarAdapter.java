package com.amstudio.fitbond;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.amstudio.fitbond.model.StreakHistoryItem;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class StreakCalendarAdapter extends RecyclerView.Adapter<StreakCalendarAdapter.CalendarViewHolder> {

    private static class CalendarDayModel {
        boolean isEmpty;
        int dayNumber;
        String dateString; // YYYY-MM-DD
        boolean isCompleted;
        boolean isToday;
        boolean isPastMissed;
        boolean isFuture;
    }

    private final List<CalendarDayModel> dayList = new ArrayList<>();
    private String todayDateStr = "";

    public StreakCalendarAdapter() {}

    public void setCalendarData(int year, int month, String todayDateStr, Map<String, StreakHistoryItem> historyMap) {
        this.todayDateStr = todayDateStr != null ? todayDateStr : "";
        this.dayList.clear();

        if (historyMap == null) {
            historyMap = new HashMap<>();
        }

        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.YEAR, year);
        cal.set(Calendar.MONTH, month - 1); // 0-based month in Calendar
        cal.set(Calendar.DAY_OF_MONTH, 1);

        int firstDayOfWeek = cal.get(Calendar.DAY_OF_WEEK); // 1 = Sunday, 2 = Monday...
        int daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH);

        // Add empty padding days
        int offset = firstDayOfWeek - 1;
        for (int i = 0; i < offset; i++) {
            CalendarDayModel empty = new CalendarDayModel();
            empty.isEmpty = true;
            dayList.add(empty);
        }

        String monthStr = month < 10 ? "0" + month : "" + month;

        for (int day = 1; day <= daysInMonth; day++) {
            CalendarDayModel model = new CalendarDayModel();
            model.isEmpty = false;
            model.dayNumber = day;

            String dayStr = day < 10 ? "0" + day : "" + day;
            model.dateString = year + "-" + monthStr + "-" + dayStr;

            if (historyMap.containsKey(model.dateString)) {
                model.isCompleted = true;
            } else if (model.dateString.equals(this.todayDateStr)) {
                model.isToday = true;
            } else if (!this.todayDateStr.isEmpty() && model.dateString.compareTo(this.todayDateStr) < 0) {
                model.isPastMissed = true;
            } else {
                model.isFuture = true;
            }

            dayList.add(model);
        }

        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public CalendarViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_calendar_day, parent, false);
        return new CalendarViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull CalendarViewHolder holder, int position) {
        CalendarDayModel model = dayList.get(position);
        Context context = holder.itemView.getContext();

        if (model.isEmpty) {
            holder.vDayBg.setVisibility(View.INVISIBLE);
            holder.tvDayNumber.setVisibility(View.INVISIBLE);
            holder.ivDayStatus.setVisibility(View.GONE);
            return;
        }

        holder.vDayBg.setVisibility(View.VISIBLE);
        holder.tvDayNumber.setVisibility(View.VISIBLE);
        holder.tvDayNumber.setText(String.valueOf(model.dayNumber));

        if (model.isCompleted) {
            holder.vDayBg.setBackgroundResource(R.drawable.bg_calendar_day_completed);
            holder.tvDayNumber.setVisibility(View.GONE);
            holder.ivDayStatus.setVisibility(View.VISIBLE);
            holder.ivDayStatus.setImageResource(R.drawable.ic_success_check);
            holder.ivDayStatus.setColorFilter(ContextCompat.getColor(context, R.color.white));
        } else if (model.isToday) {
            holder.vDayBg.setBackgroundResource(R.drawable.bg_calendar_day_today);
            holder.tvDayNumber.setVisibility(View.VISIBLE);
            holder.tvDayNumber.setTextColor(ContextCompat.getColor(context, R.color.electric_blue));
            holder.ivDayStatus.setVisibility(View.GONE);
        } else if (model.isPastMissed) {
            holder.vDayBg.setBackgroundResource(R.drawable.bg_calendar_day_missed);
            holder.tvDayNumber.setVisibility(View.VISIBLE);
            holder.tvDayNumber.setTextColor(ContextCompat.getColor(context, R.color.secondary_text));
            holder.ivDayStatus.setVisibility(View.GONE);
        } else { // Future
            holder.vDayBg.setBackgroundResource(R.color.transparent);
            holder.tvDayNumber.setVisibility(View.VISIBLE);
            holder.tvDayNumber.setTextColor(ContextCompat.getColor(context, R.color.subtle_border));
            holder.ivDayStatus.setVisibility(View.GONE);
        }
    }

    @Override
    public int getItemCount() {
        return dayList.size();
    }

    static class CalendarViewHolder extends RecyclerView.ViewHolder {
        View vDayBg;
        TextView tvDayNumber;
        ImageView ivDayStatus;

        public CalendarViewHolder(@NonNull View itemView) {
            super(itemView);
            vDayBg = itemView.findViewById(R.id.v_day_bg);
            tvDayNumber = itemView.findViewById(R.id.tv_day_number);
            ivDayStatus = itemView.findViewById(R.id.iv_day_status);
        }
    }
}
