package com.amstudio.fitbond.svg;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.net.Uri;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.Nullable;

import com.caverock.androidsvg.SVG;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.InputStream;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

public class SvgTemplateManager {

    private static final String TAG = "SvgTemplateManager";

    public static final String ASSET_TEMPLATE_1 = "food_templates/template1_fitness.svg";
    public static final String ASSET_TEMPLATE_2 = "food_templates/template2_gym.svg";
    public static final String ASSET_TEMPLATE_3 = "food_templates/template3_sunset.svg";
    public static final String ASSET_TEMPLATE_4 = "food_templates/template4_aurora.svg";

    public static class TemplateItem {
        public final int index;
        public final String name;
        public final String assetPath;
        public final String description;

        public TemplateItem(int index, String name, String assetPath, String description) {
            this.index = index;
            this.name = name;
            this.assetPath = assetPath;
            this.description = description;
        }
    }

    public static final TemplateItem[] TEMPLATES = new TemplateItem[]{
            new TemplateItem(0, "Blue Glass Card", ASSET_TEMPLATE_1, "Translucent blue glass card with specular highlights"),
            new TemplateItem(1, "Dark Glass Pro Card", ASSET_TEMPLATE_2, "Dark glassmorphism card with 25px blur and gym macro breakdown"),
            new TemplateItem(2, "Sunset Amber Glass Card", ASSET_TEMPLATE_3, "Golden sunset amber glassmorphism card with warm gradient glow"),
            new TemplateItem(3, "Glorify Aurora Glass Card", ASSET_TEMPLATE_4, "Stellar cobalt-blue to golden-amber dual tone glassmorphism card")
    };

    public static class FoodData {
        public String foodName = "Grilled Chicken Bowl";
        public String calories = "520 kcal";
        public String protein = "42g";
        public String carbs = "55g";
        public String fat = "12g";
        public String dateOrHeading = "";
        @Nullable public Bitmap foodImageBitmap = null;

        public FoodData() {}

        public FoodData(String foodName, String calories, String protein, String carbs, String fat, String dateOrHeading, @Nullable Bitmap foodImageBitmap) {
            this.foodName = foodName;
            this.calories = calories;
            this.protein = protein;
            this.carbs = carbs;
            this.fat = fat;
            this.dateOrHeading = dateOrHeading;
            this.foodImageBitmap = foodImageBitmap;
        }
    }

    /**
     * Loads and modifies an SVG template DOM document from assets based on FoodData.
     */
    public static String getModifiedSvgString(Context context, String assetPath, FoodData data) {
        try (InputStream is = context.getAssets().open(assetPath)) {
            DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
            dbFactory.setNamespaceAware(true);
            DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
            Document doc = dBuilder.parse(is);

            // Format year / header
            String dateText = "2026";

            // Update Dynamic Fields by ID
            updateTextNodeById(doc, "tv_food_name", data.foodName, true);
            updateTextNodeById(doc, "tv_calories", formatCalories(data.calories), false);
            updateTextNodeById(doc, "tv_protein", formatGramsOnly(data.protein), false);
            updateTextNodeById(doc, "tv_carbs", formatGramsOnly(data.carbs), false);
            updateTextNodeById(doc, "tv_fat", formatGramsOnly(data.fat), false);
            updateTextNodeById(doc, "tv_date", dateText, false);
            
            String heading = "NUTRITION SNAP";
            if (ASSET_TEMPLATE_4.equals(assetPath)) {
                heading = "✨ GLORIFY AURORA";
            } else if (ASSET_TEMPLATE_3.equals(assetPath)) {
                heading = "🌅 SUNSET GLOW";
            } else if (ASSET_TEMPLATE_2.equals(assetPath)) {
                heading = "💪 PRO GYM FUEL";
            }
            updateTextNodeById(doc, "tv_heading", heading, false);

            // Serialize DOM back to String
            TransformerFactory tf = TransformerFactory.newInstance();
            Transformer transformer = tf.newTransformer();
            transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
            transformer.setOutputProperty(OutputKeys.INDENT, "no");
            StringWriter writer = new StringWriter();
            transformer.transform(new DOMSource(doc), new StreamResult(writer));
            return writer.getBuffer().toString();
        } catch (Exception e) {
            Log.e(TAG, "Error modifying SVG XML", e);
            return "";
        }
    }

    private static String formatCalories(String rawCalories) {
        if (rawCalories == null || rawCalories.trim().isEmpty()) return "520 kcal";
        String val = rawCalories.trim().replace(".", "");
        if (val.toLowerCase().endsWith("kcal")) {
            return val;
        }
        return val + " kcal";
    }

    private static String formatGramsOnly(String rawVal) {
        if (rawVal == null || rawVal.trim().isEmpty()) return "0g";
        String clean = rawVal.replaceAll("[^0-9.]", "").trim();
        if (clean.isEmpty()) return rawVal.trim();
        return clean + "g";
    }

    /**
     * Safely updates text content of an SVG node with a matching id attribute.
     * Also auto-adjusts font size if text is long.
     */
    private static void updateTextNodeById(Document doc, String targetId, String textValue, boolean isTitle) {
        if (textValue == null) textValue = "";
        Element element = doc.getElementById(targetId);
        if (element == null) {
            // Search manually for element with id="targetId"
            element = findElementByIdRecursive(doc.getDocumentElement(), targetId);
        }

        if (element != null) {
            element.setTextContent(textValue);

            // Auto-adjust font size for title if long
            if (isTitle) {
                int originalFontSize = 32;
                if ("tv_food_name".equals(targetId) && element.hasAttribute("font-size")) {
                    try {
                        originalFontSize = Integer.parseInt(element.getAttribute("font-size"));
                    } catch (NumberFormatException ignored) {}
                }

                if (textValue.length() > 24) {
                    int newSize = Math.max(16, originalFontSize - 10);
                    element.setAttribute("font-size", String.valueOf(newSize));
                } else if (textValue.length() > 16) {
                    int newSize = Math.max(20, originalFontSize - 6);
                    element.setAttribute("font-size", String.valueOf(newSize));
                }
            }
        }
    }

    private static Element findElementByIdRecursive(Element parent, String targetId) {
        if (targetId.equals(parent.getAttribute("id"))) {
            return parent;
        }
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                Element res = findElementByIdRecursive((Element) child, targetId);
                if (res != null) return res;
            }
        }
        return null;
    }

    /**
     * Renders the SVG template with FoodData onto a high resolution Bitmap (e.g. 800x1000 or 1080x1920).
     */
    public static Bitmap renderTemplateToBitmap(Context context, String assetPath, FoodData data, int targetWidth, int targetHeight) {
        String svgXml = getModifiedSvgString(context, assetPath, data);
        if (TextUtils.isEmpty(svgXml)) {
            return Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888);
        }

        try {
            SVG svg = SVG.getFromString(svgXml);
            Bitmap bitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);

            // Scale Canvas to SVG viewBox (800x1000)
            float scaleX = (float) targetWidth / 800f;
            float scaleY = (float) targetHeight / 1000f;
            canvas.scale(scaleX, scaleY);

            // Render SVG template (without drawing inner image inside card)
            svg.renderToCanvas(canvas);

            return bitmap;
        } catch (Exception e) {
            Log.e(TAG, "Error rendering SVG to bitmap", e);
            return Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888);
        }
    }

    private static void drawScaledImageInRect(Canvas canvas, Bitmap srcBitmap, RectF destRect, float rx) {
        int saveCount = canvas.save();
        Path clipPath = new Path();
        clipPath.addRoundRect(destRect, rx, rx, Path.Direction.CW);
        canvas.clipPath(clipPath);

        // Center-crop matrix
        float srcWidth = srcBitmap.getWidth();
        float srcHeight = srcBitmap.getHeight();
        float scale = Math.max(destRect.width() / srcWidth, destRect.height() / srcHeight);
        float dx = destRect.left + (destRect.width() - srcWidth * scale) / 2f;
        float dy = destRect.top + (destRect.height() - srcHeight * scale) / 2f;

        Matrix matrix = new Matrix();
        matrix.postScale(scale, scale);
        matrix.postTranslate(dx, dy);

        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        canvas.drawBitmap(srcBitmap, matrix, paint);

        canvas.restoreToCount(saveCount);
    }

    /**
     * Renders a full high-resolution composite bitmap consisting of the food background photo
     * overlaid with the selected SVG food template and optional user overlay image.
     */
    public static Bitmap renderFullFoodSnapWithOverlay(
            Context context,
            @Nullable Uri foodImageUri,
            @Nullable Bitmap foodBitmap,
            int templateIndex,
            FoodData foodData,
            @Nullable Bitmap userOverlayBitmap,
            int targetWidth,
            int targetHeight) {

        Bitmap result = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(result);
        canvas.drawColor(Color.BLACK);

        // 1. Draw base food image full screen
        Bitmap bgBitmap = foodBitmap;
        if (bgBitmap == null && foodImageUri != null) {
            try (InputStream is = context.getContentResolver().openInputStream(foodImageUri)) {
                bgBitmap = BitmapFactory.decodeStream(is);
            } catch (Exception e) {
                Log.e(TAG, "Error loading background food image for full overlay rendering", e);
            }
        }

        if (bgBitmap != null && !bgBitmap.isRecycled()) {
            RectF destRect = new RectF(0, 0, targetWidth, targetHeight);
            drawScaledImageInRect(canvas, bgBitmap, destRect, 0f);
        }

        // 2. Render SVG template as overlay
        if (templateIndex >= 0 && templateIndex < TEMPLATES.length) {
            TemplateItem item = TEMPLATES[templateIndex];
            Bitmap templateBitmap = renderTemplateToBitmap(context, item.assetPath, foodData, targetWidth, targetHeight);
            if (templateBitmap != null && !templateBitmap.isRecycled()) {
                Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
                canvas.drawBitmap(templateBitmap, 0, 0, paint);
            }
        }

        // 3. Draw user added overlay image if present
        if (userOverlayBitmap != null && !userOverlayBitmap.isRecycled()) {
            int overlayW = (int) (targetWidth * 0.45f);
            int overlayH = (int) (targetHeight * 0.28f);
            int left = (targetWidth - overlayW) / 2;
            int top = (targetHeight - overlayH) / 2;
            RectF userOverlayRect = new RectF(left, top, left + overlayW, top + overlayH);
            drawScaledImageInRect(canvas, userOverlayBitmap, userOverlayRect, 18f);
        }

        return result;
    }
}
