package com.amstudio.fitup;

import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

public class TRTCCallDialog extends DialogFragment {

    public static TRTCCallDialog newInstance(String callId, String targetUid, String userName,
                                            String userInitials, boolean isVideo, boolean isIncoming) {
        return newInstance(callId, targetUid, userName, userInitials, null, isVideo, isIncoming);
    }

    public static TRTCCallDialog newInstance(String callId, String targetUid, String userName,
                                            String userInitials, String avatarUrl, boolean isVideo, boolean isIncoming) {
        TRTCCallDialog fragment = new TRTCCallDialog();
        Bundle args = new Bundle();
        args.putString("arg_call_id", callId);
        args.putString("arg_target_uid", targetUid);
        args.putString("arg_user_name", userName);
        args.putString("arg_user_initials", userInitials);
        args.putString("arg_avatar_url", avatarUrl);
        args.putBoolean("arg_is_video", isVideo);
        args.putBoolean("arg_is_incoming", isIncoming);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Bundle args = getArguments();
        if (args != null && getContext() != null) {
            TRTCCallActivity.start(
                    getContext(),
                    args.getString("arg_call_id"),
                    args.getString("arg_target_uid"),
                    args.getString("arg_user_name"),
                    args.getString("arg_user_initials"),
                    args.getString("arg_avatar_url"),
                    args.getBoolean("arg_is_video", false),
                    args.getBoolean("arg_is_incoming", false)
            );
        }
        dismissAllowingStateLoss();
    }
}
