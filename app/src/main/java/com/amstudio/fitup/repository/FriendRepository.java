package com.amstudio.fitup.repository;

import android.util.Log;

import com.amstudio.fitup.model.FriendRequest;
import com.amstudio.fitup.model.UserProfile;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldPath;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.functions.FirebaseFunctions;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class FriendRepository {

    private static final String TAG = "FriendRepository";
    private static FriendRepository instance;

    private final FirebaseFirestore db;
    private final FirebaseAuth auth;
    private final FirebaseFunctions functions;

    public interface FriendsListCallback {
        void onSuccess(List<UserProfile> friends);
        void onError(Exception e);
    }

    public interface FriendRequestsCallback {
        void onSuccess(List<FriendRequest> requests);
        void onError(Exception e);
    }

    public interface OperationCallback {
        void onSuccess();
        void onError(Exception e);
    }

    private FriendRepository() {
        this.db = FirebaseFirestore.getInstance();
        this.auth = FirebaseAuth.getInstance();
        this.functions = FirebaseFunctions.getInstance();
    }

    public static synchronized FriendRepository getInstance() {
        if (instance == null) {
            instance = new FriendRepository();
        }
        return instance;
    }

    private String getCurrentUid() {
        return auth.getCurrentUser() != null ? auth.getCurrentUser().getUid() : null;
    }

    public void searchUsersByUsername(String queryText, FriendsListCallback callback) {
        String currentUid = getCurrentUid();
        if (currentUid == null || queryText == null || queryText.trim().isEmpty()) {
            callback.onSuccess(new ArrayList<>());
            return;
        }

        String searchLower = queryText.toLowerCase().trim();

        db.collection("users")
                .whereGreaterThanOrEqualTo("username", searchLower)
                .whereLessThanOrEqualTo("username", searchLower + "\uf8ff")
                .limit(20)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    List<UserProfile> users = new ArrayList<>();
                    for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                        UserProfile user = doc.toObject(UserProfile.class);
                        if (user != null) {
                            if (user.getUid() == null) {
                                user.setUid(doc.getId());
                            }
                            if (!doc.getId().equals(currentUid) && !currentUid.equals(user.getUid())) {
                                users.add(user);
                            }
                        }
                    }
                    if (!users.isEmpty()) {
                        callback.onSuccess(users);
                    } else {
                        searchUsersByDisplayName(searchLower, currentUid, callback);
                    }
                })
                .addOnFailureListener(e -> searchUsersByDisplayName(searchLower, currentUid, callback));
    }

    private void searchUsersByDisplayName(String searchLower, String currentUid, FriendsListCallback callback) {
        db.collection("users")
                .limit(50)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    List<UserProfile> users = new ArrayList<>();
                    for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                        if (doc.getId().equals(currentUid)) continue;
                        UserProfile user = doc.toObject(UserProfile.class);
                        if (user != null) {
                            if (user.getUid() == null) {
                                user.setUid(doc.getId());
                            }
                            String username = user.getUsername() != null ? user.getUsername().toLowerCase() : "";
                            String displayName = user.getDisplayName() != null ? user.getDisplayName().toLowerCase() : "";
                            if (username.contains(searchLower) || displayName.contains(searchLower)) {
                                users.add(user);
                            }
                        }
                    }
                    callback.onSuccess(users);
                })
                .addOnFailureListener(callback::onError);
    }

    public void sendFriendRequest(String targetUid, OperationCallback callback) {
        callFriendshipFunction("SEND_REQUEST", targetUid, null, new OperationCallback() {
            @Override
            public void onSuccess() {
                callback.onSuccess();
            }

            @Override
            public void onError(Exception e) {
                fallbackSendFriendRequest(targetUid, callback);
            }
        });
    }

    private void fallbackSendFriendRequest(String targetUid, OperationCallback callback) {
        String currentUid = getCurrentUid();
        if (currentUid == null || targetUid == null) {
            callback.onError(new Exception("Invalid user ID"));
            return;
        }

        String reqId = db.collection("friendRequests").document().getId();
        Map<String, Object> req = new HashMap<>();
        req.put("requestId", reqId);
        req.put("senderUid", currentUid);
        req.put("receiverUid", targetUid);
        req.put("status", "PENDING");
        req.put("createdAt", FieldValue.serverTimestamp());
        req.put("updatedAt", FieldValue.serverTimestamp());

        db.collection("friendRequests").document(reqId)
                .set(req)
                .addOnSuccessListener(aVoid -> {
                    invalidateFriendsCache();
                    callback.onSuccess();
                })
                .addOnFailureListener(callback::onError);
    }

    public void acceptFriendRequest(String requestId, OperationCallback callback) {
        callFriendshipFunction("ACCEPT_REQUEST", null, requestId, new OperationCallback() {
            @Override
            public void onSuccess() {
                callback.onSuccess();
            }

            @Override
            public void onError(Exception e) {
                fallbackAcceptFriendRequest(requestId, callback);
            }
        });
    }

    private void fallbackAcceptFriendRequest(String requestId, OperationCallback callback) {
        String currentUid = getCurrentUid();
        if (currentUid == null || requestId == null) {
            callback.onError(new Exception("Invalid state"));
            return;
        }

        db.collection("friendRequests").document(requestId).get()
                .addOnSuccessListener(doc -> {
                    if (!doc.exists()) {
                        callback.onError(new Exception("Friend request not found"));
                        return;
                    }

                    String senderUid = doc.getString("senderUid");
                    if (senderUid == null) {
                        callback.onError(new Exception("Invalid sender UID"));
                        return;
                    }

                    String u1 = currentUid.compareTo(senderUid) < 0 ? currentUid : senderUid;
                    String u2 = currentUid.compareTo(senderUid) < 0 ? senderUid : currentUid;
                    String friendshipId = u1 + "_" + u2;

                    db.runTransaction(transaction -> {
                        transaction.update(db.collection("friendRequests").document(requestId),
                                "status", "ACCEPTED",
                                "updatedAt", FieldValue.serverTimestamp());

                        Map<String, Object> fs = new HashMap<>();
                        fs.put("friendshipId", friendshipId);
                        fs.put("user1Uid", u1);
                        fs.put("user2Uid", u2);
                        fs.put("createdAt", FieldValue.serverTimestamp());
                        transaction.set(db.collection("friendships").document(friendshipId), fs);

                        // 3. Increment current user's friend count ONLY (cannot modify another user's document under Firestore security rules)
                        transaction.update(db.collection("users").document(currentUid), "friendsCount", FieldValue.increment(1));
                        return null;
                    }).addOnSuccessListener(aVoid -> {
                        invalidateFriendsCache();
                        UserRepository.getInstance().invalidateCache();
                        callback.onSuccess();
                    }).addOnFailureListener(callback::onError);
                })
                .addOnFailureListener(callback::onError);
    }

    public void rejectFriendRequest(String requestId, OperationCallback callback) {
        callFriendshipFunction("REJECT_REQUEST", null, requestId, new OperationCallback() {
            @Override
            public void onSuccess() {
                callback.onSuccess();
            }

            @Override
            public void onError(Exception e) {
                fallbackRejectFriendRequest(requestId, callback);
            }
        });
    }

    private void fallbackRejectFriendRequest(String requestId, OperationCallback callback) {
        db.collection("friendRequests").document(requestId)
                .update("status", "REJECTED", "updatedAt", FieldValue.serverTimestamp())
                .addOnSuccessListener(aVoid -> callback.onSuccess())
                .addOnFailureListener(callback::onError);
    }

    public void removeFriend(String friendUid, OperationCallback callback) {
        callFriendshipFunction("REMOVE_FRIEND", friendUid, null, new OperationCallback() {
            @Override
            public void onSuccess() {
                callback.onSuccess();
            }

            @Override
            public void onError(Exception e) {
                fallbackRemoveFriend(friendUid, callback);
            }
        });
    }

    private void fallbackRemoveFriend(String friendUid, OperationCallback callback) {
        String currentUid = getCurrentUid();
        if (currentUid == null || friendUid == null) {
            callback.onError(new Exception("Invalid user ID"));
            return;
        }

        String u1 = currentUid.compareTo(friendUid) < 0 ? currentUid : friendUid;
        String u2 = currentUid.compareTo(friendUid) < 0 ? friendUid : currentUid;
        String friendshipId = u1 + "_" + u2;

        db.runTransaction(transaction -> {
            transaction.delete(db.collection("friendships").document(friendshipId));
            transaction.update(db.collection("users").document(currentUid), "friendsCount", FieldValue.increment(-1));
            return null;
        }).addOnSuccessListener(aVoid -> {
            invalidateFriendsCache();
            UserRepository.getInstance().invalidateCache();
            callback.onSuccess();
        }).addOnFailureListener(callback::onError);
    }

    public void blockUser(String targetUid, OperationCallback callback) {
        callFriendshipFunction("BLOCK_USER", targetUid, null, callback);
    }

    public void unblockUser(String targetUid, OperationCallback callback) {
        callFriendshipFunction("UNBLOCK_USER", targetUid, null, callback);
    }

    private List<UserProfile> cachedFriendsList = null;
    private long friendsCacheTimestamp = 0;
    private static final long FRIENDS_CACHE_TTL = 30000; // 30 seconds TTL

    public void invalidateFriendsCache() {
        cachedFriendsList = null;
        friendsCacheTimestamp = 0;
    }

    private void callFriendshipFunction(String action, String targetUid, String requestId, OperationCallback callback) {
        Map<String, Object> data = new HashMap<>();
        data.put("action", action);
        if (targetUid != null) data.put("targetUid", targetUid);
        if (requestId != null) data.put("requestId", requestId);

        functions.getHttpsCallable("processFriendshipOperation")
                .call(data)
                .addOnSuccessListener(result -> {
                    invalidateFriendsCache();
                    callback.onSuccess();
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Friendship operation failed: " + action, e);
                    callback.onError(e);
                });
    }

    public void getAcceptedFriends(FriendsListCallback callback) {
        String currentUid = getCurrentUid();
        if (currentUid == null) {
            callback.onError(new Exception("Not authenticated"));
            return;
        }

        if (cachedFriendsList != null && (System.currentTimeMillis() - friendsCacheTimestamp < FRIENDS_CACHE_TTL)) {
            callback.onSuccess(new ArrayList<>(cachedFriendsList));
            return;
        }

        db.collection("friendships")
                .whereEqualTo("user1Uid", currentUid)
                .get()
                .addOnSuccessListener(q1 -> {
                    Set<String> friendUids = new HashSet<>();
                    for (DocumentSnapshot doc : q1.getDocuments()) {
                        String u2 = doc.getString("user2Uid");
                        if (u2 != null) friendUids.add(u2);
                    }

                    db.collection("friendships")
                            .whereEqualTo("user2Uid", currentUid)
                            .get()
                            .addOnSuccessListener(q2 -> {
                                for (DocumentSnapshot doc : q2.getDocuments()) {
                                    String u1 = doc.getString("user1Uid");
                                    if (u1 != null) friendUids.add(u1);
                                }

                                if (friendUids.isEmpty()) {
                                    cachedFriendsList = new ArrayList<>();
                                    friendsCacheTimestamp = System.currentTimeMillis();
                                    callback.onSuccess(new ArrayList<>());
                                    return;
                                }

                                fetchProfilesForUids(new ArrayList<>(friendUids), callback);
                            })
                            .addOnFailureListener(callback::onError);
                })
                .addOnFailureListener(callback::onError);
    }

    private void fetchProfilesForUids(List<String> uids, FriendsListCallback callback) {
        if (uids.isEmpty()) {
            callback.onSuccess(new ArrayList<>());
            return;
        }

        List<Task<QuerySnapshot>> tasks = new ArrayList<>();
        int batchSize = 30;
        for (int i = 0; i < uids.size(); i += batchSize) {
            List<String> batch = uids.subList(i, Math.min(i + batchSize, uids.size()));
            tasks.add(db.collection("users").whereIn(FieldPath.documentId(), batch).get());
        }

        Tasks.whenAllSuccess(tasks).addOnSuccessListener(results -> {
            List<UserProfile> profiles = new ArrayList<>();
            for (Object res : results) {
                QuerySnapshot snapshot = (QuerySnapshot) res;
                for (DocumentSnapshot doc : snapshot.getDocuments()) {
                    UserProfile profile = doc.toObject(UserProfile.class);
                    if (profile != null) {
                        if (profile.getUid() == null) {
                            profile.setUid(doc.getId());
                        }
                        profiles.add(profile);
                    }
                }
            }
            cachedFriendsList = new ArrayList<>(profiles);
            friendsCacheTimestamp = System.currentTimeMillis();
            callback.onSuccess(profiles);
        }).addOnFailureListener(callback::onError);
    }

    public void getPendingFriendRequests(FriendRequestsCallback callback) {
        String currentUid = getCurrentUid();
        if (currentUid == null) {
            callback.onError(new Exception("Not authenticated"));
            return;
        }

        db.collection("friendRequests")
                .whereEqualTo("receiverUid", currentUid)
                .whereEqualTo("status", "PENDING")
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    List<FriendRequest> requests = new ArrayList<>();
                    for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                        FriendRequest req = doc.toObject(FriendRequest.class);
                        if (req != null) {
                            if (req.getRequestId() == null || req.getRequestId().isEmpty()) {
                                req.setRequestId(doc.getId());
                            }
                            requests.add(req);
                        }
                    }
                    callback.onSuccess(requests);
                })
                .addOnFailureListener(callback::onError);
    }
}
