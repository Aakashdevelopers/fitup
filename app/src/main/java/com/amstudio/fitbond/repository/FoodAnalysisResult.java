package com.amstudio.fitbond.repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class FoodAnalysisResult {

    private String status = "success"; // "success" or "no_food_detected"
    private String imageType = "food"; // "food" or "non_food"
    private String mealType = "Meal";  // "Breakfast", "Lunch", "Dinner", "Snack", etc.
    private String message = "";

    private List<DetectedFoodItem> detectedItems = new ArrayList<>();
    private NutritionInfo totalNutrition = new NutritionInfo();
    private FitnessAnalysisInfo fitnessAnalysis = new FitnessAnalysisInfo();
    private DataQualityInfo dataQuality = new DataQualityInfo();

    // Legacy fields for backward compatibility
    private boolean isFood = true;
    private String title;
    private String subtitle;
    private String grade;
    private String kcal;
    private String protein;
    private String carbs;
    private String fat;
    private String fiber;
    private String sugar;
    private String sodium;
    private String satFat;
    private String aiInsight;
    private String rawJson;

    public FoodAnalysisResult() {}

    public FoodAnalysisResult(boolean isFood, String title, String subtitle, String grade, String kcal,
                              String protein, String carbs, String fat, String fiber,
                              String sugar, String sodium, String satFat, String aiInsight) {
        this.isFood = isFood;
        this.status = isFood ? "success" : "no_food_detected";
        this.title = title;
        this.subtitle = subtitle;
        this.grade = grade;
        this.kcal = kcal;
        this.protein = protein;
        this.carbs = carbs;
        this.fat = fat;
        this.fiber = fiber;
        this.sugar = sugar;
        this.sodium = sodium;
        this.satFat = satFat;
        this.aiInsight = aiInsight;
    }

    // Inner Class: Individual Detected Food Item
    public static class DetectedFoodItem {
        private String name = "";
        private String commonName = "";
        private String hindiName = "";
        private String category = "general"; // dairy, fruit, vegetable, fitness_nutrition, beverage, ingredient, meal
        private String foodType = "vegetarian"; // vegetarian, non-vegetarian, vegan
        private String preparation = "cooked"; // cooked, raw, packaged
        private String preparationMethod = ""; // grilled, boiled, fried, sauteed, raw
        private int count = 1;
        private String position = "center"; // e.g. "held in hand", "foreground table", "background right"
        private double[] boundingBox = null; // [ymin, xmin, ymax, xmax] normalized 0.0 to 1.0
        private String visiblePortion = "100%"; // e.g. "80% visible", "partially covered by fingers"
        private String uncertaintyReason = ""; // e.g. "partially occluded by fingers"
        private double estimatedQuantity = 100.0;
        private String quantityUnit = "grams"; // grams, ml, pieces
        private double confidence = 0.95;
        private NutritionInfo nutrition = new NutritionInfo();

        public DetectedFoodItem() {}

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public String getCommonName() { return commonName; }
        public void setCommonName(String commonName) { this.commonName = commonName; }

        public String getHindiName() { return hindiName; }
        public void setHindiName(String hindiName) { this.hindiName = hindiName; }

        public String getCategory() { return category; }
        public void setCategory(String category) { this.category = category; }

        public String getFoodType() { return foodType; }
        public void setFoodType(String foodType) { this.foodType = foodType; }

        public String getPreparation() { return preparation; }
        public void setPreparation(String preparation) { this.preparation = preparation; }

        public String getPreparationMethod() { return preparationMethod; }
        public void setPreparationMethod(String preparationMethod) { this.preparationMethod = preparationMethod; }

        public int getCount() { return count; }
        public void setCount(int count) { this.count = count; }

        public String getPosition() { return position; }
        public void setPosition(String position) { this.position = position; }

        public double[] getBoundingBox() { return boundingBox; }
        public void setBoundingBox(double[] boundingBox) { this.boundingBox = boundingBox; }

        public String getVisiblePortion() { return visiblePortion; }
        public void setVisiblePortion(String visiblePortion) { this.visiblePortion = visiblePortion; }

        public String getUncertaintyReason() { return uncertaintyReason; }
        public void setUncertaintyReason(String uncertaintyReason) { this.uncertaintyReason = uncertaintyReason; }

        public double getEstimatedQuantity() { return estimatedQuantity; }
        public void setEstimatedQuantity(double estimatedQuantity) { this.estimatedQuantity = estimatedQuantity; }

        public String getQuantityUnit() { return quantityUnit; }
        public void setQuantityUnit(String quantityUnit) { this.quantityUnit = quantityUnit; }

        public double getConfidence() { return confidence; }
        public void setConfidence(double confidence) { this.confidence = confidence; }

        public NutritionInfo getNutrition() { return nutrition; }
        public void setNutrition(NutritionInfo nutrition) { this.nutrition = nutrition; }

        public String getDisplayName() {
            if (hindiName != null && !hindiName.trim().isEmpty()) {
                return name + " (" + hindiName.trim() + ")";
            }
            return name;
        }

        public String getCategoryEmoji() {
            String lower = (name + " " + commonName + " " + category).toLowerCase(Locale.US);
            if (lower.contains("apple")) return "🍎";
            if (lower.contains("banana")) return "🍌";
            if (lower.contains("orange") || lower.contains("citrus") || lower.contains("kinnow")) return "🍊";
            if (lower.contains("mango")) return "🥭";
            if (lower.contains("grape")) return "🍇";
            if (lower.contains("watermelon")) return "🍉";
            if (lower.contains("pineapple")) return "🍍";
            if (lower.contains("strawberry") || lower.contains("berry")) return "🍓";
            if (lower.contains("papaya")) return "🍈";
            if (lower.contains("guava")) return "🍏";
            if (lower.contains("avocado")) return "🥑";
            if (lower.contains("fruit")) return "🍎";

            if (lower.contains("broccoli")) return "🥦";
            if (lower.contains("potato")) return "🥔";
            if (lower.contains("tomato")) return "🍅";
            if (lower.contains("carrot")) return "🥕";
            if (lower.contains("spinach")) return "🥬";
            if (lower.contains("cucumber")) return "🥒";
            if (lower.contains("vegetable") || lower.contains("veggie")) return "🥦";

            if (lower.contains("pizza")) return "🍕";
            if (lower.contains("pasta") || lower.contains("spaghetti")) return "🍝";
            if (lower.contains("burger")) return "🍔";
            if (lower.contains("sandwich")) return "🥪";
            if (lower.contains("rice") || lower.contains("biryani")) return "🍚";
            if (lower.contains("roti") || lower.contains("chapati") || lower.contains("naan")) return "🫓";
            if (lower.contains("dal") || lower.contains("curry") || lower.contains("paneer")) return "🍛";
            if (lower.contains("salad")) return "🥗";
            if (lower.contains("meat") || lower.contains("steak") || lower.contains("chicken")) return "🥩";

            if (lower.contains("milk") || lower.contains("shake") || lower.contains("smoothie")) return "🥛";
            if (lower.contains("juice") || lower.contains("beverage") || lower.contains("drink")) return "🥤";
            if (lower.contains("egg")) return "🥚";
            if (lower.contains("nuts") || lower.contains("almond")) return "🥜";

            return "🍽️";
        }

        public String getCleanLocationText() {
            StringBuilder sb = new StringBuilder();
            if (position != null && !position.trim().isEmpty()) {
                sb.append("📍 ").append(position.trim());
            }
            if (visiblePortion != null && !visiblePortion.trim().isEmpty()) {
                if (sb.length() > 0) sb.append("  •  ");
                sb.append("👁️ ").append(visiblePortion.trim());
            }
            if (uncertaintyReason != null && !uncertaintyReason.trim().isEmpty()) {
                if (sb.length() > 0) sb.append("  •  ");
                sb.append("⚠️ ").append(uncertaintyReason.trim());
            }
            return sb.toString();
        }

        public String getFormattedBoundingBox() {
            if (boundingBox != null && boundingBox.length == 4) {
                return String.format(Locale.US, "[%.2f, %.2f, %.2f, %.2f]",
                        boundingBox[0], boundingBox[1], boundingBox[2], boundingBox[3]);
            }
            return null;
        }
    }

    // Inner Class: Nutrition Information
    public static class NutritionInfo {
        private double calories = 0.0;
        private double protein = 0.0;
        private double carbs = 0.0;
        private double fat = 0.0;
        private double saturatedFat = 0.0;
        private double fiber = 0.0;
        private double sugar = 0.0;
        private double sodium = 0.0;
        private double potassium = 0.0;

        public NutritionInfo() {}

        public NutritionInfo(double calories, double protein, double carbs, double fat,
                             double saturatedFat, double fiber, double sugar, double sodium, double potassium) {
            this.calories = calories;
            this.protein = protein;
            this.carbs = carbs;
            this.fat = fat;
            this.saturatedFat = saturatedFat;
            this.fiber = fiber;
            this.sugar = sugar;
            this.sodium = sodium;
            this.potassium = potassium;
        }

        public double getCalories() { return calories; }
        public void setCalories(double calories) { this.calories = calories; }

        public double getProtein() { return protein; }
        public void setProtein(double protein) { this.protein = protein; }

        public double getCarbs() { return carbs; }
        public void setCarbs(double carbs) { this.carbs = carbs; }

        public double getFat() { return fat; }
        public void setFat(double fat) { this.fat = fat; }

        public double getSaturatedFat() { return saturatedFat; }
        public void setSaturatedFat(double saturatedFat) { this.saturatedFat = saturatedFat; }

        public double getFiber() { return fiber; }
        public void setFiber(double fiber) { this.fiber = fiber; }

        public double getSugar() { return sugar; }
        public void setSugar(double sugar) { this.sugar = sugar; }

        public double getSodium() { return sodium; }
        public void setSodium(double sodium) { this.sodium = sodium; }

        public double getPotassium() { return potassium; }
        public void setPotassium(double potassium) { this.potassium = potassium; }
    }

    // Inner Class: Fitness Intelligence Analysis
    public static class FitnessAnalysisInfo {
        private String muscleBuilding = "";
        private String weightLoss = "";
        private String mealBalance = "";
        private String recommendation = "";

        public FitnessAnalysisInfo() {}

        public String getMuscleBuilding() { return muscleBuilding; }
        public void setMuscleBuilding(String muscleBuilding) { this.muscleBuilding = muscleBuilding; }

        public String getWeightLoss() { return weightLoss; }
        public void setWeightLoss(String weightLoss) { this.weightLoss = weightLoss; }

        public String getMealBalance() { return mealBalance; }
        public void setMealBalance(String mealBalance) { this.mealBalance = mealBalance; }

        public String getRecommendation() { return recommendation; }
        public void setRecommendation(String recommendation) { this.recommendation = recommendation; }
    }

    // Inner Class: Data Quality
    public static class DataQualityInfo {
        private String portionEstimation = "estimated";
        private String nutritionSource = "verified_database_or_ai_estimate";
        private double overallConfidence = 0.95;

        public DataQualityInfo() {}

        public String getPortionEstimation() { return portionEstimation; }
        public void setPortionEstimation(String portionEstimation) { this.portionEstimation = portionEstimation; }

        public String getNutritionSource() { return nutritionSource; }
        public void setNutritionSource(String nutritionSource) { this.nutritionSource = nutritionSource; }

        public double getOverallConfidence() { return overallConfidence; }
        public void setOverallConfidence(double overallConfidence) { this.overallConfidence = overallConfidence; }
    }

    // Getters & Setters for main result object
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getImageType() { return imageType; }
    public void setImageType(String imageType) { this.imageType = imageType; }

    public String getMealType() { return mealType; }
    public void setMealType(String mealType) { this.mealType = mealType; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public List<DetectedFoodItem> getDetectedItems() { return detectedItems; }
    public void setDetectedItems(List<DetectedFoodItem> detectedItems) { this.detectedItems = detectedItems; }

    public int getTotalItemCount() {
        if (detectedItems == null || detectedItems.isEmpty()) return 0;
        int total = 0;
        for (DetectedFoodItem item : detectedItems) {
            total += Math.max(1, item.getCount());
        }
        return total;
    }

    public NutritionInfo getTotalNutrition() { return totalNutrition; }
    public void setTotalNutrition(NutritionInfo totalNutrition) { this.totalNutrition = totalNutrition; }

    public FitnessAnalysisInfo getFitnessAnalysis() { return fitnessAnalysis; }
    public void setFitnessAnalysis(FitnessAnalysisInfo fitnessAnalysis) { this.fitnessAnalysis = fitnessAnalysis; }

    public DataQualityInfo getDataQuality() { return dataQuality; }
    public void setDataQuality(DataQualityInfo dataQuality) { this.dataQuality = dataQuality; }

    // Dynamic backward-compatible getters
    public boolean isFood() {
        return "success".equalsIgnoreCase(status) && ((detectedItems != null && !detectedItems.isEmpty()) || isFood);
    }

    public void setFood(boolean food) {
        this.isFood = food;
        this.status = food ? "success" : "no_food_detected";
    }

    public String getTitle() {
        if (title != null && !title.trim().isEmpty()) {
            return title;
        }
        if (!isFood()) {
            return "Food Not Found";
        }
        if (detectedItems != null && !detectedItems.isEmpty()) {
            if (detectedItems.size() == 1) {
                DetectedFoodItem item = detectedItems.get(0);
                if (item.getCount() > 1) {
                    return item.getCount() + "x " + item.getDisplayName();
                }
                return item.getDisplayName();
            } else {
                StringBuilder sb = new StringBuilder();
                int limit = Math.min(detectedItems.size(), 3);
                for (int i = 0; i < limit; i++) {
                    if (i > 0) {
                        sb.append(i == limit - 1 ? " & " : ", ");
                    }
                    DetectedFoodItem item = detectedItems.get(i);
                    if (item.getCount() > 1) {
                        sb.append(item.getCount()).append("x ");
                    }
                    sb.append(item.getName());
                }
                return sb.toString();
            }
        }
        return "Scanned Meal";
    }

    public void setTitle(String title) { this.title = title; }

    public String getSubtitle() {
        if (subtitle != null && !subtitle.trim().isEmpty()) {
            return subtitle;
        }
        if (!isFood()) {
            return message != null && !message.isEmpty() ? message : "No food or fruit detected";
        }
        if (detectedItems != null && !detectedItems.isEmpty()) {
            double totalQty = 0;
            String unit = "g";
            int itemCount = getTotalItemCount();
            for (DetectedFoodItem item : detectedItems) {
                totalQty += item.getEstimatedQuantity();
                if (item.getQuantityUnit() != null && !item.getQuantityUnit().isEmpty()) {
                    unit = item.getQuantityUnit().toLowerCase().contains("ml") ? "ml" : "g";
                }
            }
            String prefix = (mealType != null && !mealType.isEmpty() && !"Meal".equalsIgnoreCase(mealType)) ? mealType : "Full Scene Scan";
            String countLabel = itemCount > 1 ? itemCount + " items" : "1 item";
            return prefix + " · " + countLabel + " (" + Math.round(totalQty) + " " + unit + ")";
        }
        return "1 serving";
    }

    public void setSubtitle(String subtitle) { this.subtitle = subtitle; }

    public String getGrade() {
        if (grade != null && !grade.trim().isEmpty()) {
            return grade;
        }
        if (!isFood()) return "N/A";

        // Check if detected items are fruits, vegetables, or healthy whole food
        if (detectedItems != null && !detectedItems.isEmpty()) {
            boolean hasFruitOrVeg = false;
            for (DetectedFoodItem item : detectedItems) {
                String cat = (item.getCategory() != null ? item.getCategory() : "").toLowerCase(Locale.US);
                String name = (item.getName() != null ? item.getName() : "").toLowerCase(Locale.US);
                String common = (item.getCommonName() != null ? item.getCommonName() : "").toLowerCase(Locale.US);
                if (cat.contains("fruit") || cat.contains("veg") ||
                        name.contains("apple") || name.contains("banana") || name.contains("orange") ||
                        name.contains("mango") || name.contains("grape") || name.contains("papaya") ||
                        name.contains("salad") || name.contains("guava") || name.contains("berry") ||
                        name.contains("watermelon") || name.contains("pineapple") || name.contains("broccoli") ||
                        common.contains("apple") || common.contains("banana") || common.contains("fruit")) {
                    hasFruitOrVeg = true;
                    break;
                }
            }
            if (hasFruitOrVeg) {
                return "A+";
            }
        }

        if (totalNutrition != null) {
            double protein = totalNutrition.getProtein();
            double kcal = totalNutrition.getCalories();
            double fiber = totalNutrition.getFiber();
            double sugar = totalNutrition.getSugar();
            double satFat = totalNutrition.getSaturatedFat();

            if (fiber >= 2.0 || protein >= 10.0) {
                if (satFat <= 5.0) return "A+";
                return "A";
            } else if (sugar > 35.0 || satFat > 15.0) {
                return "D";
            } else {
                return "A";
            }
        }
        return "A+";
    }

    public void setGrade(String grade) { this.grade = grade; }

    public String getKcal() {
        if (kcal != null && !kcal.trim().isEmpty()) return kcal;
        if (!isFood()) return "0 kcal";
        if (totalNutrition != null) {
            return Math.round(totalNutrition.getCalories()) + " kcal";
        }
        return "0 kcal";
    }

    public void setKcal(String kcal) { this.kcal = kcal; }

    public String getProtein() {
        if (protein != null && !protein.trim().isEmpty()) return protein;
        if (!isFood()) return "0g";
        if (totalNutrition != null) {
            return formatWeight(totalNutrition.getProtein());
        }
        return "0g";
    }

    public void setProtein(String protein) { this.protein = protein; }

    public String getCarbs() {
        if (carbs != null && !carbs.trim().isEmpty()) return carbs;
        if (!isFood()) return "0g";
        if (totalNutrition != null) {
            return formatWeight(totalNutrition.getCarbs());
        }
        return "0g";
    }

    public void setCarbs(String carbs) { this.carbs = carbs; }

    public String getFat() {
        if (fat != null && !fat.trim().isEmpty()) return fat;
        if (!isFood()) return "0g";
        if (totalNutrition != null) {
            return formatWeight(totalNutrition.getFat());
        }
        return "0g";
    }

    public void setFat(String fat) { this.fat = fat; }

    public String getFiber() {
        if (fiber != null && !fiber.trim().isEmpty()) return fiber;
        if (!isFood()) return "0g";
        if (totalNutrition != null) {
            return formatWeight(totalNutrition.getFiber());
        }
        return "0g";
    }

    public void setFiber(String fiber) { this.fiber = fiber; }

    public String getSugar() {
        if (sugar != null && !sugar.trim().isEmpty()) return sugar;
        if (!isFood()) return "0g";
        if (totalNutrition != null) {
            return formatWeight(totalNutrition.getSugar());
        }
        return "0g";
    }

    public void setSugar(String sugar) { this.sugar = sugar; }

    public String getSodium() {
        if (sodium != null && !sodium.trim().isEmpty()) return sodium;
        if (!isFood()) return "0mg";
        if (totalNutrition != null) {
            return Math.round(totalNutrition.getSodium()) + "mg";
        }
        return "0mg";
    }

    public void setSodium(String sodium) { this.sodium = sodium; }

    public String getSatFat() {
        if (satFat != null && !satFat.trim().isEmpty()) return satFat;
        if (!isFood()) return "0g";
        if (totalNutrition != null) {
            return formatWeight(totalNutrition.getSaturatedFat());
        }
        return "0g";
    }

    public void setSatFat(String satFat) { this.satFat = satFat; }

    public String getAiInsight() {
        if (aiInsight != null && !aiInsight.trim().isEmpty()) return aiInsight;
        if (!isFood()) {
            return message != null && !message.isEmpty() ? message : "No food detected. Please scan a clear photo of food, fruit, or meal.";
        }
        if (fitnessAnalysis != null) {
            StringBuilder sb = new StringBuilder();
            if (fitnessAnalysis.getRecommendation() != null && !fitnessAnalysis.getRecommendation().isEmpty()) {
                sb.append(fitnessAnalysis.getRecommendation());
            }
            if (fitnessAnalysis.getMuscleBuilding() != null && !fitnessAnalysis.getMuscleBuilding().isEmpty()) {
                if (sb.length() > 0) sb.append(" ");
                sb.append(fitnessAnalysis.getMuscleBuilding());
            }
            if (fitnessAnalysis.getMealBalance() != null && !fitnessAnalysis.getMealBalance().isEmpty()) {
                if (sb.length() > 0) sb.append(" ");
                sb.append(fitnessAnalysis.getMealBalance());
            }
            if (sb.length() > 0) return sb.toString();
        }
        return "Nutritional analysis completed via FitBond AI.";
    }

    public void setAiInsight(String aiInsight) { this.aiInsight = aiInsight; }

    public String getRawJson() { return rawJson; }
    public void setRawJson(String rawJson) { this.rawJson = rawJson; }

    private static String formatWeight(double val) {
        if (val == (long) val) {
            return String.format(Locale.US, "%dg", (long) val);
        } else {
            return String.format(Locale.US, "%.1fg", val);
        }
    }
}
