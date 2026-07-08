# ProGuard rules for Screen Translator
# Since we rely on ML Kit, we usually don't need custom rules unless it crashes at runtime.
-keep class com.google.mlkit.** { *; }
