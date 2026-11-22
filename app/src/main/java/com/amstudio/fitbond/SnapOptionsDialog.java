package com.amstudio.fitbond;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

public class SnapOptionsDialog extends BottomSheetDialogFragment {

    private static final String ARG_REPLAY_COUNT = "arg_replay_count";
    private static final String ARG_IS_SAVED = "arg_is_saved";
    private static final String ARG_IS_EXPIRED = "arg_is_expired";

    public interface OnSnapOptionSelectedListener {
        void onReplaySelected();
        void onSaveToggleSelected(boolean currentSavedState);
        void onDeleteSelected();
    }

    private boolean isSaved;
    private OnSnapOptionSelectedListener listener;

    public static SnapOptionsDialog newInstance(int replayCount, boolean isSaved, boolean isExpired) {
        SnapOptionsDialog fragment = new SnapOptionsDialog();
        Bundle args = new Bundle();
        args.putInt(ARG_REPLAY_COUNT, replayCount);
        args.putBoolean(ARG_IS_SAVED, isSaved);
        args.putBoolean(ARG_IS_EXPIRED, isExpired);
        fragment.setArguments(args);
        return fragment;
    }

    public void setOnSnapOptionSelectedListener(OnSnapOptionSelectedListener listener) {
        this.listener = listener;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            isSaved = getArguments().getBoolean(ARG_IS_SAVED, false);
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.dialog_snap_options, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        TextView btnReplay = view.findViewById(R.id.btn_option_replay);
        TextView btnSave = view.findViewById(R.id.btn_option_save);
        TextView btnDelete = view.findViewById(R.id.btn_option_delete);

        if (btnSave != null) {
            btnSave.setText(isSaved ? "💾 Unsave from Chat" : "💾 Save in Chat");
            btnSave.setOnClickListener(v -> {
                dismiss();
                if (listener != null) {
                    listener.onSaveToggleSelected(isSaved);
                }
            });
        }

        if (btnReplay != null) {
            btnReplay.setOnClickListener(v -> {
                dismiss();
                if (listener != null) {
                    listener.onReplaySelected();
                }
            });
        }

        if (btnDelete != null) {
            btnDelete.setOnClickListener(v -> {
                dismiss();
                if (listener != null) {
                    listener.onDeleteSelected();
                }
            });
        }
    }
}
