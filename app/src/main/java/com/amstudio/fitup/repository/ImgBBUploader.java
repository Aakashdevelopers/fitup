package com.amstudio.fitup.repository;

import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import android.util.Log;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.concurrent.Executors;

public class ImgBBUploader {

    private static final String TAG = "ImgBBUploader";
    private static final String IMGBB_API_KEY = "98b5b00cee79645e2ea44a187446c2e3";

    public interface UploadCallback {
        void onSuccess(String imageUrl);
        void onError(Exception e);
    }

    public static void uploadImage(Context context, Uri imageUri, UploadCallback callback) {
        if (imageUri == null || context == null) {
            if (callback != null) callback.onError(new IllegalArgumentException("Invalid Uri or Context"));
            return;
        }

        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                InputStream inputStream = context.getContentResolver().openInputStream(imageUri);
                if (inputStream == null) {
                    notifyError(callback, new Exception("Cannot open image stream"));
                    return;
                }

                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    baos.write(buffer, 0, bytesRead);
                }
                inputStream.close();

                byte[] imageBytes = baos.toByteArray();
                String base64Image = Base64.encodeToString(imageBytes, Base64.NO_WRAP);

                uploadBase64(base64Image, callback);

            } catch (Exception e) {
                Log.e(TAG, "Error reading image stream for ImgBB upload", e);
                notifyError(callback, e);
            }
        });
    }

    public static void uploadBitmap(Bitmap bitmap, UploadCallback callback) {
        if (bitmap == null) {
            if (callback != null) callback.onError(new IllegalArgumentException("Bitmap is null"));
            return;
        }

        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                bitmap.compress(Bitmap.CompressFormat.JPEG, 85, baos);
                byte[] imageBytes = baos.toByteArray();
                String base64Image = Base64.encodeToString(imageBytes, Base64.NO_WRAP);

                uploadBase64(base64Image, callback);

            } catch (Exception e) {
                Log.e(TAG, "Error compressing bitmap for ImgBB upload", e);
                notifyError(callback, e);
            }
        });
    }

    private static void uploadBase64(String base64Image, UploadCallback callback) {
        try {
            String urlStr = "https://api.imgbb.com/1/upload?key=" + IMGBB_API_KEY;
            URL url = new URL(urlStr);

            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setConnectTimeout(20000);
            conn.setReadTimeout(20000);

            String postData = "image=" + URLEncoder.encode(base64Image, "UTF-8");
            try (OutputStream os = conn.getOutputStream()) {
                byte[] input = postData.getBytes("utf-8");
                os.write(input, 0, input.length);
            }

            int responseCode = conn.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder response = new StringBuilder();
                String inputLine;
                while ((inputLine = in.readLine()) != null) {
                    response.append(inputLine);
                }
                in.close();

                JSONObject json = new JSONObject(response.toString());
                if (json.optBoolean("success")) {
                    JSONObject data = json.getJSONObject("data");
                    String imageUrl = data.optString("url", data.optString("display_url"));
                    notifySuccess(callback, imageUrl);
                    return;
                }
            }

            notifyError(callback, new Exception("ImgBB upload returned status code: " + responseCode));

        } catch (Exception e) {
            Log.e(TAG, "ImgBB HTTP Upload Exception", e);
            notifyError(callback, e);
        }
    }

    private static void notifySuccess(UploadCallback callback, String imageUrl) {
        if (callback != null) {
            new Handler(Looper.getMainLooper()).post(() -> callback.onSuccess(imageUrl));
        }
    }

    private static void notifyError(UploadCallback callback, Exception e) {
        if (callback != null) {
            new Handler(Looper.getMainLooper()).post(() -> callback.onError(e));
        }
    }
}
