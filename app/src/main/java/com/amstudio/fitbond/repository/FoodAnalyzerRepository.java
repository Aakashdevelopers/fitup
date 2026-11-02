package com.amstudio.fitbond.repository;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import android.util.Log;
import android.util.LruCache;

import com.amstudio.fitbond.BuildConfig;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class FoodAnalyzerRepository {

    private static final String TAG = "FoodAnalyzerRepository";

    private static class ProviderConfig {
        final String name;
        final String apiKey;
        final String[] endpoints;
        final String[] models;

        ProviderConfig(String name, String apiKey, String[] endpoints, String[] models) {
            this.name = name;
            this.apiKey = apiKey;
            this.endpoints = endpoints;
            this.models = models;
        }
    }

    private static final ProviderConfig[] PROVIDERS = {
            // OpenRouter Provider
            new ProviderConfig(
                    "OpenRouter",
                    BuildConfig.OPENROUTER_API_KEY,
                    new String[]{
                            "https://openrouter.ai/api/v1/chat/completions"
                    },
                    new String[]{
                            "google/gemini-3.8-flash"
                    }
            )
    };

    // In-memory LRU Cache (capacity: 30 food analysis results)
    private static final LruCache<String, FoodAnalysisResult> RESULT_CACHE = new LruCache<>(30);

    // In-flight request deduplication map
    private static final Map<String, List<AnalysisCallback>> ACTIVE_REQUESTS = new HashMap<>();

    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(3);

    public interface AnalysisCallback {
        void onSuccess(FoodAnalysisResult result);
        void onError(Exception e);
    }

    public static void analyzeFood(Context context, Uri imageUri, AnalysisCallback callback) {
        if (context == null || imageUri == null) {
            if (callback != null) {
                notifyCallbackError(callback, new IllegalArgumentException("Invalid Context or Uri"));
            }
            return;
        }

        final String cacheKey = generateCacheKey(context, imageUri);

        // 1. Check in-memory cache
        synchronized (RESULT_CACHE) {
            FoodAnalysisResult cachedResult = RESULT_CACHE.get(cacheKey);
            if (cachedResult != null) {
                Log.d(TAG, "Returning cached FoodAnalysisResult for key: " + cacheKey);
                if (callback != null) {
                    notifyCallbackSuccess(callback, cachedResult);
                }
                return;
            }
        }

        // 2. Request deduplication
        synchronized (ACTIVE_REQUESTS) {
            if (ACTIVE_REQUESTS.containsKey(cacheKey)) {
                Log.d(TAG, "Analysis already in progress for Uri. Attaching callback to active request.");
                List<AnalysisCallback> existingCallbacks = ACTIVE_REQUESTS.get(cacheKey);
                if (existingCallbacks != null && callback != null) {
                    existingCallbacks.add(callback);
                }
                return;
            }
            List<AnalysisCallback> callbacks = new ArrayList<>();
            if (callback != null) {
                callbacks.add(callback);
            }
            ACTIVE_REQUESTS.put(cacheKey, callbacks);
        }

        // 3. Execute background AI analysis
        EXECUTOR.execute(() -> {
            try {
                // Scale down bitmap to max 1024px for optimized network transmission
                Bitmap bitmap = loadAndScaleBitmap(context, imageUri, 1024);
                if (bitmap == null) {
                    dispatchError(cacheKey, new Exception("Failed to load or scale food image"));
                    return;
                }

                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                bitmap.compress(Bitmap.CompressFormat.JPEG, 80, baos);
                byte[] imageBytes = baos.toByteArray();
                bitmap.recycle();

                String base64Image = Base64.encodeToString(imageBytes, Base64.NO_WRAP);

                Exception lastException = null;
                FoodAnalysisResult analysisResult = null;

                // Loop through providers, endpoints and vision models sequentially for high availability
                for (ProviderConfig provider : PROVIDERS) {
                    for (String endpoint : provider.endpoints) {
                        for (String model : provider.models) {
                            try {
                                Log.d(TAG, "Analyzing image with provider [" + provider.name + "] model: " + model + " at endpoint: " + endpoint);
                                analysisResult = callAiApi(endpoint, provider.apiKey, model, base64Image);
                                if (analysisResult != null) {
                                    break;
                                }
                            } catch (Exception e) {
                                Log.w(TAG, "Provider [" + provider.name + "] model " + model + " failed at " + endpoint + ": " + e.getMessage());
                                lastException = e;
                            }
                        }
                        if (analysisResult != null) {
                            break;
                        }
                    }
                    if (analysisResult != null) {
                        break;
                    }
                }

                if (analysisResult != null) {
                    synchronized (RESULT_CACHE) {
                        RESULT_CACHE.put(cacheKey, analysisResult);
                    }
                    dispatchSuccess(cacheKey, analysisResult);
                } else {
                    Exception finalErr = lastException != null ? lastException :
                            new Exception("All AI vision providers/models failed to process request");
                    dispatchError(cacheKey, finalErr);
                }

            } catch (Exception e) {
                Log.e(TAG, "Unhandled exception in food analysis pipeline", e);
                dispatchError(cacheKey, e);
            }
        });
    }

    private static FoodAnalysisResult callAiApi(String endpointUrl, String apiKey, String model, String base64Image) throws Exception {
        URL url = new URL(endpointUrl);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Authorization", "Bearer " + apiKey);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("HTTP-Referer", "https://fitbond.app");
        conn.setRequestProperty("X-Title", "FitBond");
        conn.setConnectTimeout(18000);
        conn.setReadTimeout(22000);
        conn.setDoOutput(true);

        JSONObject payload = new JSONObject();
        payload.put("model", model);
        payload.put("max_tokens", 4096);
        payload.put("temperature", 0.1);

        JSONObject responseFormat = new JSONObject();
        responseFormat.put("type", "json_object");
        payload.put("response_format", responseFormat);

        JSONArray messages = new JSONArray();
        JSONObject userMessage = new JSONObject();
        userMessage.put("role", "user");

        JSONArray contentArray = new JSONArray();

        JSONObject textContent = new JSONObject();
        textContent.put("type", "text");
        textContent.put("text", getFitnessSystemPrompt());
        contentArray.put(textContent);

        JSONObject imageContent = new JSONObject();
        imageContent.put("type", "image_url");
        JSONObject imageUrlObj = new JSONObject();
        imageUrlObj.put("url", "data:image/jpeg;base64," + base64Image);
        imageContent.put("image_url", imageUrlObj);
        contentArray.put(imageContent);

        userMessage.put("content", contentArray);
        messages.put(userMessage);

        payload.put("messages", messages);

        byte[] input = payload.toString().getBytes(StandardCharsets.UTF_8);
        try (OutputStream os = conn.getOutputStream()) {
            os.write(input, 0, input.length);
        }

        int responseCode = conn.getResponseCode();
        Log.d(TAG, "OpenRouter HTTP Response Code: " + responseCode);

        InputStream stream = (responseCode >= 200 && responseCode < 300)
                ? conn.getInputStream()
                : conn.getErrorStream();

        if (stream == null) {
            throw new Exception("HTTP " + responseCode + " with null response stream");
        }

        BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
        StringBuilder responseBuilder = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            responseBuilder.append(line);
        }
        reader.close();

        if (responseCode < 200 || responseCode >= 300) {
            throw new Exception("OpenRouter API error (" + responseCode + "): " + responseBuilder);
        }

        JSONObject responseJson = new JSONObject(responseBuilder.toString());
        JSONArray choices = responseJson.optJSONArray("choices");
        if (choices == null || choices.length() == 0) {
            throw new Exception("No choices returned from OpenRouter response");
        }

        JSONObject firstChoice = choices.getJSONObject(0);
        JSONObject messageObj = firstChoice.optJSONObject("message");
        if (messageObj == null) {
            throw new Exception("No message object in choice");
        }

        String rawContent = messageObj.optString("content", "");
        return parseResultJson(rawContent);
    }

    private static String getFitnessSystemPrompt() {
        return "You are a specialized Full-Scene Visual Food Scanner & Fitness AI for the FitBond app.\n" +
                "Your objective is to scan and analyze the ENTIRE photo (foreground, middle ground, background, held in hands, placed on tables, counters, baskets, fridges, shelves, or floors) for food, fruits, vegetables, Indian dishes, meals, beverages, and nutrition products.\n\n" +
                "ADVANCED FULL-IMAGE DETECTION RULES:\n" +
                "1. SCAN THE ENTIRE IMAGE EVERYWHERE:\n" +
                "   - Do NOT restrict detection to central plates or containers. Scan foreground, middle ground, and background.\n" +
                "   - Detect food anywhere: held in someone's hand, on a table, inside a basket or container, on kitchen counters, inside refrigerators, on shelves, on the floor, or visible in background.\n" +
                "   - Detect packaged food, protein supplements, healthy snacks, and beverages.\n" +
                "2. HAND & OCCLUSION DETECTION (NEVER CLASSIFY HAND AS FOOD):\n" +
                "   - If a person is holding a fruit or vegetable (e.g., holding a banana, apple, or orange in hand):\n" +
                "     * Identify the fruit/food item separately from the human hand/fingers.\n" +
                "     * NEVER classify the hand, fingers, or person as food.\n" +
                "     * Identify the actual fruit even if partially covered by fingers or hand.\n" +
                "     * Provide 'visible_portion' (e.g. '80% visible (held in hand, partially covered by fingers)') and 'uncertainty_reason'.\n" +
                "3. TABLE & MULTI-OBJECT SCENE DETECTION:\n" +
                "   - If an image contains multiple food items (e.g. 5 apples, 2 bananas, 3 oranges on a table or basket):\n" +
                "     * Detect every individually recognizable fruit/food item separately.\n" +
                "     * Provide accurate 'count' (integer count of visible items of this type).\n" +
                "     * Estimate total weight/quantity and individual item portion.\n" +
                "     * DO NOT ignore items simply because they are in the background or edge of the photo.\n" +
                "     * DO NOT double-count the same object.\n" +
                "4. OBJECT LOCALIZATION & BOUNDING BOXES:\n" +
                "   - For every detected item, return normalized bounding box: 'bounding_box': [ymin, xmin, ymax, xmax] with coordinates between 0.0 and 1.0 (where 0.0,0.0 is top-left and 1.0,1.0 is bottom-right).\n" +
                "   - Return approximate 'position': e.g., 'held in hand (center)', 'foreground table center', 'top-left background', 'inside basket', 'refrigerator shelf'.\n" +
                "5. PARTIAL VISIBILITY & CONFIDENCE:\n" +
                "   - Detect items ONLY when visual evidence is sufficient. DO NOT hallucinate non-existent food.\n" +
                "   - Set 'confidence' score (0.0 to 1.0). Reduce confidence if item is blurry, occluded, or background.\n" +
                "6. HEALTH GRADE EVALUATION:\n" +
                "   - Always return 'health_grade': 'A+', 'A', 'B', 'C', or 'D'.\n" +
                "   - ALL fresh fruits (Apple, Banana, Orange, Mango, Berries, Watermelon, Papaya, Guava, etc.) and raw vegetables MUST be graded 'A+' or 'A' as natural health superfoods.\n" +
                "   - High protein/wholesome meals get 'A' or 'B'. Deep fried / junk foods get 'C' or 'D'.\n" +
                "7. STRICT NON-FOOD RULE:\n" +
                "   - If NO recognizable food, fruit, beverage, or nutrition item is present in the image, return:\n" +
                "     {\n" +
                "       \"status\": \"no_food_detected\",\n" +
                "       \"image_type\": \"non_food\",\n" +
                "       \"detected_items\": [],\n" +
                "       \"message\": \"No food or fruit detected in photo.\"\n" +
                "     }\n\n" +
                "SUPPORTED CATEGORIES:\n" +
                "A. Indian & International Dishes: Roti, Rice, Dal, Paneer, Rajma, Chole, Dosa, Idli, Curries, Biryani, Thali, Bowls, Salads, Pizza, Burgers, Sandwiches, etc.\n" +
                "B. Fruits: Apple, Banana, Orange, Mango, Papaya, Watermelon, Grapes, Guava, Strawberry, Pineapple, all edible fruits.\n" +
                "C. Vegetables: Potato, Tomato, Broccoli, Spinach, Carrot, Cucumber, Onion, Capsicum, all vegetables.\n" +
                "D. Fitness Nutrition & Beverages: Whey protein, Protein bars, Milk, Smoothies, Coconut water, Juices, Tea, Coffee.\n" +
                "E. Ingredients: Nuts, Almonds, Seeds, Oats, Pulses, Spices.\n\n" +
                "STRICT JSON OUTPUT FORMAT (Return ONLY valid raw JSON, NO ```json markdown code block):\n" +
                "{\n" +
                "  \"status\": \"success\",\n" +
                "  \"image_type\": \"food\",\n" +
                "  \"meal_type\": \"Snack\",\n" +
  "  \"health_grade\": \"A+\",\n" +
  "  \"detected_items\": [\n" +
                "    {\n" +
                "      \"name\": \"Banana\",\n" +
                "      \"common_name\": \"Banana\",\n" +
                "      \"hindi_name\": \"केला\",\n" +
                "      \"category\": \"fruit\",\n" +
                "      \"food_type\": \"vegan\",\n" +
                "      \"preparation\": \"raw\",\n" +
                "      \"preparation_method\": \"raw\",\n" +
                "      \"count\": 1,\n" +
                "      \"position\": \"held in hand (center)\",\n" +
                "      \"bounding_box\": [0.20, 0.30, 0.80, 0.70],\n" +
                "      \"visible_portion\": \"85% visible (partially covered by fingers)\",\n" +
                "      \"uncertainty_reason\": \"slight finger occlusion\",\n" +
                "      \"estimated_quantity\": 120,\n" +
                "      \"quantity_unit\": \"grams\",\n" +
                "      \"confidence\": 0.94,\n" +
                "      \"nutrition\": {\n" +
                "        \"calories\": 105,\n" +
                "        \"protein\": 1.3,\n" +
                "        \"carbs\": 27.0,\n" +
                "        \"fat\": 0.3,\n" +
                "        \"saturated_fat\": 0.1,\n" +
                "        \"fiber\": 3.1,\n" +
                "        \"sugar\": 14.4,\n" +
                "        \"sodium\": 1,\n" +
                "        \"potassium\": 422\n" +
                "      }\n" +
                "    }\n" +
                "  ],\n" +
                "  \"total_nutrition\": {\n" +
                "    \"calories\": 105,\n" +
                "    \"protein\": 1.3,\n" +
                "    \"carbs\": 27.0,\n" +
                "    \"fat\": 0.3,\n" +
                "    \"saturated_fat\": 0.1,\n" +
                "    \"fiber\": 3.1,\n" +
                "    \"sugar\": 14.4,\n" +
                "    \"sodium\": 1,\n" +
                "    \"potassium\": 422\n" +
                "  },\n" +
                "  \"fitness_analysis\": {\n" +
                "    \"muscle_building\": \"Good source of potassium & quick carbs for energy\",\n" +
                "    \"weight_loss\": \"Natural fruit sugar with low fat content\",\n" +
                "    \"meal_balance\": \"High fiber fruit portion\",\n" +
                "    \"recommendation\": \"Ideal pre-workout or quick snack fruit\"\n" +
                "  },\n" +
                "  \"data_quality\": {\n" +
                "    \"portion_estimation\": \"full_scene_visual_scan\",\n" +
                "    \"nutrition_source\": \"ai_full_image_scanner\",\n" +
                "    \"overall_confidence\": 0.94\n" +
                "  }\n" +
                "}";
    }

    private static String repairTruncatedJson(String jsonStr) {
        if (jsonStr == null || jsonStr.isEmpty()) return jsonStr;
        StringBuilder sb = new StringBuilder(jsonStr.trim());

        int openBraces = 0;
        int openBrackets = 0;
        boolean inString = false;
        boolean isEscaped = false;

        for (int i = 0; i < sb.length(); i++) {
            char c = sb.charAt(i);
            if (isEscaped) {
                isEscaped = false;
                continue;
            }
            if (c == '\\') {
                isEscaped = true;
                continue;
            }
            if (c == '"') {
                inString = !inString;
                continue;
            }
            if (!inString) {
                if (c == '{') openBraces++;
                else if (c == '}') openBraces--;
                else if (c == '[') openBrackets++;
                else if (c == ']') openBrackets--;
            }
        }

        if (inString) {
            sb.append('"');
        }

        String trimmed = sb.toString().trim();
        while (trimmed.endsWith(",")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1).trim();
        }
        sb = new StringBuilder(trimmed);

        while (openBrackets > 0) {
            sb.append(']');
            openBrackets--;
        }
        while (openBraces > 0) {
            sb.append('}');
            openBraces--;
        }

        return sb.toString();
    }

    private static FoodAnalysisResult parseResultJson(String rawContent) throws Exception {
        if (rawContent == null || rawContent.trim().isEmpty()) {
            throw new Exception("Empty response from AI vision model");
        }

        String cleaned = rawContent.trim();

        // Strip markdown fences if present
        if (cleaned.startsWith("```json")) {
            cleaned = cleaned.substring(7);
        } else if (cleaned.startsWith("```")) {
            cleaned = cleaned.substring(3);
        }
        if (cleaned.endsWith("```")) {
            cleaned = cleaned.substring(0, cleaned.length() - 3);
        }
        cleaned = cleaned.trim();

        int firstBrace = cleaned.indexOf('{');
        int lastBrace = cleaned.lastIndexOf('}');
        if (firstBrace != -1 && lastBrace > firstBrace) {
            cleaned = cleaned.substring(firstBrace, lastBrace + 1);
        } else if (firstBrace != -1) {
            cleaned = cleaned.substring(firstBrace);
        }

        JSONObject json;
        try {
            json = new JSONObject(cleaned);
        } catch (Exception e) {
            Log.w(TAG, "Failed initial JSON parsing, attempting auto-repair: " + e.getMessage());
            String repaired = repairTruncatedJson(cleaned);
            json = new JSONObject(repaired);
        }

        FoodAnalysisResult result = new FoodAnalysisResult();
        result.setRawJson(rawContent);

        String statusStr = json.optString("status", "success").trim();
        String imageTypeStr = json.optString("image_type", "food").trim();

        result.setStatus(statusStr);
        result.setImageType(imageTypeStr);
        result.setMealType(json.optString("meal_type", "Meal"));
        result.setMessage(json.optString("message", ""));

        String gradeStr = json.optString("health_grade", json.optString("grade", "")).trim();
        if (!gradeStr.isEmpty()) {
            result.setGrade(gradeStr.toUpperCase(Locale.US));
        }

        // Parse detected food items array
        JSONArray itemsArr = json.optJSONArray("detected_items");
        List<FoodAnalysisResult.DetectedFoodItem> detectedItemList = new ArrayList<>();

        if (itemsArr != null && itemsArr.length() > 0) {
            for (int i = 0; i < itemsArr.length(); i++) {
                JSONObject itemObj = itemsArr.optJSONObject(i);
                if (itemObj == null) continue;

                FoodAnalysisResult.DetectedFoodItem item = new FoodAnalysisResult.DetectedFoodItem();
                item.setName(itemObj.optString("name", "Food Item"));
                item.setCommonName(itemObj.optString("common_name", ""));
                item.setHindiName(itemObj.optString("hindi_name", ""));
                item.setCategory(itemObj.optString("category", "general"));
                item.setFoodType(itemObj.optString("food_type", "vegetarian"));
                item.setPreparation(itemObj.optString("preparation", "cooked"));
                item.setPreparationMethod(itemObj.optString("preparation_method", ""));
                item.setCount(itemObj.optInt("count", 1));
                item.setPosition(itemObj.optString("position", "center"));
                item.setVisiblePortion(itemObj.optString("visible_portion", "100%"));
                item.setUncertaintyReason(itemObj.optString("uncertainty_reason", ""));
                item.setEstimatedQuantity(itemObj.optDouble("estimated_quantity", 100.0));
                item.setQuantityUnit(itemObj.optString("quantity_unit", "grams"));
                item.setConfidence(itemObj.optDouble("confidence", 0.95));

                JSONArray bboxArr = itemObj.optJSONArray("bounding_box");
                if (bboxArr != null && bboxArr.length() == 4) {
                    double[] bbox = new double[]{
                            bboxArr.optDouble(0, 0.0),
                            bboxArr.optDouble(1, 0.0),
                            bboxArr.optDouble(2, 1.0),
                            bboxArr.optDouble(3, 1.0)
                    };
                    item.setBoundingBox(bbox);
                }

                JSONObject nutObj = itemObj.optJSONObject("nutrition");
                if (nutObj != null) {
                    FoodAnalysisResult.NutritionInfo nut = new FoodAnalysisResult.NutritionInfo(
                            nutObj.optDouble("calories", 0.0),
                            nutObj.optDouble("protein", 0.0),
                            nutObj.optDouble("carbs", 0.0),
                            nutObj.optDouble("fat", 0.0),
                            nutObj.optDouble("saturated_fat", 0.0),
                            nutObj.optDouble("fiber", 0.0),
                            nutObj.optDouble("sugar", 0.0),
                            nutObj.optDouble("sodium", 0.0),
                            nutObj.optDouble("potassium", 0.0)
                    );
                    item.setNutrition(nut);
                }

                detectedItemList.add(item);
            }
        }

        result.setDetectedItems(detectedItemList);

        // Parse total nutrition block
        JSONObject totalNutObj = json.optJSONObject("total_nutrition");
        if (totalNutObj != null) {
            FoodAnalysisResult.NutritionInfo totalNut = new FoodAnalysisResult.NutritionInfo(
                    totalNutObj.optDouble("calories", 0.0),
                    totalNutObj.optDouble("protein", 0.0),
                    totalNutObj.optDouble("carbs", 0.0),
                    totalNutObj.optDouble("fat", 0.0),
                    totalNutObj.optDouble("saturated_fat", 0.0),
                    totalNutObj.optDouble("fiber", 0.0),
                    totalNutObj.optDouble("sugar", 0.0),
                    totalNutObj.optDouble("sodium", 0.0),
                    totalNutObj.optDouble("potassium", 0.0)
            );
            result.setTotalNutrition(totalNut);
        } else if (!detectedItemList.isEmpty()) {
            // Aggregate from items if missing in total_nutrition block
            double cals = 0, prot = 0, carbs = 0, fat = 0, satFat = 0, fiber = 0, sugar = 0, sod = 0, pot = 0;
            for (FoodAnalysisResult.DetectedFoodItem item : detectedItemList) {
                FoodAnalysisResult.NutritionInfo n = item.getNutrition();
                if (n != null) {
                    cals += n.getCalories();
                    prot += n.getProtein();
                    carbs += n.getCarbs();
                    fat += n.getFat();
                    satFat += n.getSaturatedFat();
                    fiber += n.getFiber();
                    sugar += n.getSugar();
                    sod += n.getSodium();
                    pot += n.getPotassium();
                }
            }
            result.setTotalNutrition(new FoodAnalysisResult.NutritionInfo(cals, prot, carbs, fat, satFat, fiber, sugar, sod, pot));
        }

        // Parse fitness analysis block
        JSONObject fitAnalysisObj = json.optJSONObject("fitness_analysis");
        if (fitAnalysisObj != null) {
            FoodAnalysisResult.FitnessAnalysisInfo fitInfo = new FoodAnalysisResult.FitnessAnalysisInfo();
            fitInfo.setMuscleBuilding(fitAnalysisObj.optString("muscle_building", ""));
            fitInfo.setWeightLoss(fitAnalysisObj.optString("weight_loss", ""));
            fitInfo.setMealBalance(fitAnalysisObj.optString("meal_balance", ""));
            fitInfo.setRecommendation(fitAnalysisObj.optString("recommendation", ""));
            result.setFitnessAnalysis(fitInfo);
        }

        // Parse data quality block
        JSONObject dataQualObj = json.optJSONObject("data_quality");
        if (dataQualObj != null) {
            FoodAnalysisResult.DataQualityInfo dqInfo = new FoodAnalysisResult.DataQualityInfo();
            dqInfo.setPortionEstimation(dataQualObj.optString("portion_estimation", "full_scene_visual_scan"));
            dqInfo.setNutritionSource(dataQualObj.optString("nutrition_source", "ai_full_image_scanner"));
            dqInfo.setOverallConfidence(dataQualObj.optDouble("overall_confidence", 0.95));
            result.setDataQuality(dqInfo);
        }

        boolean isExplicitNonFood = "no_food_detected".equalsIgnoreCase(statusStr) || "non_food".equalsIgnoreCase(imageTypeStr);

        if (isExplicitNonFood || detectedItemList.isEmpty()) {
            result.setFood(false);
            result.setStatus("no_food_detected");
            result.setImageType("non_food");
            result.setTitle("Food Not Found");
            result.setSubtitle("No food or fruit detected in photo");
            result.setGrade("N/A");
            result.setKcal("0 kcal");
            result.setProtein("0g");
            result.setCarbs("0g");
            result.setFat("0g");
            result.setFiber("0g");
            result.setSugar("0g");
            result.setSodium("0mg");
            result.setSatFat("0g");
            result.setMessage("No recognizable food or nutrition-related item found.");
            result.setAiInsight("No food detected. Please scan a clear photo of food, fruit, or meal.");
        } else {
            result.setFood(true);
        }

        return result;
    }

    private static String generateCacheKey(Context context, Uri uri) {
        if (uri == null) return "null_uri";
        try {
            InputStream is = context.getContentResolver().openInputStream(uri);
            long available = is != null ? is.available() : 0;
            if (is != null) is.close();
            return uri.toString() + "_" + available;
        } catch (Exception e) {
            return uri.toString();
        }
    }

    private static Bitmap loadAndScaleBitmap(Context context, Uri uri, int maxDimension) {
        try {
            InputStream is = context.getContentResolver().openInputStream(uri);
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeStream(is, null, options);
            if (is != null) is.close();

            int srcWidth = options.outWidth;
            int srcHeight = options.outHeight;
            if (srcWidth <= 0 || srcHeight <= 0) return null;

            int sampleSize = 1;
            while (srcWidth / sampleSize > maxDimension || srcHeight / sampleSize > maxDimension) {
                sampleSize *= 2;
            }

            BitmapFactory.Options scaleOptions = new BitmapFactory.Options();
            scaleOptions.inSampleSize = sampleSize;
            InputStream is2 = context.getContentResolver().openInputStream(uri);
            Bitmap scaledBitmap = BitmapFactory.decodeStream(is2, null, scaleOptions);
            if (is2 != null) is2.close();

            return scaledBitmap;
        } catch (Exception e) {
            Log.e(TAG, "Error loading and scaling image for vision AI", e);
            return null;
        }
    }

    private static void dispatchSuccess(String cacheKey, FoodAnalysisResult result) {
        List<AnalysisCallback> callbacks;
        synchronized (ACTIVE_REQUESTS) {
            callbacks = ACTIVE_REQUESTS.remove(cacheKey);
        }
        if (callbacks != null) {
            new Handler(Looper.getMainLooper()).post(() -> {
                for (AnalysisCallback cb : callbacks) {
                    notifyCallbackSuccess(cb, result);
                }
            });
        }
    }

    private static void dispatchError(String cacheKey, Exception e) {
        List<AnalysisCallback> callbacks;
        synchronized (ACTIVE_REQUESTS) {
            callbacks = ACTIVE_REQUESTS.remove(cacheKey);
        }
        if (callbacks != null) {
            new Handler(Looper.getMainLooper()).post(() -> {
                for (AnalysisCallback cb : callbacks) {
                    notifyCallbackError(cb, e);
                }
            });
        }
    }

    private static void notifyCallbackSuccess(AnalysisCallback callback, FoodAnalysisResult result) {
        if (callback != null) {
            callback.onSuccess(result);
        }
    }

    private static void notifyCallbackError(AnalysisCallback callback, Exception e) {
        if (callback != null) {
            callback.onError(e);
        }
    }
}
