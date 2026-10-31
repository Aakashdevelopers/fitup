package com.amstudio.fitup.repository;

import android.util.Log;

import androidx.annotation.NonNull;

import com.amstudio.fitup.model.NextMilestoneInfo;
import com.amstudio.fitup.model.StreakHistoryItem;
import com.amstudio.fitup.model.StreakReward;
import com.amstudio.fitup.model.StreakStatus;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.functions.FirebaseFunctions;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

public class StreakRepository {

    private static final String TAG = "StreakRepository";
    private static StreakRepository instance;

    private final FirebaseFirestore db;
    private final FirebaseFunctions functions;
    private final FirebaseAuth auth;

    public interface StreakStatusCallback {
        void onSuccess(StreakStatus status);
        void onError(Exception e);
    }

    public static class CheckInResult {
        public boolean success;
        public boolean isNewCheckIn;
        public String message;
        public int currentStreak;
        public int longestStreak;
        public int totalCheckIns;
        public String lastCheckInDate;
        public boolean isCheckedInToday;
        public StreakReward unlockedMilestone;
        public NextMilestoneInfo nextMilestoneInfo;
    }

    public interface CheckInCallback {
        void onSuccess(CheckInResult result);
        void onError(Exception e);
    }

    public interface StreakHistoryCallback {
        void onSuccess(Map<String, StreakHistoryItem> historyMap);
        void onError(Exception e);
    }

    public interface ClaimRewardCallback {
        void onSuccess(String rewardId, String status, String rewardValue);
        void onError(Exception e);
    }

    private StreakRepository() {
        this.db = FirebaseFirestore.getInstance();
        this.functions = FirebaseFunctions.getInstance();
        this.auth = FirebaseAuth.getInstance();
    }

    public static synchronized StreakRepository getInstance() {
        if (instance == null) {
            instance = new StreakRepository();
        }
        return instance;
    }

    private String getClientTimezone() {
        try {
            return TimeZone.getDefault().getID();
        } catch (Exception e) {
            return "UTC";
        }
    }

    private String getTodayDateString() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        return sdf.format(new Date());
    }

    private String getYesterdayDateString() {
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_YEAR, -1);
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        return sdf.format(cal.getTime());
    }

    /**
     * Fetches the comprehensive streak status from Cloud Functions, falling back to Firestore.
     */
    @SuppressWarnings("unchecked")
    public void getStreakStatus(@NonNull StreakStatusCallback callback) {
        if (auth.getCurrentUser() == null) {
            callback.onError(new Exception("User not authenticated"));
            return;
        }

        Map<String, Object> data = new HashMap<>();
        data.put("timezone", getClientTimezone());

        functions.getHttpsCallable("getStreakStatus")
                .call(data)
                .addOnSuccessListener(result -> {
                    try {
                        Object resData = result.getData();
                        if (resData instanceof Map) {
                            Map<String, Object> resultMap = (Map<String, Object>) resData;
                            StreakStatus status = parseStreakStatus(resultMap);
                            callback.onSuccess(status);
                        } else {
                            fallbackGetStreakStatus(callback);
                        }
                    } catch (Exception e) {
                        fallbackGetStreakStatus(callback);
                    }
                })
                .addOnFailureListener(e -> fallbackGetStreakStatus(callback));
    }

    private void fallbackGetStreakStatus(@NonNull StreakStatusCallback callback) {
        String uid = auth.getCurrentUser() != null ? auth.getCurrentUser().getUid() : null;
        if (uid == null) {
            callback.onError(new Exception("Not authenticated"));
            return;
        }

        String todayDate = getTodayDateString();

        db.collection("users").document(uid).collection("streak").document("current").get()
                .addOnSuccessListener(doc -> {
                    StreakStatus status = new StreakStatus();
                    status.setUid(uid);
                    status.setTimezone(getClientTimezone());
                    status.setTodayDate(todayDate);

                    int currentStreak = 0;
                    int longestStreak = 0;
                    int totalCheckIns = 0;
                    String lastCheckInDate = "";

                    if (doc.exists()) {
                        Long curVal = doc.getLong("currentStreak");
                        Long longVal = doc.getLong("longestStreak");
                        Long totVal = doc.getLong("totalCheckIns");
                        currentStreak = curVal != null ? curVal.intValue() : 0;
                        longestStreak = longVal != null ? longVal.intValue() : 0;
                        totalCheckIns = totVal != null ? totVal.intValue() : 0;
                        lastCheckInDate = doc.getString("lastCheckInDate") != null ? doc.getString("lastCheckInDate") : "";
                    }

                    status.setCurrentStreak(currentStreak);
                    status.setLongestStreak(longestStreak);
                    status.setTotalCheckIns(totalCheckIns);
                    status.setLastCheckInDate(lastCheckInDate);
                    status.setCheckedInToday(todayDate.equals(lastCheckInDate));

                    status.setNextMilestoneInfo(calculateNextMilestoneInfo(currentStreak));

                    final int finalCurrentStreak = currentStreak;

                    // Fetch user rewards
                    db.collection("users").document(uid).collection("streakRewards").get()
                            .addOnSuccessListener(querySnapshot -> {
                                Map<String, String> userRewards = new HashMap<>();
                                for (DocumentSnapshot rDoc : querySnapshot.getDocuments()) {
                                    String rStatus = rDoc.getString("status");
                                    if (rStatus != null) {
                                        userRewards.put(rDoc.getId(), rStatus);
                                    }
                                }

                                status.setRewards(buildDefaultRewardsList(finalCurrentStreak, userRewards));
                                callback.onSuccess(status);
                            })
                            .addOnFailureListener(e -> {
                                status.setRewards(buildDefaultRewardsList(finalCurrentStreak, new HashMap<>()));
                                callback.onSuccess(status);
                            });
                })
                .addOnFailureListener(callback::onError);
    }

    /**
     * Performs daily check-in via Cloud Functions, falling back to Firestore.
     */
    @SuppressWarnings("unchecked")
    public void dailyCheckIn(@NonNull CheckInCallback callback) {
        if (auth.getCurrentUser() == null) {
            callback.onError(new Exception("User not authenticated"));
            return;
        }

        Map<String, Object> data = new HashMap<>();
        data.put("timezone", getClientTimezone());

        functions.getHttpsCallable("dailyCheckIn")
                .call(data)
                .addOnSuccessListener(result -> {
                    try {
                        Object resData = result.getData();
                        if (resData instanceof Map) {
                            Map<String, Object> resultMap = (Map<String, Object>) resData;
                            CheckInResult res = new CheckInResult();
                            res.success = getBoolean(resultMap, "success", true);
                            res.isNewCheckIn = getBoolean(resultMap, "isNewCheckIn", false);
                            res.message = getString(resultMap, "message", "");
                            res.currentStreak = getInt(resultMap, "currentStreak", 0);
                            res.longestStreak = getInt(resultMap, "longestStreak", 0);
                            res.totalCheckIns = getInt(resultMap, "totalCheckIns", 0);
                            res.lastCheckInDate = getString(resultMap, "lastCheckInDate", "");
                            res.isCheckedInToday = getBoolean(resultMap, "isCheckedInToday", true);

                            if (resultMap.containsKey("unlockedMilestone") && resultMap.get("unlockedMilestone") instanceof Map) {
                                Map<String, Object> uMap = (Map<String, Object>) resultMap.get("unlockedMilestone");
                                res.unlockedMilestone = parseStreakReward(uMap);
                            }

                            if (resultMap.containsKey("nextMilestoneInfo") && resultMap.get("nextMilestoneInfo") instanceof Map) {
                                Map<String, Object> nMap = (Map<String, Object>) resultMap.get("nextMilestoneInfo");
                                res.nextMilestoneInfo = parseNextMilestoneInfo(nMap);
                            }

                            callback.onSuccess(res);
                        } else {
                            fallbackDailyCheckIn(callback);
                        }
                    } catch (Exception e) {
                        fallbackDailyCheckIn(callback);
                    }
                })
                .addOnFailureListener(e -> fallbackDailyCheckIn(callback));
    }

    private void fallbackDailyCheckIn(@NonNull CheckInCallback callback) {
        String uid = auth.getCurrentUser() != null ? auth.getCurrentUser().getUid() : null;
        if (uid == null) {
            callback.onError(new Exception("Not authenticated"));
            return;
        }

        String todayDate = getTodayDateString();
        String yesterdayDate = getYesterdayDateString();

        db.collection("users").document(uid).collection("streak").document("current").get()
                .addOnSuccessListener(doc -> {
                    int currentStreak = 0;
                    int longestStreak = 0;
                    int totalCheckIns = 0;
                    String lastCheckInDate = "";

                    if (doc.exists()) {
                        Long curVal = doc.getLong("currentStreak");
                        Long longVal = doc.getLong("longestStreak");
                        Long totVal = doc.getLong("totalCheckIns");
                        currentStreak = curVal != null ? curVal.intValue() : 0;
                        longestStreak = longVal != null ? longVal.intValue() : 0;
                        totalCheckIns = totVal != null ? totVal.intValue() : 0;
                        lastCheckInDate = doc.getString("lastCheckInDate") != null ? doc.getString("lastCheckInDate") : "";
                    }

                    if (todayDate.equals(lastCheckInDate)) {
                        CheckInResult res = new CheckInResult();
                        res.success = true;
                        res.isNewCheckIn = false;
                        res.message = "Already checked in today!";
                        res.currentStreak = currentStreak;
                        res.longestStreak = longestStreak;
                        res.totalCheckIns = totalCheckIns;
                        res.lastCheckInDate = todayDate;
                        res.isCheckedInToday = true;
                        res.nextMilestoneInfo = calculateNextMilestoneInfo(currentStreak);
                        callback.onSuccess(res);
                        return;
                    }

                    int newCurrentStreak = yesterdayDate.equals(lastCheckInDate) ? currentStreak + 1 : 1;
                    int newLongestStreak = Math.max(longestStreak, newCurrentStreak);
                    int newTotalCheckIns = totalCheckIns + 1;

                    Map<String, Object> streakMap = new HashMap<>();
                    streakMap.put("currentStreak", newCurrentStreak);
                    streakMap.put("longestStreak", newLongestStreak);
                    streakMap.put("totalCheckIns", newTotalCheckIns);
                    streakMap.put("lastCheckInDate", todayDate);
                    streakMap.put("timezone", getClientTimezone());
                    streakMap.put("updatedAt", FieldValue.serverTimestamp());

                    db.collection("users").document(uid).collection("streak").document("current")
                            .set(streakMap)
                            .addOnSuccessListener(aVoid -> {
                                Map<String, Object> historyMap = new HashMap<>();
                                historyMap.put("date", todayDate);
                                historyMap.put("streakValue", newCurrentStreak);
                                historyMap.put("activityType", "DAILY_CHECKIN");
                                historyMap.put("checkedInAt", FieldValue.serverTimestamp());

                                db.collection("users").document(uid).collection("streakHistory").document(todayDate)
                                        .set(historyMap);

                                CheckInResult res = new CheckInResult();
                                res.success = true;
                                res.isNewCheckIn = true;
                                res.message = "Check-in successful! Streak increased!";
                                res.currentStreak = newCurrentStreak;
                                res.longestStreak = newLongestStreak;
                                res.totalCheckIns = newTotalCheckIns;
                                res.lastCheckInDate = todayDate;
                                res.isCheckedInToday = true;
                                res.nextMilestoneInfo = calculateNextMilestoneInfo(newCurrentStreak);

                                callback.onSuccess(res);
                            })
                            .addOnFailureListener(callback::onError);
                })
                .addOnFailureListener(callback::onError);
    }

    /**
     * Retrieves streak history for a specified year and month.
     */
    @SuppressWarnings("unchecked")
    public void getStreakHistory(int year, int month, @NonNull StreakHistoryCallback callback) {
        if (auth.getCurrentUser() == null) {
            callback.onError(new Exception("User not authenticated"));
            return;
        }

        Map<String, Object> data = new HashMap<>();
        data.put("year", year);
        data.put("month", month);

        functions.getHttpsCallable("getStreakHistory")
                .call(data)
                .addOnSuccessListener(result -> {
                    try {
                        Object resData = result.getData();
                        Map<String, StreakHistoryItem> historyMap = new HashMap<>();

                        if (resData instanceof Map) {
                            Map<String, Object> resultMap = (Map<String, Object>) resData;
                            if (resultMap.containsKey("history") && resultMap.get("history") instanceof Map) {
                                Map<String, Object> rawHistory = (Map<String, Object>) resultMap.get("history");
                                if (rawHistory != null) {
                                    for (Map.Entry<String, Object> entry : rawHistory.entrySet()) {
                                        if (entry.getValue() instanceof Map) {
                                            Map<String, Object> itemMap = (Map<String, Object>) entry.getValue();
                                            StreakHistoryItem item = new StreakHistoryItem();
                                            item.setDate(entry.getKey());
                                            item.setStreakValue(getInt(itemMap, "streakValue", 0));
                                            item.setActivityType(getString(itemMap, "activityType", "DAILY_CHECKIN"));
                                            historyMap.put(entry.getKey(), item);
                                        }
                                    }
                                }
                            }
                        }
                        callback.onSuccess(historyMap);
                    } catch (Exception e) {
                        fallbackGetStreakHistory(year, month, callback);
                    }
                })
                .addOnFailureListener(e -> fallbackGetStreakHistory(year, month, callback));
    }

    private void fallbackGetStreakHistory(int year, int month, @NonNull StreakHistoryCallback callback) {
        String uid = auth.getCurrentUser() != null ? auth.getCurrentUser().getUid() : null;
        if (uid == null) {
            callback.onError(new Exception("Not authenticated"));
            return;
        }

        String monthStr = month < 10 ? "0" + month : "" + month;
        String startPrefix = year + "-" + monthStr + "-01";
        String endPrefix = year + "-" + monthStr + "-31";

        db.collection("users").document(uid).collection("streakHistory")
                .whereGreaterThanOrEqualTo("date", startPrefix)
                .whereLessThanOrEqualTo("date", endPrefix)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    Map<String, StreakHistoryItem> historyMap = new HashMap<>();
                    for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                        String date = doc.getId();
                        Long val = doc.getLong("streakValue");
                        String act = doc.getString("activityType");

                        StreakHistoryItem item = new StreakHistoryItem();
                        item.setDate(date);
                        item.setStreakValue(val != null ? val.intValue() : 1);
                        item.setActivityType(act != null ? act : "DAILY_CHECKIN");
                        historyMap.put(date, item);
                    }
                    callback.onSuccess(historyMap);
                })
                .addOnFailureListener(e -> callback.onSuccess(new HashMap<>()));
    }

    /**
     * Claims an unlocked milestone reward.
     */
    @SuppressWarnings("unchecked")
    public void claimStreakReward(@NonNull String rewardId, @NonNull ClaimRewardCallback callback) {
        if (auth.getCurrentUser() == null) {
            callback.onError(new Exception("User not authenticated"));
            return;
        }

        Map<String, Object> data = new HashMap<>();
        data.put("rewardId", rewardId);

        functions.getHttpsCallable("claimStreakReward")
                .call(data)
                .addOnSuccessListener(result -> {
                    try {
                        Object resData = result.getData();
                        if (resData instanceof Map) {
                            Map<String, Object> resultMap = (Map<String, Object>) resData;
                            String rId = getString(resultMap, "rewardId", rewardId);
                            String status = getString(resultMap, "status", "CLAIMED");
                            String val = getString(resultMap, "rewardValue", "");
                            callback.onSuccess(rId, status, val);
                        } else {
                            fallbackClaimStreakReward(rewardId, callback);
                        }
                    } catch (Exception e) {
                        fallbackClaimStreakReward(rewardId, callback);
                    }
                })
                .addOnFailureListener(e -> fallbackClaimStreakReward(rewardId, callback));
    }

    private void fallbackClaimStreakReward(@NonNull String rewardId, @NonNull ClaimRewardCallback callback) {
        String uid = auth.getCurrentUser() != null ? auth.getCurrentUser().getUid() : null;
        if (uid == null) {
            callback.onError(new Exception("Not authenticated"));
            return;
        }

        Map<String, Object> rewardMap = new HashMap<>();
        rewardMap.put("rewardId", rewardId);
        rewardMap.put("status", "CLAIMED");
        rewardMap.put("claimedAt", FieldValue.serverTimestamp());

        db.collection("users").document(uid).collection("streakRewards").document(rewardId)
                .set(rewardMap, SetOptions.merge())
                .addOnSuccessListener(aVoid -> callback.onSuccess(rewardId, "CLAIMED", "Reward Claimed!"))
                .addOnFailureListener(callback::onError);
    }

    // --- Helper Milestone Calculations ---

    private NextMilestoneInfo calculateNextMilestoneInfo(int currentStreak) {
        NextMilestoneInfo info = new NextMilestoneInfo();
        int[] milestones = {3, 7, 14, 30, 60, 100};
        String[] titles = {"3-Day Novice Flame 🔥", "7-Day Iron Warrior 🏋️‍♂️", "14-Day Silver Streak ⚡", "30-Day Gold Champion 👑", "60-Day Platinum Legend 💎", "100-Day Diamond Titan 🏆"};
        String[] rewards = {"Bronze Flame Badge", "Iron Warrior Title", "Silver Profile Frame", "Gold Champion Badge", "Platinum Legend Title", "Diamond Titan Frame"};

        for (int i = 0; i < milestones.length; i++) {
            if (currentStreak < milestones[i]) {
                int prevM = i > 0 ? milestones[i - 1] : 0;
                int totalSteps = milestones[i] - prevM;
                int currentProg = currentStreak - prevM;
                int percent = Math.min(100, Math.max(0, Math.round((currentProg / (float) totalSteps) * 100)));

                info.setNextMilestone(milestones[i]);
                info.setNextMilestoneTitle(titles[i]);
                info.setNextMilestoneReward(rewards[i]);
                info.setDaysRemaining(milestones[i] - currentStreak);
                info.setProgressPercent(percent);
                return info;
            }
        }

        info.setNextMilestone(100);
        info.setNextMilestoneTitle("Max Milestone Reached! 👑");
        info.setNextMilestoneReward("Titan Legend Status");
        info.setDaysRemaining(0);
        info.setProgressPercent(100);
        return info;
    }

    private List<StreakReward> buildDefaultRewardsList(int currentStreak, Map<String, String> userRewards) {
        List<StreakReward> list = new ArrayList<>();
        int[] milestones = {3, 7, 14, 30, 60, 100};
        String[] rewardIds = {"milestone_3", "milestone_7", "milestone_14", "milestone_30", "milestone_60", "milestone_100"};
        String[] types = {"BADGE", "TITLE", "FRAME", "BADGE", "TITLE", "FRAME"};
        String[] titles = {"3-Day Novice Flame 🔥", "7-Day Iron Warrior 🏋️‍♂️", "14-Day Silver Streak ⚡", "30-Day Gold Champion 👑", "60-Day Platinum Legend 💎", "100-Day Diamond Titan 🏆"};
        String[] descs = {"Completed 3 consecutive workout check-ins!", "Conquered a full week of continuous workout check-ins!", "Maintained a 2-week active workout streak!", "Achieved a full 1-month fitness streak milestone!", "60 consecutive days of unwavering workout discipline!", "Ultimate century streak achieved! You are a Gym Titan!"};
        String[] values = {"Bronze Flame Badge", "Iron Warrior Title", "Silver Profile Frame", "Gold Champion Badge", "Platinum Legend Title", "Diamond Titan Frame"};

        for (int i = 0; i < milestones.length; i++) {
            StreakReward reward = new StreakReward();
            reward.setRewardId(rewardIds[i]);
            reward.setMilestone(milestones[i]);
            reward.setRewardType(types[i]);
            reward.setTitle(titles[i]);
            reward.setDescription(descs[i]);
            reward.setRewardValue(values[i]);

            String customStatus = userRewards.get(rewardIds[i]);
            if (customStatus != null) {
                reward.setStatus(customStatus);
            } else if (currentStreak >= milestones[i]) {
                reward.setStatus("UNLOCKED");
            } else {
                reward.setStatus("LOCKED");
            }

            list.add(reward);
        }

        return list;
    }

    // --- Helper Parsing Methods ---

    @SuppressWarnings("unchecked")
    private StreakStatus parseStreakStatus(Map<String, Object> map) {
        StreakStatus status = new StreakStatus();
        if (map == null) return status;

        status.setUid(getString(map, "uid", ""));
        status.setCurrentStreak(getInt(map, "currentStreak", 0));
        status.setLongestStreak(getInt(map, "longestStreak", 0));
        status.setTotalCheckIns(getInt(map, "totalCheckIns", 0));
        status.setLastCheckInDate(getString(map, "lastCheckInDate", ""));
        status.setTodayDate(getString(map, "todayDate", ""));
        status.setCheckedInToday(getBoolean(map, "isCheckedInToday", false));
        status.setTimezone(getString(map, "timezone", "UTC"));

        if (map.containsKey("nextMilestoneInfo") && map.get("nextMilestoneInfo") instanceof Map) {
            status.setNextMilestoneInfo(parseNextMilestoneInfo((Map<String, Object>) map.get("nextMilestoneInfo")));
        }

        if (map.containsKey("rewards") && map.get("rewards") instanceof List) {
            List<Map<String, Object>> rawList = (List<Map<String, Object>>) map.get("rewards");
            List<StreakReward> rewardList = new ArrayList<>();
            if (rawList != null) {
                for (Map<String, Object> rMap : rawList) {
                    if (rMap != null) {
                        rewardList.add(parseStreakReward(rMap));
                    }
                }
            }
            status.setRewards(rewardList);
        }

        return status;
    }

    private NextMilestoneInfo parseNextMilestoneInfo(Map<String, Object> map) {
        NextMilestoneInfo info = new NextMilestoneInfo();
        if (map == null) return info;

        info.setNextMilestone(getInt(map, "nextMilestone", 100));
        info.setNextMilestoneTitle(getString(map, "nextMilestoneTitle", ""));
        info.setNextMilestoneReward(getString(map, "nextMilestoneReward", ""));
        info.setDaysRemaining(getInt(map, "daysRemaining", 0));
        info.setProgressPercent(getInt(map, "progressPercent", 0));
        return info;
    }

    private StreakReward parseStreakReward(Map<String, Object> map) {
        StreakReward reward = new StreakReward();
        if (map == null) return reward;

        reward.setRewardId(getString(map, "rewardId", ""));
        reward.setMilestone(getInt(map, "milestone", 0));
        reward.setRewardType(getString(map, "rewardType", "BADGE"));
        reward.setRewardValue(getString(map, "rewardValue", ""));
        reward.setTitle(getString(map, "title", ""));
        reward.setDescription(getString(map, "description", ""));
        reward.setStatus(getString(map, "status", "LOCKED"));
        reward.setClaimedAt(map.get("claimedAt"));
        return reward;
    }

    private String getString(Map<String, Object> map, String key, String def) {
        if (map != null && map.containsKey(key) && map.get(key) != null) {
            return String.valueOf(map.get(key));
        }
        return def;
    }

    private int getInt(Map<String, Object> map, String key, int def) {
        if (map != null && map.containsKey(key) && map.get(key) != null) {
            try {
                Object val = map.get(key);
                if (val instanceof Number) {
                    return ((Number) val).intValue();
                }
            } catch (Exception ignored) {}
        }
        return def;
    }

    private boolean getBoolean(Map<String, Object> map, String key, boolean def) {
        if (map != null && map.containsKey(key) && map.get(key) != null) {
            try {
                Object val = map.get(key);
                if (val instanceof Boolean) {
                    return (Boolean) val;
                }
            } catch (Exception ignored) {}
        }
        return def;
    }
}
