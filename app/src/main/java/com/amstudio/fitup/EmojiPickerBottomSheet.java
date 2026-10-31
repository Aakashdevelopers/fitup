package com.amstudio.fitup;

import android.os.Bundle;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
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
    private ImageButton btnBackspace;
    private EmojiAdapter emojiAdapter;
    private int selectedCategoryIndex = 0;

    private static final String[][] EMOJI_CATEGORIES = {
            // Category 0: Smileys & Emotions
            {
                    "😀", "😃", "😄", "😁", "😆", "😅", "😂", "🤣", "🥲", "☺️", "😊", "😇", "🙂", "🙃", "😉",
                    "😌", "😍", "🥰", "😘", "😗", "😙", "😚", "😋", "😛", "😝", "😜", "🤪", "🤨", "🧐", "🤓",
                    "😎", "🥸", "🤩", "🥳", "😏", "😒", "😞", "😔", "😟", "😕", "🙁", "☹️", "😣", "😖", "😫",
                    "😩", "🥺", "😢", "😭", "😤", "😠", "😡", "🤬", "🤯", "😳", "🥵", "🥶", "😱", "😨", "😰",
                    "😥", "😓", "🤗", "🤔", "🤭", "🤫", "🤥", "😶", "😐", "😑", "😬", "🙄", "😯", "😦", "😧",
                    "😮", "😲", "🥱", "😴", "🤤", "😪", "😵", "🤐", "🥴", "🤢", "🤮", "🤧", "😷", "🤒", "🤕",
                    "🤑", "🤠", "😈", "👿", "👹", "👺", "🤡", "💩", "👻", "💀", "☠️", "👽", "👾", "🤖", "🎃"
            },
            // Category 1: People & Gestures
            {
                    "👋", "🤚", "🖐️", "✋", "🖖", "👌", "🤌", "🤏", "✌️", "🤞", "🤟", "🤘", "🤙", "👈", "👉",
                    "👆", "🖕", "👇", "☝️", "👍", "👎", "✊", "👊", "🤛", "🤜", "👏", "🙌", "👐", "🤲", "🤝",
                    "✍️", "💅", "🤳", "💪", "🦾", "🦿", "🦵", "🦶", "👂", "🦻", "👃", "🧠", "🫀", "🫁", "🦷",
                    "🦴", "👀", "👁️", "👅", "👄", "👶", "🧒", "👦", "👧", "🧑", "👨", "👩", "🧔", "👱‍♂️", "👱‍♀️",
                    "🧓", "👴", "👵", "🙍", "🙎", "🙅", "🙆", "💁", "🙋", "🧏", "🙇", "🤦", "🤷", "👨‍⚕️", "👩‍⚕️"
            },
            // Category 2: Hearts & Symbols
            {
                    "❤️", "🧡", "💛", "💚", "💙", "💜", "🖤", "🤍", "🤎", "💔", "❣️", "💕", "💞", "💓", "💗",
                    "💖", "💘", "💝", "💟", "☮️", "✝️", "☪️", "🕉️", "☸️", "✡️", "🔯", "🕎", "☯️", "☦️", "🛐",
                    "🛑", "⛔", "📛", "🚫", "💯", "💢", "♨️", "⚠️", "⚡️", "💥", "🔥", "✨", "🌟", "💫", "⭐️",
                    "✅", "❇️", "✳️", "❎", "🌐", "💠", "💤", "🏧", "♿", "🅿️", "🈳", "🚻", "🚹", "🚺", "🚼"
            },
            // Category 3: Animals & Nature
            {
                    "🐶", "🐱", "🐭", "🐹", "🐰", "🦊", "🐻", "🐼", "🐻‍❄️", "🐨", "🐯", "🦁", "🐮", "🐷", "🐸",
                    "🐵", "🙈", "🙉", "🙊", "🐒", "🐔", "🐧", "🐦", "🐤", "🐣", "🐥", "🦆", "🦅", "🦉", "🦇",
                    "🐺", "🐗", "🐴", "🦄", "🐝", "🐛", "🦋", "🐌", "🐞", "🐜", "🪰", "🪲", "🪳", "🦟", "🦗",
                    "🕷️", "🕸️", "🦂", "🐢", "🐍", "🦎", "🦖", "🦕", "🐙", "🦑", "🦐", "🦞", "🦀", "🐡", "🐠",
                    "🐬", "🐳", "🐋", "🦈", "🐊", "🐅", "🐆", "Zebra", "🦍", "🐘", "🦏", "🐪", "🦒", "🦘", "🐕"
            },
            // Category 4: Food & Drink
            {
                    "🍏", "🍎", "🍐", "🍊", "🍋", "🍌", "🍉", "🍇", "🍓", "🫐", "🍈", "🍒", "🍑", "🥭", "🍍",
                    "🥥", "🥝", "🍅", "🍆", "🥑", "🥦", "🥬", "🥒", "🌶️", "🫑", "🌽", "🥕", "🫒", "🧄", "🧅",
                    "🥔", "🍠", "🥐", "🥯", "🍞", "🥖", "🥨", "🧀", "🥚", "🍳", "🧈", "🥞", "🧇", "🥓", "🥩",
                    "🍗", "🍖", "🌭", "🍔", "🍟", "🍕", "🥪", "🥙", "🧆", "🌮", "🌯", "🥗", "🥘", "🍝", "🍜",
                    "🍲", "🍛", "🍣", "🍱", "🥟", "🦪", "🍤", "🍙", "🍚", "🍦", "🍧", "🍨", "🍩", "🍪", "🎂",
                    "🍰", "🧁", "🥧", "🍫", "🍬", "🍭", "🍮", "🍯", "☕️", "🫖", "🍵", "🧃", "🥤", "🧋", "🍺"
            },
            // Category 5: Sports & Activities
            {
                    "⚽️", "🏀", "🏈", "⚾️", "🥎", "🎾", "🏐", "🏉", "🥏", "🎱", "🪀", "🏓", "🏸", "🏒", "🏑",
                    "🥍", "🏏", "🪃", "🥅", "⛳️", "🪁", "🏹", "🎣", "🥊", "🥋", "🎽", "🛹", "🛼", "🛷", "⛸️",
                    "🎿", "⛷️", "🏂", "🏋️", "🤼", "🤸", "⛹️", "🤺", "🤾", "🏌️", "🏇", "🧘", "🏄", "🏊", "🤽",
                    "🚣", "🧗", "🚵", "🚴", "🏆", "🥇", "🥈", "🥉", "🏅", "🎖️", "🎗️", "🎟️", "🎫", "🎪", "🎯",
                    "🎲", "♟️", "🎳", "🎮", "🎰", "🧩"
            },
            // Category 6: Travel & Places
            {
                    "🚗", "🚕", "🚙", "🚌", "🏎️", "🚑", "🚒", "🚐", "🛻", "🚚", "🚛", "🚜", "🛵", "🏍️", "🛺",
                    "🚲", "🛴", "🚨", "🚔", "🚘", "🚃", "🚋", "🚞", "🚄", "🚅", "🚈", "🚂", "🚆", "🚇", "🚊",
                    "✈️", "🛫", "🛬", "🛸", "🚀", "🛰️", "🚁", "🛶", "⛵️", "🛥️", "🚤", "⛴️", "🛳️", "⚓️", "⛽️",
                    "🚧", "🚨", "🚥", "🚦", "🚏", "🗺️", "🗿", "🏙️", "🌃", "🌄", "🌅", "🌆", "🌇", "🌉", "🎡",
                    "🎢", "💈", "🎪", "🏕️", "🏠", "🏡", "🏢", "🏬", "🏣", "🏥", "🏦", "🏨", "🏪", "🏫", "🏰"
            }
    };

    private static final String[] CATEGORY_ICONS = {"😃", "👋", "❤️", "🐶", "🍕", "⚽", "🚗"};

    public void setOnEmojiClickListener(OnEmojiClickListener listener) {
        this.listener = listener;
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
        btnBackspace = view.findViewById(R.id.btn_emoji_backspace);

        rvEmojiGrid.setLayoutManager(new GridLayoutManager(getContext(), 7));
        emojiAdapter = new EmojiAdapter(new ArrayList<>());
        rvEmojiGrid.setAdapter(emojiAdapter);

        setupCategoryTabs();
        loadCategory(0);

        if (btnBackspace != null) {
            btnBackspace.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onBackspacePressed();
                }
            });
        }
    }

    private void setupCategoryTabs() {
        if (layoutCategories == null) return;
        layoutCategories.removeAllViews();

        for (int i = 0; i < CATEGORY_ICONS.length; i++) {
            final int categoryIndex = i;
            TextView tvTab = new TextView(getContext());
            tvTab.setText(CATEGORY_ICONS[i]);
            tvTab.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f);
            tvTab.setPadding(20, 10, 20, 10);
            tvTab.setFocusable(true);
            tvTab.setClickable(true);

            updateTabStyle(tvTab, i == selectedCategoryIndex);

            tvTab.setOnClickListener(v -> {
                selectedCategoryIndex = categoryIndex;
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
            tvTab.setAlpha(0.5f);
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
