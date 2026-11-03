# ProGuard / R8 optimization rules for FitBond

# FitBond Data Models (used for Firebase Firestore & JSON serialization)
-keep class com.amstudio.fitbond.model.** { *; }
-keep class com.amstudio.fitbond.repository.FoodAnalysisResult** { *; }
-keep class com.amstudio.fitbond.ActiveFriend { *; }
-keep class com.amstudio.fitbond.ChatItem { *; }
-keep class com.amstudio.fitbond.ChatMessage { *; }

# Preserve Annotations and Generic Types
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# Keep native methods
-keepclasseswithmembernames class * {
    native <methods>;
}

# Glide Keep Rules
-keep public class * extends com.bumptech.glide.module.AppGlideModule
-dontwarn com.bumptech.glide.generated.**
-dontwarn com.bumptech.glide.**

# Lottie Keep Rules
-keep class com.airbnb.lottie.** { *; }

# Tencent IM & TRTC SDKs
-keep class com.tencent.** { *; }
-dontwarn com.tencent.**

# Zego Express Video/Audio SDK
-keep class im.zego.** { *; }
-dontwarn im.zego.**

# OneSignal SDK
-keep class com.onesignal.** { *; }
-dontwarn com.onesignal.**

# AndroidSVG
-keep class com.caverock.androidsvg.** { *; }

# Firebase & Play Integrity SDKs
-keep class com.google.firebase.** { *; }
-dontwarn com.google.firebase.**
-keep class com.google.android.play.core.** { *; }
-dontwarn com.google.android.play.core.**
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.android.gms.**

