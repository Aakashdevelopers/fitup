package com.amstudio.fitup;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.LinearSnapHelper;
import androidx.recyclerview.widget.RecyclerView;

import com.amstudio.fitup.svg.SvgTemplateManager;

public class FoodTemplateActivity extends AppCompatActivity {

    public static final String EXTRA_FOOD_IMAGE_URI = "EXTRA_FOOD_IMAGE_URI";
    public static final String EXTRA_FOOD_TITLE = "EXTRA_FOOD_TITLE";
    public static final String EXTRA_FOOD_SUBTITLE = "EXTRA_FOOD_SUBTITLE";
    public static final String EXTRA_FOOD_GRADE = "EXTRA_FOOD_GRADE";
    public static final String EXTRA_FOOD_KCAL = "EXTRA_FOOD_KCAL";
    public static final String EXTRA_FOOD_PROTEIN = "EXTRA_FOOD_PROTEIN";
    public static final String EXTRA_FOOD_CARBS = "EXTRA_FOOD_CARBS";
    public static final String EXTRA_FOOD_FAT = "EXTRA_FOOD_FAT";
    public static final String EXTRA_FOOD_FIBER = "EXTRA_FOOD_FIBER";
    public static final String EXTRA_FOOD_SUGAR = "EXTRA_FOOD_SUGAR";
    public static final String EXTRA_FOOD_SODIUM = "EXTRA_FOOD_SODIUM";

    private String foodTitle = "Lay's® Classic Potato Chips";
    private String foodSubtitle = "FreshMart · 170 g container";
    private String foodGrade = "A";
    private String kcal = "90";
    private String protein = "16g";
    private String carbs = "6g";
    private String fat = "0g";
    private @Nullable Uri foodImageUri;

    private RecyclerView rvFoodTemplates;
    private FoodTemplateAdapter adapter;
    private TextView tvCarouselIndicator;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_food_template);

        WindowInsetsControllerCompat insetsController = WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        if (insetsController != null) {
            insetsController.setAppearanceLightStatusBars(false);
            insetsController.setAppearanceLightNavigationBars(false);
        }

        View rootLayout = findViewById(R.id.root_food_template);
        View headerBar = findViewById(R.id.header_bar);
        View bottomActionBar = findViewById(R.id.bottom_action_bar);

        if (rootLayout != null) {
            ViewCompat.setOnApplyWindowInsetsListener(rootLayout, (v, insets) -> {
                Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                if (headerBar != null) {
                    headerBar.setPadding(
                            headerBar.getPaddingLeft(),
                            systemBars.top + 12,
                            headerBar.getPaddingRight(),
                            headerBar.getPaddingBottom()
                    );
                }
                if (bottomActionBar != null) {
                    bottomActionBar.setPadding(
                            bottomActionBar.getPaddingLeft(),
                            bottomActionBar.getPaddingTop(),
                            bottomActionBar.getPaddingRight(),
                            systemBars.bottom + 12
                    );
                }
                return WindowInsetsCompat.CONSUMED;
            });
        }

        parseIntentData();
        initViews();
        setupCarouselRecyclerView();
    }

    private void parseIntentData() {
        Intent intent = getIntent();
        if (intent != null) {
            if (intent.hasExtra(EXTRA_FOOD_TITLE) && intent.getStringExtra(EXTRA_FOOD_TITLE) != null) {
                foodTitle = intent.getStringExtra(EXTRA_FOOD_TITLE);
            }
            if (intent.hasExtra(EXTRA_FOOD_SUBTITLE) && intent.getStringExtra(EXTRA_FOOD_SUBTITLE) != null) {
                foodSubtitle = intent.getStringExtra(EXTRA_FOOD_SUBTITLE);
            }
            if (intent.hasExtra(EXTRA_FOOD_GRADE) && intent.getStringExtra(EXTRA_FOOD_GRADE) != null) {
                foodGrade = intent.getStringExtra(EXTRA_FOOD_GRADE);
            }
            if (intent.hasExtra(EXTRA_FOOD_KCAL) && intent.getStringExtra(EXTRA_FOOD_KCAL) != null) {
                kcal = intent.getStringExtra(EXTRA_FOOD_KCAL);
            }
            if (intent.hasExtra(EXTRA_FOOD_PROTEIN) && intent.getStringExtra(EXTRA_FOOD_PROTEIN) != null) {
                protein = intent.getStringExtra(EXTRA_FOOD_PROTEIN);
            }
            if (intent.hasExtra(EXTRA_FOOD_CARBS) && intent.getStringExtra(EXTRA_FOOD_CARBS) != null) {
                carbs = intent.getStringExtra(EXTRA_FOOD_CARBS);
            }
            if (intent.hasExtra(EXTRA_FOOD_FAT) && intent.getStringExtra(EXTRA_FOOD_FAT) != null) {
                fat = intent.getStringExtra(EXTRA_FOOD_FAT);
            }
            if (intent.hasExtra(EXTRA_FOOD_IMAGE_URI) && intent.getStringExtra(EXTRA_FOOD_IMAGE_URI) != null) {
                foodImageUri = Uri.parse(intent.getStringExtra(EXTRA_FOOD_IMAGE_URI));
            }
        }
    }

    private void initViews() {
        ImageButton btnBack = findViewById(R.id.btn_back_templates);
        if (btnBack != null) {
            btnBack.setOnClickListener(v -> finish());
        }

        tvCarouselIndicator = findViewById(R.id.tv_carousel_indicator);
        rvFoodTemplates = findViewById(R.id.rv_food_templates);
    }

    private LinearSnapHelper snapHelper;

    private void setupCarouselRecyclerView() {
        if (rvFoodTemplates == null) return;

        LinearLayoutManager layoutManager = new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false);
        rvFoodTemplates.setLayoutManager(layoutManager);

        // Dynamically compute side padding so the centered card is perfectly centered on screen
        rvFoodTemplates.post(() -> {
            int screenWidth = rvFoodTemplates.getWidth();
            if (screenWidth > 0) {
                int cardWidth = Math.round(290 * getResources().getDisplayMetrics().density);
                int sidePadding = Math.max(0, (screenWidth - cardWidth) / 2);
                rvFoodTemplates.setPadding(sidePadding, 0, sidePadding, 0);
            }
        });

        adapter = new FoodTemplateAdapter(new FoodTemplateAdapter.OnTemplateSelectListener() {
            @Override
            public void onTemplateSelected(int templateIndex) {
                View centerView = snapHelper != null ? snapHelper.findSnapView(layoutManager) : null;
                int centerPos = centerView != null ? layoutManager.getPosition(centerView) : -1;

                if (templateIndex == centerPos) {
                    launchSnapEditorForTemplate(templateIndex);
                } else {
                    rvFoodTemplates.smoothScrollToPosition(templateIndex);
                }
            }

            @Override
            public void onUseButtonClicked(int templateIndex) {
                launchSnapEditorForTemplate(templateIndex);
            }
        });

        adapter.updateFoodData(this, foodTitle, foodSubtitle, foodGrade, kcal, protein, carbs, fat, foodImageUri);
        rvFoodTemplates.setAdapter(adapter);

        snapHelper = new LinearSnapHelper();
        snapHelper.attachToRecyclerView(rvFoodTemplates);

        rvFoodTemplates.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                int childCount = recyclerView.getChildCount();
                if (childCount == 0) return;

                int center = recyclerView.getWidth() / 2;
                int maxDistance = (int) (recyclerView.getWidth() * 0.55f);

                for (int i = 0; i < childCount; i++) {
                    View child = recyclerView.getChildAt(i);
                    int childCenter = (child.getLeft() + child.getRight()) / 2;
                    int distanceFromCenter = Math.abs(childCenter - center);

                    float fraction = Math.min(1.0f, (float) distanceFromCenter / (float) maxDistance);

                    // 1. Smooth Scale Transformation: center is 1.0, side items ~0.84
                    float scale = 1.0f - (0.16f * fraction);
                    child.setScaleX(scale);
                    child.setScaleY(scale);

                    // 2. Smooth Alpha Fading: center is 1.0, side items ~0.78
                    float alpha = 1.0f - (0.22f * fraction);
                    child.setAlpha(alpha);

                    // 3. Elevation Z-translation
                    float elevation = 16.0f * (1.0f - fraction);
                    child.setTranslationZ(elevation);

                    // Keep cards flat & aligned with screen without 3D rotation skew
                    child.setRotationY(0.0f);
                }

                View centerChild = snapHelper.findSnapView(layoutManager);
                if (centerChild != null) {
                    int centerPosition = layoutManager.getPosition(centerChild);
                    if (centerPosition >= 0 && centerPosition < adapter.getItemCount()) {
                        SvgTemplateManager.TemplateItem item = adapter.getTemplateItem(centerPosition);
                        if (tvCarouselIndicator != null) {
                            tvCarouselIndicator.setText("Template " + (centerPosition + 1) + " of " + adapter.getItemCount() + ": " + item.name);
                        }
                    }
                }
            }
        });

        // Trigger initial layout scroll effect
        rvFoodTemplates.post(() -> {
            if (rvFoodTemplates != null) {
                rvFoodTemplates.scrollBy(1, 0);
            }
        });
    }

    private void launchSnapEditorForTemplate(int templateIndex) {
        Intent intent = new Intent(FoodTemplateActivity.this, FoodSnapEditActivity.class);
        intent.putExtra(FoodSnapEditActivity.EXTRA_TEMPLATE_INDEX, templateIndex);
        if (foodImageUri != null) {
            intent.putExtra(FoodSnapEditActivity.EXTRA_FOOD_IMAGE_URI, foodImageUri.toString());
        }
        intent.putExtra(FoodSnapEditActivity.EXTRA_FOOD_TITLE, foodTitle);
        intent.putExtra(FoodSnapEditActivity.EXTRA_FOOD_SUBTITLE, foodSubtitle);
        intent.putExtra(FoodSnapEditActivity.EXTRA_FOOD_GRADE, foodGrade);
        intent.putExtra(FoodSnapEditActivity.EXTRA_FOOD_KCAL, kcal);
        intent.putExtra(FoodSnapEditActivity.EXTRA_FOOD_PROTEIN, protein);
        intent.putExtra(FoodSnapEditActivity.EXTRA_FOOD_CARBS, carbs);
        intent.putExtra(FoodSnapEditActivity.EXTRA_FOOD_FAT, fat);
        startActivity(intent);
    }
}
