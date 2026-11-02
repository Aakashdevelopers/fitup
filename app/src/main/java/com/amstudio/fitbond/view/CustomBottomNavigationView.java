package com.amstudio.fitbond.view;

import android.content.Context;
import android.content.res.ColorStateList;
import android.transition.TransitionManager;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.PopupMenu;
import androidx.core.content.ContextCompat;

import com.amstudio.fitbond.R;

public class CustomBottomNavigationView extends FrameLayout {

    public interface OnItemSelectedListener {
        boolean onNavigationItemSelected(@NonNull MenuItem item);
    }

    public interface OnItemReselectedListener {
        void onNavigationItemReselected(@NonNull MenuItem item);
    }

    private ViewGroup tabsContainer;
    private View tabChat;
    private View tabCamera;
    private View tabPost;
    private View tabProfile;

    private LinearLayout pillChat;
    private LinearLayout pillCamera;
    private LinearLayout pillPost;
    private LinearLayout pillProfile;

    private ImageView ivChatIcon;
    private TextView tvChatLabel;
    private ImageView ivCameraIcon;
    private TextView tvCameraLabel;
    private ImageView ivPostIcon;
    private TextView tvPostLabel;
    private ImageView ivProfileIcon;
    private TextView tvProfileLabel;

    private MenuItem itemChat;
    private MenuItem itemCamera;
    private MenuItem itemPost;
    private MenuItem itemProfile;

    private OnItemSelectedListener itemSelectedListener;
    private OnItemReselectedListener itemReselectedListener;

    private int selectedItemId = R.id.nav_camera;
    private int selectedColor;
    private int inactiveColor;

    public CustomBottomNavigationView(@NonNull Context context) {
        super(context);
        init(context);
    }

    public CustomBottomNavigationView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public CustomBottomNavigationView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        LayoutInflater.from(context).inflate(R.layout.layout_custom_bottom_nav, this, true);

        selectedColor = ContextCompat.getColor(context, R.color.nav_selected_item);
        inactiveColor = ContextCompat.getColor(context, R.color.nav_inactive_item);

        tabsContainer = findViewById(R.id.tabs_container);

        tabChat = findViewById(R.id.tab_chat);
        tabCamera = findViewById(R.id.tab_camera);
        tabPost = findViewById(R.id.tab_post);
        tabProfile = findViewById(R.id.tab_profile);

        pillChat = findViewById(R.id.pill_chat);
        pillCamera = findViewById(R.id.pill_camera);
        pillPost = findViewById(R.id.pill_post);
        pillProfile = findViewById(R.id.pill_profile);

        ivChatIcon = findViewById(R.id.iv_chat_icon);
        tvChatLabel = findViewById(R.id.tv_chat_label);
        ivCameraIcon = findViewById(R.id.iv_camera_icon);
        tvCameraLabel = findViewById(R.id.tv_camera_label);
        ivPostIcon = findViewById(R.id.iv_post_icon);
        tvPostLabel = findViewById(R.id.tv_post_label);
        ivProfileIcon = findViewById(R.id.iv_profile_icon);
        tvProfileLabel = findViewById(R.id.tv_profile_label);

        PopupMenu popupMenu = new PopupMenu(context, this);
        itemChat = popupMenu.getMenu().add(0, R.id.nav_chat, 0, R.string.nav_chat);
        itemCamera = popupMenu.getMenu().add(0, R.id.nav_camera, 1, R.string.nav_camera);
        itemPost = popupMenu.getMenu().add(0, R.id.nav_post, 2, R.string.nav_post);
        itemProfile = popupMenu.getMenu().add(0, R.id.nav_profile, 3, R.string.nav_profile);

        if (tabChat != null) {
            tabChat.setOnClickListener(v -> handleTabClick(R.id.nav_chat, itemChat));
        }
        if (tabCamera != null) {
            tabCamera.setOnClickListener(v -> handleTabClick(R.id.nav_camera, itemCamera));
        }
        if (tabPost != null) {
            tabPost.setOnClickListener(v -> handleTabClick(R.id.nav_post, itemPost));
        }
        if (tabProfile != null) {
            tabProfile.setOnClickListener(v -> handleTabClick(R.id.nav_profile, itemProfile));
        }

        post(() -> updateUI(false));
    }

    private void handleTabClick(int itemId, MenuItem menuItem) {
        if (selectedItemId == itemId) {
            if (itemReselectedListener != null) {
                itemReselectedListener.onNavigationItemReselected(menuItem);
            }
            return;
        }

        if (itemSelectedListener != null) {
            boolean handled = itemSelectedListener.onNavigationItemSelected(menuItem);
            if (handled) {
                selectedItemId = itemId;
                updateUI(true);
            }
        } else {
            selectedItemId = itemId;
            updateUI(true);
        }
    }

    public void setOnItemSelectedListener(@Nullable OnItemSelectedListener listener) {
        this.itemSelectedListener = listener;
    }

    public void setOnItemReselectedListener(@Nullable OnItemReselectedListener listener) {
        this.itemReselectedListener = listener;
    }

    public void setSelectedItemId(int itemId) {
        if (this.selectedItemId != itemId) {
            this.selectedItemId = itemId;
            updateUI(true);
        } else {
            updateUI(false);
        }
    }

    public int getSelectedItemId() {
        return selectedItemId;
    }

    private void updateUI(boolean animate) {
        if (animate && tabsContainer != null) {
            TransitionManager.beginDelayedTransition(tabsContainer);
        }

        boolean isChat = (selectedItemId == R.id.nav_chat);
        boolean isCamera = (selectedItemId == R.id.nav_camera);
        boolean isPost = (selectedItemId == R.id.nav_post);
        boolean isProfile = (selectedItemId == R.id.nav_profile);

        setTabSelected(pillChat, ivChatIcon, tvChatLabel, isChat);
        setTabSelected(pillCamera, ivCameraIcon, tvCameraLabel, isCamera);
        setTabSelected(pillPost, ivPostIcon, tvPostLabel, isPost);
        setTabSelected(pillProfile, ivProfileIcon, tvProfileLabel, isProfile);
    }

    private void setTabSelected(LinearLayout pill, ImageView icon, TextView label, boolean active) {
        if (active) {
            if (pill != null) {
                pill.setBackgroundResource(R.drawable.bg_nav_selected_pill);
            }
            if (icon != null) {
                icon.setImageTintList(ColorStateList.valueOf(selectedColor));
            }
            if (label != null) {
                label.setVisibility(View.VISIBLE);
                label.setTextColor(selectedColor);
            }
        } else {
            if (pill != null) {
                pill.setBackground(null);
            }
            if (icon != null) {
                icon.setImageTintList(ColorStateList.valueOf(inactiveColor));
            }
            if (label != null) {
                label.setVisibility(View.GONE);
            }
        }
    }
}
