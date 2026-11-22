package com.amstudio.fitbond.repository;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SnapMediaCache {
    private static final String TAG = "SnapMediaCache";
    private static SnapMediaCache instance;

    private final ExecutorService executor = Executors.newFixedThreadPool(4);
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public interface VideoCacheCallback {
        void onSuccess(File videoFile);
        void onError(Exception e);
    }

    public static synchronized SnapMediaCache getInstance() {
        if (instance == null) {
            instance = new SnapMediaCache();
        }
        return instance;
    }

    private SnapMediaCache() {}

    public File getCachedFile(Context context, String url) {
        if (url == null || url.trim().isEmpty() || context == null) {
            return null;
        }

        try {
            if (url.startsWith("file://")) {
                Uri uri = Uri.parse(url);
                if (uri.getPath() != null) {
                    File file = new File(uri.getPath());
                    if (file.exists() && file.isFile()) {
                        return file;
                    }
                }
            } else if (url.startsWith("/")) {
                File file = new File(url);
                if (file.exists() && file.isFile()) {
                    return file;
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Error checking local file path: " + e.getMessage());
        }

        File cachedFile = new File(getCacheDir(context), getFileNameForUrl(url));
        if (cachedFile.exists() && cachedFile.isFile() && cachedFile.length() > 0) {
            return cachedFile;
        }

        return null;
    }

    public void preloadMedia(Context context, String mediaUrl, boolean isVideo) {
        if (mediaUrl == null || mediaUrl.trim().isEmpty() || context == null) {
            return;
        }

        if (getCachedFile(context, mediaUrl) != null) {
            return;
        }

        Context appContext = context.getApplicationContext();
        executor.execute(() -> {
            try {
                downloadToFile(appContext, mediaUrl);
            } catch (Exception e) {
                Log.w(TAG, "Failed to preload media (isVideo=" + isVideo + ") from url: " + mediaUrl + " (" + e.getMessage() + ")");
            }
        });
    }

    public void deleteCachedFile(Context context, String mediaUrl) {
        if (mediaUrl == null || mediaUrl.trim().isEmpty() || context == null) {
            return;
        }

        try {
            File cachedFile = getCachedFile(context, mediaUrl);
            if (cachedFile != null && cachedFile.exists()) {
                boolean deleted = cachedFile.delete();
                if (!deleted) {
                    Log.w(TAG, "Could not delete cached file: " + cachedFile.getAbsolutePath());
                }
            }

            File diskCacheFile = new File(getCacheDir(context), getFileNameForUrl(mediaUrl));
            if (diskCacheFile.exists()) {
                boolean deleted = diskCacheFile.delete();
                if (!deleted) {
                    Log.w(TAG, "Could not delete disk cache file: " + diskCacheFile.getAbsolutePath());
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Error deleting cached file: " + e.getMessage());
        }
    }

    public void getOrFetchVideoFile(Context context, String url, VideoCacheCallback callback) {
        if (url == null || url.trim().isEmpty() || context == null) {
            if (callback != null) {
                mainHandler.post(() -> callback.onError(new IllegalArgumentException("Invalid URL or Context")));
            }
            return;
        }

        File cached = getCachedFile(context, url);
        if (cached != null) {
            if (callback != null) {
                mainHandler.post(() -> callback.onSuccess(cached));
            }
            return;
        }

        Context appContext = context.getApplicationContext();
        executor.execute(() -> {
            try {
                File downloadedFile = downloadToFile(appContext, url);
                if (callback != null) {
                    mainHandler.post(() -> callback.onSuccess(downloadedFile));
                }
            } catch (Exception e) {
                Log.w(TAG, "Failed to fetch video file from url: " + url, e);
                if (callback != null) {
                    mainHandler.post(() -> callback.onError(e));
                }
            }
        });
    }

    private synchronized File downloadToFile(Context context, String urlStr) throws Exception {
        File targetFile = new File(getCacheDir(context), getFileNameForUrl(urlStr));
        if (targetFile.exists() && targetFile.length() > 0) {
            return targetFile;
        }

        File tempFile = new File(getCacheDir(context), getFileNameForUrl(urlStr) + ".tmp");

        try {
            try (InputStream in = openStream(context, urlStr)) {
                if (in == null) {
                    throw new Exception("Unable to open input stream for URL: " + urlStr);
                }

                try (FileOutputStream out = new FileOutputStream(tempFile)) {
                    byte[] buffer = new byte[8192];
                    int bytesRead;
                    while ((bytesRead = in.read(buffer)) != -1) {
                        out.write(buffer, 0, bytesRead);
                    }
                    out.flush();
                }
            }

            if (tempFile.exists() && tempFile.length() > 0) {
                if (tempFile.renameTo(targetFile)) {
                    return targetFile;
                } else if (targetFile.exists()) {
                    boolean deleted = tempFile.delete();
                    if (!deleted) {
                        Log.w(TAG, "Could not delete temp file after target creation");
                    }
                    return targetFile;
                } else {
                    return tempFile;
                }
            }
            throw new Exception("Downloaded file is empty or missing");
        } finally {
            if (tempFile.exists() && !targetFile.exists()) {
                boolean deleted = tempFile.delete();
                if (!deleted) {
                    Log.w(TAG, "Could not delete temporary file: " + tempFile.getAbsolutePath());
                }
            }
        }
    }

    private InputStream openStream(Context context, String urlStr) throws Exception {
        if (urlStr.startsWith("content://")) {
            return context.getContentResolver().openInputStream(Uri.parse(urlStr));
        } else {
            URL url = new URL(urlStr);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(15000);
            conn.setInstanceFollowRedirects(true);
            int responseCode = conn.getResponseCode();
            if (responseCode >= 400) {
                throw new Exception("HTTP error code: " + responseCode);
            }
            return conn.getInputStream();
        }
    }

    private File getCacheDir(Context context) {
        File dir = new File(context.getCacheDir(), "snap_media_cache");
        if (!dir.exists()) {
            boolean created = dir.mkdirs();
            if (!created && !dir.exists()) {
                Log.w(TAG, "Failed to create cache directory: " + dir.getAbsolutePath());
            }
        }
        return dir;
    }

    private String getFileNameForUrl(String url) {
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            digest.update(url.getBytes());
            byte[] messageDigest = digest.digest();
            StringBuilder hexString = new StringBuilder();
            for (byte b : messageDigest) {
                String h = Integer.toHexString(0xFF & b);
                if (h.length() < 2) {
                    hexString.append('0');
                }
                hexString.append(h);
            }
            return hexString.toString();
        } catch (Exception e) {
            return String.valueOf(url.hashCode());
        }
    }
}
