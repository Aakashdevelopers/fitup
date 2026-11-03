package com.amstudio.fitbond.repository;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.net.Uri;
import android.util.Base64;
import android.util.Log;

import androidx.annotation.NonNull;

import com.amstudio.fitbond.FitUpApplication;
import com.amstudio.fitbond.model.UserProfile;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.UserProfileChangeRequest;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.functions.FirebaseFunctions;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import org.json.JSONObject;

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

    public void ensureUserProfileExistsForGoogleUser(FirebaseUser user, GoogleSignInAccount account, ProfileCallback callback) {
        if (user == null) {
            callback.onError(new IllegalArgumentException("FirebaseUser cannot be null"));
            return;
        }

        String uid = user.getUid();
        db.collection("users").document(uid).get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        UserProfile profile = doc.toObject(UserProfile.class);
                        if (profile != null) {
                            if (profile.getUid() == null) profile.setUid(uid);
                            updateUserOnlineStatus(true);
                            cachedProfile = profile;
                            profileCacheTimestamp = System.currentTimeMillis();
                            saveProfileToLocalCache(profile);
                            callback.onSuccess(profile);
                        } else {
                            callback.onError(new Exception("Failed to parse profile"));
                        }
                    } else {
                        // Create profile for new Google user
                        String email = user.getEmail() != null && !user.getEmail().isEmpty() ? user.getEmail() : (account != null && account.getEmail() != null ? account.getEmail() : "");
                        String fullName = user.getDisplayName() != null && !user.getDisplayName().isEmpty() ? user.getDisplayName() : (account != null && account.getDisplayName() != null ? account.getDisplayName() : "Gym Member");
                        String avatarUrl = user.getPhotoUrl() != null ? user.getPhotoUrl().toString() : (account != null && account.getPhotoUrl() != null ? account.getPhotoUrl().toString() : null);

                        String rawUsername = email.contains("@") ? email.split("@")[0] : fullName.replace(" ", "_");

                        createProfileForNewUserWithAvatar(uid, email, fullName, rawUsername, avatarUrl, callback);
                    }
                })
                .addOnFailureListener(e -> {
                    Log.w(TAG, "Error checking user profile in Firestore for Google User", e);
                    String email = user.getEmail() != null ? user.getEmail() : "";
                    String fullName = user.getDisplayName() != null ? user.getDisplayName() : "Gym Member";
                    String avatarUrl = user.getPhotoUrl() != null ? user.getPhotoUrl().toString() : null;
                    String rawUsername = email.contains("@") ? email.split("@")[0] : fullName.replace(" ", "_");

                    createProfileForNewUserWithAvatar(uid, email, fullName, rawUsername, avatarUrl, callback);
                });
    }

    public void createProfileForNewUser(String uid, String email, String fullName, String rawUsername, ProfileCallback callback) {
        createProfileForNewUserWithAvatar(uid, email, fullName, rawUsername, null, callback);
    }

    public void createProfileForNewUserWithAvatar(String uid, String email, String fullName, String rawUsername, String avatarUrl, ProfileCallback callback) {
        String cleanUsername = rawUsername.toLowerCase().trim().replaceAll("[^a-z0-9_]", "");
        if (cleanUsername.isEmpty()) {
            cleanUsername = "gymgoer_" + uid.substring(0, Math.min(6, uid.length())).toLowerCase();
        }

        final String finalUsername = cleanUsername;

        db.collection("usernames").document(finalUsername).get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful() && task.getResult() != null && task.getResult().exists()) {
                        String mappedUid = task.getResult().getString("uid");
                        if (mappedUid != null && !mappedUid.equals(uid)) {
                            String uniqueUsername = finalUsername + "_" + (System.currentTimeMillis() % 1000);
                            saveUserProfile(uid, email, uniqueUsername, fullName, avatarUrl, callback);
                            return;
                        }
                    }
                    saveUserProfile(uid, email, finalUsername, fullName, avatarUrl, callback);
                })
                .addOnFailureListener(e -> {
                    Log.w(TAG, "Username check error, proceeding with profile creation", e);
                    saveUserProfile(uid, email, finalUsername, fullName, avatarUrl, callback);
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
        saveUserProfile(uid, email, username, fullName, null, callback);
    }

    private void saveUserProfile(String uid, String email, String username, String fullName, String avatarUrl, ProfileCallback callback) {
        UserProfile profile = new UserProfile(uid, email, username, fullName, "Gym enthusiast 💪", avatarUrl);
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
            if (uid.equals(getCurrentUid())) {
                cachedProfile = resultProfile;
                profileCacheTimestamp = System.currentTimeMillis();
                saveProfileToLocalCache(resultProfile);
            }
            callback.onSuccess(resultProfile);
        }).addOnFailureListener(e -> {
            Log.e(TAG, "Failed to create UserProfile in Firestore", e);
            callback.onError(e);
        });
    }

    private UserProfile cachedProfile;
    private long profileCacheTimestamp = 0;
    private static final long PROFILE_CACHE_TTL = 60000; // 60 seconds TTL
    private static final String PREF_PROFILE_CACHE = "user_profile_cache_pref";

    private void saveProfileToLocalCache(UserProfile profile) {
        if (profile == null || profile.getUid() == null) return;
        try {
            FitUpApplication app = FitUpApplication.getInstance();
            if (app == null) return;
            SharedPreferences prefs = app.getSharedPreferences(PREF_PROFILE_CACHE, Context.MODE_PRIVATE);

            JSONObject json = new JSONObject();
            json.put("uid", profile.getUid());
            json.put("email", profile.getEmail() != null ? profile.getEmail() : "");
            json.put("username", profile.getUsername() != null ? profile.getUsername() : "");
            json.put("normalizedUsername", profile.getNormalizedUsername() != null ? profile.getNormalizedUsername() : "");
            json.put("displayName", profile.getDisplayName() != null ? profile.getDisplayName() : "");
            json.put("bio", profile.getBio() != null ? profile.getBio() : "");
            json.put("avatarUrl", profile.getAvatarUrl() != null ? profile.getAvatarUrl() : "");
            json.put("gender", profile.getGender() != null ? profile.getGender() : "");
            json.put("dateOfBirth", profile.getDateOfBirth() != null ? profile.getDateOfBirth() : "");
            json.put("location", profile.getLocation() != null ? profile.getLocation() : "");
            json.put("friendsCount", profile.getFriendsCount());
            json.put("isOnline", profile.isOnline());
            json.put("isPrivate", profile.isPrivate());

            prefs.edit().putString("profile_" + profile.getUid(), json.toString()).apply();
        } catch (Exception e) {
            Log.w(TAG, "Failed to save profile to local cache", e);
        }
    }

    private UserProfile loadProfileFromLocalCache(String uid) {
        if (uid == null) return null;
        try {
            FitUpApplication app = FitUpApplication.getInstance();
            if (app == null) return null;
            SharedPreferences prefs = app.getSharedPreferences(PREF_PROFILE_CACHE, Context.MODE_PRIVATE);

            String jsonStr = prefs.getString("profile_" + uid, null);
            if (jsonStr == null || jsonStr.isEmpty()) return null;

            JSONObject json = new JSONObject(jsonStr);
            UserProfile profile = new UserProfile();
            profile.setUid(json.optString("uid"));
            profile.setEmail(json.optString("email"));
            profile.setUsername(json.optString("username"));
            profile.setNormalizedUsername(json.optString("normalizedUsername"));
            profile.setDisplayName(json.optString("displayName"));
            profile.setBio(json.optString("bio"));
            profile.setAvatarUrl(json.optString("avatarUrl"));
            profile.setGender(json.optString("gender"));
            profile.setDateOfBirth(json.optString("dateOfBirth"));
            profile.setLocation(json.optString("location"));
            profile.setFriendsCount(json.optLong("friendsCount", 0));
            profile.setOnline(json.optBoolean("isOnline", false));
            profile.setPrivate(json.optBoolean("isPrivate", false));
            return profile;
        } catch (Exception e) {
            Log.w(TAG, "Failed to load profile from local cache", e);
            return null;
        }
    }

    public void invalidateCache() {
        cachedProfile = null;
        profileCacheTimestamp = 0;
    }

    public void getUserProfile(String uid, ProfileCallback callback) {
        if (uid == null) {
            callback.onError(new IllegalArgumentException("UID cannot be null"));
            return;
        }

        boolean isCurrent = uid.equals(getCurrentUid());

        // 1. Check in-memory cache
        if (isCurrent && cachedProfile != null) {
            callback.onSuccess(cachedProfile);
            if (System.currentTimeMillis() - profileCacheTimestamp < PROFILE_CACHE_TTL) {
                return;
            }
        }

        // 2. Check persistent SharedPreferences cache
        if (isCurrent && cachedProfile == null) {
            UserProfile localProfile = loadProfileFromLocalCache(uid);
            if (localProfile != null) {
                cachedProfile = localProfile;
                profileCacheTimestamp = System.currentTimeMillis();
                callback.onSuccess(localProfile);
            }
        }

        // 3. Query Firestore in background
        db.collection("users").document(uid).get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        UserProfile profile = doc.toObject(UserProfile.class);
                        if (profile != null) {
                            if (profile.getUid() == null) profile.setUid(uid);
                            if (isCurrent) {
                                cachedProfile = profile;
                                profileCacheTimestamp = System.currentTimeMillis();
                                saveProfileToLocalCache(profile);
                            }
                            callback.onSuccess(profile);
                        } else {
                            callback.onError(new Exception("Failed to parse profile"));
                        }
                    } else {
                        callback.onError(new Exception("Profile not found"));
                    }
                })
                .addOnFailureListener(e -> {
                    if (!isCurrent || cachedProfile == null) {
                        callback.onError(e);
                    }
                });
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

    private void syncFirebaseAuthProfile(UserProfile updatedProfile) {
        invalidateCache();
        FirebaseUser user = getCurrentUser();
        if (user != null && updatedProfile != null) {
            UserProfileChangeRequest.Builder builder = new UserProfileChangeRequest.Builder();
            boolean hasChanges = false;
            if (updatedProfile.getDisplayName() != null) {
                builder.setDisplayName(updatedProfile.getDisplayName());
                hasChanges = true;
            }
            if (updatedProfile.getAvatarUrl() != null) {
                builder.setPhotoUri(Uri.parse(updatedProfile.getAvatarUrl()));
                hasChanges = true;
            }
            if (hasChanges) {
                user.updateProfile(builder.build());
            }
        }
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
        params.put("isPrivate", updatedProfile.isPrivate());

        functions.getHttpsCallable("updateMyProfile")
                .call(params)
                .addOnSuccessListener(result -> {
                    try {
                        Map<String, Object> resMap = (Map<String, Object>) result.getData();
                        if (resMap != null && Boolean.TRUE.equals(resMap.get("success"))) {
                            syncFirebaseAuthProfile(updatedProfile);
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
                            updates.put("isPrivate", profile.isPrivate());
                            updates.put("username", normalized);
                            updates.put("normalizedUsername", normalized);
                            updates.put("updatedAt", FieldValue.serverTimestamp());

                            transaction.update(db.collection("users").document(uid), updates);
                            return null;
                        }).addOnSuccessListener(aVoid -> {
                            syncFirebaseAuthProfile(profile);
                            callback.onSuccess();
                        }).addOnFailureListener(callback::onError);
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
            updates.put("isPrivate", profile.isPrivate());
            updates.put("updatedAt", FieldValue.serverTimestamp());

            db.collection("users").document(uid)
                    .update(updates)
                    .addOnSuccessListener(aVoid -> {
                        syncFirebaseAuthProfile(profile);
                        callback.onSuccess();
                    })
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

        Bitmap avatarBitmap = scaleBitmapForAvatar(bitmap, 400);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        avatarBitmap.compress(Bitmap.CompressFormat.JPEG, 75, baos);
        byte[] imageBytes = baos.toByteArray();
        String base64Image = Base64.encodeToString(imageBytes, Base64.NO_WRAP);

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
                            saveAvatarUrlToFirestore(uid, imgUrl, callback);
                        } else {
                            fallbackUploadAvatarToStorage(uid, imageBytes, callback);
                        }
                    } catch (Exception e) {
                        fallbackUploadAvatarToStorage(uid, imageBytes, callback);
                    }
                })
                .addOnFailureListener(e -> fallbackUploadAvatarToStorage(uid, imageBytes, callback));
    }

    private void fallbackUploadAvatarToStorage(String uid, byte[] imageBytes, ProfileCallback callback) {
        try {
            StorageReference avatarRef = FirebaseStorage.getInstance().getReference().child("avatars/" + uid + ".jpg");
            avatarRef.putBytes(imageBytes)
                    .addOnSuccessListener(taskSnapshot -> avatarRef.getDownloadUrl().addOnSuccessListener(downloadUrl -> {
                        String imgUrl = downloadUrl.toString();
                        saveAvatarUrlToFirestore(uid, imgUrl, callback);
                    }).addOnFailureListener(e -> saveBase64AvatarToFirestore(uid, imageBytes, callback)))
                    .addOnFailureListener(e -> saveBase64AvatarToFirestore(uid, imageBytes, callback));
        } catch (Exception e) {
            Log.w(TAG, "Storage fallback error, using Base64 Data URL fallback", e);
            saveBase64AvatarToFirestore(uid, imageBytes, callback);
        }
    }

    private void saveBase64AvatarToFirestore(String uid, byte[] imageBytes, ProfileCallback callback) {
        String dataUrl = "data:image/jpeg;base64," + Base64.encodeToString(imageBytes, Base64.NO_WRAP);
        saveAvatarUrlToFirestore(uid, dataUrl, callback);
    }

    private void saveAvatarUrlToFirestore(String uid, String avatarUrl, ProfileCallback callback) {
        db.collection("users").document(uid)
                .update("avatarUrl", avatarUrl, "updatedAt", FieldValue.serverTimestamp())
                .addOnSuccessListener(aVoid -> {
                    invalidateCache();
                    UserProfile p = new UserProfile();
                    p.setAvatarUrl(avatarUrl);
                    syncFirebaseAuthProfile(p);
                    getUserProfile(uid, callback);
                })
                .addOnFailureListener(callback::onError);
    }

    private Bitmap scaleBitmapForAvatar(Bitmap original, int maxDimension) {
        if (original == null) return null;
        int width = original.getWidth();
        int height = original.getHeight();

        if (width <= maxDimension && height <= maxDimension) {
            return original;
        }

        float ratio = (float) width / (float) height;
        int newWidth;
        int newHeight;

        if (width > height) {
            newWidth = maxDimension;
            newHeight = Math.round(maxDimension / ratio);
        } else {
            newHeight = maxDimension;
            newWidth = Math.round(maxDimension * ratio);
        }

        return Bitmap.createScaledBitmap(original, newWidth, newHeight, true);
    }
}
