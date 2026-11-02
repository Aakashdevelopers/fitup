package com.amstudio.fitup;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

public class CreateContentBottomSheetDialog extends BottomSheetDialogFragment {

    private View rowPost;
    private View rowStory;

    public interface OnCreateOptionSelectedListener {
        void onSelectPost();
        void onSelectStory();
    }

    private OnCreateOptionSelectedListener listener;

    public static CreateContentBottomSheetDialog newInstance() {
        return new CreateContentBottomSheetDialog();
    }

    public void setOnCreateOptionSelectedListener(OnCreateOptionSelectedListener listener) {
        this.listener = listener;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setStyle(DialogFragment.STYLE_NORMAL, 0);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.dialog_create_content_bottom_sheet, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        rowPost = view.findViewById(R.id.row_create_post);
        rowStory = view.findViewById(R.id.row_create_story);

        if (rowPost != null) {
            rowPost.setOnClickListener(v -> {
                dismiss();
                if (listener != null) {
                    listener.onSelectPost();
                }
            });
        }

        if (rowStory != null) {
            rowStory.setOnClickListener(v -> {
                dismiss();
                if (listener != null) {
                    listener.onSelectStory();
                }
            });
        }
    }
}
