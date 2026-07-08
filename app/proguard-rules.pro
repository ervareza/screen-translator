# ProGuard rules for Screen Translator

# ML Kit
-keep class com.google.mlkit.** { *; }
-dontwarn com.google.mlkit.**

# Google Play Services
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.android.gms.**

# Keep native JNI methods
-keepclasseswithmembernames class * {
    native <methods>;
}
