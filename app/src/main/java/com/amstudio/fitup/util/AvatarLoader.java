package com.amstudio.fitup.util;

import android.app.Activity;
import android.content.Context;
import android.text.TextUtils;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;

import androidx.fragment.app.Fragment;

import com.amstudio.fitup.R;
import com.bumptech.glide.Glide;

public class AvatarLoader {

    private static final String TAG = "AvatarLoader";

    public static void loadAvatar(Object contextOrView, String avatarUrl, ImageView imageView) {
        if (imageView == null) return;

        if (!TextUtils.isEmpty(avatarUrl)) {
            if (avatarUrl.startsWith("data:image")) {
                try {
                    String base64Data = avatarUrl.substring(avatarUrl.indexOf(",") + 1);
                    byte[] decodedBytes = Base64.decode(base64Data, Base64.DEFAULT);
                    loadBytes(contextOrView, decodedBytes, imageView);
                    return;
                } catch (Exception e) {
                    Log.e(TAG, "Base64 avatar decode error", e);
                }
            }

            loadUrl(contextOrView, avatarUrl, imageView);
        } else {
            imageView.setImageResource(R.drawable.ic_avatar_placeholder);
        }
    }

    private static void loadBytes(Object target, byte[] bytes, ImageView imageView) {
        try {
            if (target instanceof Fragment) {
                Fragment fragment = (Fragment) target;
                if (fragment.isAdded()) {
                    Glide.with(fragment)
                            .load(bytes)
                            .placeholder(R.drawable.ic_avatar_placeholder)
                            .error(R.drawable.ic_avatar_placeholder)
                            .into(imageView);
                }
            } else if (target instanceof Activity) {
                Activity activity = (Activity) target;
                if (!activity.isFinishing()) {
                    Glide.with(activity)
                            .load(bytes)
                            .placeholder(R.drawable.ic_avatar_placeholder)
                            .error(R.drawable.ic_avatar_placeholder)
                            .into(imageView);
                }
            } else if (target instanceof View) {
                View view = (View) target;
                Glide.with(view)
                        .load(bytes)
                        .placeholder(R.drawable.ic_avatar_placeholder)
                        .error(R.drawable.ic_avatar_placeholder)
                        .into(imageView);
            } else if (target instanceof Context) {
                Context context = (Context) target;
                Glide.with(context)
                        .load(bytes)
                        .placeholder(R.drawable.ic_avatar_placeholder)
                        .error(R.drawable.ic_avatar_placeholder)
                        .into(imageView);
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed loading avatar bytes", e);
            imageView.setImageResource(R.drawable.ic_avatar_placeholder);
        }
    }

    private static void loadUrl(Object target, String url, ImageView imageView) {
        try {
            if (target instanceof Fragment) {
                Fragment fragment = (Fragment) target;
                if (fragment.isAdded()) {
                    Glide.with(fragment)
                            .load(url)
                            .placeholder(R.drawable.ic_avatar_placeholder)
                            .error(R.drawable.ic_avatar_placeholder)
                            .into(imageView);
                }
            } else if (target instanceof Activity) {
                Activity activity = (Activity) target;
                if (!activity.isFinishing()) {
                    Glide.with(activity)
                            .load(url)
                            .placeholder(R.drawable.ic_avatar_placeholder)
                            .error(R.drawable.ic_avatar_placeholder)
                            .into(imageView);
                }
            } else if (target instanceof View) {
                View view = (View) target;
                Glide.with(view)
                        .load(url)
                        .placeholder(R.drawable.ic_avatar_placeholder)
                        .error(R.drawable.ic_avatar_placeholder)
                        .into(imageView);
            } else if (target instanceof Context) {
                Context context = (Context) target;
                Glide.with(context)
                        .load(url)
                        .placeholder(R.drawable.ic_avatar_placeholder)
                        .error(R.drawable.ic_avatar_placeholder)
                        .into(imageView);
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed loading avatar url", e);
            imageView.setImageResource(R.drawable.ic_avatar_placeholder);
        }
    }
}
