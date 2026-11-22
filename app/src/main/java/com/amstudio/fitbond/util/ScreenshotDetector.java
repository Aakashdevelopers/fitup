package com.amstudio.fitbond.util;

import android.content.ContentResolver;
import android.content.Context;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.util.Log;

public class ScreenshotDetector {

    private static final String TAG = "ScreenshotDetector";

    public interface OnScreenshotListener {
        void onScreenshotDetected(String path);
    }

    private final Context context;
    private final OnScreenshotListener listener;
    private ContentObserver contentObserver;
    private boolean isListening = false;
    private String lastDetectedPath = null;
    private long lastDetectedTime = 0;

    public ScreenshotDetector(Context context, OnScreenshotListener listener) {
        this.context = context.getApplicationContext();
        this.listener = listener;
    }

    public void startListening() {
        if (isListening) return;

        try {
            contentObserver = new ContentObserver(new Handler(Looper.getMainLooper())) {
                @Override
                public void onChange(boolean selfChange, Uri uri) {
                    super.onChange(selfChange, uri);
                    checkMediaStoreForScreenshot();
                }
            };

            ContentResolver contentResolver = context.getContentResolver();
            contentResolver.registerContentObserver(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    true,
                    contentObserver
            );
            isListening = true;
        } catch (Exception e) {
            Log.e(TAG, "Error starting ScreenshotDetector: " + e.getMessage(), e);
        }
    }

    public void stopListening() {
        if (!isListening) return;

        try {
            if (contentObserver != null) {
                context.getContentResolver().unregisterContentObserver(contentObserver);
                contentObserver = null;
            }
            isListening = false;
        } catch (Exception e) {
            Log.e(TAG, "Error stopping ScreenshotDetector: " + e.getMessage(), e);
        }
    }

    private void checkMediaStoreForScreenshot() {
        try {
            String[] projection = new String[]{
                    MediaStore.Images.Media._ID,
                    MediaStore.Images.Media.DATA,
                    MediaStore.Images.Media.DATE_ADDED
            };

            String sortOrder = MediaStore.Images.Media.DATE_ADDED + " DESC";

            try (Cursor cursor = context.getContentResolver().query(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    projection,
                    null,
                    null,
                    sortOrder
            )) {
                if (cursor != null && cursor.moveToFirst()) {
                    int dataColumnIndex = cursor.getColumnIndex(MediaStore.Images.Media.DATA);
                    int dateAddedColumnIndex = cursor.getColumnIndex(MediaStore.Images.Media.DATE_ADDED);

                    if (dataColumnIndex != -1) {
                        String path = cursor.getString(dataColumnIndex);
                        long dateAdded = dateAddedColumnIndex != -1 ? cursor.getLong(dateAddedColumnIndex) : 0;
                        long currentTime = System.currentTimeMillis() / 1000;

                        if (Math.abs(currentTime - dateAdded) <= 10) {
                            if (isScreenshotPath(path)) {
                                long now = System.currentTimeMillis();
                                if (!path.equals(lastDetectedPath) || (now - lastDetectedTime > 3000)) {
                                    lastDetectedPath = path;
                                    lastDetectedTime = now;
                                    if (listener != null) {
                                        listener.onScreenshotDetected(path);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to query MediaStore for screenshot: " + e.getMessage(), e);
        }
    }

    private boolean isScreenshotPath(String path) {
        if (path == null) return false;
        String lowerPath = path.toLowerCase();
        return lowerPath.contains("screenshot") || lowerPath.contains("screen_shot") || lowerPath.contains("screen-shot") || lowerPath.contains("screencap");
    }
}
