package com.amstudio.fitbond;

import android.content.Context;
import android.net.Uri;

/**
 * Local MediaStore implementation of MediaRepository.
 * Handles local media tracking and provides a clean abstraction for future cloud upload integration.
 */
public class LocalMediaRepository implements MediaRepository {

    private final Context context;

    public LocalMediaRepository(Context context) {
        this.context = context.getApplicationContext();
    }

    @Override
    public void onPhotoCaptured(Uri photoUri, MediaSaveCallback callback) {
        if (photoUri != null) {
            callback.onSuccess(photoUri);
        } else {
            callback.onError(new IllegalArgumentException("Invalid photo URI"));
        }
    }

    @Override
    public void onVideoRecorded(Uri videoUri, MediaSaveCallback callback) {
        if (videoUri != null) {
            callback.onSuccess(videoUri);
        } else {
            callback.onError(new IllegalArgumentException("Invalid video URI"));
        }
    }
}
