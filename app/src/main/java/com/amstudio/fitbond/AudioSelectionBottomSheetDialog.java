package com.amstudio.fitbond;

import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.DialogFragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executors;

public class AudioSelectionBottomSheetDialog extends BottomSheetDialogFragment {

    private static final String TAG = "AudioSelectionBottomSheetDialog";

    public interface OnAudioSelectedListener {
        void onAudioSelected(String trackName, String artistName, Uri audioUri);
    }

    public static class AudioItem {
        public final String title;
        public final String artist;
        public final String duration;
        public final Uri uri;

        public AudioItem(String title, String artist, String duration, Uri uri) {
            this.title = title;
            this.artist = artist;
            this.duration = duration;
            this.uri = uri;
        }
    }

    private EditText etSearch;
    private TextView btnCategoryForYou;
    private TextView btnCategoryLocal;
    private RecyclerView rvTracks;

    private AudioAdapter adapter;
    private final List<AudioItem> forYouTracks = new ArrayList<>();
    private final List<AudioItem> localTracks = new ArrayList<>();
    private final List<AudioItem> displayList = new ArrayList<>();

    private boolean isForYouActive = true;
    private OnAudioSelectedListener listener;

    public static AudioSelectionBottomSheetDialog newInstance() {
        return new AudioSelectionBottomSheetDialog();
    }

    public void setOnAudioSelectedListener(OnAudioSelectedListener listener) {
        this.listener = listener;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setStyle(DialogFragment.STYLE_NORMAL, 0);
        initForYouTracks();
    }

    private void initForYouTracks() {
        forYouTracks.clear();
        forYouTracks.add(new AudioItem("Poser", "Deville", "2:42", null));
        forYouTracks.add(new AudioItem("The Business", "Trap Beats Pandits", "0:54", null));
        forYouTracks.add(new AudioItem("Flow", "Money J", "2:32", null));
        forYouTracks.add(new AudioItem("Off Limits", "Parmish Verma, Starboy X", "2:02", null));
        forYouTracks.add(new AudioItem("Legacy", "Violinpop", "1:39", null));
        forYouTracks.add(new AudioItem("Hyper Feelings", "Basso", "3:15", null));
        forYouTracks.add(new AudioItem("Gym Motivation", "Heavy Beats", "2:10", null));
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.dialog_audio_selection_bottom_sheet, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        initViews(view);
        setupRecyclerView();
        setupSearch();
        setupCategories();
        updateCategoryState();
    }

    private void initViews(View view) {
        etSearch = view.findViewById(R.id.et_search_audio);
        btnCategoryForYou = view.findViewById(R.id.btn_category_for_you);
        btnCategoryLocal = view.findViewById(R.id.btn_category_local);
        rvTracks = view.findViewById(R.id.rv_audio_tracks);
    }

    private void setupRecyclerView() {
        if (getContext() == null || rvTracks == null) return;

        adapter = new AudioAdapter(displayList, item -> {
            dismiss();
            if (listener != null) {
                listener.onAudioSelected(item.title, item.artist, item.uri);
            }
        });

        rvTracks.setLayoutManager(new LinearLayoutManager(getContext()));
        rvTracks.setAdapter(adapter);
    }

    private void setupSearch() {
        if (etSearch == null) return;

        etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                filterTracks(s != null ? s.toString() : "");
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
    }

    private void setupCategories() {
        if (btnCategoryForYou != null) {
            btnCategoryForYou.setOnClickListener(v -> {
                isForYouActive = true;
                updateCategoryState();
            });
        }

        if (btnCategoryLocal != null) {
            btnCategoryLocal.setOnClickListener(v -> {
                isForYouActive = false;
                updateCategoryState();
                if (localTracks.isEmpty()) {
                    loadLocalDeviceAudio();
                }
            });
        }
    }

    private void updateCategoryState() {
        if (getContext() == null) return;

        int activeColor = ContextCompat.getColor(getContext(), R.color.white);
        int inactiveColor = ContextCompat.getColor(getContext(), R.color.secondary_text);

        if (btnCategoryForYou != null) {
            btnCategoryForYou.setTextColor(isForYouActive ? activeColor : inactiveColor);
            btnCategoryForYou.setAlpha(isForYouActive ? 1.0f : 0.6f);
        }

        if (btnCategoryLocal != null) {
            btnCategoryLocal.setTextColor(!isForYouActive ? activeColor : inactiveColor);
            btnCategoryLocal.setAlpha(!isForYouActive ? 1.0f : 0.6f);
        }

        filterTracks(etSearch != null ? etSearch.getText().toString() : "");
    }

    private void filterTracks(String query) {
        displayList.clear();
        String q = query != null ? query.toLowerCase(Locale.ROOT).trim() : "";

        List<AudioItem> source = isForYouActive ? forYouTracks : localTracks;

        if (TextUtils.isEmpty(q)) {
            displayList.addAll(source);
        } else {
            for (AudioItem item : source) {
                String title = item.title != null ? item.title.toLowerCase(Locale.ROOT) : "";
                String artist = item.artist != null ? item.artist.toLowerCase(Locale.ROOT) : "";
                if (title.contains(q) || artist.contains(q)) {
                    displayList.add(item);
                }
            }
        }

        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
    }

    private void loadLocalDeviceAudio() {
        if (getContext() == null) return;

        Executors.newSingleThreadExecutor().execute(() -> {
            List<AudioItem> loadedTracks = new ArrayList<>();
            Context ctx = getContext();
            if (ctx == null) return;

            Uri collection = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ?
                    MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL) :
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;

            String[] projection = new String[]{
                    MediaStore.Audio.Media._ID,
                    MediaStore.Audio.Media.TITLE,
                    MediaStore.Audio.Media.ARTIST,
                    MediaStore.Audio.Media.DURATION
            };

            String selection = MediaStore.Audio.Media.IS_MUSIC + "!= 0";
            String sortOrder = MediaStore.Audio.Media.TITLE + " ASC";

            try (Cursor cursor = ctx.getContentResolver().query(collection, projection, selection, null, sortOrder)) {
                if (cursor != null) {
                    int idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID);
                    int titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE);
                    int artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST);
                    int durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION);

                    while (cursor.moveToNext()) {
                        long id = cursor.getLong(idCol);
                        String title = cursor.getString(titleCol);
                        String artist = cursor.getString(artistCol);
                        long durationMs = cursor.getLong(durationCol);

                        long sec = (durationMs / 1000) % 60;
                        long min = (durationMs / 1000) / 60;
                        String durStr = String.format(Locale.US, "%d:%02d", min, sec);

                        Uri contentUri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id);
                        loadedTracks.add(new AudioItem(title, artist != null ? artist : "Unknown Artist", durStr, contentUri));
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to load MediaStore audio", e);
            }

            if (loadedTracks.isEmpty()) {
                loadedTracks.add(new AudioItem("Device Audio Sample 1", "Local File", "3:10", null));
                loadedTracks.add(new AudioItem("Device Audio Sample 2", "Local File", "2:45", null));
            }

            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> {
                    if (!isAdded()) return;
                    localTracks.clear();
                    localTracks.addAll(loadedTracks);
                    filterTracks(etSearch != null ? etSearch.getText().toString() : "");
                });
            }
        });
    }

    // RecyclerView Adapter
    private static class AudioAdapter extends RecyclerView.Adapter<AudioAdapter.AudioViewHolder> {

        interface OnItemClickListener {
            void onItemClick(AudioItem item);
        }

        private final List<AudioItem> list;
        private final OnItemClickListener listener;

        AudioAdapter(List<AudioItem> list, OnItemClickListener listener) {
            this.list = list;
            this.listener = listener;
        }

        @NonNull
        @Override
        public AudioViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_audio_track, parent, false);
            return new AudioViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull AudioViewHolder holder, int position) {
            AudioItem item = list.get(position);
            holder.bind(item, listener);
        }

        @Override
        public int getItemCount() {
            return list.size();
        }

        static class AudioViewHolder extends RecyclerView.ViewHolder {
            ImageView ivArt;
            TextView tvTitle;
            TextView tvArtist;

            AudioViewHolder(@NonNull View itemView) {
                super(itemView);
                ivArt = itemView.findViewById(R.id.iv_track_art);
                tvTitle = itemView.findViewById(R.id.tv_track_title);
                tvArtist = itemView.findViewById(R.id.tv_track_artist);
            }

            void bind(AudioItem item, OnItemClickListener listener) {
                if (tvTitle != null) {
                    tvTitle.setText(item.title);
                }
                if (tvArtist != null) {
                    tvArtist.setText(item.artist + " • " + item.duration);
                }

                itemView.setOnClickListener(v -> {
                    if (listener != null) listener.onItemClick(item);
                });
            }
        }
    }
}
