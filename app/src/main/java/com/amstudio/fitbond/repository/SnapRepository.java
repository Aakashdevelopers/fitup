package com.amstudio.fitbond.repository;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import com.amstudio.fitbond.model.SnapMetadata;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;
import com.tencent.imsdk.v2.V2TIMMessage;

import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class SnapRepository {

    private static final String TAG = "SnapRepository";
    private static SnapRepository instance;

    private final FirebaseFirestore db;
    private final FirebaseAuth auth;

    public interface SnapUploadCallback {
        void onSuccess(SnapMetadata metadata);
        void onError(Exception e);
    }

    public interface SnapDetailsCallback {
        void onSuccess(SnapMetadata metadata);
        void onError(Exception e);
    }

    private SnapRepository() {
        this.db = FirebaseFirestore.getInstance();
        this.auth = FirebaseAuth.getInstance();
    }

    public static synchronized SnapRepository getInstance() {
        if (instance == null) {
            instance = new SnapRepository();
        }
        return instance;
    }

    private FirebaseStorage getStorageInstance() {
        try {
            return FirebaseStorage.getInstance();
        } catch (Exception e) {
            Log.e(TAG, "Failed to get default FirebaseStorage instance", e);
            return FirebaseStorage.getInstance();
        }
    }

    private String getCurrentUid() {
        return auth.getCurrentUser() != null ? auth.getCurrentUser().getUid() : null;
    }

    public void sendPrivateSnap(Uri localMediaUri, String mediaType, List<String> recipientUids,
                               long durationSeconds, SnapUploadCallback callback) {
        sendPrivateSnap(null, localMediaUri, mediaType, "", recipientUids, durationSeconds, callback);
    }

    public void sendPrivateSnap(Context context, Uri localMediaUri, String mediaType, List<String> recipientUids,
                               long durationSeconds, SnapUploadCallback callback) {
        sendPrivateSnap(context, localMediaUri, mediaType, "", recipientUids, durationSeconds, callback);
    }

    public void sendPrivateSnap(Context context, Uri localMediaUri, String mediaType, String caption, List<String> recipientUids,
                               long durationSeconds, SnapUploadCallback callback) {
        String senderUid = getCurrentUid();
        if (senderUid == null) {
            callback.onError(new Exception("User not authenticated"));
            return;
        }

        if (recipientUids == null || recipientUids.isEmpty()) {
            callback.onError(new IllegalArgumentException("Recipients cannot be empty"));
            return;
        }

        String snapId = UUID.randomUUID().toString();
        String fileExt = SnapMetadata.MEDIA_TYPE_VIDEO.equalsIgnoreCase(mediaType) ? ".mp4" : ".jpg";
        String storagePath = "snaps/" + snapId + "/media" + fileExt;

        FirebaseStorage storageInstance = getStorageInstance();
        StorageReference snapRef = storageInstance.getReference().child(storagePath);

        InputStream inputStream = null;
        if (context != null && localMediaUri != null) {
            try {
                inputStream = context.getContentResolver().openInputStream(localMediaUri);
            } catch (Exception e) {
                Log.w(TAG, "Failed to open InputStream for URI: " + localMediaUri, e);
            }
        }

        UploadTask uploadTask;
        if (inputStream != null) {
            uploadTask = snapRef.putStream(inputStream);
        } else if (localMediaUri != null) {
            uploadTask = snapRef.putFile(localMediaUri);
        } else {
            callback.onError(new IllegalArgumentException("Media URI cannot be null"));
            return;
        }

        uploadTask.addOnSuccessListener(taskSnapshot -> snapRef.getDownloadUrl().addOnSuccessListener(downloadUrl -> {
            String fullMediaUrl = downloadUrl.toString();

            SnapMetadata metadata = new SnapMetadata(
                    snapId,
                    senderUid,
                    recipientUids,
                    mediaType.toUpperCase(),
                    fullMediaUrl,
                    durationSeconds
            );
            metadata.setCaption(caption);

            // Save Snap metadata in Firestore snaps/{snapId}
            db.collection("snaps").document(snapId)
                    .set(metadata)
                    .addOnSuccessListener(aVoid -> {
                        Log.d(TAG, "Snap metadata stored in Firestore: " + snapId);

                        TencentChatManager chatManager = TencentChatManager.getInstance();
                        for (String recipientUid : recipientUids) {
                            saveSnapChatMessageToFirestore(senderUid, recipientUid, snapId, mediaType, fullMediaUrl, durationSeconds, caption);

                            chatManager.sendCustomSnapMessage(
                                    recipientUid,
                                    snapId,
                                    mediaType.toUpperCase(),
                                    fullMediaUrl,
                                    durationSeconds,
                                    null
                            );
                        }

                        callback.onSuccess(metadata);
                    })
                    .addOnFailureListener(callback::onError);

        }).addOnFailureListener(callback::onError)).addOnFailureListener(e -> {
            Log.w(TAG, "Firebase storage upload failed, uploading via ImgBB fallback: " + e.getLocalizedMessage());
            if (context != null && localMediaUri != null) {
                ImgBBUploader.uploadImage(context, localMediaUri, new ImgBBUploader.UploadCallback() {
                    @Override
                    public void onSuccess(String imgBbUrl) {
                        SnapMetadata meta = new SnapMetadata(snapId, senderUid, recipientUids, mediaType.toUpperCase(), imgBbUrl, durationSeconds);
                        meta.setCaption(caption);
                        db.collection("snaps").document(snapId).set(meta);
                        for (String recipientUid : recipientUids) {
                            saveSnapChatMessageToFirestore(senderUid, recipientUid, snapId, mediaType, imgBbUrl, durationSeconds, caption);
                        }
                        callback.onSuccess(meta);
                    }

                    @Override
                    public void onError(Exception ex) {
                        Log.e(TAG, "ImgBB snap upload fallback error: " + ex.getLocalizedMessage());
                        callback.onError(ex);
                    }
                });
            } else {
                callback.onError(e);
            }
        });
    }

    private void saveSnapChatMessageToFirestore(String senderUid, String recipientUid, String snapId,
                                                String mediaType, String mediaUrl, long duration, String caption) {
        if (senderUid == null || recipientUid == null) return;

        String conversationId = senderUid.compareTo(recipientUid) < 0 ? senderUid + "_" + recipientUid : recipientUid + "_" + senderUid;
        String msgId = "snap_" + snapId;

        Map<String, Object> snapMsgDoc = new HashMap<>();
        snapMsgDoc.put("id", snapId);
        snapMsgDoc.put("senderUid", senderUid);
        snapMsgDoc.put("senderName", "You");
        snapMsgDoc.put("text", !android.text.TextUtils.isEmpty(caption) ? caption : ("VIDEO".equalsIgnoreCase(mediaType) ? "Video Snap" : "Snap"));
        snapMsgDoc.put("caption", caption != null ? caption : "");
        snapMsgDoc.put("type", "SNAP");
        snapMsgDoc.put("mediaType", mediaType.toUpperCase());
        snapMsgDoc.put("mediaUrl", mediaUrl);
        snapMsgDoc.put("duration", duration);
        snapMsgDoc.put("status", "SENT");
        snapMsgDoc.put("createdAt", FieldValue.serverTimestamp());

        db.collection("chats").document(conversationId)
                .collection("messages").document(msgId)
                .set(snapMsgDoc);
    }

    public void getSnapMetadata(String snapId, SnapDetailsCallback callback) {
        if (snapId == null || snapId.isEmpty()) {
            callback.onError(new IllegalArgumentException("Snap ID cannot be empty"));
            return;
        }

        String cleanSnapId = snapId.startsWith("snap_") ? snapId.substring(5) : snapId;

        db.collection("snaps").document(cleanSnapId).get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        SnapMetadata metadata = doc.toObject(SnapMetadata.class);
                        callback.onSuccess(metadata);
                    } else {
                        db.collection("snaps").document(snapId).get()
                                .addOnSuccessListener(rawDoc -> {
                                    if (rawDoc.exists()) {
                                        callback.onSuccess(rawDoc.toObject(SnapMetadata.class));
                                    } else {
                                        callback.onError(new Exception("Snap not found or expired"));
                                    }
                                })
                                .addOnFailureListener(callback::onError);
                    }
                })
                .addOnFailureListener(callback::onError);
    }

    public interface SnapActionCallback {
        void onSuccess();
        void onError(Exception e);
    }

    public interface SnapReplayCallback {
        void onSuccess(int newReplayCount, boolean isExpired);
        void onError(Exception e);
    }

    public void incrementSnapReplayCount(String conversationId, String msgId, String snapId, SnapReplayCallback callback) {
        if (conversationId == null || msgId == null) {
            callback.onError(new IllegalArgumentException("Invalid message parameters"));
            return;
        }

        db.collection("chats").document(conversationId)
                .collection("messages").document(msgId)
                .get()
                .addOnSuccessListener(doc -> {
                    if (!doc.exists()) {
                        callback.onError(new Exception("Snap message not found"));
                        return;
                    }

                    Long currentReplaysObj = doc.getLong("replayCount");
                    int currentReplays = currentReplaysObj != null ? currentReplaysObj.intValue() : 0;
                    Boolean isSavedObj = doc.getBoolean("isSaved");
                    boolean isSaved = Boolean.TRUE.equals(isSavedObj);

                    if (currentReplays >= 3) {
                        if (!isSaved) {
                            deleteSnapMediaAndDoc(conversationId, msgId, snapId, doc.getString("mediaUrl"), null);
                        }
                        callback.onSuccess(3, true);
                        return;
                    }

                    int newCount = currentReplays + 1;
                    boolean willExpire = (newCount >= 3) && !isSaved;

                    Map<String, Object> updates = new HashMap<>();
                    updates.put("replayCount", newCount);
                    updates.put("isExpired", willExpire);
                    if (willExpire) {
                        updates.put("mediaUrl", "");
                    }

                    db.collection("chats").document(conversationId)
                            .collection("messages").document(msgId)
                            .update(updates)
                            .addOnSuccessListener(aVoid -> {
                                if (willExpire) {
                                    deleteSnapMediaAndDoc(conversationId, msgId, snapId, doc.getString("mediaUrl"), null);
                                }
                                callback.onSuccess(newCount, willExpire);
                            })
                            .addOnFailureListener(callback::onError);
                })
                .addOnFailureListener(callback::onError);
    }

    public void setSnapSavedStatus(String conversationId, String msgId, boolean isSaved, SnapActionCallback callback) {
        if (conversationId == null || msgId == null) {
            if (callback != null) callback.onError(new IllegalArgumentException("Invalid parameters"));
            return;
        }

        Map<String, Object> updates = new HashMap<>();
        updates.put("isSaved", isSaved);
        if (isSaved) {
            updates.put("isExpired", false);
        }

        db.collection("chats").document(conversationId)
                .collection("messages").document(msgId)
                .update(updates)
                .addOnSuccessListener(aVoid -> {
                    if (callback != null) callback.onSuccess();
                })
                .addOnFailureListener(e -> {
                    if (callback != null) callback.onError(e);
                });
    }

    public void deleteSnapMediaAndDoc(String conversationId, String msgId, String snapId, String mediaUrl, SnapActionCallback callback) {
        if (conversationId != null && msgId != null) {
            db.collection("chats").document(conversationId)
                    .collection("messages").document(msgId)
                    .delete();
        }

        if (snapId != null && !snapId.trim().isEmpty()) {
            String cleanSnapId = snapId.startsWith("snap_") ? snapId.substring(5) : snapId;
            db.collection("snaps").document(cleanSnapId).delete();
            db.collection("snaps").document(snapId).delete();
        }

        if (mediaUrl != null && mediaUrl.contains("firebasestorage")) {
            try {
                FirebaseStorage.getInstance().getReferenceFromUrl(mediaUrl).delete();
            } catch (Exception e) {
                Log.w(TAG, "Storage delete error: " + e.getMessage());
            }
        }

        if (callback != null) {
            callback.onSuccess();
        }
    }
}
