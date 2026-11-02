package com.amstudio.fitbond;

import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import java.util.ArrayList;
import java.util.List;

public class ImageFilterBottomSheetDialog extends BottomSheetDialogFragment {

    public interface OnFilterSelectedListener {
        void onFilterSelected(int filterIndex, String filterName, float[] matrix);
        void onDone(int selectedIndex);
        void onCancel();
    }

    public static class FilterPreset {
        public final String name;
        public final float[] matrix;

        public FilterPreset(String name, float[] matrix) {
            this.name = name;
            this.matrix = matrix;
        }
    }

    private RecyclerView rvPresets;
    private TextView btnCancel;
    private TextView btnDone;

    private FilterAdapter adapter;
    private final List<FilterPreset> presetsList = new ArrayList<>();
    private int selectedIndex = 0;
    private Uri previewUri;

    private OnFilterSelectedListener listener;

    public static ImageFilterBottomSheetDialog newInstance(int initialIndex, @Nullable Uri mediaUri) {
        ImageFilterBottomSheetDialog dialog = new ImageFilterBottomSheetDialog();
        Bundle args = new Bundle();
        args.putInt("arg_index", initialIndex);
        if (mediaUri != null) {
            args.putString("arg_uri", mediaUri.toString());
        }
        dialog.setArguments(args);
        return dialog;
    }

    public void setOnFilterSelectedListener(OnFilterSelectedListener listener) {
        this.listener = listener;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setStyle(DialogFragment.STYLE_NORMAL, 0);

        if (getArguments() != null) {
            selectedIndex = getArguments().getInt("arg_index", 0);
            String uriStr = getArguments().getString("arg_uri");
            if (uriStr != null) {
                previewUri = Uri.parse(uriStr);
            }
        }

        initPresets();
    }

    private void initPresets() {
        presetsList.clear();

        // 0: Normal
        presetsList.add(new FilterPreset("Normal", null));

        // 1: Paris (Warm Soft Glow)
        presetsList.add(new FilterPreset("Paris", new float[]{
                1.1f, 0.1f, 0.0f, 0, 10,
                0.0f, 1.05f, 0.0f, 0, 8,
                0.0f, 0.0f, 0.95f, 0, 5,
                0, 0, 0, 1, 0
        }));

        // 2: Los Angeles (Vivid Contrast)
        presetsList.add(new FilterPreset("Los Angeles", new float[]{
                1.2f, 0.0f, 0.0f, 0, 15,
                0.0f, 1.15f, 0.0f, 0, 10,
                0.0f, 0.0f, 1.1f, 0, 5,
                0, 0, 0, 1, 0
        }));

        // 3: Clarendon (Cool Shadows & Contrast)
        presetsList.add(new FilterPreset("Clarendon", new float[]{
                1.2f, 0.0f, 0.0f, 0, 10,
                0.0f, 1.1f, 0.0f, 0, 5,
                0.0f, 0.0f, 1.3f, 0, 15,
                0, 0, 0, 1, 0
        }));

        // 4: Mono (Black & White Grayscale)
        ColorMatrix bwMatrix = new ColorMatrix();
        bwMatrix.setSaturation(0);
        presetsList.add(new FilterPreset("Mono", bwMatrix.getArray()));

        // 5: Vintage (Warm Sepia Tones)
        presetsList.add(new FilterPreset("Vintage", new float[]{
                0.393f, 0.769f, 0.189f, 0, 0,
                0.349f, 0.686f, 0.168f, 0, 0,
                0.272f, 0.534f, 0.131f, 0, 0,
                0, 0, 0, 1, 0
        }));
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.dialog_image_filter_bottom_sheet, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        rvPresets = view.findViewById(R.id.rv_filter_presets);
        btnCancel = view.findViewById(R.id.btn_cancel_filter);
        btnDone = view.findViewById(R.id.btn_done_filter);

        if (rvPresets != null) {
            adapter = new FilterAdapter(presetsList, previewUri, selectedIndex, (index, preset) -> {
                selectedIndex = index;
                if (listener != null) {
                    listener.onFilterSelected(index, preset.name, preset.matrix);
                }
            });

            rvPresets.setLayoutManager(new LinearLayoutManager(getContext(), LinearLayoutManager.HORIZONTAL, false));
            rvPresets.setAdapter(adapter);
        }

        if (btnCancel != null) {
            btnCancel.setOnClickListener(v -> {
                dismiss();
                if (listener != null) listener.onCancel();
            });
        }

        if (btnDone != null) {
            btnDone.setOnClickListener(v -> {
                dismiss();
                if (listener != null) listener.onDone(selectedIndex);
            });
        }
    }

    // RecyclerView Adapter
    private static class FilterAdapter extends RecyclerView.Adapter<FilterAdapter.FilterViewHolder> {

        interface OnPresetClickListener {
            void onPresetClick(int index, FilterPreset preset);
        }

        private final List<FilterPreset> list;
        private final Uri previewUri;
        private int selectedIndex;
        private final OnPresetClickListener listener;

        FilterAdapter(List<FilterPreset> list, Uri previewUri, int initialIndex, OnPresetClickListener listener) {
            this.list = list;
            this.previewUri = previewUri;
            this.selectedIndex = initialIndex;
            this.listener = listener;
        }

        @NonNull
        @Override
        public FilterViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_filter_preset, parent, false);
            return new FilterViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull FilterViewHolder holder, int position) {
            FilterPreset preset = list.get(position);
            holder.bind(preset, previewUri, position == selectedIndex, v -> {
                int prev = selectedIndex;
                selectedIndex = holder.getAdapterPosition();
                notifyItemChanged(prev);
                notifyItemChanged(selectedIndex);
                if (listener != null) {
                    listener.onPresetClick(selectedIndex, preset);
                }
            });
        }

        @Override
        public int getItemCount() {
            return list.size();
        }

        static class FilterViewHolder extends RecyclerView.ViewHolder {
            TextView tvName;
            ImageView ivThumbnail;
            View viewRing;

            FilterViewHolder(@NonNull View itemView) {
                super(itemView);
                tvName = itemView.findViewById(R.id.tv_filter_name);
                ivThumbnail = itemView.findViewById(R.id.iv_filter_thumbnail);
                viewRing = itemView.findViewById(R.id.view_filter_selected_ring);
            }

            void bind(FilterPreset preset, Uri uri, boolean isSelected, View.OnClickListener clickListener) {
                if (tvName != null) {
                    tvName.setText(preset.name);
                }

                if (ivThumbnail != null) {
                    if (uri != null) {
                        Glide.with(itemView.getContext())
                                .load(uri)
                                .placeholder(R.drawable.ic_avatar_placeholder)
                                .error(R.drawable.ic_avatar_placeholder)
                                .into(ivThumbnail);
                    } else {
                        ivThumbnail.setImageResource(R.drawable.bg_sample_gym_preview);
                    }

                    if (preset.matrix != null) {
                        ivThumbnail.setColorFilter(new ColorMatrixColorFilter(preset.matrix));
                    } else {
                        ivThumbnail.clearColorFilter();
                    }
                }

                if (viewRing != null) {
                    viewRing.setVisibility(isSelected ? View.VISIBLE : View.GONE);
                }

                itemView.setOnClickListener(clickListener);
            }
        }
    }
}
