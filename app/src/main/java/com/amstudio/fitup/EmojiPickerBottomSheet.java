package com.amstudio.fitup;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class EmojiPickerBottomSheet extends BottomSheetDialogFragment {

    public interface OnEmojiClickListener {
        void onEmojiSelected(String emoji);
        void onBackspacePressed();
    }

    private OnEmojiClickListener listener;
    private RecyclerView rvEmojiGrid;
    private LinearLayout layoutCategories;
    private ImageButton btnDismiss;
    private ImageButton btnBackspace;
    private EditText etSearch;
    private EmojiAdapter emojiAdapter;
    private int selectedCategoryIndex = 0;

    private static final String[][] EMOJI_CATEGORIES = {
            // Category 0: Recent & Popular
            {
                    "😭", "😉", "😗", "😘", "🥰", "😍", "😊", "😂", "🤣", "🔥", "❤️", "👍", "👏", "🥳", "🥺",
                    "💯", "✨", "🙏", "😎", "😏", "🤔", "🤫", "😜", "😋", "💖", "🎉", "💩", "💀", "🤩", "😁"
            },
            // Category 1: Smileys & Emotions
            {
                    "😀", "😃", "😄", "😁", "😆", "😅", "😂", "🤣", "🥲", "☺️", "😊", "😇", "🙂", "🙃", "😉",
                    "😌", "😍", "🥰", "😘", "😗", "😙", "😚", "😋", "😛", "😝", "😜", "🤪", "🤨", "🧐", "🤓",
                    "😎", "🥸", "🤩", "🥳", "😏", "😒", "😞", "😔", "😟", "😕", "🙁", "☹️", "😣", "😖", "😫",
                    "😩", "🥺", "😢", "😭", "😤", "😠", "😡", "🤬", "🤯", "😳", "🥵", "🥶", "😱", "😨", "😰",
                    "😥", "😓", "🤗", "🤔", "🤭", "🤫", "🤥", "😶", "😐", "😑", "😬", "🙄", "😯", "😦", "😧",
                    "😮", "😲", "🥱", "😴", "🤤", "😪", "😵", "🤐", "🥴", "🤢", "🤮", "🤧", "😷", "🤒", "🤕",
                    "🤑", "🤠", "😈", "👿", "👹", "👺", "🤡", "💩", "👻", "💀", "☠️", "👽", "👾", "🤖", "🎃"
            },
            // Category 2: People & Gestures
            {
                    "👋", "🤚", "🖐️", "✋", "🖖", "👌", "🤌", "🤏", "✌️", "🤞", "🤟", "🤘", "🤙", "👈", "👉",
                    "👆", "🖕", "👇", "☝️", "👍", "👎", "✊", "👊", "🤛", "🤜", "👏", "🙌", "👐", "🤲", "🤝",
                    "✍️", "💅", "<ctrl42>", "💪", "🦾", "🦿", "🦵", "🦶", "👂", "🦻", "👃", "🧠", "🫀", "🫁", "🦷",
                    "🦴", "👀", "👁️", "👅", "👄", "👶", "🧒", "👦", "👧", "🧑", "👨", "👩", "🧔", "👱‍♂️", "👱‍♀️"
            },
            // Category 3: Activities & Celebrations
            {
                    "🎉", "🥳", "🎊", "🎈", "🎁", "🎂", "🍰", "🧁", "🍿", "🍕", "🍔", "🍟", "🌭", "🥪", "🌮",
                    "🌯", "🍝", "🍜", "🍣", "🍱", "🍦", "🍩", "🍪", "🍫", "🍬", "🍭", "☕️", "🧃", "🧋", "🍺"
            },
            // Category 4: Sports & Games
            {
                    "⚽️", "🏀", "🏈", "⚾️", "🥎", "🎾", "🏐", "🏉", "🏓", "🏸", "🏒", "🏏", "🥊", "🥋", "🛹",
                    "🏆", "🥇", "🥈", "🥉", "🏅", "🎖️", "🎯", "🎲", "♟️", "🎳", "🎮", "🎰", "🧩", "🎯", "🚴"
            },
            // Category 5: Travel & Places
            {
                    "🚗", "🚕", "🚙", "🚌", "🏎️", "🚑", "🚒", "🚐", "🛻", "🚚", "🚛", "🚜", "🛵", "🏍️", "🛺",
                    "🚲", "🛴", "🚨", "🚔", "🚘", "🚃", "🚋", "🚞", "🚄", "🚅", "✈️", "🛫", "🛬", "🛸", "🚀"
            },
            // Category 6: Hearts & Symbols
            {
                    "❤️", "🧡", "💛", "💚", "💙", "💜", "🖤", "🤍", "🤎", "💔", "❣️", "💕", "💞", "💓", "💗",
                    "💖", "💘", "💝", "💟", "💯", "💢", "💥", "🔥", "✨", "🌟", "💫", "⭐️", "✅", "❇️", "⚡️"
            }
    };

    private static final String[] CATEGORY_ICONS = {"🕒", "😃", "🙋", "🎉", "⚽", "🚗", "🔣"};

    public void setOnEmojiClickListener(OnEmojiClickListener listener) {
        this.listener = listener;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setStyle(STYLE_NORMAL, R.style.CustomBottomSheetDialogTheme);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.dialog_emoji_picker, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        rvEmojiGrid = view.findViewById(R.id.rv_emoji_grid);
        layoutCategories = view.findViewById(R.id.layout_emoji_categories);
        btnDismiss = view.findViewById(R.id.btn_emoji_dismiss);
        btnBackspace = view.findViewById(R.id.btn_emoji_backspace);
        etSearch = view.findViewById(R.id.et_emoji_search);

        rvEmojiGrid.setLayoutManager(new GridLayoutManager(getContext(), 8));
        emojiAdapter = new EmojiAdapter(new ArrayList<>());
        rvEmojiGrid.setAdapter(emojiAdapter);

        if (btnDismiss != null) {
            btnDismiss.setOnClickListener(v -> dismiss());
        }

        if (btnBackspace != null) {
            btnBackspace.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onBackspacePressed();
                }
            });
        }

        if (etSearch != null) {
            etSearch.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    filterEmojis(s != null ? s.toString().trim() : "");
                }

                @Override
                public void afterTextChanged(Editable s) {}
            });
        }

        setupCategoryTabs();
        loadCategory(0);
    }

    private void setupCategoryTabs() {
        if (layoutCategories == null) return;
        layoutCategories.removeAllViews();

        for (int i = 0; i < CATEGORY_ICONS.length; i++) {
            final int categoryIndex = i;
            TextView tvTab = new TextView(getContext());
            tvTab.setText(CATEGORY_ICONS[i]);
            tvTab.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f);
            tvTab.setPadding(16, 8, 16, 8);
            tvTab.setFocusable(true);
            tvTab.setClickable(true);

            updateTabStyle(tvTab, i == selectedCategoryIndex);

            tvTab.setOnClickListener(v -> {
                selectedCategoryIndex = categoryIndex;
                if (etSearch != null && etSearch.getText().length() > 0) {
                    etSearch.setText("");
                }
                loadCategory(categoryIndex);
                refreshTabStyles();
            });

            layoutCategories.addView(tvTab);
        }
    }

    private void refreshTabStyles() {
        if (layoutCategories == null) return;
        for (int i = 0; i < layoutCategories.getChildCount(); i++) {
            View child = layoutCategories.getChildAt(i);
            if (child instanceof TextView) {
                updateTabStyle((TextView) child, i == selectedCategoryIndex);
            }
        }
    }

    private void updateTabStyle(TextView tvTab, boolean isSelected) {
        if (isSelected) {
            tvTab.setAlpha(1.0f);
            tvTab.setScaleX(1.15f);
            tvTab.setScaleY(1.15f);
        } else {
            tvTab.setAlpha(0.55f);
            tvTab.setScaleX(1.0f);
            tvTab.setScaleY(1.0f);
        }
    }

    private void loadCategory(int index) {
        if (index < 0 || index >= EMOJI_CATEGORIES.length) return;
        List<String> emojis = Arrays.asList(EMOJI_CATEGORIES[index]);
        if (emojiAdapter != null) {
            emojiAdapter.setEmojis(emojis);
        }
    }

    private void filterEmojis(String query) {
        if (query.isEmpty()) {
            loadCategory(selectedCategoryIndex);
            return;
        }

        List<String> allFiltered = new ArrayList<>();
        for (String[] category : EMOJI_CATEGORIES) {
            for (String emoji : category) {
                if (!allFiltered.contains(emoji)) {
                    allFiltered.add(emoji);
                }
            }
        }
        if (emojiAdapter != null) {
            emojiAdapter.setEmojis(allFiltered);
        }
    }

    private class EmojiAdapter extends RecyclerView.Adapter<EmojiAdapter.EmojiViewHolder> {

        private List<String> emojiList;

        public EmojiAdapter(List<String> emojiList) {
            this.emojiList = emojiList;
        }

        public void setEmojis(List<String> newEmojis) {
            this.emojiList = new ArrayList<>(newEmojis);
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public EmojiViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View itemView = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_emoji, parent, false);
            return new EmojiViewHolder(itemView);
        }

        @Override
        public void onBindViewHolder(@NonNull EmojiViewHolder holder, int position) {
            String emoji = emojiList.get(position);
            holder.tvEmoji.setText(emoji);
            holder.itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onEmojiSelected(emoji);
                }
            });
        }

        @Override
        public int getItemCount() {
            return emojiList != null ? emojiList.size() : 0;
        }

        class EmojiViewHolder extends RecyclerView.ViewHolder {
            TextView tvEmoji;

            public EmojiViewHolder(@NonNull View itemView) {
                super(itemView);
                tvEmoji = itemView.findViewById(R.id.tv_emoji);
            }
        }
    }
}
