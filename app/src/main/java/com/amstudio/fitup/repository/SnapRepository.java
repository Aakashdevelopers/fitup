package com.amstudio.fitup.repository;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import com.amstudio.fitup.model.SnapMetadata;
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
        sendPrivateSnap(null, localMediaUri, mediaType, recipientUids, durationSeconds, callback);
    }

    public void sendPrivateSnap(Context context, Uri localMediaUri, String mediaType, List<String> recipientUids,
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

            // Save Snap metadata in Firestore snaps/{snapId}
            db.collection("snaps").document(snapId)
                    .set(metadata)
                    .addOnSuccessListener(aVoid -> {
                        Log.d(TAG, "Snap metadata stored in Firestore: " + snapId);

                        TencentChatManager chatManager = TencentChatManager.getInstance();
                        for (String recipientUid : recipientUids) {
                            saveSnapChatMessageToFirestore(senderUid, recipientUid, snapId, mediaType, fullMediaUrl, durationSeconds);

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
                        db.collection("snaps").document(snapId).set(meta);
                        for (String recipientUid : recipientUids) {
                            saveSnapChatMessageToFirestore(senderUid, recipientUid, snapId, mediaType, imgBbUrl, durationSeconds);
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
                                                String mediaType, String mediaUrl, long duration) {
        if (senderUid == null || recipientUid == null) return;

        String conversationId = senderUid.compareTo(recipientUid) < 0 ? senderUid + "_" + recipientUid : recipientUid + "_" + senderUid;
        String msgId = "snap_" + snapId;

        Map<String, Object> snapMsgDoc = new HashMap<>();
        snapMsgDoc.put("id", snapId);
        snapMsgDoc.put("senderUid", senderUid);
        snapMsgDoc.put("senderName", "You");
        snapMsgDoc.put("text", "VIDEO".equalsIgnoreCase(mediaType) ? "Video Snap" : "Snap");
        snapMsgDoc.put("type", "SNAP");
        snapMsgDoc.put("mediaType", mediaType.toUpperCase());
        snapMsgDoc.put("mediaUrl", mediaUrl);
        snapMsgDoc.put("duration", duration);
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

        db.collection("snaps").document(snapId).get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        SnapMetadata metadata = doc.toObject(SnapMetadata.class);
                        callback.onSuccess(metadata);
                    } else {
                        callback.onError(new Exception("Snap not found or expired"));
                    }
                })
                .addOnFailureListener(callback::onError);
    }
}
