package com.amstudio.fitup.repository;

import android.graphics.Bitmap;
import android.util.Base64;
import android.util.Log;

import androidx.annotation.NonNull;

import com.amstudio.fitup.model.UserProfile;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.functions.FirebaseFunctions;

import java.io.ByteArrayOutputStream;
import java.util.HashMap;
import java.util.Map;

public class UserRepository {

    private static final String TAG = "UserRepository";
    private static UserRepository instance;

    private final FirebaseFirestore db;
    private final FirebaseAuth auth;
    private final FirebaseFunctions functions;

    public interface ProfileCallback {
        void onSuccess(UserProfile profile);
        void onError(Exception e);
    }

    public interface ActionCallback {
        void onSuccess();
        void onError(Exception e);
    }

    public interface UsernameAvailabilityCallback {
        void onResult(boolean isAvailable, boolean isCurrent, String message);
        void onError(Exception e);
    }

    private UserRepository() {
        this.db = FirebaseFirestore.getInstance();
        this.auth = FirebaseAuth.getInstance();
        this.functions = FirebaseFunctions.getInstance();
    }

    public static synchronized UserRepository getInstance() {
        if (instance == null) {
            instance = new UserRepository();
        }
        return instance;
    }

    public FirebaseUser getCurrentUser() {
        return auth.getCurrentUser();
    }

    public String getCurrentUid() {
        FirebaseUser user = auth.getCurrentUser();
        return user != null ? user.getUid() : null;
    }

    public void createProfileForNewUser(String uid, String email, String fullName, String rawUsername, ProfileCallback callback) {
        String cleanUsername = rawUsername.toLowerCase().trim().replaceAll("[^a-z0-9_]", "");
        if (cleanUsername.isEmpty()) {
            cleanUsername = "gymgoer_" + uid.substring(0, 6).toLowerCase();
        }

        final String finalUsername = cleanUsername;

        db.collection("usernames").document(finalUsername).get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful() && task.getResult() != null && task.getResult().exists()) {
                        String mappedUid = task.getResult().getString("uid");
                        if (mappedUid != null && !mappedUid.equals(uid)) {
                            String uniqueUsername = finalUsername + "_" + (System.currentTimeMillis() % 1000);
                            saveUserProfile(uid, email, uniqueUsername, fullName, callback);
                            return;
                        }
                    }
                    saveUserProfile(uid, email, finalUsername, fullName, callback);
                })
                .addOnFailureListener(e -> {
                    Log.w(TAG, "Username check error, proceeding with profile creation", e);
                    saveUserProfile(uid, email, finalUsername, fullName, callback);
                });
    }

    public void updateUserOnlineStatus(boolean isOnline) {
        String uid = getCurrentUid();
        if (uid == null) return;

        Map<String, Object> updates = new HashMap<>();
        updates.put("isOnline", isOnline);
        updates.put("lastActive", FieldValue.serverTimestamp());

        db.collection("users").document(uid)
                .update(updates)
                .addOnFailureListener(e -> Log.w(TAG, "Failed to update online status", e));
    }

    private void saveUserProfile(String uid, String email, String username, String fullName, ProfileCallback callback) {
        UserProfile profile = new UserProfile(uid, email, username, fullName, "Gym enthusiast 💪", null);
        profile.setOnline(true);

        db.runTransaction(transaction -> {
            Map<String, Object> usernameData = new HashMap<>();
            usernameData.put("uid", uid);
            usernameData.put("createdAt", FieldValue.serverTimestamp());
            usernameData.put("updatedAt", FieldValue.serverTimestamp());
            transaction.set(db.collection("usernames").document(username), usernameData);

            transaction.set(db.collection("users").document(uid), profile);
            return profile;
        }).addOnSuccessListener(resultProfile -> {
            Log.d(TAG, "UserProfile created in Firestore: " + uid);
            callback.onSuccess(resultProfile);
        }).addOnFailureListener(e -> {
            Log.e(TAG, "Failed to create UserProfile in Firestore", e);
            callback.onError(e);
        });
    }

    private UserProfile cachedProfile;
    private long profileCacheTimestamp = 0;
    private static final long PROFILE_CACHE_TTL = 60000; // 60 seconds TTL

    public void invalidateCache() {
        cachedProfile = null;
        profileCacheTimestamp = 0;
    }

    public void getUserProfile(String uid, ProfileCallback callback) {
        if (uid == null) {
            callback.onError(new IllegalArgumentException("UID cannot be null"));
            return;
        }

        if (uid.equals(getCurrentUid()) && cachedProfile != null && (System.currentTimeMillis() - profileCacheTimestamp < PROFILE_CACHE_TTL)) {
            callback.onSuccess(cachedProfile);
            return;
        }

        db.collection("users").document(uid).get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        UserProfile profile = doc.toObject(UserProfile.class);
                        if (uid.equals(getCurrentUid())) {
                            cachedProfile = profile;
                            profileCacheTimestamp = System.currentTimeMillis();
                        }
                        callback.onSuccess(profile);
                    } else {
                        callback.onError(new Exception("Profile not found"));
                    }
                })
                .addOnFailureListener(callback::onError);
    }

    public void checkUsernameAvailability(String rawUsername, UsernameAvailabilityCallback callback) {
        String uid = getCurrentUid();
        if (uid == null) {
            callback.onError(new Exception("Not authenticated"));
            return;
        }

        String normalized = rawUsername != null ? rawUsername.toLowerCase().trim().replaceAll("[^a-z0-9_]", "") : "";
        if (normalized.length() < 3 || normalized.length() > 30) {
            callback.onResult(false, false, "Username must be 3-30 characters (letters, numbers, underscores)");
            return;
        }

        Map<String, Object> params = new HashMap<>();
        params.put("username", normalized);

        functions.getHttpsCallable("checkUsernameAvailability")
                .call(params)
                .addOnSuccessListener(result -> {
                    try {
                        Map<String, Object> resMap = (Map<String, Object>) result.getData();
                        if (resMap != null) {
                            boolean available = Boolean.TRUE.equals(resMap.get("available"));
                            boolean isCurrent = Boolean.TRUE.equals(resMap.get("isCurrent"));
                            String reason = (String) resMap.get("reason");
                            callback.onResult(available, isCurrent, reason != null ? reason : (available ? "Username is available!" : "Username taken"));
                        } else {
                            fallbackCheckUsername(normalized, uid, callback);
                        }
                    } catch (Exception e) {
                        fallbackCheckUsername(normalized, uid, callback);
                    }
                })
                .addOnFailureListener(e -> fallbackCheckUsername(normalized, uid, callback));
    }

    private void fallbackCheckUsername(String normalizedUsername, String currentUid, UsernameAvailabilityCallback callback) {
        db.collection("usernames").document(normalizedUsername).get()
                .addOnSuccessListener(doc -> {
                    if (!doc.exists()) {
                        callback.onResult(true, false, "Username is available!");
                    } else {
                        String mappedUid = doc.getString("uid");
                        if (currentUid.equals(mappedUid)) {
                            callback.onResult(true, true, "Your current username");
                        } else {
                            callback.onResult(false, false, "Username @" + normalizedUsername + " is already taken");
                        }
                    }
                })
                .addOnFailureListener(callback::onError);
    }

    public void updateMyProfile(UserProfile updatedProfile, ActionCallback callback) {
        String uid = getCurrentUid();
        if (uid == null) {
            callback.onError(new Exception("Not authenticated"));
            return;
        }

        Map<String, Object> params = new HashMap<>();
        params.put("fullName", updatedProfile.getDisplayName());
        params.put("username", updatedProfile.getUsername());
        params.put("bio", updatedProfile.getBio());
        params.put("gender", updatedProfile.getGender());
        params.put("dateOfBirth", updatedProfile.getDateOfBirth());
        params.put("location", updatedProfile.getLocation());
        params.put("avatarUrl", updatedProfile.getAvatarUrl());

        functions.getHttpsCallable("updateMyProfile")
                .call(params)
                .addOnSuccessListener(result -> {
                    try {
                        Map<String, Object> resMap = (Map<String, Object>) result.getData();
                        if (resMap != null && Boolean.TRUE.equals(resMap.get("success"))) {
                            callback.onSuccess();
                        } else {
                            fallbackUpdateProfile(updatedProfile, callback);
                        }
                    } catch (Exception e) {
                        fallbackUpdateProfile(updatedProfile, callback);
                    }
                })
                .addOnFailureListener(e -> fallbackUpdateProfile(updatedProfile, callback));
    }

    private void fallbackUpdateProfile(UserProfile profile, ActionCallback callback) {
        String uid = getCurrentUid();
        if (uid == null) {
            callback.onError(new Exception("Not authenticated"));
            return;
        }

        String rawUser = profile.getUsername();
        String normalized = rawUser != null ? rawUser.toLowerCase().trim().replaceAll("[^a-z0-9_]", "") : null;

        if (normalized != null && !normalized.isEmpty()) {
            db.collection("usernames").document(normalized).get()
                    .addOnSuccessListener(doc -> {
                        if (doc.exists() && !uid.equals(doc.getString("uid"))) {
                            callback.onError(new Exception("Username @" + normalized + " is already taken"));
                            return;
                        }

                        db.runTransaction(transaction -> {
                            Map<String, Object> usernameMap = new HashMap<>();
                            usernameMap.put("uid", uid);
                            usernameMap.put("updatedAt", FieldValue.serverTimestamp());
                            transaction.set(db.collection("usernames").document(normalized), usernameMap);

                            Map<String, Object> updates = new HashMap<>();
                            if (profile.getDisplayName() != null) updates.put("displayName", profile.getDisplayName());
                            if (profile.getBio() != null) updates.put("bio", profile.getBio());
                            if (profile.getGender() != null) updates.put("gender", profile.getGender());
                            if (profile.getDateOfBirth() != null) updates.put("dateOfBirth", profile.getDateOfBirth());
                            if (profile.getLocation() != null) updates.put("location", profile.getLocation());
                            if (profile.getAvatarUrl() != null) updates.put("avatarUrl", profile.getAvatarUrl());
                            updates.put("username", normalized);
                            updates.put("normalizedUsername", normalized);
                            updates.put("updatedAt", FieldValue.serverTimestamp());

                            transaction.update(db.collection("users").document(uid), updates);
                            return null;
                        }).addOnSuccessListener(aVoid -> callback.onSuccess())
                          .addOnFailureListener(callback::onError);
                    })
                    .addOnFailureListener(callback::onError);
        } else {
            Map<String, Object> updates = new HashMap<>();
            if (profile.getDisplayName() != null) updates.put("displayName", profile.getDisplayName());
            if (profile.getBio() != null) updates.put("bio", profile.getBio());
            if (profile.getGender() != null) updates.put("gender", profile.getGender());
            if (profile.getDateOfBirth() != null) updates.put("dateOfBirth", profile.getDateOfBirth());
            if (profile.getLocation() != null) updates.put("location", profile.getLocation());
            if (profile.getAvatarUrl() != null) updates.put("avatarUrl", profile.getAvatarUrl());
            updates.put("updatedAt", FieldValue.serverTimestamp());

            db.collection("users").document(uid)
                    .update(updates)
                    .addOnSuccessListener(aVoid -> callback.onSuccess())
                    .addOnFailureListener(callback::onError);
        }
    }

    public void updateProfileDetails(String displayName, String bio, ActionCallback callback) {
        updateProfileFullDetails(displayName, bio, null, callback);
    }

    public void updateProfileFullDetails(String displayName, String bio, String newUsername, ActionCallback callback) {
        UserProfile p = new UserProfile();
        p.setDisplayName(displayName);
        p.setBio(bio);
        p.setUsername(newUsername);
        updateMyProfile(p, callback);
    }

    public void uploadAvatarViaProxy(Bitmap bitmap, ProfileCallback callback) {
        String uid = getCurrentUid();
        if (uid == null || bitmap == null) {
            callback.onError(new IllegalArgumentException("Invalid state or bitmap"));
            return;
        }

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, 80, baos);
        String base64Image = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP);

        Map<String, Object> data = new HashMap<>();
        data.put("imageBase64", base64Image);
        data.put("mimeType", "image/jpeg");
        data.put("fileName", "avatar_" + uid + ".jpg");

        functions.getHttpsCallable("uploadPublicImageProxy")
                .call(data)
                .addOnSuccessListener(result -> {
                    try {
                        Map<String, Object> resMap = (Map<String, Object>) result.getData();
                        if (resMap != null && Boolean.TRUE.equals(resMap.get("success"))) {
                            String imgUrl = (String) resMap.get("url");

                            db.collection("users").document(uid)
                                    .update("avatarUrl", imgUrl, "updatedAt", FieldValue.serverTimestamp())
                                    .addOnSuccessListener(aVoid -> getUserProfile(uid, callback))
                                    .addOnFailureListener(callback::onError);
                        } else {
                            callback.onError(new Exception("Proxy upload failed"));
                        }
                    } catch (Exception e) {
                        callback.onError(e);
                    }
                })
                .addOnFailureListener(callback::onError);
    }
}
