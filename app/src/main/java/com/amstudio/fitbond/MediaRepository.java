package com.amstudio.fitbond;

import android.net.Uri;

/**
 * Interface representing media storage operations.
 * Allows seamless switching between local MediaStore storage and future cloud backends.
 */
public interface MediaRepository {

    interface MediaSaveCallback {
        void onSuccess(Uri savedMediaUri);
        void onError(Exception exception);
    }

    /**
     * Notify repository of newly captured photo.
     */
    void onPhotoCaptured(Uri photoUri, MediaSaveCallback callback);

    /**
     * Notify repository of newly recorded video.
     */
    void onVideoRecorded(Uri videoUri, MediaSaveCallback callback);
}
